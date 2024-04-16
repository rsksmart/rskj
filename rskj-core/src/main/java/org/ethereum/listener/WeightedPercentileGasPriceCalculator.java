/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.listener;

import co.rsk.core.Coin;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;

import java.util.*;

public class WeightedPercentileGasPriceCalculator implements GasPriceCalculator {
    private static final int WINDOW_SIZE = 512;
    public static final int REFERENCE_PERCENTILE = 25;

    private final ArrayDeque<GasEntry> gasWindow;
    private final WeightedPercentileCalc auxCalculator;

    private int txCount = 0;
    private Coin cachedGasPrice = null;

    public WeightedPercentileGasPriceCalculator() {
        this(new WeightedPercentileCalc());
    }

    public WeightedPercentileGasPriceCalculator(WeightedPercentileCalc weightedPercentileCalc) {
        auxCalculator = weightedPercentileCalc;
        gasWindow = new ArrayDeque<>(WINDOW_SIZE);
    }

    @Override
    public synchronized Optional<Coin> getGasPrice() {
        if (cachedGasPrice == null) {
            cachedGasPrice = calculateGasPrice();
        }
        return cachedGasPrice == null ? Optional.empty() : Optional.of(cachedGasPrice);
    }

    @Override
    public synchronized void onBlock(Block block, List<TransactionReceipt> receipts) {
        for (TransactionReceipt receipt : receipts) {
            if (!(receipt.getTransaction() instanceof RemascTransaction)) {
                addTx(receipt.getTransaction(), new Coin(receipt.getGasUsed()).asBigInteger().longValue());
            }
        }
    }

    @Override
    public GasCalculatorType getType() {
        return GasCalculatorType.WEIGHTED_PERCENTILE;
    }

    private void addTx(Transaction tx, long gasUsed) {
        if (gasUsed == 0) {
            return;
        }

        txCount++;

        Coin gasPrice = tx.getGasPrice();

        if (gasWindow.size() == WINDOW_SIZE) {
            gasWindow.removeFirst();

        }
        gasWindow.add(new GasEntry(gasPrice, gasUsed));

        if (txCount > WINDOW_SIZE) {
            txCount = 0; // Reset the count
            cachedGasPrice = null; // Invalidate the cached value to force recalculation when queried.
        }
    }

    private Coin calculateGasPrice() {
        return auxCalculator.calculateWeightedPercentile(REFERENCE_PERCENTILE, new ArrayList<>(gasWindow));
    }

    static class GasEntry implements Comparable<GasEntry> {
        protected Coin gasPrice;
        protected long gasUsed;

        GasEntry(Coin gasPrice, long gasUsed) {
            this.gasPrice = gasPrice;
            this.gasUsed = gasUsed;
        }


        public Coin getGasPrice() {
            return gasPrice;
        }

        public long getGasUsed() {
            return gasUsed;
        }

        @Override
        public int compareTo
                (GasEntry o) {
            return this.gasPrice.compareTo(o.gasPrice);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof GasEntry)) {
                return false;
            }
            GasEntry gasEntry = (GasEntry) o;
            return gasUsed == gasEntry.gasUsed &&
                    Objects.equals(gasPrice, gasEntry.gasPrice);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gasPrice, gasUsed);
        }

        @Override
        public String toString() {
            return "(" + gasPrice + ", " + gasUsed + ")";
        }
    }
}
