package com.iota.iri.service.tipselection.impl;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashMap;
import java.util.HashSet;

public class WalkValidatorImplTest {
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;
    
    @Mock
    private static LedgerService ledgerService;

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        snapshotProvider = Mockito.mock(SnapshotProvider.class);
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider( new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
    }

    @Test
    public void shouldPassValidation() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.updateSolid(true);
        tx.store(tangle);
        Hash hash = tx.getHash();
        Mockito.when(ledgerService.isBalanceDiffConsistent(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerService);
        Assert.assertTrue("Validation failed", walkValidator.isValid(hash));
    }

    @Test
    public void failOnTxType() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getTrunkTransactionHash();
        tx.updateSolid(true);
        Mockito.when(ledgerService.isBalanceDiffConsistent(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerService);
        Assert.assertFalse("Validation succeded but should have failed since tx is missing", walkValidator.isValid(hash));
    }

    @Test
    public void failOnTxIndex() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(2);
        tx.store(tangle);
        Hash hash = tx.getHash();
        tx.updateSolid(true);
        Mockito.when(ledgerService.isBalanceDiffConsistent(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerService);
        Assert.assertFalse("Validation succeded but should have failed since we are not on a tail", walkValidator.isValid(hash));
    }

    @Test
    public void failOnSolid() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getHash();
        tx.updateSolid(false);
        Mockito.when(ledgerService.isBalanceDiffConsistent(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerService);
        Assert.assertFalse("Validation succeded but should have failed since tx is not solid",
                walkValidator.isValid(hash));
    }

    @Test
    public void failOnInconsistency() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getHash();
        tx.updateSolid(true);
        Mockito.when(ledgerService.isBalanceDiffConsistent(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(false);

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerService);
        Assert.assertFalse("Validation succeded but should have failed due to inconsistent ledger state",
                walkValidator.isValid(hash));
    }
}
