package com.iota.iri.service.tipselection.impl;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch;

import java.util.ArrayList;
import java.util.List;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class EntryPointSelectorCumulativeWeightThresholdTest {
    private TemporaryFolder dbFolder;
    private TemporaryFolder logFolder;
    private Tangle tangle;
    private TipsViewModel tipsViewModel;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @After
    public void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
    }

    @Before
    public void setUp() throws Exception {
        dbFolder = new TemporaryFolder();
        logFolder = new TemporaryFolder();
        tangle = new Tangle();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(),
                logFolder.getRoot().getAbsolutePath(), 1000));
        tangle.init();

        tipsViewModel = Mockito.mock(TipsViewModel.class);
    }

    @Test
    public void returnsGenesisForEmptyTangle() throws Exception {
        final int threshold = 50;
        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(Hash.NULL_HASH);
        
        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold);
        Hash entryPoint = entryPointSelector.getEntryPoint();

        Assert.assertEquals(Hash.NULL_HASH, entryPoint);
    }

    @Test
    public void returnsCorrectTxInChain() throws Exception {
        final int threshold = 5;
        final int chainLength = 30;
        final int expectedEntrypoint = chainLength - 3;
        
        List<TransactionViewModel> transactions = new ArrayList<TransactionViewModel>();

        transactions.add(new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash()));

        for (int i = 0; i < chainLength; i++) {
            Hash prevTxHash = transactions.get(transactions.size() - 1).getHash();
            transactions.add(new TransactionViewModel(
                getRandomTransactionWithTrunkAndBranch(prevTxHash, prevTxHash), getRandomTransactionHash()));
        }

        for (TransactionViewModel transaction : transactions) {
            transaction.store(tangle);
        }

        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(transactions.get(transactions.size() - 1).getHash());
        
        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold);
        Hash entryPoint = entryPointSelector.getEntryPoint();

        Assert.assertNotEquals(Hash.NULL_HASH, entryPoint);
        Assert.assertEquals(transactions.get(expectedEntrypoint).getHash(), entryPoint);
    }

    @Test
    public void returnsCorrectTxInWheatStockShape() throws Exception {
        final int threshold = 15;
        final int stalkLevels = 15;
        final int txPerLevel = 5;
        final int expectedStalkLevel = stalkLevels - 2;
        
        List<TransactionViewModel> mainStalk = new ArrayList<TransactionViewModel>();

        mainStalk.add(new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash()));
        mainStalk.get(0).store(tangle);

        for (int i = 0; i < stalkLevels - 1; i++) {
            Hash prevTxHash = mainStalk.get(mainStalk.size() - 1).getHash();
            TransactionViewModel mainStalkTx = new TransactionViewModel(
                getRandomTransactionWithTrunkAndBranch(prevTxHash, prevTxHash), getRandomTransactionHash());

            mainStalk.add(mainStalkTx);
            mainStalkTx.store(tangle);
            
            for (int j = 0; j < txPerLevel; j++) {
                new TransactionViewModel(
                    getRandomTransactionWithTrunkAndBranch(mainStalkTx.getHash(), mainStalkTx.getHash()), getRandomTransactionHash())
                    .store(tangle);
            }
        }

        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(mainStalk.get(mainStalk.size() - 1).getHash());
        
        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold);
        Hash entryPoint = entryPointSelector.getEntryPoint();

        Assert.assertNotEquals(Hash.NULL_HASH, entryPoint);
        Assert.assertEquals(mainStalk.get(expectedStalkLevel).getHash(), entryPoint);
    }

    @Test
    public void chooseHeighestTipWhenSolidTipIsGone() throws Exception {
        // Three chains, expect to end up on the longest
        final int threshold = 50;
        
        // Two chains
        final int shortChainLength = CumulativeWeightCalculator.MAX_FUTURE_SET_SIZE * 3;
        final int mediumChainLength = CumulativeWeightCalculator.MAX_FUTURE_SET_SIZE * 4;
        final int longChainLength = CumulativeWeightCalculator.MAX_FUTURE_SET_SIZE * 5;

        List<Hash> shortChain = makeChain(shortChainLength);
        List<Hash> mediumChain = makeChain(mediumChainLength);
        List<Hash> longChain = makeChain(longChainLength);

        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(null);

        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold);

        Hash entryPoint = entryPointSelector.getEntryPoint();

        Assert.assertTrue(longChain.contains(entryPoint));
        Assert.assertFalse(mediumChain.contains(entryPoint));
        Assert.assertFalse(shortChain.contains(entryPoint));
    }

    @Test
    public void failWhenEntryPointSizeIsTooBig() throws Exception {
        // This isn't supposed to happen, we simulate it by having getRandomSolidTipHash
        // return the genesis, rather than a tip.
        final int threshold = 10;
        final int chainLength = EntryPointSelectorCumulativeWeightThreshold.MAX_SUBTANGLE_SIZE + 1;

        makeChain(chainLength);

        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(Hash.NULL_HASH);
        
        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold);

        exception.expect(IllegalStateException.class);
        entryPointSelector.getEntryPoint();
    }

    @Test
    public void succeedWhenEntryPointSizeIsJustRight() throws Exception {
        // We simulate the maximum subtangle size by having getRandomSolidTipHash
        // return the genesis, rather than a tip.
        final int threshold = 10;
        final int chainLength = (int)(EntryPointSelectorCumulativeWeightThreshold.MAX_SUBTANGLE_SIZE - 1);
        
        makeChain(chainLength);

        // Start from the short chain, so we start the BFS from the genesis
        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(Hash.NULL_HASH);
        
        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold);

        Hash entryPoint = entryPointSelector.getEntryPoint();

        Assert.assertEquals(Hash.NULL_HASH, entryPoint);
    }

    private List<Hash> makeChain(int length) throws Exception {
        List<Hash> chain = new ArrayList<>();

        // start from genesis
        Hash tip = Hash.NULL_HASH;

        for (int i = 0; i < length; i++) {
            TransactionViewModel newTip = new TransactionViewModel(
                getRandomTransactionWithTrunkAndBranch(tip, tip), getRandomTransactionHash());
            newTip.store(tangle);
            tip = newTip.getHash();
            chain.add(tip);
        }

        return chain;
    }
}
