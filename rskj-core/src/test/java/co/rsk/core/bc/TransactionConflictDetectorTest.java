package co.rsk.core.bc;

import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
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
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector();
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

        Assert.assertFalse(conflictDetector.hasConflict);
    }

    @Test
    public void test_2_read_conflict() {
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector();
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

        // TODO - it shall not be a conflict if concurrent tasks only read the same key
        Assert.assertTrue(conflictDetector.hasConflict);
    }

    @Test
    public void test_3_write_conflict() {
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector();
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

        Assert.assertTrue(conflictDetector.hasConflict);
    }

    @Test
    public void test_4_read_write_conflict() {
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector();
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

        Assert.assertTrue(conflictDetector.hasConflict);
    }

    @Test
    public void test_5_read_delete_conflict() {
        TransactionConflictDetector conflictDetector = new TransactionConflictDetector();
        MutableTrieCache mtCache = prepareCache();
        mtCache.subscribe(conflictDetector);

        Map<String, String> writtenKeysTask3 = new HashMap<>();
        writtenKeysTask3.put("CAROL", "lorac");
        Map<String, String> writtenKeysTask4 = new HashMap<>();
        writtenKeysTask4.put("ROBERT", "trebor");

        Callable[] tasks = {
                createTask(mtCache, new String[] {"ALICE"}, new HashMap<>(), new String[] {}),
                createTask(mtCache, new String[] {"BOB", "DENISE"}, new HashMap<>(), new String[] {}),
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

        Assert.assertTrue(conflictDetector.hasConflict);
    }

}