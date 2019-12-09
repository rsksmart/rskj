package co.rsk.core;

import org.ethereum.core.Transaction;

import java.util.*;

public class TransactionsPartition {
    private static int instanceCounter = 0;

    private int id;
    private List<Transaction> transactions;
    private TransactionsPartitioner partitioner;

    public TransactionsPartition(TransactionsPartitioner partitioner) {
        this.id = instanceCounter++;
        this.partitioner = partitioner;
        this.transactions = new LinkedList<>();
    }

    public int getId() {
        return id;
    }

    public void addTransaction(Transaction tx) {
        addTransaction(tx, false);
    }

    public void addTransaction(Transaction tx, boolean atBeginning) {
        partitioner.resetPartitionEnds();
        if (atBeginning) {
            transactions.add(0, tx);
        } else {
            transactions.add(tx);
        }
    }

    public void removeTransaction(Transaction tx) {
        partitioner.resetPartitionEnds();
        transactions.remove(tx);
    }

    public int size() {
        return transactions.size();
    }

    public Collection<Transaction> getTransactions() {
        return new LinkedList<>(transactions);
    }

    public void clear() {
        partitioner.resetPartitionEnds();
        transactions.clear();
    }

    public static class BySizeSorter implements Comparator<TransactionsPartition> {
        @Override
        public int compare(TransactionsPartition p1, TransactionsPartition p2) {
            if (p1 == null) {
                return 1;
            } else if (p2 == null) {
                return -1;
            } else {
                return p1.size() - p2.size();
            }
        }
    }

    public static class ByIdSorter implements Comparator<TransactionsPartition> {
        @Override
        public int compare(TransactionsPartition p1, TransactionsPartition p2) {
            if (p1 == null) {
                return 1;
            } else if (p2 == null) {
                return -1;
            } else {
                return p1.id - p2.id;
            }
        }
    }

}