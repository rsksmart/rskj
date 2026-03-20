package org.ethereum.datasource;

import org.slf4j.Logger;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntSupplier;

/**
 * Lightweight in-process metrics + periodic structured logging for DataSourceWithCache.
 *
 * Goal:
 * 1) Track user-visible operations: get/put/delete/updateBatch/flush/close (read/write from "user perspective")
 * 2) Independently track internal cache activity on committedCache and uncommittedCache
 *
 * Notes:
 * - "user_*" counters should be incremented exactly once per public API call (or per batch/flush call).
 * - "cache_*" counters are internal, and can be incremented multiple times per user call as needed.
 * - "known_absent" means cached mapping to null (negative cache entry).
 */
public final class DataSourceWithCacheMetrics {

    public enum FlushReason { SIZE, MANUAL, CLOSE }

    private final Logger logger;
    private final String name;
    private final String owner;
    private final IntSupplier committedSize;
    private final IntSupplier uncommittedSize;

    // emit every N operations (0 disables periodic logs)
    private final long emitEveryOps;

    // USER-PERSPECTIVE counters (increment once per API call)
    private final LongAdder user_read_get_total = new LongAdder();
    private final LongAdder user_write_put_total = new LongAdder();
    private final LongAdder user_write_delete_total = new LongAdder();
    private final LongAdder user_write_batch_total = new LongAdder();
    private final LongAdder user_write_flush_total = new LongAdder();

    // READ source breakdown (user get() outcome)
    private final LongAdder user_read_from_committed_cache_value = new LongAdder();
    private final LongAdder user_read_from_committed_cache_known_absent = new LongAdder();
    private final LongAdder user_read_from_uncommitted_cache_value = new LongAdder();
    private final LongAdder user_read_from_uncommitted_cache_known_absent = new LongAdder();
    private final LongAdder user_read_from_store_value = new LongAdder();
    private final LongAdder user_read_from_store_known_absent = new LongAdder();

    // INTERNAL cache operations (independent of user perspective)
    // committedCache internal ops
    private final LongAdder cache_committed_contains = new LongAdder(); //as “contains attempts”.
    private final LongAdder cache_committed_get = new LongAdder();
    private final LongAdder cache_committed_put = new LongAdder();
    private final LongAdder cache_committed_remove = new LongAdder();
    private final LongAdder cache_committed_remove_known_absent = new LongAdder();
    private final LongAdder cache_committed_putAll = new LongAdder(); // counts calls, not entries
    // uncommittedCache internal ops
    private final LongAdder cache_uncommitted_contains = new LongAdder();
    private final LongAdder cache_uncommitted_get = new LongAdder();
    private final LongAdder cache_uncommitted_put = new LongAdder();
    private final LongAdder cache_uncommitted_remove = new LongAdder();

    // A read miss hit store and then populated committedCache (read-through fill).
    private final LongAdder read_through_fill_committed_from_store_value = new LongAdder();
    private final LongAdder read_through_fill_committed_from_store_known_absent = new LongAdder();

    // A write invalidated committed entry (e.g., put() removes committed before staging into uncommitted).
    private final LongAdder write_invalidate_committed_on_put = new LongAdder();

    // Flush reason breakdown (user flushes)
    private final LongAdder write_flush_manual = new LongAdder();
    private final LongAdder write_flush_size = new LongAdder();
    private final LongAdder write_flush_close = new LongAdder();


    // Timings (nanos)
    private final LongAdder read_store_nanos = new LongAdder();   // only when base.get() happens
    private final LongAdder write_flush_nanos = new LongAdder();  // flush duration (manual/size/close)
    private final LongAdder write_batch_nanos = new LongAdder();  // updateBatch duration

    //flush operation
    private final LongAdder store_flush_to_update = new LongAdder();
    private final LongAdder store_flush_to_remove = new LongAdder();
    private final LongAdder store_flush_nanos = new LongAdder();
    private final LongAdder store_flush_counts = new LongAdder();

    private final LongAdder store_flush_committed_and_uncommitted_nanos = new LongAdder();
    private final LongAdder store_flush_committed_and_uncommitted_count = new LongAdder();


    // Lock metrics
    private final LongAdder readLockAcquireTotal = new LongAdder();
    private final LongAdder writeLockAcquireTotal = new LongAdder();

    private final LongAdder readLockWaitNanos = new LongAdder();
    private final LongAdder writeLockWaitNanos = new LongAdder();

    private final LongAdder readLockHeldNanos = new LongAdder();
    private final LongAdder writeLockHeldNanos = new LongAdder();

    // Method latency metrics
    private final LongAdder getLatencyTotal = new LongAdder();
    private final LongAdder putLatencyTotal = new LongAdder();
    private final LongAdder deleteLatencyTotal = new LongAdder();
    private final LongAdder flushLatencyTotal = new LongAdder();
    private final LongAdder batchLatencyTotal = new LongAdder();

    private final LongAdder getLatencyNanos = new LongAdder();
    private final LongAdder putLatencyNanos = new LongAdder();
    private final LongAdder deleteLatencyNanos = new LongAdder();
    private final LongAdder flushLatencyNanos = new LongAdder();
    private final LongAdder batchLatencyNanos = new LongAdder();

    public DataSourceWithCacheMetrics(Logger logger,
                                      String name,
                                      String owner,
                                      IntSupplier committedSize,
                                      IntSupplier uncommittedSize,
                                      long emitEveryOps) {
        this.logger = logger;
        this.owner = owner;
        this.name = name;
        this.committedSize = committedSize;
        this.uncommittedSize = uncommittedSize;
        this.emitEveryOps = emitEveryOps;
    }

    public void onStoreFlushBatchUpdate(int  entriesToUpdate, int keysToRemove, long nanos) {
        store_flush_to_update.add(entriesToUpdate);
        store_flush_to_remove.add(keysToRemove);
        store_flush_counts.increment();
        store_flush_nanos.add(nanos);
        maybeEmit();
    }

    public void onStoreFlushCommitedAndUncommittedUpdate( long nanos) {
        store_flush_committed_and_uncommitted_nanos.add(nanos);
        store_flush_committed_and_uncommitted_count.increment();
        maybeEmit();
    }

    // USER-PERSPECTIVE: one increment per API call
    public void onUserReadGet() {
        user_read_get_total.increment();
        maybeEmit();
    }

    public void onUserWritePut() {
        user_write_put_total.increment();
        maybeEmit();
    }

    public void onUserWriteDelete() {
        user_write_delete_total.increment();
        maybeEmit();
    }

    public void onUserWriteBatch(long nanos) {
        user_write_batch_total.increment();
        write_batch_nanos.add(nanos);
        maybeEmit();
    }

    public void onUserWriteFlush(FlushReason reason, long nanos) {
        user_write_flush_total.increment();
        write_flush_nanos.add(nanos);
        switch (reason) {
            case MANUAL: write_flush_manual.increment(); break;
            case SIZE:   write_flush_size.increment(); break;
            case CLOSE:  write_flush_close.increment(); break;
        }
        maybeEmit();
    }


    // USER READ OUTCOME: exactly one of these per get() call (after caches/store)
    public void onUserReadGetFromCommittedCache(boolean knownAbsent) {
        if (knownAbsent) user_read_from_committed_cache_known_absent.increment();
        else user_read_from_committed_cache_value.increment();
        maybeEmit();
    }

    public void onUserReadGetFromUncommittedCache(boolean knownAbsent) {
        if (knownAbsent) user_read_from_uncommitted_cache_known_absent.increment();
        else user_read_from_uncommitted_cache_value.increment();
        maybeEmit();
    }

    public void onUserReadGetFromStore(long nanos, boolean knownAbsent) {
        read_store_nanos.add(nanos);
        if (knownAbsent) user_read_from_store_known_absent.increment();
        else user_read_from_store_value.increment();
        maybeEmit();
    }

    // INTERNAL CACHE OPS: call whenever you actually call the map methods
    // committedCache READ
    public void onCacheCommittedReadContains() { cache_committed_contains.increment(); }
    public void onCacheCommittedReadGet() { cache_committed_get.increment(); }
    // committedCache WRITE
    public void onCacheCommittedWritePut() { cache_committed_put.increment();}
    public void onCacheCommittedWriteRemove(boolean knownAbsent) {
        if (knownAbsent) cache_committed_remove_known_absent.increment();
        else cache_committed_remove.increment();
    }
    public void onCacheCommittedWritePutAll(int entries) {
        cache_committed_putAll.increment();
        cache_committed_put.add(entries);
    }
    // committedCache READ
    public void onCacheUncommittedReadContains() { cache_uncommitted_contains.increment(); }
    public void onCacheUncommittedReadGet() { cache_uncommitted_get.increment(); }
    // committedCache WRITE
    public void onCacheUncommittedWritePut() { cache_uncommitted_put.increment(); }
    public void onCacheUncommittedWriteRemove() { cache_uncommitted_remove.increment(); }


    /** READ-through: a user READ miss hit store and filled committed cache. */
    public void onReadThroughFillCommittedFromStore(boolean knownAbsent) {
        if (knownAbsent) read_through_fill_committed_from_store_known_absent.increment();
        else read_through_fill_committed_from_store_value.increment();
    }

    /** WRITE effect: a user WRITE invalidated committed entry (e.g., put removes committed before staging). */
    public void onWriteInvalidateCommittedOnPut() {
        write_invalidate_committed_on_put.increment();
    }
    public void onReadLockWait(long nanos) {
        readLockAcquireTotal.increment();
        readLockWaitNanos.add(nanos);
    }

    public void onWriteLockWait(long nanos) {
        writeLockAcquireTotal.increment();
        writeLockWaitNanos.add(nanos);
    }

    public void onReadLockHeld(long nanos) {
        readLockHeldNanos.add(nanos);
    }

    public void onWriteLockHeld(long nanos) {
        writeLockHeldNanos.add(nanos);
    }

    public void onGetLatency(long nanos) {
        getLatencyTotal.increment();
        getLatencyNanos.add(nanos);
    }

    public void onPutLatency(long nanos) {
        putLatencyTotal.increment();
        putLatencyNanos.add(nanos);
    }

    public void onDeleteLatency(long nanos) {
        deleteLatencyTotal.increment();
        deleteLatencyNanos.add(nanos);
    }

    public void onFlushLatency(long nanos) {
        flushLatencyTotal.increment();
        flushLatencyNanos.add(nanos);
    }

    public void onBatchLatency(long nanos) {
        batchLatencyTotal.increment();
        batchLatencyNanos.add(nanos);
    }

    private void maybeEmit() {
        if (!logger.isInfoEnabled()) return;
        if (emitEveryOps <= 0) return;

        long ops = user_read_get_total.sum()
                + user_write_put_total.sum()
                + user_write_delete_total.sum()
                + user_write_batch_total.sum()
                + user_write_flush_total.sum();

        if (ops % emitEveryOps == 0) {
            logger.info(buildLine());
        }
    }

    private String buildLine() {
        // USER totals
        long userGets = user_read_get_total.sum();
        long userPuts = user_write_put_total.sum();
        long userDels = user_write_delete_total.sum();
        long userBatches = user_write_batch_total.sum();
        long userFlushes = user_write_flush_total.sum();

        // USER read outcomes
        long rcV = user_read_from_committed_cache_value.sum();
        long rcA = user_read_from_committed_cache_known_absent.sum();
        long ruV = user_read_from_uncommitted_cache_value.sum();
        long ruA = user_read_from_uncommitted_cache_known_absent.sum();
        long rsV = user_read_from_store_value.sum();
        long rsA = user_read_from_store_known_absent.sum();

        long userReadFromCache = rcV + rcA + ruV + ruA;
        double userReadCacheRate = userGets == 0 ? 0.0 : (double) userReadFromCache / (double) userGets;

        // INTERNAL cache ops
        long cContains = cache_committed_contains.sum();
        long cGet = cache_committed_get.sum();
        long cPut = cache_committed_put.sum();
        long cRem = cache_committed_remove.sum();
        long cRemAbsent = cache_committed_remove_known_absent.sum();
        long cPutAll = cache_committed_putAll.sum();

        long uContains = cache_uncommitted_contains.sum();
        long uGet = cache_uncommitted_get.sum();
        long uPut = cache_uncommitted_put.sum();
        long uRem = cache_uncommitted_remove.sum();


        // Semantics
        long rtV = read_through_fill_committed_from_store_value.sum();
        long rtA = read_through_fill_committed_from_store_known_absent.sum();
        long invPut = write_invalidate_committed_on_put.sum();

        // Flush reasons
        long fMan = write_flush_manual.sum();
        long fSize = write_flush_size.sum();
        long fClose = write_flush_close.sum();

        // Timings
        long avgReadStoreNs = avg(read_store_nanos.sum(), (rsV + rsA));
        long avgWriteFlushNs = avg(write_flush_nanos.sum(), userFlushes);
        long avgWriteBatchNs = avg(write_batch_nanos.sum(), userBatches);

        //flush operation
        long storeFlushToUpdateCount = store_flush_to_update.sum();
        long storeFlushToRemoveCount = store_flush_to_remove.sum();
        long storeFlushToUpdateNs = avg(store_flush_nanos.sum(), store_flush_counts.sum());
        long storeFlushCommittedAndUncommittedNs = avg(store_flush_committed_and_uncommitted_nanos.sum(), store_flush_committed_and_uncommitted_count.sum());

        // Lock metrics
        long avgReadLockWaitNs = avg(readLockWaitNanos.sum(), readLockAcquireTotal.sum());
        long avgWriteLockWaitNs = avg(writeLockWaitNanos.sum(), writeLockAcquireTotal.sum());

        long avgReadLockHeldNs = avg(readLockHeldNanos.sum(), readLockAcquireTotal.sum());
        long avgWriteLockHeldNs = avg(writeLockHeldNanos.sum(), writeLockAcquireTotal.sum());

        long avgGetLatencyNs = avg(getLatencyNanos.sum(), getLatencyTotal.sum());
        long avgPutLatencyNs = avg(putLatencyNanos.sum(), putLatencyTotal.sum());
        long avgDeleteLatencyNs = avg(deleteLatencyNanos.sum(), deleteLatencyTotal.sum());
        long avgFlushLatencyNs = avg(flushLatencyNanos.sum(), flushLatencyTotal.sum());
        long avgBatchLatencyNs = avg(batchLatencyNanos.sum(), batchLatencyTotal.sum());

        return String.format(Locale.ROOT,
                "owner=%s " +
                        "ds_cache_metrics name=%s " +
                        // USER totals
                        "user_get_total=%d user_put_total=%d user_delete_total=%d user_batch_total=%d user_flush_total=%d " +
                        // USER read outcomes
                        "user_read_from_committed_cache_value=%d user_read_from_committed_cache_known_absent=%d " +
                        "user_read_from_uncommitted_cache_value=%d user_read_from_uncommitted_cache_known_absent=%d " +
                        "user_read_from_store_value=%d user_read_from_store_known_absent=%d " +
                        "user_read_cache_rate=%.4f " +
                        // INTERNAL committed ops
                        "cache_committed_contains=%d cache_committed_get=%d cache_committed_put=%d cache_committed_remove=%d cache_committed_remove_absent=%d cache_committed_putAll=%d " +
                        // INTERNAL uncommitted ops
                        "cache_uncommitted_contains=%d cache_uncommitted_get=%d cache_uncommitted_put=%d cache_uncommitted_remove=%d " +
                        // SEMANTICS
                        "read_through_fill_committed_from_store_value=%d read_through_fill_committed_from_store_known_absent=%d " +
                        "write_invalidate_committed_on_put=%d " +
                        // FLUSH reasons
                        "write_flush_manual=%d write_flush_size=%d write_flush_close=%d " +
                        // SIZES
                        "committed_size=%d uncommitted_size=%d " +
                        // TIMINGS
                        "avg_read_store_ns=%d avg_write_flush_ns=%d avg_write_batch_ns=%d " +
                        //Flush
                        "store_flush_to_update=%d store_flush_to_remove=%d store_flush_to_update_ns=%d store_flush_committed_and_uncommitted_ns=%d "+
                        //Lock metrics
                        "read_lock_acquire_total=%d write_lock_acquire_total=%d avg_read_lock_wait_ns=%d avg_write_lock_wait_ns=%d avg_read_lock_held_ns=%d avg_write_lock_held_ns=%d avg_get_latency_ns=%d avg_put_latency_ns=%d avg_delete_latency_ns=%d avg_flush_latency_ns=%d avg_batch_latency_ns=%d",
                owner,
                name,
                userGets, userPuts, userDels, userBatches, userFlushes,
                rcV, rcA,
                ruV, ruA,
                rsV, rsA,
                userReadCacheRate,
                cContains, cGet, cPut, cRem, cRemAbsent, cPutAll,
                uContains, uGet, uPut, uRem,
                rtV, rtA,
                invPut, //write_invalidate_committed_on_put
                fMan, fSize, fClose,
                committedSize.getAsInt(), uncommittedSize.getAsInt(),
                avgReadStoreNs, avgWriteFlushNs, avgWriteBatchNs,
                //flush
                storeFlushToUpdateCount, storeFlushToRemoveCount, storeFlushToUpdateNs, storeFlushCommittedAndUncommittedNs,
                //lock metrics
                readLockAcquireTotal.sum(), writeLockAcquireTotal.sum(),
                avgReadLockWaitNs, avgWriteLockWaitNs,
                avgReadLockHeldNs, avgWriteLockHeldNs,
                avgGetLatencyNs, avgPutLatencyNs, avgDeleteLatencyNs, avgFlushLatencyNs, avgBatchLatencyNs
        );
    }

    private static long avg(long total, long count) {
        return count == 0 ? 0 : (total / count);
    }
}
