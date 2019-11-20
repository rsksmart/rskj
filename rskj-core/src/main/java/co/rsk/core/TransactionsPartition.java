package co.rsk.core;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Transaction;

import java.util.*;

public class TransactionsPartition {
//    private static final int NB_MAX_PARTITIONS = 16;
//    private static Map<String, TransactionsPartition> partitionPerThreadGroup = new HashMap<>();
//    private static int[] partitionEnds = new int[0];
    private static int instanceCounter = 0;
//
//    /**
//     * Creates a new instance of TransactionPartition only if there exists less than NB_MAX_PARTITIONS partitions,
//     * otherwise, returns the smallest partition (the one with contains the less transactions)
//     *
//     * @return
//     */
//    public static synchronized TransactionsPartition newTransactionsPartition() {
//        if (partitionPerThreadGroup.size() >= NB_MAX_PARTITIONS) {
//            // we reach the limit of partition so lets choose the partition with less Tx inside.
//            return findSmallestPartition();
//        } else {
//            TransactionsPartition partition = new TransactionsPartition();
//            if (partitionPerThreadGroup.putIfAbsent(partition.threadGroup.getName(), partition) != null) {
//                throw new IllegalStateException(
//                        "Unable to register the new TransactionPartition with ThreadGroup '" +
//                                partition.threadGroup.getName() + "' because there is already another registered partition"
//                );
//            }
//            return partition;
//        }
//    }
//
//
//    private static TransactionsPartition findSmallestPartition() {
//        List<TransactionsPartition> partitions = new ArrayList<>(partitionPerThreadGroup.values());
//        if (partitions.isEmpty()) {
//            return null;
//        }
//        Collections.sort(partitions, new ByIdSorter());
//        Collections.sort(partitions, new BySizeSorter());
//        return partitions.get(0);
//    }
//
//    @VisibleForTesting
//    public static List<TransactionsPartition> getPartitions() {
//        List<TransactionsPartition> partitions = new ArrayList<>(partitionPerThreadGroup.values());
//        if (partitions.isEmpty()) {
//            return new ArrayList<>();
//        }
//        Collections.sort(partitions, new ByIdSorter());
//        return partitions;
//    }
//
//    public static void clearAllPartitions() {
//        partitionPerThreadGroup.clear();
//    }
//
//    public static TransactionsPartition fromThreadGroup(String threadGroupName) {
//        return partitionPerThreadGroup.get(threadGroupName);
//    }
//
//    @VisibleForTesting
//    public static int getNbPartitions() {
//        return partitionPerThreadGroup.size();
//    }
//
//    public static void deletePartition(TransactionsPartition partition) {
//        partitionPerThreadGroup.remove(partition.getThreadGroup().getName());
//    }
//
//    public static List<Transaction> getAllTransactionsSortedPerPartition() {
//        List<Transaction> listTransactions = new ArrayList<>();
//        int indexTx = 0;
//        List<TransactionsPartition> partitions = getPartitions();
//        partitionEnds = new int[partitions.size()];
//        for (int partId = 0; partId < partitions.size(); partId++) {
//            TransactionsPartition partition = partitions.get(partId);
//            Collection<Transaction> txs = partition.getTransactions();
//            listTransactions.addAll(txs);
//            indexTx += txs.size();
//            partitionEnds[partId] = indexTx - 1;
//        }
//        return listTransactions;
//    }
//
//    private static void clearPartitionEnds() {
//        partitionEnds = new int[0];
//    }
//
//    public static int[] getPartitionEnds() {
//        if (partitionEnds.length == 0) {
//            // this will recompute partitionEnds
//            getAllTransactionsSortedPerPartition();
//        }
//        return Arrays.copyOf(partitionEnds, partitionEnds.length);
//    }
//
//    public static TransactionsPartition mergePartitions(Set<TransactionsPartition> conflictingPartitions) {
//        List<TransactionsPartition> listPartitions = new ArrayList<>(conflictingPartitions);
//        // Collections.sort(listPartitions, new ByIdSorter());
//        TransactionsPartition resultingPartition = listPartitions.remove(0);
//        for(TransactionsPartition toMerge: listPartitions) {
//            for (Transaction tx: toMerge.getTransactions()) {
//                resultingPartition.addTransaction(tx);
//            }
//            toMerge.clear();
//            partitionPerThreadGroup.remove(toMerge.getThreadGroup().getName());
//        }
//        return resultingPartition;
//    }

    private ThreadGroup threadGroup;
    private int id;
    private List<Transaction> transactions;

    public TransactionsPartition() {
        this.id = instanceCounter++;
        this.threadGroup = new ThreadGroup("Tx-part-grp-" + id);
        this.transactions = new ArrayList<>();
    }

//    public int getId() {
//        return id;
//    }

    @Override
    public int hashCode() {
        return this.threadGroup.getName().hashCode();
    }

    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    public void addTransaction(Transaction tx) {
        addTransaction(tx, false);
    }

    public void addTransaction(Transaction tx, boolean atBeginning) {
        // clearPartitionEnds(); // force to recompute it because the partitions map has changed
        if (atBeginning) {
            transactions.add(0, tx);
        } else {
            transactions.add(tx);
        }
    }

    public void removeTransaction(Transaction tx) {
        // clearPartitionEnds(); // force to recompute it because the partitions map has changed
        transactions.remove(tx);
    }

    public int size() {
        return transactions.size();
    }

    public Collection<Transaction> getTransactions() {
        return transactions;
    }

    public void clear() {
        transactions.clear();
    }

    public static class BySizeSorter implements Comparator<TransactionsPartition> {
        @Override
        public int compare(TransactionsPartition p1, TransactionsPartition p2) {
            if (p1 == p2) {
                return 0;
            } else if (p1 == null) {
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
            if (p1 == p2) {
                return 0;
            } else if (p1 == null) {
                return 1;
            } else if (p2 == null) {
                return -1;
            } else {
                return p1.id - p2.id;
            }
        }
    }

}