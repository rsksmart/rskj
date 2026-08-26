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

import co.rsk.core.types.bytes.Bytes;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.MetricKind;
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
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.System.getProperty;

public class RocksDbDataSource implements KeyValueDataSource {

    private static final Long GENERAL_SIZE = 10L * 1024L * 1024L;
    private static final int MAX_RETRIES = 2;

    // Bulk-sync oriented RocksDB tuning. Every value can be overridden with a -D system property so
    // that a node can be tuned without a rebuild.
    private static final long WRITE_BUFFER_SIZE =
            Long.getLong("rocksdb.writeBufferSizeMb", 128L) * 1024L * 1024L;
    private static final int MAX_WRITE_BUFFER_NUMBER =
            Integer.getInteger("rocksdb.maxWriteBufferNumber", 4);
    private static final int MIN_WRITE_BUFFER_NUMBER_TO_MERGE =
            Integer.getInteger("rocksdb.minWriteBufferNumberToMerge", 2);
    private static final long BLOCK_CACHE_SIZE =
            Long.getLong("rocksdb.blockCacheSizeMb", 1024L) * 1024L * 1024L;
    private static final int MAX_BACKGROUND_JOBS =
            Integer.getInteger("rocksdb.maxBackgroundJobs", 8);
    private static final int BLOOM_BITS_PER_KEY =
            Integer.getInteger("rocksdb.bloomBitsPerKey", 10);
    private static final int BLOCK_SIZE =
            Integer.getInteger("rocksdb.blockSizeKb", 16) * 1024;
    private static final boolean PARANOID_CHECKS =
            Boolean.parseBoolean(System.getProperty("rocksdb.paranoidChecks", "false"));
    private static final boolean DISABLE_WAL =
            Boolean.parseBoolean(System.getProperty("rocksdb.disableWAL", "false"));

    static {
        // The shared cache and filter below are native objects, so the RocksDB library has to be
        // loaded before they are constructed. Stock code got away without this because its only
        // RocksDB field was per-instance, initialised well after class loading.
        RocksDB.loadLibrary();
    }

    // A single block cache and a single filter policy shared by every datasource. Without this each
    // datasource silently gets its own small default cache, and none of them get a bloom filter at
    // all, which turns every trie point lookup into a scan across the LSM levels.
    private static final Cache SHARED_BLOCK_CACHE = new LRUCache(BLOCK_CACHE_SIZE);
    private static final Filter SHARED_BLOOM_FILTER = new BloomFilter(BLOOM_BITS_PER_KEY, false);

    private static final Logger logger = LoggerFactory.getLogger("db");
    private static final Profiler profiler = ProfilerFactory.getInstance();
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final String databaseDir;
    private final String name;

    private final Options options = createOptions();
    private final WriteOptions writeOptions = createWriteOptions();
    private RocksDB db;
    private boolean alive;

    // The native LevelDB insert/update/delete are normally thread-safe
    // However close operation is not thread-safe and may lead to a native crash when
    // accessing a closed DB.
    // The rocksdbJNI lib has a protection over accessing closed DB, but it is not synchronized
    // This ReadWriteLock still permits concurrent execution of insert/delete/update operations
    // however blocks them on init/close/delete operations
    private final ReadWriteLock resetDbLock = new ReentrantReadWriteLock();

    public RocksDbDataSource(String name, String databaseDir) {
        this.databaseDir = databaseDir;
        this.name = name;
        logger.info("New RocksDbDataSource: {}", name);
    }

    @Override
    public void init() {
        resetDbLock.writeLock().lock();
        Metric metric = profiler.start(MetricKind.DB_INIT);

        try {
            logger.debug("~> RocksDbDataSource.init(): {}", name);

            if (isAlive()) {
                return;
            }

            Objects.requireNonNull(name, "no name set to the db");

            logger.debug("Opening database");
            Path dbPath = getPathForName(name, databaseDir);

            Files.createDirectories(dbPath.getParent());

            logger.debug("Initializing new or existing database: '{}'", name);
            openDb(dbPath);

            logger.debug("<~ RocksDbDataSource.init(): {}", name);
        } catch (RocksDBException ioe) {
            logger.error(ioe.getMessage(), ioe);
            panicProcessor.panic("rocksdb", ioe.getMessage());
            throw new RuntimeException("Can't initialize rocksdb");
        } catch (IOException ioe) {
            logger.error(ioe.getMessage(), ioe);
            panicProcessor.panic("rocksdb", ioe.getMessage());
            throw new RuntimeException("Can't initialize database");
        } finally {
            profiler.stop(metric);
            resetDbLock.writeLock().unlock();
        }
    }

    private void openDb(Path dbPath) throws RocksDBException {
        db = RocksDB.open(options, dbPath.toString());

        alive = true;
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

        byte[] result = null;

        Metric metric = profiler.start(MetricKind.DB_READ);
        resetDbLock.readLock().lock();

        int retries = 0;
        Exception exCaught = null;

        try {
            while (retries < MAX_RETRIES) {
                try {
                    if (logger.isTraceEnabled()) {
                        logger.trace("~> RocksDbDataSource.get(): {}, key: {}", name, Bytes.of(key));
                    }

                    byte[] ret = db.get(key);

                    if (logger.isTraceEnabled()) {
                        logger.trace("<~ RocksDbDataSource.get(): {}, key: {}, return length: {}", name, Bytes.of(key), (ret == null ? "null" : ret.length));
                    }

                    result = ret;
                    break;
                } catch (RocksDBException e) {
                    logger.error("Exception. Retrying again...", e);
                    exCaught = e;
                }

                retries++;
            }
        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }

        if (exCaught != null && retries > 1) {
            logger.error("Exception. Not retrying.", exCaught);
            panicProcessor.panic("rocksdb", String.format("Exception. Not retrying. %s", exCaught.getMessage()));
            throw new RuntimeException("Couldn't get the data back for the given key");
        }

        return result;
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        Metric metric = profiler.start(MetricKind.DB_WRITE);
        resetDbLock.readLock().lock();

        try {
            if (logger.isTraceEnabled()) {
                logger.trace("~> RocksDbDataSource.put(): {}, key: {}, return length: {}", name, Bytes.of(key), value.length);
            }

            db.put(key, value);

            if (logger.isTraceEnabled()) {
                logger.trace("<~ RocksDbDataSource.put(): {}, key: {}, return length: {}", name, Bytes.of(key), value.length);
            }
        } catch (RocksDBException e) {
            logger.error("Exception. Not retrying.", e);
            throw new RuntimeException(e);
        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }

        return value;
    }

    @Override
    public void delete(byte[] key) {
        Metric metric = profiler.start(MetricKind.DB_WRITE);
        resetDbLock.readLock().lock();

        try {
            if (logger.isTraceEnabled()) {
                logger.trace("~> RocksDbDataSource.delete(): {}, key: {}", name, Bytes.of(key));
            }

            db.delete(key);
            if (logger.isTraceEnabled()) {
                logger.trace("<~ RocksDbDataSource.delete(): {}, key: {}", name, Bytes.of(key));
            }

        } catch (RocksDBException e) {
            logger.error("Exception. Not retrying.", e);
            throw new RuntimeException(e);
        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }
    }

    @Override
    public DataSourceKeyIterator keyIterator() {
        return new RocksDbKeyIterator(this.db);
    }

    @Override
    public Set<ByteArrayWrapper> keys() {
        if (logger.isTraceEnabled()) {
            logger.trace("~> RocksDbDataSource.keys(): {}", name);
        }

        Set<ByteArrayWrapper> result = new HashSet<>();

        Metric metric = profiler.start(MetricKind.DB_READ);
        resetDbLock.readLock().lock();

        try (RocksIterator iterator = db.newIterator()) {

            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                result.add(ByteUtil.wrap(iterator.key()));
            }

            if (logger.isTraceEnabled()) {
                logger.trace("<~ RocksDbDataSource.keys(): {}, {}", name, result.size());
            }
        } finally {
            resetDbLock.readLock().unlock();
            profiler.stop(metric);
        }

        return result;
    }

    private void updateBatchInternal(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> deleteKeys) {
        Metric metric = profiler.start(MetricKind.DB_WRITE);
        if (rows.containsKey(null) || rows.containsValue(null)) {
            profiler.stop(metric);
            throw new IllegalArgumentException("Cannot update null values");
        }
        // Note that this is not atomic.
        try (WriteBatch batch = new WriteBatch()) {
            for (Map.Entry<ByteArrayWrapper, byte[]> entry : rows.entrySet()) {
                batch.put(entry.getKey().getData(), entry.getValue());
            }
            for (ByteArrayWrapper deleteKey : deleteKeys) {
                batch.delete(deleteKey.getData());
            }
            db.write(writeOptions, batch);
        } catch (RocksDBException e) {
            logger.error("Exception. Not retrying.", e);
            throw new RuntimeException(e);
        } finally {
            profiler.stop(metric);
        }

    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> deleteKeys) {
        if (rows.containsKey(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        if (logger.isTraceEnabled()) {
            logger.trace("~> RocksDbDataSource.updateBatch(): {}, {}", name, rows.size());
        }

        resetDbLock.readLock().lock();

        int retries = 0;
        RuntimeException exCaught = null;

        try {
            while (retries < MAX_RETRIES) {
                try {
                    updateBatchInternal(rows, deleteKeys);

                    if (logger.isTraceEnabled()) {
                        logger.trace("<~ RocksDbDataSource.updateBatch(): {}, {}", name, rows.size());
                    }

                    break;
                } catch (RuntimeException e) {
                    logger.error("Exception. Retrying again...", e);
                    exCaught = e;
                }

                retries++;
            }
        } finally {
            resetDbLock.readLock().unlock();
        }


        if (exCaught != null && retries > 1) {
            logger.error("Exception. Not retrying.", exCaught);
            panicProcessor.panic("rocksdb", String.format("Error %s", exCaught.getMessage()));
            throw exCaught;
        }
    }

    @Override
    public void close() {
        Metric metric = profiler.start(MetricKind.DB_CLOSE);
        resetDbLock.writeLock().lock();
        try {
            if (!isAlive()) {
                return;
            }

            logger.debug("Close db: {}", name);
            db.close();

            alive = false;
        } finally {
            resetDbLock.writeLock().unlock();
            profiler.stop(metric);
        }
    }

    @Override
    public void flush() {
        // All is flushed immediately: there is no uncommittedCache to flush
    }

    private static Options createOptions() {
        Options options = new Options();
        options.setCreateIfMissing(true);
        options.setCompressionType(CompressionType.NO_COMPRESSION);
        options.setArenaBlockSize(GENERAL_SIZE);

        // Bigger memtables mean fewer, larger L0 files and therefore much less compaction work
        // while importing millions of blocks.
        options.setWriteBufferSize(WRITE_BUFFER_SIZE);
        options.setMaxWriteBufferNumber(MAX_WRITE_BUFFER_NUMBER);
        options.setMinWriteBufferNumberToMerge(MIN_WRITE_BUFFER_NUMBER_TO_MERGE);

        // Compaction is the main background cost of a bulk import; give it more than one thread.
        options.setMaxBackgroundJobs(MAX_BACKGROUND_JOBS);
        options.setLevelCompactionDynamicLevelBytes(true);
        options.setBytesPerSync(4L * 1024L * 1024L);
        options.setCompactionReadaheadSize(2L * 1024L * 1024L);

        // Paranoid checks re-verify data during opens and compactions. Useful, but it is pure
        // overhead on a node that is rebuilding its whole database from the network.
        options.setParanoidChecks(PARANOID_CHECKS);

        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig();
        tableConfig.setBlockCache(SHARED_BLOCK_CACHE);
        // The trie datasource is a pure random-key point-lookup workload; without a bloom filter
        // every miss has to touch an SST on each level.
        tableConfig.setFilterPolicy(SHARED_BLOOM_FILTER);
        tableConfig.setWholeKeyFiltering(true);
        tableConfig.setCacheIndexAndFilterBlocks(true);
        tableConfig.setPinL0FilterAndIndexBlocksInCache(true);
        tableConfig.setBlockSize(BLOCK_SIZE);
        options.setTableFormatConfig(tableConfig);

        return options;
    }

    private static WriteOptions createWriteOptions() {
        WriteOptions writeOptions = new WriteOptions();
        // The WAL doubles the bytes written per block. It can be turned off while rebuilding the
        // database from scratch, where a crash simply means resyncing.
        writeOptions.setDisableWAL(DISABLE_WAL);
        return writeOptions;
    }
}
