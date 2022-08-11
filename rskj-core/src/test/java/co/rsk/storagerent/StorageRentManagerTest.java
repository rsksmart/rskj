package co.rsk.storagerent;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepositoryTracked;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.program.Program;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StorageRentManagerTest {

    @Test
    public void pay_shouldPayRent() {
        long nodeSize = 20L;
        long threeYearsAccumulatedRent = new GregorianCalendar(2019, 1, 1).getTime().getTime();


        // todo(fedejinich) should refactor this into Map<ByteArray, OperationType> and avoid using RentedNode instances
        // params
        Map<ByteArrayWrapper, OperationType> rentedNodes = mockRentedNodes(Arrays.asList(
            trackedNodeWriteOperation("key2"), // should pay rent cap
            trackedNodeWriteOperation("key4"), // should pay rent cap
            trackedNodeWriteOperation("key7"), // should pay rent cap
            trackedNodeReadOperation("key1"), // should pay rent cap
            trackedNodeReadOperation("key6") // should pay rent cap
        ));
        Map<ByteArrayWrapper, OperationType> rollbackNodes = mockRentedNodes(Arrays.asList(
            trackedNodeWriteOperation("key3"), // should pay 25% rent
            trackedNodeReadOperation("key5"), // should pay 25% rent
            trackedNodeWriteOperation("key8") // should pay 25% rent
        ));
        long gasRemaining = 28750;
        long executionBlockTimestamp = new GregorianCalendar(2022, 1, 1).getTime().getTime();
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class);

        // mocks
        when(mockBlockTrack.getStorageRentNodes()).thenReturn(rentedNodes);
        when(mockBlockTrack.getRollBackNodes()).thenReturn(rollbackNodes);
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
    }

    @Test
    public void pay_shouldPayZeroRent() {
        long nodeSize = 32L;
        long oneDayAccumulatedRent = new GregorianCalendar(2021, 12, 31).getTime().getTime();

        // params
        Map<ByteArrayWrapper, OperationType> rentedNodes = mockRentedNodes(Arrays.asList(
            // not enough accumulated rent, should pay zero gas
            trackedNodeReadOperation("key1"),
            // not enough accumulated rent, should pay zero gas
            trackedNodeWriteOperation("key3"),
            // not enough accumulated rent, should pay zero gas
            trackedNodeWriteOperation("key4")
        ));
        Map<ByteArrayWrapper, OperationType> rollbackNodes = new HashMap<>();
        long gasRemaining = 28750;
        long executionBlockTimestamp = new GregorianCalendar(2022, 1, 1).getTime().getTime();
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class);

        // mocks
        when(mockBlockTrack.getStorageRentNodes()).thenReturn(rentedNodes);
        when(mockBlockTrack.getRollBackNodes()).thenReturn(rollbackNodes);
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

    private Map<ByteArrayWrapper, OperationType> mockRentedNodes(List<RentedNode> rentedNodes) {
        Map<ByteArrayWrapper, OperationType> rentedNodeMap = new HashMap<>();
        rentedNodes.forEach(r -> rentedNodeMap.put(r.getKey(), r.getOperationType()));
        return rentedNodeMap;
    }

    @Test
    public void pay_shouldThrowRuntimeExceptionForEmptyLists() {
        int notRelevant = -1;

        // params
        Map<ByteArrayWrapper, OperationType> rentedNodes = new HashMap<>(); // empty
        Map<ByteArrayWrapper, OperationType> rollbackNodes = new HashMap<>(); // empty
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class); // useful to spy fetched data

        when(mockBlockTrack.getStorageRentNodes()).thenReturn(rentedNodes);
        when(mockBlockTrack.getRollBackNodes()).thenReturn(rollbackNodes);

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
        Map<ByteArrayWrapper, OperationType> rentedNodes = mockRentedNodes(Arrays.asList(
            trackedNodeReadOperation("key1"), // should pay rent cap
            trackedNodeWriteOperation("key2"), // should pay rent cap
            trackedNodeWriteOperation("key4"), // should pay rent cap
            trackedNodeReadOperation("key6"), // should pay rent cap
            trackedNodeWriteOperation("key7") // should pay rent cap
        ));
        Map<ByteArrayWrapper, OperationType> rollbackNodes = mockRentedNodes(Arrays.asList(
            trackedNodeWriteOperation("key3"), // should pay 25%
            trackedNodeReadOperation("key5"), // should pay 25%
            trackedNodeWriteOperation("key8") // should pay 25%
        ));

        long gasRemaining = 28750;
        long executionBlockTimestamp = new GregorianCalendar(2022, 1, 1).getTime().getTime();
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class);

        when(mockBlockTrack.getStorageRentNodes()).thenReturn(rentedNodes);
        when(mockBlockTrack.getRollBackNodes()).thenReturn(rollbackNodes);
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

        when(newBlockTrack.getStorageRentNodes()).thenReturn(rentedNodes);
        when(newBlockTrack.getRollBackNodes()).thenReturn(rollbackNodes);
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


    // todo(fedejinich) refactor pay_keyAsRentedAndRollbackNode/2, both tests share the same logic
    /***
     * Tests when a key is present as rentedNode (payableRent() > 0) and also as rollbackNode.
     * It should pay rent.
     */
    @Test
    public void pay_keyAsRentedAndRollbackNode() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        RskAddress anAddress = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");
        long firstBlockTimestamp = 7;
        int enoughGas = 100000;

        // init a new state
        MutableRepositoryTracked initialRepository = repositoryTracked(trieStore, null);

        initialRepository.addBalance(anAddress, Coin.valueOf(10));

        // timestamping the trie
        MutableRepositoryTracked repositoryWithTimestamps = (MutableRepositoryTracked) initialRepository.startTracking();
        StorageRentManager.pay(enoughGas, firstBlockTimestamp, initialRepository, repositoryWithTimestamps);

        repositoryWithTimestamps.commit();

        // save into the trie store
        initialRepository.save();

        // new trie but same trie store & root
        MutableRepositoryTracked blockTrack = repositoryTracked(trieStore, initialRepository.getRoot());

        // check that the balance is already increased (this adds a tracked node)
        assertEquals(Coin.valueOf(10), blockTrack.getBalance(anAddress));

        // both repositories should contain the same rented node
        ByteArrayWrapper key = new ByteArrayWrapper(new TrieKeyMapper().getAccountKey(anAddress));
        RentedNode initialNode = initialRepository.fetchRentedNode(key, READ_OPERATION);
        assertEquals(initialNode, blockTrack.fetchRentedNode(key, READ_OPERATION));
        assertEquals(firstBlockTimestamp, initialNode.getRentTimestamp());

        // create a new repository as a normal transaction
        MutableRepositoryTracked transactionTrack = (MutableRepositoryTracked) blockTrack.startTracking();

        // create a child repository (as an internal transaction)
        MutableRepositoryTracked internalTransaction = (MutableRepositoryTracked) transactionTrack.startTracking();

        // try to add balance but rollback
        internalTransaction.getBalance(anAddress);
        internalTransaction.rollback();

        // pay and update timestamp
        long updatedTimestamp = 50000000000l;
        StorageRentResult result = StorageRentManager.pay(enoughGas, updatedTimestamp,
                blockTrack, transactionTrack);

        transactionTrack.commit();

        assertTrue(result.paidRent() > 0);
        assertEquals(1, result.getRollbackNodes().size());
        assertEquals(result.getRentedNodes(), result.getRollbackNodes());
        assertEquals(new RentedNode(key, READ_OPERATION, 3, updatedTimestamp),
                transactionTrack.fetchRentedNode(key, READ_OPERATION));
    }

    /***
     * Tests when a key is present as rentedNode (payableRent() == 0) and also as rollbackNode.
     * It should pay a rollback fee (25% of accumulated rent).
     */
    @Test
    public void pay_keyAsRentedAndRollbackNode2() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        RskAddress anAddress = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");
        long firstBlockTimestamp = 7;
        int enoughGas = 100000;

        // init a new state
        MutableRepositoryTracked initialRepository = repositoryTracked(trieStore, null);

        initialRepository.addBalance(anAddress, Coin.valueOf(10));

        // timestamping the trie
        MutableRepositoryTracked repositoryWithTimestamps = (MutableRepositoryTracked) initialRepository.startTracking();
        StorageRentManager.pay(enoughGas, firstBlockTimestamp, initialRepository, repositoryWithTimestamps);

        repositoryWithTimestamps.commit();

        // save into the trie store
        initialRepository.save();

        // new trie but same trie store & root
        MutableRepositoryTracked blockTrack = repositoryTracked(trieStore, initialRepository.getRoot());

        // check that the balance is already increased (this adds a tracked node)
        assertEquals(Coin.valueOf(10), blockTrack.getBalance(anAddress));

        // both repositories should contain the same rented node
        ByteArrayWrapper key = new ByteArrayWrapper(new TrieKeyMapper().getAccountKey(anAddress));
        RentedNode initialNode = initialRepository.fetchRentedNode(key, READ_OPERATION);
        assertEquals(initialNode, blockTrack.fetchRentedNode(key, READ_OPERATION));
        assertEquals(firstBlockTimestamp, initialNode.getRentTimestamp());

        // create a new repository as a normal transaction
        MutableRepositoryTracked transactionTrack = (MutableRepositoryTracked) blockTrack.startTracking();

        // create a child repository (as an internal transaction)
        MutableRepositoryTracked internalTransaction = (MutableRepositoryTracked) transactionTrack.startTracking();

        // try to add balance but rollback
        internalTransaction.getBalance(anAddress);
        internalTransaction.rollback();

        // pay and update timestamp
        long notEnoughtAccumulatedRent = firstBlockTimestamp + 100000000;
        StorageRentResult result = StorageRentManager.pay(enoughGas, notEnoughtAccumulatedRent,
                blockTrack, transactionTrack);

        transactionTrack.commit();

        // it shouldn't have updated the timestamp
        assertTrue(result.paidRent() > 0);
        assertEquals(1, result.getRollbackNodes().size());
        assertEquals(result.getRentedNodes(), result.getRollbackNodes());
        assertEquals(new RentedNode(key, READ_OPERATION, 3, firstBlockTimestamp), // no timestamp update
                transactionTrack.fetchRentedNode(key, READ_OPERATION));
        assertEquals(1, result.getRollbacksRent());
        assertEquals(0, result.getPayableRent());
    }

    private MutableRepositoryTracked repositoryTracked(TrieStore trieStore, byte[] root) {
        return MutableRepositoryTracked.trackedRepository(
                new MutableTrieCache(new MutableTrieImpl(trieStore, root == null ? new Trie(trieStore) :
                        trieStore.retrieve(root).get())));
    }

    private void mockGetRentedNode(MutableRepositoryTracked mockBlockTrack, Map<ByteArrayWrapper, OperationType> rentedNodes,
                                   Map<ByteArrayWrapper, OperationType> rollbackNodes, long nodeSize, long rentTimestamp) {
        rentedNodes.forEach((key, operationType) -> when(mockBlockTrack.fetchRentedNode(key, operationType)).thenReturn(
                new RentedNode(key, operationType, nodeSize, rentTimestamp)));
        rollbackNodes.forEach((key, operationType) -> when(mockBlockTrack.fetchRentedNode(key, operationType)).thenReturn(
                new RentedNode(key, operationType, nodeSize, rentTimestamp)));
    }

    /**
     * Checks rent payment, given rented nodes and rollback nodes.
     */
    private void checkStorageRentPayment(long gasRemaining, long executionBlockTimestamp,
                                         MutableRepositoryTracked mockBlockTrack, MutableRepositoryTracked mockTransactionTrack, long expectedRemainingGas,
                                         long expectedPaidRent, long expectedPayableRent, long expectedRollbacksRent,
                                         long expectedRentedNodesCount, long expectedRollbackNodesCount) {
        StorageRentResult storageRentResult = StorageRentManager.pay(gasRemaining, executionBlockTimestamp,
                mockBlockTrack, mockTransactionTrack);
        long remainingGasAfterPayingRent = storageRentResult.getGasAfterPayingRent();

        assertTrue(remainingGasAfterPayingRent >= 0);
        assertEquals(expectedRemainingGas, remainingGasAfterPayingRent);
        assertEquals(expectedPaidRent, storageRentResult.paidRent());
        assertEquals(expectedPayableRent, storageRentResult.getPayableRent());
        assertEquals(expectedRollbacksRent, storageRentResult.getRollbacksRent());
        assertEquals(expectedRentedNodesCount, storageRentResult.getRentedNodes().size());
        assertEquals(expectedRollbackNodesCount, storageRentResult.getRollbackNodes().size());
    }

    public static RentedNode trackedNodeReadOperation(String key) {
        return trackedNode(key, READ_OPERATION);
    }

    public static RentedNode trackedNodeWriteOperation(String key) {
        return trackedNode(key, WRITE_OPERATION);
    }

    private static RentedNode trackedNode(String key, OperationType operationType) {
        // todo(fedejinich) should remove this
        long notRelevant = 0;
        return new RentedNode(
            new ByteArrayWrapper(key.getBytes(StandardCharsets.UTF_8)),
            operationType,
            notRelevant,
            notRelevant
        );
    }
}
