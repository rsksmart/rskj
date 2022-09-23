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

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.DummyReadWrittenKeysTracker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.util.*;
import java.util.concurrent.*;

public class ReadWrittenKeysTrackerTest {

    private IReadWrittenKeysTracker tracker;
    private IReadWrittenKeysTracker dummyTracker;
    private ByteArrayWrapper key1;
    private ByteArrayWrapper key2;


    @BeforeEach
    void setup() {
        this.tracker = new ReadWrittenKeysTracker();
        this.dummyTracker = new DummyReadWrittenKeysTracker();
        this.key1 = new ByteArrayWrapper(new byte[]{1});
        this.key2 = new ByteArrayWrapper(new byte[]{2});
    }

    ByteArrayWrapper getKey(int thread, int readWrite,int i ) {
        // Supports upto 65536 keys
        return new ByteArrayWrapper(new byte[]{(byte)thread, (byte) readWrite,
                (byte) (i >> 8), (byte) (i & 0xff)});
    }

    @Test
    void collisionWithLongerSets() {
        //..
        ReadWrittenKeysTracker myTracker = (ReadWrittenKeysTracker) this.tracker;
        int keysPerThread = 10;
        int maxThreads = 4;

        // Add read 10 distinct keys for each one of 4 threads
        for (int thread=0; thread<maxThreads; thread++) {
            for (int i = 0; i < keysPerThread; i++) {
                ByteArrayWrapper key = getKey(thread,0,i);
                myTracker.addNewReadKeyToThread(thread,key);
            }
        }
        // No collisions at this point
        Assertions.assertFalse(tracker.detectCollision());

        // Now add 10 distinct written keys per thread
        for (int thread=0;thread<maxThreads;thread++) {
            for (int i = 0; i < keysPerThread; i++) {
                ByteArrayWrapper key = getKey(thread,1,i);

                myTracker.addNewWrittenKeyToThread(thread,key);
            }
        }

        // No collisions at this point
        Assertions.assertFalse(tracker.detectCollision());

        // Now add 3 read keys to thread 0, shared with keys read by threads 1,2,3
        for (int i = 0; i < maxThreads-1; i++) {
            ByteArrayWrapper key = getKey(i+1,0,i);
            myTracker.addNewReadKeyToThread(0,key);
        }
        // No collisions at this point
        Assertions.assertFalse(tracker.detectCollision());

        ByteArrayWrapper readKeyAdded = getKey(1,1,5);

        // Now add a single read key to thread 3 that collides with a key written in thread 1
        myTracker.addNewReadKeyToThread(3,readKeyAdded);

        // Collision must be detected
        Assertions.assertTrue(tracker.detectCollision());

        // Now remove that key.
        myTracker.removeReadKeyToThread(3,readKeyAdded);

        // all back to normal, no collision
        Assertions.assertFalse(tracker.detectCollision());

        // Now add a write-write collision
        ByteArrayWrapper writeKeyAdded = readKeyAdded; // the same key, but written
        // Now add a single read key to thread 3 that collides with a key written in thread 1
        myTracker.addNewWrittenKeyToThread(3,writeKeyAdded);

        // Collision must be detected
        Assertions.assertTrue(tracker.detectCollision());

        myTracker.removeWrittenKeyToThread(3,writeKeyAdded);

        // all back to normal, no collision
        Assertions.assertFalse(tracker.detectCollision());

        /////////////////////////////////////////////////////////
        // Now we'll do the same, but in the opposite direction
        // between threads 1 and 3
        /////////////////////////////////////////////////////////
        
        readKeyAdded = getKey(3,1,5);

        // Now add a single read key to thread 1 that collides with a key written in thread 3
        myTracker.addNewReadKeyToThread(1,readKeyAdded);

        // Collision must be detected
        Assertions.assertTrue(tracker.detectCollision());

        // Now remove that key.
        myTracker.removeReadKeyToThread(1,readKeyAdded);

        // all back to normal, no collision
        Assertions.assertFalse(tracker.detectCollision());

        // Now add a write-write collision
        writeKeyAdded = readKeyAdded; // the same key, but written
        // Now add a single read key to thread 1 that collides with a key written in thread 3
        myTracker.addNewWrittenKeyToThread(1,writeKeyAdded);

        // Collision must be detected
        Assertions.assertTrue(tracker.detectCollision());
    }

    @Test
    void createATrackerShouldHaveEmptyKeysForThisThread() {
        Assertions.assertEquals(0, tracker.getThisThreadReadKeys().size());
        Assertions.assertEquals(0, tracker.getThisThreadWrittenKeys().size());
    }

    @Test
    void createATrackerShouldHaveEmptyKeysForAllThreads() {
        Assertions.assertEquals(0, tracker.getReadKeysByThread().size());
        Assertions.assertEquals(0, tracker.getWrittenKeysByThread().size());
    }

    @Test
    void addReadKeyToTheTrackerAndShouldBeInReadMapForThisThread() {
        tracker.addNewReadKey(key1);
        Set<ByteArrayWrapper> temporalReadKeys = tracker.getThisThreadReadKeys();
        assertKeyWasAddedInMap(temporalReadKeys, key1);
    }

    @Test
    void addReadKeyToTheTrackerAndShouldBeInReadKeysForAllThreads() {
        tracker.addNewReadKey(key1);
        Map<Long, Set<ByteArrayWrapper>> readKeys = tracker.getReadKeysByThread();
        Set<ByteArrayWrapper> readKeysByThisThread = readKeys.get(Thread.currentThread().getId());

        Assertions.assertEquals(1, readKeys.size());
        Assertions.assertEquals(1, readKeysByThisThread.size());
        Assertions.assertTrue(readKeysByThisThread.contains(key1));
    }

    @Test
    void addReadKeyToTheTrackerAndShouldNotBeInWrittenMapForThisThread() {
        tracker.addNewReadKey(key1);
        Assertions.assertEquals(0, tracker.getThisThreadWrittenKeys().size());
    }

    @Test
    void addReadKeyToTheTrackerAndShouldNotBeInWrittenMapForAllThreads() {
        tracker.addNewReadKey(key1);
        Assertions.assertEquals(0, tracker.getWrittenKeysByThread().size());
    }

    @Test
    void addWrittenKeyToTheTrackerAndShouldBeInWrittenMapForThisThread() {
        tracker.addNewWrittenKey(key1);
        Set<ByteArrayWrapper> temporalWrittenKeys = tracker.getThisThreadWrittenKeys();
        assertKeyWasAddedInMap(temporalWrittenKeys, key1);
    }

    @Test
    void addWrittenKeyToTheTrackerAndShouldBeInWrittenMapForAllThreads() {
        tracker.addNewWrittenKey(key1);
        Map<Long, Set<ByteArrayWrapper>> writtenKeys = tracker.getWrittenKeysByThread();

        Set<ByteArrayWrapper> writtenKeysByThisThread = writtenKeys.get(Thread.currentThread().getId());
        Assertions.assertEquals(1, writtenKeys.size());
        Assertions.assertEquals(1, writtenKeysByThisThread.size());
        Assertions.assertTrue(writtenKeysByThisThread.contains(key1));
    }

    @Test
    void addWrittenKeyToTheTrackerAndShouldNotBeInReadMapForThisThread() {
        tracker.addNewWrittenKey(key1);
        Assertions.assertEquals(0, tracker.getThisThreadReadKeys().size());
    }

    @Test
    void addWrittenKeyToTheTrackerAndShouldNotBeInReadMapForAllThreads() {
        tracker.addNewWrittenKey(key1);
        Assertions.assertEquals(0, tracker.getReadKeysByThread().size());
    }

    @Test
    void clearTrackerShouldEmptyAllTheMaps() {
        tracker.addNewWrittenKey(key1);
        tracker.addNewWrittenKey(key2);
        tracker.addNewReadKey(key1);
        tracker.addNewReadKey(key2);

        Assertions.assertEquals(1, tracker.getWrittenKeysByThread().size());
        Assertions.assertEquals(2, tracker.getThisThreadWrittenKeys().size());
        Assertions.assertEquals(1, tracker.getReadKeysByThread().size());
        Assertions.assertEquals(2, tracker.getThisThreadReadKeys().size());


        tracker.clear();

        Assertions.assertEquals(0, tracker.getWrittenKeysByThread().size());
        Assertions.assertEquals(0, tracker.getThisThreadWrittenKeys().size());
        Assertions.assertEquals(0, tracker.getReadKeysByThread().size());
        Assertions.assertEquals(0, tracker.getThisThreadReadKeys().size());
    }

    @Test
    void createADummyTrackerShouldHaveEmptyMaps() {
        Assertions.assertEquals(0, dummyTracker.getReadKeysByThread().size());
        Assertions.assertEquals(0, dummyTracker.getWrittenKeysByThread().size());
        Assertions.assertEquals(0, dummyTracker.getThisThreadReadKeys().size());
        Assertions.assertEquals(0, dummyTracker.getThisThreadWrittenKeys().size());
    }

    @Test
    void addReadKeyToTheDummyTrackerShouldDoNothing() {
        dummyTracker.addNewReadKey(key1);
        Assertions.assertEquals(0, dummyTracker.getReadKeysByThread().size());
        Assertions.assertEquals(0, dummyTracker.getThisThreadReadKeys().size());
    }

    @Test
    void addWrittenKeyToTheTrackerShouldDoNothing() {
        dummyTracker.addNewWrittenKey(key1);
        Assertions.assertEquals(0, dummyTracker.getThisThreadWrittenKeys().size());
        Assertions.assertEquals(0, dummyTracker.getWrittenKeysByThread().size());
    }

    @Test
    void clearDummyTrackerShouldDoNothing() {
        dummyTracker.addNewWrittenKey(key1);
        dummyTracker.addNewReadKey(key1);
        dummyTracker.addNewWrittenKey(key2);
        dummyTracker.addNewReadKey(key2);

        Assertions.assertEquals(0, dummyTracker.getThisThreadReadKeys().size());
        Assertions.assertEquals(0, dummyTracker.getThisThreadWrittenKeys().size());

        dummyTracker.clear();

        Assertions.assertEquals(0, dummyTracker.getThisThreadReadKeys().size());
        Assertions.assertEquals(0, dummyTracker.getThisThreadWrittenKeys().size());
    }

    @Test
    void ifAThreadReadsAndWritesTheSameKeyCollideShouldBeFalse() {
        int nThreads = 1;

        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.singleton(key1), Collections.singleton(key1));
            completionService.submit(rwKeys);
        }

        getTrackerHelperAfterCompletion(nThreads, completionService);
        Assertions.assertFalse(tracker.detectCollision());
    }

    @Test
    void ifAThreadWritesTwiceTheSameKeyCollideShouldBeFalse() {
        ReadWrittenKeysTracker myTracker = (ReadWrittenKeysTracker) this.tracker;
        myTracker.addNewWrittenKeyToThread(0, key1);
        myTracker.addNewWrittenKeyToThread(0, key1);
        Assertions.assertFalse(myTracker.detectCollision());
    }

    @Test
    void ifAThreadReadsTwiceTheSameKeyCollideShouldBeFalse() {
        ReadWrittenKeysTracker myTracker = (ReadWrittenKeysTracker) this.tracker;
        myTracker.addNewReadKeyToThread(0, key1);
        myTracker.addNewReadKeyToThread(0, key1);
        Assertions.assertFalse(myTracker.detectCollision());
    }

    @Test
    void ifTwoThreadsDontWriteAnyKeyCollideShouldBeFalse() {
        int nThreads = 2;

        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.emptySet(), Collections.emptySet());
            completionService.submit(rwKeys);
        }

        getTrackerHelperAfterCompletion(nThreads, completionService);
        Assertions.assertFalse(tracker.detectCollision());
    }

    @Test
    void ifTwoThreadsReadDifferentKeysCollideShouldBeFalse() {
        int nThreads = 2;

        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.emptySet(), Collections.singleton(i % 2 ==0 ? key1 : key2));
            completionService.submit(rwKeys);
        }

        getTrackerHelperAfterCompletion(nThreads, completionService);
        Assertions.assertFalse(tracker.detectCollision());
    }

    @Test
    void ifTwoThreadsReadTheSameKeyCollideShouldBeFalse() {
        int nThreads = 2;

        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.emptySet(), Collections.singleton(key1));
            completionService.submit(rwKeys);
        }

        getTrackerHelperAfterCompletion(nThreads, completionService);
        Assertions.assertFalse(tracker.detectCollision());
    }

    @Test
    void ifTwoThreadsWriteDifferentKeysCollideShouldBeFalse() {
        int nThreads = 2;

        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.singleton(i % 2 ==0 ? key1 : key2), Collections.emptySet());
            completionService.submit(rwKeys);
        }

        getTrackerHelperAfterCompletion(nThreads, completionService);
        Assertions.assertFalse(tracker.detectCollision());
    }

    @Test
    void ifTwoThreadsWriteTheSameKeyCollideShouldBeTrue() {
        int nThreads = 2;

        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.singleton(key1), Collections.emptySet());
            completionService.submit(rwKeys);
        }

        getTrackerHelperAfterCompletion(nThreads, completionService);
        Assertions.assertTrue(tracker.detectCollision());
    }

    @Test
    void ifTwoThreadsReadAndWriteTheSameKeyCollideShouldBeTrue() {
        int nThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);
        Set<ByteArrayWrapper> writtenKeys;
        Set<ByteArrayWrapper> readKeys;
        for (int i = 0; i < nThreads; i++) {
            boolean isEven = i % 2 == 0;
            writtenKeys = isEven? Collections.singleton(this.key1) : Collections.emptySet();
            readKeys = isEven? Collections.emptySet() : Collections.singleton(this.key1);
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, writtenKeys, readKeys);
            completionService.submit(rwKeys);
        }

        getTrackerHelperAfterCompletion(nThreads, completionService);
        Assertions.assertTrue(tracker.detectCollision());
    }

    @Test
    void ifTwoThreadsWriteTheSameKeyShouldBeStored() {
        int nThreads = 2;

        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.singleton(key1), Collections.emptySet());
            completionService.submit(rwKeys);
        }

        List<ReadWrittenKeysHelper> helpers = getTrackerHelperAfterCompletion(nThreads, completionService);

        Map<Long, Set<ByteArrayWrapper>> writtenKeysByThread = this.tracker.getWrittenKeysByThread();
        Assertions.assertEquals(nThreads, writtenKeysByThread.size());
        Map<Long, Set<ByteArrayWrapper>> readKeysByThread = this.tracker.getReadKeysByThread();
        Assertions.assertEquals(0, readKeysByThread.size());
        assertKeysAreAddedCorrectlyIntoTheTracker(helpers, writtenKeysByThread, readKeysByThread);
    }

    @Test
    void ifTwoThreadsReadAndWriteAKeyTheyShouldBeStored() {
        int nThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);
        Set<ByteArrayWrapper> writtenKeys;
        Set<ByteArrayWrapper> readKeys;
        for (int i = 0; i < nThreads; i++) {
            boolean isEven = i % 2 == 0;
            writtenKeys = isEven? Collections.singleton(this.key1) : Collections.emptySet();
            readKeys = isEven? Collections.emptySet() : Collections.singleton(this.key1);
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, writtenKeys, readKeys);
            completionService.submit(rwKeys);
        }

        List<ReadWrittenKeysHelper> helpers = getTrackerHelperAfterCompletion(nThreads, completionService);

        Map<Long, Set<ByteArrayWrapper>> writtenKeysByThread = this.tracker.getWrittenKeysByThread();
        Assertions.assertEquals(1, writtenKeysByThread.size());
        Map<Long, Set<ByteArrayWrapper>> readKeysByThread = this.tracker.getReadKeysByThread();
        Assertions.assertEquals(1, readKeysByThread.size());
        assertKeysAreAddedCorrectlyIntoTheTracker(helpers, writtenKeysByThread, readKeysByThread);
    }

    @Test
    void ifTwoThreadsReadSomeKeysTheyShouldBeStored() {
        int nThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<ReadWrittenKeysHelper> completionService = new ExecutorCompletionService<>(service);
        Set<ByteArrayWrapper> writtenKeys;
        Set<ByteArrayWrapper> readKeys;
        for (int i = 0; i < nThreads; i++) {
            writtenKeys = Collections.emptySet();
            readKeys = Collections.singleton(this.key1);
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, writtenKeys, readKeys);
            completionService.submit(rwKeys);
        }

        List<ReadWrittenKeysHelper> helpers = getTrackerHelperAfterCompletion(nThreads, completionService);
        Map<Long, Set<ByteArrayWrapper>> writtenKeysByThread = this.tracker.getWrittenKeysByThread();
        Assertions.assertEquals(0, writtenKeysByThread.size());
        Map<Long, Set<ByteArrayWrapper>> readKeysByThread = this.tracker.getReadKeysByThread();
        Assertions.assertEquals(2, readKeysByThread.size());
        assertKeysAreAddedCorrectlyIntoTheTracker(helpers, writtenKeysByThread, readKeysByThread);
    }

    private List<ReadWrittenKeysHelper> getTrackerHelperAfterCompletion(int nThreads, CompletionService<ReadWrittenKeysHelper> completionService) {
        List<ReadWrittenKeysHelper> helpers = new ArrayList<>();
        for (int i = 0; i < nThreads; i++) {
            try {
                Future<ReadWrittenKeysHelper> helperFuture = completionService.take();
                helpers.add(helperFuture.get());
            } catch (Exception e) {
                Assertions.fail();
            }
        }

        return helpers;
    }

    private void assertKeysAreAddedCorrectlyIntoTheTracker(List<ReadWrittenKeysHelper> helpers, Map<Long, Set<ByteArrayWrapper>> writtenKeysByThread, Map<Long, Set<ByteArrayWrapper>> readKeysByThread) {
        for (ReadWrittenKeysHelper h: helpers) {
            if (h.getWrittenKeys().size() == 0) {
                Assertions.assertNull(writtenKeysByThread.get(h.getThreadId()));
            } else {
                Assertions.assertEquals(h.getWrittenKeys().size(), writtenKeysByThread.get(h.getThreadId()).size());
                Assertions.assertTrue(h.getWrittenKeys().containsAll(writtenKeysByThread.get(h.getThreadId())));
            }

            if (h.getReadKeys().size() == 0) {
                Assertions.assertNull(readKeysByThread.get(h.getThreadId()));
            } else {
                Assertions.assertEquals(h.getReadKeys().size(), readKeysByThread.get(h.getThreadId()).size());
                Assertions.assertTrue(h.getReadKeys().containsAll(readKeysByThread.get(h.getThreadId())));
            }
        }
    }

    private void assertKeyWasAddedInMap(Set<ByteArrayWrapper> map, ByteArrayWrapper key) {
        Assertions.assertEquals(1, map.size());
        Assertions.assertTrue(map.contains(key));
    }
    private static class ReadWrittenKeysHelper implements Callable<ReadWrittenKeysHelper> {

        private final Set<ByteArrayWrapper> readKeys;
        private final Set<ByteArrayWrapper> writtenKeys;
        private final IReadWrittenKeysTracker tracker;
        private long threadId;

        public ReadWrittenKeysHelper(IReadWrittenKeysTracker tracker, Set<ByteArrayWrapper> writtenKeys, Set<ByteArrayWrapper> readKeys) {
            this.tracker = tracker;
            this.readKeys = readKeys;
            this.writtenKeys = writtenKeys;
            this.threadId = -1L;
        }
        //At first, it reads and then it writes.
        public ReadWrittenKeysHelper call() {

            this.threadId = Thread.currentThread().getId();

            for (ByteArrayWrapper rk : this.readKeys) {
                this.tracker.addNewReadKey(rk);
            }

            for (ByteArrayWrapper wk : this.writtenKeys) {
                this.tracker.addNewWrittenKey(wk);
            }
            return this;
        }

        public Set<ByteArrayWrapper> getReadKeys() {
            return this.readKeys;
        }

        public Set<ByteArrayWrapper> getWrittenKeys() {
            return this.writtenKeys;
        }

        public long getThreadId() {
            return this.threadId;
        }
    }

}
