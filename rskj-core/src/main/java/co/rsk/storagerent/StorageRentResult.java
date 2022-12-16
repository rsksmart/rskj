package co.rsk.storagerent;

import com.google.common.annotations.VisibleForTesting;

import java.util.Set;

public class StorageRentResult {

    private final boolean outOfGas;

    // params used for testing and log trace
    private final Set<RentedNode> rentedNodes;
    private final Set<RentedNode> rollbackNodes;
    private final long gasAfterPayingRent;
    private final long mismatchesCount;
    private final long executionBlockTimestamp;
    private final long paidRent;


    private StorageRentResult(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes, long gasAfterPayingRent,
                              long mismatchesCount, long executionBlockTimestamp, boolean outOfGas, long paidRent) {
        this.rentedNodes = rentedNodes;
        this.rollbackNodes = rollbackNodes;
        this.gasAfterPayingRent = gasAfterPayingRent;
        this.mismatchesCount = mismatchesCount;
        this.executionBlockTimestamp = executionBlockTimestamp;
        this.outOfGas = outOfGas;
        this.paidRent = paidRent;
    }

    public static StorageRentResult outOfGas(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes,
                                             long mismatchesCount, long executionBlockTimestamp) {
        return new StorageRentResult(rentedNodes, rollbackNodes, 0,
                mismatchesCount, executionBlockTimestamp, true, 0);
    }

    public static StorageRentResult ok(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes,
                                       long gasAfterPayingRent, long mismatchesCount, long executionBlockTimestamp,
                                       long paidRent) {
        return new StorageRentResult(rentedNodes, rollbackNodes, gasAfterPayingRent,
                mismatchesCount, executionBlockTimestamp, false, paidRent);
    }

    public boolean isOutOfGas() {
        return outOfGas;
    }

    public long getGasAfterPayingRent() {
        return gasAfterPayingRent;
    }

    @VisibleForTesting
    public Set<RentedNode> getRentedNodes() {
        return rentedNodes;
    }

    @VisibleForTesting
    public Set<RentedNode> getRollbackNodes() {
        return rollbackNodes;
    }

    @VisibleForTesting
    public long getMismatchCount() {
        return this.mismatchesCount;
    }

    @VisibleForTesting
    public long getRollbacksRent() {
        return StorageRentUtil.rentBy(this.rollbackNodes,
                rentedNode -> rentedNode.rollbackFee(this.executionBlockTimestamp, this.rentedNodes));
    }

    @VisibleForTesting
    public long getPayableRent() {
        return StorageRentUtil.rentBy(this.rentedNodes,
                rentedNode -> rentedNode.payableRent(this.executionBlockTimestamp));
    }

    @VisibleForTesting
    public long getPaidRent() {
        return paidRent;
    }
}
