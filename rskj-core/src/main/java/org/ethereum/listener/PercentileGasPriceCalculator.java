/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PercentileGasPriceCalculator implements GasPriceCalculator {
    private static final int TX_WINDOW_SIZE = 512;

    private final Coin[] txWindow = new Coin[TX_WINDOW_SIZE];
    private int txIdx = TX_WINDOW_SIZE - 1;
    private Coin lastVal;

    @Override
    public synchronized Optional<Coin> getGasPrice() {
        if (txWindow[0] == null) { // for some reason, not filled yet (i.e. not enough blocks on DB)
            return Optional.empty();
        } else {
            if (lastVal == null) {
                Coin[] values = Arrays.copyOf(txWindow, TX_WINDOW_SIZE);
                Arrays.sort(values);
                lastVal = values[values.length / 4];  // 25% percentile
            }
            return Optional.of(lastVal);
        }
    }

    @Override
    public synchronized void onBlock(Block block, List<TransactionReceipt> receipts) {
       onBlock(block.getTransactionsList());
    }

    @Override
    public GasCalculatorType getType() {
        return GasCalculatorType.PLAIN_PERCENTILE;
    }

    private void onBlock(List<Transaction> transactionList) {
        for (Transaction tx : transactionList) {
            if (!(tx instanceof RemascTransaction)) {
                trackGasPrice(tx);
            }
        }
    }

    private void trackGasPrice(Transaction tx) {
        if (txIdx == -1) {
            txIdx = TX_WINDOW_SIZE - 1;
            lastVal = null;  // recalculate only 'sometimes'
        }
        txWindow[txIdx--] = tx.getGasPrice();
    }

}
