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
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class TransactionConflictDetectorTest {

    private static byte[] toBytes(String x) {
        return x.getBytes(StandardCharsets.UTF_8);
    }

    private static String bytesToString(byte[] data) {
        if (data == null) {
            return new String();
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static Callable<Boolean> createTask(MutableTrieCache mtCache, String[] readKeys, Map<String, String> writtenKeys, String[] deletedKeys) {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                for(String key: readKeys) {
                    byte[] value = mtCache.get(toBytes(key));
                    String valStr = bytesToString(value);
                }
                for(String key: writtenKeys.keySet()) {
                    mtCache.put(toBytes(key), toBytes(writtenKeys.get(key)));
                }
                for(String key: deletedKeys) {
                    mtCache.deleteRecursive(toBytes(key));
                }
                return true;
            }
        };
    }

    private static MutableTrieCache prepareCache() {
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
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Map<String, String> writtenKeysTask3 = new HashMap<>();
        writtenKeysTask3.put("CAROL", "lorac");
        Map<String, String> writtenKeysTask4 = new HashMap<>();
        writtenKeysTask4.put("ROBERT", "trebor");

        Callable[] tasks = {
                createTask(mtCache, new String[] {"ALICE"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask3, new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask4, new String[] {}),
                createTask(mtCache, new String[] {}, new HashMap<>(), new String[] {"DENISE"})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                threadFactory -> new Thread(
                        new ThreadGroup("grp-" + UUID.randomUUID()),
                        threadFactory)
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

        Assert.assertFalse(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(0, conflicts.size());
    }

    @Test
    public void test_2_read_same_key_no_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Map<String, String> writtenKeysTask3 = new HashMap<>();
        writtenKeysTask3.put("CAROL", "lorac");
        Map<String, String> writtenKeysTask4 = new HashMap<>();
        writtenKeysTask4.put("ROBERT", "trebor");

        Callable[] tasks = {
                createTask(mtCache, new String[] {"ALICE", "BOB"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask3, new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask4, new String[] {}),
                createTask(mtCache, new String[] {}, new HashMap<>(), new String[] {"DENISE"})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                threadFactory -> new Thread(
                        new ThreadGroup("grp-" + UUID.randomUUID()),
                        threadFactory)
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

        Assert.assertFalse(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(0, conflicts.size());
    }

    @Test
    public void test_3_write_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Map<String, String> writtenKeysTask3 = new HashMap<>();
        writtenKeysTask3.put("CAROL", "lorac");
        writtenKeysTask3.put("ROBERT", "trebor2");
        Map<String, String> writtenKeysTask4 = new HashMap<>();
        writtenKeysTask4.put("ROBERT", "trebor");

        Callable[] tasks = {
                createTask(mtCache, new String[] {"ALICE"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask3, new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask4, new String[] {}),
                createTask(mtCache, new String[] {}, new HashMap<>(), new String[] {"DENISE"})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                threadFactory -> new Thread(
                        partitioner.newPartition().getThreadGroup(),
                        threadFactory)
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

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(1, conflicts.size());
    }

    @Test
    public void test_4_read_write_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Map<String, String> writtenKeysTask3 = new HashMap<>();
        writtenKeysTask3.put("CAROL", "lorac");
        Map<String, String> writtenKeysTask4 = new HashMap<>();
        writtenKeysTask4.put("ROBERT", "trebor");

        Callable[] tasks = {
                createTask(mtCache, new String[] {"ALICE", "ROBERT"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask3, new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask4, new String[] {}),
                createTask(mtCache, new String[] {}, new HashMap<>(), new String[] {"DENISE"})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                threadFactory -> new Thread(
                        new ThreadGroup("grp-" + UUID.randomUUID()),
                        threadFactory)
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

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(1, conflicts.size());
    }

    @Test
    public void test_6_write_read_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Map<String, String> writtenKeysTask1 = new HashMap<>();
        writtenKeysTask1.put("DENISE", "esined");
        Map<String, String> writtenKeysTask3 = new HashMap<>();
        writtenKeysTask3.put("CAROL", "lorac");
        Map<String, String> writtenKeysTask4 = new HashMap<>();
        writtenKeysTask4.put("ROBERT", "trebor");

        Callable[] tasks = {
                createTask(mtCache, new String[] {"ALICE"}, writtenKeysTask1, new String[] {}),
                createTask(mtCache, new String[] {"BOB"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask3, new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask4, new String[] {}),
                createTask(mtCache, new String[] {"DENISE"}, new HashMap<>(), new String[] {})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                threadFactory -> new Thread(
                        new ThreadGroup("grp-" + UUID.randomUUID()),
                        threadFactory)
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

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(1, conflicts.size());
    }

    @Test
    public void test_5_read_delete_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        StringBuilder accountLikeKeyDenise = new StringBuilder("DENISE");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKeyDenise.length() < keySize;) accountLikeKeyDenise.append("0");

        Map<String, String> writtenKeysTask3 = new HashMap<>();
        writtenKeysTask3.put("CAROL", "lorac");
        Map<String, String> writtenKeysTask4 = new HashMap<>();
        writtenKeysTask4.put("ROBERT", "trebor");

        Callable[] tasks = {
                createTask(mtCache, new String[] {"ALICE"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {"BOB", accountLikeKeyDenise.toString()}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask3, new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask4, new String[] {}),
                createTask(mtCache, new String[] {}, new HashMap<>(), new String[] {accountLikeKeyDenise.toString()})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks.length,
                threadFactory -> new Thread(
                        new ThreadGroup("grp-" + UUID.randomUUID()),
                        threadFactory)
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

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(1, conflicts.size());
    }

    @Test
    public void test_7_multiple_conflict() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector(partitioner);
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        StringBuilder accountLikeKeyDenise = new StringBuilder("DENISE");
        int keySize = TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.domainPrefix().length + TrieKeyMapper.SECURE_KEY_SIZE;
        for (; accountLikeKeyDenise.length() < keySize;) accountLikeKeyDenise.append("0");

        Map<String, String> writtenKeysTask3 = new HashMap<>();
        writtenKeysTask3.put("CAROL", "lorac");
        // task3 and task4 both are written ROBERT
        writtenKeysTask3.put("ROBERT", "trebor2");
        writtenKeysTask3.put("DENISE", "esined");
        Map<String, String> writtenKeysTask4 = new HashMap<>();
        writtenKeysTask4.put("ROBERT", "trebor");
        Map<String, String> writtenKeysTask5 = new HashMap<>();
        // task5 is written ALICE, while the 4 other tasks are reading it
        // --> resulting in 4 conflicts assuming that task5 is run after the others
        // task5 is also deleting DENISE, read by task2 and written by task3
        // --> resulting in 2 conflicts assuming that task5 is run after the others
        writtenKeysTask5.put("ALICE", "ecila");

        Callable[] tasks1 = {
                createTask(mtCache, new String[] {"ALICE"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {"ALICE", "BOB", accountLikeKeyDenise.toString()}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {"ALICE"}, writtenKeysTask3, new String[] {}),
                createTask(mtCache, new String[] {"ALICE"}, writtenKeysTask4, new String[] {}),
        };

        Callable[] tasks2 = {
                createTask(mtCache, new String[] {}, writtenKeysTask5, new String[] {accountLikeKeyDenise.toString()})
        };

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks1.length,
                threadFactory -> new Thread(
                        new ThreadGroup("grp-" + UUID.randomUUID()),
                        threadFactory)
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

        // Now run task5 alone
        executor = Executors.newFixedThreadPool(
                tasks2.length,
                threadFactory -> new Thread(
                        new ThreadGroup("grp-" + UUID.randomUUID()),
                        threadFactory)
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

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(6, conflicts.size());
    }


    @Test
    public void test_8_recursive_delete() {
        TransactionsPartitioner partitioner = new TransactionsPartitioner();
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector(partitioner);
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

        Map<String, String> writtenKeysTask3 = new HashMap<>();
        writtenKeysTask3.put("CAROL", "lorac");
        Map<String, String> writtenKeysTask4 = new HashMap<>();
        writtenKeysTask4.put("ROBERT", "trebor");
        Map<String, String> writtenKeysTask5 = new HashMap<>();
        writtenKeysTask5.put(accountLikeKeyBob.toString() + "456", "654bob");

        Callable[] tasks1 = {
                createTask(mtCache, new String[] {accountLikeKeyAlice.toString() + "123"}, new HashMap<>(), new String[] {accountLikeKeyBob.toString()}),
                createTask(mtCache, new String[] {accountLikeKeyAlice.toString() + "456"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask3, new String[] {}),
                createTask(mtCache, new String[] {}, writtenKeysTask4, new String[] {}),
        };

        Callable[] tasks2 = {
                createTask(mtCache, new String[] {accountLikeKeyBob.toString() + "123"}, writtenKeysTask5, new String[] {accountLikeKeyAlice.toString()})
        };

        // Expected conflicts :
        // - BOB account is deleted by task1 before task5 reads BOB123 and write BOB456 --> +2 conflicts
        // - ALICE account is deleted by task5 after task1 reads ALICE123 and task2 reads ALICE456 --> +2 conflicts

        // Use an executor service, assuming that each thread will run in a different ThreadGroup
        ExecutorService executor = Executors.newFixedThreadPool(
                tasks1.length,
                threadFactory -> new Thread(
                        new ThreadGroup("grp-" + UUID.randomUUID()),
                        threadFactory)
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

        // There should now be any conflict before task5 is run
        Assert.assertFalse(conflictDetector.hasConflict());

        // Now run task5 alone
        executor = Executors.newFixedThreadPool(
                tasks2.length,
                threadFactory -> new Thread(
                        new ThreadGroup("grp-" + UUID.randomUUID()),
                        threadFactory)
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

        Assert.assertTrue(conflictDetector.hasConflict());

        Collection<TransactionConflictDetector.Conflict> conflicts = conflictDetector.getConflicts();
        Assert.assertEquals(4, conflicts.size());
    }

}
