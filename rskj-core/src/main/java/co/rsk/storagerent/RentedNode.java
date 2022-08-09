package co.rsk.storagerent;

import co.rsk.trie.Trie;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;

import static co.rsk.storagerent.StorageRentComputation.*;
import static org.ethereum.db.OperationType.READ_CONTRACT_CODE_OPERATION;

/**
 * A RentedNode contains the relevant data of an involved node during transaction execution.
 * It also returns the relevant information to pay the storage rent.
 * */
public class RentedNode {
    private final ByteArrayWrapper key; // a trie key
    private final OperationType operationType; // an operation type
    private final long nodeSize;
    private final long rentTimestamp;

    public RentedNode(ByteArrayWrapper rawKey, OperationType operationType, long nodeSize, long rentTimestamp) {
        this.key = rawKey;
        this.operationType = operationType;
        this.nodeSize = nodeSize;
        this.rentTimestamp = rentTimestamp;
    }

    /**
     * Calculates the payable rent amount (the total amount it's limited by the rent cap)
     *
     * @param currentBlockTimestamp a block timestamp (expressed in milliseconds)
     *
     * @return the rent amount expressed in gas units
     * */
    public long payableRent(long currentBlockTimestamp) {
        return computeRent(
                rentDue(getNodeSize(), duration(currentBlockTimestamp)),
                rentCap(),
                rentThreshold(getOperationType()));
    }
    /**
     * Calculates the new timestamp after paying the rent
     *
     * @param currentBlockTimestamp a block timestamp (expressed in milliseconds)
     *
     * @return a new updated timestamp
     * */
    public long getUpdatedRentTimestamp(long currentBlockTimestamp) {
        return computeNewTimestamp(
                getNodeSize(),
                rentDue(getNodeSize(), duration(currentBlockTimestamp)),
                getRentTimestamp(),
                currentBlockTimestamp,
                rentCap(),
                rentThreshold(getOperationType())
        );
    }

    private Long getNodeSize() {
        return nodeSize;
    }

    public long getRentTimestamp() {
        return rentTimestamp;
    }

    /***
     * Given a block timestamp, calculates the duration of a rented node (in milliseconds)
     *
     * @param currentBlockTimestamp a given block timestamp (in milliseconds)
     *
     * @return a duration, expressed in milliseconds.
     */
    private long duration(long currentBlockTimestamp) {
        long duration = 0;

        if(getRentTimestamp() == Trie.NO_RENT_TIMESTAMP) {
            // new nodes or old nodes (before hop) have zero duration, they receive the timestamp of the current block
            return duration;
        }

        duration = Math.subtractExact(currentBlockTimestamp, getRentTimestamp()); // this prevents overflows

        return duration;
    }

    public static long rentThreshold(OperationType operationType) {
        switch (operationType) {
            case WRITE_OPERATION:
                return WRITE_THRESHOLD;
            case READ_OPERATION:
                return READ_THRESHOLD;
            case READ_CONTRACT_CODE_OPERATION:
                return READ_THRESHOLD_CONTRACT_CODE;
            default:
                throw new RuntimeException("this shouldn't happen");
        }
    }

    private long rentCap() {
        return this.operationType == READ_CONTRACT_CODE_OPERATION ? RENT_CAP_CONTRACT_CODE : RENT_CAP;
    }

    public long rollbackFee(long executionBlockTimestamp) {
        long computedRent = computeRent(
                rentDue(getNodeSize(), duration(executionBlockTimestamp)),
                rentCap(),
                0); // there are no thresholds for rollbacks, we want to make the user to pay something
        return (long) (computedRent * 0.25);
    }

    @Override
    public String toString() {
        return "RentedNode[key: " + key +
                ", operationType: " + operationType +
                ", nodeSize: " + nodeSize +
                ", lastRentPaidTimestamp: " + rentTimestamp
                +"]";
    }

    public ByteArrayWrapper getKey() {
        return key;
    }

    public OperationType getOperationType() {
        return operationType;
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
