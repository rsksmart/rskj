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

import co.rsk.crypto.Keccak256;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.net.server.ChannelManager;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Centralizes receiving and relaying transactions, so we can only distribute information to nodes that don't already
 * have it.
 */
public class TransactionGateway {
    private final ChannelManager channelManager;
    private final CompositeEthereumListener emitter;
    private final TransactionPool transactionPool;

    private final TransactionNodeInformation transactionNodeInformation = new TransactionNodeInformation();
    private final OnPendingTransactionsReceivedListener listener = new OnPendingTransactionsReceivedListener();

    public TransactionGateway(
            ChannelManager channelManager,
            TransactionPool transactionPool,
            CompositeEthereumListener emitter) {
        this.channelManager = Objects.requireNonNull(channelManager);
        this.transactionPool = Objects.requireNonNull(transactionPool);
        this.emitter = Objects.requireNonNull(emitter);
    }

    public void start() {
        emitter.addListener(listener);
    }

    public void stop() {
        emitter.removeListener(listener);
    }

    public List<Transaction> receiveTransactionsFrom(List<Transaction> txs, NodeID nodeID) {
        txs.forEach(tx -> transactionNodeInformation.addTransactionToNode(tx.getHash(), nodeID));
        return transactionPool.addTransactions(txs);
    }

    private class OnPendingTransactionsReceivedListener extends EthereumListenerAdapter {
        @Override
        public void onPendingTransactionsReceived(List<Transaction> txs) {
            for (Transaction tx : txs) {
                Keccak256 txHash = tx.getHash();
                Set<NodeID> nodesToSkip = new HashSet<>(transactionNodeInformation.getNodesByTransaction(txHash));
                Set<NodeID> newNodes = channelManager.broadcastTransaction(tx, nodesToSkip);

                newNodes.forEach(nodeID -> transactionNodeInformation.addTransactionToNode(txHash, nodeID));
            }
        }
    }
}

