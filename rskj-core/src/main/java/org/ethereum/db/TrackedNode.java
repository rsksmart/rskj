package org.ethereum.db;

import static org.ethereum.db.OperationType.*;

/**
 * A presentational class, used (by MutableRepository) to track relevant data for trie accesses
 * */
public class TrackedNode { 
    protected final ByteArrayWrapper key; // a trie key
    protected final OperationType operationType; // an operation type
    protected final String transactionHash; // a transaction  hash
    protected final boolean nodeExistsInTrie; // if the tracked node exists in the trie or not

    public TrackedNode(ByteArrayWrapper rawKey, OperationType operationType,
                       String transactionHash, boolean nodeExistsInTrie) {
        this.key = rawKey;
        this.operationType = operationType;
        this.transactionHash = transactionHash;
        this.nodeExistsInTrie = nodeExistsInTrie;

        if(operationType == WRITE_OPERATION && !nodeExistsInTrie) {
            throw new IllegalArgumentException("a WRITE_OPERATION should always have a true result");
        }
    }

    public ByteArrayWrapper getKey() {
        return key;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public boolean getNodeExistsInTrie() {
        return this.nodeExistsInTrie;
    }

    @Override
    public String toString() {
        return "TrackedNode[key: " + key +
                ", operationType: " + operationType +
                ", nodeExistsInTrie:" + nodeExistsInTrie +
                ", transactionHash: " + transactionHash
                +"]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackedNode)) return false;

        TrackedNode that = (TrackedNode) o;

        if (!key.equals(that.key)) return false;
        if (operationType != that.operationType) return false;
        return transactionHash.equals(that.transactionHash);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + operationType.hashCode();
        result = 31 * result + transactionHash.hashCode();
        return result;
    }

    public boolean useForStorageRent(String transactionHash) {
        // to filter storage rent nodes, excluding non-existing nodes and deletes
        return this.nodeExistsInTrie &&
                this.operationType != DELETE_OPERATION &&
                this.transactionHash.equals(transactionHash);
    }
}
