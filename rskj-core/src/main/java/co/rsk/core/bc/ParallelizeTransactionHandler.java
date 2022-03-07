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
    private final HashMap<ByteArrayWrapper, Short> bucketByReadKey;
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

    public Optional<Short> addTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {

        // FALTA QUE REMASC SE AGREGUE DIRECTO AL SECUENCIAL

        Set<Short> bucketCandidates = getBucketCandidates(newReadKeys, newWrittenKeys);
        long txGasLimit = GasCost.toGas(tx.getGasLimit());

        if (bucketCandidates.size() > 1) {
            addTxToSequentialBucket(tx, gasUsedByTx);
            return Optional.of(SEQUENTIAL_BUCKET_ID);
        }

        Optional<Short> bucketId = getBucketBySender(tx);

        if (bucketId.isPresent()) {
            if (bucketCandidates.size() == 1 && !bucketCandidates.contains(bucketId.get())) {
                addTxToSequentialBucket(tx, gasUsedByTx);
                return Optional.of(SEQUENTIAL_BUCKET_ID);
            }

            TransactionBucket transactionBucket = this.parallelBuckets.get(bucketId.get());

            if (!transactionBucket.hasGasAvailable(txGasLimit)) {
                addTxToSequentialBucket(tx, gasUsedByTx);
                return Optional.of(SEQUENTIAL_BUCKET_ID);
            }

            transactionBucket.add(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
            return bucketId;
        }

        if (bucketCandidates.size() == 1) {
            bucketId = Optional.of(bucketCandidates.iterator().next());
        } else {
            bucketId = getFirstAvailableBucket(txGasLimit);
        }

        if (!bucketId.isPresent()) {
            addTxToSequentialBucket(tx, gasUsedByTx);
            return Optional.of(SEQUENTIAL_BUCKET_ID);
        }

        TransactionBucket transactionBucket = this.parallelBuckets.get(bucketId.get());
        transactionBucket.add(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
        return bucketId;
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

    private void addTxToSequentialBucket(Transaction tx, long gasUsedByTx) {
//        if (!sequentialBucket.hasGasAvailable(txGasLimit)) {
//            return false;
//        }

        this.sequentialBucket.add(tx, gasUsedByTx);
//        return true;
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
                bucketPerTx.add(bucketByReadKey.get(newWrittenKey));
            }
        }
        return bucketPerTx;
    }

    private static class TransactionBucket {

        final Short id;
        final long gasLimit;
        final List<Transaction> transactions;
        final Set<ByteArrayWrapper> readKeys;
        final Set<ByteArrayWrapper> writtenKeys;
        long gasUsedInBucket;

        public TransactionBucket(Short id, long bucketGasLimit) {
            this.id = id;
            this.gasLimit = bucketGasLimit;
            this.transactions = new ArrayList<>();
            this.gasUsedInBucket = 0;
            this.readKeys = new HashSet<>();
            this.writtenKeys = new HashSet<>();
        }

        public short getId() {
            return id;
        }

        private void add(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
            addTransactionAndGas(tx, gasUsedByTx);
            readKeys.addAll(newReadKeys);
            writtenKeys.addAll(newWrittenKeys);
        }

        private void add(Transaction tx, long gasUsedByTx) {
            addTransactionAndGas(tx, gasUsedByTx);
        }

        private void addTransactionAndGas(Transaction tx, long gasUsedByTx) {
            transactions.add(tx);
            gasUsedInBucket = gasUsedInBucket + gasUsedByTx;
        }

        public boolean hasGasAvailable(long txGasLimit) {
            long cumulativeGas = GasCost.add(gasUsedInBucket, txGasLimit);
            return cumulativeGas <= gasLimit;
        }

        public long getGasUsed() {
            return gasUsedInBucket;
        }
    }
}