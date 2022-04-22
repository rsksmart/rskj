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
    private final HashMap<ByteArrayWrapper, TransactionBucket> bucketByWrittenKey;
    private final HashMap<ByteArrayWrapper, Set<TransactionBucket>> bucketByReadKey;
    private final Map<RskAddress, TransactionBucket> bucketBySender;
    private final ArrayList<TransactionBucket> buckets;

    public ParallelizeTransactionHandler(short buckets, long bucketGasLimit) {
        this.bucketBySender = new HashMap<>();
        this.bucketByWrittenKey = new HashMap<>();
        this.bucketByReadKey = new HashMap<>();
        this.buckets = new ArrayList<>();
        for (short i = 0; i < buckets; i++){
            this.buckets.add(new TransactionBucket(i, bucketGasLimit, false));
        }
        this.buckets.add(new TransactionBucket(buckets, bucketGasLimit, true));
    }

    public Optional<TransactionBucket> addTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
        TransactionBucket bucketCandidate = getBucketCandidates(tx, newReadKeys, newWrittenKeys);
        Optional<TransactionBucket> addedBucket = bucketCandidate.addTransaction(this, tx, newReadKeys, newWrittenKeys, gasUsedByTx);

        if (!addedBucket.isPresent()) {
            return addedBucket;
        }

        addNewKeysToMaps(tx.getSender(), addedBucket.get(), newReadKeys, newWrittenKeys);
        return addedBucket;
    }

    public Optional<TransactionBucket> addRemascTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
        return getSequentialBucket().addTransaction(this, tx, newReadKeys, newWrittenKeys, gasUsedByTx);
    }

    public long getGasUsedIn(Short bucketId) {

        if (bucketId < 0 || bucketId >= buckets.size()) {
            throw new NoSuchElementException();
        }

        return this.buckets.get(bucketId).getGasUsed();
    }

    public List<Transaction> getTransactionsInOrder() {
        List<Transaction> txs = new ArrayList<>();
        for (TransactionBucket bucket: this.buckets) {
            txs.addAll(bucket.getTransactions());
        }
        return txs;
    }

    public short[] getTransactionsPerBucketInOrder() {
        List<Short> bucketSizes = new ArrayList<>();
        short bucketEdges = 0;

        for (TransactionBucket bucket: this.buckets) {
            if (bucket.getTransactions().isEmpty() || bucket.isSequential()) {
                continue;
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

    private void addNewKeysToMaps(RskAddress sender, TransactionBucket bucket, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        for (ByteArrayWrapper key : newReadKeys) {
            Set<TransactionBucket> bucketsAlreadyRead = bucketByReadKey.getOrDefault(key, new HashSet<>());
            bucketsAlreadyRead.add(bucket);
            bucketByReadKey.put(key, bucketsAlreadyRead);
        }

        if (bucket.isSequential() || !bucketBySender.containsKey(sender)) {
            bucketBySender.put(sender, bucket);
        }

        if (bucket.equals(getSequentialBucket())) {
            return;
        }

        for (ByteArrayWrapper key: newWrittenKeys) {
            bucketByWrittenKey.putIfAbsent(key, bucket);
        }
    }

    private Optional<TransactionBucket> getBucketBySender(Transaction tx) {
        return Optional.ofNullable(bucketBySender.get(tx.getSender()));
    }

    private Optional<TransactionBucket> getAvailableBucketWithLessUsedGas(long txGasLimit) {
        long gasUsed = Long.MAX_VALUE;
        Optional<TransactionBucket> bucketCandidate =  Optional.empty();

        for (TransactionBucket bucket : buckets) {
            if (!bucket.isSequential() && bucket.hasGasAvailable(txGasLimit) && bucket.getGasUsed() < gasUsed) {
                    bucketCandidate = Optional.of(bucket);
                    gasUsed = bucket.getGasUsed();
            }
        }

        return bucketCandidate;
    }


    private TransactionBucket getBucketCandidates(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        Optional<TransactionBucket> bucketCandidate = getBucketBySender(tx);

        if (bucketCandidate.isPresent() && bucketCandidate.get().isSequential()) {
            return getSequentialBucket();
        }

        // read - written
        for (ByteArrayWrapper newReadKey : newReadKeys) {
            if (bucketByWrittenKey.containsKey(newReadKey)) {
                TransactionBucket bucket = bucketByWrittenKey.get(newReadKey);

                if (bucketCandidate.isPresent()) {
                    if(!bucketCandidate.get().equals(bucket)) {
                       return getSequentialBucket();
                    }
                } else {
                    bucketCandidate = Optional.of(bucket);
                }
            }
        }

        for (ByteArrayWrapper newWrittenKey : newWrittenKeys) {
            // written - written,
            if (bucketByWrittenKey.containsKey(newWrittenKey)) {
                TransactionBucket bucket = bucketByWrittenKey.get(newWrittenKey);

                if (bucketCandidate.isPresent()) {
                    if(!bucketCandidate.get().equals(bucket)) {
                        return getSequentialBucket();
                    }
                } else {
                    bucketCandidate = Optional.of(bucket);
                }
            }
            // read - written
            if (bucketByReadKey.containsKey(newWrittenKey)) {
                Set<TransactionBucket> bucketsWritten = bucketByReadKey.get(newWrittenKey);

                if (bucketsWritten.size() > 1) {
                    return getSequentialBucket();
                }

                if (bucketCandidate.isPresent()) {
                    if(!bucketsWritten.contains(bucketCandidate.get())) {
                        return getSequentialBucket();
                    }
                } else {
                    bucketCandidate = Optional.of(bucketsWritten.iterator().next());
                }
            }
        }

        return getTransactionBucket(tx, bucketCandidate);
    }

    private TransactionBucket getTransactionBucket(Transaction tx, Optional<TransactionBucket> bucketCandidate) {
        if (bucketCandidate.isPresent()) {
            return bucketCandidate.get();
        }

        Optional<TransactionBucket> availableBucketWithLessUsedGas = getAvailableBucketWithLessUsedGas(GasCost.toGas(tx.getGasLimit()));
        return availableBucketWithLessUsedGas.orElseGet(this::getSequentialBucket);

    }

    private Optional<TransactionBucket> addInSequentialBucket(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
        return getSequentialBucket().addTransaction(this, tx, newReadKeys, newWrittenKeys, gasUsedByTx);
    }

    private TransactionBucket getSequentialBucket() {
        return this.buckets.get(this.buckets.size()-1);
    }

    public static class TransactionBucket {

        final Short id;
        final long gasLimit;
        final boolean isSequential;
        final List<Transaction> transactions;
        long gasUsedInBucket;

        public TransactionBucket(Short id, long bucketGasLimit, boolean isSequential) {
            this.id = id;
            this.gasLimit = bucketGasLimit;
            this.isSequential = isSequential;
            this.transactions = new ArrayList<>();
            this.gasUsedInBucket = 0;
        }

        public short getId() {
            return id;
        }

        private Optional<TransactionBucket> addTransaction(ParallelizeTransactionHandler handler, Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
            if (!this.hasGasAvailable(GasCost.toGas(tx.getGasLimit()))) {
                if (isSequential) {
                    return Optional.empty();
                }
                return handler.addInSequentialBucket(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
            }

            addInternalTx(tx, gasUsedByTx);
            return Optional.of(this);
        }

        private void addInternalTx(Transaction tx, long gasUsedByTx) {
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

        public List<Transaction> getTransactions() {
            return this.transactions;
        }

        public boolean isSequential() {
            return isSequential;
        }
    }
}
