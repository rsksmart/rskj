/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;

import java.util.*;
import java.util.stream.Collectors;

public class TransactionSet {
    private final Map<Keccak256, Transaction> transactionsByHash;
    private final Map<RskAddress, List<Transaction>> transactionsByAddress;

    private final SignatureCache signatureCache;

    public TransactionSet(SignatureCache signatureCache) {
        this(new HashMap<>(), new HashMap<>(), signatureCache);
    }

    public TransactionSet(TransactionSet transactionSet, SignatureCache signatureCache) {
        this(new HashMap<>(transactionSet.transactionsByHash), new HashMap<>(transactionSet.transactionsByAddress), signatureCache);
    }

    public TransactionSet(Map<Keccak256, Transaction> transactionsByHash, Map<RskAddress, List<Transaction>> transactionsByAddress, SignatureCache signatureCache) {
        this.transactionsByHash = transactionsByHash;
        this.transactionsByAddress = transactionsByAddress;
        this.signatureCache = signatureCache;
    }

    public void addTransaction(Transaction transaction) {
        Keccak256 txhash = transaction.getHash();

        if (this.transactionsByHash.containsKey(txhash)) {
            return;
        }

        this.transactionsByHash.put(txhash, transaction);

        RskAddress senderAddress = transaction.getSender(signatureCache);

        List<Transaction> txs = this.transactionsByAddress.get(senderAddress);

        if (txs == null) {
            txs = new ArrayList<>();
            this.transactionsByAddress.put(senderAddress, txs);
        } else {
            Optional<Transaction> optTxToRemove = txs.stream()
                    .filter(tx -> tx.getNonceAsInteger().equals(transaction.getNonceAsInteger()))
                    .findFirst();

            if (optTxToRemove.isPresent()) {
                Transaction txToRemove = optTxToRemove.get();
                txs.remove(txToRemove);
                this.transactionsByHash.remove(txToRemove.getHash());
            }
        }

        txs.add(transaction);
    }

    public boolean hasTransaction(Transaction transaction) {
        return this.transactionsByHash.containsKey(transaction.getHash());
    }

    public void removeTransactionByHash(Keccak256 hash) {
        Transaction transaction = this.transactionsByHash.get(hash);

        if (transaction == null) {
            return;
        }

        this.transactionsByHash.remove(hash);

        RskAddress senderAddress = transaction.getSender(signatureCache);
        List<Transaction> txs = this.transactionsByAddress.get(senderAddress);

        if (txs != null) {
            txs.remove(transaction);

            if (txs.isEmpty()) {
                this.transactionsByAddress.remove(senderAddress);
            }
        }
    }

    public List<Transaction> getTransactions() {
        return transactionsByHash.values().stream()
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public List<Transaction> getTransactionsWithSender(RskAddress senderAddress) {
        List<Transaction> list = this.transactionsByAddress.get(senderAddress);

        if (list == null) {
            return Collections.emptyList();
        }

        return list;
    }
}
