package co.rsk.storagerent;

import java.util.Set;

public class StorageRentResult {

    private final Set<RentedNode> rentedNodes;
    private final Set<RentedNode> rollbackNodes;
    private final long gasAfterPayingRent;
    private final long mismatchesCount;
    private final boolean outOfGas;
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

    public long totalRent() {
        return this.getPayableRent() + this.getRollbacksRent() + this.getMismatchesRent();
    }

    public Set<RentedNode> getRentedNodes() {
        return rentedNodes;
    }

    public Set<RentedNode> getRollbackNodes() {
        return rollbackNodes;
    }

    public long getRollbacksRent() {
        return StorageRentUtil.rentBy(this.rollbackNodes,
                rentedNode -> rentedNode.rollbackFee(this.executionBlockTimestamp, this.rentedNodes));
    }

    public long getPayableRent() {
        return StorageRentUtil.rentBy(this.rentedNodes,
                rentedNode -> rentedNode.payableRent(this.executionBlockTimestamp));
    }

    public long getGasAfterPayingRent() {
        return gasAfterPayingRent;
    }

    public long getMismatchesRent() {
        return StorageRentUtil.mismatchesRent(this.mismatchesCount);
    }

    public long getMismatchCount() {
        return this.mismatchesCount;
    }

    public boolean isOutOfGas() {
        return outOfGas;
    }

    public long getOutOfGasRentToPay() {
        return outOfGasRentToPay;
    }

    public long getPaidRent() {
        return paidRent;
    }
}
