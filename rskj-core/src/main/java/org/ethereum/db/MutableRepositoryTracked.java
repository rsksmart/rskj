package org.ethereum.db;

import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.storagerent.RentedNode;
import co.rsk.trie.MutableTrie;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import java.util.*;
import java.util.stream.Collectors;

import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;
import static org.ethereum.db.OperationType.*;
import static org.ethereum.db.OperationType.READ_CONTRACT_CODE_OPERATION;

public class MutableRepositoryTracked extends MutableRepository {

    // todo(fedejinich) ALL THIS MEMBERS MIGHT BE MOVED TO MutableRepositoryTracked
    // enables node tracking feature
    private final boolean enableTracking;
    // a set to track all the used trie-value-containing nodes in this repository (and its children repositories)
    protected final Set<TrackedNode> trackedNodes;
    // a list of nodes tracked nodes that were rolled back (due to revert or OOG)
    protected final List<TrackedNode> rollbackNodes;
    // parent repository to commit tracked nodes
    protected final MutableRepositoryTracked parentRepository;
    // this contains the hash of the ongoing tracked transaction
    protected String trackedTransactionHash = "NO_TRANSACTION_HASH";

    private MutableRepositoryTracked(MutableTrie mutableTrie, MutableRepositoryTracked parentRepository,
                                       Set<TrackedNode> trackedNodes, List<TrackedNode> rollbackNodes,
                                       boolean enableTracking) {
        super(mutableTrie);
        this.parentRepository = parentRepository;
        this.trackedNodes = trackedNodes;
        this.rollbackNodes = rollbackNodes;
        this.enableTracking = enableTracking;
    }

    // useful for key tracking, it connects between repositories. the root Repository will contain a null parentRepository
    protected MutableRepositoryTracked(MutableTrie mutableTrie, MutableRepositoryTracked parentRepository, boolean enableTracking) {
        this(mutableTrie, parentRepository, new HashSet<>(), new ArrayList<>(), enableTracking);
    }

    // creates a tracked repository, all the child repositories (created with startTracking()) will also be tracked.
    // this should be only called from RepositoryLocator.trackedRepository
    public static MutableRepositoryTracked trackedRepository(MutableTrie mutableTrieCache) {
        return new MutableRepositoryTracked(mutableTrieCache, null, true);
    }

    @Override
    public synchronized Repository startTracking() {
        MutableRepositoryTracked mutableRepositoryTracked = new MutableRepositoryTracked(new MutableTrieCache(this.mutableTrie), this, this.enableTracking);
        mutableRepositoryTracked.setTrackedTransactionHash(trackedTransactionHash); // todo(fedejinich) this will be moved to MutableRepositoryTracked

        return mutableRepositoryTracked;
    }

    @Override
    public synchronized void commit() {
        super.commit();

        if(this.parentRepository != null) {
            this.parentRepository.mergeTrackedNodes(this.trackedNodes);
            this.parentRepository.addRollbackNodes(this.rollbackNodes);
        }
    }

    @Override
    public synchronized void rollback() {
        super.rollback();

        if(parentRepository != null) {
            this.parentRepository.addRollbackNodes(this.trackedNodes);
            this.trackedNodes.clear();
            this.rollbackNodes.clear();
        }
    }

    @VisibleForTesting
    public Set<TrackedNode> getTrackedNodes() {
        return this.trackedNodes;
    }

    public List<TrackedNode> getRollBackNodes(String transactionHash) {
        return this.rollbackNodes.stream()
                .filter(trackedNode -> trackedNode.useForStorageRent(transactionHash))
                .collect(Collectors.toList());
    }

    public RentedNode getRentedNode(TrackedNode trackedNode) {
        byte[] key = trackedNode.getKey().getData();

        // if we reach here, it will always get timestamp/valueLength from an existing key

        Long nodeSize = Long.valueOf(this.mutableTrie.getValueLength(key).intValue());
        Optional<Long> rentTimestamp = this.mutableTrie.getRentTimestamp(key);
        long lastRentPaidTimestamp = rentTimestamp.isPresent() ? rentTimestamp.get() : NO_RENT_TIMESTAMP;

        RentedNode rentedNode = new RentedNode(trackedNode, nodeSize, lastRentPaidTimestamp);

        return rentedNode;
    }

    public void updateRents(Set<RentedNode> rentedNodes, long executionBlockTimestamp) {
        rentedNodes.forEach(node -> {
            long updatedRentTimestamp = node.getUpdatedRentTimestamp(executionBlockTimestamp);

            this.mutableTrie.putRentTimestamp(node.getKey().getData(), updatedRentTimestamp);
        });
    }

    public void setTrackedTransactionHash(String trackedTransactionHash) {
        this.trackedTransactionHash = trackedTransactionHash;
    }

    // Internal methods contains node tracking

    protected void internalPut(byte[] key, byte[] value) {
        super.internalPut(key, value);
        if(value == null) {
            trackNodeDeleteOperation(key);
        } else {
            trackNodeWriteOperation(key);
        }
    }

    protected void internalDeleteRecursive(byte[] key) {
        // todo(fedejinich) what happens for non existing keys? should track with false result?
        super.internalDeleteRecursive(key);
        trackNodeDeleteOperation(key);
    }

    protected byte[] internalGet(byte[] key, boolean readsContractCode) {
        byte[] value = super.internalGet(key, readsContractCode);
        boolean isSuccessful = value != null;

        if(readsContractCode) {
            trackNodeReadContractOperation(key, isSuccessful);
        } else {
            // todo(fedejinich) should track get() success with a bool (value != null)
            trackNodeReadOperation(key, isSuccessful);
        }

        return value;
    }

    protected Optional<Keccak256> internalGetValueHash(byte[] key) {
        Optional<Keccak256> valueHash = super.internalGetValueHash(key);

        trackNodeReadOperation(key, valueHash.isPresent());

        return valueHash;
    }

    protected Uint24 internalGetValueLength(byte[] key) {
        Uint24 valueLength = super.internalGetValueLength(key);

        trackNodeReadOperation(key, valueLength != Uint24.ZERO);

        return valueLength;
    }

    protected Iterator<DataWord> internalGetStorageKeys(RskAddress addr) {
        Iterator<DataWord> storageKeys = super.internalGetStorageKeys(addr);

        // todo(fedejinich) how should I track the right key/s?
        boolean result = !storageKeys.equals(Collections.emptyIterator());
        byte[] accountStoragePrefixKey = trieKeyMapper.getAccountStoragePrefixKey(addr);

        trackNodeReadOperation(accountStoragePrefixKey, result);

        return storageKeys;
    }

    protected void trackNodeWriteOperation(byte[] key) {
        trackNode(key, WRITE_OPERATION, true);
    }

    protected void trackNodeDeleteOperation(byte[] key) {
        trackNode(key, DELETE_OPERATION, true);
    }

    protected void trackNodeReadOperation(byte[] key, boolean result) {
        trackNode(key, READ_OPERATION, result);
    }

    protected void trackNodeReadContractOperation(byte[] key, boolean result) {
        trackNode(key, READ_CONTRACT_CODE_OPERATION, result);
    }

    protected void trackNode(byte[] key, OperationType operationType, boolean isSuccessful) {
        if(this.enableTracking) {
            TrackedNode trackedNode = new TrackedNode(
                    new ByteArrayWrapper(key),
                    operationType,
                    this.trackedTransactionHash,
                    isSuccessful
            );
            this.trackedNodes.add(trackedNode);
        }
    }

    private void mergeTrackedNodes(Set<TrackedNode> trackedNodes) {
        this.trackedNodes.addAll(trackedNodes);
    }

    private void addRollbackNodes(Collection<TrackedNode> trackedNodes) {
        this.rollbackNodes.addAll(trackedNodes);
    }

    public Set<TrackedNode> getStorageRentNodes(String transactionHash) {
        Map<ByteArrayWrapper, TrackedNode> storageRentNodes = new HashMap<>();
        this.trackedNodes.stream()
                .filter(trackedNode -> trackedNode.useForStorageRent(transactionHash))
                .forEach(trackedNode -> {
                    ByteArrayWrapper key = new ByteArrayWrapper(trackedNode.getKey().getData());
                    TrackedNode containedNode = storageRentNodes.get(key);

                    boolean isContainedNode = containedNode != null;
                    if(isContainedNode) {
                        long notRelevant = -1;
                        RentedNode nodeToBeReplaced = new RentedNode(containedNode, notRelevant, notRelevant);
                        RentedNode newNode = new RentedNode(trackedNode, notRelevant, notRelevant);
                        if(nodeToBeReplaced.shouldBeReplaced(newNode)) {
                            // we pass the TrackedNode instance because we don't need a populated RentedNode yet
                            storageRentNodes.put(key, trackedNode);
                        }
                    } else {
                        storageRentNodes.put(key, trackedNode);
                    }
                });

        return new HashSet<>(storageRentNodes.values());
    }
}
