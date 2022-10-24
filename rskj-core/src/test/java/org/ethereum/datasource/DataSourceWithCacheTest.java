package org.ethereum.datasource;

import org.awaitility.Awaitility;
import org.ethereum.TestUtils;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

class DataSourceWithCacheTest {

    private static final int CACHE_SIZE = 15;

    private HashMapDB baseDataSource;
    private DataSourceWithCache dataSourceWithCache;

    @BeforeEach
    void setupDataSources() {
        this.baseDataSource = spy(new HashMapDB());
        this.dataSourceWithCache = new DataSourceWithCache(baseDataSource, CACHE_SIZE);
    }

    /**
     * Checks that the base is acceded once
     */
    @Test
    void getAfterMiss() {
        byte[] randomKey = TestUtils.randomBytes(20);

        baseDataSource.put(randomKey, TestUtils.randomBytes(20));
        dataSourceWithCache.get(randomKey);
        dataSourceWithCache.get(randomKey);

        try (DataSourceKeyIterator iterator = dataSourceWithCache.keyIterator()){
            assertTrue(iterator.hasNext());
            assertArrayEquals(iterator.next(), randomKey);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        verify(baseDataSource, times(1)).get(any(byte[].class));

        dataSourceWithCache.flush();
        dataSourceWithCache.get(randomKey);
        verify(baseDataSource, times(1)).get(any(byte[].class));
    }

    @Test
    void getAfterModification() {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        baseDataSource.put(randomKey, randomValue);
        byte[] modifiedRandomValue = Arrays.copyOf(randomValue, randomValue.length);
        modifiedRandomValue[modifiedRandomValue.length - 1] += 1;

        dataSourceWithCache.put(randomKey, modifiedRandomValue);

        dataSourceWithCache.get(randomKey);
        verify(baseDataSource, never()).get(any(byte[].class));

        dataSourceWithCache.flush();
        dataSourceWithCache.get(randomKey);
        verify(baseDataSource, never()).get(any(byte[].class));
    }

    @Test
    void getAfterDeletion() {
        byte[] randomKey = TestUtils.randomBytes(20);
        baseDataSource.put(randomKey, TestUtils.randomBytes(20));

        dataSourceWithCache.delete(randomKey);

        dataSourceWithCache.get(randomKey);
        verify(baseDataSource, never()).get(any(byte[].class));

        dataSourceWithCache.flush();
        dataSourceWithCache.get(randomKey);
        verify(baseDataSource, never()).get(any(byte[].class));
    }

    /**
     * Note: we cannot exhaustively verify baseDataSource#get access b/c on flush all the uncommittedCache is dumped
     * into the underlying layer and it's impossible to establish which entries stayed in the committedCache due to the
     * {@link java.util.LinkedHashMap#putAll(Map)} eviction semantic
     */
    @Test
    void getWithFullCache() {
        int expectedMisses = 1;
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE + expectedMisses);
        dataSourceWithCache.updateBatch(initialEntries, Collections.emptySet());
        dataSourceWithCache.flush();

        for (ByteArrayWrapper key : initialEntries.keySet()) {
            MatcherAssert.assertThat(dataSourceWithCache.get(key.getData()), is(initialEntries.get(key)));
        }

        verify(baseDataSource, atLeast(expectedMisses)).get(any(byte[].class));
    }

    @Test
    void put() {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        dataSourceWithCache.put(randomKey, randomValue);
        MatcherAssert.assertThat(baseDataSource.get(randomKey), is(nullValue()));

        dataSourceWithCache.flush();
        MatcherAssert.assertThat(baseDataSource.get(randomKey), is(randomValue));
    }

    @Test
    void putNull() {
        byte[] key = TestUtils.generateBytes(this.getClass(), "key", 20);
        Assertions.assertThrows(NullPointerException.class, () -> dataSourceWithCache.put(key, null));
    }

    @Test
    void putCachedValueReturnsCachedValue() {
        byte[] key = TestUtils.generateBytes(this.getClass(), "key", 20);
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value = TestUtils.generateBytes(this.getClass(), "value", 20);

        Map<ByteArrayWrapper, byte[]> committedCache = spy(new HashMap<>());
        committedCache.put(wrappedKey, value);
        TestUtils.setInternalState(dataSourceWithCache, "committedCache", committedCache);

        Assertions.assertEquals(value, dataSourceWithCache.put(key, value));
        verify(committedCache, times(1)).get(wrappedKey);
        verify(committedCache, never()).remove(any(ByteArrayWrapper.class));
    }

    @Test
    void putTwoKeyValuesWrittenInOrder() {
        InOrder order = inOrder(baseDataSource);

        byte[] randomKey1 = TestUtils.randomBytes(20);
        byte[] randomValue1 = TestUtils.randomBytes(20);
        byte[] randomKey2 = TestUtils.randomBytes(20);
        byte[] randomValue2 = TestUtils.randomBytes(20);

        dataSourceWithCache.put(randomKey1, randomValue1);
        dataSourceWithCache.put(randomKey2, randomValue2);

        dataSourceWithCache.flush();

        order.verify(baseDataSource).put(randomKey1, randomValue1);
        order.verify(baseDataSource).put(randomKey2, randomValue2);
    }

    @Test
    void putLockWorks() {
        ReentrantReadWriteLock lock = TestUtils.getInternalState(dataSourceWithCache, "lock");
        lock.writeLock().lock();
        boolean unlocked = false;

        try {
            Map<ByteArrayWrapper, byte[]> committedCache = spy(new HashMap<>());
            TestUtils.setInternalState(dataSourceWithCache, "committedCache", committedCache);

            byte[] key1 = TestUtils.generateBytes(this.getClass(), "key1", 20);
            ByteArrayWrapper randomKeyWrapped = ByteUtil.wrap(key1);
            byte[] value1 = TestUtils.generateBytes(this.getClass(), "value1",20);
            byte[] valueAfterLock = TestUtils.generateBytes(this.getClass(), "valueAfterLock",20);
            committedCache.put(randomKeyWrapped, value1); // to check how many times remove is called

            AtomicBoolean threadStarted = new AtomicBoolean(false);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(new Thread(() -> {
                threadStarted.set(true);
                dataSourceWithCache.put(key1, valueAfterLock);
            }));

            // wait for thread to be started and put a value while thread is locked
            Awaitility.await().timeout(Duration.ofMillis(1000)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, equalTo(true));
            dataSourceWithCache.put(key1, value1);
            verify(committedCache, times(1)).get(any(ByteArrayWrapper.class)); // not called from thread yet
            verify(committedCache, times(0)).remove(randomKeyWrapped); // not called, it was in committedCache

            lock.writeLock().unlock();
            unlocked = true;

            future.get(500, TimeUnit.MILLISECONDS); // would throw assertion errors in thread if any
            Assertions.assertArrayEquals(dataSourceWithCache.get(key1), valueAfterLock); // prevailing value should be the last one being put
            verify(committedCache, times(2)).get(randomKeyWrapped); // called from thread now also
            verify(committedCache, times(1)).remove(randomKeyWrapped); // called from thread after updating the value
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Assertions.fail(e.getMessage());
        } finally {
            if (!unlocked) {
                lock.readLock().unlock();
            }
        }
    }

    @Test
    void keys() {
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE);
        Set<byte[]> initialKeys = initialEntries.keySet().stream().map(ByteArrayWrapper::getData).collect(Collectors.toSet());
        baseDataSource.updateBatch(initialEntries, Collections.emptySet());

        Set<byte[]> datasourceSet = dataSourceWithCache.keys().stream()
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toCollection(HashSet::new));

        MatcherAssert.assertThat(datasourceSet, is(initialKeys));

        byte[] keyNotIncluded = TestUtils.randomBytes(20);
        dataSourceWithCache.get(keyNotIncluded);
        Assertions.assertFalse(datasourceSet.contains(keyNotIncluded));

        // ensure "contains" behavior is checked
        assertFalse(datasourceSet.contains(keyNotIncluded));
        assertTrue(datasourceSet.contains(initialKeys.iterator().next()));
    }

    @Test
    void keysAfterPut() {
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE);
        baseDataSource.updateBatch(initialEntries, Collections.emptySet());

        byte[] keyIncluded = TestUtils.randomBytes(20);
        dataSourceWithCache.put(keyIncluded, TestUtils.randomBytes(20));

        Set<byte[]> datasourceSet = dataSourceWithCache.keys().stream()
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toCollection(HashSet::new));
        MatcherAssert.assertThat(datasourceSet, hasItem(keyIncluded));

        // ensure "contains" behavior is checked
        byte[] keyNotIncluded = TestUtils.randomBytes(20);
        assertFalse(datasourceSet.contains(keyNotIncluded));
        assertTrue(datasourceSet.contains(keyIncluded));
    }

    @Test
    void keysAfterDelete() {
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE);
        baseDataSource.updateBatch(initialEntries, Collections.emptySet());

        Iterator<ByteArrayWrapper> initialEntriesIterator = initialEntries.keySet().iterator();

        byte[] keyToRemove = initialEntriesIterator.next().getData();
        dataSourceWithCache.delete(keyToRemove);

        Set<byte[]> datasourceSet = dataSourceWithCache.keys().stream()
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toCollection(HashSet::new));
        Assertions.assertFalse(datasourceSet.contains(keyToRemove));

        // ensure "contains" behavior is checked
        assertFalse(datasourceSet.contains(keyToRemove));
        byte[] keyIncluded = initialEntriesIterator.next().getData();
        assertTrue(datasourceSet.contains(keyIncluded));
    }

    @Test
    void delete() {
        byte[] randomKey = TestUtils.randomBytes(20);
        baseDataSource.put(randomKey, TestUtils.randomBytes(20));

        dataSourceWithCache.delete(randomKey);
        dataSourceWithCache.flush();

        MatcherAssert.assertThat(baseDataSource.get(randomKey), is(nullValue()));
    }

    @Test
    void deleteNonExistentCachedKey() {
        byte[] randomKey = TestUtils.randomBytes(20);

        // force caching non existing value
        dataSourceWithCache.get(randomKey);
        dataSourceWithCache.delete(randomKey);
        dataSourceWithCache.flush();

        ArgumentCaptor<Set<ByteArrayWrapper>> keysToDeleteArgument = ArgumentCaptor.forClass((Class) Set.class);
        verify(baseDataSource, times(1)).updateBatch(anyMap(), keysToDeleteArgument.capture());
        MatcherAssert.assertThat(keysToDeleteArgument.getValue(), is(empty()));
    }

    @Test
    void deleteUnknownKey() {
        byte[] randomKey = TestUtils.randomBytes(20);

        dataSourceWithCache.delete(randomKey);
        dataSourceWithCache.flush();

        ArgumentCaptor<Set<ByteArrayWrapper>> keysToDeleteArgument = ArgumentCaptor.forClass((Class) Set.class);
        verify(baseDataSource, times(1)).updateBatch(anyMap(), keysToDeleteArgument.capture());
        Set<ByteArrayWrapper> keysToDelete = keysToDeleteArgument.getValue();
        MatcherAssert.assertThat(keysToDelete, hasSize(1));
        MatcherAssert.assertThat(keysToDelete, hasItem(ByteUtil.wrap(randomKey)));
    }

    @Test
    void updateBatch() {
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE);
        baseDataSource.updateBatch(initialEntries, Collections.emptySet());

        Set<ByteArrayWrapper> keysToBatchRemove = initialEntries.keySet().stream().limit(CACHE_SIZE / 2).collect(Collectors.toSet());
        dataSourceWithCache.updateBatch(Collections.emptyMap(), keysToBatchRemove);
        dataSourceWithCache.flush();

        for (ByteArrayWrapper removedKey : keysToBatchRemove) {
            MatcherAssert.assertThat(baseDataSource.get(removedKey.getData()), is(nullValue()));
        }
    }

    @Test
    void checkCacheSnapshotLoadTriggered() {
        CacheSnapshotHandler cacheSnapshotHandler = mock(CacheSnapshotHandler.class);
        new DataSourceWithCache(baseDataSource, CACHE_SIZE, cacheSnapshotHandler);

        verify(cacheSnapshotHandler, atLeastOnce()).load(anyMap());
    }

    @Test
    void checkCacheSnapshotSaveTriggered() {
        CacheSnapshotHandler cacheSnapshotHandler = mock(CacheSnapshotHandler.class);
        DataSourceWithCache dataSourceWithCache = new DataSourceWithCache(baseDataSource, CACHE_SIZE, cacheSnapshotHandler);

        dataSourceWithCache.close();

        verify(cacheSnapshotHandler, atLeastOnce()).save(anyMap());
    }

    @Test
    void flush() {
        byte[] baseKey1 = TestUtils.generateBytes(this.getClass(), "baseKey1", 20);
        ByteArrayWrapper baseKeyWrapped = ByteUtil.wrap(baseKey1);
        byte[] baseValue1 = TestUtils.generateBytes(this.getClass(), "baseValue1",20);
        baseDataSource.put(baseKey1, baseValue1);
        dataSourceWithCache.get(baseKey1); // this should put baseKey1 into committedCache

        byte[] baseKeyToDelete = TestUtils.generateBytes(this.getClass(), "baseKeyToDelete", 20);
        ByteArrayWrapper baseKeyToDeleteWrapped = ByteUtil.wrap(baseKeyToDelete);
        byte[] baseValueToDelete = TestUtils.generateBytes(this.getClass(), "baseValueToDelete", 20);
        baseDataSource.put(baseKeyToDelete, baseValueToDelete);
        dataSourceWithCache.get(baseKeyToDelete); // this should put the key in committedCache
        dataSourceWithCache.delete(baseKeyToDelete); // this should remove the key from committedCache

        byte[] newKey = TestUtils.generateBytes(this.getClass(), "newKey", 20);
        ByteArrayWrapper newKeyWrapped = ByteUtil.wrap(newKey);
        byte[] newValue = TestUtils.generateBytes(this.getClass(), "newValue", 20);
        dataSourceWithCache.put(newKey, newValue); // this should add the key to uncommittedCache

        byte[] newKeyToDelete = TestUtils.generateBytes(this.getClass(), "newKeyToDelete", 20);
        ByteArrayWrapper newKeyToDeleteWrapped = ByteUtil.wrap(newKeyToDelete);
        byte[] newValueToDelete = TestUtils.generateBytes(this.getClass(), "newValueToDelete", 20);
        dataSourceWithCache.put(newKeyToDelete, newValueToDelete); // this should add the key to uncommittedCache
        dataSourceWithCache.delete(newKeyToDelete); // this should mark the key for deletion

        Map<ByteArrayWrapper, byte[]> uncommittedCache = TestUtils.getInternalState(dataSourceWithCache, "uncommittedCache");
        Assertions.assertEquals(3, uncommittedCache.size());

        dataSourceWithCache.flush();

        Map<ByteArrayWrapper, byte[]> uncommittedBatch = Collections.singletonMap(newKeyWrapped, newValue);
        Set<ByteArrayWrapper> uncommittedKeysToRemove = new HashSet<>(Arrays.asList(newKeyToDeleteWrapped, baseKeyToDeleteWrapped));

        verify(baseDataSource, times(1)).updateBatch(uncommittedBatch, uncommittedKeysToRemove);

        Map<ByteArrayWrapper, byte[]> committedCache = TestUtils.getInternalState(dataSourceWithCache, "committedCache");
        Assertions.assertEquals(committedCache.get(baseKeyWrapped), baseValue1);
        Assertions.assertEquals(committedCache.get(newKeyWrapped), newValue);
        Assertions.assertNull(committedCache.get(newKeyToDeleteWrapped));
        Assertions.assertNull(committedCache.get(baseKeyToDeleteWrapped));
        Assertions.assertEquals(4, committedCache.size());

        uncommittedCache = TestUtils.getInternalState(dataSourceWithCache, "uncommittedCache");
        Assertions.assertTrue(uncommittedCache.isEmpty());

        Assertions.assertEquals(baseDataSource.get(baseKey1), baseValue1);
        Assertions.assertEquals(baseDataSource.get(newKey), newValue);
        Assertions.assertNull(baseDataSource.get(baseKeyToDelete));
        Assertions.assertNull(baseDataSource.get(newKeyToDelete));
    }

    @Test
    void flushLockWorks() {
        ReentrantReadWriteLock lock = TestUtils.getInternalState(dataSourceWithCache, "lock");
        lock.writeLock().lock();
        boolean unlocked = false;

        try {
            AtomicBoolean threadStarted = new AtomicBoolean(false);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(new Thread(() -> {
                threadStarted.set(true);
                dataSourceWithCache.flush();
            }));

            // wait for thread to be started and flush while thread is locked
            Awaitility.await().timeout(Duration.ofMillis(1000)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, equalTo(true));
            verify(baseDataSource, never()).updateBatch(any(), any()); // thread without the lock waits

            dataSourceWithCache.flush();
            verify(baseDataSource, times(1)).updateBatch(any(), any()); // thread with the lock succeeds instantly

            lock.writeLock().unlock();
            unlocked = true;

            future.get(500, TimeUnit.MILLISECONDS); // would throw assertion errors in thread if any
            verify(baseDataSource, times(2)).updateBatch(any(), any()); // thread without the lock finally gets it
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Assertions.fail(e.getMessage());
        } finally {
            if (!unlocked) {
                lock.readLock().unlock();
            }
        }
    }

    private Map<ByteArrayWrapper, byte[]> generateRandomValuesToUpdate(int maxValuesToCreate) {
        Map<ByteArrayWrapper, byte[]> updatedValues = new HashMap<>();
        for (int i = 0; i < maxValuesToCreate; i++) {
            updatedValues.put(ByteUtil.wrap(TestUtils.randomBytes(20)), TestUtils.randomBytes(20));
        }
        return updatedValues;
    }
}
