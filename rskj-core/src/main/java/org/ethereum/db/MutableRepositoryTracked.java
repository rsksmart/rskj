package org.ethereum.db;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.storagerent.RentedNode;
import co.rsk.trie.MutableTrie;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;

import java.util.*;

import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;
import static org.ethereum.db.OperationType.*;

/**
 * A `Repository` to track all the used trie nodes.
 * */
public class MutableRepositoryTracked extends MutableRepository {

    // used trie nodes in this repository (and its sub-repositories)
    private Map<ByteArrayWrapper, RentedNode> trackedNodes;
    // nodes that have been part of a rolled back repository
    private Map<ByteArrayWrapper, RentedNode> rollbackNodes;
    // parent repository to commit tracked nodes
    private MutableRepositoryTracked parentRepository;

    // default constructor
    protected MutableRepositoryTracked(MutableTrie mutableTrie, MutableRepositoryTracked parentRepository,
                                       Map<ByteArrayWrapper, RentedNode> trackedNodes, Map<ByteArrayWrapper, RentedNode> rollbackNodes){
        super(mutableTrie);
        this.parentRepository = parentRepository;
        this.trackedNodes = trackedNodes;
        this.rollbackNodes = rollbackNodes;
    }

    // creates a tracked repository, all the child repositories (created with startTracking()) will also be tracked.
    // this should be only called from RepositoryLocator.trackedRepository
    public static MutableRepositoryTracked trackedRepository(MutableTrie mutableTrieCache) {
        return new MutableRepositoryTracked(mutableTrieCache, null, new HashMap<>(), new HashMap<>());
    }

    @Override
    public synchronized Repository startTracking() {
        MutableRepositoryTracked mutableRepositoryTracked = new MutableRepositoryTracked(
            new MutableTrieCache(this.mutableTrie),
            this,
            new HashMap<>(),
            new HashMap<>()
        );

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
    public Map<ByteArrayWrapper, RentedNode> getTrackedNodes() {
        return this.trackedNodes;
    }

    public Map<ByteArrayWrapper, RentedNode> getRollBackNodes() {
        return this.rollbackNodes;
    }

    /**
     * Fills up an already tracked node by providing the rent timestamp and the node size.
     *
     * @param trackedNode an already tracked node, this node doens't contains the relevant data to collect rent
     * @return a new fulfilled RentedNode ready to use for storage rent payment
     * */
    public RentedNode fillUpRentedNode(RentedNode trackedNode) {
        byte[] key = trackedNode.getKey().getData();

        // if we reach here, it will always get timestamp/valueLength from an existing key

        Long nodeSize = Long.valueOf(this.mutableTrie.getValueLength(key).intValue());
        Optional<Long> rentTimestamp = this.mutableTrie.getRentTimestamp(key);
        long lastRentPaidTimestamp = rentTimestamp.isPresent() ? rentTimestamp.get() : NO_RENT_TIMESTAMP;

        RentedNode rentedNode = new RentedNode(trackedNode.getKey(), trackedNode.getOperationType(),
                trackedNode.getNodeExistsInTrie(), nodeSize, lastRentPaidTimestamp);

        return rentedNode;
    }

    public void updateRents(Set<RentedNode> rentedNodes, long executionBlockTimestamp) {
        rentedNodes.forEach(node -> {
            long updatedRentTimestamp = node.getUpdatedRentTimestamp(executionBlockTimestamp);

            this.mutableTrie.putRentTimestamp(node.getKey().getData(), updatedRentTimestamp);
        });
    }

    public Map<ByteArrayWrapper, RentedNode> getStorageRentNodes() {
        return this.trackedNodes;
    }

    // Internal methods contains node tracking

    @Override
    protected void internalPut(byte[] key, byte[] value) {
        super.internalPut(key, value);
        if(value == null) {
            trackNodeDeleteOperation(key);
        } else {
            trackNodeWriteOperation(key);
        }
    }

    @Override
    protected void internalDeleteRecursive(byte[] key) {
        super.internalDeleteRecursive(key);
        trackNodeDeleteOperation(key);
    }

    @Override
    protected byte[] internalGet(byte[] key, boolean readsContractCode) {
        byte[] value = super.internalGet(key, readsContractCode);
        boolean nodeExistsInTrie = value != null;

        if(readsContractCode) {
            trackNodeReadContractOperation(key, nodeExistsInTrie);
        } else {
            trackNodeReadOperation(key, nodeExistsInTrie);
        }

        return value;
    }

    @Override
    protected Optional<Keccak256> internalGetValueHash(byte[] key) {
        Optional<Keccak256> valueHash = super.internalGetValueHash(key);

        trackNodeReadOperation(key, valueHash.isPresent());

        return valueHash;
    }

    @Override
    protected Uint24 internalGetValueLength(byte[] key) {
        Uint24 valueLength = super.internalGetValueLength(key);

        trackNodeReadOperation(key, valueLength != Uint24.ZERO);

        return valueLength;
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

    protected void trackNodeReadContractOperation(byte[] key, boolean nodeExistsInTrie) {
        trackNode(key, READ_CONTRACT_CODE_OPERATION, nodeExistsInTrie);
    }

    protected void trackNode(byte[] rawKeyToTrack, OperationType operationType, boolean nodeExistsInTrie) {
        ByteArrayWrapper keyToTrack = new ByteArrayWrapper(rawKeyToTrack);
        RentedNode nodeToTrack = new RentedNode(keyToTrack, operationType, nodeExistsInTrie);

        track(keyToTrack, nodeToTrack, this.trackedNodes);
    }

    // todo(fedejinich) refactor needed: remove unnecessary key, should only accept (RentedNode, Map)
    public static void track(ByteArrayWrapper keyToTrack, RentedNode nodeToTrack,
                             Map<ByteArrayWrapper, RentedNode> trackedNodesMap) {
        if(!nodeToTrack.useForStorageRent()) {
            return;
        }

        RentedNode alreadyContainedNode = trackedNodesMap.get(keyToTrack);

        if(alreadyContainedNode == null) {
            trackedNodesMap.put(keyToTrack, nodeToTrack);
        } else {
            // track nodes with the lowest threshold
            if(nodeToTrack.rentThreshold() < alreadyContainedNode.rentThreshold()) {
                trackedNodesMap.put(keyToTrack, nodeToTrack);
            }
        }
    }

    private void mergeTrackedNodes(Map<ByteArrayWrapper, RentedNode> trackedNodes) {
        // tracked nodes should ONLY be added by the trackNode() method
        trackedNodes.values().forEach(t -> trackNode(t.getKey().getData(), t.getOperationType(), t.getNodeExistsInTrie()));
    }

    private void addRollbackNodes(Map<ByteArrayWrapper, RentedNode> rollbackNodes) {
        rollbackNodes.values().forEach(t -> track(t.getKey(), t, this.rollbackNodes));
    }
    public void clearTrackedNodes() {
        this.trackedNodes = new HashMap<>();
        this.rollbackNodes = new HashMap<>();
    }
}
