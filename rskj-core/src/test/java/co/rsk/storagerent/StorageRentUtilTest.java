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

import co.rsk.core.RskAddress;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrieKeyMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static co.rsk.storagerent.StorageRentUtil.*;
import static co.rsk.storagerent.StorageRentUtil.rentThreshold;
import static org.ethereum.db.OperationType.READ_OPERATION;
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
    public void rentThreshold_forEachOperationType() {
        assertEquals(READ_THRESHOLD, StorageRentUtil.rentThreshold(OperationType.READ_OPERATION));
        assertEquals(WRITE_THRESHOLD, StorageRentUtil.rentThreshold(OperationType.WRITE_OPERATION));
        assertEquals(WRITE_THRESHOLD, StorageRentUtil.rentThreshold(OperationType.DELETE_OPERATION));
        assertEquals(3, OperationType.values().length);
    }

    @Test
    public void updatedTimestamp() {
        RentedNode rentedNode =  new RentedNode(new ByteArrayWrapper(new byte[0]) , READ_OPERATION,
                10, 0);

        // not enough duration, same timestamp
        assertEquals(0, updatedRentTimestamp(1, rentedNode));
        // normal duration, returns the current block timestamp
        assertEquals(400_000_000_00l, updatedRentTimestamp(400_000_000_00l, rentedNode));
        // excessive duration, partially advanced timestamp
        assertEquals(1_048_576_000, updatedRentTimestamp(100_000_000_000l, rentedNode));
    }

    @Test
    public void rollbackFee_eachCase() {
        ByteArrayWrapper aKey = new ByteArrayWrapper(new TrieKeyMapper()
                .getAccountKey(new RskAddress("a0663f719962ec10bb57865532bef522059dfd96")));
        OperationType anOperation = READ_OPERATION;
        long aNodeSize = 1;
        RentedNode rentedNode = new RentedNode(aKey, anOperation, aNodeSize, 1);

        // rollback fee => 25%
        long executionBlockTimestamp = 100000000;
        long expected = (long) (rentDue(rentedNode.getNodeSize(), executionBlockTimestamp) * 0.25);
        assertTrue(rollbackFee(executionBlockTimestamp, Collections.emptySet(), rentedNode) > 0);
        assertEquals(expected, rollbackFee(executionBlockTimestamp, Collections.emptySet(), rentedNode));

        // already paid && payable rent > 0 => 0
        executionBlockTimestamp = 100000000000l;
        assertTrue(payableRent(executionBlockTimestamp, rentedNode) > 0);
        assertEquals(0, rollbackFee(executionBlockTimestamp, Collections.singleton(rentedNode), rentedNode));

        // already paid && payable rent == 0 => 25%
        executionBlockTimestamp = 1000000000l;
        assertEquals(0, payableRent(executionBlockTimestamp, rentedNode));
        assertTrue(rollbackFee(executionBlockTimestamp, Collections.singleton(rentedNode), rentedNode) > 0);
        assertEquals((long) (rentDue(rentedNode.getNodeSize(), executionBlockTimestamp) * 0.25),
                rollbackFee(executionBlockTimestamp, Collections.singleton(rentedNode), rentedNode));
    }

    @Test
    public void payableRent_eachCase() {
        RentedNode rentedNode =  new RentedNode(new ByteArrayWrapper(new byte[0]) , READ_OPERATION,
                10, 0);

        // not enough duration, zero rent
        assertEquals(0, payableRent(1, rentedNode));
        // normal duration, accumulates rent
        assertEquals(2632, payableRent(400_000_000_00l, rentedNode));
        // excessive duration, outstanding rent
        assertEquals(RENT_CAP, payableRent(100_000_000_000l, rentedNode));
    }

    private long updatedRentTimestamp(long executionBlockTimestamp, RentedNode node) {
        return newTimestamp(
                node.getNodeSize(),
                rentDue(node.getNodeSize(), duration(executionBlockTimestamp, node.getRentTimestamp())),
                node.getRentTimestamp(),
                executionBlockTimestamp,
                RENT_CAP,
                rentThreshold(node.getOperationType()));
    }

    private void assertEqualsDouble(double expected, double actual) {
        assertEquals(expected, actual, 0);
    }
}
