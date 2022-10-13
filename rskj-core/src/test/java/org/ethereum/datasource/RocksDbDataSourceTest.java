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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.ethereum.TestUtils.assertThrows;
import static org.ethereum.TestUtils.randomBytes;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.*;

class RocksDbDataSourceTest {

    @TempDir
    public Path databaseDir;

    private RocksDbDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new RocksDbDataSource("test", databaseDir.toString());
        dataSource.init();
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void updateBatchRetriesOnPutError() {
        RocksDB db = Mockito.mock(RocksDB.class);
        TestUtils.setInternalState(dataSource, "db", db);

        String dbExceptionMessage = "fake DBException";

        final int batchSize = 100;
        Map<ByteArrayWrapper, byte[]> batch = createBatch(batchSize);

        Set<ByteArrayWrapper> deleteKeys = Collections.emptySet();

        try (MockedConstruction<WriteBatch> writeBatchMockedConstruction = Mockito.mockConstruction(WriteBatch.class,
                (mock, context) -> doThrow(new RocksDBException(dbExceptionMessage)).when(mock).put(any(byte[].class), any(byte[].class))
        )) {
            RuntimeException updateException = assertThrows(RuntimeException.class, () -> dataSource.updateBatch(batch, deleteKeys));
            assertEquals(RocksDBException.class.getName() + ": " + dbExceptionMessage, updateException.getMessage());

            WriteBatch writeBatch1 = writeBatchMockedConstruction.constructed().get(1);
            verify(writeBatch1, times(1)).put(any(byte[].class), any(byte[].class));
            WriteBatch writeBatch2 = writeBatchMockedConstruction.constructed().get(1);
            verify(writeBatch2, times(1)).put(any(byte[].class), any(byte[].class));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void updateBatchRetriesOnDeleteError() {
        RocksDB db = Mockito.mock(RocksDB.class);
        TestUtils.setInternalState(dataSource, "db", db);

        String dbExceptionMessage = "fake DBException";

        final int batchSize = 100;
        Map<ByteArrayWrapper, byte[]> batch = createBatch(batchSize);

        byte[] keyToDelete1 = randomBytes(32);
        byte[] keyToDelete2 = randomBytes(32);
        Set<ByteArrayWrapper> deleteKeys = ImmutableSet.of(ByteUtil.wrap(keyToDelete1), ByteUtil.wrap(keyToDelete2));

        try (MockedConstruction<WriteBatch> writeBatchMockedConstruction = Mockito.mockConstruction(WriteBatch.class,
                (mock, context) -> doThrow(new RocksDBException(dbExceptionMessage)).when(mock).delete(any())
        )) {
            RuntimeException updateException = assertThrows(RuntimeException.class, () -> dataSource.updateBatch(batch, deleteKeys));
            assertEquals(RocksDBException.class.getName() + ": " + dbExceptionMessage, updateException.getMessage());

            WriteBatch writeBatch1 = writeBatchMockedConstruction.constructed().get(1);
            verify(writeBatch1, times(1)).delete(any());
            WriteBatch writeBatch2 = writeBatchMockedConstruction.constructed().get(1);
            verify(writeBatch2, times(1)).delete(any());
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
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
            Awaitility.await().timeout(Duration.ofMillis(1000)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, equalTo(true));
            Assertions.assertNull(dataSource.get(key)); // thread put should have not been executed during lock
            dataSource.put(key, initialValue);
            assertArrayEquals(initialValue, dataSource.get(key)); // thread put should have not been executed during write lock

            lock.writeLock().unlock();
            unlocked = true;

            future.get(500, TimeUnit.MILLISECONDS); // would throw assertion errors in thread if any
            assertArrayEquals(updatedValue, dataSource.get(key)); // thread put should prevail as last one being run
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail(e.getMessage());
        } finally {
            if (!unlocked) {
                lock.writeLock().unlock();
            }
        }
    }

    @Test
    void getWithException() throws RocksDBException {
        RocksDB db = Mockito.mock(RocksDB.class);
        TestUtils.setInternalState(dataSource, "db", db);

        byte[] key = TestUtils.randomBytes(20);
        RocksDBException fakeException = new RocksDBException("fake exception");
        Mockito.when(db.get(key)).thenThrow(fakeException);
        Assertions.assertThrows(RuntimeException.class, () -> dataSource.get(key));
        Mockito.verify(db, Mockito.times(2)).get(key);
    }

    @Test
    void getLockWorks() {
        ReentrantReadWriteLock lock = TestUtils.getInternalState(dataSource, "resetDbLock");
        lock.writeLock().lock(); // we test write-locking because readLock() would allow multiple "read" access
        boolean unlocked = false;

        try {
            byte[] key = TestUtils.randomBytes(20);
            byte[] value = TestUtils.randomBytes(20);

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
            Awaitility.await().timeout(Duration.ofMillis(1000)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, equalTo(true));
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
            Awaitility.await().timeout(Duration.ofMillis(1000)).pollDelay(Duration.ofMillis(10)).untilAtomic(threadStarted, equalTo(true));
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
