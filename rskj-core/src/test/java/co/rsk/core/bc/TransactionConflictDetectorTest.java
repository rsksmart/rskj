package co.rsk.core.bc;

import co.rsk.core.TransactionExecutorThread;
import co.rsk.core.TransactionsPartition;
import co.rsk.core.TransactionsPartitioner;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import org.ethereum.core.Transaction;
import org.ethereum.db.TrieKeyMapper;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class TransactionConflictDetectorTest {

    protected TransactionConflictDetector createConflictDetector(TransactionsPartitioner partitioner) {
        return new TransactionConflictDetector(partitioner);
    }

    protected void commitConflictDetector(TransactionConflictDetector conflictDetector, TransactionsPartitioner partitioner) {
    }

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
    public void test_1_no_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Callable[] tasks = {
                createTask(mtCache, new String[] {"ALICE"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"CAROL"}, new String[] {"lorac"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {}, new String[] {}, new String[] {"DENISE"})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );

        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertFalse(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(0, conflicts.size());
    }

    @Test
    public void test_2_read_same_key_no_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Callable[] tasks = {
                // task1 and task2 both read key "BOB"
                createTask(mtCache, new String[] {"ALICE", "BOB"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"CAROL"}, new String[] {"lorac"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {}, new String[] {}, new String[] {"DENISE"})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );

        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertFalse(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(0, conflicts.size());
    }

    @Test
    public void test_3_write_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Callable[] tasks = {
                createTask(mtCache, new String[] {"ALICE"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new String[] {}, new String[] {}, new String[] {}),
                // task3 and task4 both write key "ROBERT"
                createTask(mtCache, new String[] {}, new String[] {"CAROL", "ROBERT"}, new String[] {"lorac", "trebor2"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {}, new String[] {}, new String[] {"DENISE"})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );

        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(1, conflicts.size());
    }

    @Test
    public void test_4_read_write_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Callable[] tasks = {
                // task1 read key "ROBERT" and task4 write key "ROBERT"
                createTask(mtCache, new String[] {"ALICE", "ROBERT"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"CAROL"}, new String[] {"lorac"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {}, new String[] {}, new String[] {"DENISE"})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );

        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(1, conflicts.size());
    }

    @Test
    public void test_6_write_read_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Callable[] tasks = {
                // task1 write key "DENISE" and task5 read key "DENISE"
                createTask(mtCache, new String[] {"ALICE"}, new String[] {"DENISE"}, new String[] {"esined"}, new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"CAROL"}, new String[] {"lorac"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
                createTask(mtCache, new String[] {"DENISE"}, new String[] {}, new String[] {}, new String[] {})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );

        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(1, conflicts.size());
    }

    @Test
    public void test_5_read_delete_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        StringBuilder accountLikeKeyDenise = new StringBuilder("DENISE");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKeyDenise.length() < keySize;) accountLikeKeyDenise.append("0");

        Callable[] tasks = {
                // task2 read key "DENISE" and task5 delete account "DENISE"
                createTask(mtCache, new String[] {"ALICE"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {"BOB", accountLikeKeyDenise.toString()}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"CAROL"}, new String[] {"lorac"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {}, new String[] {}, new String[] {accountLikeKeyDenise.toString()})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );

        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(1, conflicts.size());
    }

    @Test
    public void test_7_multiple_conflics() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        StringBuilder accountLikeKeyDenise = new StringBuilder("DENISE");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKeyDenise.length() < keySize;) accountLikeKeyDenise.append("0");

        Callable[] tasks1 = {
                createTask(mtCache, new String[] {"ALICE"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {"ALICE", "BOB", accountLikeKeyDenise.toString()}, new String[] {}, new String[] {}, new String[] {}),
                // task3 and task4 both are written ROBERT
                createTask(mtCache, new String[] {"ALICE"}, new String[] {"CAROL", "ROBERT", accountLikeKeyDenise.toString() + "123"}, new String[] {"lorac", "trebor2", "esined123"}, new String[] {}),
                createTask(mtCache, new String[] {"ALICE"}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
        };

        Callable[] tasks2 = {
                // task5 is written ALICE, while the 4 other tasks are reading it
                // --> resulting in 4 conflicts assuming that task5 is run after the others
                // task5 is also deleting DENISE, read by task2 and written by task3
                // --> resulting in 2 conflicts assuming that task5 is run after the others
                createTask(mtCache, new String[] {}, new String[] {"ALICE"}, new String[] {"ecila"}, new String[] {accountLikeKeyDenise.toString()})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks1.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );

        // Run task1 to task4 in parallel
        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks1)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(1, conflicts.size());

        // Now run task5 alone
        executor = Executors.newFixedThreadPool(
                tasks2.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );
        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks2)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertTrue(conflictDetector.hasConflict());

        conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(7, conflicts.size());
    }


    @Test
    public void test_9_resolve_conflicts() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        StringBuilder accountLikeKeyDenise = new StringBuilder("DENISE");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKeyDenise.length() < keySize;) accountLikeKeyDenise.append("0");

        Callable[] tasks1 = {
                // task1 write key "BOB" and task2 read key "BOB"
                createTask(mtCache, new String[] {"ALICE"}, new String[] {"BOB"}, new String[] {"bob"}, new String[] {}),
                // task2 read key "CAROL" and task3 write key "CAROL"
                createTask(mtCache, new String[] {"ALICE", "BOB", "CAROL", accountLikeKeyDenise.toString()}, new String[] {}, new String[] {}, new String[] {}),
                // task3 and task4 both are writing ROBERT
                createTask(mtCache, new String[] {"ALICE"}, new String[] {"CAROL", "ROBERT", accountLikeKeyDenise.toString() + "123"}, new String[] {"lorac", "trebor2", "esined123"}, new String[] {}),
                createTask(mtCache, new String[] {"ALICE"}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
        };

        Callable[] tasks2 = {
                // task5 is written ALICE, while the 4 other tasks are reading it
                // task5 is also deleting DENISE, read by task2 and written by task3
                createTask(mtCache, new String[] {}, new String[] {"ALICE"}, new String[] {"ecila"}, new String[] {accountLikeKeyDenise.toString()})
        };


        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks1.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );

        // Run task1 to task4 in parallel
        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks1)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(3, conflicts.size());

        Set<TransactionsPartition> conflictingPartitions = conflictDetector.getConflictingPartitions();
        TransactionsPartition mergedPartition = partitioner.mergePartitions(conflictingPartitions);
        conflictingPartitions.remove(mergedPartition);

        conflictDetector.resolveConflicts(conflictingPartitions, mergedPartition);

        Assert.assertFalse(conflictDetector.hasConflict());

        conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(0, conflicts.size());

        // Now run task5 alone
        executor = Executors.newFixedThreadPool(
                tasks2.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );
        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks2)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertTrue(conflictDetector.hasConflict());

        conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(2, conflicts.size());
    }


    @Test
    public void test_8_recursive_delete() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();

        StringBuilder accountLikeKeyAlice = new StringBuilder("ALICE");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKeyAlice.length() < keySize;) accountLikeKeyAlice.append("0");

        mtCache.put(toBytes(accountLikeKeyAlice.toString() + "123"), toBytes("alice123"));
        mtCache.put(toBytes(accountLikeKeyAlice.toString() + "456"), toBytes("alice456"));

        StringBuilder accountLikeKeyBob = new StringBuilder("BOB");
        keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKeyBob.length() < keySize;) accountLikeKeyBob.append("0");

        mtCache.put(toBytes(accountLikeKeyBob.toString() + "123"), toBytes("bob123"));
        mtCache.put(toBytes(accountLikeKeyBob.toString() + "456"), toBytes("bob456"));

        mtCache.subscribe(conflictDetector);

        Callable[] tasks1 = {
                // task1 is reading key "ALICE123" and is deleting "BOB" account
                createTask(mtCache, new String[] {accountLikeKeyAlice.toString() + "123"}, new String[] {}, new String[] {}, new String[] {accountLikeKeyBob.toString()}),
                // task2 is reading key "ALICE456"
                createTask(mtCache, new String[] {accountLikeKeyAlice.toString() + "456"}, new String[] {}, new String[] {}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"CAROL"}, new String[] {"lorac"}, new String[] {}),
                createTask(mtCache, new String[] {}, new String[] {"ROBERT"}, new String[] {"trebor"}, new String[] {}),
        };

        Callable[] tasks2 = {
                // task5 reads key "BOB123", writes key "BOB456" and deletes account "ALICE"
                createTask(mtCache, new String[] {accountLikeKeyBob.toString() + "123"}, new String[] {accountLikeKeyBob.toString() + "456"}, new String[] {"654bob"}, new String[] {accountLikeKeyAlice.toString()})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks1.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );

        // Run task1 to task4 in parallel
        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks1)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        // There should now be any conflict before task5 is run
        Assert.assertFalse(conflictDetector.hasConflict());

        // Now run task5 alone
        executor = Executors.newFixedThreadPool(
                tasks2.length,
                TransactionExecutorThread.getFactoryForNewPartition(partitioner)
        );
        try {
            executor.invokeAll(new ArrayList(Arrays.asList(tasks2)));
            executor.shutdown();
            boolean timeoutExpired = !executor.awaitTermination(10, TimeUnit.SECONDS);
            Assert.assertFalse(timeoutExpired);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }

        commitConflictDetector(conflictDetector, partitioner);

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(4, conflicts.size());
    }

}
