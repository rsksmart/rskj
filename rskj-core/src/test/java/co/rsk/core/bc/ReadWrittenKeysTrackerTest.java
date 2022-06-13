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
import org.junit.Before;
import org.junit.Test;

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
            ReadWrittenKeysTest rwKeys = new ReadWrittenKeysTest(key1);
            completionService.submit(rwKeys);
        }
        boolean hasCollided = false;

        for (int i = 0; i < nThreads; i++) {
            try {
                Future<Boolean> hasCollidedFuture = completionService.take();
                hasCollided |= hasCollidedFuture.get();
                System.out.println(hasCollided);
            } catch (Exception e) {
                fail();
            }
        }
        service.shutdown();
        assertTrue(hasCollided);
    }

    @Test
    public void ifTwoThreadsWriteDifferentKeyCollideShouldBeFalse() {
        int nThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(nThreads);
        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(service);

        for (int i = 0; i < nThreads; i++) {
            ReadWrittenKeysTest rwKeys = new ReadWrittenKeysTest(i == 0? key1 : key2);
            completionService.submit(rwKeys);
        }
        boolean hasCollided = false;

        for (int i = 0; i < nThreads; i++) {
            try {
                Future<Boolean> hasCollidedFuture = completionService.take();
                hasCollided |= hasCollidedFuture.get();
                System.out.println(hasCollided);
            } catch (Exception e) {
                fail();
            }
        }
        service.shutdown();
        assertFalse(hasCollided);
    }

    private void assertKeyWasAddedInMap(Set<ByteArrayWrapper> map, ByteArrayWrapper key) {
        assertEquals(1, map.size());
        assertTrue(map.contains(key));
    }
    private class ReadWrittenKeysTest implements Callable<Boolean> {

        private final ByteArrayWrapper key;

        public ReadWrittenKeysTest(ByteArrayWrapper key) {
            this.key = key;
        }
        public Boolean call() {
            tracker.addNewWrittenKey(key);
            return tracker.hasCollided();
        }
    }

}
