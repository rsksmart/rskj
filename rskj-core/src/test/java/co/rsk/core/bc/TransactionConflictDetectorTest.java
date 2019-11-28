package co.rsk.core.bc;

import co.rsk.core.TransactionExecutorThread;
import co.rsk.core.TransactionsPartition;
import co.rsk.core.TransactionsPartitioner;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TrieKeyMapper;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

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
                for (String key : readKeys) {
                    byte[] value = mtCache.get(toBytes(key));
                    String valStr = bytesToString(value);
                }
                if (writtenKeys.length != writtenKeys.length) {
                    throw new Exception("writtenKeys and writtenValues must have the same number of elements");
                }
                for (int i = 0; i < writtenKeys.length; i++) {
                    mtCache.put(writtenKeys[i], toBytes(writtenValues[i]));
                }
                for (String key : deletedKeys) {
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
                createTask(mtCache, new String[]{"ALICE"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{"BOB"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"CAROL"}, new String[]{"lorac"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"ROBERT"}, new String[]{"trebor"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{}, new String[]{}, new String[]{"DENISE"})
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
                createTask(mtCache, new String[]{"ALICE", "BOB"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{"BOB"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"CAROL"}, new String[]{"lorac"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"ROBERT"}, new String[]{"trebor"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{}, new String[]{}, new String[]{"DENISE"})
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
                createTask(mtCache, new String[]{"ALICE"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{"BOB"}, new String[]{}, new String[]{}, new String[]{}),
                // task3 and task4 both write key "ROBERT"
                createTask(mtCache, new String[]{}, new String[]{"CAROL", "ROBERT"}, new String[]{"lorac", "trebor2"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"ROBERT"}, new String[]{"trebor"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{}, new String[]{}, new String[]{"DENISE"})
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
                createTask(mtCache, new String[]{"ALICE", "ROBERT"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{"BOB"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"CAROL"}, new String[]{"lorac"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"ROBERT"}, new String[]{"trebor"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{}, new String[]{}, new String[]{"DENISE"})
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
                createTask(mtCache, new String[]{"ALICE"}, new String[]{"DENISE"}, new String[]{"esined"}, new String[]{}),
                createTask(mtCache, new String[]{"BOB"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"CAROL"}, new String[]{"lorac"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"ROBERT"}, new String[]{"trebor"}, new String[]{}),
                createTask(mtCache, new String[]{"DENISE"}, new String[]{}, new String[]{}, new String[]{})
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
        for (; accountLikeKeyDenise.length() < keySize; ) accountLikeKeyDenise.append("0");

        Callable[] tasks = {
                // task2 read key "DENISE" and task5 delete account "DENISE"
                createTask(mtCache, new String[]{"ALICE"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{"BOB", accountLikeKeyDenise.toString()}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"CAROL"}, new String[]{"lorac"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"ROBERT"}, new String[]{"trebor"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{}, new String[]{}, new String[]{accountLikeKeyDenise.toString()})
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
        for (; accountLikeKeyDenise.length() < keySize; ) accountLikeKeyDenise.append("0");

        Callable[] tasks1 = {
                createTask(mtCache, new String[]{"ALICE"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{"ALICE", "BOB", accountLikeKeyDenise.toString()}, new String[]{}, new String[]{}, new String[]{}),
                // task3 and task4 both are written ROBERT
                createTask(mtCache, new String[]{"ALICE"}, new String[]{"CAROL", "ROBERT", accountLikeKeyDenise.toString() + "123"}, new String[]{"lorac", "trebor2", "esined123"}, new String[]{}),
                createTask(mtCache, new String[]{"ALICE"}, new String[]{"ROBERT"}, new String[]{"trebor"}, new String[]{}),
        };

        Callable[] tasks2 = {
                // task5 is written ALICE, while the 4 other tasks are reading it
                // --> resulting in 4 conflicts assuming that task5 is run after the others
                // task5 is also deleting DENISE, read by task2 and written by task3
                // --> resulting in 2 conflicts assuming that task5 is run after the others
                createTask(mtCache, new String[]{}, new String[]{"ALICE"}, new String[]{"ecila"}, new String[]{accountLikeKeyDenise.toString()})
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
        for (; accountLikeKeyDenise.length() < keySize; ) accountLikeKeyDenise.append("0");

        Callable[] tasks1 = {
                // task1 write key "BOB" and task2 read key "BOB"
                createTask(mtCache, new String[]{"ALICE"}, new String[]{"BOB"}, new String[]{"bob"}, new String[]{}),
                // task2 read key "CAROL" and task3 write key "CAROL"
                createTask(mtCache, new String[]{"ALICE", "BOB", "CAROL", accountLikeKeyDenise.toString()}, new String[]{}, new String[]{}, new String[]{}),
                // task3 and task4 both are writing ROBERT
                createTask(mtCache, new String[]{"ALICE"}, new String[]{"CAROL", "ROBERT", accountLikeKeyDenise.toString() + "123"}, new String[]{"lorac", "trebor2", "esined123"}, new String[]{}),
                createTask(mtCache, new String[]{"ALICE"}, new String[]{"ROBERT"}, new String[]{"trebor"}, new String[]{}),
        };

        Callable[] tasks2 = {
                // task5 is written ALICE, while the 4 other tasks are reading it
                // task5 is also deleting DENISE, read by task2 and written by task3
                createTask(mtCache, new String[]{}, new String[]{"ALICE"}, new String[]{"ecila"}, new String[]{accountLikeKeyDenise.toString()})
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
        for (; accountLikeKeyAlice.length() < keySize; ) accountLikeKeyAlice.append("0");

        mtCache.put(toBytes(accountLikeKeyAlice.toString() + "123"), toBytes("alice123"));
        mtCache.put(toBytes(accountLikeKeyAlice.toString() + "456"), toBytes("alice456"));

        StringBuilder accountLikeKeyBob = new StringBuilder("BOB");
        keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKeyBob.length() < keySize; ) accountLikeKeyBob.append("0");

        mtCache.put(toBytes(accountLikeKeyBob.toString() + "123"), toBytes("bob123"));
        mtCache.put(toBytes(accountLikeKeyBob.toString() + "456"), toBytes("bob456"));

        mtCache.subscribe(conflictDetector);

        Callable[] tasks1 = {
                // task1 is reading key "ALICE123" and is deleting "BOB" account
                createTask(mtCache, new String[]{accountLikeKeyAlice.toString() + "123"}, new String[]{}, new String[]{}, new String[]{accountLikeKeyBob.toString()}),
                // task2 is reading key "ALICE456"
                createTask(mtCache, new String[]{accountLikeKeyAlice.toString() + "456"}, new String[]{}, new String[]{}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"CAROL"}, new String[]{"lorac"}, new String[]{}),
                createTask(mtCache, new String[]{}, new String[]{"ROBERT"}, new String[]{"trebor"}, new String[]{}),
        };

        Callable[] tasks2 = {
                // task5 reads key "BOB123", writes key "BOB456" and deletes account "ALICE"
                createTask(mtCache, new String[]{accountLikeKeyBob.toString() + "123"}, new String[]{accountLikeKeyBob.toString() + "456"}, new String[]{"654bob"}, new String[]{accountLikeKeyAlice.toString()})
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

    @Test
    public void countConflicts() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = createConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();

        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;

        StringBuilder accountLikeKeyAlice = new StringBuilder("ALICE");
        for (; accountLikeKeyAlice.length() < keySize; ) accountLikeKeyAlice.append("0");
        mtCache.put(toBytes(accountLikeKeyAlice.toString() + "123"), toBytes("alice123"));

        StringBuilder accountLikeKeyDenise = new StringBuilder("DENISE");
        for (; accountLikeKeyDenise.length() < keySize; ) accountLikeKeyDenise.append("0");
        mtCache.put(toBytes(accountLikeKeyDenise.toString() + "123"), toBytes("denise123"));


        mtCache.subscribe(conflictDetector);
        // partition1
        // task11 reads key "ALICE123" and writes key "BOB"
        // task12 reads key "CAROL" and writes key "ALICE123"
        Callable task11 = createTask(mtCache, new String[]{accountLikeKeyAlice.toString() + "123"}, new String[]{"BOB"}, new String[]{"bob"}, new String[]{});
        Callable task12 = createTask(mtCache, new String[]{accountLikeKeyAlice.toString()}, new String[]{accountLikeKeyAlice.toString() + "123"}, new String[]{"ecila123"}, new String[]{});
        // partition2
        // task21 reads key "DENISE123" and writes key "ALICE123"
        // --> we could expect 2 conflicts with partition1 (an ACCESSED_BEFORE_WRITTEN with task11 and a WRITTEN_BEFORE_ACCESSED
        // with task12), BUT according to implementation, we do not check for 'read' conflict if we detect a 'write' conflict beforehand.
        // So, there will be only 1 conflict recorded, of type WRITTEN_BEFORE_ACCESSED
        // task22 deletes account "DENISE"
        Callable task21 = createTask(mtCache, new String[]{accountLikeKeyDenise.toString() + "123"}, new String[]{accountLikeKeyAlice.toString() + "123"}, new String[]{"ecilalice"}, new String[]{});
        Callable task22 = createTask(mtCache, new String[]{}, new String[]{}, new String[]{}, new String[]{accountLikeKeyDenise.toString()});
        // partition3
        // task31 writes key "DENISE123" --> 2 conflicts with partition2 (of types ACCESSED_BEFORE_WRITTEN and DELETED_BEFORE_ACCESSED)
        // task32 deletes account "ALICE" --> 1 conflict with partition1 and 1 conflict with partition2 (all of type ACCESSED_BEFORE_DELETE)
        Callable task31 = createTask(mtCache, new String[]{}, new String[]{accountLikeKeyDenise.toString() + "123"}, new String[]{"esined123"}, new String[]{});
        Callable task32 = createTask(mtCache, new String[]{}, new String[]{}, new String[]{}, new String[]{accountLikeKeyAlice.toString()});

        // Single thread executor for partition1
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(TransactionExecutorThread.getFactoryForNewPartition(partitioner));
        executor.submit(task11);
        executor.submit(task12);
        executor.shutdown();
        try {
            executor.awaitTermination(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.fail();
        }
        // No conflict after partition1 completed
        Assert.assertFalse(conflictDetector.hasConflict());

        // Single thread executor for partition2
        executor = Executors.newSingleThreadScheduledExecutor(TransactionExecutorThread.getFactoryForNewPartition(partitioner));
        executor.submit(task21);
        executor.submit(task22);
        executor.shutdown();
        try {
            executor.awaitTermination(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.fail();
        }
        // Some conflicts after partition2 completed
        Assert.assertTrue(conflictDetector.hasConflict());
        Set<TransactionsPartition> conflictingPartitions = conflictDetector.getConflictingPartitions();
        // conflicting partitions are partition1 and partition2
        Assert.assertEquals(2, conflictingPartitions.size());
        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        // we expect 1 conflict (see explanation above)
        Assert.assertEquals(1, conflicts.size());
        TransactionConflictDetector.Conflict conflict = conflicts.iterator().next();
        Assert.assertEquals(TransactionConflictDetector.eConflictType.WRITTEN_BEFORE_ACCESSED, conflict.getConflictType());
        Assert.assertArrayEquals(toBytes(accountLikeKeyAlice.toString() + "123"), conflict.getKey().getData());

        // Single thread executor for partition3
        executor = Executors.newSingleThreadScheduledExecutor(TransactionExecutorThread.getFactoryForNewPartition(partitioner));
        executor.submit(task31);
        executor.submit(task32);
        executor.shutdown();
        try {
            executor.awaitTermination(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.fail();
        }
        // Some conflicts after partition2 completed
        Assert.assertTrue(conflictDetector.hasConflict());
        conflictingPartitions = conflictDetector.getConflictingPartitions();
        // conflicting partitions are partition1, partition2 and partition3
        Assert.assertEquals(3, conflictingPartitions.size());
        List<TransactionsPartition> partitions = new ArrayList<>(conflictingPartitions);
        partitions.sort(new Comparator<TransactionsPartition>() {
            @Override
            public int compare(TransactionsPartition o1, TransactionsPartition o2) {
                return o1.getId() - o2.getId();
            }
        });

        conflicts = conflictDetector.getConflicts();
        // we expect 5 conflicts
        Assert.assertEquals(5, conflicts.size());
        List<TransactionConflictDetector.Conflict> expectedConflicts = new ArrayList<>();
        expectedConflicts.add(
                new TransactionConflictDetector.Conflict(
                        new ByteArrayWrapper(toBytes(accountLikeKeyAlice.toString() + "123")),
                        partitions.get(0),
                        partitions.get(1),
                        TransactionConflictDetector.eConflictType.WRITTEN_BEFORE_ACCESSED
                )
        );
        expectedConflicts.add(
                new TransactionConflictDetector.Conflict(
                        new ByteArrayWrapper(toBytes(accountLikeKeyAlice.toString())),
                        partitions.get(0),
                        partitions.get(2),
                        TransactionConflictDetector.eConflictType.ACCESSED_BEFORE_DELETE
                )
        );
        expectedConflicts.add(
                new TransactionConflictDetector.Conflict(
                        new ByteArrayWrapper(toBytes(accountLikeKeyAlice.toString())),
                        partitions.get(1),
                        partitions.get(2),
                        TransactionConflictDetector.eConflictType.ACCESSED_BEFORE_DELETE
                )
        );
        expectedConflicts.add(
                new TransactionConflictDetector.Conflict(
                        new ByteArrayWrapper(toBytes(accountLikeKeyDenise.toString() + "123")),
                        partitions.get(1),
                        partitions.get(2),
                        TransactionConflictDetector.eConflictType.ACCESSED_BEFORE_WRITTEN
                )
        );
        expectedConflicts.add(
                new TransactionConflictDetector.Conflict(
                        new ByteArrayWrapper(toBytes(accountLikeKeyDenise.toString())),
                        partitions.get(1),
                        partitions.get(2),
                        TransactionConflictDetector.eConflictType.DELETED_BEFORE_ACCESSED
                )
        );

        for (TransactionConflictDetector.Conflict aConflict : conflicts) {
            List<TransactionConflictDetector.Conflict> copyExpectedConflicts = new ArrayList<>(expectedConflicts);
            for (TransactionConflictDetector.Conflict expectedConflict : copyExpectedConflicts) {
                if (
                        aConflict.getConflictType().equals(expectedConflict.getConflictType()) &&
                                Arrays.equals(aConflict.getKey().getData(), expectedConflict.getKey().getData()) &&
                                ((
                                        (aConflict.getConflictWith() == expectedConflict.getConflictWith()) &&
                                                (aConflict.getConflictFrom() == expectedConflict.getConflictFrom())
                                ) || (
                                        (aConflict.getConflictFrom() == expectedConflict.getConflictWith()) &&
                                                (aConflict.getConflictWith() == expectedConflict.getConflictFrom())
                                )
                                )
                ) {
                    expectedConflicts.remove(expectedConflict);
                    break;
                }
            }
        }
        Assert.assertTrue(expectedConflicts.isEmpty());
    }

}
