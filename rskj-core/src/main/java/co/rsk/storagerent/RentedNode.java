package co.rsk.storagerent;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;

/**
 * Represents a trie-node used within transaction execution and contains only the relevant data to pay the storage rent.
 * */
public class RentedNode {
    private final ByteArrayWrapper key;
    private final OperationType operationType;
    private final long nodeSize;
    private final long rentTimestamp;

    public RentedNode(ByteArrayWrapper rawKey, OperationType operationType, long nodeSize, long rentTimestamp) {
        this.key = rawKey;
        this.operationType = operationType;
        this.nodeSize = nodeSize;
        this.rentTimestamp = rentTimestamp;
    }

    public ByteArrayWrapper getKey() {
        return key;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public long getNodeSize() {
        return nodeSize;
    }

    public long getRentTimestamp() {
        return rentTimestamp;
    }

    @Override
    public String toString() {
        return "RentedNode[key: " + key +
                ", operationType: " + operationType +
                ", nodeSize: " + nodeSize +
                ", lastRentPaidTimestamp: " + rentTimestamp
                +"]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RentedNode)) return false;

        RentedNode that = (RentedNode) o;

        if (getNodeSize() != that.getNodeSize()) return false;
        if (getRentTimestamp() != that.getRentTimestamp()) return false;
        if (!getKey().equals(that.getKey())) return false;
        return getOperationType() == that.getOperationType();
    }

    @Override
    public int hashCode() {
        int result = getKey().hashCode();
        result = 31 * result + getOperationType().hashCode();
        result = 31 * result + (int) (getNodeSize() ^ (getNodeSize() >>> 32));
        result = 31 * result + (int) (getRentTimestamp() ^ (getRentTimestamp() >>> 32));
        return result;
    }
}
