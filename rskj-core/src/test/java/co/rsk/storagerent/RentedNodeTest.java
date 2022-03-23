package co.rsk.storagerent;

import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrackedNode;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

import static co.rsk.storagerent.StorageRentComputation.*;
import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;
import static org.junit.Assert.*;

/**
 * Test ValueContainingNode public methods:
 * - payableRent
 * - updatedRentTimestamp
 *
 * NOTE: rent & timestamp calculations are tested in StorageRentComputationTest
 * */
public class RentedNodeTest {

    private static final ByteArrayWrapper SOME_KEY = new ByteArrayWrapper("key".getBytes(StandardCharsets.UTF_8));

    public static final long CURRENT_BLOCK_TIMESTAMP =
            new GregorianCalendar(2022, 1, 30).getTime().getTime();

    private static final long ONE_DAY_AGO_TIMESTAMP =
            new GregorianCalendar(2022, 1, 29).getTime().getTime();
    private static final long ONE_MONTH_AGO_TIMESTAMP =
            new GregorianCalendar(2021, 12, 30).getTime().getTime();
    private static final long ONE_YEAR_AGO_TIMESTAMP =
            new GregorianCalendar(2021, 1, 30).getTime().getTime();

    private static final long NODE_SIZE = 5032; // aprox size (bytes) of an erc20 from openzeppelin

    @Test
    public void payableRent_readOperation() {
        long limit = ONE_DAY_AGO_TIMESTAMP - TimeUnit.DAYS.toMillis(11);
        int expectedRent = 2551;
        checkPayableRent(READ_OPERATION,
            false,
            limit, // 12 days ago
            expectedRent
        );
        assertTrue(expectedRent > READ_THRESHOLD);

        checkPayableRent(READ_OPERATION,
            false,
            limit + TimeUnit.DAYS.toMillis(1), // 11 days ago
            0
        );
        checkPayableRent(READ_OPERATION,
            false,
            limit - TimeUnit.DAYS.toMillis(12), // 24 days ago
            RENT_CAP
        );
    }

    @Test
    public void payableRent_readOperationLoadsContractCode() {
        long limit = ONE_DAY_AGO_TIMESTAMP - TimeUnit.DAYS.toMillis(70);
        int expectedRent = 15093;
        checkPayableRent(READ_OPERATION,
            true,
            limit, // 71 days ago
            expectedRent
        );
        assertTrue(expectedRent > READ_THRESHOLD);

        checkPayableRent(READ_OPERATION,
            true,
            limit + TimeUnit.DAYS.toMillis(1), // 70 days ago
            0
        );
        checkPayableRent(READ_OPERATION,
            true,
            limit - TimeUnit.DAYS.toMillis(71), // 142 days ago
            RENT_CAP_CONTRACT_CODE
        );
    }

    @Test
    public void payableRent_writeOperation() {
        long limit = ONE_DAY_AGO_TIMESTAMP - TimeUnit.DAYS.toMillis(4);
        int expectedRent = 1062;
        checkPayableRent(WRITE_OPERATION,
            false,
            limit, // 5 days ago
            expectedRent
        );
        assertTrue(expectedRent > WRITE_THRESHOLD);

        checkPayableRent(WRITE_OPERATION,
            false,
            limit + TimeUnit.DAYS.toMillis(1), // 4 days ago
            0
        );

        checkPayableRent(WRITE_OPERATION,
            false,
            limit - TimeUnit.DAYS.toMillis(19), // 24 days ago
            RENT_CAP
        );
    }

    private void checkPayableRent(OperationType operationType, boolean loadsContractCode,
                                  Long lastRentPaidTimestamp, long expected) {
        RentedNode rentedNode = rentedNode(SOME_KEY, operationType,
                loadsContractCode, lastRentPaidTimestamp);

        assertEquals(expected, rentedNode.payableRent(CURRENT_BLOCK_TIMESTAMP));
    }

    @Test
    public void updatedTimestamp_readOperation() {
        checkUpdatedRentTimestamp(READ_OPERATION,
                false,
                ONE_DAY_AGO_TIMESTAMP,
                ONE_DAY_AGO_TIMESTAMP
        );
    }

    @Test
    public void updatedTimestamp_readOperationLoadsContractCode() {
        checkUpdatedRentTimestamp(READ_OPERATION,
                true,
                ONE_DAY_AGO_TIMESTAMP,
                ONE_DAY_AGO_TIMESTAMP
        );
    }

    @Test
    public void updatedTimestamp_writeOperation() {
        checkUpdatedRentTimestamp(WRITE_OPERATION,
                false,
                ONE_DAY_AGO_TIMESTAMP,
                ONE_DAY_AGO_TIMESTAMP
        );
    }

    @Test
    public void rollbackFee_shouldBe25OfPayableRent() {
        RentedNode rentedNode = rentedNode(SOME_KEY, READ_OPERATION, false,
                ONE_MONTH_AGO_TIMESTAMP);

        long payableRent = rentedNode.payableRent(CURRENT_BLOCK_TIMESTAMP);
        assertEquals(5000, payableRent);
        assertEquals((long) (payableRent * 0.25), rentedNode.rollbackFee(CURRENT_BLOCK_TIMESTAMP));
    }

    private void checkUpdatedRentTimestamp(OperationType operationType, boolean loadsContractCode,
                                           Long lastRentPaidTimestamp, long expected) {
        RentedNode rentedNode = rentedNode(SOME_KEY, operationType, loadsContractCode,
                lastRentPaidTimestamp);

        assertEquals(expected, rentedNode.getUpdatedRentTimestamp(CURRENT_BLOCK_TIMESTAMP));
    }

    private RentedNode rentedNode(ByteArrayWrapper someKey, OperationType operationType,
                                  boolean loadsContractCode, Long lastRentPaidTimestamp) {
        RentedNode rentedNode = new RentedNode(
            new TrackedNode(
                someKey,
                operationType,
                new Keccak256(HashUtil.keccak256("something".getBytes(StandardCharsets.UTF_8))).toHexString(),
                true,
                false
            ),
            NODE_SIZE,
            lastRentPaidTimestamp,
            loadsContractCode
        );
        return rentedNode;
    }
}
