package org.ethereum.datasource;
/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
import org.slf4j.Logger;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

public final class DataSourceWithLockMetrics {

    private final Logger logger;
    private final String name;
    private final long emitEveryOps;
    private final String owner;

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
    private final LongAdder flushManualLatencyTotal = new LongAdder();
    private final LongAdder flushSizeLatencyTotal = new LongAdder();
    private final LongAdder flushCloseLatencyTotal = new LongAdder();
    private final LongAdder batchLatencyTotal = new LongAdder();

    private final LongAdder getLatencyNanos = new LongAdder();
    private final LongAdder putLatencyNanos = new LongAdder();
    private final LongAdder deleteLatencyNanos = new LongAdder();
    private final LongAdder flushManualLatencyNanos = new LongAdder();
    private final LongAdder flushSizeLatencyNanos = new LongAdder();
    private final LongAdder flushCloseLatencyNanos = new LongAdder();
    private final LongAdder batchLatencyNanos = new LongAdder();

    public DataSourceWithLockMetrics(Logger logger, String name,
                                     String owner,
                                     long emitEveryOps) {
        this.logger = logger;
        this.name = name;
        this.owner = owner;
        this.emitEveryOps = emitEveryOps;
    }


    public void onReadLockWait(long nanos) {
        readLockAcquireTotal.increment();
        readLockWaitNanos.add(nanos);
        maybeEmit();
    }

    public void onWriteLockWait(long nanos) {
        writeLockAcquireTotal.increment();
        writeLockWaitNanos.add(nanos);
        maybeEmit();
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

    public void onFlushLatency(DataSourceWithCacheMetrics.FlushReason reason, long nanos) {
        switch (reason) {
            case MANUAL -> {
                flushManualLatencyTotal.increment();
                flushManualLatencyNanos.add(nanos);
            }
            case SIZE -> {
                flushSizeLatencyTotal.increment();
                flushSizeLatencyNanos.add(nanos);
            }
            case CLOSE -> {
                flushCloseLatencyTotal.increment();
                flushCloseLatencyNanos.add(nanos);
            }
        }
    }

    public void onBatchLatency(long nanos) {
        batchLatencyTotal.increment();
        batchLatencyNanos.add(nanos);
    }

    private void maybeEmit() {
        if (!logger.isInfoEnabled()) return;
        if (emitEveryOps <= 0) return;

        long ops = readLockAcquireTotal.sum() + writeLockAcquireTotal.sum();
        if (ops % emitEveryOps == 0) {
            logger.info(buildLine());
        }
    }

    private String buildLine() {
        long readAcq = readLockAcquireTotal.sum();
        long writeAcq = writeLockAcquireTotal.sum();

        long avgReadWait = avg(readLockWaitNanos.sum(), readAcq);
        long avgWriteWait = avg(writeLockWaitNanos.sum(), writeAcq);

        long avgReadHeld = avg(readLockHeldNanos.sum(), readAcq);
        long avgWriteHeld = avg(writeLockHeldNanos.sum(), writeAcq);

        long avgGetLatency = avg(getLatencyNanos.sum(), getLatencyTotal.sum());
        long avgPutLatency = avg(putLatencyNanos.sum(), putLatencyTotal.sum());
        long avgDeleteLatency = avg(deleteLatencyNanos.sum(), deleteLatencyTotal.sum());
        long avgFlushCloseLatency = avg(flushCloseLatencyNanos.sum(), flushCloseLatencyTotal.sum());
        long avgFlushManualLatency = avg(flushManualLatencyNanos.sum(), flushManualLatencyTotal.sum());
        long avgFlushSizeLatency = avg(flushSizeLatencyNanos.sum(), flushSizeLatencyTotal.sum());
        long avgBatchLatency = avg(batchLatencyNanos.sum(), batchLatencyTotal.sum());

        return String.format(Locale.ROOT,
                "owner=%s " +
                        "ds_lock_metrics name=%s " +
                        "read_lock_acquire_total=%d write_lock_acquire_total=%d " +
                        "avg_read_lock_wait_ns=%d avg_write_lock_wait_ns=%d " +
                        "avg_read_lock_held_ns=%d avg_write_lock_held_ns=%d " +
                        "avg_get_latency_ns=%d avg_put_latency_ns=%d avg_delete_latency_ns=%d " +
                        "avg_flush_close_latency_ns=%d avg_flush_manual_latency_ns=%d avg_flush_size_latency_ns=%d " +
                        "avg_batch_latency_ns=%d",
                owner,
                name,
                readAcq, writeAcq,
                avgReadWait, avgWriteWait,
                avgReadHeld, avgWriteHeld,
                avgGetLatency, avgPutLatency, avgDeleteLatency,
                avgFlushCloseLatency,
                avgFlushManualLatency,
                avgFlushSizeLatency,
                avgBatchLatency
        );
    }

    private static long avg(long total, long count) {
        return count == 0 ? 0 : (total / count);
    }
}
