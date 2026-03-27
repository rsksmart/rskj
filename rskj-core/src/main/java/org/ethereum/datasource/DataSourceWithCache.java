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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;


public class DataSourceWithCache implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("datasourcewithcache");
    private final DataSourceWithLockMetrics metrics;
    private final int cacheSize;
    //private final int uncommittedMaxSize;
    private final KeyValueDataSource base;
    private final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    private final Cache<ByteArrayWrapper, byte[]> cache;

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
        String instanceId = Integer.toHexString(System.identityHashCode(this));
        //this.uncommittedMaxSize= Math.max(1, cacheSize / 8);
        this.base = Objects.requireNonNull(base);
        this.uncommittedCache = new LinkedHashMap<>(cacheSize / 8, (float) 0.75, false);
        this.cache = makeCommittedCache(cacheSize, cacheSnapshotHandler);
        this.cacheSnapshotHandler = cacheSnapshotHandler;
        this.metrics = new DataSourceWithLockMetrics(logger, getName() + "#" + instanceId, owner, 10_000);
    }

    // Cache population is intentionally allowed under read lock because the cache
    // is independently thread-safe. The outer lock primarily protects uncommittedCache.
    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);
        long methodStart = System.nanoTime();
        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;
        long lockWaitStart = System.nanoTime();
        this.lock.readLock().lock();
        metrics.onReadLockWait(System.nanoTime() - lockWaitStart);
        long lockHeldStart = System.nanoTime();
        try {
            if (uncommittedCache.containsKey(wrappedKey)) {
                byte[] result = uncommittedCache.get(wrappedKey);
                return result;
            }
            byte[] cachedValue = cache.getIfPresent(wrappedKey);
            if (cachedValue != null) {
                return cachedValue;
            }
            value = base.get(key);

            if (traceEnabled) {
                numOfGetsFromStore.incrementAndGet();
            }

            if (value != null) {
                cache.put(wrappedKey, value);
            }
        } finally {
            metrics.onReadLockHeld( System.nanoTime() - lockHeldStart);
            if (traceEnabled) {
                numOfGets.incrementAndGet();
            }
            this.lock.readLock().unlock();
            metrics.onGetLatency(System.nanoTime() - methodStart);
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
        long methodStart = System.nanoTime();
        long lockWaitStart = System.nanoTime();
        this.lock.writeLock().lock();
        metrics.onWriteLockWait(System.nanoTime() - lockWaitStart);
        long lockHeldStart = System.nanoTime();
        try {

            byte[] pendingValue = uncommittedCache.get(wrappedKey);
            if (pendingValue != null && Arrays.equals(pendingValue, value)) {
                // Same value is already scheduled to be flushed.
                return value;
            }

            byte[] cachedValue = cache.getIfPresent(wrappedKey);
            if (cachedValue != null && Arrays.equals(cachedValue, value)) {
                // Requested value already matches committed state, so any pending write is redundant.
                uncommittedCache.remove(wrappedKey);
                return value;
            }

            uncommittedCache.put(wrappedKey, value);

            cache.invalidate(wrappedKey);
            if (uncommittedCache.size() > this.cacheSize) {
                flushNotManual(DataSourceWithCacheMetrics.FlushReason.SIZE);
            }
            return value;

        } finally {
            metrics.onWriteLockHeld(System.nanoTime() - lockHeldStart);
            if (logger.isTraceEnabled()) {
                numOfPuts.incrementAndGet();
            }
            this.lock.writeLock().unlock();
            metrics.onPutLatency(System.nanoTime() - methodStart);
        }
    }


    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        long methodStart = System.nanoTime();
        long lockWaitStart = System.nanoTime();
        this.lock.writeLock().lock();
        metrics.onWriteLockWait(System.nanoTime() - lockWaitStart);
        long lockHeldStart = System.nanoTime();
        try {
            uncommittedCache.remove(wrappedKey);
            cache.invalidate(wrappedKey);
            base.delete(wrappedKey.getData());

        } finally {
            metrics.onWriteLockHeld(System.nanoTime() - lockHeldStart);
            this.lock.writeLock().unlock();
            metrics.onDeleteLatency( System.nanoTime() - methodStart);
        }
    }

    @Override
    public Set<ByteArrayWrapper> keys() {
        this.lock.readLock().lock();
        try {
            return Stream.concat(
                    base.keys().stream(),
                    uncommittedCache.keySet().stream()
            ).collect(Collectors.toCollection(HashSet::new));
        } finally {
            this.lock.readLock().unlock();
        }
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
        if (keysToRemove.contains(null)) {
            throw new IllegalArgumentException("Cannot remove null keys");
        }
        long methodStart = System.nanoTime();
        rows.keySet().removeAll(keysToRemove);
        long start = System.nanoTime();
        long lockWaitStart = System.nanoTime();
        this.lock.writeLock().lock();
        metrics.onWriteLockWait( System.nanoTime() - lockWaitStart);
        long lockHeldStart = System.nanoTime();
        try {
            rows.forEach(this::put);
            keysToRemove.forEach(this::delete);
        } finally {
            metrics.onWriteLockHeld(System.nanoTime() - lockHeldStart);
            this.lock.writeLock().unlock();
            long nanos = System.nanoTime() - start;
            metrics.onBatchLatency(System.nanoTime() - methodStart);
        }
    }

    @Override
    public void flush() {
        long methodStart = System.nanoTime();
        long lockWaitStart = System.nanoTime();
        this.lock.writeLock().lock();
        metrics.onWriteLockWait(System.nanoTime() - lockWaitStart);
        long lockHeldStart = System.nanoTime();
        try {
            long saveTime = System.nanoTime();

            Map<ByteArrayWrapper, byte[]> uncommittedBatch = new LinkedHashMap<>(uncommittedCache);
            Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toSet());

            base.updateBatch(uncommittedBatch, uncommittedKeysToRemove);

            uncommittedBatch.forEach(cache::put);
            uncommittedCache.clear();
            long totalTime = System.nanoTime() - saveTime;

            if (logger.isTraceEnabled()) {
                logger.trace("datasource flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
            }
            base.flush();
        } finally {
            metrics.onWriteLockHeld(System.nanoTime() - lockHeldStart);
            this.lock.writeLock().unlock();
            long methodNanos = System.nanoTime() - methodStart;
            metrics.onFlushLatency(DataSourceWithCacheMetrics.FlushReason.MANUAL, methodNanos);
        }
    }

    public void flushNotManual(DataSourceWithCacheMetrics.FlushReason reason) {
        long methodStart = System.nanoTime();
        long lockWaitStart = System.nanoTime();
        this.lock.writeLock().lock();
        metrics.onWriteLockWait(System.nanoTime() - lockWaitStart);
        long lockHeldStart = System.nanoTime();
        try {
            long saveTime = System.nanoTime();

            Map<ByteArrayWrapper, byte[]> uncommittedBatch = new LinkedHashMap<>(uncommittedCache);

            base.updateBatch(uncommittedBatch, Collections.emptySet());
            uncommittedBatch.forEach(cache::put);
            uncommittedCache.clear();
            long totalTime = System.nanoTime() - saveTime;

            if (logger.isTraceEnabled()) {
                logger.trace("datasource flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
            }
            base.flush();
        } finally {
            metrics.onWriteLockHeld(System.nanoTime() - lockHeldStart);
            this.lock.writeLock().unlock();
            metrics.onFlushLatency(reason,  System.nanoTime() - methodStart);
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
            flushNotManual(DataSourceWithCacheMetrics.FlushReason.CLOSE);
            base.close();
            if (cacheSnapshotHandler != null) {
                cacheSnapshotHandler.save(new LinkedHashMap<>(cache.asMap()));
            }
            uncommittedCache.clear();
            cache.invalidateAll();
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
    private static Cache<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize, @Nullable CacheSnapshotHandler cacheSnapshotHandler) {
        Cache<ByteArrayWrapper, byte[]> cache = Caffeine.newBuilder().maximumSize(cacheSize).build();
        if (cacheSnapshotHandler != null) {
            Map<ByteArrayWrapper, byte[]> snapshot = new LinkedHashMap<>();
            cacheSnapshotHandler.load(snapshot);
            snapshot.forEach((key, value) -> {
                if (value != null) {
                    cache.put(key, value);
                }
            });
        }

        return cache;
    }
}
