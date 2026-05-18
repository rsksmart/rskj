/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.ByteArrayWrapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReadWrittenKeysTracker implements IReadWrittenKeysTracker {

    private final ConcurrentHashMap<Long, Set<ByteArrayWrapper>> readKeysByThread;
    private final ConcurrentHashMap<Long, Set<ByteArrayWrapper>> writtenKeysByThread;


    public ReadWrittenKeysTracker() {
        this.readKeysByThread = new ConcurrentHashMap<>();
        this.writtenKeysByThread = new ConcurrentHashMap<>();
    }

    @Override
    public Set<ByteArrayWrapper> getThisThreadReadKeys(){
        long threadId = Thread.currentThread().getId();
        return new HashSet<>(readKeysByThread.getOrDefault(threadId, Collections.emptySet()));
    }

    @Override
    public Set<ByteArrayWrapper> getThisThreadWrittenKeys(){
        long threadId = Thread.currentThread().getId();
        return new HashSet<>(writtenKeysByThread.getOrDefault(threadId, Collections.emptySet()));
    }

    @Override
    public  void addNewReadKey(ByteArrayWrapper key) {
        long threadId = Thread.currentThread().getId();
        this.addNewReadKeyToThread(threadId, key);
    }

    @Override
    public  void addNewWrittenKey(ByteArrayWrapper key) {
        long threadId = Thread.currentThread().getId();
        this.addNewWrittenKeyToThread(threadId, key);

    }

    public void addNewReadKeyToThread(long threadId, ByteArrayWrapper key) {
        readKeysByThread.computeIfAbsent(threadId,id -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public void addNewWrittenKeyToThread(long threadId, ByteArrayWrapper key) {
        writtenKeysByThread.computeIfAbsent(threadId,id -> ConcurrentHashMap.newKeySet()).add(key);
    }

    @Override
    public void clear() {
        readKeysByThread.clear();
        writtenKeysByThread.clear();
    }

    /**
     * Checks whether any two execution threads accessed overlapping state keys.
     *
     * <p>A collision exists if one thread writes a key that another thread
     * reads or writes (write–write or write–read conflict). Read–read
     * overlaps are ignored.
     *
     * <p><b>Intended usage:</b> call this method after a parallel execution
     * phase, once all worker tasks have completed. In the current execution
     * flow this is guaranteed by waiting on all submitted tasks via
     * {@link java.util.concurrent.ExecutorCompletionService#take()}
     * followed by {@link java.util.concurrent.Future#get()}, which provides
     * both completion and memory-visibility guarantees.
     *
     * <p><b>Why this method is not synchronized:</b> when invoked after the above
     * completion barrier, no worker task is still mutating the tracker and
     * {@code Future.get()} provides the required memory-visibility guarantees.
     * Therefore, additional synchronization around this read-only method is not
     * necessary.
     *
     * <p><b>Warning:</b> Invoking this method while other threads are still
     * mutating the tracker, or without a proper completion barrier
     * (e.g. {@code Future.get()}, {@code Thread.join()}, or
     * {@code CountDownLatch.await()}), may produce inconsistent or
     * nondeterministic results.
     *
     * <p><b>Warning:</b> Also ensure tasks do not spawn <i>subtasks</i> (e.g.,
     * new threads, {@code CompletableFuture.runAsync}, callbacks scheduled on other
     * executors) that continue mutating the tracker after the parent task completes;
     * such background work is not covered by the {@code take()+get()} barrier and
     * may lead to nondeterministic results.
     *
     * @return {@code true} if a cross-thread read/write conflict is detected,
     *         {@code false} otherwise.
     */
    public boolean detectCollision() {
        Set<Long> threads = new HashSet<>();
        threads.addAll(readKeysByThread.keySet());
        threads.addAll(writtenKeysByThread.keySet());

        for (Long threadId : threads) {
            Set<ByteArrayWrapper> baseReadKeys = readKeysByThread.getOrDefault(threadId, Collections.emptySet());
            Set<ByteArrayWrapper> baseWrittenKeys = writtenKeysByThread.getOrDefault(threadId, Collections.emptySet());

            for (Long threadId2 : threads) {
                if (threadId >= threadId2) {
                    continue;
                }

                Set<ByteArrayWrapper> temporalReadKeys = readKeysByThread.getOrDefault(threadId2, Collections.emptySet());
                Set<ByteArrayWrapper> temporalWrittenKeys = writtenKeysByThread.getOrDefault(threadId2, Collections.emptySet());

                 boolean isDisjoint = Collections.disjoint(baseWrittenKeys, temporalWrittenKeys) && Collections.disjoint(baseWrittenKeys, temporalReadKeys)
                    && Collections.disjoint(baseReadKeys, temporalWrittenKeys);

                 if (!isDisjoint) {
                     return true;
                 }
            }
        }
        return false;
    }

    @Override
    public Map<Long, Set<ByteArrayWrapper>> getReadKeysByThread() {
        Map<Long, Set<ByteArrayWrapper>> copy = new HashMap<>();
        readKeysByThread.forEach((k, v) -> copy.put(k, new HashSet<>(v)));
        return copy;
    }


    @Override
    public Map<Long, Set<ByteArrayWrapper>> getWrittenKeysByThread() {
        Map<Long, Set<ByteArrayWrapper>> copy = new HashMap<>();
        writtenKeysByThread.forEach((k, v) -> copy.put(k, new HashSet<>(v)));
        return copy;
    }

    @VisibleForTesting
    void removeReadKeyToThread(long threadId, ByteArrayWrapper key) {
        Set<ByteArrayWrapper> readKeys = readKeysByThread.get(threadId);
        if (readKeys == null) {
            return;
        }
        readKeys.remove(key);
    }

    @VisibleForTesting
    void removeWrittenKeyToThread(long threadId, ByteArrayWrapper key) {
        Set<ByteArrayWrapper> writtenKeys = writtenKeysByThread.get(threadId);
        if (writtenKeys == null) {
            return;
        }
        writtenKeys.remove(key);
    }
}
