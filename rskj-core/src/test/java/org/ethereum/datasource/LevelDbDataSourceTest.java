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

import com.google.common.collect.ImmutableSet;
import org.awaitility.Awaitility;
import org.ethereum.TestUtils;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.WriteBatch;
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
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

class LevelDbDataSourceTest {
    @TempDir
    public Path databaseDir;

    private LevelDbDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new LevelDbDataSource("test", databaseDir.toString());
        dataSource.init();
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void updateBatchRetriesOnPutError() {
        DB db = Mockito.mock(DB.class);
        TestUtils.setInternalState(dataSource, "db", db);

        WriteBatch writeBatch = mock(WriteBatch.class);
        when(db.createWriteBatch()).thenReturn(writeBatch);
        String dbExceptionMessage = "fake DBException";
        when(writeBatch.put(any(), any())).thenThrow(new DBException(dbExceptionMessage));

        final int batchSize = 100;
        Map<ByteArrayWrapper, byte[]> batch = createBatch(batchSize);

        Set<ByteArrayWrapper> deleteKeys = Collections.emptySet();
        RuntimeException updateException = Assertions.assertThrows(RuntimeException.class, () -> dataSource.updateBatch(batch, deleteKeys));
        Assertions.assertEquals(DBException.class.getName() + ": " + dbExceptionMessage, updateException.getMessage());

        verify(writeBatch, times(2)).put(any(), any());
    }

    @Test
    void updateBatchRetriesOnDeleteError() {
        DB db = Mockito.mock(DB.class);
        TestUtils.setInternalState(dataSource, "db", db);

        WriteBatch writeBatch = mock(WriteBatch.class);
        when(db.createWriteBatch()).thenReturn(writeBatch);
        String dbExceptionMessage = "fake DBException";
        when(writeBatch.delete(any())).thenThrow(new DBException(dbExceptionMessage));

        final int batchSize = 100;
        Map<ByteArrayWrapper, byte[]> batch = createBatch(batchSize);

        byte[] keyToDelete1 = randomBytes(32);
        byte[] keyToDelete2 = randomBytes(32);
        Set<ByteArrayWrapper> deleteKeys = ImmutableSet.of(ByteUtil.wrap(keyToDelete1), ByteUtil.wrap(keyToDelete2));

        RuntimeException updateException = Assertions.assertThrows(RuntimeException.class, () -> dataSource.updateBatch(batch, deleteKeys));
        Assertions.assertEquals(DBException.class.getName() + ": " + dbExceptionMessage, updateException.getMessage());

        verify(writeBatch, times(2)).delete(any());
    }

    @Test
    void putLockWorks() {
        ReentrantReadWriteLock lock = TestUtils.getInternalState(dataSource, "resetDbLock");
        lock.writeLock().lock(); // we test write-locking because readLock() would allow multiple "read" access
        boolean unlocked = false;

        try {
            byte[] key = randomBytes(32);
            byte[] initialValue = randomBytes(32);
            byte[] updatedValue = randomBytes(32);

            AtomicBoolean threadStarted = new AtomicBoolean(false);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(new Thread(() -> {
                threadStarted.set(true);
                dataSource.put(key, updatedValue);
            }));

            // wait for thread to be started and put a new value on thread holding the write lock
            Awaitility.await().timeout(Duration.ofMillis(100)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, equalTo(true));
            Assertions.assertNull(dataSource.get(key)); // thread put should have not been executed during lock
            dataSource.put(key, initialValue);
            Assertions.assertArrayEquals(initialValue, dataSource.get(key)); // thread put should have not been executed during write lock

            lock.writeLock().unlock();
            unlocked = true;

            future.get(500, TimeUnit.MILLISECONDS); // would throw assertion errors in thread if any
            Assertions.assertArrayEquals(updatedValue, dataSource.get(key)); // thread put should prevail as last one being run
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Assertions.fail(e.getMessage());
        } finally {
            if (!unlocked) {
                lock.writeLock().unlock();
            }
        }
    }

    @Test
    void getWithException() {
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
    void getLockWorks() {
        ReentrantReadWriteLock lock = TestUtils.getInternalState(dataSource, "resetDbLock");
        lock.writeLock().lock(); // we test write-locking because readLock() would allow multiple "read" access
        boolean unlocked = false;

        try {
            byte[] key = TestUtils.randomBytes(20);
            byte[] value = TestUtils.randomBytes(20); // TODO:I use generateBytes for all random usages in my changes

            AtomicBoolean threadStarted = new AtomicBoolean(false);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(new Thread(() -> {
                threadStarted.set(true);
                byte[] readValue = dataSource.get(key); // should be locked
                Assertions.assertArrayEquals(value, readValue); // should read value put during lock
            }));

            // wait for thread to be started and put a value while thread is locked
            Awaitility.await().timeout(Duration.ofMillis(1000)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, equalTo(true));
            dataSource.put(key, value); // put value during write lock
            lock.writeLock().unlock(); // release write lock, so future can start read
            unlocked = true;

            future.get(500, TimeUnit.MILLISECONDS); // would throw assertion errors in thread if any
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Assertions.fail(e.getMessage());
        } finally {
            if (!unlocked) {
                lock.readLock().unlock();
            }
        }
    }

    @Test
    void deleteLockWorks() {
        ReentrantReadWriteLock lock = TestUtils.getInternalState(dataSource, "resetDbLock");
        lock.writeLock().lock(); // we test write-locking because readLock() would allow multiple "read" access
        boolean unlocked = false;

        try {
            byte[] key = randomBytes(32);
            byte[] value = randomBytes(32);

            AtomicBoolean threadStarted = new AtomicBoolean(false);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(new Thread(() -> {
                threadStarted.set(true);
                Assertions.assertNotNull(dataSource.get(key));
                dataSource.delete(key);
                Assertions.assertNull(dataSource.get(key));
            }));

            // wait for thread to be started and put a new value on thread holding the write lock
            Awaitility.await().timeout(Duration.ofMillis(100)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, equalTo(true));
            Assertions.assertNull(dataSource.get(key)); // thread put should have not been executed during lock
            dataSource.put(key, value);
            Assertions.assertArrayEquals(value, dataSource.get(key)); // thread put should have not been executed during write lock

            Assertions.assertNotNull(dataSource.get(key));
            lock.writeLock().unlock();
            unlocked = true;

            future.get(500, TimeUnit.MILLISECONDS); // would throw assertion errors in thread if any
            Assertions.assertNull(dataSource.get(key)); // thread put should prevail as last one being run
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Assertions.fail(e.getMessage());
        } finally {
            if (!unlocked) {
                lock.writeLock().unlock();
            }
        }
    }

    @Test
    void keysDBThrows() throws IOException {
        DB db = Mockito.mock(DB.class);
        TestUtils.setInternalState(dataSource, "db", db);

        DBIterator dbIterator = mock(DBIterator.class);
        when(db.iterator()).thenReturn(dbIterator);

        String exceptionMessage = "close threw";
        doThrow(new IOException(exceptionMessage)).when(dbIterator).close();
        RuntimeException rte = Assertions.assertThrows(RuntimeException.class, () -> dataSource.keys());
        Assertions.assertEquals(IOException.class.getName() + ": " + exceptionMessage, rte.getMessage());
    }

    @Test
    void keysLockWorks() {
        ReentrantReadWriteLock lock = TestUtils.getInternalState(dataSource, "resetDbLock");
        lock.writeLock().lock(); // we test write-locking because readLock() would allow multiple "read" access
        boolean unlocked = false;

        byte[] key1 = TestUtils.randomBytes(20);
        byte[] value1 = TestUtils.randomBytes(20);
        byte[] key2 = TestUtils.randomBytes(20);
        byte[] value2 = TestUtils.randomBytes(20);

        dataSource.put(key1, value1);

        Set<ByteArrayWrapper> expectedKeysBeforeThread = new HashSet<>();
        expectedKeysBeforeThread.add(ByteUtil.wrap(key1));

        Set<ByteArrayWrapper> expectedKeysOnThread = new HashSet<>();
        expectedKeysOnThread.add(ByteUtil.wrap(key1));
        expectedKeysOnThread.add(ByteUtil.wrap(key2));

        try {
            AtomicBoolean threadStarted = new AtomicBoolean(false);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(new Thread(() -> {
                threadStarted.set(true);
                Assertions.assertEquals(expectedKeysOnThread, dataSource.keys());
            }));

            // wait for thread to be started and put a value while thread is locked
            Awaitility.await().timeout(Duration.ofMillis(100)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, equalTo(true));
            Assertions.assertEquals(expectedKeysBeforeThread, dataSource.keys());
            dataSource.put(key2, value2);

            lock.writeLock().unlock();
            unlocked = true;

            future.get(500, TimeUnit.MILLISECONDS); // would throw assertion errors in thread if any
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Assertions.fail(e.getMessage());
        } finally {
            if (!unlocked) {
                lock.readLock().unlock();
            }
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
