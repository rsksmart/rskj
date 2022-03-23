package co.rsk.storagerent;

import co.rsk.trie.Trie;
import co.rsk.util.HexUtils;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrackedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static co.rsk.storagerent.StorageRentComputation.*;

/**
 * A RentedNode contains the relevant data of an involved node during transaction execution.
 * It also returns the relevant information to pay the storage rent.
 * */
public class RentedNode extends TrackedNode {
    private static final Logger LOGGER_FEDE = LoggerFactory.getLogger("fede");

    private final Long nodeSize;
    private final Long rentTimestamp;

    private boolean loadsContractCode = false;

    // todo(fedejinich) WRITE OPERATION & LOADS CONTRACT CODE, IS THAT AN ILLEGAL STATE? SHOULD I ADD A CHECK ?

    public RentedNode(TrackedNode trackedNode, Long nodeSize, Long rentTimestamp) {
        super(trackedNode.getKey(), trackedNode.getOperationType(),
                trackedNode.getTransactionHash(), trackedNode.getResult(), trackedNode.isDelete());
        this.nodeSize = nodeSize;
        this.rentTimestamp = rentTimestamp;
    }

    @VisibleForTesting
    public RentedNode(TrackedNode trackedNode, Long nodeSize, Long rentTimestamp, boolean loadsContractCode) {
        this(trackedNode, nodeSize, rentTimestamp);
        this.loadsContractCode = loadsContractCode;
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
           // LOGGER_FEDE.error("{} is not timestamped yet", printableKey(this.getKey().getData()));
            logDuration(currentBlockTimestamp, duration);

            // new nodes or old nodes (before hop) have zero duration, but they receive the timestamp of the current block
            return duration;
        }

        duration = Math.subtractExact(currentBlockTimestamp, getRentTimestamp()); // this prevents overflows
        logDuration(currentBlockTimestamp, duration); // todo(fedejinich) debug

        return duration;
    }

    private long rentThreshold() {
        switch (operationType) {
            case WRITE_OPERATION:
                return WRITE_THRESHOLD;
            case READ_OPERATION:
                return loadsContractCode ?
                        READ_THRESHOLD_CONTRACT_CODE : READ_THRESHOLD;
            default:
                throw new RuntimeException("this shouldn't happen");
        }
    }

    private long rentCap() {
        return loadsContractCode ? RENT_CAP_CONTRACT_CODE : RENT_CAP;
    }

    public long rollbackFee(long executionBlockTimestamp) {
        return (long) (payableRent(executionBlockTimestamp) * 0.25); // todo(fedejinich) avoid casting?
    }

    // todo(fedejinich) should I override equals & hashcode?

    private String printableKey(byte[] key) {
        String s = HexUtils.toJsonHex(key);
        return s.substring(s.length() - 5);
    }

    private void logDuration(long currentBlockTimsetamp, long duration) {
        // LOGGER_FEDE.error("key: {}, blockTimestamp: {}, lastRentPaidTimestamp: {}, duration: {}", printableKey(getKey().getData()), currentBlockTimsetamp, getLastRentPaidTimestamp(), duration);
    }


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
        String s = this.key.toString();
        String key = s;//s.substring(s.length() - 5);
        String transactionHash = this.transactionHash.substring(this.transactionHash.length() - 5);
        String operationType = "NO_OP";
        if(this.operationType.equals(OperationType.READ_OPERATION)) {
            operationType = "READ_OPERATION";
        } else if(this.operationType.equals(OperationType.WRITE_OPERATION)) {
            operationType = "WRITE_OPERATION";
        } else {
            throw new RuntimeException("shoudln't reach here"); // todo(fedejinch) checkout this exeception
        }

        return "RentedNode[key: " + key + ", operationType: " + operationType + ", result: " + result + ", isDelete: " + isDelete +
                ", loadsContractCode: " + loadsContractCode + ", transactionHash: " + transactionHash +
                ", nodeSize: " + nodeSize +", lastRentPaidTimestamp: " + rentTimestamp +"]";
    }
}
