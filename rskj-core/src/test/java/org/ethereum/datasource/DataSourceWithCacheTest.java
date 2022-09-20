package org.ethereum.datasource;

import org.ethereum.TestUtils;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

public class DataSourceWithCacheTest {

    protected static final int CACHE_SIZE = 15;

    protected HashMapDB baseDataSource;
    protected DataSourceWithCache dataSourceWithCache;

    @Before
    public void setupDataSources() {
        this.baseDataSource = spy(new HashMapDB());
        this.dataSourceWithCache = DataSourceWithCache.create(baseDataSource, CACHE_SIZE);
    }

    /**
     * Checks that the base is acceded once
     */
    @Test
    public void getAfterMiss() {
        byte[] randomKey = TestUtils.randomBytes(20);

        baseDataSource.put(randomKey, TestUtils.randomBytes(20));
        dataSourceWithCache.get(randomKey);
        dataSourceWithCache.get(randomKey);

        verify(baseDataSource, times(1)).get(any(byte[].class));

        dataSourceWithCache.flush();
        dataSourceWithCache.get(randomKey);
        verify(baseDataSource, times(1)).get(any(byte[].class));
    }

    @Test
    public void getAfterModification() {
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
    public void getAfterDeletion() {
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
    public void getWithFullCache() {
        int expectedMisses = 1;
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE + expectedMisses);
        dataSourceWithCache.updateBatch(initialEntries, Collections.emptySet());
        dataSourceWithCache.flush();

        for (ByteArrayWrapper key : initialEntries.keySet()) {
            assertThat(dataSourceWithCache.get(key.getData()), is(initialEntries.get(key)));
        }

        verify(baseDataSource, atLeast(expectedMisses)).get(any(byte[].class));
    }

    @Test
    public void put() {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        dataSourceWithCache.put(randomKey, randomValue);
        assertThat(baseDataSource.get(randomKey), is(nullValue()));

        dataSourceWithCache.flush();
        assertThat(baseDataSource.get(randomKey), is(randomValue));
    }

    @Test
    public void putTwoKeyValuesWrittenInOrder() {
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
    public void keys() {
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE);
        Set<byte[]> initialKeys = initialEntries.keySet().stream().map(ByteArrayWrapper::getData).collect(Collectors.toSet());
        baseDataSource.updateBatch(initialEntries, Collections.emptySet());

        Set<byte[]> datasourceSet = dataSourceWithCache.keys().stream()
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toCollection(HashSet::new));

        assertThat(datasourceSet, is(initialKeys));

        byte[] keyNotIncluded = TestUtils.randomBytes(20);
        dataSourceWithCache.get(keyNotIncluded);
        assertThat(datasourceSet, not(hasItem(keyNotIncluded)));

        // ensure "contains" behavior is checked
        assertFalse(datasourceSet.contains(keyNotIncluded));
        assertTrue(datasourceSet.contains(initialKeys.iterator().next()));
    }

    @Test
    public void keysAfterPut() {
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE);
        baseDataSource.updateBatch(initialEntries, Collections.emptySet());

        byte[] keyIncluded = TestUtils.randomBytes(20);
        dataSourceWithCache.put(keyIncluded, TestUtils.randomBytes(20));

        Set<byte[]> datasourceSet = dataSourceWithCache.keys().stream()
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toCollection(HashSet::new));
        assertThat(datasourceSet, hasItem(keyIncluded));

        // ensure "contains" behavior is checked
        byte[] keyNotIncluded = TestUtils.randomBytes(20);
        assertFalse(datasourceSet.contains(keyNotIncluded));
        assertTrue(datasourceSet.contains(keyIncluded));
    }

    @Test
    public void keysAfterDelete() {
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE);
        baseDataSource.updateBatch(initialEntries, Collections.emptySet());

        Iterator<ByteArrayWrapper> initialEntriesIterator = initialEntries.keySet().iterator();

        byte[] keyToRemove = initialEntriesIterator.next().getData();
        dataSourceWithCache.delete(keyToRemove);

        Set<byte[]> datasourceSet = dataSourceWithCache.keys().stream()
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toCollection(HashSet::new));
        assertThat(datasourceSet, not(hasItem(keyToRemove)));

        // ensure "contains" behavior is checked
        assertFalse(datasourceSet.contains(keyToRemove));
        byte[] keyIncluded = initialEntriesIterator.next().getData();
        assertTrue(datasourceSet.contains(keyIncluded));
    }

    @Test
    public void delete() {
        byte[] randomKey = TestUtils.randomBytes(20);
        baseDataSource.put(randomKey, TestUtils.randomBytes(20));

        dataSourceWithCache.delete(randomKey);
        dataSourceWithCache.flush();

        assertThat(baseDataSource.get(randomKey), is(nullValue()));
    }

    @Test
    public void deleteNonExistentCachedKey() {
        byte[] randomKey = TestUtils.randomBytes(20);

        // force caching non existing value
        dataSourceWithCache.get(randomKey);
        dataSourceWithCache.delete(randomKey);
        dataSourceWithCache.flush();

        ArgumentCaptor<Set<ByteArrayWrapper>> keysToDeleteArgument = ArgumentCaptor.forClass((Class) Set.class);
        verify(baseDataSource, times(1)).updateBatch(anyMap(), keysToDeleteArgument.capture());
        assertThat(keysToDeleteArgument.getValue(), is(empty()));
    }

    @Test
    public void deleteUnknownKey() {
        byte[] randomKey = TestUtils.randomBytes(20);

        dataSourceWithCache.delete(randomKey);
        dataSourceWithCache.flush();

        ArgumentCaptor<Set<ByteArrayWrapper>> keysToDeleteArgument = ArgumentCaptor.forClass((Class) Set.class);
        verify(baseDataSource, times(1)).updateBatch(anyMap(), keysToDeleteArgument.capture());
        Set<ByteArrayWrapper> keysToDelete = keysToDeleteArgument.getValue();
        assertThat(keysToDelete, hasSize(1));
        assertThat(keysToDelete, hasItem(ByteUtil.wrap(randomKey)));
    }

    @Test
    public void updateBatch() {
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE);
        baseDataSource.updateBatch(initialEntries, Collections.emptySet());

        Set<ByteArrayWrapper> keysToBatchRemove = initialEntries.keySet().stream().limit(CACHE_SIZE / 2).collect(Collectors.toSet());
        dataSourceWithCache.updateBatch(Collections.emptyMap(), keysToBatchRemove);
        dataSourceWithCache.flush();

        for (ByteArrayWrapper removedKey : keysToBatchRemove) {
            assertThat(baseDataSource.get(removedKey.getData()), is(nullValue()));
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
