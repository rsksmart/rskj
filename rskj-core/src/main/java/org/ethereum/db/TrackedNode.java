package org.ethereum.db;

import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;

public class TrackedNode {
    protected final ByteArrayWrapper key; // a trie key
    protected final OperationType operationType; // an operation type
    protected final String transactionHash; // a transaction  hash
    protected final boolean result; // whether the operation was successful or not
    protected final boolean isDelete; // if the node was deleted

    public TrackedNode(ByteArrayWrapper rawKey, OperationType operationType,
                       String transactionHash, boolean result, boolean isDelete) {
        this.key = rawKey;
        this.operationType = operationType;
        this.transactionHash = transactionHash;
        this.result = result;
        this.isDelete = isDelete;

        if(operationType.equals(WRITE_OPERATION) && !result) {
            throw new IllegalArgumentException("a WRITE_OPERATION should always have a true result");
        }

        if(operationType.equals(READ_OPERATION) && isDelete) {
            throw new IllegalArgumentException("a READ_OPERATION shouldn't have an isDelete");
        }

        // todo(fedejinich) find a way to validate key param
        // todo(fedejinich) find a way to validate the txHash
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

    @Override
    public String toString() { // todo(fedejinich) this was used for debugging purposes, might be removed before production
        String s = this.key.toString();
        String key = s.substring(s.length() - 5);
        String transactionHash = this.transactionHash.substring(this.transactionHash.length() - 5);
        String operationType = "";

        if(this.operationType.equals(OperationType.READ_OPERATION)) {
            operationType = "READ_OPERATION";
        } else if(this.operationType.equals(WRITE_OPERATION)) {
            operationType = "WRITE_OPERATION";
        }

        return "TrackedNode[key: " + key + ", operationType: " + operationType +
                ", transactionHash: " + transactionHash +"]";
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

    public boolean getResult() {
        return this.result;
    }

    public boolean isDelete() {
        return this.isDelete;
    }
}
