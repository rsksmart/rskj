/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.System.getProperty;

public class RocksDbDataSource implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("db");
    private static final Profiler profiler = ProfilerFactory.getInstance();
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    static {
        RocksDB.loadLibrary();
    }

    private final String databaseDir;
    private final String name;
    private RocksDB db;
    private boolean alive;

    // The native LevelDB insert/update/delete are normally thread-safe
    // However close operation is not thread-safe and may lead to a native crash when
    // accessing a closed DB.
    // The leveldbJNI lib has a protection over accessing closed DB but it is not synchronized
    // This ReadWriteLock still permits concurrent execution of insert/delete/update operations
    // however blocks them on init/close/delete operations
    // TODO: check if this lock is needed
    private final ReadWriteLock resetDbLock = new ReentrantReadWriteLock();

    public RocksDbDataSource(String name, String databaseDir) {
        this.databaseDir = databaseDir;
        this.name = name;
        logger.debug("New RocksDbDataSource: {}", name);
    }

    @Override
    public void init() {
        resetDbLock.writeLock().lock();
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.LEVEL_DB_INIT);
        try {
            logger.debug("~> RocksDbDataSource.init(): {}", name);

            if (isAlive()) {
                return;
            }

            Objects.requireNonNull(name, "no name set to the db");

            Options options = new Options();
            options.setCreateIfMissing(true);
            options.setCompressionType(CompressionType.NO_COMPRESSION);
//            options.setArenaBlockSize(10 * 1024 * 1024); TODO: check if this is needed
            options.setWriteBufferSize(10 * 1024 * 1024);
//            options.cacheSize(0); TODO: check if this is needed
            options.setParanoidChecks(true);
//            options.verifyChecksums(true); TODO: check if this is needed

            try {

                logger.debug("Opening database");
                Path dbPath = getPathForName(name, databaseDir);

                Files.createDirectories(dbPath.getParent());

                logger.debug("Initializing new or existing database: '{}'", name);
                db = RocksDB.open(options, dbPath.toString());

                alive = true;
            } catch (IOException | RocksDBException e) {
                logger.error(e.getMessage(), e);
                panicProcessor.panic("rocksdb", e.getMessage());
                throw new DataSourceException("Can't initialize database", e);
            }
            logger.debug("<~ RocksDbDataSource.init(): {}", name);
        } finally {
            profiler.stop(metric);
            resetDbLock.writeLock().unlock();
        }
    }

    private static Path getPathForName(String name, String databaseDir) {
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
                logger.trace("~> RocksDbDataSource.get(): {}, key: {}", name,  ByteUtil.toHexString(key));
            }

            if (!alive) {
                throw new DataSourceException("Db is not alive");
            }

            try {
                byte[] ret = db.get(key);
                if (logger.isTraceEnabled()) {
                    logger.trace("<~ RocksDbDataSource.get(): {}, key: {}, return length: {}", name, ByteUtil.toHexString(key), (ret == null ? "null" : ret.length));
                }

                return ret;
            } catch (RocksDBException e) {
                logger.error("Exception. Retrying again...", e);
                try {
                    byte[] ret = db.get(key);
                    if (logger.isTraceEnabled()) {
                        logger.trace("<~ RocksDbDataSource.get(): {}, key: {}, return length: {}", name, ByteUtil.toHexString(key), (ret == null ? "null" : ret.length));
                    }

                    return ret;
                } catch (RocksDBException e2) {
                    logger.error("Exception. Not retrying.", e2);
                    panicProcessor.panic("leveldb", String.format("Exception. Not retrying. %s", e2.getMessage()));
                    throw new DataSourceException("Get op failed", e2);
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
                logger.trace("~> RocksDbDataSource.put(): {}, key: {}, return length: {}", name, ByteUtil.toHexString(key), value.length);
            }

            if (!alive) {
                throw new DataSourceException("Db is not alive");
            }

            db.put(key, value);
            if (logger.isTraceEnabled()) {
                logger.trace("<~ RocksDbDataSource.put(): {}, key: {}, return length: {}", name, ByteUtil.toHexString(key), value.length);
            }

            return value;
        } catch (RocksDBException e) {
            logger.error("Exception. Not retrying.", e);
            panicProcessor.panic("leveldb", String.format("Exception. Not retrying. %s", e.getMessage()));
            throw new DataSourceException("Put op failed", e);
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
                logger.trace("~> RocksDbDataSource.delete(): {}, key: {}", name, ByteUtil.toHexString(key));
            }

            if (!alive) {
                throw new DataSourceException("Db is not alive");
            }

            db.delete(key);
            if (logger.isTraceEnabled()) {
                logger.trace("<~ RocksDbDataSource.delete(): {}, key: {}", name, ByteUtil.toHexString(key));
            }

        } catch (RocksDBException e) {
            logger.error("Exception. Not retrying.", e);
            panicProcessor.panic("leveldb", String.format("Exception. Not retrying. %s", e.getMessage()));
            throw new DataSourceException("Delete op failed", e);
        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }
    }

    @Override
    public Set<byte[]> keys() {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.DB_READ);
        resetDbLock.readLock().lock();
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("~> RocksDbDataSource.keys(): {}", name);
            }

            if (!alive) {
                throw new DataSourceException("Db is not alive");
            }

            try (RocksIterator iterator = db.newIterator()) {
                Set<byte[]> result = new HashSet<>();
                iterator.seekToFirst();
                while (iterator.isValid()) {
                    result.add(iterator.key());
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("<~ RocksDbDataSource.keys(): {}, {}", name, result.size());
                }

                return result;
            } catch (RuntimeException e) {
                logger.error("Unexpected", e);
                panicProcessor.panic("leveldb", String.format("Unexpected %s", e.getMessage()));
                throw new DataSourceException("Cannot retrieve keys", e);
            }
        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }
    }

    private void updateBatchInternal(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> deleteKeys) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.DB_WRITE);
        if (rows.containsKey(null) || rows.containsValue(null)) {
            profiler.stop(metric);
            throw new IllegalArgumentException("Cannot update null values");
        }
        // Note that this is not atomic.
        try (WriteBatch batch = new WriteBatch()) {
            WriteOptions writeOpts = new WriteOptions();

            for (Map.Entry<ByteArrayWrapper, byte[]> entry : rows.entrySet()) {
                batch.put(entry.getKey().getData(), entry.getValue());
            }
            for (ByteArrayWrapper deleteKey : deleteKeys) {
                batch.delete(deleteKey.getData());
            }
            db.write(writeOpts, batch);
            profiler.stop(metric);
        } catch (RocksDBException e) {
            logger.error("Exception. Not retrying.", e);
            panicProcessor.panic("leveldb", String.format("Exception. Not retrying. %s", e.getMessage()));
            throw new DataSourceException("Update batch op failed", e);
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
                logger.trace("~> RocksDbDataSource.updateBatch(): {}, {}", name, rows.size());
            }

            if (!alive) {
                throw new DataSourceException("Db is not alive");
            }

            try {
                updateBatchInternal(rows, deleteKeys);
                if (logger.isTraceEnabled()) {
                    logger.trace("<~ RocksDbDataSource.updateBatch(): {}, {}", name, rows.size());
                }
            } catch (IllegalArgumentException iae) {
                throw iae;
            } catch (RuntimeException e) {
                logger.error("Error, retrying one more time...", e);
                // try one more time
                try {
                    updateBatchInternal(rows, deleteKeys);
                    if (logger.isTraceEnabled()) {
                        logger.trace("<~ RocksDbDataSource.updateBatch(): {}, {}", name, rows.size());
                    }
                } catch (IllegalArgumentException iae) {
                    throw iae;
                } catch (RuntimeException e1) {
                    logger.error("Error", e);
                    panicProcessor.panic("leveldb", String.format("Error %s", e.getMessage()));
                    throw e1;
                }
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.LEVEL_DB_CLOSE);
        resetDbLock.writeLock().lock();
        try {
            if (!isAlive()) {
                return;
            }

            try {
                logger.debug("Close db: {}", name);
                db.close();

                alive = false;
            } catch (RuntimeException e) {
                logger.error("Failed to find the db file on the close: {} ", name);
                panicProcessor.panic("leveldb", String.format("Failed to find the db file on the close: %s", name));
            }
        } finally {
            resetDbLock.writeLock().unlock();
            profiler.stop(metric);
        }
    }

    @Override
    public void flush(){
        // All is flushed immediately: there is no uncommittedCache to flush
    }
}
