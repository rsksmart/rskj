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
    private final TransactionBucket sequentialBucket;
    private final HashMap<ByteArrayWrapper, Short> bucketByWrittenKey;
    private final HashMap<ByteArrayWrapper, Set<Short>> bucketByReadKey;
    private final Map<RskAddress, Short> bucketBySender;
    private final List<TransactionBucket> parallelBuckets;

    public ParallelizeTransactionHandler(short buckets, long blockGasLimit) {
        long gasLimit = blockGasLimit/2;
        this.bucketBySender = new HashMap<>();
        this.bucketByWrittenKey = new HashMap<>();
        this.bucketByReadKey = new HashMap<>();
        this.parallelBuckets = new ArrayList<>();
        for (short i = 0; i < buckets; i++){
            this.parallelBuckets.add(new TransactionBucket(i, gasLimit));
        }
        this.sequentialBucket = new TransactionBucket(buckets, gasLimit);
    }

    public boolean sequentialBucketHasGasAvailable(Transaction tx) {
        long txGasLimit = GasCost.toGas(tx.getGasLimit());
        return this.sequentialBucket.hasGasAvailable(txGasLimit);
    }

    public Optional<Short> addTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
        Set<Short> bucketCandidates = getBucketCandidates(tx, newReadKeys, newWrittenKeys);
        long txGasLimit = GasCost.toGas(tx.getGasLimit());

        if (bucketCandidates.size() > 1) {
            return addTransactionInSequentialBucketAndGetId(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
        }

        if (bucketCandidates.size() == 1) {
            Short bucketIdNumber = bucketCandidates.iterator().next();
            TransactionBucket transactionBucket = this.parallelBuckets.get(bucketIdNumber);

            if (!transactionBucket.hasGasAvailable(txGasLimit)) {
                return addTransactionInSequentialBucketAndGetId(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
            }

            addTransactionInternal(tx, transactionBucket, gasUsedByTx, bucketIdNumber, newReadKeys, newWrittenKeys);
            return Optional.of(bucketIdNumber);
        }

        Optional<Short> bucketId = getAvailableBucketWithLessUsedGas(txGasLimit);

        if (!bucketId.isPresent()) {
            return addTransactionInSequentialBucketAndGetId(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
        }

        Short bucketIdNumber = bucketId.get();
        addTransactionInternal(tx, this.parallelBuckets.get(bucketIdNumber), gasUsedByTx, bucketIdNumber, newReadKeys, newWrittenKeys);
        return bucketId;
    }

    public Optional<Short> addRemascTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
        return addTransactionInSequentialBucketAndGetId(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
    }

    public long getGasUsedIn(Short bucketId) {
        //TODO(Juli): If the bucketId doesnt exist, should it throw an error?
        if (bucketId == this.sequentialBucket.getId()) {
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

    public short[] getTransactionsPerBucketInOrder() {
        List<Short> bucketSizes = new ArrayList<>();
        short bucketEdges = 0;

        for (TransactionBucket bucket: this.parallelBuckets) {
            if (bucket.getTransactions().isEmpty()) {
                break;
            }
            bucketEdges += bucket.getTransactions().size();
            bucketSizes.add(bucketEdges);
        }

        short[] bucketOrder = new short[bucketSizes.size()];
        int i = 0;
        for (Short size: bucketSizes) {
            bucketOrder[i] = size;
            i++;
        }

        return bucketOrder;
    }

    private void addTransactionInternal(Transaction tx, TransactionBucket transactionBucket, long gasUsedByTx, Short bucketId, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        addNewKeysToMaps(tx.getSender(), bucketId, newReadKeys, newWrittenKeys);
        transactionBucket.addTransactionAndGas(tx, gasUsedByTx);
    }

    private Optional<Short> addTransactionInSequentialBucketAndGetId(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
        addTransactionInternal(tx, this.sequentialBucket, gasUsedByTx, this.sequentialBucket.getId(), newReadKeys, newWrittenKeys);
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

        bucketBySender.put(sender, bucketId);
    }

    private Optional<Short> getBucketBySender(Transaction tx) {
        return Optional.ofNullable(bucketBySender.get(tx.getSender()));
    }

    private Optional<Short> getAvailableBucketWithLessUsedGas(long txGasLimit) {
        long gasUsed = Long.MAX_VALUE;
        Optional<Short> bucketId =  Optional.empty();

        for (TransactionBucket bucket : parallelBuckets) {
            if (bucket.hasGasAvailable(txGasLimit)) {
                if (bucket.getGasUsed() < gasUsed) {
                    bucketId = Optional.of(bucket.getId());
                    gasUsed = bucket.getGasUsed();
                }
            }
        }
        return bucketId;
    }


    private Set<Short> getBucketCandidates(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        Set<Short> bucketCandidates = new HashSet<>();

        getBucketBySender(tx).ifPresent(bucketCandidates::add);

        // read - written
        for (ByteArrayWrapper newReadKey : newReadKeys) {
            if (bucketByWrittenKey.containsKey(newReadKey)) {
                bucketCandidates.add(bucketByWrittenKey.get(newReadKey));
            }
        }

        // written - read
        // written - written
        for (ByteArrayWrapper newWrittenKey : newWrittenKeys) {
            if (bucketByWrittenKey.containsKey(newWrittenKey)) {
                bucketCandidates.add(bucketByWrittenKey.get(newWrittenKey));
            }

            if (bucketByReadKey.containsKey(newWrittenKey)) {
                bucketCandidates.addAll(bucketByReadKey.get(newWrittenKey));
            }
        }


        return bucketCandidates;
    }

    private static class TransactionBucket {

        final Short id;
        final long gasLimit;
        final List<Transaction> transactions;
        long gasUsedInBucket;

        public TransactionBucket(Short id, long bucketGasLimit) {
            this.id = id;
            this.gasLimit = bucketGasLimit;
            this.transactions = new ArrayList<>();
            this.gasUsedInBucket = 0;
        }

        private short getId() {
            return id;
        }

        private void addTransactionAndGas(Transaction tx, long gasUsedByTx) {
            transactions.add(tx);
            gasUsedInBucket = gasUsedInBucket + gasUsedByTx;
        }

        private boolean hasGasAvailable(long txGasLimit) {
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
    }
}
