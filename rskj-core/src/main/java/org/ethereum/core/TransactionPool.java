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

package org.ethereum.core;

import java.util.List;

/**
 * @author Mikhail Kalinin
 * @since 28.09.2015
 */
public interface TransactionPool extends org.ethereum.facade.TransactionPool {

    void start(Block initialBestBlock);

    /**
     * Adds transaction to the list of pending state txs  <br>
     * Triggers an update of pending state
     *
     * @param tx transaction
     */
    boolean addPendingTransaction(Transaction tx);

    /**
     * Adds a list of transactions to the list of pending state txs  <br>
     * Triggers an update of pending state
     *
     * @param txs transaction list
     *
     * @return the list of added transactions
     */
    List<Transaction> addPendingTransactions(List<Transaction> txs);

    /**
     * It should be called on each block imported as <b>BEST</b> <br>
     * Does several things:
     * <ul>
     *     <li>removes block's txs from pending state and wire lists</li>
     *     <li>removes outdated pending txs</li>
     *     <li>updates pending state</li>
     * </ul>
     *
     * @param block block imported into blockchain as a <b>BEST</b> one
     */
    void processBest(Block block);

    void clearPendingState(List<Transaction> txs);

    // Returns a list of pending txs
    List<Transaction> getAllPendingTransactions();
}
