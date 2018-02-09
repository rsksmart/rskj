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

import javax.annotation.Nonnull;
import java.util.*;

/**
 * TransactionNodeInformation has information about which transactions are known by which nodes,
 * and provides convenient functions to retrieve all the nodes that know a certain transaction.
 * <p>
 * TransactionNodeInformation will only hold a limited amount of transactions and peers. Blocks
 * that aren't accessed frequently will be deleted, as well as peers.
 * Peers will only remember the last maxTransactions transactions that were inserted.
 */
public class TransactionNodeInformation {
    private final LinkedHashMap<Keccak256, Set<NodeID>> nodesByTransaction;
    private final int maxTransactions;
    private final int maxPeers;

    public TransactionNodeInformation(final int maxTransactions, final int maxPeers) {
        this.maxTransactions = maxTransactions;
        this.maxPeers = maxPeers;

        // Transactions are evicted in Least-recently-accessed order.
        nodesByTransaction = new LinkedHashMap<Keccak256, Set<NodeID>>(TransactionNodeInformation.this.maxTransactions, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Keccak256, Set<NodeID>> eldest) {
                return size() > TransactionNodeInformation.this.maxTransactions;
            }
        };
    }

    public TransactionNodeInformation() {
        this(1000, 50);
    }

    /**
     * addTransactionToNode specifies that a given node knows about a given transaction.
     *
     * @param transactionHash the transaction hash.
     * @param nodeID    the node to add the block to.
     */
    public void addTransactionToNode(@Nonnull final Keccak256 transactionHash, @Nonnull final NodeID nodeID) {
        Set<NodeID> transactionNodes = nodesByTransaction.get(transactionHash);
        if (transactionNodes == null) {
            // Create a new set for the nodes that know about a block.
            // There is no peer eviction, because there are few peers compared to blocks.
            transactionNodes = new HashSet<>();
            nodesByTransaction.put(transactionHash, transactionNodes);
        }

        transactionNodes.add(nodeID);
    }

    /**
     * getNodesByTransaction retrieves all the nodes that contain a given block.
     *
     * @param transactionHash the block's hash.
     * @return A set containing all the nodes that have that block.
     */
    @Nonnull
    public Set<NodeID> getNodesByTransaction(@Nonnull final Keccak256 transactionHash) {
        Set<NodeID> result = nodesByTransaction.get(transactionHash);
        if (result == null) {
            result = new HashSet<>();
        }
        return Collections.unmodifiableSet(result);
    }

}
