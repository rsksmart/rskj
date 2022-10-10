/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.datasource;

import org.awaitility.Awaitility;
import org.ethereum.TestUtils;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.ethereum.TestUtils.randomBytes;
import static org.junit.jupiter.api.Assertions.*;

class LevelDbDataSourceTest {

    @TempDir
    public Path databaseDir;

    private LevelDbDataSource dataSource;

    @BeforeEach
    public void setUp() throws IOException {
        dataSource = new LevelDbDataSource("test", databaseDir.toString());
        dataSource.init();
    }

    @AfterEach
    public void tearDown() {
        dataSource.close();
    }

    @Test
    public void testBatchUpdating() {
        final int batchSize = 100;
        Map<ByteArrayWrapper, byte[]> batch = createBatch(batchSize);

        dataSource.updateBatch(batch, Collections.emptySet());

        assertEquals(batchSize, dataSource.keys().size());

        try (DataSourceKeyIterator iterator = dataSource.keyIterator()){
            assertTrue(iterator.hasNext());
            assertNotNull(iterator.next());
        } catch (Exception e) {
            fail(e.getMessage());
        }

        dataSource.close();
    }

    @Test
    public void testPutting() {
        byte[] key = randomBytes(32);
        dataSource.put(key, randomBytes(32));

        assertNotNull(dataSource.get(key));
        assertEquals(1, dataSource.keys().size());
    }

    @Test
    public void getAfterMiss() {
        byte[] key = randomBytes(32);
        Assertions.assertNull(dataSource.get(key));

        byte[] value = randomBytes(32);
        dataSource.put(key, value);
        Assertions.assertArrayEquals(dataSource.get(key), value);

        dataSource.flush();
        Assertions.assertArrayEquals(dataSource.get(key), value);
    }

    @Test
    public void getAfterUpdate() {
        byte[] key = randomBytes(32);
        byte[] value = randomBytes(32);

        dataSource.put(key, value);
        Assertions.assertArrayEquals(dataSource.get(key), value);

        byte[] newValue = randomBytes(32);
        dataSource.put(key, newValue);
        Assertions.assertArrayEquals(dataSource.get(key), newValue);

        dataSource.flush();
        Assertions.assertArrayEquals(dataSource.get(key), newValue);
    }

    @Test
    public void getAfterDelete() {
        byte[] key = randomBytes(32);
        byte[] value = randomBytes(32);

        dataSource.put(key, value);
        Assertions.assertArrayEquals(dataSource.get(key), value);

        dataSource.delete(key);
        Assertions.assertNull(dataSource.get(key));

        dataSource.flush();
        Assertions.assertNull(dataSource.get(key));
    }

    @Test
    public void getWithException() {
        DB db = Mockito.mock(DB.class);
        TestUtils.setInternalState(dataSource, "db", db);

        byte[] key = TestUtils.randomBytes(20);
        DBException fakeException = new DBException("fake exception");
        Mockito.when(db.get(key)).thenThrow(fakeException);
        DBException dbExceptionThrown = Assertions.assertThrows(DBException.class, () -> dataSource.get(key));
        Assertions.assertEquals(fakeException, dbExceptionThrown);
        Mockito.verify(db, Mockito.times(2)).get(key);
    }

    @Test
    public void getLockWorks() {
        ReentrantReadWriteLock lock = TestUtils.getInternalState(dataSource, "resetDbLock");
        lock.writeLock().lock();

        byte[] key = TestUtils.randomBytes(20);
        byte[] value = TestUtils.randomBytes(20);

        AtomicBoolean threadStarted = new AtomicBoolean(false);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(new Thread(() -> {
            threadStarted.set(true);
            byte[] readValue = dataSource.get(key); // should be locked
            Assertions.assertArrayEquals(value, readValue); // should read value put during lock
        }));

        // wait for thread to be started and put a value during active lock for thread
        Awaitility.await().timeout(Duration.ofMillis(100)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, CoreMatchers.equalTo(true));
        dataSource.put(key, value); // put value during thread lock

        lock.writeLock().unlock(); // release the lock

        try {
            future.get(500, TimeUnit.MILLISECONDS); // would throw assertion errors in thread if any
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Assertions.fail("No threading exception should've happened");
        }
    }


    private static Map<ByteArrayWrapper, byte[]> createBatch(int batchSize) {
        HashMap<ByteArrayWrapper, byte[]> result = new HashMap<>();
        for (int i = 0; i < batchSize; i++) {
            result.put(ByteUtil.wrap(randomBytes(32)), randomBytes(32));
        }
        return result;
    }

}
