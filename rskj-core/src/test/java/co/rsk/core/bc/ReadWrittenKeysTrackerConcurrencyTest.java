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
package co.rsk.core.bc;

import org.ethereum.db.ByteArrayWrapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests verify that the ReadWrittenKeysTracker behaves correctly under
 * real parallel execution, including:
 *
 * <p>No false collision detection when key spaces are disjoint
 * <p>Correct collision detection when overlaps occur
 * <p>No structural corruption or exceptions during concurrent add and read operations per thread logical key space
 * <p>No structural corruption or exceptions during concurrent add and read operations per global logical key space
 *
 * <p>The tests use synchronization primitives (CyclicBarrier and
 * CountDownLatch) to maximize overlap between worker threads and
 * increase the likelihood of exposing race conditions.
 */
class ReadWrittenKeysTrackerConcurrencyTest {

    /**
     * Verifies that concurrent writes from multiple threads using
     * disjoint key spaces do not produce a collision.
     *
     * <p>Two worker threads add read and written keys in parallel.
     * Since their key namespaces do not overlap, detectCollision()
     * must return false. The test repeats multiple rounds to increase
     * the chance of exposing race conditions.
     */
    @Test
    void noCollision_underConcurrentWrites_disjointKeys() throws Exception {
        Assumptions.assumeTrue(Runtime.getRuntime().availableProcessors() >= 2, "Needs >= 2 CPUs to meaningfully test parallelism");

        ReadWrittenKeysTracker tracker = new ReadWrittenKeysTracker();

        int iters = 200;
        int keysPerThread = 5_000;

        for (int round = 0; round < iters; round++) {
            tracker.clear();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CyclicBarrier start = new CyclicBarrier(2);

            Future<?> f1 = pool.submit(() -> {
                await(start);
                for (int i = 0; i < keysPerThread; i++) {
                    tracker.addNewReadKey(key(1, 1, i));
                    tracker.addNewWrittenKey(key(1, 1, i + 10_000));
                }
            });

            Future<?> f2 = pool.submit(() -> {
                await(start);
                for (int i = 0; i < keysPerThread; i++) {
                    tracker.addNewReadKey(key(2, 2, i));
                    tracker.addNewWrittenKey(key(2, 2, i + 10_000));
                }
            });

            f1.get();
            f2.get();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            assertFalse(tracker.detectCollision(), "Expected no collision on disjoint key sets (round " + round + ")");
        }
    }

    /**
     * Verifies that detectCollision() returns true when there is
     * a write-read or write-write overlap between threads.
     *
     * <p>One thread writes a shared key while another reads or writes
     * the same key. After both complete, detectCollision() must detect
     * the conflict.
     */

    @Test
    void collision_detected_whenWriteOverlapsReadOrWrite() throws Exception {
        Assumptions.assumeTrue(Runtime.getRuntime().availableProcessors() >= 2, "Needs >= 2 CPUs to meaningfully test parallelism");

        ReadWrittenKeysTracker tracker = new ReadWrittenKeysTracker();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);

        ByteArrayWrapper shared = new ByteArrayWrapper(new byte[]{7, 7, 7});

        Future<?> writer = pool.submit(() -> {
            await(start);
            tracker.addNewWrittenKey(shared);
        });

        Future<?> reader = pool.submit(() -> {
            await(start);
            tracker.addNewReadKey(shared);
        });

        writer.get();
        reader.get();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(tracker.detectCollision(), "Expected collision (write-read overlap)");
    }

    /**
     * Verifies that concurrent additions and reads
     * (getThisThreadReadKeys / getThisThreadWrittenKeys)
     * do not throw exceptions or corrupt internal state.
     *
     * <p>Each thread repeatedly adds keys while simultaneously
     * requesting its OWN keys. The goal is to stress
     * concurrent map and set access and ensure structural safety.
     *
     * <p>This test does NOT exercise global iteration while other threads mutate.
     */
    @Test
    void concurrentAddAndReads_doNotThrow_andRemainConsistentPerThread() throws Exception {
        Assumptions.assumeTrue(Runtime.getRuntime().availableProcessors() >= 2, "Needs >= 2 CPUs to meaningfully test parallelism");

        ReadWrittenKeysTracker tracker = new ReadWrittenKeysTracker();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> f1 = pool.submit(() -> {
            await(start);
            for (int i = 0; i < 50_000; i++) {
                tracker.addNewReadKey(key(1, 1, i));
                Set<ByteArrayWrapper> readKeys = tracker.getThisThreadReadKeys();
                assertNotNull(readKeys);
            }
        });

        Future<?> f2 = pool.submit(() -> {
            await(start);
            for (int i = 0; i < 50_000; i++) {
                tracker.addNewWrittenKey(key(2, 2, i));
                Set<ByteArrayWrapper> writtenKeys = tracker.getThisThreadWrittenKeys();
                assertNotNull(writtenKeys);
            }
        });

        start.countDown();
        f1.get();
        f2.get();

        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertFalse(tracker.detectCollision());
    }


    /**
     * A writer thread mutates the tracker while a reader thread
     * concurrently get global state. This must not throw (e.g., no ConcurrentModificationException).
     */
    @Test
    void concurrentTracker_doesNotThrow_whenReadingWhileWriting() throws Exception {
        final int DURATION_MS = 500;
        final int MAX_ITERS   = 5_000_000;

        ReadWrittenKeysTracker tracker = new ReadWrittenKeysTracker();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        AtomicReference<Throwable> observed = new AtomicReference<>();
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DURATION_MS);

        Future<?> writer = pool.submit(() -> {
            await(start);
            for (int i = 0; i < MAX_ITERS && System.nanoTime() < deadlineNanos && observed.get() == null; i++) {
                long fakeTid = i;
                tracker.addNewReadKeyToThread(fakeTid, key(i, i, i ));
                tracker.addNewWrittenKeyToThread(fakeTid, key(i+1, i+1, i + 1));
                if ((i & 0x3FFF) == 0) Thread.yield();
            }
        });

        Future<?> reader = pool.submit(() -> {
            await(start);
            try {
                while (System.nanoTime() < deadlineNanos && observed.get() == null) {
                    tracker.getReadKeysByThread();
                    tracker.getWrittenKeysByThread();
                    tracker.detectCollision();
                    Thread.yield();
                }
            } catch (Throwable t) {
                observed.compareAndSet(null, t);
            }
        });

        start.countDown();

        writer.get();
        reader.get();

        pool.shutdown();
        assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        Throwable t = observed.get();
        assertNull(t, "Concurrent tracker should not throw ... but got: " + t);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private ByteArrayWrapper key(int thread, int readWrite, int i) {

        return new ByteArrayWrapper(new byte[]{
                (byte) thread,
                (byte) readWrite,
                (byte) (i >> 8),
                (byte) (i & 0xff)
        });
    }
}
