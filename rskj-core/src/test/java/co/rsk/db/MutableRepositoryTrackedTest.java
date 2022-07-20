package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.trie.Trie;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrackedNode;
import org.ethereum.vm.DataWord;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ethereum.db.OperationType.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MutableRepositoryTrackedTest {

    private static final String TRANSACTION_HASH = Keccak256Helper.keccak256String("something".getBytes(StandardCharsets.UTF_8));
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

    // Testing internalGetStorageKeys calls: getStorageKeys

    @Test
    public void internalGetStorageKeys_getStorageKeys_shouldTrackNodesIfValueItsPresent() {
        // should track on nonexistent account state
        spyRepository.getStorageKeys(randomAccountAddress());

        verifyNodeTracking(1, 0, 0);

        spyRepository = newMutableRepositoryTestable();

        RskAddress accAddress1 = randomAccountAddress();

        spyRepository.createAccount(accAddress1);
        spyRepository.setupContract(accAddress1);
        spyRepository.saveCode(accAddress1, "something".getBytes(StandardCharsets.UTF_8));

        spyRepository.getStorageKeys(accAddress1);

        verifyNodeTracking(2, 3, 0);
    }

    // testing trackNode

    @Test
    public void trackNode_shouldTrackNodesWithoutDuplicates() {
        List<TrackedNode> testNodes = Arrays.asList(
                trackedNodeReadOperation("key1", false),
                trackedNodeWriteOperation("key1"),
                trackedNodeReadOperation("key1", true),
                trackedNodeReadOperation("key1", true),
                trackedNodeWriteOperation("key2"),
                trackedNodeReadOperation("key2", true),
                trackedNodeWriteOperation("key2"),
                trackedNodeReadOperation("key3", true),
                trackedNodeWriteOperation("key3"),
                trackedNodeReadOperation("key4", true),
                trackedNodeReadOperation("key4", true),
                trackedNodeReadOperation("key4", true),
                trackedNodeReadOperation("key4", true),
                trackedNodeWriteOperation("key5"),
                trackedNodeReadOperation("key6", true),
                trackedNodeWriteOperation("key6"),
                trackedNodeReadOperation("key7", false)
        );

        MutableRepositoryTestable repository = MutableRepositoryTestable
                .trackedRepository(new MutableTrieImpl(null, new Trie()));
        repository.setTrackedTransactionHash(TRANSACTION_HASH);

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

        Set<TrackedNode> trackedNodes = repository.getTrackedNodes()
                .stream()
                .filter(trackedNode -> trackedNode.getTransactionHash().equals(TRANSACTION_HASH))
                .collect(Collectors.toSet());

        // all new nodes, they should be tracked normally
        assertEquals(11, trackedNodes.size());
        assertTrue(trackedNodes.contains(trackedNodeReadOperation("key1",false)));
        assertTrue(trackedNodes.contains(trackedNodeWriteOperation("key1")));
        assertTrue(trackedNodes.contains(trackedNodeReadOperation("key1", true)));
        assertTrue(trackedNodes.contains(trackedNodeWriteOperation("key2")));
        assertTrue(trackedNodes.contains(trackedNodeReadOperation("key2", true)));
        assertTrue(trackedNodes.contains(trackedNodeWriteOperation("key2")));
        assertTrue(trackedNodes.contains(trackedNodeReadOperation("key3",true)));
        assertTrue(trackedNodes.contains(trackedNodeWriteOperation("key3")));
        assertTrue(trackedNodes.contains(trackedNodeReadOperation("key4", true)));
        assertTrue(trackedNodes.contains(trackedNodeWriteOperation("key5")));
        assertTrue(trackedNodes.contains(trackedNodeReadOperation("key6", true)));
        assertTrue(trackedNodes.contains(trackedNodeWriteOperation("key6")));
        assertTrue(trackedNodes.contains(trackedNodeReadOperation("key7", false)));
    }

    private TrackedNode trackedNodeWriteOperation(String key) {
        return trackedNode(key, WRITE_OPERATION, true);
    }

    private TrackedNode trackedNodeReadOperation(String key, boolean result) {
        return trackedNode(key, READ_OPERATION, result);
    }

    private static TrackedNode trackedNode(String key, OperationType operationType, boolean result) {
        return new TrackedNode(
                new ByteArrayWrapper(key.getBytes(StandardCharsets.UTF_8)),
                operationType,
                TRANSACTION_HASH,
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
        repositoryTestable.setTrackedTransactionHash(TRANSACTION_HASH);

        return spy(repositoryTestable);
    }
}
