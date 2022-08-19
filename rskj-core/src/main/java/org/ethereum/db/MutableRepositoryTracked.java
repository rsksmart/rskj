package org.ethereum.db;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.storagerent.RentedNode;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;

import java.util.*;

import static co.rsk.storagerent.RentedNode.rentThreshold;
import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;
import static org.ethereum.db.OperationType.*;

/**
 * A `Repository` to track all the used trie nodes.
 * */
public class MutableRepositoryTracked extends MutableRepository {

    // used trie nodes in this repository (and its sub-repositories)
    private Map<ByteArrayWrapper, OperationType> trackedNodes;
    // nodes that have been part of a rolled back repository
    private Map<ByteArrayWrapper, OperationType> rollbackNodes;
    // parent repository to commit tracked nodes
    private MutableRepositoryTracked parentRepository;

    // default constructor
    protected MutableRepositoryTracked(MutableTrie mutableTrie, MutableRepositoryTracked parentRepository,
                                       Map<ByteArrayWrapper, OperationType> trackedNodes, Map<ByteArrayWrapper, OperationType> rollbackNodes){
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
    public Map<ByteArrayWrapper, OperationType> getTrackedNodes() {
        return this.trackedNodes;
    }

    public Map<ByteArrayWrapper, OperationType> getRollBackNodes() {
        return this.rollbackNodes;
    }

    /**
     * Creates RentedNode object by retrieving timestamp and nodeSize from the trie.
     * */
    public RentedNode fetchRentedNode(ByteArrayWrapper key, OperationType operationType) {
        byte[] rawKey = key.getData();

        // if we reach here, it will always get timestamp/valueLength from an existing key

        Long nodeSize = Long.valueOf(this.mutableTrie.getValueLength(rawKey).intValue());
        Optional<Long> rentTimestamp = this.mutableTrie.getRentTimestamp(rawKey);
        long lastRentPaidTimestamp = rentTimestamp.isPresent() ? rentTimestamp.get() : NO_RENT_TIMESTAMP;

        // todo(fedejinich) remove nodeExistInTrie, it's always true! (nonexisting nodes are excluded at trackNode())
        RentedNode rentedNode = new RentedNode(key, operationType, nodeSize, lastRentPaidTimestamp);

        return rentedNode;
    }

    public void updateRents(Set<RentedNode> rentedNodes, long executionBlockTimestamp) {
        rentedNodes.forEach(node -> {
            long updatedRentTimestamp = node.getUpdatedRentTimestamp(executionBlockTimestamp);

            // only updates to bigger timestamps
            if(updatedRentTimestamp > node.getRentTimestamp()) {
                this.mutableTrie.putRentTimestamp(node.getKey().getData(), updatedRentTimestamp);
            }
        });
    }

    public Map<ByteArrayWrapper, OperationType> getStorageRentNodes() {
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

    public long getValueLength(byte[] key) {
        return this.mutableTrie.getValueLength(key).intValue();
    }

    @Override
    protected void internalDeleteRecursive(byte[] key) {
        super.internalDeleteRecursive(key);
        trackNodeDeleteOperation(key);
    }

    @Override
    protected byte[] internalGet(byte[] key) {
        byte[] value = super.internalGet(key);

        trackNodeReadOperation(key, value != null);

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
        trackNode(key,  DELETE_OPERATION, true);
    }

    protected void trackNodeReadOperation(byte[] key, boolean result) {
        trackNode(key, READ_OPERATION, result);
    }

    protected void trackNode(byte[] rawKeyToTrack, OperationType operationType, boolean nodeExistsInTrie) {
        if(!nodeExistsInTrie) {
            return;
        }

        track(new ByteArrayWrapper(rawKeyToTrack), operationType, this.trackedNodes);
    }

    public static void track(ByteArrayWrapper keyToTrack, OperationType operationTypeToTrack, Map<ByteArrayWrapper, OperationType> trackedNodesMap) {
        OperationType alreadyContainedOperationType = trackedNodesMap.get(keyToTrack);

        if(alreadyContainedOperationType == null) {
            trackedNodesMap.put(keyToTrack, operationTypeToTrack);
        } else {
            // track nodes with the lowest threshold
            if(rentThreshold(operationTypeToTrack) < rentThreshold(alreadyContainedOperationType)) {
                trackedNodesMap.put(keyToTrack, operationTypeToTrack);
            }
        }
    }

    private void mergeTrackedNodes(Map<ByteArrayWrapper, OperationType> trackedNodes) {
        // tracked nodes should ONLY be added by the trackNode() method
        trackedNodes.forEach((key, operationType) -> track(key, operationType, this.trackedNodes));
    }

    private void addRollbackNodes(Map<ByteArrayWrapper, OperationType> rollbackNodes) {
        rollbackNodes.forEach((key, operationType) -> track(key, operationType, this.rollbackNodes));
    }
    public void clearTrackedNodes() {
        this.trackedNodes = new HashMap<>();
        this.rollbackNodes = new HashMap<>();
    }
}