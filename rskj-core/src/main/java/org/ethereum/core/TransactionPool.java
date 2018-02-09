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
     * Adds transactions received from the net to the list of wire transactions <br>
     * Don't have an impact on pending state
     *
     * @param transactions txs received from the net
     * @return A list with the accepted transactions
     */
    List<Transaction> addWireTransactions(List<Transaction> transactions);

    /**
     * Adds transaction to the list of pending state txs  <br>
     * For the moment this list is populated with txs sent by our peer only <br>
     * Triggers an update of pending state
     *
     * @param tx transaction
     */
    void addPendingTransaction(Transaction tx);

    /**
     * It should be called on each block imported as <b>BEST</b> <br>
     * Does several things:
     * <ul>
     *     <li>removes block's txs from pending state and wire lists</li>
     *     <li>removes outdated wire txs</li>
     *     <li>updates pending state</li>
     * </ul>
     *
     * @param block block imported into blockchain as a <b>BEST</b> one
     */
    void processBest(Block block);

    void clearPendingState(List<Transaction> txs);

    void clearWire(List<Transaction> txs);

    // Returns a list of pending txs
    List<Transaction> getAllPendingTransactions();
}
