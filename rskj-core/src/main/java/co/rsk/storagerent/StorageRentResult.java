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
    private final long outOfGasRentToPay;
    private final long paidRent;


    private StorageRentResult(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes, long gasAfterPayingRent,
                              long mismatchesCount, long executionBlockTimestamp, boolean outOfGas,
                              long outOfGasRentToPay, long paidRent) {
        this.rentedNodes = rentedNodes;
        this.rollbackNodes = rollbackNodes;
        this.gasAfterPayingRent = gasAfterPayingRent;
        this.mismatchesCount = mismatchesCount;
        this.executionBlockTimestamp = executionBlockTimestamp;
        this.outOfGas = outOfGas;
        this.outOfGasRentToPay = outOfGasRentToPay;
        this.paidRent = paidRent;
    }

    public static StorageRentResult outOfGas(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes,
                                             long mismatchesCount, long executionBlockTimestamp, long rentToPay) {
        return new StorageRentResult(rentedNodes, rollbackNodes, 0,
                mismatchesCount, executionBlockTimestamp, true, rentToPay, 0);
    }

    public static StorageRentResult ok(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes,
                                       long gasAfterPayingRent, long mismatchesCount, long executionBlockTimestamp, long paidRent) {
        return new StorageRentResult(rentedNodes, rollbackNodes, gasAfterPayingRent,
                mismatchesCount, executionBlockTimestamp, false, -1, paidRent);
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

    @VisibleForTesting
    public long getOutOfGasRentToPay() {
        return outOfGasRentToPay; // todo(fedejinich) complex with no sense, remove it
    }

    @VisibleForTesting
    public long totalRent() {
        return this.getPayableRent() + this.getRollbacksRent() + StorageRentUtil.mismatchesRent(this.mismatchesCount);
    }
}
