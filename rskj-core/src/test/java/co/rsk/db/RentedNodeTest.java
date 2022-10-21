package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.storagerent.RentedNode;
import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrieKeyMapper;
import org.junit.Test;

import java.util.Collections;

import static co.rsk.storagerent.StorageRentUtil.RENT_CAP;
import static co.rsk.storagerent.StorageRentUtil.rentDue;
import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RentedNodeTest {

    @Test
    public void rollbackFee() {
        ByteArrayWrapper aKey = new ByteArrayWrapper(new TrieKeyMapper()
                .getAccountKey(new RskAddress("a0663f719962ec10bb57865532bef522059dfd96")));
        OperationType anOperation = READ_OPERATION;
        long aNodeSize = 1;
        RentedNode rentedNode = new RentedNode(aKey, anOperation, aNodeSize, 1);

        // rollback fee => 25%
        long executionBlockTimestamp = 100000000;
        long expected = (long) (rentDue(rentedNode.getNodeSize(), executionBlockTimestamp) * 0.25);
        assertTrue(rentedNode.rollbackFee(executionBlockTimestamp, Collections.emptySet()) > 0);
        assertEquals(expected, rentedNode.rollbackFee(executionBlockTimestamp, Collections.emptySet()));

        // already paid && payable rent > 0 => 0
        executionBlockTimestamp = 100000000000l;
        assertTrue(rentedNode.payableRent(executionBlockTimestamp) > 0);
        assertEquals(0, rentedNode.rollbackFee(executionBlockTimestamp, Collections.singleton(rentedNode)));

        // already paid && payable rent == 0 => 25%
        executionBlockTimestamp = 1000000000l;
        assertEquals(0, rentedNode.payableRent(executionBlockTimestamp));
        assertTrue(rentedNode.rollbackFee(executionBlockTimestamp, Collections.singleton(rentedNode)) > 0);
        assertEquals((long) (rentDue(rentedNode.getNodeSize(), executionBlockTimestamp) * 0.25),
                rentedNode.rollbackFee(executionBlockTimestamp, Collections.singleton(rentedNode)));
    }

    @Test
    public void payableRent() {
        RentedNode rentedNode =  new RentedNode(new ByteArrayWrapper(new byte[0]) , READ_OPERATION,
                10, 0);

        // not enough duration, zero rent
        assertEquals(0, rentedNode.payableRent(1));
        // normal duration, accumulates rent
        assertEquals(2632, rentedNode.payableRent(400_000_000_00l));
        // excessive duration, outstanding rent
        assertEquals(RENT_CAP, rentedNode.payableRent(100_000_000_000l));
    }

    @Test
    public void updatedTimestamp() {
        RentedNode rentedNode =  new RentedNode(new ByteArrayWrapper(new byte[0]) , READ_OPERATION,
                10, 0);

        // not enough duration, same timestamp
        assertEquals(0, rentedNode.updatedRentTimestamp(1));
        // normal duration, returns the current block timestamp
        assertEquals(400_000_000_00l, rentedNode.updatedRentTimestamp(400_000_000_00l));
        // excessive duration, partially advanced timestamp
        assertEquals(1_048_576_000, rentedNode.updatedRentTimestamp(100_000_000_000l));
    }
}
