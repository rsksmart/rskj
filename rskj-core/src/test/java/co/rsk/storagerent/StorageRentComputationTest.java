package co.rsk.storagerent;

import org.junit.Test;

import static co.rsk.storagerent.StorageRentComputation.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StorageRentComputationTest {

    private static final long ONE_MONTH = 2_629_746; // expressed in seconds

    @Test
    public void computeRead_normalArguments_rent() {
        long rent = StorageRentComputation.computeRent(rentDue(100, ONE_MONTH * 9),
                RENT_CAP, READ_THRESHOLD);

        assertTrue(READ_THRESHOLD < rent);
        assertTrue(RENT_CAP > rent);
        assertEqualsDouble(2573.0, rent);
    }

    @Test
    public void computeRead_belowThreshold_zeroRent() {
        long rent = StorageRentComputation.computeRent(rentDue(100, ONE_MONTH * 8),
                RENT_CAP, READ_THRESHOLD);

        assertTrue(READ_THRESHOLD > rent);
        assertEqualsDouble(0, rent);
    }

    @Test
    public void computeRead_aboveCap_cappedRent() {
        long rent = StorageRentComputation.computeRent(rentDue(100, ONE_MONTH * (17 + 1)),
                RENT_CAP, READ_THRESHOLD);

        assertEqualsDouble(RENT_CAP, rent);
    }

    @Test
    public void computeRead_invalidArguments() {
        try {
            StorageRentComputation.computeRent(rentDue(0, ONE_MONTH), RENT_CAP, READ_THRESHOLD);
        } catch (IllegalArgumentException e) {
            assertEquals("node size must be positive", e.getMessage());
        }

        try {
            StorageRentComputation.computeRent(rentDue(100, 0), RENT_CAP, READ_THRESHOLD);
        } catch (IllegalArgumentException e) {
            assertEquals("duration must be positive", e.getMessage());
        }

        try {
            StorageRentComputation.computeRent(rentDue(100, ONE_MONTH), 0, READ_THRESHOLD);
        } catch (IllegalArgumentException e) {
            assertEquals("cap must be positive", e.getMessage());
        }

        try {
            StorageRentComputation.computeRent(rentDue(100, ONE_MONTH), RENT_CAP, 0);
        } catch (IllegalArgumentException e) {
            assertEquals("threshold must be positive", e.getMessage());
        }
    }

    @Test
    public void computeTimestamp_withinRange_blockTimestamp() {
        long newTimestamp = StorageRentComputation.computeNewTimestamp(100, 2574, 1,
                2, RENT_CAP, READ_THRESHOLD);

        assertEquals(2, newTimestamp);
    }

    @Test
    public void computeTimestamp_belowThreshold_sameTimestamp() { // since it won't be paid
        long newTimestamp = StorageRentComputation.computeNewTimestamp(100, 0,1,
                2, RENT_CAP, READ_THRESHOLD);

        assertEquals(1, newTimestamp);
    }

    @Test
    public void computeTimestamp_exceedingRentCap_partiallyAdvancedTimestamp() { // since rent it's not completely paid
        long nodeSize = 100;
        long lastPaidTimestamp = 1_519_873_200_000L; // Thu Mar 01 00:00:00 ART 2018
        long currentBlockTimestamp = 1_614_567_600_000L; // Mon Mar 01 00:00:00 ART 2021
        long partiallyAdvancedTimestamp = 1_519_978_057_600L; // Fri Mar 02 05:07:37 ART 2018
        long rentDue = rentDue(nodeSize, currentBlockTimestamp - lastPaidTimestamp);

        long result = StorageRentComputation.computeNewTimestamp(nodeSize, rentDue, lastPaidTimestamp,
                currentBlockTimestamp, RENT_CAP, READ_THRESHOLD);

        assertEquals(partiallyAdvancedTimestamp, result);
        assertTrue(lastPaidTimestamp < partiallyAdvancedTimestamp);
        assertTrue(partiallyAdvancedTimestamp < currentBlockTimestamp);
    }

    private void assertEqualsDouble(double expected, double actual) {
        assertEquals(expected, actual, 0);
    }
}
