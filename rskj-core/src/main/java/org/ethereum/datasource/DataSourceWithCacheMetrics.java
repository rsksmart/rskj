package org.ethereum.datasource;

import org.ethereum.util.ByteUtil;
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
    private final LongAdder user_write_put_total = new LongAdder();
    private final LongAdder user_write_delete_total = new LongAdder();
    private final LongAdder user_write_batch_total = new LongAdder();
    private final LongAdder user_write_flush_total = new LongAdder();
    private final LongAdder user_read_get_total = new LongAdder();


    // READ source breakdown (user get() outcome)
    private final LongAdder user_read_from_committed_cache_value = new LongAdder();
    private final LongAdder user_read_from_committed_cache_known_absent = new LongAdder();
    private final LongAdder user_read_from_uncommitted_cache_value = new LongAdder();
    private final LongAdder user_read_from_uncommitted_cache_known_absent = new LongAdder();
    private final LongAdder user_read_from_store_value = new LongAdder();
    private final LongAdder user_read_from_store_known_absent = new LongAdder();
    private final LongAdder user_read_write_to_committed_cache = new LongAdder();



    // WRITE
    private final LongAdder user_write_uncommitted_cache_get_total = new LongAdder();
    private final LongAdder user_write_uncommitted_cache_new_value_added_total = new LongAdder();
    private final LongAdder user_write_committed_cache_get_total = new LongAdder();
    private final LongAdder user_write_committed_cache_value_invalidated_total = new LongAdder();


    // INTERNAL cache operations (independent of user perspective)
    // committedCache internal ops
    private final LongAdder cache_committed_contains = new LongAdder(); //as “contains attempts”.
    private final LongAdder cache_committed_get = new LongAdder();
    private final LongAdder cache_committed_put_with_value = new LongAdder();
    private final LongAdder cache_committed_put_absent = new LongAdder();
    private final LongAdder cache_committed_remove = new LongAdder();
    private final LongAdder cache_committed_remove_known_absent = new LongAdder();

    // uncommittedCache internal ops
    private final LongAdder cache_uncommitted_contains = new LongAdder();
    private final LongAdder cache_uncommitted_get = new LongAdder();
    private final LongAdder cache_uncommitted_put = new LongAdder();
    private final LongAdder cache_uncommitted_remove = new LongAdder();

    // A read miss hit store and then populated committedCache (read-through fill).
    private final LongAdder read_through_fill_committed_from_store_value = new LongAdder();
    private final LongAdder read_through_fill_committed_from_store_known_absent = new LongAdder();

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
    private final LongAdder store_delete = new LongAdder();
    private final LongAdder store_flush_nanos = new LongAdder();
    private final LongAdder store_flush_counts = new LongAdder();


    private final LongAdder store_flush_committed_and_uncommitted_nanos = new LongAdder();
    private final LongAdder store_flush_committed_and_uncommitted_count = new LongAdder();


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

    public void onUserReadWriteToCommittedCache() {
        user_read_write_to_committed_cache.increment();
        maybeEmit();
    }

    public void onUserWriteUncommittedCacheGet() {
        user_write_uncommitted_cache_get_total.increment();
        maybeEmit();
    }

    public void onUserWriteCommittedCacheGet() {
        user_write_committed_cache_get_total.increment();
        maybeEmit();
    }


    public void onUserWriteUncommittedCacheNewValueAdded() {
        user_write_uncommitted_cache_new_value_added_total.increment();
        maybeEmit();
    }

    public void onUserWriteCommitedCacheValueInvalidated() {
        user_write_committed_cache_value_invalidated_total.increment();
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

    public void onStoreDelete() {
        store_delete.increment();
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


    public void onCacheCommittedContains() { cache_committed_contains.increment(); }
    public void onCacheCommittedGet() { cache_committed_get.increment(); }

    public void onCacheCommittedRemove(boolean knownAbsent) {
        if (knownAbsent) cache_committed_remove_known_absent.increment();
        else cache_committed_remove.increment();
    }
    public void onCacheCommittedPutWithValue(int entries) {
        cache_committed_put_with_value.add(entries);
    }
    public void onCacheCommittedPutAbsent(int entries) {
        cache_committed_put_absent.add(entries);
    }
    public void onCacheUncommittedContains() { cache_uncommitted_contains.increment(); }
    public void onCacheUncommittedGet() { cache_uncommitted_get.increment(); }
    public void onCacheUncommittedPut() { cache_uncommitted_put.increment(); }
    public void onCacheUncommittedRemove() { cache_uncommitted_remove.increment(); }


    /** READ-through: a user READ miss hit store and filled committed cache. */
    public void onReadThroughFillCommittedFromStore(boolean knownAbsent) {
        if (knownAbsent) read_through_fill_committed_from_store_known_absent.increment();
        else read_through_fill_committed_from_store_value.increment();
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

        //user write outcomes
        long userWriteUncommitedCacheGetTotal = user_write_uncommitted_cache_get_total.sum();
        long userWriteUncommitedCacheNewValueAddedTotal = user_write_uncommitted_cache_new_value_added_total.sum();
        long userWriteCommitedCacheGetTotal = user_write_committed_cache_get_total.sum();
        long userWriteCommittedCacheValueInvalidatedTotal = user_write_committed_cache_value_invalidated_total.sum();

        // USER read outcomes
        long userReadFromCommittedCacheWithValue = user_read_from_committed_cache_value.sum();
        long userReadFromCommittedCacheWithAbsentValue = user_read_from_committed_cache_known_absent.sum();
        long userReadFromUNCommittedCacheWithValue = user_read_from_uncommitted_cache_value.sum();
        long userReadFromUNCommittedCacheAbsentValue = user_read_from_uncommitted_cache_known_absent.sum();

        long rsV = user_read_from_store_value.sum();
        long rsA = user_read_from_store_known_absent.sum();
        long userReadWriteToCommittedCache = user_read_write_to_committed_cache.sum();


        long userReadFromCache = userReadFromCommittedCacheWithValue + userReadFromCommittedCacheWithAbsentValue
                + userReadFromUNCommittedCacheWithValue + userReadFromUNCommittedCacheAbsentValue;
        double userReadCacheRate = userGets == 0 ? 0.0 : (double) userReadFromCache / (double) userGets;

        // INTERNAL cache ops
        //committed
        long cContains = cache_committed_contains.sum();
        long cGet = cache_committed_get.sum();
        long cPutWithValue = cache_committed_put_with_value.sum();
        long cPutAbsent = cache_committed_put_absent.sum();
        long cRem = cache_committed_remove.sum();
        long cRemAbsent = cache_committed_remove_known_absent.sum();

        //uncommitted
        long uContains = cache_uncommitted_contains.sum();
        long uGet = cache_uncommitted_get.sum();
        long uPut = cache_uncommitted_put.sum();
        long uRem = cache_uncommitted_remove.sum();


        // Semantics
        long rtV = read_through_fill_committed_from_store_value.sum();
        long rtA = read_through_fill_committed_from_store_known_absent.sum();

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
        long storeRemove = store_delete.sum();
        long storeFlushToUpdateNs = avg(store_flush_nanos.sum(), store_flush_counts.sum());
        long storeFlushCommittedAndUncommittedNs = avg(store_flush_committed_and_uncommitted_nanos.sum(), store_flush_committed_and_uncommitted_count.sum());


        return String.format(Locale.ROOT,
                "owner=%s " +
                        "ds_cache_metrics name=%s " +
                        // USER totals
                        "user_get_total=%d user_put_total=%d user_delete_total=%d user_batch_total=%d user_flush_total=%d " +
                       // User write outcomes
                        "user_write_uncommitted_cache_get_total=%d user_write_uncommitted_cache_new_value_added_total=%d user_write_committed_cache_get_total=%d user_write_committed_cache_value_invalidated_total=%d " +
                        // USER read outcomes
                        "user_read_from_committed_cache_value=%d user_read_from_committed_cache_known_absent=%d " +
                        "user_read_from_uncommitted_cache_value=%d user_read_from_uncommitted_cache_known_absent=%d " +
                        "user_read_from_store_value=%d user_read_from_store_known_absent=%d  user_read_write_to_committed_cache=%d "+
                        "user_read_cache_rate=%.4f " +
                        // INTERNAL committed ops
                        "cache_committed_contains=%d cache_committed_get=%d cache_committed_put=%d cache_committed_put_absent=%d cache_committed_remove=%d cache_committed_remove_absent=%d " +
                        // INTERNAL uncommitted ops
                        "cache_uncommitted_contains=%d cache_uncommitted_get=%d cache_uncommitted_put=%d cache_uncommitted_remove=%d " +
                        // SEMANTICS
                        "read_through_fill_committed_from_store_value=%d read_through_fill_committed_from_store_known_absent=%d " +
                        // FLUSH reasons
                        "write_flush_manual=%d write_flush_size=%d write_flush_close=%d " +
                        // SIZES
                        "committed_size=%d uncommitted_size=%d " +
                        // TIMINGS
                        "avg_read_store_ns=%d avg_write_flush_ns=%d avg_write_batch_ns=%d " +
                        //Flush
                        "store_flush_to_update=%d store_flush_to_remove=%d store_flush_to_update_ns=%d store_flush_committed_and_uncommitted_ns=%d  storeRemove=%d "
                ,
                owner,
                name,
                userGets, userPuts, userDels, userBatches, userFlushes,
                userWriteUncommitedCacheGetTotal, userWriteUncommitedCacheNewValueAddedTotal, userWriteCommitedCacheGetTotal, userWriteCommittedCacheValueInvalidatedTotal,
                userReadFromCommittedCacheWithValue, userReadFromCommittedCacheWithAbsentValue,
                userReadFromUNCommittedCacheWithValue, userReadFromUNCommittedCacheAbsentValue,
                rsV, rsA, userReadWriteToCommittedCache,
                userReadCacheRate,
                cContains, cGet, cPutWithValue, cPutAbsent, cRem, cRemAbsent,
                uContains, uGet, uPut, uRem,
                rtV, rtA,
                fMan, fSize, fClose,
                committedSize.getAsInt(), uncommittedSize.getAsInt(),
                avgReadStoreNs, avgWriteFlushNs, avgWriteBatchNs,
                //flush
                storeFlushToUpdateCount, storeFlushToRemoveCount, storeFlushToUpdateNs, storeFlushCommittedAndUncommittedNs, storeRemove);
    }

    private static long avg(long total, long count) {
        return count == 0 ? 0 : (total / count);
    }
}
