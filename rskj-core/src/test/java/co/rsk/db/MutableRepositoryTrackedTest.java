package co.rsk.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.storagerent.RentedNode;
import co.rsk.storagerent.StorageRentManager;
import co.rsk.storagerent.StorageRentResult;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.AccountState;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepositoryTracked;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static co.rsk.storagerent.StorageRentUtil.rentThreshold;
import static org.ethereum.db.OperationType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MutableRepositoryTrackedTest {

    private MutableRepositoryTestable spyRepository;
    private StorageRentManager storageRentManager;

    @BeforeEach
    public void setup() {
        this.spyRepository = newMutableRepositoryTestable();
        this.storageRentManager = new StorageRentManager();
    }

    // Testing internalGet calls: getCode, isContract, getStorageBytes, getStorageValue, getAccountState

    @Test
    public void internalGet_getCode_shouldTriggerNodeTrackingIfValueItsPresent() {
        RskAddress accAddress1 = randomAccountAddress();

        // a nonexistent account
        spyRepository.getCode(randomAccountAddress());

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        spyRepository.createAccount(accAddress1);
        spyRepository.setupContract(accAddress1);
        spyRepository.saveCode(accAddress1, "someCode".getBytes(StandardCharsets.UTF_8));

        spyRepository.getCode(accAddress1);

        verifyNodeTracking(4, 3, 1);
    }

    @Test
    public void internalGet_isContract_shouldTriggerNodeTrackingIfValueItsPresent() {
        // a nonexistent account
        spyRepository.isContract(randomAccountAddress());

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        RskAddress accAddress1 = randomAccountAddress();

        spyRepository.createAccount(accAddress1);
        spyRepository.setupContract(accAddress1);
        spyRepository.saveCode(accAddress1, "someCode".getBytes(StandardCharsets.UTF_8));

        spyRepository.isContract(accAddress1);

        verifyNodeTracking(2, 3, 0);
    }

    @Test
    public void internalGet_getStorageBytes_shouldTriggerNodeTrackingIfValueItsPresent() {
        RskAddress accAddress1 = randomAccountAddress();

        // should track a nonexistent storage key
        spyRepository.getStorageBytes(accAddress1, DataWord.ZERO);

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        // should track a nonexistent account
        spyRepository.getStorageBytes(randomAccountAddress(), DataWord.ONE);

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        spyRepository.createAccount(accAddress1);
        spyRepository.setupContract(accAddress1);
        spyRepository.saveCode(accAddress1, "someCode".getBytes(StandardCharsets.UTF_8));
        spyRepository.addStorageBytes(accAddress1, DataWord.ONE,
                "something".getBytes(StandardCharsets.UTF_8));

        spyRepository.getStorageBytes(accAddress1, DataWord.ONE);

        verifyNodeTracking(3, 4, 0);
    }

    @Test
    public void internalGet_getStorageValue_shouldTriggerNodeTrackingIfValueItsPresent() {
        RskAddress accAddress1 = randomAccountAddress();

        // nonexistent storage key
        spyRepository.getStorageValue(accAddress1, DataWord.valueOf(2));

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        // nonexistent account
        spyRepository.getStorageValue(randomAccountAddress(), DataWord.ONE);

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        // existent account & storage key
        spyRepository.createAccount(accAddress1);
        spyRepository.setupContract(accAddress1);
        spyRepository.saveCode(accAddress1, "someCode".getBytes(StandardCharsets.UTF_8));
        spyRepository.addStorageBytes(accAddress1, DataWord.ONE,
                "something".getBytes(StandardCharsets.UTF_8));

        spyRepository.getStorageValue(accAddress1, DataWord.ONE);

        verifyNodeTracking(3, 4, 0);
    }

    @Test
    public void internalGet_getAccountState_shouldTriggerNodeTrackingIfValueItsPresent() {
        // should track a nonexistent account state
        spyRepository.getAccountState(randomAccountAddress());

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        RskAddress accAddress1 = randomAccountAddress();

        spyRepository.createAccount(accAddress1);
        spyRepository.setupContract(accAddress1);
        spyRepository.saveCode(accAddress1, "someCode".getBytes(StandardCharsets.UTF_8));
        spyRepository.addStorageBytes(accAddress1, DataWord.ONE,
                "something".getBytes(StandardCharsets.UTF_8));

        spyRepository.getAccountState(accAddress1);

        verifyNodeTracking(3, 4, 0);
    }

    // Testing internalPut calls: setupContract, saveCode, addStorageBytes, updateAccountState

    @Test
    public void internalPut_setupContract_shouldTrackNodesIfValueItsPresent() {
        spyRepository.setupContract(randomAccountAddress());

        verifyNodeTracking(0, 1, 0);
    }

    @Test
    public void internalPut_saveCode_shouldTrackNodesIfValueItsPresent() {
        spyRepository.saveCode(randomAccountAddress(), "something".getBytes(StandardCharsets.UTF_8));

        verifyNodeTracking(1, 2, 0);
    }

    @Test
    public void internalPut_addStorageBytes_shouldTrackNodesIfValueItsPresent() {
        RskAddress accAddress1 = randomAccountAddress();

        spyRepository.createAccount(accAddress1);

        spyRepository.addStorageBytes(accAddress1, DataWord.ONE, "something".getBytes(StandardCharsets.UTF_8));

        verifyNodeTracking(1, 2, 0);
    }

    @Test
    public void internalPut_updateAccountState_shouldTrackNodesIfValueItsPresent() {
        RskAddress accAddress1 = randomAccountAddress();

        spyRepository.createAccount(accAddress1);

        spyRepository.updateAccountState(accAddress1, new AccountState());

        verifyNodeTracking(0, 2, 0);
    }

    // Testing internalGetValueHash calls: getCodeHashStandard, getCodeHashNonStandard

    @Test
    public void internalGetValueHash_getCodeHashStandard_shouldTrackNodesIfValueItsPresent() {
        // track a nonexistent account state
        spyRepository.getCodeHashStandard(randomAccountAddress());

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        RskAddress accAddress1 = randomAccountAddress();

        spyRepository.createAccount(accAddress1);

        spyRepository.getCodeHashStandard(accAddress1);

        verifyNodeTracking(2, 1, 0);
    }

    @Test
    public void internalGetValueHash_getCodeHashNonStandard_shouldTrackNodesIfValueItsPresent() {
        // a nonexistent account state
        spyRepository.getCodeHashNonStandard(randomAccountAddress());

        // READ: isExist (MutableRepository:210), WRITE: 0
        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        // an existent account
        RskAddress accAddress1 = randomAccountAddress();

        spyRepository.createAccount(accAddress1);

        spyRepository.getCodeHashNonStandard(accAddress1);

        verifyNodeTracking(2, 1, 0);
    }

    private void verifyNodeTracking(int invokedReads, int invokedWrites, int invokedReadsContract) {
        verify(spyRepository, times(invokedReads)).trackNodeReadOperation(any(), anyBoolean());
        verify(spyRepository, times(invokedWrites)).trackNodeWriteOperation(any());
    }

    // Testing internalGetValueLength calls: isExist, getCodeLength

    @Test
    public void internalGetValueLength_isExist_shouldTrackNodesIfValueItsPresent() {
        // should track a nonexistent account state
        spyRepository.isExist(randomAccountAddress());

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        RskAddress accAddress1 = randomAccountAddress();

        spyRepository.createAccount(accAddress1);

        spyRepository.isExist(accAddress1);

        // one at createAccount(), the other one at isExist()
        verifyNodeTracking(1, 1, 0);
    }

    @Test
    public void internalGetValueLength_getCodeLength_shouldTrackNodesIfValueItsPresent() {
        // should track a nonexistent account state
        spyRepository.getCodeLength(randomAccountAddress());

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        RskAddress accAddress1 = randomAccountAddress();

        spyRepository.createAccount(accAddress1);
        spyRepository.setupContract(accAddress1);
        spyRepository.saveCode(accAddress1, "something".getBytes(StandardCharsets.UTF_8));

        spyRepository.getCodeLength(accAddress1);

        verifyNodeTracking(3, 3, 0);

        spyRepository = newMutableRepositoryTestable();

        RskAddress accAddress2 = randomAccountAddress();
        spyRepository.createAccount(accAddress2);

        spyRepository.getCodeLength(accAddress2);

        // because it's an address that doesn't have any code
        verifyNodeTracking(2, 1, 0);
    }

    // testing trackNode

    /**
     * Track all different keys, should track them all
     * */
    @Test
    public void track_trackAllDifferentKeys() {
        Map<ByteArrayWrapper, OperationType> trackedNodes = new HashMap<>();

        MutableRepositoryTracked.track(key("1"), READ_OPERATION, trackedNodes);
        MutableRepositoryTracked.track(key("2"), READ_OPERATION, trackedNodes);
        MutableRepositoryTracked.track(key("3"), WRITE_OPERATION, trackedNodes);
        MutableRepositoryTracked.track(key("4"), READ_OPERATION, trackedNodes);
        MutableRepositoryTracked.track(key("5"), DELETE_OPERATION, trackedNodes);
        MutableRepositoryTracked.track(key("6"), WRITE_OPERATION, trackedNodes);
        MutableRepositoryTracked.track(key("7"), DELETE_OPERATION, trackedNodes);

        assertEquals(7, trackedNodes.size());

        Set<ByteArrayWrapper> trackedKeys = trackedNodes.keySet();

        // all different keys
        assertEquals(new HashSet<>(trackedKeys).size(), trackedKeys.size());

        // check each key
        assertTrue(trackedNodes.containsKey(key("1")));
        assertTrue(trackedNodes.containsKey(key("2")));
        assertTrue(trackedNodes.containsKey(key("3")));
        assertTrue(trackedNodes.containsKey(key("4")));
        assertTrue(trackedNodes.containsKey(key("5")));
        assertTrue(trackedNodes.containsKey(key("6")));
        assertTrue(trackedNodes.containsKey(key("7")));
    }

    private ByteArrayWrapper key(String key) {
        return new ByteArrayWrapper(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Track already contained keys, should track without duplicates and replace a key when it has a lower threshold.
     * */
    @Test
    public void track_duplicatedKeys() {
        Map<ByteArrayWrapper, OperationType> trackedNodes = new HashMap<>();

        ByteArrayWrapper key = key("1");

        MutableRepositoryTracked.track(key, READ_OPERATION, trackedNodes);
        MutableRepositoryTracked.track(key, READ_OPERATION, trackedNodes);

        // contains just one element (no duplicates)
        assertEquals(1, trackedNodes.size());
        assertEquals(READ_OPERATION, trackedNodes.get(key));

        // add the same key but with a lower threshold
        MutableRepositoryTracked.track(key, WRITE_OPERATION, trackedNodes);

        // the key should've been replaced by the node with the lowest threshold
        assertEquals(1, trackedNodes.size());
        assertEquals(WRITE_OPERATION, trackedNodes.get(key));
        assertTrue(rentThreshold(WRITE_OPERATION) < rentThreshold(READ_OPERATION));
    }

    /**
     * Merge two repositories with duplicated tracked keys, should end without duplicates.
     * */
    @Test
    public void mergeRepositoriesWithDuplicates() {
        TrieKeyMapper keyMapper = new TrieKeyMapper();
        MutableTrieCache mutableTrieCache = new MutableTrieCache(
                new MutableTrieImpl(new TrieStoreImpl(new HashMapDB())));

        // a temporary repository to setup the trie store
        MutableRepositoryTracked tempRepository = MutableRepositoryTracked.trackedRepository(mutableTrieCache);

        RskAddress address1 = new RskAddress("3e1127bf1a673d378a8570f7a79cea4f10e20489");
        ByteArrayWrapper address1TrieKey = new ByteArrayWrapper(keyMapper.getAccountKey(address1)); // 20489
        RskAddress address2 = new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87");
        ByteArrayWrapper address2TrieKey = new ByteArrayWrapper(keyMapper.getAccountKey(address2)); // 52d87

        tempRepository.addBalance(address1, Coin.valueOf(10));
        tempRepository.addBalance(address2, Coin.valueOf(10));

        assertEquals(2, tempRepository.getTrackedNodes().size());
        assertEquals(WRITE_OPERATION, tempRepository.getTrackedNodes().get(address1TrieKey));
        assertEquals(WRITE_OPERATION, tempRepository.getTrackedNodes().get(address2TrieKey));

        // creating two repositories to merge them and check tracked nodes
        MutableRepositoryTracked repository = MutableRepositoryTracked.trackedRepository(mutableTrieCache);

        // read address1/2TrieKey (two READ_OPERATION)
        repository.getBalance(address1);
        repository.getBalance(address2);

        assertEquals(2, repository.getTrackedNodes().size());
        assertEquals(READ_OPERATION, repository.getTrackedNodes().get(address1TrieKey));
        assertEquals(READ_OPERATION, repository.getTrackedNodes().get(address2TrieKey));

        // creating a child repository to perform a WRITE_OPERATION for address1
        MutableRepositoryTracked childRepository = (MutableRepositoryTracked) repository.startTracking();

        childRepository.addBalance(address1, Coin.valueOf(10));

        assertEquals(1, childRepository.getTrackedNodes().size());
        assertEquals(WRITE_OPERATION, childRepository.getTrackedNodes().get(address1TrieKey));

        // merging both repositories
        childRepository.commit();

        // now there are two nodes, but one has been replaced with the WRITE_OPERATION (which has the lowest threshold)
        assertEquals(2, repository.getTrackedNodes().size());
        assertEquals(WRITE_OPERATION, repository.getTrackedNodes().get(address1TrieKey));
        assertEquals(READ_OPERATION, repository.getTrackedNodes().get(address2TrieKey));
    }

    /**
     * Timestamp a trie and then read the same keys.
     * They should contain the given timestamp
     * */
    @Test
    public void readDataFromAnAlreadyTimestampedTrie() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        RskAddress anAddress = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");
        long firstBlockTimestamp = 7;

        // init a new state
        MutableRepositoryTracked initialRepository = repositoryTracked(trieStore, null);

        initialRepository.addBalance(anAddress, Coin.valueOf(10));

        // timestamping the trie
        MutableRepositoryTracked repositoryWithTimestamps = (MutableRepositoryTracked) initialRepository.startTracking();
        storageRentManager.pay(100000, firstBlockTimestamp,
                initialRepository, repositoryWithTimestamps);

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
        long updatedTimesteamp = 50000000000l;
        StorageRentResult result = storageRentManager.pay(100000, updatedTimesteamp,
                blockTrack, transactionTrack);

        RentedNode nodeAfterPayment = new RentedNode(key, READ_OPERATION, 3, updatedTimesteamp);

        transactionTrack.commit();

        assertTrue(result.getPaidRent() > 0);
        assertEquals(1, result.getRollbackNodes().size());
        assertEquals(result.getRentedNodes(), result.getRollbackNodes());
        assertEquals(nodeAfterPayment, transactionTrack.fetchRentedNode(key, READ_OPERATION));
    }

    private static MutableRepositoryTracked repositoryTracked(TrieStore trieStore, byte[] root) {
        return MutableRepositoryTracked.trackedRepository(
                new MutableTrieCache(new MutableTrieImpl(trieStore, root == null ? new Trie(trieStore) :
                        trieStore.retrieve(root).get())));
    }

    private static RskAddress randomAccountAddress() {
        byte[] bytes = new byte[20];

        new Random().nextBytes(bytes);

        return new RskAddress(bytes);
    }

    private MutableRepositoryTestable newMutableRepositoryTestable() {
        MutableTrieCache mutableTrie = new MutableTrieCache(new MutableTrieImpl(null, new Trie()));
        MutableRepositoryTestable repositoryTestable = MutableRepositoryTestable.trackedRepository(mutableTrie);

        return spy(repositoryTestable);
    }
}
