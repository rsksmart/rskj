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

package co.rsk.core.bc;

import co.rsk.core.RskAddress;
import org.ethereum.core.Transaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.GasCost;

import java.util.*;

public class ParallelizeTransactionHandler {
    public static final short SEQUENTIAL_BUCKET_ID = (short) 0;
    private final TransactionBucket sequentialBucket;
    private final HashMap<ByteArrayWrapper, Short> bucketByWrittenKey;
    private final HashMap<ByteArrayWrapper, Set<Short>> bucketByReadKey;
    private final Map<RskAddress, Short> senderBucket;
    private final List<TransactionBucket> parallelBuckets;

    public ParallelizeTransactionHandler(int buckets, long blockGasLimit) {
        long gasLimit = blockGasLimit/2;
        this.senderBucket = new HashMap<>();
        this.bucketByWrittenKey = new HashMap<>();
        this.bucketByReadKey = new HashMap<>();
        this.parallelBuckets = new ArrayList<>();
        this.sequentialBucket = new TransactionBucket(SEQUENTIAL_BUCKET_ID, gasLimit);
        for (short i = 1; i < buckets + 1; i++){
            this.parallelBuckets.add(new TransactionBucket(i, gasLimit));
        }
    }

    public boolean sequentialBucketHasGasAvailable(Transaction tx) {
        long txGasLimit = GasCost.toGas(tx.getGasLimit());
        return this.sequentialBucket.hasGasAvailable(txGasLimit);
    }

    public Optional<Short> addTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {

        //TODO(JULI): Check whether the tx is remasc.

        Set<Short> bucketCandidates = getBucketCandidates(newReadKeys, newWrittenKeys);
        long txGasLimit = GasCost.toGas(tx.getGasLimit());

        if (bucketCandidates.size() > 1) {
            return addTransactionInSequentialBucketAndGetId(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
        }

        Optional<Short> bucketId = getBucketBySender(tx);

        if (bucketId.isPresent()) {
            Short bucketIdNumber = bucketId.get();
            if (bucketCandidates.size() == 1 && bucketCandidates.contains(bucketIdNumber)) {
                TransactionBucket transactionBucket = this.parallelBuckets.get(bucketIdNumber);

                if (transactionBucket.hasGasAvailable(txGasLimit)) {
                    addNewKeysToMaps(tx.getSender(), bucketIdNumber, newReadKeys, newWrittenKeys);
                    transactionBucket.addTransactionAndGas(tx, gasUsedByTx);
                    return bucketId;
                }
            }
        } else {
            bucketId = bucketCandidates.size() == 1? Optional.of(bucketCandidates.iterator().next()) : getFirstAvailableBucket(txGasLimit);

            if (bucketId.isPresent()) {
                return addTransactionInParallelBucketAndGetId(tx, newReadKeys, newWrittenKeys, gasUsedByTx, bucketId);
            }
        }
        return addTransactionInSequentialBucketAndGetId(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
    }

    private Optional<Short> addTransactionInParallelBucketAndGetId(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx, Optional<Short> bucketId) {
        Short bucketNumber = bucketId.get();
        TransactionBucket transactionBucket = this.parallelBuckets.get(bucketNumber);
        addNewKeysToMaps(tx.getSender(), bucketNumber, newReadKeys, newWrittenKeys);
        transactionBucket.addTransactionAndGas(tx, gasUsedByTx);
        return bucketId;
    }

    private Optional<Short> addTransactionInSequentialBucketAndGetId(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
        addNewKeysToMaps(tx.getSender(), this.sequentialBucket.getId(), newReadKeys, newWrittenKeys);
        this.sequentialBucket.addTransactionAndGas(tx, gasUsedByTx);
        return Optional.of(this.sequentialBucket.getId());
    }

    private void addNewKeysToMaps(RskAddress sender, Short bucketId, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        for (ByteArrayWrapper key : newReadKeys) {
            Set<Short> bucketIds = bucketByReadKey.getOrDefault(key, new HashSet<>());
            bucketIds.add(bucketId);
            bucketByReadKey.put(key, bucketIds);
        }

        for (ByteArrayWrapper key: newWrittenKeys) {
            bucketByWrittenKey.put(key, bucketId);
        }

        senderBucket.put(sender, bucketId);
    }

    private Optional<Short> getBucketBySender(Transaction tx) {
        return Optional.ofNullable(senderBucket.get(tx.getSender()));
    }

    private Optional<Short> getFirstAvailableBucket(long txGasLimit) {
        for (TransactionBucket bucket : parallelBuckets) {
            if (bucket.hasGasAvailable(txGasLimit)) {
                return Optional.of(bucket.getId());
            }
        }
        return Optional.empty();
    }


    private Set<Short> getBucketCandidates(Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        Set<Short> bucketPerTx = new HashSet<>();

        // read - written
        for (ByteArrayWrapper newReadKey : newReadKeys) {
            if (bucketByWrittenKey.containsKey(newReadKey)) {
                bucketPerTx.add(bucketByWrittenKey.get(newReadKey));
            }
        }

        // written - read
        // written - written
        for (ByteArrayWrapper newWrittenKey : newWrittenKeys) {
            if (bucketByWrittenKey.containsKey(newWrittenKey)) {
                bucketPerTx.add(bucketByWrittenKey.get(newWrittenKey));
            }

            if (bucketByReadKey.containsKey(newWrittenKey)) {
                bucketPerTx.addAll(bucketByReadKey.get(newWrittenKey));
            }
        }
        return bucketPerTx;
    }

    public long getGasUsedIn(Short bucketId) {
//        if (bucketId > this.parallelBuckets.size() || bucketId < 0) {
//            throw
//        }

        if (bucketId == SEQUENTIAL_BUCKET_ID) {
            return this.sequentialBucket.getGasUsed();
        }

        return this.parallelBuckets.get(bucketId).getGasUsed();
    }

    public List<Transaction> getTransactionsInOrder() {
        List<Transaction> txs = new ArrayList<>();
        for (TransactionBucket bucket: this.parallelBuckets) {
            txs.addAll(bucket.getTransactions());
        }

        txs.addAll(this.sequentialBucket.getTransactions());
        return txs;
    }

    public List<Integer> getBucketOrder() {
        List<Integer> txs = new ArrayList<>();
        for (TransactionBucket bucket: this.parallelBuckets) {
            txs.add(bucket.getNumberOfTransactions());
        }

        return txs;
    }

    private static class TransactionBucket {

        final Short id;
        final long gasLimit;
        final List<Transaction> transactions;
        private int numberOfTransactions;
        long gasUsedInBucket;

        public TransactionBucket(Short id, long bucketGasLimit) {
            this.id = id;
            this.gasLimit = bucketGasLimit;
            this.transactions = new ArrayList<>();
            this.numberOfTransactions = 0;
            this.gasUsedInBucket = 0;
        }

        public short getId() {
            return id;
        }

        private void addTransactionAndGas(Transaction tx, long gasUsedByTx) {
            transactions.add(tx);
            gasUsedInBucket = gasUsedInBucket + gasUsedByTx;
            numberOfTransactions++;
        }

        public boolean hasGasAvailable(long txGasLimit) {
            long cumulativeGas = GasCost.add(gasUsedInBucket, txGasLimit);
            return cumulativeGas <= gasLimit;
        }

        public long getGasUsed() {
            return gasUsedInBucket;
        }

        public long getGasLimit() {
            return gasLimit;
        }

        public List<Transaction> getTransactions() {
            return this.transactions;
        }

        public int getNumberOfTransactions() {
            return numberOfTransactions;
        }
    }
}