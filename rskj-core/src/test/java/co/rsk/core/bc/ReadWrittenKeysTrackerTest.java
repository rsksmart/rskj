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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class ReadWrittenKeysTrackerTest {

    private IReadWrittenKeysTracker tracker;
    private IReadWrittenKeysTracker dummyTracker;
    private ByteArrayWrapper key1;
    private ByteArrayWrapper key2;


    @Before
    public void setup() {
        this.tracker = new ReadWrittenKeysTracker();
        this.dummyTracker = new DummyReadWrittenKeysTracker();
        this.key1 = new ByteArrayWrapper(new byte[]{1});
        this.key2 = new ByteArrayWrapper(new byte[]{2});
    }

    @Test
    public void createATrackerShouldHaveEmptyMaps() {
        assertEquals(0, tracker.getTemporalReadKeys().size());
        assertEquals(0, tracker.getTemporalWrittenKeys().size());
    }

    @Test
    public void addReadKeyToTheTrackerAndShouldBeInReadMap() {
        tracker.addNewReadKey(key1);
        Set<ByteArrayWrapper> temporalReadKeys = tracker.getTemporalReadKeys();
        assertKeyWasAddedInMap(temporalReadKeys, key1);
    }

    @Test
    public void addReadKeyToTheTrackerAndShouldntBeInWrittenMap() {
        tracker.addNewReadKey(key1);
        assertEquals(0, tracker.getTemporalWrittenKeys().size());
    }

    @Test
    public void addWrittenKeyToTheTrackerAndShouldBeInWrittenMap() {
        tracker.addNewWrittenKey(key1);
        Set<ByteArrayWrapper> temporalWrittenKeys = tracker.getTemporalWrittenKeys();
        assertKeyWasAddedInMap(temporalWrittenKeys, key1);
    }

    @Test
    public void addWrittenKeyToTheTrackerAndShouldntBeInReadMap() {
        tracker.addNewWrittenKey(key1);
        assertEquals(0, tracker.getTemporalReadKeys().size());
    }

    @Test
    public void clearTrackerShouldEmptyTheMaps() {
        tracker.addNewWrittenKey(key1);
        tracker.addNewReadKey(key1);
        tracker.addNewWrittenKey(key2);
        tracker.addNewReadKey(key2);

        assertEquals(2, tracker.getTemporalReadKeys().size());
        assertEquals(2, tracker.getTemporalWrittenKeys().size());

        tracker.clear();

        assertEquals(0, tracker.getTemporalReadKeys().size());
        assertEquals(0, tracker.getTemporalWrittenKeys().size());
    }

    @Test
    public void createADummyTrackerShouldHaveEmptyMaps() {
        assertEquals(0, dummyTracker.getTemporalReadKeys().size());
        assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());
    }

    @Test
    public void addReadKeyToTheDummyTrackerShouldDoNothing() {
        dummyTracker.addNewReadKey(key1);
        assertEquals(0, dummyTracker.getTemporalReadKeys().size());
    }

    @Test
    public void addReadKeyToTheTrackerShouldDoNothing() {
        dummyTracker.addNewReadKey(key1);
        assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());
    }

    @Test
    public void addWrittenKeyToTheDummyTrackerShouldDoNothing() {
        dummyTracker.addNewWrittenKey(key1);
        assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());
    }

    @Test
    public void clearDummyTrackerShouldDoNothing() {
        dummyTracker.addNewWrittenKey(key1);
        dummyTracker.addNewReadKey(key1);
        dummyTracker.addNewWrittenKey(key2);
        dummyTracker.addNewReadKey(key2);

        assertEquals(0, dummyTracker.getTemporalReadKeys().size());
        assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());

        dummyTracker.clear();

        assertEquals(0, dummyTracker.getTemporalReadKeys().size());
        assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());
    }

    @Test
    public void ifTwoThreadsWriteTheSameKeyCollideShouldBeTrue() {
        int nThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.singletonList(key1), Collections.emptyList());
            completionService.submit(rwKeys);
        }

        assertThereWasACollision(nThreads, service, completionService);
    }

    @Test
    public void ifTwoThreadsReadAndWriteTheSameKeyShouldCollide() {
        int nThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(service);
        List<ByteArrayWrapper> writtenKeys;
        List<ByteArrayWrapper> readKeys;
        for (int i = 0; i < nThreads; i++) {
            if (i == 0) {
                writtenKeys = Collections.singletonList(key1);
                readKeys = Collections.emptyList();
            } else {
                writtenKeys = Collections.emptyList();
                readKeys = Collections.singletonList(key1);
            }

            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, writtenKeys, readKeys);
            completionService.submit(rwKeys);
        }

        assertThereWasACollision(nThreads, service, completionService);
    }

    @Test
    public void ifTwoThreadsWriteDifferentKeyCollideShouldBeFalse() {
        int nThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.singletonList(i == 0? key1 : key2), Collections.emptyList());
            completionService.submit(rwKeys);
        }
        assertThereWasNotACollision(nThreads, service, completionService);
    }

    @Test
    public void allThreadIdsShouldBeStoredInTheReadKeysMap() {
        int nThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(service);
        boolean hasCollided = false;

        ReadWrittenKeysHelper rwKeys = new ReadWrittenKeysHelper(this.tracker, Collections.emptyList(), Collections.singletonList(key1));
        completionService.submit(rwKeys);

        try {
            Future<Boolean> hasCollidedFuture = completionService.take();
            hasCollided = hasCollidedFuture.get();
        } catch (Exception e) {
            fail();
        }

        Assert.assertFalse(hasCollided);
        ReadWrittenKeysHelper rwKeys2 = new ReadWrittenKeysHelper(this.tracker, Collections.singletonList(key1), Collections.singletonList(key1));
        completionService.submit(rwKeys2);

        try {
            Future<Boolean> hasCollidedFuture = completionService.take();
            hasCollided = hasCollidedFuture.get();
        } catch (Exception e) {
            fail();
        }

        service.shutdown();
        Assert.assertTrue(hasCollided);
    }

    private void assertThereWasNotACollision(int nThreads, ExecutorService service, CompletionService<Boolean> completionService) {
        boolean hasCollided = hasCollided(nThreads, completionService);
        assertFalse(hasCollided);
        service.shutdown();
    }

    private void assertThereWasACollision(int nThreads, ExecutorService service, CompletionService<Boolean> completionService) {
        boolean hasCollided = hasCollided(nThreads, completionService);
        System.out.println(hasCollided);
        assertTrue(hasCollided);
        service.shutdown();
    }

    private boolean hasCollided(int nThreads, CompletionService<Boolean> completionService) {
        boolean hasCollided = false;
        for (int i = 0; i < nThreads; i++) {
            try {
                Future<Boolean> hasCollidedFuture = completionService.take();
                hasCollided |= hasCollidedFuture.get();
            } catch (Exception e) {
                fail();
            }
        }
        return hasCollided;
    }

    private void assertKeyWasAddedInMap(Set<ByteArrayWrapper> map, ByteArrayWrapper key) {
        assertEquals(1, map.size());
        assertTrue(map.contains(key));
    }
    private static class ReadWrittenKeysHelper implements Callable<Boolean> {

        private final List<ByteArrayWrapper> readKeys;
        private final List<ByteArrayWrapper> writtenKeys;
        private final IReadWrittenKeysTracker tracker;

        public ReadWrittenKeysHelper(IReadWrittenKeysTracker tracker, List<ByteArrayWrapper> writtenKeys, List<ByteArrayWrapper> readKeys) {
            this.tracker = tracker;
            this.readKeys = readKeys;
            this.writtenKeys = writtenKeys;
        }
        //At first, it reads and then it writes.
        public Boolean call() {
            for (ByteArrayWrapper rk : readKeys) {
                tracker.addNewReadKey(rk);
            }

            for (ByteArrayWrapper wk : writtenKeys) {
                tracker.addNewWrittenKey(wk);
            }

            return tracker.hasCollided();
        }
    }

}
