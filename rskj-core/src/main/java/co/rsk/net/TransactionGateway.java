/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net;

import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.net.server.ChannelManager;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Centralizes receiving and relaying transactions, so we can only distribute information to nodes that don't already
 * have it.
 */
public class TransactionGateway {
    private final ChannelManager channelManager;
    private final TransactionPool transactionPool;

    public TransactionGateway(
            ChannelManager channelManager,
            TransactionPool transactionPool) {
        this.channelManager = Objects.requireNonNull(channelManager);
        this.transactionPool = Objects.requireNonNull(transactionPool);
    }

    /**
     * Receives transactions from other node
     */
    public void receiveTransactionsFrom(@Nonnull List<Transaction> txs, @Nonnull Set<NodeID> nodeIDS) {
        List<Transaction> result  = transactionPool.addTransactions(txs);
        if(!result.isEmpty()) {
            channelManager.broadcastTransactions(result, nodeIDS);
        }
    }

    /**
    * Receives transaction via JSON RPC
    */
    public TransactionPoolAddResult receiveTransaction(Transaction transaction) {
        TransactionPoolAddResult result  = transactionPool.addTransaction(transaction);
        if(result.pendingTransactionsWereAdded()) {
            channelManager.broadcastTransactions(result.getPendingTransactionsAdded(), Collections.emptySet());
        }
        return result;
    }
}
