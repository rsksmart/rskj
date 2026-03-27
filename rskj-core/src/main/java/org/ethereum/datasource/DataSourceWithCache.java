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

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;


public class DataSourceWithCache implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("datasourcewithcache");
    private final int cacheSize;
    //private final int uncommittedMaxSize;
    private final KeyValueDataSource base;
    private final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    private final Cache<ByteArrayWrapper, byte[]> cache;

    private final AtomicInteger numOfPuts = new AtomicInteger();
    private final AtomicInteger numOfGets = new AtomicInteger();
    private final AtomicInteger numOfGetsFromStore = new AtomicInteger();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
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
        this.uncommittedCache = new LinkedHashMap<>(cacheSize / 8, (float) 0.75, false);
        this.cache = makeCommittedCache(cacheSize, cacheSnapshotHandler);
        this.cacheSnapshotHandler = cacheSnapshotHandler;
        String instanceId = Integer.toHexString(System.identityHashCode(this));
        startCacheStatsLogging( getName() + "#" + instanceId, owner);
    }

    // Cache population is intentionally allowed under read lock because the cache
    // is independently thread-safe. The outer lock primarily protects uncommittedCache.
    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);
        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;
        this.lock.readLock().lock();
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
        this.lock.writeLock().lock();
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
            if (logger.isTraceEnabled()) {
                numOfPuts.incrementAndGet();
            }
            this.lock.writeLock().unlock();
        }
    }


    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        this.lock.writeLock().lock();
        try {
            uncommittedCache.remove(wrappedKey);
            cache.invalidate(wrappedKey);
            base.delete(wrappedKey.getData());

        } finally {
            this.lock.writeLock().unlock();
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
        rows.keySet().removeAll(keysToRemove);
        this.lock.writeLock().lock();
        try {
            rows.forEach(this::put);
            keysToRemove.forEach(this::delete);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void flush() {
        this.lock.writeLock().lock();
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
            this.lock.writeLock().unlock();
        }
    }

    public void flushNotManual(DataSourceWithCacheMetrics.FlushReason reason) {
        this.lock.writeLock().lock();
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
        Cache<ByteArrayWrapper, byte[]> cache = Caffeine.newBuilder().maximumSize(cacheSize).recordStats().build();
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

    private void startCacheStatsLogging(String name, String owner ) {
        scheduler.scheduleAtFixedRate(() -> {
            CacheStats stats = cache.stats();

            logger.info(
                    "ds_caffeine_metrics owner={} name={} requests={} hits={} misses={} hitRate={} evictions={} size={} loadSuccess={} loadFailure={} avgLoadPenaltyNs={}",
                    owner,
                    name,
                    stats.requestCount(),
                    stats.hitCount(),
                    stats.missCount(),
                    stats.hitRate(),
                    stats.evictionCount(),
                    cache.estimatedSize(),
                    stats.loadSuccessCount(),
                    stats.loadFailureCount(),
                    stats.averageLoadPenalty()
            );

        }, 1, 1, TimeUnit.MINUTES); // initial delay + period
    }
}
