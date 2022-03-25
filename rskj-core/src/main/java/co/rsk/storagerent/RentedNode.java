package co.rsk.storagerent;

import co.rsk.trie.Trie;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.TrackedNode;

import java.util.Objects;

import static co.rsk.storagerent.StorageRentComputation.*;
import static org.ethereum.db.OperationType.*;

/**
 * A RentedNode contains the relevant data of an involved node during transaction execution.
 * It also returns the relevant information to pay the storage rent.
 * */
public class RentedNode extends TrackedNode {
    private final Long nodeSize;
    private final Long rentTimestamp;

    private boolean loadsContractCode = false;

    // todo(fedejinich) WRITE OPERATION & LOADS CONTRACT CODE, IS THAT AN ILLEGAL STATE? SHOULD I ADD A CHECK ?

    public RentedNode(TrackedNode trackedNode, Long nodeSize, Long rentTimestamp) {
        super(trackedNode.getKey(), trackedNode.getOperationType(),
                trackedNode.getTransactionHash(), trackedNode.getSuccessful());
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
                rentThreshold());
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
                rentThreshold()
        );
    }

    private Long getNodeSize() {
        return nodeSize;
    }

    @VisibleForTesting
    public Long getRentTimestamp() {
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
            // new nodes or old nodes (before hop) have zero duration, but they receive the timestamp of the current block
            return duration;
        }

        duration = Math.subtractExact(currentBlockTimestamp, getRentTimestamp()); // this prevents overflows

        return duration;
    }

    public long rentThreshold() {
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
        return (long) (computedRent * 0.25); // todo(fedejinich) avoid casting?
    }

    // todo(fedejinich) should I override equals & hashcode?

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RentedNode)) return false;
        if (!super.equals(o)) return false;

        RentedNode that = (RentedNode) o;

        if (!Objects.equals(nodeSize, that.nodeSize)) return false;
        return Objects.equals(rentTimestamp, that.rentTimestamp);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (nodeSize != null ? nodeSize.hashCode() : 0);
        result = 31 * result + (rentTimestamp != null ? rentTimestamp.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "RentedNode[key: " + key +
                ", operationType: " + operationType +
                ", isSuccessful: " + isSuccessful +
                ", loadsContractCode: " + loadsContractCode +
                ", transactionHash: " + transactionHash +
                ", nodeSize: " + nodeSize +
                ", lastRentPaidTimestamp: " + rentTimestamp
                +"]";
    }

    /**
     * Determines if a node should be replaced by another one due to different operation types,
     * the operation with the lowest threshold it's the one that leads the storage rent payment.
     * */
    public boolean shouldBeReplaced(RentedNode newNode) {
        return newNode.rentThreshold() < this.rentThreshold();
    }

    public boolean shouldUpdateRentTimestamp(long executionBlockTimestamp) {
        return this.payableRent(executionBlockTimestamp) > 0 ||
                this.rentTimestamp == Trie.NO_RENT_TIMESTAMP;
    }
}
