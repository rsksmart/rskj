package co.rsk.storagerent;

import java.util.Set;

public class StorageRentResult {

    private final Set<RentedNode> rentedNodes;
    private final Set<RentedNode> rollbackNodes;
    private final long rollbacksRent;
    private final long payableRent;
    private final long gasAfterPayingRent;
    private final long mismatchesCount;

    // todo(fedejinich) refactor this, calculate rents instead of having fixed values

    public StorageRentResult(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes, long payableRent,
                             long rollbacksRent, long gasAfterPayingRent, long mismatchesCount) {
        this.rentedNodes = rentedNodes;
        this.rollbackNodes = rollbackNodes;
        this.payableRent = payableRent;
        this.rollbacksRent = rollbacksRent;
        this.gasAfterPayingRent = gasAfterPayingRent;
        this.mismatchesCount = mismatchesCount;
    }

    public long paidRent() {
        return this.payableRent + this.rollbacksRent + this.getMismatchesRent();
    }

    public Set<RentedNode> getRentedNodes() {
        return rentedNodes;
    }

    public Set<RentedNode> getRollbackNodes() {
        return rollbackNodes;
    }

    public long getRollbacksRent() {
        return rollbacksRent;
    }

    public long getPayableRent() {
        return payableRent;
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
