package co.rsk.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.storagerent.RentedNode;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepositoryTracked;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.ethereum.db.OperationType.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MutableRepositoryTrackedTest {

    private MutableRepositoryTestable spyRepository;

    @Before
    public void setup() {
        spyRepository = newMutableRepositoryTestable();
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

        verifyNodeTracking(3, 3, 1);
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
        verify(spyRepository, times(invokedReadsContract)).trackNodeReadContractOperation(any(), anyBoolean());
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

    @Test
    public void trackNode_shouldTrackNodesWithoutDuplicates() {
        List<RentedNode> testNodes = Arrays.asList(
                trackedNodeReadOperation("key1", false),
                trackedNodeWriteOperation("key1"),
                trackedNodeReadOperation("key1", true),
                trackedNodeReadOperation("key1", true),
                trackedNodeWriteOperation("key2"),
                trackedNodeReadOperation("key2", true),
                trackedNodeReadContractCodeOperation("key2", true),
                trackedNodeWriteOperation("key2"),
                trackedNodeReadOperation("key3", true),
                trackedNodeWriteOperation("key3"),
                trackedNodeReadOperation("key4", true),
                trackedNodeReadOperation("key4", true),
                trackedNodeReadOperation("key4", true),
                trackedNodeReadContractCodeOperation("key4", true),
                trackedNodeReadOperation("key4", true),
                trackedNodeReadOperation("key4", true),
                trackedNodeWriteOperation("key5"),
                trackedNodeReadOperation("key6", true),
                trackedNodeWriteOperation("key6"),
                trackedNodeReadOperation("key7", false)
        );

        MutableRepositoryTestable repository = MutableRepositoryTestable
                .trackedRepository(new MutableTrieImpl(null, new Trie()));

        // track nodes
        testNodes.forEach(v -> {
            if(v.getOperationType().equals(READ_OPERATION)) {
                repository.trackNodeReadOperation(v.getKey().getData(), v.getNodeExistsInTrie());
            } else if(v.getOperationType().equals(READ_CONTRACT_CODE_OPERATION)) {
                repository.trackNodeReadContractOperation(v.getKey().getData(), v.getNodeExistsInTrie());
            } else if(v.getOperationType().equals(WRITE_OPERATION)) {
                repository.trackNodeWriteOperation(v.getKey().getData());
            } else {
                fail("shouldn't reach here");
            }
        });

        Map<ByteArrayWrapper, RentedNode> trackedNodes = repository.getTrackedNodes();

        // all new nodes, they should be tracked normally
        assertTrue(trackedNodes.containsKey(trackedNodeWriteOperation("key1").getKey()));
        assertTrue(trackedNodes.containsKey(trackedNodeWriteOperation("key2").getKey()));
        assertTrue(trackedNodes.containsKey(trackedNodeWriteOperation("key3").getKey()));
        assertTrue(trackedNodes.containsKey(trackedNodeReadOperation("key4", true).getKey()));
        assertTrue(trackedNodes.containsKey(trackedNodeWriteOperation("key5").getKey()));
        assertTrue(trackedNodes.containsKey(trackedNodeWriteOperation("key6").getKey()));
        assertEquals(6, trackedNodes.size());
    }

    /**
     * Track all different keys, should track them all
     * */
    @Test
    public void track_trackAllDifferentKeys() {
        Map<ByteArrayWrapper, RentedNode> trackedNodes = new HashMap<>();

        RentedNode node1 = new RentedNode(key("1"), READ_OPERATION, true);
        RentedNode node2 = new RentedNode(key("2"), READ_OPERATION, true);
        RentedNode node3 = new RentedNode(key("3"), WRITE_OPERATION, true);
        RentedNode node4 = new RentedNode(key("4"), READ_CONTRACT_CODE_OPERATION, true);

        MutableRepositoryTracked.track(node1.getKey(), node1, trackedNodes);
        MutableRepositoryTracked.track(node2.getKey(), node2, trackedNodes);
        MutableRepositoryTracked.track(node3.getKey(), node3, trackedNodes);
        MutableRepositoryTracked.track(node4.getKey(), node4, trackedNodes);

        assertEquals(4, trackedNodes.size());

        List<ByteArrayWrapper> trackedKeys = trackedNodes.values()
                .stream()
                .map(RentedNode::getKey)
                .collect(Collectors.toList());

        // all different keys
        assertEquals(new HashSet<>(trackedKeys).size(), trackedKeys.size());

        // check each key
        assertTrue(trackedNodes.containsKey(key("1")));
        assertTrue(trackedNodes.containsKey(key("2")));
        assertTrue(trackedNodes.containsKey(key("3")));
        assertTrue(trackedNodes.containsKey(key("4")));
    }

    private ByteArrayWrapper key(String key) {
        return new ByteArrayWrapper(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Track already contained keys, should track without duplicates and replace a key when it has a lower threshold.
     * */
    @Test
    public void track_duplicatedKeys() {
        Map<ByteArrayWrapper, RentedNode> trackedNodes = new HashMap<>();

        ByteArrayWrapper key = key("1");
        RentedNode thresholdHigh = new RentedNode(key, READ_OPERATION, true);
        RentedNode thresholdLow = new RentedNode(key, WRITE_OPERATION, true);

        MutableRepositoryTracked.track(key, thresholdHigh, trackedNodes);
        MutableRepositoryTracked.track(key, thresholdHigh, trackedNodes);

        // contains just one element (no duplicates)
        assertEquals(1, trackedNodes.size());
        assertEquals(thresholdHigh, trackedNodes.get(key));

        // add the same key but with a lower threshold
        MutableRepositoryTracked.track(key, thresholdLow, trackedNodes);

        // the key should be replaced with the lower threshold node
        assertEquals(1, trackedNodes.size());
        assertEquals(thresholdLow, trackedNodes.get(key));
        assertTrue(trackedNodes.get(key).rentThreshold() < thresholdHigh.rentThreshold());
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
        assertEquals(new RentedNode(address1TrieKey, WRITE_OPERATION, true),
                tempRepository.getTrackedNodes().get(address1TrieKey));
        assertEquals(new RentedNode(address2TrieKey, WRITE_OPERATION, true),
                tempRepository.getTrackedNodes().get(address2TrieKey));

        // creating two repositories to merge them and check tracked nodes
        MutableRepositoryTracked repository = MutableRepositoryTracked.trackedRepository(mutableTrieCache);

        // read address1/2TrieKey (two READ_OPERATION)
        repository.getBalance(address1);
        repository.getBalance(address2);

        assertEquals(2, repository.getTrackedNodes().size());
        assertEquals(new RentedNode(address1TrieKey, READ_OPERATION, true),
                repository.getTrackedNodes().get(address1TrieKey));
        assertEquals(new RentedNode(address2TrieKey, READ_OPERATION, true),
                repository.getTrackedNodes().get(address2TrieKey));

        // creating a child repository to perform a WRITE_OPERATION for address1
        MutableRepositoryTracked childRepository = (MutableRepositoryTracked) repository.startTracking();

        childRepository.addBalance(address1, Coin.valueOf(10));

        assertEquals(1, childRepository.getTrackedNodes().size());
        assertEquals(new RentedNode(address1TrieKey, WRITE_OPERATION, true),
                childRepository.getTrackedNodes().get(address1TrieKey));

        // merging both repositories
        childRepository.commit();

        // now there are two nodes, but one has been replaced with the WRITE_OPERATION (which has the lowest threshold)
        assertEquals(2, repository.getTrackedNodes().size());
        assertEquals(new RentedNode(address1TrieKey, WRITE_OPERATION, true),
                repository.getTrackedNodes().get(address1TrieKey));
        assertEquals(new RentedNode(address2TrieKey, READ_OPERATION, true),
                repository.getTrackedNodes().get(address2TrieKey));
    }

    private RentedNode trackedNodeWriteOperation(String key) {
        return trackedNode(key, WRITE_OPERATION, true);
    }

    private RentedNode trackedNodeReadOperation(String key, boolean result) {
        return trackedNode(key, READ_OPERATION, result);
    }

    private RentedNode trackedNodeReadContractCodeOperation(String key, boolean result) {
        return trackedNode(key, READ_CONTRACT_CODE_OPERATION, result);
    }

    private static RentedNode trackedNode(String key, OperationType operationType, boolean result) {
        return new RentedNode(
                new ByteArrayWrapper(key.getBytes(StandardCharsets.UTF_8)),
                operationType,
                result
        );
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
