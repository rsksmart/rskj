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
    public static StorageRentResult pay(long gasRemaining, long executionBlockTimestamp,
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

        Set<RentedNode> rentedNodes = storageRentNodes.stream()
                .map(trackedNode -> blockTrack.getRentedNode(trackedNode))
                .collect(Collectors.toSet());
        List<RentedNode> rollbackRentedNodes = rollbackNodes.stream()
                .map(trackedNode -> blockTrack.getRentedNode(trackedNode))
                .collect(Collectors.toList());

        LOGGER.trace("storage rent - rented nodes: {}, rollback nodes: {}",
                rentedNodes.size(), rollbackNodes.size());

        // calculate rent

        long payableRent = rentBy(rentedNodes,
                rentedNode -> rentedNode.payableRent(executionBlockTimestamp));

        long rollbacksRent = rentBy(rollbackRentedNodes,
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
        Set<RentedNode> nodesWithRent = rentedNodes.stream()
                .filter(rentedNode -> shouldUpdateRentTimestamp(rentedNode, executionBlockTimestamp))
                .collect(Collectors.toSet());

        transactionTrack.updateRents(nodesWithRent, executionBlockTimestamp);

        StorageRentResult result = new StorageRentResult(rentedNodes, rollbackRentedNodes,
                payableRent, rollbacksRent, gasAfterPayingRent);

        LOGGER.trace("storage rent - paid rent: {}, payable rent: {}, rollbacks rent: {}",
            result.paidRent(), result.getPayableRent(), result.getRollbacksRent());

        return result;
    }

    private static boolean shouldUpdateRentTimestamp(RentedNode rentedNode, long executionBlockTimestamp) {
        return rentedNode.payableRent(executionBlockTimestamp) > 0 ||
                rentedNode.getRentTimestamp() == Trie.NO_RENT_TIMESTAMP;
    }

    private static long rentBy(Collection<RentedNode> rentedNodes, Function<RentedNode, Long> rentFunction) {
        Optional<Long> rent = rentedNodes.stream()
                .map(r -> rentFunction.apply(r))
                .reduce(GasCost::add);

        return rentedNodes.isEmpty() || !rent.isPresent() ? 0 : rent.get();
    }
}
