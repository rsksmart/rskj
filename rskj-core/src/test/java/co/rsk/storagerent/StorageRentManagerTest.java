package co.rsk.storagerent;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepositoryTracked;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrackedNode;
import org.ethereum.vm.program.Program;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class StorageRentManagerTest {
    public static final String TRANSACTION_HASH = "something";

    @Test
    public void pay_shouldPayRent() {
        long nodeSize = 20L;
        long threeYearsAccumulatedRent = new GregorianCalendar(2019, 1, 1).getTime().getTime();

        // params
        Set<TrackedNode> rentedNodes = new HashSet<>(Arrays.asList(
            trackedNodeWriteOperation("key2"), // should pay rent cap
            trackedNodeWriteOperation("key4"), // should pay rent cap
            trackedNodeWriteOperation("key7"), // should pay rent cap
            trackedNodeReadOperation("key1", true), // should pay rent cap
            trackedNodeReadOperation("key6", true) // should pay rent cap
        ));
        List<TrackedNode> rollbackNodes = Arrays.asList(
            trackedNodeWriteOperation("key3"), // should pay 25% rent
            trackedNodeReadOperation("key5", true), // should pay 25% rent
            trackedNodeWriteOperation("key8") // should pay 25% rent
        );
        long gasRemaining = 28750;
        long executionBlockTimestamp = new GregorianCalendar(2022, 1, 1).getTime().getTime();
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class);

        // mocks
        when(mockBlockTrack.getStorageRentNodes(TRANSACTION_HASH)).thenReturn(rentedNodes);
        when(mockBlockTrack.getRollBackNodes(TRANSACTION_HASH)).thenReturn(rollbackNodes);
        mockGetRentedNode(mockBlockTrack, rentedNodes, rollbackNodes, nodeSize, threeYearsAccumulatedRent);

        // expected
        long expectedRemainingGas = 0;
        long expectedPaidRent = 28750; // paid rent + rollbacks rent
        long expectedPayableRent = 25000;
        long expectedRollbacksRent = 3750;
        long expectedRentedNodesCount = 5;
        long expectedRollbackNodesCount = 3;

        checkStorageRentPayment(gasRemaining, executionBlockTimestamp,
                mockBlockTrack, mockTransactionTrack, expectedRemainingGas, expectedPaidRent, expectedPayableRent,
                expectedRollbacksRent, expectedRentedNodesCount, expectedRollbackNodesCount);
//        todo verifyTimestampUpdateInvoke(rentedNodes, executionBlockTimestamp, mockTransactionTrack);
    }

    @Test
    public void pay_shouldPayZeroRent() {
        long nodeSize = 32L;
        long oneDayAccumulatedRent = new GregorianCalendar(2021, 12, 31).getTime().getTime();

        // params
        Set<TrackedNode> rentedNodes = new HashSet<>(Arrays.asList(
            // not enough accumulated rent, should pay zero gas
            trackedNodeReadOperation("key1", true),
            // not enough accumulated rent, should pay zero gas
            trackedNodeWriteOperation("key3"),
            // not enough accumulated rent, should pay zero gas
            trackedNodeWriteOperation("key4")
        ));
        List<TrackedNode> rollbackNodes = new ArrayList<>();
        long gasRemaining = 28750;
        long executionBlockTimestamp = new GregorianCalendar(2022, 1, 1).getTime().getTime();
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class);

        // mocks
        when(mockBlockTrack.getStorageRentNodes(TRANSACTION_HASH)).thenReturn(rentedNodes);
        when(mockBlockTrack.getRollBackNodes(TRANSACTION_HASH)).thenReturn(rollbackNodes);
        mockGetRentedNode(mockBlockTrack, rentedNodes, rollbackNodes, nodeSize, oneDayAccumulatedRent);

        // expected
        long expectedRemainingGas = 28750;
        long expectedPaidRent = 0; // paid rent + rollbacks rent
        long expectedPayableRent = 0;
        long expectedRollbacksRent = 0;
        long expectedRentedNodesCount = 3;
        long expectedRollbackNodesCount = 0;

        checkStorageRentPayment(gasRemaining, executionBlockTimestamp,
                mockBlockTrack, mockTransactionTrack, expectedRemainingGas, expectedPaidRent, expectedPayableRent,
                expectedRollbacksRent, expectedRentedNodesCount, expectedRollbackNodesCount);
    }

    @Test
    public void pay_shouldThrowRuntimeExceptionForEmptyLists() {
        int notRelevant = -1;

        // params
        Set<TrackedNode> rentedNodes = new HashSet<>(); // empty
        List<TrackedNode> rollbackNodes = new ArrayList<>(); // empty
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class); // useful to spy fetched data

        when(mockBlockTrack.getStorageRentNodes(TRANSACTION_HASH)).thenReturn(rentedNodes);
        when(mockBlockTrack.getRollBackNodes(TRANSACTION_HASH)).thenReturn(rollbackNodes);

        try {
            // there are no expected values because this test should fail
            checkStorageRentPayment(notRelevant, notRelevant,
                    mockBlockTrack, mockTransactionTrack, notRelevant, notRelevant, notRelevant, notRelevant,
                    notRelevant, notRelevant);
        } catch (RuntimeException e) {
            assertEquals("there should be rented nodes or rollback nodes", e.getMessage());
        }
    }

    @Test
    public void pay_shouldThrowOOGException() {
        long nodeSize = 20L;
        long threeYearsAccumulatedRent = new GregorianCalendar(2019, 1, 1).getTime().getTime();

        // params
        Set<TrackedNode> rentedNodes = new HashSet<>(Arrays.asList(
            trackedNodeReadOperation("key1", true), // should pay rent cap
            trackedNodeWriteOperation("key2"), // should pay rent cap
            trackedNodeWriteOperation("key4"), // should pay rent cap
            trackedNodeReadOperation("key6", true), // should pay rent cap
            trackedNodeWriteOperation("key7") // should pay rent cap
        ));
        List<TrackedNode> rollbackNodes = Arrays.asList(
            trackedNodeWriteOperation("key3"), // should pay 25%
            trackedNodeReadOperation("key5", true), // should pay 25%
            trackedNodeWriteOperation("key8") // should pay 25%
        );

        long gasRemaining = 28750;
        long executionBlockTimestamp = new GregorianCalendar(2022, 1, 1).getTime().getTime();
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class);

        when(mockBlockTrack.getStorageRentNodes(TRANSACTION_HASH)).thenReturn(rentedNodes);
        when(mockBlockTrack.getRollBackNodes(TRANSACTION_HASH)).thenReturn(rollbackNodes);
        mockGetRentedNode(mockBlockTrack, rentedNodes, rollbackNodes, nodeSize, threeYearsAccumulatedRent);

        // expected
        long expectedRemainingGas = 0;
        long expectedPaidRent = 28750; // paid rent + rollbacks rent
        long expectedPayableRent = 25000;
        long expectedRollbacksRent = 3750;
        long expectedRentedNodesCount = 5;
        long expectedRollbackNodesCount = 3;

        // normal rent payment
        checkStorageRentPayment(gasRemaining, executionBlockTimestamp,
                mockBlockTrack, mockTransactionTrack, expectedRemainingGas, expectedPaidRent, expectedPayableRent,
                expectedRollbacksRent, expectedRentedNodesCount, expectedRollbackNodesCount);

        // now try to pay rent without enough gas

        MutableRepositoryTracked unusedTrack = mock(MutableRepositoryTracked.class);
        MutableRepositoryTracked newBlockTrack = mock(MutableRepositoryTracked.class);

        when(newBlockTrack.getStorageRentNodes(TRANSACTION_HASH)).thenReturn(rentedNodes);
        when(newBlockTrack.getRollBackNodes(TRANSACTION_HASH)).thenReturn(rollbackNodes);
        mockGetRentedNode(newBlockTrack, rentedNodes, rollbackNodes, nodeSize, threeYearsAccumulatedRent);

        try {
            long notEnoughGas = gasRemaining - 1;
            long notRelevant = -1;

            checkStorageRentPayment(notEnoughGas, executionBlockTimestamp,
                    newBlockTrack, unusedTrack, notRelevant, notRelevant, notRelevant,
                    notRelevant, notRelevant, notRelevant);
        } catch (Program.OutOfGasException e) {
            assertEquals("not enough gasRemaining to pay storage rent. gasRemaining: 28749, gasNeeded: 28750", e.getMessage());
        }
    }

    private void mockGetRentedNode(MutableRepositoryTracked mockBlockTrack, Set<TrackedNode> rentedNodes,
                                   List<TrackedNode> rollbackNodes, long nodeSize, long rentTimestamp) {
        rentedNodes.forEach(r -> when(mockBlockTrack.getRentedNode(r)).thenReturn(new RentedNode(r, nodeSize, rentTimestamp)));
        rollbackNodes.forEach(r -> when(mockBlockTrack.getRentedNode(r)).thenReturn(new RentedNode(r, nodeSize, rentTimestamp)));
    }

    /**
     * Checks rent payment, given rented nodes and rollback nodes.
     */
    private void checkStorageRentPayment(long gasRemaining, long executionBlockTimestamp,
                                         MutableRepositoryTracked mockBlockTrack, MutableRepositoryTracked mockTransactionTrack, long expectedRemainingGas,
                                         long expectedPaidRent, long expectedPayableRent, long expectedRollbacksRent,
                                         long expectedRentedNodesCount, long expectedRollbackNodesCount) {
        StorageRentManager storageRentManager = new StorageRentManager();

        long remainingGasAfterPayingRent = storageRentManager.pay(gasRemaining, executionBlockTimestamp,
                mockBlockTrack, mockTransactionTrack, TRANSACTION_HASH);

        assertTrue(remainingGasAfterPayingRent >= 0);
        assertEquals(expectedRemainingGas, remainingGasAfterPayingRent);
        assertEquals(expectedPaidRent, storageRentManager.getPaidRent());
        assertEquals(expectedPayableRent, storageRentManager.getPayableRent());
        assertEquals(expectedRollbacksRent, storageRentManager.getRollbacksRent());
        assertEquals(expectedRentedNodesCount, storageRentManager.getRentedNodes().size());
        assertEquals(expectedRollbackNodesCount, storageRentManager.getRollbackNodes().size());
    }

    public static TrackedNode trackedNodeReadOperation(String key, boolean result) {
        return trackedNode(key, READ_OPERATION, result);
    }

    public static TrackedNode trackedNodeWriteOperation(String key) {
        return trackedNode(key, WRITE_OPERATION, true);
    }

    private static TrackedNode trackedNode(String key, OperationType operationType, boolean result) {
        return new TrackedNode(
            new ByteArrayWrapper(key.getBytes(StandardCharsets.UTF_8)),
            operationType,
//                        LOADS_CONTRACT,
            TRANSACTION_HASH,
            result
        );
    }
}
