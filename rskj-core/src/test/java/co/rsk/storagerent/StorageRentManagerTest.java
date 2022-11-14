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
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StorageRentManagerTest {

    private StorageRentManager storageRentManager;

    @Before
    public void setup() {
        this.storageRentManager = new StorageRentManager();
    }

    @Test
    public void pay_shouldPayRent() {
        long nodeSize = 20L;
        long threeYearsAccumulatedRent = new GregorianCalendar(2019, 1, 1).getTime().getTime();

        // params
        Map<ByteArrayWrapper, OperationType> rentedKeys = new HashMap<>();
        rentedKeys.put(key("key2"), WRITE_OPERATION); // should pay rent cap
        rentedKeys.put(key("key4"), WRITE_OPERATION); // should pay rent cap
        rentedKeys.put(key("key7"), WRITE_OPERATION); // should pay rent cap
        rentedKeys.put(key("key1"), READ_OPERATION); // should pay rent cap
        rentedKeys.put(key("key6"), READ_OPERATION); // should pay rent cap

        Map<ByteArrayWrapper, OperationType> rollbackKeys = new HashMap<>();
        rollbackKeys.put(key("key3"), WRITE_OPERATION);
        rollbackKeys.put(key("key5"), READ_OPERATION);
        rollbackKeys.put(key("key8"), WRITE_OPERATION);

        long gasRemaining = 28750;
        long executionBlockTimestamp = new GregorianCalendar(2022, 1, 1).getTime().getTime();
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class);

        // mocks
        when(mockBlockTrack.getStorageRentNodes()).thenReturn(rentedKeys);
        when(mockBlockTrack.getRollBackNodes()).thenReturn(rollbackKeys);
        mockGetRentedNode(mockBlockTrack, rentedKeys, rollbackKeys, nodeSize, threeYearsAccumulatedRent);

        // expected
        long expectedRemainingGas = 0;
        long expectedPaidRent = 28750; // paid rent + rollbacks rent
        long expectedPayableRent = 25000;
        long expectedRollbacksRent = 3750;
        long expectedRentedNodesCount = 5;
        long expectedRollbackNodesCount = 3;

        checkStorageRentPayment(gasRemaining, executionBlockTimestamp,
                mockBlockTrack, mockTransactionTrack, expectedRemainingGas, expectedPayableRent,
                expectedRollbacksRent, expectedRentedNodesCount, expectedRollbackNodesCount, false,
                expectedPaidRent);
    }

    private ByteArrayWrapper key(String key) {
        return new ByteArrayWrapper(key.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void pay_shouldPayZeroRent() {
        long nodeSize = 32L;
        long oneDayAccumulatedRent = new GregorianCalendar(2021, 12, 31).getTime().getTime();

        // params
        long gasRemaining = 28750;
        long executionBlockTimestamp = new GregorianCalendar(2022, 1, 1).getTime().getTime();
        Map<ByteArrayWrapper, OperationType> rentedKeys = new HashMap<>();
        rentedKeys.put(key("key1"), READ_OPERATION); // not enough accumulated rent, should pay zero gas
        rentedKeys.put(key("key3"), WRITE_OPERATION); // not enough accumulated rent, should pay zero gas
        rentedKeys.put(key("key4"), WRITE_OPERATION); // not enough accumulated rent, should pay zero gas
        Map<ByteArrayWrapper, OperationType> rollbackKeys = new HashMap<>();

        // mocks
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class);
        when(mockBlockTrack.getStorageRentNodes()).thenReturn(rentedKeys);
        when(mockBlockTrack.getRollBackNodes()).thenReturn(rollbackKeys);
        mockGetRentedNode(mockBlockTrack, rentedKeys, rollbackKeys, nodeSize, oneDayAccumulatedRent);

        // expected
        long expectedRemainingGas = 28750;
        long expectedPaidRent = 0; // paid rent + rollbacks rent
        long expectedPayableRent = 0;
        long expectedRollbacksRent = 0;
        long expectedRentedNodesCount = 3;
        long expectedRollbackNodesCount = 0;

        checkStorageRentPayment(gasRemaining, executionBlockTimestamp,
                mockBlockTrack, mockTransactionTrack, expectedRemainingGas, expectedPayableRent,
                expectedRollbacksRent, expectedRentedNodesCount, expectedRollbackNodesCount, false,
                expectedPaidRent);
    }

    @Test
    public void pay_shouldThrowIllegalStateExceptionForEmptyLists() {
        int notRelevant = -1;

        // params
        Map<ByteArrayWrapper, OperationType> rentedKeys = new HashMap<>(); // empty
        Map<ByteArrayWrapper, OperationType> rollbackKeys = new HashMap<>(); // empty
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class); // useful to spy fetched data

        when(mockBlockTrack.getStorageRentNodes()).thenReturn(rentedKeys);
        when(mockBlockTrack.getRollBackNodes()).thenReturn(rollbackKeys);

        try {
            // there are no expected values because this test should fail
            checkStorageRentPayment(notRelevant, notRelevant,
                    mockBlockTrack, mockTransactionTrack, notRelevant, notRelevant, notRelevant,
                    notRelevant, notRelevant, false, notRelevant);
        } catch (IllegalStateException e) {
            assertEquals("there should be rented nodes or rollback nodes", e.getMessage());
        }
    }

    @Test
    public void pay_shouldThrowOOGException() {
        long nodeSize = 20L;
        long threeYearsAccumulatedRent = new GregorianCalendar(2019, 1, 1).getTime().getTime();

        // params
        Map<ByteArrayWrapper, OperationType> rentedKeys = new HashMap<>();
        rentedKeys.put(key("key1"), READ_OPERATION); // should pay rent cap
        rentedKeys.put(key("key2"), WRITE_OPERATION); // should pay rent cap
        rentedKeys.put(key("key4"), WRITE_OPERATION); // should pay rent cap
        rentedKeys.put(key("key6"), READ_OPERATION); // should pay rent cap
        rentedKeys.put(key("key7"), WRITE_OPERATION); // should pay rent cap

        Map<ByteArrayWrapper, OperationType> rollbackKeys = new HashMap<>();
        rollbackKeys.put(key("key3"), WRITE_OPERATION); // should pay 25%
        rollbackKeys.put(key("key5"), READ_OPERATION); // should pay 25%
        rollbackKeys.put(key("key8"), WRITE_OPERATION); // should pay 25%

        long gasRemaining = 28750;
        long executionBlockTimestamp = new GregorianCalendar(2022, 1, 1).getTime().getTime();
        MutableRepositoryTracked mockTransactionTrack = mock(MutableRepositoryTracked.class); // useful to spy rent updates
        MutableRepositoryTracked mockBlockTrack = mock(MutableRepositoryTracked.class);

        when(mockBlockTrack.getStorageRentNodes()).thenReturn(rentedKeys);
        when(mockBlockTrack.getRollBackNodes()).thenReturn(rollbackKeys);
        mockGetRentedNode(mockBlockTrack, rentedKeys, rollbackKeys, nodeSize, threeYearsAccumulatedRent);

        // expected
        long expectedRemainingGas = 0;
        long expectedPaidRent = 28750; // paid rent + rollbacks rent
        long expectedPayableRent = 25000;
        long expectedRollbacksRent = 3750;
        long expectedRentedNodesCount = 5;
        long expectedRollbackNodesCount = 3;

        // normal rent payment
        checkStorageRentPayment(gasRemaining, executionBlockTimestamp,
                mockBlockTrack, mockTransactionTrack, expectedRemainingGas, expectedPayableRent,
                expectedRollbacksRent, expectedRentedNodesCount, expectedRollbackNodesCount, false, expectedPaidRent);

        // now try to pay rent without enough gas

        MutableRepositoryTracked unusedTrack = mock(MutableRepositoryTracked.class);
        MutableRepositoryTracked newBlockTrack = mock(MutableRepositoryTracked.class);

        when(newBlockTrack.getStorageRentNodes()).thenReturn(rentedKeys);
        when(newBlockTrack.getRollBackNodes()).thenReturn(rollbackKeys);
        mockGetRentedNode(newBlockTrack, rentedKeys, rollbackKeys, nodeSize, threeYearsAccumulatedRent);

        long notEnoughGas = gasRemaining - 1;
        checkStorageRentPayment(notEnoughGas, executionBlockTimestamp,
                newBlockTrack, unusedTrack, 0, 25000,
                3750, 5, 3, true, 0);
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
        storageRentManager.pay(enoughGas, firstBlockTimestamp, initialRepository, repositoryWithTimestamps);

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
        StorageRentResult result = storageRentManager.pay(enoughGas, updatedTimestamp,
                blockTrack, transactionTrack);

        transactionTrack.commit();

        assertTrue(result.totalRent() > 0);
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
        storageRentManager.pay(enoughGas, firstBlockTimestamp, initialRepository, repositoryWithTimestamps);

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
        StorageRentResult result = storageRentManager.pay(enoughGas, notEnoughtAccumulatedRent,
                blockTrack, transactionTrack);

        transactionTrack.commit();

        // it shouldn't have updated the timestamp
        assertTrue(result.totalRent() > 0);
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

    private void mockGetRentedNode(MutableRepositoryTracked mockBlockTrack, Map<ByteArrayWrapper, OperationType> rentedKeys,
                                   Map<ByteArrayWrapper, OperationType> rollbackKeys, long nodeSize, long rentTimestamp) {
        rentedKeys.forEach((key, operationType) -> when(mockBlockTrack.fetchRentedNode(key, operationType)).thenReturn(
                new RentedNode(key, operationType, nodeSize, rentTimestamp)));
        rollbackKeys.forEach((key, operationType) -> when(mockBlockTrack.fetchRentedNode(key, operationType)).thenReturn(
                new RentedNode(key, operationType, nodeSize, rentTimestamp)));
    }

    /**
     * Checks rent payment, given rented nodes and rollback nodes.
     */
    private void checkStorageRentPayment(long gasRemaining, long executionBlockTimestamp,
                                         MutableRepositoryTracked mockBlockTrack,
                                         MutableRepositoryTracked mockTransactionTrack,
                                         long expectedRemainingGas, long expectedPayableRent,
                                         long expectedRollbacksRent, long expectedRentedNodesCount,
                                         long expectedRollbackNodesCount, boolean expectedIsOutOfGas,
                                         long expectedPaidRent) {
        StorageRentResult storageRentResult = storageRentManager.pay(gasRemaining, executionBlockTimestamp,
                mockBlockTrack, mockTransactionTrack);
        long remainingGasAfterPayingRent = storageRentResult.getGasAfterPayingRent();

        assertTrue(remainingGasAfterPayingRent >= 0);
        assertEquals(expectedRemainingGas, remainingGasAfterPayingRent);
        assertEquals(expectedPayableRent, storageRentResult.getPayableRent());
        assertEquals(expectedRollbacksRent, storageRentResult.getRollbacksRent());
        assertEquals(expectedRentedNodesCount, storageRentResult.getRentedNodes().size());
        assertEquals(expectedRollbackNodesCount, storageRentResult.getRollbackNodes().size());
        assertEquals(expectedIsOutOfGas, storageRentResult.isOutOfGas());
        assertEquals(expectedPaidRent, storageRentResult.getPaidRent());
    }
}
