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

import co.rsk.config.InternalService;
import co.rsk.core.bc.PendingState;

import java.util.List;

public interface TransactionPool extends InternalService {
    /**
     * Adds transaction to the list of pending or queued state txs  <br>
     * Triggers an update of pending state
     *
     * Doesn't broadcast transaction to active peers
     *
     * @param tx transaction
     */
    TransactionPoolAddResult addTransaction(Transaction tx);

    /**
     * Adds a list of transactions to the list of pending state txs or
     * queued transactions
     *
     * Triggers an update of pending state
     *
     * Doesn't broadcast transaction to active peers
     *
     * @param txs transaction list
     *
     * @return the list of added transactions
     */
    List<Transaction> addTransactions(List<Transaction> txs);

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

    void setBestBlock(Block block);

    void removeTransactions(List<Transaction> txs);

    /**
     * @return list of pending transactions (ready to be executed)
     */
    List<Transaction> getPendingTransactions();

    // Returns a list of queued txs (out of nonce sequence)
    List<Transaction> getQueuedTransactions();

    /**
     * @return pending state
     */
    PendingState getPendingState();
}
