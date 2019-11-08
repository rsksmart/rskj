package co.rsk.core;

import org.bouncycastle.util.test.TestRandomBigInteger;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

public class TransactionsPartitionerTest {
    @Test
    public void testTransactionPartition_1() {
        Transaction[] transactions = createTransactions(6).toArray(new Transaction[6]);
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        Transaction tx1 = transactions[0];
        Transaction tx2 = transactions[1];
        Transaction tx3 = transactions[2];
        Transaction tx4 = transactions[3];
        Transaction tx5 = transactions[4];
        Transaction tx6 = transactions[5];

        int part1 = partitioner.addToNewPartition(tx1);
        Assert.assertEquals(0, part1);

        partitioner.addToPartition(tx2, part1);

        int part2 = partitioner.addToNewPartition(tx3);
        Assert.assertEquals(1, part2);

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

        partitioner.addToPartition(tx4, part1);

        int part3 = partitioner.addToNewPartition(tx5);
        Assert.assertEquals(2, part3);

        partitioner.addToPartition(tx6, part2);

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
    public void testFullPartioner() {
        Transaction[] transactions = createTransactions(36).toArray(new Transaction[36]);
        TransactionsPartitioner partitioner = new TransactionsPartitioner();

        // Fill all partition with 2 txs each, except partition nb 12
        for (int i = 0; i < 16; i++) {
            int partId = partitioner.addToNewPartition(transactions[2*i]);
            if (partId != 12) {
                partitioner.addToPartition(transactions[2 * i + 1], partId);
            }
        }

        Collection<Transaction> lstTxs = partitioner.getAllTransactionsSortedPerPartition();
        Assert.assertEquals(31,lstTxs.size());
        int[] partitionEnds = partitioner.getPartitionEnds();
        Assert.assertEquals(16, partitionEnds.length);

        int partId = partitioner.addToNewPartition(transactions[32]);
        // The smallest partition was partition with id 12
        Assert.assertEquals(12,partId);

        partId = partitioner.addToNewPartition(transactions[33]);
        // All partitions have the same size, the chosen one must be the first one
        Assert.assertEquals(0,partId);

        partitioner.addToPartition(transactions[34], 1);
        partId = partitioner.addToNewPartition(transactions[35]);
        // Now partition 0 and 1 are filled with 3 txs, whereas other have only 2 txs
        // The first 'smallest' partition must the third one.
        Assert.assertEquals(2,partId);

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

