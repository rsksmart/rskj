package co.rsk.core;

import org.bouncycastle.util.test.TestRandomBigInteger;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TransactionsPartitionerTest {
    @Test
    public void testTransactionPartition_1() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();

        Transaction[] transactions = createTransactions(6).toArray(new Transaction[6]);
        Transaction tx1 = transactions[0];
        Transaction tx2 = transactions[1];
        Transaction tx3 = transactions[2];
        Transaction tx4 = transactions[3];
        Transaction tx5 = transactions[4];
        Transaction tx6 = transactions[5];

        TransactionsPartition part1 = partitioner.newPartition();
        part1.addTransaction(tx1);
        Assert.assertEquals(1, partitioner.getNbPartitions());

        part1.addTransaction(tx2);

        TransactionsPartition part2 = partitioner.newPartition();
        part2.addTransaction(tx3);
        Assert.assertEquals(2, partitioner.getNbPartitions());

        Transaction[] sortedTxs = partitioner.getAllTransactionsSortedPerPartition().toArray(new Transaction[3]);
        Assert.assertArrayEquals(
                new Transaction[] {tx1, tx2, tx3},
                sortedTxs
        );
        int[] partitionEnds = partitioner.getPartitionEnds();
        Assert.assertEquals(2, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {1,2},
                partitionEnds
        );

        part1.addTransaction(tx4);

        TransactionsPartition part3 = partitioner.newPartition();
        part3.addTransaction(tx5);
        Assert.assertEquals(3, partitioner.getNbPartitions());

        part2.addTransaction(tx6);

        partitionEnds = partitioner.getPartitionEnds();
        Assert.assertEquals(3, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {2,4,5},
                partitionEnds
        );
        sortedTxs = partitioner.getAllTransactionsSortedPerPartition().toArray(new Transaction[6]);
        Assert.assertArrayEquals(
                new Transaction[] {tx1, tx2, tx4, tx3, tx6, tx5},
                sortedTxs
        );
    }

    @Test
    public void testTransactionPartition_2() {
        // Very similar to test_1 but this time we create a partition for each Tx, even if it remains empty
        // (this case happens when a Tx is conflicting with another partition)
        TransactionsPartitioner partitioner = new TransactionsPartitioner();

        Transaction[] transactions = createTransactions(7).toArray(new Transaction[7]);
        Transaction tx1 = transactions[0];
        Transaction tx2 = transactions[1];
        Transaction tx3 = transactions[2];
        Transaction tx4 = transactions[3];
        Transaction tx5 = transactions[4];
        Transaction tx6 = transactions[5];
        Transaction tx7 = transactions[6];

        TransactionsPartition part1 = partitioner.newPartition();
        part1.addTransaction(tx1);
        Assert.assertEquals(1, partitioner.getNbPartitions());

        TransactionsPartition part2 = partitioner.newPartition();
        // assuming that tx3 is conflicting with partition part1
        part1.addTransaction(tx2);
        Assert.assertEquals(2, partitioner.getNbPartitions());
        Assert.assertEquals(1, partitioner.getNotEmptyPartitions().size());

        TransactionsPartition part3 = partitioner.newPartition();
        part3.addTransaction(tx3);
        Assert.assertEquals(3, partitioner.getNbPartitions());
        Assert.assertEquals(2, partitioner.getNotEmptyPartitions().size());

        Transaction[] sortedTxs = partitioner.getAllTransactionsSortedPerPartition().toArray(new Transaction[3]);
        Assert.assertArrayEquals(
                new Transaction[] {tx1, tx2, tx3},
                sortedTxs
        );
        int[] partitionEnds = partitioner.getPartitionEnds();
        Assert.assertEquals(2, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {1,2},
                partitionEnds
        );

        TransactionsPartition part4 = partitioner.newPartition();
        // assuming that tx4 is conflicting with partition part1
        part1.addTransaction(tx4);

        TransactionsPartition part5 = partitioner.newPartition();
        part5.addTransaction(tx5);
        Assert.assertEquals(3, partitioner.getNotEmptyPartitions().size());

        TransactionsPartition part6 = partitioner.newPartition();
        // assuming that tx6 is conflicting with partition part3
        part3.addTransaction(tx6);

        partitionEnds = partitioner.getPartitionEnds();
        Assert.assertEquals(3, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {2,4,5},
                partitionEnds
        );
        sortedTxs = partitioner.getAllTransactionsSortedPerPartition().toArray(new Transaction[6]);
        Assert.assertArrayEquals(
                new Transaction[] {tx1, tx2, tx4, tx3, tx6, tx5},
                sortedTxs
        );

        TransactionsPartition part7 = partitioner.newPartition();
        // assuming that tx7 is conflicting with partitions part1 and part3
        Set<TransactionsPartition> conflictingPartitions = new HashSet<>();
        conflictingPartitions.add(part1);
        conflictingPartitions.add(part3);
        part1 = partitioner.mergePartitions(conflictingPartitions);
        part1.addTransaction(tx7);
        Assert.assertEquals(2, partitioner.getNotEmptyPartitions().size());

        partitionEnds = partitioner.getPartitionEnds();
        Assert.assertEquals(2, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {5,6},
                partitionEnds
        );
        sortedTxs = partitioner.getAllTransactionsSortedPerPartition().toArray(new Transaction[6]);
        Assert.assertArrayEquals(
                new Transaction[] {tx1, tx2, tx4, tx3, tx6, tx7, tx5},
                sortedTxs
        );

    }

    @Test
    public void testFullPartioner() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();

        Transaction[] transactions = createTransactions(36).toArray(new Transaction[36]);

        // Fill all partition with 2 txs each, except partition nb 12
        for (int i = 0; i < 16; i++) {
            TransactionsPartition partition = partitioner.newPartition();
            partition.addTransaction(transactions[2*i]);
            if (partitioner.getNbPartitions() != 13) {
                partition.addTransaction(transactions[2 * i + 1]);
            }
        }

        Collection<Transaction> lstTxs = partitioner.getAllTransactionsSortedPerPartition();
        Assert.assertEquals(31,lstTxs.size());
        int[] partitionEnds = partitioner.getPartitionEnds();
        Assert.assertEquals(16, partitionEnds.length);

        TransactionsPartition partition = partitioner.newPartition();
        int partId = partitioner.getPartitions().indexOf(partition);
        // The smallest partition was partition with id 12
        Assert.assertEquals(12,partId);
        partition.addTransaction(transactions[32]);

        partition = partitioner.newPartition();
        partId = partitioner.getPartitions().indexOf(partition);
        // All partitions have the same size, the chosen one must be the first one
        Assert.assertEquals(0,partId);
        partition.addTransaction(transactions[33]);

        partitioner.getPartitions().get(1).addTransaction(transactions[34]);
        partition = partitioner.newPartition();
        partId = partitioner.getPartitions().indexOf(partition);
        // Now partition 0 and 1 are filled with 3 txs, whereas other have only 2 txs
        // The first 'smallest' partition must the third one.
        Assert.assertEquals(2,partId);
        partition.addTransaction(transactions[35]);

        lstTxs = partitioner.getAllTransactionsSortedPerPartition();
        Assert.assertEquals(35,lstTxs.size());
        partitionEnds = partitioner.getPartitionEnds();
        Assert.assertEquals(16, partitionEnds.length);
        Assert.assertArrayEquals(
                new int[] {2,5,8,10,12,14,16,18,20,22,24,26,28,30,32,34},
                partitionEnds
        );

    }

    private Collection<Transaction> createTransactions(int nbTransactions) {
        ArrayList transactions = new ArrayList();
        for (int i = 0; i < nbTransactions; i++) {
            transactions.add(
                    new Transaction((byte[]) null, null, null, null, null, null)
            );
        }
        return transactions;
    }
}

