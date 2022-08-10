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

public class DataSourceWithCachePreventWritesTest {

    private static final int CACHE_SIZE = 0; // should be ignored

    private HashMapDB baseDataSource;
    private DataSourceWithCache dataSourceWithCache;

    @Before
    public void setupDataSources() {
        this.baseDataSource = spy(new HashMapDB());
        this.dataSourceWithCache = new DataSourceWithCache(baseDataSource, CACHE_SIZE,null,true);
    }



    @Test
    public void neverPutsOrDeletes() {
        byte[] randomKey = TestUtils.randomBytes(20);
        byte[] randomValue = TestUtils.randomBytes(20);

        baseDataSource.put(randomKey, randomValue);
        verify(baseDataSource, times(1)).put(any(byte[].class),any(byte[].class));


        // very that the pass-through is actually working
        byte[] ret =dataSourceWithCache.get(randomKey);
        assertThat(ret, is(randomValue));

        // now change the value
        byte[] modifiedRandomValue = Arrays.copyOf(randomValue, randomValue.length);
        modifiedRandomValue[modifiedRandomValue.length - 1] += 1;

        // write modified value
        dataSourceWithCache.put(randomKey, modifiedRandomValue);

        // verify that the value was changed
        ret =dataSourceWithCache.get(randomKey);
        assertThat(ret, is(modifiedRandomValue));

        // try to write to base
        dataSourceWithCache.flush();
        // Make sure the change has not being passed to the base data source
        verify(baseDataSource, times(1)).put(any(byte[].class),any(byte[].class));

        // we can also re-check here that the base still has the previous value
        ret =baseDataSource.get(randomKey);
        assertThat(ret, is(randomValue));

        // delete it
        dataSourceWithCache.delete(randomKey);
        dataSourceWithCache.flush();

        ret =dataSourceWithCache.get(randomKey);
        assertEquals(ret, null);
        verify(baseDataSource, times(1)).put(any(byte[].class),any(byte[].class));

    }

    @Test
    public void getWithFullCache() {
        int expectedMisses = 1;
        Map<ByteArrayWrapper, byte[]> initialEntries = generateRandomValuesToUpdate(CACHE_SIZE + expectedMisses);
        dataSourceWithCache.updateBatch(initialEntries, Collections.emptySet());
        dataSourceWithCache.flush();

        for (ByteArrayWrapper key : initialEntries.keySet()) {
            assertThat(dataSourceWithCache.get(key.getData()), is(initialEntries.get(key)));
        }
        verify(baseDataSource, never()).put(any(byte[].class),any(byte[].class));
    }

    private Map<ByteArrayWrapper, byte[]> generateRandomValuesToUpdate(int maxValuesToCreate) {
        Map<ByteArrayWrapper, byte[]> updatedValues = new HashMap<>();
        for (int i = 0; i < maxValuesToCreate; i++) {
            updatedValues.put(ByteUtil.wrap(TestUtils.randomBytes(20)), TestUtils.randomBytes(20));
        }
        return updatedValues;
    }
}
