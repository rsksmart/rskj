package co.rsk.storagerent;

import co.rsk.trie.Trie;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;

import static co.rsk.storagerent.StorageRentComputation.*;
import static org.ethereum.db.OperationType.*;

/**
 * A RentedNode contains the relevant data of an involved node during transaction execution.
 * It also returns the relevant information to pay the storage rent.
 * */
public class RentedNode {
    private final ByteArrayWrapper key; // a trie key
    private final OperationType operationType; // an operation type
    private final String transactionHash; // a transaction  hash
    private final boolean nodeExistsInTrie; // if the tracked node exists in the trie or not

    private final Long nodeSize;
    private final Long rentTimestamp;

    private boolean loadsContractCode = false;

    public RentedNode(ByteArrayWrapper rawKey, OperationType operationType,
                       String transactionHash, boolean nodeExistsInTrie, Long nodeSize, Long rentTimestamp) {
        this.key = rawKey;
        this.operationType = operationType;
        this.transactionHash = transactionHash;
        this.nodeExistsInTrie = nodeExistsInTrie;

        if(operationType == WRITE_OPERATION && !nodeExistsInTrie) {
            throw new IllegalArgumentException("a WRITE_OPERATION should always exist in trie");
        }

        this.nodeSize = nodeSize;
        this.rentTimestamp = rentTimestamp;
    }

    public RentedNode(ByteArrayWrapper rawKey, OperationType operationType,
                      String transactionHash, boolean nodeExistsInTrie) {
        this(rawKey, operationType,transactionHash, nodeExistsInTrie, null, null);
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
        return (long) (computedRent * 0.25);
    }

    @Override
    public String toString() {
        return "RentedNode[key: " + key +
                ", operationType: " + operationType +
                ", nodeExistsInTrie: " + nodeExistsInTrie +
                ", loadsContractCode: " + loadsContractCode +
                ", transactionHash: " + transactionHash +
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

    public String getTransactionHash() {
        return transactionHash;
    }

    public boolean getNodeExistsInTrie() {
        return this.nodeExistsInTrie;
    }


    public boolean useForStorageRent(String transactionHash) {
        // to filter storage rent nodes, excluding non-existing nodes and deletes
        return this.nodeExistsInTrie &&
                this.operationType != DELETE_OPERATION &&
                this.transactionHash.equals(transactionHash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RentedNode)) return false;

        RentedNode that = (RentedNode) o;

        if (nodeExistsInTrie != that.nodeExistsInTrie) return false;
        if (loadsContractCode != that.loadsContractCode) return false;
        if (!key.equals(that.key)) return false;
        if (operationType != that.operationType) return false;
        if (!transactionHash.equals(that.transactionHash)) return false;
        if (nodeSize != null ? !nodeSize.equals(that.nodeSize) : that.nodeSize != null) return false;
        return rentTimestamp != null ? rentTimestamp.equals(that.rentTimestamp) : that.rentTimestamp == null;
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + operationType.hashCode();
        result = 31 * result + transactionHash.hashCode();
        result = 31 * result + (nodeExistsInTrie ? 1 : 0);
        result = 31 * result + (nodeSize != null ? nodeSize.hashCode() : 0);
        result = 31 * result + (rentTimestamp != null ? rentTimestamp.hashCode() : 0);
        result = 31 * result + (loadsContractCode ? 1 : 0);
        return result;
    }
}
