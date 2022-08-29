package co.rsk.storagerent;

import java.util.Set;

public class StorageRentResult {

    private final Set<RentedNode> rentedNodes;
    private final Set<RentedNode> rollbackNodes;
    private final long gasAfterPayingRent;
    private final long mismatchesCount;

    public long getExecutionBlockTimestamp() {
        return executionBlockTimestamp;
    }

    private final long executionBlockTimestamp;

    public StorageRentResult(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes, long gasAfterPayingRent,
                             long mismatchesCount, long executionBlockTimestamp) {
        this.rentedNodes = rentedNodes;
        this.rollbackNodes = rollbackNodes;
        this.gasAfterPayingRent = gasAfterPayingRent;
        this.mismatchesCount = mismatchesCount;
        this.executionBlockTimestamp = executionBlockTimestamp;
    }

    public long totalPaidRent() {
        return this.getPayableRent() + this.getRollbacksRent() + this.getMismatchesRent();
    }

    public Set<RentedNode> getRentedNodes() {
        return rentedNodes;
    }

    public Set<RentedNode> getRollbackNodes() {
        return rollbackNodes;
    }

    public long getRollbacksRent() {
        return StorageRentManager.rentBy(this.rollbackNodes,
                rentedNode -> rentedNode.rollbackFee(this.executionBlockTimestamp, this.rentedNodes));
    }

    public long getPayableRent() {
        return StorageRentManager.rentBy(this.rentedNodes,
                rentedNode -> rentedNode.payableRent(this.executionBlockTimestamp));
    }

    public long getGasAfterPayingRent() {
        return gasAfterPayingRent;
    }

    public long getMismatchesRent() {
        return StorageRentManager.getMismatchesRent(this.mismatchesCount);
    }

    public long getMismatchCount() {
        return this.mismatchesCount;
    }
}
