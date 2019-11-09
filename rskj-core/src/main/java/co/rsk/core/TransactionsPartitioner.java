package co.rsk.core;

import org.ethereum.core.Transaction;

import java.util.*;

public class TransactionsPartitioner {

    private static final int NB_MAX_PARTITIONS = 16;

    private Map<Integer, Collection<Transaction>> partitions = new HashMap<>();

    private int[] partitionEnds = new int[0];

    /**
     * add a transaction to a new partition if available
     * @param tx
     * @return the zero-based index of the partition where the Tx has been added
     */
    public int addToNewPartition(Transaction tx) {
        int partId;
        if (partitions.size() >= NB_MAX_PARTITIONS) {
            // we reach the limit of partition so lets choose the partition with less Tx inside.
            partId = findSmallestPartition();
        } else {
            partId = partitions.size();
        }
        this.addToPartition(tx, partId);
        return partId;
    }

    public void addToPartition(Transaction tx, int partitionId) {
        partitions.computeIfAbsent(partitionId, k -> new ArrayList());
        partitions.get(partitionId).add(tx);
        clearPartitionEnds(); // force to recompute it because the partitions map has changed
    }

    public List<Transaction> getAllTransactionsSortedPerPartition() {
        List<Transaction> listTransactions = new ArrayList<>();
        int indexTx = 0;
        partitionEnds = new int[partitions.size()];
        for (int partId = 0; partId < partitions.size(); partId++) {
            Collection<Transaction> txs = partitions.get(partId);
            listTransactions.addAll(txs);
            indexTx += txs.size();
            partitionEnds[partId] = indexTx - 1;
        }
        return listTransactions;
    }

    private void clearPartitionEnds() {
        partitionEnds = new int[0];
    }

    public int[] getPartitionEnds() {
        if (partitionEnds.length == 0) {
            // this will recompute partitionEnds
            getAllTransactionsSortedPerPartition();
        }
        return Arrays.copyOf(partitionEnds, partitionEnds.length);
    }

    private int findSmallestPartition() {
        if (partitions.isEmpty()) {
            return -1;
        }
        TreeMap<Integer, Collection> sortedMap = new TreeMap<>( new MappedCollectionComparator<Transaction>(partitions) );
        sortedMap.putAll(partitions);
        return sortedMap.firstKey();
    }

    /**
     * This class allows to sort partitions Map according to the size of the mapped collections
     */
    private static class MappedCollectionComparator<T> implements Comparator<Integer> {

        private final Map<Integer, Collection<T>> map;

        public MappedCollectionComparator(Map<Integer, Collection<T>> map) {
            this.map = map;
        }

        @Override
        public int compare(Integer key1, Integer key2) {
            Collection c1 = map.get(key1);
            Collection c2 = map.get(key2);
            if (c1 == c2) {
                return 0;
            } else if (c1 == null) {
                return 1;
            } else if (c2 == null) {
                return -1;
            } else {
                return c1.size() - c2.size();
            }
        }
    }
}
