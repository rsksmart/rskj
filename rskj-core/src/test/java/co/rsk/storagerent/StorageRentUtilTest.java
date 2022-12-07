/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.storagerent;

import org.ethereum.db.OperationType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static co.rsk.storagerent.StorageRentUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StorageRentUtilTest {

    private static final long ONE_MONTH = TimeUnit.DAYS.toMillis(30); // expressed in milliseconds
    public static final int NODE_SIZE = 100;

    @Test
    public void computeRent_computeReadBetweenThresholds() {
        long rent = StorageRentUtil.payableRent(rentDue(NODE_SIZE, ONE_MONTH * 9),
                RENT_CAP, READ_THRESHOLD);

        assertTrue(READ_THRESHOLD < rent);
        assertTrue(RENT_CAP > rent);
        assertEqualsDouble(2536.0, rent);
    }

    @Test
    public void computeRent_computeReadBelowThreshold() {
        long rent = StorageRentUtil.payableRent(rentDue(NODE_SIZE, ONE_MONTH * 8),
                RENT_CAP, READ_THRESHOLD);

        assertTrue(READ_THRESHOLD > rent);
        assertEqualsDouble(0, rent);
    }

    @Test
    public void computeRent_computeReadAboveCap() {
        long rent = StorageRentUtil.payableRent(rentDue(NODE_SIZE, ONE_MONTH * (17 + 1)),
                RENT_CAP, READ_THRESHOLD);

        assertEqualsDouble(RENT_CAP, rent);
    }

    @Test
    public void computeRent_computeReadInvalidArguments() {
        long notRelevant = 1;
        
        try {
            StorageRentUtil.payableRent(rentDue(0, notRelevant), notRelevant, notRelevant);
        } catch (IllegalArgumentException e) {
            assertEquals("node size must be positive", e.getMessage());
        }

        try {
            StorageRentUtil.payableRent(rentDue(notRelevant, 0), notRelevant, notRelevant);
        } catch (IllegalArgumentException e) {
            assertEquals("duration must be positive", e.getMessage());
        }

        try {
            StorageRentUtil.payableRent(rentDue(notRelevant, notRelevant), 0, notRelevant);
        } catch (IllegalArgumentException e) {
            assertEquals("cap must be positive", e.getMessage());
        }

        try {
            StorageRentUtil.payableRent(rentDue(notRelevant, notRelevant), notRelevant, 0);
        } catch (IllegalArgumentException e) {
            assertEquals("threshold must be positive", e.getMessage());
        }
    }

    @Test
    public void computeNewTimestamp_withinRange() { // new timestamp, rent is paid
        long newTimestamp = StorageRentUtil.newTimestamp(NODE_SIZE, 2574, 1,
                2, RENT_CAP, READ_THRESHOLD);

        assertEquals(2, newTimestamp);
    }

    @Test
    public void computeNewTimestamp_belowThreshold() { // same timestamp, since it won't be paid
        long newTimestamp = StorageRentUtil.newTimestamp(NODE_SIZE, 0,1,
                2, RENT_CAP, READ_THRESHOLD);

        assertEquals(1, newTimestamp);
    }

    @Test
    public void computeNewTimestamp_exceedingRentCap() { // partially advanced timestamp, since rent it's not completely paid
        long nodeSize = NODE_SIZE;
        long lastPaidTimestamp = 1_519_873_200_000L; // Thu Mar 01 00:00:00 ART 2018
        long currentBlockTimestamp = 1_614_567_600_000L; // Mon Mar 01 00:00:00 ART 2021
        long partiallyAdvancedTimestamp = 1_519_978_057_600L; // Fri Mar 02 05:07:37 ART 2018
        long rentDue = rentDue(nodeSize, currentBlockTimestamp - lastPaidTimestamp);

        long result = StorageRentUtil.newTimestamp(nodeSize, rentDue, lastPaidTimestamp,
                currentBlockTimestamp, RENT_CAP, READ_THRESHOLD);

        assertEquals(partiallyAdvancedTimestamp, result);
        assertTrue(lastPaidTimestamp < partiallyAdvancedTimestamp);
        assertTrue(partiallyAdvancedTimestamp < currentBlockTimestamp);
    }

    @Test
    public void computeNewTimestamp_invalidArguments() {
        long notRelevant = 1;

        try {
            StorageRentUtil.newTimestamp(0, notRelevant, notRelevant, notRelevant,
                    notRelevant, notRelevant);
        } catch (IllegalArgumentException e) {
            assertEquals("nodeSize must be positive", e.getMessage());
        }

        try {
            StorageRentUtil.newTimestamp(notRelevant, 0, notRelevant, notRelevant,
                    notRelevant, notRelevant);
        } catch (IllegalArgumentException e) {
            assertEquals("rentDue must be positive", e.getMessage());
        }

        try {
            StorageRentUtil.newTimestamp(notRelevant, notRelevant, 0, notRelevant,
                    notRelevant, notRelevant);
        } catch (IllegalArgumentException e) {
            assertEquals("lastPaidTime must be positive", e.getMessage());
        }

        try {
            StorageRentUtil.newTimestamp(notRelevant, notRelevant, notRelevant, 0,
                    notRelevant, notRelevant);
        } catch (IllegalArgumentException e) {
            assertEquals("currentBlockTimestamp must be positive", e.getMessage());
        }

        try {
            StorageRentUtil.newTimestamp(notRelevant, notRelevant, notRelevant, notRelevant,
                    0, notRelevant);
        } catch (IllegalArgumentException e) {
            assertEquals("rentCap must be positive", e.getMessage());
        }

        try {
            StorageRentUtil.newTimestamp(notRelevant, notRelevant, notRelevant, notRelevant,
                    notRelevant, 0);
        } catch (IllegalArgumentException e) {
            assertEquals("rentThreshold must be positive", e.getMessage());
        }
    }

    @Test
    public void feeByRent() {
        assertEquals(25, StorageRentUtil.feeByRent(100));
    }

    @Test
    public void rentThreshold() {
        assertEquals(READ_THRESHOLD, StorageRentUtil.rentThreshold(OperationType.READ_OPERATION));
        assertEquals(WRITE_THRESHOLD, StorageRentUtil.rentThreshold(OperationType.WRITE_OPERATION));
        assertEquals(WRITE_THRESHOLD, StorageRentUtil.rentThreshold(OperationType.DELETE_OPERATION));
        assertEquals(3, OperationType.values().length);
    }

    private void assertEqualsDouble(double expected, double actual) {
        assertEquals(expected, actual, 0);
    }
}
