package com.iota.iri.service.tipselection.impl;

import java.util.Objects;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.service.tipselection.StartingTipSelector;
import com.iota.iri.service.tipselection.TailFinder;
import com.iota.iri.storage.Tangle;

/**
 * Implementation of <tt>EntryPointSelector</tt> that backtracks via trunkTransaction 
 * until reaching a minimum Cumulative Weight or the genesis.
 */
public class EntryPointSelectorCumulativeWeightThreshold implements EntryPointSelector {
    public final Tangle tangle;
    private final CumulativeWeightCalculator cumulativeWeightCalculator;
    private final StartingTipSelector startingTipSelector;
    private final int threshold;
    private final TailFinder tailFinder;

    public static int MAX_SUBTANGLE_SIZE = 15 * CumulativeWeightCalculator.MAX_FUTURE_SET_SIZE;

    public EntryPointSelectorCumulativeWeightThreshold(Tangle tangle, int threshold,
            StartingTipSelector startingTipSelector, TailFinder tailFinder) {
        this.tangle = tangle;
        this.cumulativeWeightCalculator = new CumulativeWeightCalculator(tangle);
        this.threshold = threshold;
        this.startingTipSelector = startingTipSelector;
        this.tailFinder = tailFinder;
    }

    @Override
    public Hash getEntryPoint() throws Exception {
        Hash entryBundle = backtrack(startingTipSelector.getTip(), threshold);
        Hash entryPoint = tailFinder.findTail(entryBundle).get();

        int subtangleWeight = cumulativeWeightCalculator.calculateSingle(entryPoint);
        if (subtangleWeight > MAX_SUBTANGLE_SIZE) {
            throw new IllegalStateException(String.format(
                "The selected entry point's subtangle size is too big. EntryPoint Hash: %s Subtangle size: %d",
                entryPoint, subtangleWeight));
        }

        return entryPoint;
    }

    /**
     * Backtracks from tip until it reaches a transaction with a cumulative weight
     * of at least {@code threshold}.
     * 
     * Uses an exponential search to avoid running the expensive CW computations
     * more than necessary.
     */
    private Hash backtrack(Hash tip, int threshold) throws Exception {
        Hash currentHash = tip;
        int currentWeight = 1;
        int stepSize = 1;

        // Backtrack as long as the genesis hasn't been reached and the threshold has not been crossed
        while (currentWeight < threshold && !isGenesis(currentHash)) {
            currentHash = nStepsBack(currentHash, stepSize);
            currentWeight = cumulativeWeightCalculator.calculateSingle(currentHash);
            stepSize *= 2;
        }

        return currentHash;
    }

    private Hash nStepsBack(Hash from, int steps) throws Exception {
        Hash currentHash = from;
        int i = 0;

        while (i < steps && !isGenesis(currentHash)) {
            currentHash = TransactionViewModel.fromHash(tangle, currentHash).getTrunkTransactionHash();
            i++;
        }

        return currentHash;
    }

    private static boolean isGenesis(Hash transaction) {
        return Objects.equals(transaction, Hash.NULL_HASH);
    }
}
