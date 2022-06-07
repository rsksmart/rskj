package co.rsk.storagerent;

import co.rsk.trie.Trie;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.MutableRepositoryTracked;
import org.ethereum.db.TrackedNode;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * StorageRentManager, responsible for paying StorageRent (RSKIP240)
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 * */
public class StorageRentManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("execute");

    public static final long NO_ROLLBACK_RENT_YET = -1;
    private static final long NO_PAYABLE_RENT_YET = -1;
    private static final long NO_TOTAL_RENT_YET = -1;

    private Set<RentedNode> rentedNodes = Collections.emptySet();
    private List<RentedNode> rollbackNodes = Collections.emptyList();

    // todo(fedejinich) this fields are unnecessary for production, but good for testing
    //  they will be removed before merging into master
    private long rollbacksRent;
    private long payableRent;
    private long paidRent;

    public StorageRentManager() {
        this.rollbacksRent = NO_ROLLBACK_RENT_YET;
        this.payableRent = NO_PAYABLE_RENT_YET;
        this.paidRent = NO_TOTAL_RENT_YET;
    }

    /**
     * Pay storage rent.
     *
     * @param gasRemaining remaining gas amount to pay storage rent
     * @param executionBlockTimestamp execution block timestamp
     * @param blockTrack repository to fetch the relevant data before the transaction execution
     * @param transactionTrack repository to update the rent timestamps
     *
     * @return new remaining gas
     * */
    public long pay(long gasRemaining, long executionBlockTimestamp,
                    MutableRepositoryTracked blockTrack, MutableRepositoryTracked transactionTrack,
                    String transactionHash) {
        // todo(fedejinich) this step is unnecessary, i should request RentedNodes directly
        // get trie-nodes used within a transaction execution

        Set<TrackedNode> storageRentNodes = new HashSet<>();
        blockTrack.getStorageRentNodes(transactionHash).forEach(trackedNode -> storageRentNodes.add(trackedNode));
        transactionTrack.getStorageRentNodes(transactionHash).forEach(trackedNode -> storageRentNodes.add(trackedNode));

        List<TrackedNode> rollbackNodes = new ArrayList<>();
        blockTrack.getRollBackNodes(transactionHash).forEach(rollBackNode -> rollbackNodes.add(rollBackNode));
        transactionTrack.getRollBackNodes(transactionHash).forEach(rollBackNode -> rollbackNodes.add(rollBackNode));

        if(storageRentNodes.isEmpty() && rollbackNodes.isEmpty()) {
            throw new RuntimeException("there should be rented nodes or rollback nodes");
        }

        // map tracked nodes to RentedNode to fetch nodeSize and rentTimestamp

        this.rentedNodes = storageRentNodes.stream()
                .map(trackedNode -> blockTrack.getRentedNode(trackedNode))
                .collect(Collectors.toSet());
        this.rollbackNodes = rollbackNodes.stream()
                .map(trackedNode -> blockTrack.getRentedNode(trackedNode))
                .collect(Collectors.toList());

        LOGGER.trace("storage rent - rented nodes: {}, rollback nodes: {}",
                this.rentedNodes.size(), this.rollbackNodes.size());

        // calculate rent

        long payableRent = rentBy(this.rentedNodes,
                rentedNode -> rentedNode.payableRent(executionBlockTimestamp));

        long rollbacksRent = rentBy(this.rollbackNodes,
                rentedNode -> rentedNode.rollbackFee(executionBlockTimestamp));
        
        long rentToPay = payableRent + rollbacksRent;

        if(gasRemaining < rentToPay) {
            // todo(fedejinich) this is not the right way to throw an OOG
            throw new Program.OutOfGasException("not enough gasRemaining to pay storage rent. " +
                    "gasRemaining: " + gasRemaining + ", gasNeeded: " + rentToPay);
        }

        // pay storage rent
        long gasAfterPayingRent = GasCost.subtract(gasRemaining, rentToPay);

        // update rent timestamps
        // should update timestamps ONLY if there's any payable rent or if node is not timestamped yet
        Set<RentedNode> nodesWithRent = this.rentedNodes.stream()
                .filter(rentedNode -> shouldUpdateRentTimestamp(rentedNode, executionBlockTimestamp))
                .collect(Collectors.toSet());

        transactionTrack.updateRents(nodesWithRent, executionBlockTimestamp);

        this.paidRent = rentToPay;
        this.payableRent = payableRent;
        this.rollbacksRent = rollbacksRent;

        LOGGER.trace("storage rent - paid rent: {}, payable rent: {}, rollbacks rent: {}",
            this.paidRent, this.payableRent, this.rollbacksRent);

        return gasAfterPayingRent;
    }

    public boolean shouldUpdateRentTimestamp(RentedNode rentedNode, long executionBlockTimestamp) {
        return rentedNode.payableRent(executionBlockTimestamp) > 0 ||
                rentedNode.getRentTimestamp() == Trie.NO_RENT_TIMESTAMP;
    }

    private long rentBy(Collection<RentedNode> rentedNodes, Function<RentedNode, Long> rentFunction) {
        Optional<Long> rent = rentedNodes.stream()
                .map(r -> rentFunction.apply(r))
                .reduce(GasCost::add);

        return rentedNodes.isEmpty() || !rent.isPresent() ? 0 : rent.get();
    }

    @VisibleForTesting
    public long getPaidRent() {
        if(this.payableRent == NO_PAYABLE_RENT_YET ||
                this.rollbacksRent == NO_ROLLBACK_RENT_YET || this.paidRent == NO_TOTAL_RENT_YET) {
            throw new RuntimeException("should pay rent before querying paid rent");
        }

        return this.paidRent;
    }

    @VisibleForTesting
    public long getPayableRent() {
        return payableRent;
    }

    @VisibleForTesting
    public long getRollbacksRent() {
        return this.rollbacksRent;
    }

    @VisibleForTesting
    public Set<RentedNode> getRentedNodes() {
        return this.rentedNodes;
    }

    @VisibleForTesting
    public List<RentedNode> getRollbackNodes() {
        return this.rollbackNodes;
    }
}
