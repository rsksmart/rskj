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

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ParallelizeTransactionHandler {
    private final HashMap<ByteArrayWrapper, TransactionSublist> sublistsHavingWrittenToKey;
    private final HashMap<ByteArrayWrapper, Set<TransactionSublist>> sublistsHavingReadFromKey;
    private final Map<RskAddress, TransactionSublist> sublistOfSender;
    private final ArrayList<TransactionSublist> sublists;
    private boolean collided;

    public ParallelizeTransactionHandler(short numberOfSublists, long sequentialSublistGasLimit, long parallelSublistGast) {
        this.sublistOfSender = new HashMap<>();
        this.sublistsHavingWrittenToKey = new HashMap<>();
        this.sublistsHavingReadFromKey = new HashMap<>();
        this.sublists = new ArrayList<>();
        for (short i = 0; i < numberOfSublists; i++){
            this.sublists.add(new TransactionSublist(i, parallelSublistGast, false));
        }
        this.sublists.add(new TransactionSublist(-1, sequentialSublistGasLimit, true));
        this.collided = false;
    }

    public Optional<Long> addTransaction(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long gasUsedByTx, long blockNumber) {
        String filePath = "/Users/julianlen/workspace/output-experiments/collisions.txt";
        TransactionSublist sublistCandidate;

        try {
            FileWriter myWriter = new FileWriter(filePath, true);
            sublistCandidate = getSublistCandidates(tx, newReadKeys, newWrittenKeys, blockNumber, myWriter);
            myWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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

    public long getGasUsedInSequential() {
        return getSequentialSublist().getGasUsed();
    }

    public boolean getCollision() {
        return this.collided;
    }
    private void addNewKeysToMaps(RskAddress sender, TransactionSublist sublist, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys) {
        for (ByteArrayWrapper key : newReadKeys) {
            Set<TransactionSublist> sublistsAlreadyRead = sublistsHavingReadFromKey.getOrDefault(key, new HashSet<>());
            sublistsAlreadyRead.add(sublist);
            sublistsHavingReadFromKey.put(key, sublistsAlreadyRead);
        }

        if (sublist.isSequential()) {
            sublistOfSender.put(sender, sublist);
            return;
        } else {
            sublistOfSender.putIfAbsent(sender, sublist);
        }

        for (ByteArrayWrapper key: newWrittenKeys) {
            sublistsHavingWrittenToKey.putIfAbsent(key, sublist);
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


    private TransactionSublist getSublistCandidates(Transaction tx, Set<ByteArrayWrapper> newReadKeys, Set<ByteArrayWrapper> newWrittenKeys, long blockNumber, FileWriter myWriter) {
        Optional<TransactionSublist> sublistCandidate = getSublistBySender(tx);
        String result = "";
        if (blockNumber == 2) {
            result = result.concat("====================== BN:" + blockNumber + ",TN:" + tx.getHash().toHexString() + "========================\r");
        }
        if (blockNumber == 2 && sublistCandidate.isPresent() && !sublistCandidate.get().isSequential()) {
            result = result.concat("====================== COLLISION BY SENDER ========================\r");
            result = result.concat(tx.getSender().toHexString() + "\r");
        }

        if (sublistCandidate.isPresent() && sublistCandidate.get().isSequential()) {
            return getSequentialSublist();
        }

        // read - written
        for (ByteArrayWrapper newReadKey : newReadKeys) {
            if (sublistsHavingWrittenToKey.containsKey(newReadKey)) {
                TransactionSublist sublist = sublistsHavingWrittenToKey.get(newReadKey);
                sublistCandidate = Optional.of(sublistCandidate.map(sc -> returnsSequentialIfBothAreDifferent(sc, sublist)).orElse(sublist));
                if (blockNumber == 2) {
                    result = result.concat("====================== nR-W ========================\r");
                    result = result.concat(newReadKey.toString()+"\r");
                }
            }
        }

        if (sublistCandidate.isPresent() && sublistCandidate.get().isSequential()) {
            return sublistCandidate.get();
        }

        for (ByteArrayWrapper newWrittenKey : newWrittenKeys) {
            // written - written,
            if (sublistsHavingWrittenToKey.containsKey(newWrittenKey)) {
                TransactionSublist sublist = sublistsHavingWrittenToKey.get(newWrittenKey);
                sublistCandidate = Optional.of(sublistCandidate.map(sc -> returnsSequentialIfBothAreDifferent(sc, sublist)).orElse(sublist));
                if (blockNumber == 2) {
                    result = result.concat("====================== nW-W ========================\r");
                    result = result.concat(newWrittenKey.toString()+"\r");
                }
            }

            if (sublistCandidate.isPresent() && sublistCandidate.get().isSequential()) {
                return sublistCandidate.get();
            }
            // read - written
            if (sublistsHavingReadFromKey.containsKey(newWrittenKey)) {
                Set<TransactionSublist> sublist = sublistsHavingReadFromKey.get(newWrittenKey);
                if (blockNumber == 2) {
                    result = result.concat("====================== nW-R ========================\r");
                    result = result.concat(newWrittenKey.toString()+"\r");
                }

                if (sublist.size() > 1) {
                    this.collided = true;
                    return getSequentialSublist();
                }

                sublistCandidate = Optional.of(sublistCandidate.map(sc -> getTransactionSublistForReadKeys(sublist, sc)).orElse(getNextSublist(sublist)));
            }
        }

        if (sublistCandidate.isPresent()) {
            try {
                if (blockNumber == 2){
                    result = result.concat("====================== BUCKET:" +  sublistCandidate.get().getName() + "========================\r\r");
                }
                myWriter.write(result);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return sublistCandidate.orElseGet(() -> getAvailableSublistWithLessUsedGas(GasCost.toGas(tx.getGasLimit())).orElseGet(this::getSequentialSublist));
    }

    private TransactionSublist getTransactionSublistForReadKeys(Set<TransactionSublist> sublist, TransactionSublist sc) {
        if (!sublist.contains(sc)) {
            return getSequentialSublist();
        }

        return getNextSublist(sublist);
    }

    private TransactionSublist returnsSequentialIfBothAreDifferent(TransactionSublist sublist1, TransactionSublist sublist2) {
        if (!sublist1.equals(sublist2)) {
            this.collided = true;
            return getSequentialSublist();
        }
        return sublist1;
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

        private long name;
        private final long gasLimit;
        private final boolean isSequential;
        private final List<Transaction> transactions;
        private long gasUsedInSublist;

        public TransactionSublist(long name, long sublistGasLimit, boolean isSequential) {
            this.name = name;
            this.gasLimit = sublistGasLimit;
            this.isSequential = isSequential;
            this.transactions = new ArrayList<>();
            this.gasUsedInSublist = 0;
        }

        private long getName() {
            return this.name;
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
