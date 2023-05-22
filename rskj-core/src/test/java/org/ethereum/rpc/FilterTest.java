/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package org.ethereum.rpc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterTest {

    private Filter filter;

    @BeforeEach
    void setUp() {
        filter = new Filter();
    }

    @Test
    void testGetEvents() {
        Filter.FilterEvent mockEvent1 = new FilterEventMock();
        Filter.FilterEvent mockEvent2 = new FilterEventMock();
        Filter.FilterEvent mockEvent3 = new FilterEventMock();
        filter.add(mockEvent1);
        filter.add(mockEvent2);
        filter.add(mockEvent3);

        Object[] events = filter.getEvents();
        assertArrayEquals(new Object[] { mockEvent1.getJsonEventObject(), mockEvent2.getJsonEventObject(), mockEvent3.getJsonEventObject() }, events);
    }

    @Test
    @DisplayName("Test getEventsInternal() method")
    void testGetEventsInternal() {
        Filter.FilterEvent mockEvent1 = new FilterEventMock();
        Filter.FilterEvent mockEvent2 = new FilterEventMock();
        filter.add(mockEvent1);
        filter.add(mockEvent2);

        // get the internal events list and make sure it's a copy of the original list
        List<Filter.FilterEvent> events = filter.getEventsInternal();
        assertNotSame(events, filter.getEventsInternal());
        assertArrayEquals(new Object[] { mockEvent1, mockEvent2 }, events.toArray());
    }


    @Test
    void testHasExpired_mustFailIfExpired() {
        assertFalse(filter.hasExpired(1000));

        filter = new Filter();
        filter.hasExpired(1000); // to set the accessTime
        try {
            //TODO replace with TestUtils.waitFor when the other PR is merged
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(filter.hasExpired(1000));
    }

    @Test
    void testGetNewEvents() {
        Filter.FilterEvent mockEvent = new FilterEventMock();
        filter.add(mockEvent);

        // call getNewEvents() twice, the first time it should return the mock event, the second time it should return an empty array
        Object[] events1 = filter.getNewEvents();
        Object[] events2 = filter.getNewEvents();

        assertArrayEquals(new Object[] { mockEvent.getJsonEventObject() }, events1);
        assertArrayEquals(new Object[] {}, events2);
    }



    static class FilterEventMock implements Filter.FilterEvent {

        @Override
        public Object getJsonEventObject() {
            return "MockObject";
        }
    }
}