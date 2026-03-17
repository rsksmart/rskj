/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.util.FormatUtils;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataSourceWithCache implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("datasourcewithcache");
    private final DataSourceWithCacheMetrics metrics;
    private final int cacheSize;
    private final KeyValueDataSource base;
    private final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    private final Map<ByteArrayWrapper, byte[]> committedCache;

    private final AtomicInteger numOfPuts = new AtomicInteger();
    private final AtomicInteger numOfGets = new AtomicInteger();
    private final AtomicInteger numOfGetsFromStore = new AtomicInteger();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Nullable
    private final CacheSnapshotHandler cacheSnapshotHandler;

    public DataSourceWithCache(@Nonnull KeyValueDataSource base, int cacheSize, String owner) {
        this(base, cacheSize, null, owner);
    }

    public DataSourceWithCache(@Nonnull KeyValueDataSource base, int cacheSize,
                               @Nullable CacheSnapshotHandler cacheSnapshotHandler, String owner) {
        this.cacheSize = cacheSize;
        this.base = Objects.requireNonNull(base);
        String instanceId = Integer.toHexString(System.identityHashCode(this));
        this.uncommittedCache = new LinkedHashMap<>(cacheSize / 8, (float) 0.75, false);
        this.committedCache = Collections.synchronizedMap(makeCommittedCache(cacheSize, cacheSnapshotHandler));
        this.cacheSnapshotHandler = cacheSnapshotHandler;
        this.metrics = new DataSourceWithCacheMetrics(
                logger,
                getName() + "#" + instanceId,
                owner,
                () -> committedCache.size(),
                () -> uncommittedCache.size(),
                10_000 // emit every 10k ops
        );
    }

    @Override
    public byte[] get(byte[] key) {
        metrics.onUserReadGet();
        Objects.requireNonNull(key);

        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;

        this.lock.readLock().lock();

        try {
            metrics.onCacheCommittedReadContains();
            if (committedCache.containsKey(wrappedKey)) {
                metrics.onCacheCommittedReadGet();
                byte[] result = committedCache.get(wrappedKey);
                metrics.onUserReadGetFromCommittedCache(result == null);
                return result;
            }
            metrics.onCacheUncommittedReadContains();
            if (uncommittedCache.containsKey(wrappedKey)) {
                metrics.onCacheUncommittedReadGet();
                byte[] result = uncommittedCache.get(wrappedKey);
                metrics.onUserReadGetFromUncommittedCache(result == null);
                return result;
            }

            long start = System.nanoTime();
            value = base.get(key);
            long nanos = System.nanoTime() - start;
            metrics.onUserReadGetFromStore(nanos, value==null);
            if (traceEnabled) {
                numOfGetsFromStore.incrementAndGet();
            }

            //null value, as expected, is allowed here to be stored in committedCache
            metrics.onCacheCommittedWritePut();
            committedCache.put(wrappedKey, value);
            metrics.onReadThroughFillCommittedFromStore(value == null);
        } finally {
            if (traceEnabled) {
                numOfGets.incrementAndGet();
            }

            this.lock.readLock().unlock();
        }

        return value;
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        return put(wrappedKey, value);
    }

    private byte[] put(ByteArrayWrapper wrappedKey, byte[] value) {
        Objects.requireNonNull(value);
        metrics.onUserWritePut();

        this.lock.writeLock().lock();

        try {
            // here I could check for equal data or just move to the uncommittedCache.
            metrics.onCacheCommittedReadGet();
            byte[] priorValue = committedCache.get(wrappedKey);
            if (priorValue != null && Arrays.equals(priorValue, value)) {
                return value;
            }
            metrics.onCacheCommittedWriteRemove();
            committedCache.remove(wrappedKey);
            this.putKeyValue(wrappedKey, value);
        } finally {
            if (logger.isTraceEnabled()) {
                numOfPuts.incrementAndGet();
            }

            this.lock.writeLock().unlock();
        }

        return value;
    }

    private void putKeyValue(ByteArrayWrapper key, byte[] value) {
        uncommittedCache.put(key, value);
        metrics.onCacheUncommittedWritePut();

        if (uncommittedCache.size() > cacheSize) {
            long start = System.nanoTime();
            flushNotManual();
            long nanos = System.nanoTime() - start;
            metrics.onUserWriteFlush(DataSourceWithCacheMetrics.FlushReason.SIZE, nanos);
        }
    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        this.lock.writeLock().lock();
        metrics.onUserWriteDelete();

        try {
            // always mark for deletion if we don't know the state in the underlying store
            metrics.onCacheCommittedReadContains();
            if (!committedCache.containsKey(wrappedKey)) {
                this.putKeyValue(wrappedKey, null);
                return;
            }
            metrics.onCacheCommittedReadGet();
            byte[] valueToRemove = committedCache.get(wrappedKey);

            // a null value means we know for a fact that the key doesn't exist in the underlying store, so this is a noop
            if (valueToRemove != null) {
                this.putKeyValue(wrappedKey, null);
                metrics.onCacheCommittedWriteRemove();
                committedCache.remove(wrappedKey);

            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public Set<ByteArrayWrapper> keys() {
        Stream<ByteArrayWrapper> baseKeys;
        Stream<ByteArrayWrapper> committedKeys;
        Stream<ByteArrayWrapper> uncommittedKeys;
        Set<ByteArrayWrapper> uncommittedKeysToRemove;

        this.lock.readLock().lock();

        try {
            baseKeys = base.keys().stream();
            committedKeys = committedCache.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey);
            uncommittedKeys = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey);
            uncommittedKeysToRemove = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() == null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        } finally {
            this.lock.readLock().unlock();
        }

        Stream<ByteArrayWrapper> knownKeys = Stream.concat(Stream.concat(baseKeys, committedKeys), uncommittedKeys);
        return knownKeys.filter(k -> !uncommittedKeysToRemove.contains(k))
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public DataSourceKeyIterator keyIterator() {
        if(!uncommittedCache.isEmpty()) {
            throw new IllegalStateException("There are uncommitted keys");
        }

        return new DefaultKeyIterator(base.keys());
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
        rows.keySet().removeAll(keysToRemove);
        long start = System.nanoTime();
        this.lock.writeLock().lock();

        try {
            rows.forEach(this::put);
            keysToRemove.forEach(this::delete);
        } finally {
            this.lock.writeLock().unlock();
            long nanos = System.nanoTime() - start;
            metrics.onUserWriteBatch(nanos);
        }
    }

    @Override
    public void flush() {
        long start = System.nanoTime();
        Map<ByteArrayWrapper, byte[]> uncommittedBatch = new LinkedHashMap<>();

        this.lock.writeLock().lock();

        try {
            long saveTime = System.nanoTime();

            this.uncommittedCache.forEach((key, value) -> {
                if (value != null) {
                    uncommittedBatch.put(key, value);
                }
            });
            long updateBatchTime = System.nanoTime();
            Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toSet());
            base.updateBatch(uncommittedBatch, uncommittedKeysToRemove);
            metrics.onStoreFlushBatchUpdate(uncommittedBatch.size(), uncommittedKeysToRemove.size(),  System.nanoTime() - updateBatchTime);

            long updateCommittedAndUncommittedTime = System.nanoTime();
            metrics.onCacheCommittedWritePutAll(uncommittedCache.size());
            committedCache.putAll(uncommittedCache);
            uncommittedCache.clear();
            metrics.onStoreFlushCommitedAndUncommittedUpdate(System.nanoTime()-updateCommittedAndUncommittedTime);

            long totalTime = System.nanoTime() - saveTime;

            if (logger.isTraceEnabled()) {
                logger.trace("datasource flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
            }
            base.flush();
        } finally {
            long nanos = System.nanoTime() - start;
           metrics.onUserWriteFlush(DataSourceWithCacheMetrics.FlushReason.MANUAL, nanos);
            this.lock.writeLock().unlock();
        }
    }

    public void flushNotManual() {
        Map<ByteArrayWrapper, byte[]> uncommittedBatch = new LinkedHashMap<>();

        this.lock.writeLock().lock();

        try {
            long saveTime = System.nanoTime();

            this.uncommittedCache.forEach((key, value) -> {
                if (value != null) {
                    uncommittedBatch.put(key, value);
                }
            });

            Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toSet());
            base.updateBatch(uncommittedBatch, uncommittedKeysToRemove);
            metrics.onCacheCommittedWritePutAll(uncommittedCache.size());
            committedCache.putAll(uncommittedCache);
            uncommittedCache.clear();

            long totalTime = System.nanoTime() - saveTime;

            if (logger.isTraceEnabled()) {
                logger.trace("datasource flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
            }
            base.flush();
        } finally {
            this.lock.writeLock().unlock();
        }
    }


    public String getName() {
        return base.getName() + "-with-uncommittedCache";
    }

    public void init() {
        base.init();
    }

    public boolean isAlive() {
        return base.isAlive();
    }

    public void close() {
        this.lock.writeLock().lock();

        try {
            long start = System.nanoTime();
            flushNotManual();
            long nanos = System.nanoTime() - start;
            metrics.onUserWriteFlush(DataSourceWithCacheMetrics.FlushReason.CLOSE, nanos);
            base.close();
            if (cacheSnapshotHandler != null) {
                cacheSnapshotHandler.save(committedCache);
            }
            uncommittedCache.clear();

            committedCache.clear();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void emitLogs() {
        if (!logger.isTraceEnabled()) {
            return;
        }

        this.lock.writeLock().lock();

        try {
            logger.trace("Activity: No. Gets: {}. No. Puts: {}. No. Gets from Store: {}",
                    numOfGets.getAndSet(0),
                    numOfPuts.getAndSet(0),
                    numOfGetsFromStore.getAndSet(0));
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Nonnull
    private static Map<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize,
                                                                    @Nullable CacheSnapshotHandler cacheSnapshotHandler) {
        Map<ByteArrayWrapper, byte[]> cache = new MaxSizeHashMap<>(cacheSize, true);

        if (cacheSnapshotHandler != null) {
            cacheSnapshotHandler.load(cache);
        }

        return cache;
    }
}
