package co.rsk.core;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class TransactionsPartitioner {

    private static final int NB_MAX_PARTITIONS = 16;
    private Map<Integer, TransactionsPartition> partitionPerPartId = new HashMap<>();
    private int[] partitionEnds = new int[0];

    public List<Transaction> getAllTransactionsSortedPerPartition() {
        List<Transaction> listTransactions = new ArrayList<>();
        int indexTx = 0;
        List<TransactionsPartition> partitions = getNotEmptyPartitions();
        partitionEnds = new int[partitions.size()];
        for (int partId = 0; partId < partitions.size(); partId++) {
            TransactionsPartition partition = partitions.get(partId);
            Collection<Transaction> txs = partition.getTransactions();
            listTransactions.addAll(txs);
            indexTx += txs.size();
            partitionEnds[partId] = indexTx - 1;
        }
        return listTransactions;
    }

    public int[] getPartitionEnds() {
        if (partitionEnds.length == 0) {
            // this will recompute partitionEnds
            getAllTransactionsSortedPerPartition();
        }
        return Arrays.copyOf(partitionEnds, partitionEnds.length);
    }

    public TransactionsPartition newPartition() {
        if (partitionPerPartId.size() >= NB_MAX_PARTITIONS) {
            // we reach the limit of partition so lets choose the partition with less Tx inside.
            return findSmallestPartition();
        } else {
            TransactionsPartition partition = new TransactionsPartition(this);
            if (partitionPerPartId.putIfAbsent(partition.getId(), partition) != null) {
                throw new IllegalStateException(
                        "Unable to register the new TransactionPartition with Id '" +
                                partition.getId() + "' because there is already another registered partition"
                );
            }
            resetPartitionEnds();
            return partition;
        }
    }

    private List<TransactionsPartitionExecutor> partExecutors = new ArrayList<>();


    public TransactionsPartitionExecutor newPartitionExecutor(TransactionsPartition partition) {
        TransactionsPartitionExecutor partExecutor = new TransactionsPartitionExecutor(partition);
        partExecutors.add(partExecutor);
        return partExecutor;
    }

    public void waitForAllThreadTermination(int timeoutMSec, TransactionsPartitionExecutor.PeriodicCheck periodicCheck)
            throws TimeoutException, ExecutionException, TransactionsPartitionExecutor.PeriodicCheckException {
        while (!partExecutors.isEmpty()) {
            // As soon as there are futures to wait for, wait for the first of each thread
            List<TransactionsPartitionExecutor> toRemove = new ArrayList<>();
            for (TransactionsPartitionExecutor partExecutor : partExecutors) {
                if (partExecutor.hasNextResult()) {
                    try {
                        Optional<TransactionReceipt> receipt = partExecutor.waitForNextResult(timeoutMSec);
                    } catch (TimeoutException e) {
                        clearExecutors();
                        throw new TimeoutException();
                    } catch (RuntimeException e) {
                        clearExecutors();
                        throw new ExecutionException(e);
                    }
                    if (periodicCheck != null) {
                        try {
                            periodicCheck.check();
                            // raises an exception when check fails
                        } catch (Exception e) {
                            throw new TransactionsPartitionExecutor.PeriodicCheckException(e);
                        }
                    }
                } else {
                    toRemove.add(partExecutor);
                }
            }
            partExecutors.removeAll(toRemove);
        }
    }

    private synchronized void clearExecutors() {
        for (TransactionsPartitionExecutor partExecutor : partExecutors) {
            partExecutor.shutdownNow();
        }
        partExecutors.clear();
    }

    public TransactionsPartition mergePartitions(Set<TransactionsPartition> conflictingPartitions) {
        List<TransactionsPartition> listPartitions = new ArrayList<>(conflictingPartitions);
        Collections.sort(listPartitions, new Comparator<TransactionsPartition>() {
            @Override
            public int compare(TransactionsPartition p1, TransactionsPartition p2) {
                if (p1 == p2) {
                    return 0;
                }
                if (p1 == null) {
                    return -1;
                }
                if (p2 == null) {
                    return 1;
                }
                return p1.getId() - p2.getId();
            }
        });
        // Collections.sort(listPartitions, new ByIdSorter());
        TransactionsPartition resultingPartition = listPartitions.remove(0);
        for (TransactionsPartition toMerge : listPartitions) {
            for (Transaction tx : toMerge.getTransactions()) {
                resultingPartition.addTransaction(tx);
            }
            toMerge.clear();
            partitionPerPartId.remove(toMerge.getId());
        }
        resetPartitionEnds();
        return resultingPartition;
    }

    @VisibleForTesting
    public List<TransactionsPartition> getPartitions() {
        List<TransactionsPartition> partitions = new ArrayList<>(partitionPerPartId.values());
        if (partitions.isEmpty()) {
            return new ArrayList<>();
        }
        Collections.sort(partitions, new TransactionsPartition.ByIdSorter());
        return partitions;
    }

    @VisibleForTesting
    public List<TransactionsPartition> getNotEmptyPartitions() {
        List<TransactionsPartition> partitions = new ArrayList<>(partitionPerPartId.values());
        partitions = partitions.stream().filter(partition -> partition.size() > 0).collect(Collectors.toList());
        if (partitions.isEmpty()) {
            return new ArrayList<>();
        }
        Collections.sort(partitions, new TransactionsPartition.ByIdSorter());
        return partitions;
    }

    @VisibleForTesting
    public int getNbPartitions() {
        return partitionPerPartId.size();
    }

    public void clearAllPartitions() {
        partitionPerPartId.clear();
        resetPartitionEnds();
    }

    public TransactionsPartition fromId(int partId) {
        return partitionPerPartId.get(partId);
    }

    public void deletePartition(TransactionsPartition partition) {
        partitionPerPartId.remove(partition.getId());
        resetPartitionEnds();
    }

    public void resetPartitionEnds() {
        partitionEnds = new int[0];
    }

    private TransactionsPartition findSmallestPartition() {
        List<TransactionsPartition> partitions = new ArrayList<>(partitionPerPartId.values());
        if (partitions.isEmpty()) {
            return null;
        }
        Collections.sort(partitions, new TransactionsPartition.ByIdSorter());
        Collections.sort(partitions, new TransactionsPartition.BySizeSorter());
        return partitions.get(0);
    }

}
