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

import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.panic.PanicProcessor;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.iq80.leveldb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.System.getProperty;
import static org.fusesource.leveldbjni.JniDBFactory.factory;

public class LevelDbDataSource implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("db");
    private static final Profiler profiler = ProfilerFactory.getInstance();
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final String databaseDir;
    private final String name;
    private DB db;
    private boolean alive;

    // The native LevelDB insert/update/delete are normally thread-safe
    // However close operation is not thread-safe and may lead to a native crash when
    // accessing a closed DB.
    // The leveldbJNI lib has a protection over accessing closed DB but it is not synchronized
    // This ReadWriteLock still permits concurrent execution of insert/delete/update operations
    // however blocks them on init/close/delete operations
    private final ReadWriteLock resetDbLock = new ReentrantReadWriteLock();

    public LevelDbDataSource(String name, String databaseDir) {
        this.databaseDir = databaseDir;
        this.name = name;
        logger.debug("New LevelDbDataSource: {}", name);
    }

    public static KeyValueDataSource makeDataSource(Path datasourcePath) {
        KeyValueDataSource ds = new LevelDbDataSource(datasourcePath.getFileName().toString(), datasourcePath.getParent().toString());
        ds.init();
        return ds;
    }

    @Override
    public void init() {
        resetDbLock.writeLock().lock();
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.DB_INIT);
        try {
            logger.debug("~> LevelDbDataSource.init(): {}", name);

            if (isAlive()) {
                return;
            }

            Objects.requireNonNull(name, "no name set to the db");

            Options options = new Options();
            options.createIfMissing(true);
            options.compressionType(CompressionType.NONE);
            options.blockSize(10 * 1024 * 1024);
            options.writeBufferSize(10 * 1024 * 1024);
            options.cacheSize(0);
            options.paranoidChecks(true);
            options.verifyChecksums(true);

            try {

                logger.debug("Opening database");
                Path dbPath = getPathForName(name, databaseDir);

                Files.createDirectories(dbPath.getParent());

                logger.debug("Initializing new or existing database: '{}'", name);
                db = factory.open(dbPath.toFile(), options);

                alive = true;
            } catch (IOException ioe) {
                logger.error(ioe.getMessage(), ioe);
                panicProcessor.panic("leveldb", ioe.getMessage());
                throw new RuntimeException("Can't initialize database");
            }
            logger.debug("<~ LevelDbDataSource.init(): {}", name);
        } finally {
            profiler.stop(metric);
            resetDbLock.writeLock().unlock();
        }
    }

    public static Path getPathForName(String name, String databaseDir) {
        if (Paths.get(databaseDir).isAbsolute()) {
            return Paths.get(databaseDir, name);
        } else {
            return Paths.get(getProperty("user.dir"), databaseDir, name);
        }
    }

    @Override
    public boolean isAlive() {
        try {
            resetDbLock.readLock().lock();
            return alive;
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.DB_READ);
        resetDbLock.readLock().lock();
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("~> LevelDbDataSource.get(): {}, key: {}", name, ByteUtil.toHexString(key));
            }

            try {
                byte[] ret = db.get(key);
                if (logger.isTraceEnabled()) {
                    logger.trace("<~ LevelDbDataSource.get(): {}, key: {}, return length: {}", name, ByteUtil.toHexString(key), (ret == null ? "null" : ret.length));
                }

                return ret;
            } catch (DBException e) {
                logger.error("Exception. Retrying again...", e);
                try {
                    byte[] ret = db.get(key);
                    if (logger.isTraceEnabled()) {
                        logger.trace("<~ LevelDbDataSource.get(): {}, key: {}, return length: {}", name, ByteUtil.toHexString(key), (ret == null ? "null" : ret.length));
                    }

                    return ret;
                } catch (DBException e2) {
                    logger.error("Exception. Not retrying.", e2);
                    panicProcessor.panic("leveldb", String.format("Exception. Not retrying. %s", e2.getMessage()));
                    throw e2;
                }
            }
        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        Metric metric = profiler.start(Profiler.PROFILING_TYPE.DB_WRITE);
        resetDbLock.readLock().lock();
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("~> LevelDbDataSource.put(): {}, key: {}, return length: {}", name, ByteUtil.toHexString(key), value.length);
            }

            db.put(key, value);
            if (logger.isTraceEnabled()) {
                logger.trace("<~ LevelDbDataSource.put(): {}, key: {}, return length: {}", name, ByteUtil.toHexString(key), value.length);
            }

            return value;
        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }
    }

    @Override
    public void delete(byte[] key) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.DB_WRITE);
        resetDbLock.readLock().lock();
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("~> LevelDbDataSource.delete(): {}, key: {}", name, ByteUtil.toHexString(key));
            }

            db.delete(key);
            if (logger.isTraceEnabled()) {
                logger.trace("<~ LevelDbDataSource.delete(): {}, key: {}", name, ByteUtil.toHexString(key));
            }

        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }
    }

    @Override
    public Set<ByteArrayWrapper> keys() {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.DB_READ);
        resetDbLock.readLock().lock();
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("~> LevelDbDataSource.keys(): {}", name);
            }

            try (DBIterator iterator = db.iterator()) {
                Set<ByteArrayWrapper> result = new HashSet<>();
                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    byte[] key = iterator.peekNext().getKey();
                    result.add(ByteUtil.wrap(key));
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("<~ LevelDbDataSource.keys(): {}, {}", name, result.size());
                }

                return result;
            } catch (IOException e) {
                logger.error("Unexpected", e);
                panicProcessor.panic("leveldb", String.format("Unexpected %s", e.getMessage()));
                throw new RuntimeException(e);
            }
        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }
    }

    private void updateBatchInternal(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> deleteKeys) throws IOException {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.DB_WRITE);
        if (rows.containsKey(null) || rows.containsValue(null)) {
            profiler.stop(metric);
            throw new IllegalArgumentException("Cannot update null values");
        }
        // Note that this is not atomic.
        try (WriteBatch batch = db.createWriteBatch()) {
            for (Map.Entry<ByteArrayWrapper, byte[]> entry : rows.entrySet()) {
                batch.put(entry.getKey().getData(), entry.getValue());
            }
            for (ByteArrayWrapper deleteKey : deleteKeys) {
                batch.delete(deleteKey.getData());
            }
            db.write(batch);
            profiler.stop(metric);
        }

    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> deleteKeys) {
        if (rows.containsKey(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }
        resetDbLock.readLock().lock();
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("~> LevelDbDataSource.updateBatch(): {}, {}", name, rows.size());
            }

            try {
                updateBatchInternal(rows, deleteKeys);
                if (logger.isTraceEnabled()) {
                    logger.trace("<~ LevelDbDataSource.updateBatch(): {}, {}", name, rows.size());
                }
            } catch (IllegalArgumentException iae) {
                throw iae;
            } catch (Exception e) {
                logger.error("Error, retrying one more time...", e);
                // try one more time
                try {
                    updateBatchInternal(rows, deleteKeys);
                    if (logger.isTraceEnabled()) {
                        logger.trace("<~ LevelDbDataSource.updateBatch(): {}, {}", name, rows.size());
                    }
                } catch (IllegalArgumentException iae) {
                    throw iae;
                } catch (Exception e1) {
                    logger.error("Error", e);
                    panicProcessor.panic("leveldb", String.format("Error %s", e.getMessage()));
                    throw new RuntimeException(e);
                }
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.DB_CLOSE);
        resetDbLock.writeLock().lock();
        try {
            if (!isAlive()) {
                return;
            }

            try {
                logger.debug("Close db: {}", name);
                db.close();

                alive = false;
            } catch (IOException e) {
                logger.error("Failed to find the db file on the close: {} ", name);
                panicProcessor.panic("leveldb", String.format("Failed to find the db file on the close: %s", name));
            }
        } finally {
            resetDbLock.writeLock().unlock();
            profiler.stop(metric);
        }
    }

    @Override
    public void flush() {
        // All is flushed immediately: there is no uncommittedCache to flush
    }
}
