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
    private final long paidRent;
    private final long payableRent;
    private final long rollbacksRent;

    private StorageRentResult(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes, long gasAfterPayingRent,
                              long mismatchesCount, boolean outOfGas, long paidRent,
                              long payableRent, long rollbacksRent) {
        this.rentedNodes = rentedNodes;
        this.rollbackNodes = rollbackNodes;
        this.gasAfterPayingRent = gasAfterPayingRent;
        this.mismatchesCount = mismatchesCount;
        this.outOfGas = outOfGas;
        this.paidRent = paidRent;
        this.payableRent = payableRent;
        this.rollbacksRent = rollbacksRent;
    }

    public static StorageRentResult outOfGas(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes,
                                             long mismatchesCount, long payableRent, long rollbacksRent) {
        return new StorageRentResult(rentedNodes, rollbackNodes, 0,
                mismatchesCount, true, 0, payableRent, rollbacksRent);
    }

    public static StorageRentResult ok(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes,
                                       long gasAfterPayingRent, long mismatchesCount,
                                       long paidRent, long payableRent, long rollbacksRent) {
        return new StorageRentResult(rentedNodes, rollbackNodes, gasAfterPayingRent,
                mismatchesCount, false, paidRent, payableRent, rollbacksRent);
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
        return this.rollbacksRent;
    }

    @VisibleForTesting
    public long getPayableRent() {
        return this.payableRent;
    }

    @VisibleForTesting
    public long getPaidRent() {
        return paidRent;
    }
}
