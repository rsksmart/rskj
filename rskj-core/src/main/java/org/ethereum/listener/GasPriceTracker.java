/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Calculates a 'reasonable' Gas price based on statistics of the latest transaction's Gas prices
 *
 * Normally the price returned should be sufficient to execute a transaction since ~25% of the latest
 * transactions were executed at this or lower price.
 *
 * Created by Anton Nashatyrev on 22.09.2015.
 */
public class GasPriceTracker extends EthereumListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger("gaspricetracker");

    private final Coin[] window = new Coin[512];
    private final Coin defaultPrice = Coin.valueOf(20_000_000_000L);
    private int idx = window.length - 1;
    private boolean filled = false;

    private Coin lastVal;

    @Override
    public void onBlock(Block block, List<TransactionReceipt> receipts) {
        logger.trace("Start onBlock");

        for (Transaction tx : block.getTransactionsList()) {
            onTransaction(tx);
        }

        logger.trace("End onBlock");
    }

    public void onTransaction(Transaction tx) {
        if (idx == -1) {
            idx = window.length - 1;
            filled = true;
            lastVal = null;  // recalculate only 'sometimes'
        }
        window[idx--] = tx.getGasPrice();
    }

    public Coin getGasPrice() {
        if (!filled) {
            return defaultPrice;
        } else {
            if (lastVal == null) {
                Coin[] values = Arrays.copyOf(window, window.length);
                Arrays.sort(values);
                lastVal = values[values.length / 4];  // 25% percentile
            }
            return lastVal;
        }
    }
}
