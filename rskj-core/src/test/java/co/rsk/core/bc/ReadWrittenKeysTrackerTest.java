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

import java.util.Set;


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

    @Test
    void createATrackerShouldHaveEmptyMaps() {
        Assertions.assertEquals(0, tracker.getTemporalReadKeys().size());
        Assertions.assertEquals(0, tracker.getTemporalWrittenKeys().size());
    }

    @Test
    void addReadKeyToTheTrackerAndShouldBeInReadMap() {
        tracker.addNewReadKey(key1);
        Set<ByteArrayWrapper> temporalReadKeys = tracker.getTemporalReadKeys();
        assertKeyWasAddedInMap(temporalReadKeys, key1);
    }

    @Test
    void addReadKeyToTheTrackerAndShouldntBeInWrittenMap() {
        tracker.addNewReadKey(key1);
        Assertions.assertEquals(0, tracker.getTemporalWrittenKeys().size());
    }

    @Test
    void addWrittenKeyToTheTrackerAndShouldBeInWrittenMap() {
        tracker.addNewWrittenKey(key1);
        Set<ByteArrayWrapper> temporalWrittenKeys = tracker.getTemporalWrittenKeys();
        assertKeyWasAddedInMap(temporalWrittenKeys, key1);
    }

    @Test
    void addWrittenKeyToTheTrackerAndShouldntBeInReadMap() {
        tracker.addNewWrittenKey(key1);
        Assertions.assertEquals(0, tracker.getTemporalReadKeys().size());
    }

    @Test
    void clearTrackerShouldEmptyTheMaps() {
        tracker.addNewWrittenKey(key1);
        tracker.addNewReadKey(key1);
        tracker.addNewWrittenKey(key2);
        tracker.addNewReadKey(key2);

        Assertions.assertEquals(2, tracker.getTemporalReadKeys().size());
        Assertions.assertEquals(2, tracker.getTemporalWrittenKeys().size());

        tracker.clear();

        Assertions.assertEquals(0, tracker.getTemporalReadKeys().size());
        Assertions.assertEquals(0, tracker.getTemporalWrittenKeys().size());
    }

    @Test
    void createADummyTrackerShouldHaveEmptyMaps() {
        Assertions.assertEquals(0, dummyTracker.getTemporalReadKeys().size());
        Assertions.assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());
    }

    @Test
    void addReadKeyToTheDummyTrackerShouldDoNothing() {
        dummyTracker.addNewReadKey(key1);
        Assertions.assertEquals(0, dummyTracker.getTemporalReadKeys().size());
    }

    @Test
    void addReadKeyToTheTrackerShouldDoNothing() {
        dummyTracker.addNewReadKey(key1);
        Assertions.assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());
    }

    @Test
    void addWrittenKeyToTheDummyTrackerShouldDoNothing() {
        dummyTracker.addNewWrittenKey(key1);
        Assertions.assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());
    }

    @Test
    void clearDummyTrackerShouldDoNothing() {
        dummyTracker.addNewWrittenKey(key1);
        dummyTracker.addNewReadKey(key1);
        dummyTracker.addNewWrittenKey(key2);
        dummyTracker.addNewReadKey(key2);

        Assertions.assertEquals(0, dummyTracker.getTemporalReadKeys().size());
        Assertions.assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());

        dummyTracker.clear();

        Assertions.assertEquals(0, dummyTracker.getTemporalReadKeys().size());
        Assertions.assertEquals(0, dummyTracker.getTemporalWrittenKeys().size());
    }

    private void assertKeyWasAddedInMap(Set<ByteArrayWrapper> map, ByteArrayWrapper key) {
        Assertions.assertEquals(1, map.size());
        Assertions.assertTrue(map.contains(key));
    }
}
