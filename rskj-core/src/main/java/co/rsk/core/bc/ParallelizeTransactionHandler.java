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
    private final HashMap<ByteArrayWrapper, TransactionSublist> sublistByWrittenKey;
    private final HashMap<ByteArrayWrapper, Set<TransactionSublist>> sublistsByReadKey;
    private final Map<RskAddress, TransactionSublist> sublistBySender;
    private final ArrayList<TransactionSublist> sublists;

    public ParallelizeTransactionHandler(short sublists, long sublistGasLimit) {
        this.sublistBySender = new HashMap<>();
        this.sublistByWrittenKey = new HashMap<>();
        this.sublistsByReadKey = new HashMap<>();
        this.sublists = new ArrayList<>();
        for (short i = 0; i < sublists; i++){
            this.sublists.add(new TransactionSublist(sublistGasLimit, false));
        }
        this.sublists.add(new TransactionSublist(sublistGasLimit, true));
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

    public short[] getTransactionsPerSublistInOrder() {
        List<Short> sublistSizes = new ArrayList<>();
        short sublistEdges = 0;

        for (TransactionSublist sublist: this.sublists) {
            if (sublist.getTransactions().isEmpty() || sublist.isSequential()) {
                continue;
            }
            sublistEdges += sublist.getTransactions().size();
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

    private void addNewKeysToMaps(RskAddress sender, TransactionSublist sublist, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        for (ByteArrayWrapper key : newReadKeys) {
            Set<TransactionSublist> sublistsAlreadyRead = sublistsByReadKey.getOrDefault(key, new HashSet<>());
            sublistsAlreadyRead.add(sublist);
            sublistsByReadKey.put(key, sublistsAlreadyRead);
        }

        if (sublist.isSequential()) {
            sublistBySender.put(sender, sublist);
            return;
        } else {
            sublistBySender.putIfAbsent(sender, sublist);
        }

        for (ByteArrayWrapper key: newWrittenKeys) {
            sublistByWrittenKey.putIfAbsent(key, sublist);
        }
    }

    private Optional<TransactionSublist> getSublistBySender(Transaction tx) {
        return Optional.ofNullable(sublistBySender.get(tx.getSender()));
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

        if (checkIsSequentialSublist(sublistCandidate)) {
            return getSequentialSublist();
        }

        // read - written
        for (ByteArrayWrapper newReadKey : newReadKeys) {
            if (sublistByWrittenKey.containsKey(newReadKey)) {
                TransactionSublist sublist = sublistByWrittenKey.get(newReadKey);

                if (areDifferentSublists(sublistCandidate, sublist)) {
                   return getSequentialSublist();
                }

                sublistCandidate = getTransactionSublistIfNoPresent(sublistCandidate, sublist);
            }
        }

        for (ByteArrayWrapper newWrittenKey : newWrittenKeys) {
            // written - written,
            if (sublistByWrittenKey.containsKey(newWrittenKey)) {
                TransactionSublist sublist = sublistByWrittenKey.get(newWrittenKey);

                if (areDifferentSublists(sublistCandidate, sublist)) {
                    return getSequentialSublist();
                }

                sublistCandidate = getTransactionSublistIfNoPresent(sublistCandidate, sublist);

            }
            // read - written
            if (sublistsByReadKey.containsKey(newWrittenKey)) {
                Set<TransactionSublist> sublist = sublistsByReadKey.get(newWrittenKey);

                if (sublist.size() > 1 || (sublistCandidate.isPresent() && !sublist.contains(sublistCandidate.get()))) {
                    return getSequentialSublist();
                }

                sublistCandidate = Optional.of(sublist.iterator().next());
            }
        }

        return sublistCandidate.orElseGet(() -> getAvailableSublistWithLessUsedGas(GasCost.toGas(tx.getGasLimit())).orElseGet(this::getSequentialSublist));
    }

    private Optional<TransactionSublist> getTransactionSublistIfNoPresent(Optional<TransactionSublist> sublistCandidate, TransactionSublist sublist) {
        if (!sublistCandidate.isPresent()) {
            sublistCandidate = Optional.of(sublist);
        }
        return sublistCandidate;
    }

    private boolean areDifferentSublists(Optional<TransactionSublist> sublistCandidate, TransactionSublist sublist) {
        return sublistCandidate.isPresent() && !sublistCandidate.get().equals(sublist);
    }

    private boolean checkIsSequentialSublist(Optional<TransactionSublist> sublistCandidate) {
        return sublistCandidate.isPresent() && sublistCandidate.get().isSequential();
    }

    private TransactionSublist getSequentialSublist() {
        return this.sublists.get(this.sublists.size()-1);
    }

    public long getGasUsedInSequential() {
        return getSequentialSublist().getGasUsed();
    }

    private static class TransactionSublist {

        final private long gasLimit;
        final private boolean isSequential;
        final private List<Transaction> transactions;
        long gasUsedInSublist;

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
            return this.transactions;
        }

        public boolean isSequential() {
            return isSequential;
        }
    }
}
