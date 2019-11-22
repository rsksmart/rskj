package co.rsk.core.bc;

import co.rsk.core.TransactionsPartition;
import co.rsk.core.TransactionsPartitioner;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import org.ethereum.db.TrieKeyMapper;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TransactionConflictDetectorWithCacheTest {

//    @Override
//    protected TransactionConflictDetector createConflictDetector(TransactionsPartitioner partitioner) {
//        return new TransactionConflictDetectorWithCache(partitioner);
//    }
//
//    @Override
//    protected void commitConflictDetector(TransactionConflictDetector conflictDetector, TransactionsPartitioner partitioner) {
//        if (conflictDetector instanceof TransactionConflictDetectorWithCache) {
//            TransactionConflictDetectorWithCache conflictDetectorWithCache = (TransactionConflictDetectorWithCache) conflictDetector;
//            for (TransactionsPartition partition : partitioner.getPartitions()) {
//                conflictDetectorWithCache.commitPartition(partition);
//            }
//        }
//    }
protected static byte[] toBytes(String x) {
    return x.getBytes(StandardCharsets.UTF_8);
}

    protected static String bytesToString(byte[] data) {
        if (data == null) {
            return new String();
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    protected static Callable<Boolean> createTask(MutableTrieCache mtCache, String[] readKeys, String[] writtenKeys, String[] writtenValues, String[] deletedKeys) {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                for(String key: readKeys) {
                    byte[] value = mtCache.get(toBytes(key));
                    String valStr = bytesToString(value);
                }
                if (writtenKeys.length != writtenKeys.length) {
                    throw new Exception("writtenKeys and writtenValues must have the same number of elements");
                }
                for(int i = 0; i < writtenKeys.length; i++) {
                    mtCache.put(writtenKeys[i], toBytes(writtenValues[i]));
                }
                for(String key: deletedKeys) {
                    mtCache.deleteRecursive(toBytes(key));
                }
                return true;
            }
        };
    }

    protected static MutableTrieCache prepareCache() {
        MutableTrieImpl baseMutableTrie = new MutableTrieImpl(null, new Trie());
        MutableTrieCache mtCache = new MutableTrieCache(baseMutableTrie);

        mtCache.put("ALICE", toBytes("alice"));
        mtCache.put("BOB", toBytes("bob"));
        mtCache.put("CAROL", toBytes("carol"));
        mtCache.put("DENISE", toBytes("denise"));
        mtCache.put("ROBERT", toBytes("robert"));

        return mtCache;
    }

    @Test
    public void sequential_detect_conflicts_and_resolve() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetectorWithCache conflictDetectorWithCache = new TransactionConflictDetectorWithCache(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetectorWithCache);

        StringBuilder accountLikeKeyDenise = new StringBuilder("DENISE");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKeyDenise.length() < keySize;) accountLikeKeyDenise.append("0");

        Callable[] tasks = {
                // task1 write key "BOB" and task2 read key "BOB"
                createTask(mtCache, new String[] {"ALICE"}, new String[] {"BOB"}, new String[] {"bob"}, new String[] {}),
                // task2 read key "CAROL" and task3 write key "CAROL"
                createTask(mtCache, new String[] {"ALICE", "BOB", "CAROL", accountLikeKeyDenise.toString()}, new String[] {}, new String[] {}, new String[] {}),
                // task3 and task4 both are writing ROBERT
                createTask(mtCache, new String[] {"ALICE"}, new String[] {"CAROL", "ROBERT", accountLikeKeyDenise.toString() + "123"}, new String[] {"lorac", "trebor2", "esined123"}, new String[] {}),
                createTask(mtCache, new String[] {"ALICE"}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
                // task5 is written ALICE, while the 4 other tasks are reading it
                // task5 is also deleting DENISE, read by task2 and written by task3
                createTask(mtCache, new String[] {}, new String[] {"ALICE"}, new String[] {"ecila"}, new String[] {accountLikeKeyDenise.toString()})
        };

        for (int i = 0; i < tasks.length; i++) {

            Callable task = tasks[i];
            TransactionsPartition partition = partitioner.newPartition();
            ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory -> new Thread(
                    partition.getThreadGroup(),
                    threadFactory));

            try {
                executor.submit(task);
                executor.shutdown();
                executor.awaitTermination(10000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
                Assert.fail();
            }

            if ( i == 4 ) { // after task5

                Assert.assertFalse(conflictDetectorWithCache.hasConflict());

                conflictDetectorWithCache.commitPartition(partition);

                Assert.assertTrue(conflictDetectorWithCache.hasConflict());

                Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetectorWithCache.getConflicts();
                Assert.assertEquals(2, conflicts.size());

            } else {

                conflictDetectorWithCache.commitPartition(partition);

                if (i == 3) { // after task4

                    Assert.assertTrue(conflictDetectorWithCache.hasConflict());

                    Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetectorWithCache.getConflicts();
                    Assert.assertEquals(3, conflicts.size());

                    Set<TransactionsPartition> conflictingPartitions = conflictDetectorWithCache.getConflictingPartitions();
                    TransactionsPartition mergedPartition = partitioner.mergePartitions(conflictingPartitions);
                    conflictingPartitions.remove(mergedPartition);

                    conflictDetectorWithCache.resolveConflicts(conflictingPartitions, mergedPartition);

                    Assert.assertFalse(conflictDetectorWithCache.hasConflict());

                    conflicts = conflictDetectorWithCache.getConflicts();
                    Assert.assertEquals(0, conflicts.size());

                }
            }
        }

    }

}
