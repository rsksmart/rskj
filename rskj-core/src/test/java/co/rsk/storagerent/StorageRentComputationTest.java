package co.rsk.storagerent;

import org.junit.Test;

import static co.rsk.storagerent.StorageRentComputation.*;
import static org.junit.Assert.*;

public class StorageRentComputationTest {

    private static final double ONE_MONTH = 2629746; // expressed in seconds

    @Test
    public void computeRead() {
        double rent = StorageRentComputation.computeRent(100, ONE_MONTH * 9, RENT_CAP, READ_THRESHOLD);
        assertTrue(READ_THRESHOLD < rent);
        assertTrue(RENT_CAP > rent);
        assertEqualsDouble(2573.127170562744, rent);
    }

    @Test
    public void computeRead_belowThreshold_zeroRent() {
        double rent = StorageRentComputation.computeRent(100, ONE_MONTH * 8, RENT_CAP, READ_THRESHOLD);
        assertTrue(READ_THRESHOLD > rent);
        assertEqualsDouble(0, rent);
    }

    @Test
    public void computeRead_aboveCap_cappedRent() {
        double rent = StorageRentComputation.computeRent(100, ONE_MONTH * (17 + 1), RENT_CAP, READ_THRESHOLD);
        assertEqualsDouble(RENT_CAP, rent);
    }

    @Test
    public void computeRead_invalidArguments() {
        try {
            StorageRentComputation.computeRent(0, ONE_MONTH, RENT_CAP, READ_THRESHOLD);
        } catch (IllegalArgumentException e) {
            assertEquals("node size must be positive", e.getMessage());
        }

        try {
            StorageRentComputation.computeRent(100, 0, RENT_CAP, READ_THRESHOLD);
        } catch (IllegalArgumentException e) {
            assertEquals("duration must be positive", e.getMessage());
        }

        try {
            StorageRentComputation.computeRent(100, ONE_MONTH, 0, READ_THRESHOLD);
        } catch (IllegalArgumentException e) {
            assertEquals("cap must be positive", e.getMessage());
        }

        try {
            StorageRentComputation.computeRent(100, ONE_MONTH, RENT_CAP, 0);
        } catch (IllegalArgumentException e) {
            assertEquals("threshold must be positive", e.getMessage());
        }
    }

    private void assertEqualsDouble(double expected, double actual) {
        assertEquals(expected, actual, 0);
    }
}
