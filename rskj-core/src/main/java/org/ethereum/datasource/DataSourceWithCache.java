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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataSourceWithCache implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("datasourcewithcache");

    // Caps how many flushed-but-not-yet-persisted batches may sit in the async flush
    // queue at once. Without a cap, a producer (block processing) that outruns the
    // consumer (the single background writer thread) grows the queue -- and the full
    // key/value maps each PendingBatch retains -- without bound, which is a direct path
    // to an OOM under sustained heavy load. Once the cap is hit, the enqueuing thread
    // blocks until the background writer frees a slot, which throttles block processing
    // to the DB's actual write throughput instead of letting heap absorb the backlog.
    private static final int DEFAULT_MAX_PENDING_FLUSH_BATCHES = 4;
    private static final int MAX_PENDING_FLUSH_BATCHES =
            Integer.getInteger("datasourcewithcache.asyncFlush.maxQueueDepth", DEFAULT_MAX_PENDING_FLUSH_BATCHES);

    private final int cacheSize;
    private final KeyValueDataSource base;
    private final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    private final Map<ByteArrayWrapper, byte[]> committedCache;
    private final boolean asyncFlushEnabled;
    private final ExecutorService asyncFlushExecutor;
    private final Deque<PendingBatch> pendingFlushBatches;
    private final Object pendingFlushMonitor;
    // Index of keys touched by any batch still sitting in pendingFlushBatches (enqueued
    // but not yet durably written to `base`), mapped to their most-recently-enqueued
    // value (null means "pending delete"). Without this, get() could see a key evicted
    // from the bounded committedCache while it is still only in the pending queue, fall
    // through to base.get(key), and return a stale/absent value. Guarded by
    // pendingFlushMonitor, same as pendingFlushBatches. pendingFlushKeyRefCounts tracks
    // how many still-pending batches reference each key, so a key is only removed from
    // the index once every batch that touched it -- including newer ones enqueued after
    // an older one already flushed -- has actually been persisted.
    private final Map<ByteArrayWrapper, byte[]> pendingFlushIndex;
    private final Map<ByteArrayWrapper, Integer> pendingFlushKeyRefCounts;
    private final AtomicReference<RuntimeException> asyncFlushFailure;
    private final AtomicLong asyncFlushBatchesEnqueued;
    private final AtomicLong asyncFlushBatchesFlushed;
    private final AtomicLong asyncFlushEntriesEnqueued;
    private final AtomicLong asyncFlushEntriesFlushed;
    private final AtomicInteger asyncFlushMaxQueueDepth;

    private final AtomicInteger numOfPuts = new AtomicInteger();
    private final AtomicInteger numOfGets = new AtomicInteger();
    private final AtomicInteger numOfGetsFromStore = new AtomicInteger();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Nullable
    private final CacheSnapshotHandler cacheSnapshotHandler;

    public DataSourceWithCache(@Nonnull KeyValueDataSource base, int cacheSize) {
        this(base, cacheSize, null, false);
    }

    public DataSourceWithCache(@Nonnull KeyValueDataSource base, int cacheSize, boolean asyncFlushEnabled) {
        this(base, cacheSize, null, asyncFlushEnabled);
    }

    public DataSourceWithCache(@Nonnull KeyValueDataSource base, int cacheSize,
                               @Nullable CacheSnapshotHandler cacheSnapshotHandler) {
        this(base, cacheSize, cacheSnapshotHandler, false);
    }

    public DataSourceWithCache(@Nonnull KeyValueDataSource base, int cacheSize,
                               @Nullable CacheSnapshotHandler cacheSnapshotHandler,
                               boolean asyncFlushEnabled) {
        this.cacheSize = cacheSize;
        this.base = Objects.requireNonNull(base);
        this.uncommittedCache = new LinkedHashMap<>(cacheSize / 8, (float) 0.75, false);
        this.committedCache = Collections.synchronizedMap(makeCommittedCache(cacheSize, cacheSnapshotHandler));
        this.cacheSnapshotHandler = cacheSnapshotHandler;
        this.asyncFlushEnabled = asyncFlushEnabled;
        this.pendingFlushBatches = asyncFlushEnabled ? new ArrayDeque<>() : null;
        this.pendingFlushMonitor = asyncFlushEnabled ? new Object() : null;
        this.pendingFlushIndex = asyncFlushEnabled ? new HashMap<>() : null;
        this.pendingFlushKeyRefCounts = asyncFlushEnabled ? new HashMap<>() : null;
        this.asyncFlushFailure = asyncFlushEnabled ? new AtomicReference<>() : null;
        this.asyncFlushBatchesEnqueued = asyncFlushEnabled ? new AtomicLong() : null;
        this.asyncFlushBatchesFlushed = asyncFlushEnabled ? new AtomicLong() : null;
        this.asyncFlushEntriesEnqueued = asyncFlushEnabled ? new AtomicLong() : null;
        this.asyncFlushEntriesFlushed = asyncFlushEnabled ? new AtomicLong() : null;
        this.asyncFlushMaxQueueDepth = asyncFlushEnabled ? new AtomicInteger() : null;
        this.asyncFlushExecutor = asyncFlushEnabled
                ? Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, getName() + "-async-flush");
                    thread.setDaemon(true);
                    return thread;
                })
                : null;
    }

    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);
        ensureNoAsyncFlushFailure();

        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;

        this.lock.readLock().lock();

        try {
            if (committedCache.containsKey(wrappedKey)) {
                return committedCache.get(wrappedKey);
            }

            if (uncommittedCache.containsKey(wrappedKey)) {
                return uncommittedCache.get(wrappedKey);
            }

            if (asyncFlushEnabled) {
                synchronized (pendingFlushMonitor) {
                    if (pendingFlushIndex.containsKey(wrappedKey)) {
                        value = pendingFlushIndex.get(wrappedKey);
                        committedCache.put(wrappedKey, value);
                        return value;
                    }
                }
            }

            value = base.get(key);

            if (traceEnabled) {
                numOfGetsFromStore.incrementAndGet();
            }

            //null value, as expected, is allowed here to be stored in committedCache
            committedCache.put(wrappedKey, value);
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
        ensureNoAsyncFlushFailure();

        this.lock.writeLock().lock();

        try {
            // here I could check for equal data or just move to the uncommittedCache.
            byte[] priorValue = committedCache.get(wrappedKey);

            if (priorValue != null && Arrays.equals(priorValue, value)) {
                return value;
            }

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

        if (uncommittedCache.size() > cacheSize) {
            this.flush();
        }
    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        ensureNoAsyncFlushFailure();
        this.lock.writeLock().lock();

        try {
            // always mark for deletion if we don't know the state in the underlying store
            if (!committedCache.containsKey(wrappedKey)) {
                this.putKeyValue(wrappedKey, null);
                return;
            }

            byte[] valueToRemove = committedCache.get(wrappedKey);

            // a null value means we know for a fact that the key doesn't exist in the underlying store, so this is a noop
            if (valueToRemove != null) {
                this.putKeyValue(wrappedKey, null);
                committedCache.remove(wrappedKey);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public Set<ByteArrayWrapper> keys() {
        ensureNoAsyncFlushFailure();
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
        ensureNoAsyncFlushFailure();
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
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
        ensureNoAsyncFlushFailure();

        if (asyncFlushEnabled) {
            flushAsync();
            return;
        }

        flushSync();
    }

    private void flushSync() {
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

    private void flushAsync() {
        this.lock.writeLock().lock();

        try {
            enqueuePendingBatchLocked();
        } finally {
            this.lock.writeLock().unlock();
        }

        submitAsyncFlushWorker();
    }

    private void enqueuePendingBatchLocked() {
        if (uncommittedCache.isEmpty()) {
            return;
        }

        Map<ByteArrayWrapper, byte[]> uncommittedBatch = new LinkedHashMap<>();
        Set<ByteArrayWrapper> uncommittedKeysToRemove = new HashSet<>();

        for (Map.Entry<ByteArrayWrapper, byte[]> entry : uncommittedCache.entrySet()) {
            if (entry.getValue() != null) {
                uncommittedBatch.put(entry.getKey(), entry.getValue());
            } else {
                uncommittedKeysToRemove.add(entry.getKey());
            }
        }

        committedCache.putAll(uncommittedCache);
        uncommittedCache.clear();

        if (uncommittedBatch.isEmpty() && uncommittedKeysToRemove.isEmpty()) {
            return;
        }

        synchronized (pendingFlushMonitor) {
            while (pendingFlushBatches.size() >= MAX_PENDING_FLUSH_BATCHES && asyncFlushFailure.get() == null) {
                try {
                    pendingFlushMonitor.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while applying async flush backpressure", e);
                }
            }
            ensureNoAsyncFlushFailure();

            for (ByteArrayWrapper key : uncommittedBatch.keySet()) {
                pendingFlushKeyRefCounts.merge(key, 1, Integer::sum);
                pendingFlushIndex.put(key, uncommittedBatch.get(key));
            }
            for (ByteArrayWrapper key : uncommittedKeysToRemove) {
                pendingFlushKeyRefCounts.merge(key, 1, Integer::sum);
                pendingFlushIndex.put(key, null);
            }

            pendingFlushBatches.addLast(new PendingBatch(uncommittedBatch, uncommittedKeysToRemove));
            asyncFlushBatchesEnqueued.incrementAndGet();
            asyncFlushEntriesEnqueued.addAndGet((long) uncommittedBatch.size() + uncommittedKeysToRemove.size());
            int queueDepth = pendingFlushBatches.size();
            while (true) {
                int currentMax = asyncFlushMaxQueueDepth.get();
                if (queueDepth <= currentMax || asyncFlushMaxQueueDepth.compareAndSet(currentMax, queueDepth)) {
                    break;
                }
            }

            if (logger.isTraceEnabled()) {
                logger.trace(
                        "Async flush enqueue: dataSource={} queueDepth={} maxQueueDepth={} enqueuedBatches={} flushedBatches={} enqueuedEntries={} flushedEntries={} batchUpdates={} batchDeletes={}",
                        getName(),
                        queueDepth,
                        asyncFlushMaxQueueDepth.get(),
                        asyncFlushBatchesEnqueued.get(),
                        asyncFlushBatchesFlushed.get(),
                        asyncFlushEntriesEnqueued.get(),
                        asyncFlushEntriesFlushed.get(),
                        uncommittedBatch.size(),
                        uncommittedKeysToRemove.size()
                );
            }

            pendingFlushMonitor.notifyAll();
        }
    }

    // Must be called while holding pendingFlushMonitor. Decrements each flushed key's
    // pending-batch reference count and removes it from pendingFlushIndex once no
    // still-pending batch (including one enqueued after this one, with a newer value)
    // references it anymore. Batches are always flushed in enqueue (FIFO) order by the
    // single worker thread, so once a key's ref count reaches zero, `base` is guaranteed
    // to hold at least as fresh a value for that key as pendingFlushIndex's last entry.
    private void releasePendingFlushIndexEntriesLocked(PendingBatch pendingBatch) {
        for (ByteArrayWrapper key : pendingBatch.entriesToUpdate.keySet()) {
            decrementPendingFlushRefCountLocked(key);
        }
        for (ByteArrayWrapper key : pendingBatch.keysToRemove) {
            decrementPendingFlushRefCountLocked(key);
        }
    }

    private void decrementPendingFlushRefCountLocked(ByteArrayWrapper key) {
        Integer remaining = pendingFlushKeyRefCounts.merge(key, -1, Integer::sum);
        if (remaining <= 0) {
            pendingFlushKeyRefCounts.remove(key);
            pendingFlushIndex.remove(key);
        }
    }

    private void submitAsyncFlushWorker() {
        asyncFlushExecutor.submit(() -> {
            while (true) {
                PendingBatch pendingBatch;
                synchronized (pendingFlushMonitor) {
                    pendingBatch = pendingFlushBatches.pollFirst();
                    if (pendingBatch == null) {
                        pendingFlushMonitor.notifyAll();
                        return;
                    }
                }

                try {
                    base.updateBatch(pendingBatch.entriesToUpdate, pendingBatch.keysToRemove);
                    base.flush();
                    asyncFlushBatchesFlushed.incrementAndGet();
                    asyncFlushEntriesFlushed.addAndGet((long) pendingBatch.entriesToUpdate.size() + pendingBatch.keysToRemove.size());

                    boolean traceEnabled = logger.isTraceEnabled();
                    int queueDepthAfterFlush;
                    synchronized (pendingFlushMonitor) {
                        releasePendingFlushIndexEntriesLocked(pendingBatch);
                        queueDepthAfterFlush = pendingFlushBatches.size();
                        // Wake both a backpressure-blocked producer (a freed slot below
                        // MAX_PENDING_FLUSH_BATCHES) and close()'s awaitPendingAsyncFlush.
                        pendingFlushMonitor.notifyAll();
                    }

                    if (traceEnabled) {
                        logger.trace(
                                "Async flush drain: dataSource={} queueDepth={} maxQueueDepth={} enqueuedBatches={} flushedBatches={} enqueuedEntries={} flushedEntries={} batchUpdates={} batchDeletes={}",
                                getName(),
                                queueDepthAfterFlush,
                                asyncFlushMaxQueueDepth.get(),
                                asyncFlushBatchesEnqueued.get(),
                                asyncFlushBatchesFlushed.get(),
                                asyncFlushEntriesEnqueued.get(),
                                asyncFlushEntriesFlushed.get(),
                                pendingBatch.entriesToUpdate.size(),
                                pendingBatch.keysToRemove.size()
                        );
                    }
                } catch (RuntimeException e) {
                    asyncFlushFailure.compareAndSet(null, e);
                    synchronized (pendingFlushMonitor) {
                        pendingFlushMonitor.notifyAll();
                    }
                    throw e;
                }
            }
        });
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
            flush();
            if (asyncFlushEnabled) {
                awaitPendingAsyncFlush();
                asyncFlushExecutor.shutdown();
            }
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

    private void awaitPendingAsyncFlush() {
        long timeoutAt = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        synchronized (pendingFlushMonitor) {
            while (!pendingFlushBatches.isEmpty() && asyncFlushFailure.get() == null) {
                long remainingNanos = timeoutAt - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }

                try {
                    pendingFlushMonitor.wait(Math.max(1L, remainingNanos / 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for asynchronous flush completion", e);
                }
            }
        }

        ensureNoAsyncFlushFailure();
    }

    private void ensureNoAsyncFlushFailure() {
        if (!asyncFlushEnabled) {
            return;
        }

        RuntimeException flushException = asyncFlushFailure.get();
        if (flushException != null) {
            throw new IllegalStateException("Asynchronous flush failed", flushException);
        }
    }

    public void emitLogs() {
        if (!logger.isTraceEnabled()) {
            return;
        }

        this.lock.writeLock().lock();

        try {
            ensureNoAsyncFlushFailure();

            int pendingQueueSize = asyncFlushEnabled ? pendingFlushBatches.size() : 0;
            logger.trace("Activity: No. Gets: {}. No. Puts: {}. No. Gets from Store: {}",
                    numOfGets.getAndSet(0),
                    numOfPuts.getAndSet(0),
                    numOfGetsFromStore.getAndSet(0));

            if (asyncFlushEnabled) {
                logger.trace(
                        "Async flush activity: queueDepth={} maxQueueDepth={} enqueuedBatches={} flushedBatches={} enqueuedEntries={} flushedEntries={}",
                        pendingQueueSize,
                        asyncFlushMaxQueueDepth.getAndSet(pendingQueueSize),
                        asyncFlushBatchesEnqueued.getAndSet(0),
                        asyncFlushBatchesFlushed.getAndSet(0),
                        asyncFlushEntriesEnqueued.getAndSet(0),
                        asyncFlushEntriesFlushed.getAndSet(0)
                );
            }
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

    private static final class PendingBatch {
        private final Map<ByteArrayWrapper, byte[]> entriesToUpdate;
        private final Set<ByteArrayWrapper> keysToRemove;

        private PendingBatch(Map<ByteArrayWrapper, byte[]> entriesToUpdate, Set<ByteArrayWrapper> keysToRemove) {
            this.entriesToUpdate = entriesToUpdate;
            this.keysToRemove = keysToRemove;
        }
    }
}
