package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.storagerent.RentedNode;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrieKeyMapper;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static co.rsk.storagerent.StorageRentComputation.rentDue;
import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;
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
        long expected = (long) (rentedNode.rentByBlock(executionBlockTimestamp) * 0.25);
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
        assertEquals((long) (rentedNode.rentByBlock(executionBlockTimestamp) * 0.25), rentedNode.rollbackFee(executionBlockTimestamp, Collections.singleton(rentedNode)));
    }

}
