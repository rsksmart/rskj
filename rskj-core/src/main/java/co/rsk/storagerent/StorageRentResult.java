package co.rsk.storagerent;

import java.util.List;
import java.util.Set;

public class StorageRentResult {

    private final Set<RentedNode> rentedNodes;
    private final Set<RentedNode> rollbackNodes;
    private final long rollbacksRent;
    private final long payableRent;
    private final long gasAfterPayingRent;

    public StorageRentResult(Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes, long payableRent, long rollbacksRent, long gasAfterPayingRent) {
        this.rentedNodes = rentedNodes;
        this.rollbackNodes = rollbackNodes;
        this.payableRent = payableRent;
        this.rollbacksRent = rollbacksRent;
        this.gasAfterPayingRent = gasAfterPayingRent;
    }

    public long paidRent() {
        return this.payableRent + this.rollbacksRent;
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
}
