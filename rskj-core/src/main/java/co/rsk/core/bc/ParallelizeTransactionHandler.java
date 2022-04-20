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
    private final HashMap<ByteArrayWrapper, Short> bucketByWrittenKey;
    private final HashMap<ByteArrayWrapper, Set<Short>> bucketByReadKey;
    private final Map<RskAddress, Set<Short>> bucketBySender;
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

    public Optional<Short> addTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
        TransactionBucket bucketCandidate = getBucketCandidates(tx, newReadKeys, newWrittenKeys);
        Optional<Short> addedBucket = bucketCandidate.addTransaction(this, tx, newReadKeys, newWrittenKeys, gasUsedByTx);

        if (!addedBucket.isPresent()) {
            return addedBucket;
        }

        addNewKeysToMaps(tx.getSender(), addedBucket.get(), newReadKeys, newWrittenKeys);
        return addedBucket;
    }

    public Optional<Short> addRemascTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
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

    private void addNewKeysToMaps(RskAddress sender, Short bucketId, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        for (ByteArrayWrapper key : newReadKeys) {
            Set<Short> bucketIds = bucketByReadKey.getOrDefault(key, new HashSet<>());
            bucketIds.add(bucketId);
            bucketByReadKey.put(key, bucketIds);
        }

        Set<Short> bucketIdsBySender = bucketBySender.getOrDefault(sender, new HashSet<>());
        bucketIdsBySender.add(bucketId);
        bucketBySender.put(sender, bucketIdsBySender);

        if (bucketId.equals(getSequentialBucket().getId())) {
            return;
        }

        for (ByteArrayWrapper key: newWrittenKeys) {
            bucketByWrittenKey.putIfAbsent(key, bucketId);
        }
    }

    private Optional<Set<Short>> getBucketBySender(Transaction tx) {
        return Optional.ofNullable(bucketBySender.get(tx.getSender()));
    }

    private Optional<Short> getAvailableBucketWithLessUsedGas(long txGasLimit) {
        long gasUsed = Long.MAX_VALUE;
        Optional<Short> bucketId =  Optional.empty();

        for (TransactionBucket bucket : buckets) {
            if (!bucket.isSequential() && bucket.hasGasAvailable(txGasLimit) && bucket.getGasUsed() < gasUsed) {
                    bucketId = Optional.of(bucket.getId());
                    gasUsed = bucket.getGasUsed();
            }
        }

        return bucketId;
    }


    private TransactionBucket getBucketCandidates(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        Set<Short> bucketCandidates = new HashSet<>();

        getBucketBySender(tx).ifPresent(bucketCandidates::addAll);

        if (bucketCandidates.size() > 1) {
            return getSequentialBucket();
        }

        // read - written
        getCandidatesByReadWriteKeyCollisions(newReadKeys, bucketCandidates);

        if (bucketCandidates.size() > 1) {
            return getSequentialBucket();
        }

        // written - written, written - read
        getCandidatesByWrittenKeysCollisions(newWrittenKeys, bucketCandidates);

        return getTransactionBucket(tx, bucketCandidates);
    }

    private TransactionBucket getTransactionBucket(Transaction tx, Set<Short> bucketCandidates) {
        if (bucketCandidates.size() > 1) {
            return getSequentialBucket();
        } else if (bucketCandidates.size() == 1) {
            return this.buckets.get(bucketCandidates.stream().iterator().next());
        } else {
            Optional<Short> availableBucketWithLessUsedGas = getAvailableBucketWithLessUsedGas(GasCost.toGas(tx.getGasLimit()));
            if (!availableBucketWithLessUsedGas.isPresent()) {
                return getSequentialBucket();
            }

            return this.buckets.get(availableBucketWithLessUsedGas.get());
        }
    }

    private void getCandidatesByWrittenKeysCollisions(Set<ByteArrayWrapper> newWrittenKeys, Set<Short> bucketCandidates) {
        for (ByteArrayWrapper newWrittenKey : newWrittenKeys) {
            if (bucketByWrittenKey.containsKey(newWrittenKey)) {
                bucketCandidates.add(bucketByWrittenKey.get(newWrittenKey));
            }

            if (bucketCandidates.size() > 1) {
                break;
            }

            if (bucketByReadKey.containsKey(newWrittenKey)) {
                bucketCandidates.addAll(bucketByReadKey.get(newWrittenKey));
            }
        }
    }

    private void getCandidatesByReadWriteKeyCollisions(Set<ByteArrayWrapper> newReadKeys, Set<Short> bucketCandidates) {
        for (ByteArrayWrapper newReadKey : newReadKeys) {
            if (bucketByWrittenKey.containsKey(newReadKey)) {
                bucketCandidates.add(bucketByWrittenKey.get(newReadKey));

                if (bucketCandidates.size() > 1) {
                    break;
                }
            }
        }
    }

    private Optional<Short> addInSequentialBucket(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
        return getSequentialBucket().addTransaction(this, tx, newReadKeys, newWrittenKeys, gasUsedByTx);
    }

    private TransactionBucket getSequentialBucket() {
        return this.buckets.get(this.buckets.size()-1);
    }

    private static class TransactionBucket {

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

        private short getId() {
            return id;
        }

        private Optional<Short> addTransaction(ParallelizeTransactionHandler handler, Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {
            if (!this.hasGasAvailable(GasCost.toGas(tx.getGasLimit()))) {
                if (isSequential) {
                    return Optional.empty();
                }
                return handler.addInSequentialBucket(tx, newReadKeys, newWrittenKeys, gasUsedByTx);
            }

            addInternalTx(tx, gasUsedByTx);
            return Optional.of(this.getId());
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
