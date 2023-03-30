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
    private final HashMap<ByteArrayWrapper, TransactionSublist> sublistsHavingWrittenToKey;
    private final HashMap<ByteArrayWrapper, Set<TransactionSublist>> sublistsHavingReadFromKey;
    private final Map<RskAddress, TransactionSublist> sublistOfSender;
    private final ArrayList<TransactionSublist> sublists;

    public ParallelizeTransactionHandler(short numberOfSublists, long sequentialSublistGasLimit, long parallelSublistGasLimit) {
        this.sublistOfSender = new HashMap<>();
        this.sublistsHavingWrittenToKey = new HashMap<>();
        this.sublistsHavingReadFromKey = new HashMap<>();
        this.sublists = new ArrayList<>();
        for (short i = 0; i < numberOfSublists; i++){
            this.sublists.add(new TransactionSublist(parallelSublistGasLimit, false));
        }
        this.sublists.add(new TransactionSublist(sequentialSublistGasLimit, true));
    }

    public Optional<Long> addTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx) {

        TransactionSublist sublistCandidate = getSublistCandidates(tx, newReadKeys, newWrittenKeys);

        if (!sublistHasAvailableGas(tx, sublistCandidate)) {
            if (sublistCandidate.isSequential()) {
                return Optional.empty();
            }
            sublistCandidate = getSequentialSublist();

            if (!sublistHasAvailableGas(tx, sublistCandidate)) {
                return Optional.empty();
            }
        }

        sublistCandidate.addTransaction(tx, gasUsedByTx);
        addNewKeysToMaps(tx.getSender(), sublistCandidate, newReadKeys, newWrittenKeys);
        return Optional.of(sublistCandidate.getGasUsed());
    }

    private boolean sublistHasAvailableGas(Transaction tx, TransactionSublist sublistCandidate) {
        return sublistCandidate.hasGasAvailable(GasCost.toGas(tx.getGasLimit()));
    }

    public Optional<Long> addRemascTransaction(Transaction tx, long gasUsedByTx) {
        TransactionSublist sequentialSublist = getSequentialSublist();
        sequentialSublist.addTransaction(tx, gasUsedByTx);
        return Optional.of(sequentialSublist.getGasUsed());
    }

    public long getGasUsedIn(Short sublistId) {

        if (sublistId < 0 || sublistId >= sublists.size()) {
            throw new NoSuchElementException();
        }

        return this.sublists.get(sublistId).getGasUsed();
    }

    public List<Transaction> getTransactionsInOrder() {
        List<Transaction> txs = new ArrayList<>();
        for (TransactionSublist sublist: this.sublists) {
            txs.addAll(sublist.getTransactions());
        }
        return txs;
    }

    public List<Short> getTxsPerSublist() {
        List<Short> sublistSizes = new ArrayList<>();
        for (TransactionSublist sublist: this.sublists) {
            if (sublist.isSequential()) {
                continue;
            }
            sublistSizes.add((short) sublist.getTransactions().size());
        }
        return sublistSizes;
    }

    public List<Long> getGasPerSublist() {
        List<Long> sublistGas = new ArrayList<>();
        for (TransactionSublist sublist: this.sublists) {
            if (sublist.isSequential()) {
                continue;
            }
            sublistGas.add(sublist.getGasUsed());
        }
        return sublistGas;
    }

    public short[] getTransactionsPerSublistInOrder() {
        List<Short> sublistSizes = new ArrayList<>();
        short sublistEdges = 0;

        for (TransactionSublist sublist: this.sublists) {
            if (sublist.getTransactions().isEmpty() || sublist.isSequential()) {
                continue;
            }
            sublistEdges += (short) sublist.getTransactions().size();
            sublistSizes.add(sublistEdges);
        }

        short[] sublistOrder = new short[sublistSizes.size()];
        int i = 0;
        for (Short size: sublistSizes) {
            sublistOrder[i] = size;
            i++;
        }

        return sublistOrder;
    }

    public long getGasUsedInSequential() {
        return getSequentialSublist().getGasUsed();
    }

    private void addNewKeysToMaps(RskAddress sender, TransactionSublist sublist, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        for (ByteArrayWrapper key : newReadKeys) {
            Set<TransactionSublist> sublistsAlreadyRead = sublistsHavingReadFromKey.getOrDefault(key, new HashSet<>());
            sublistsAlreadyRead.add(sublist);
            sublistsHavingReadFromKey.put(key, sublistsAlreadyRead);
        }

        if (sublist.isSequential()) {
            sublistOfSender.put(sender, sublist);
        } else {
            sublistOfSender.putIfAbsent(sender, sublist);
        }

        for (ByteArrayWrapper key: newWrittenKeys) {
            sublistsHavingWrittenToKey.put(key, sublist);
        }
    }

    private Optional<TransactionSublist> getSublistBySender(Transaction tx) {
        return Optional.ofNullable(sublistOfSender.get(tx.getSender()));
    }

    private Optional<TransactionSublist> getAvailableSublistWithLessUsedGas(long txGasLimit) {
        long gasUsed = Long.MAX_VALUE;
        Optional<TransactionSublist> sublistCandidate =  Optional.empty();

        for (TransactionSublist sublist : sublists) {
            if (!sublist.isSequential() && sublist.hasGasAvailable(txGasLimit) && sublist.getGasUsed() < gasUsed) {
                    sublistCandidate = Optional.of(sublist);
                    gasUsed = sublist.getGasUsed();
            }
        }

        return sublistCandidate;
    }

    private TransactionSublist getSublistCandidates(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        Optional<TransactionSublist> sublistCandidate = getSublistBySender(tx);

        if (sublistCandidate.isPresent() && sublistCandidate.get().isSequential()) {
            // there is a tx with the same sender in the sequential sublist
            return sublistCandidate.get();
        }

        // analyze reads
        for (ByteArrayWrapper newReadKey : newReadKeys) {
            // read - written
            if (sublistsHavingWrittenToKey.containsKey(newReadKey)) {
                TransactionSublist sublist = sublistsHavingWrittenToKey.get(newReadKey);

                if (sublist.isSequential()) {
                    // there is a tx with read-written collision in sequential sublist
                    return sublist;
                }
                if (!sublistCandidate.isPresent()) {
                    // this is the new candidate
                    sublistCandidate = Optional.of(sublist);
                } else if (!sublistCandidate.get().equals(sublist)) {
                    // use the sequential sublist (greedy decision)
                    return getSequentialSublist();
                }
            }
        }

        // analyze writes
        for (ByteArrayWrapper newWrittenKey : newWrittenKeys) {
            // write - written
            if (sublistsHavingWrittenToKey.containsKey(newWrittenKey)) {
                TransactionSublist sublist = sublistsHavingWrittenToKey.get(newWrittenKey);

                if (sublist.isSequential()) {
                    // there is a tx with write-written collision in sequential sublist
                    return sublist;
                }
                if (!sublistCandidate.isPresent()) {
                    // this is the new candidate
                    sublistCandidate = Optional.of(sublist);
                } else if (!sublistCandidate.get().equals(sublist)) {
                    // use the sequential sublist (greedy decision)
                    return getSequentialSublist();
                }
            }

            // write - read
            if (sublistsHavingReadFromKey.containsKey(newWrittenKey)) {
                Set<TransactionSublist> sublists = sublistsHavingReadFromKey.get(newWrittenKey);
                if (sublists.size() > 1) {
                    // there is a write-read collision with multiple sublists
                    return getSequentialSublist();
                }

                // there is only one colluded sublist
                TransactionSublist sublist = getNextSublist(sublists);
                if (!sublistCandidate.isPresent()) {
                    // if there is no candidate, take the colluded sublist
                    sublistCandidate = Optional.of(sublist);
                } else if (!sublistCandidate.get().equals(sublist)) {
                    // otherwise, check if the sublist is different from the candidate and return the sequential
                    return getSequentialSublist();
                }
            }
        }

        // if there is no candidate use the sublist with more gas available
        // if the is no more gas available in any parallel sublist use the sequential
        return sublistCandidate
                .orElseGet(() -> getAvailableSublistWithLessUsedGas(GasCost.toGas(tx.getGasLimit()))
                        .orElseGet(this::getSequentialSublist));
    }

    private TransactionSublist getNextSublist(Set<TransactionSublist> sublist) {
        return sublist.iterator().next();
    }

    private TransactionSublist getSequentialSublist() {
        return this.sublists.get(this.sublists.size()-1);
    }

    public int getTxInSequential() {
        return this.getSequentialSublist().getTransactions().size();
    }

    public int getTxInParallel() {
        int transactionsInParallelSublist = 0;
        for (int i = 0; i < this.sublists.size()-1; i++) {
            transactionsInParallelSublist += this.sublists.get(i).getTransactions().size();
        }

        return transactionsInParallelSublist;
    }

    private static class TransactionSublist {

        private final long gasLimit;
        private final boolean isSequential;
        private final List<Transaction> transactions;
        private long gasUsedInSublist;

        public TransactionSublist(long sublistGasLimit, boolean isSequential) {
            this.gasLimit = sublistGasLimit;
            this.isSequential = isSequential;
            this.transactions = new ArrayList<>();
            this.gasUsedInSublist = 0;
        }

        private void addTransaction(Transaction tx, long gasUsedByTx) {
            transactions.add(tx);
            gasUsedInSublist = gasUsedInSublist + gasUsedByTx;
        }

        private boolean hasGasAvailable(long txGasLimit) {
            //TODO(JULI): Re-check a thousand of times this line.
            long cumulativeGas = GasCost.add(gasUsedInSublist, txGasLimit);
            return cumulativeGas <= gasLimit;
        }

        public long getGasUsed() {
            return gasUsedInSublist;
        }

        public List<Transaction> getTransactions() {
            return new ArrayList<>(this.transactions);
        }

        public boolean isSequential() {
            return isSequential;
        }
    }
}
