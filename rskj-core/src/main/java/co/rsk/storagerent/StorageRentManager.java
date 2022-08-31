package co.rsk.storagerent;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepositoryTracked;
import org.ethereum.db.OperationType;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static co.rsk.storagerent.StorageRentUtil.MISMATCH_PENALTY;
import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;

/**
 * StorageRentManager, responsible for paying StorageRent (RSKIP240)
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 * */
public class StorageRentManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("execute");

    private StorageRentManager() {
    }

    /**
     * Pay storage rent.
     *
     * @param gasRemaining            remaining gas amount to pay storage rent
     * @param executionBlockTimestamp execution block timestamp
     * @param blockTrack              repository to fetch the relevant data before the transaction execution
     * @param transactionTrack        repository to update the rent timestamps
     * @param initialMismatchesCount
     * @return new remaining gas
     */
    public static StorageRentResult pay(long gasRemaining, long executionBlockTimestamp,
                                        MutableRepositoryTracked blockTrack,
                                        MutableRepositoryTracked transactionTrack, int initialMismatchesCount) {
        // get trie-nodes used within a transaction execution
        Map<ByteArrayWrapper, OperationType> storageRentKeys = mergeNodes(blockTrack.getStorageRentNodes(),
                transactionTrack.getStorageRentNodes());
        Map<ByteArrayWrapper, OperationType> rollbackKeys = mergeNodes(blockTrack.getRollBackNodes(),
                transactionTrack.getRollBackNodes());

        if(storageRentKeys.isEmpty() && rollbackKeys.isEmpty()) {
            throw new RuntimeException("there should be rented nodes or rollback nodes");
        }

        // map tracked nodes to RentedNode to fetch nodeSize and rentTimestamp

        Set<RentedNode> rentedNodes = fetchRentedNodes(storageRentKeys, blockTrack);
        Set<RentedNode> rollbackNodes = fetchRentedNodes(rollbackKeys, blockTrack);

        LOGGER.trace("storage rent - rented nodes: {}, rollback nodes: {}",
                rentedNodes.size(), rollbackKeys.size());

        // calculate rent
        long nonExistingCount = blockTrack.getNonExistingKeys().size() + transactionTrack.getNonExistingKeys().size();

        long payableRent = rentBy(rentedNodes, rentedNode -> rentedNode.payableRent(executionBlockTimestamp));
        long rollbacksRent = rentBy(rollbackNodes, rentedNode -> rentedNode.rollbackFee(executionBlockTimestamp, rentedNodes));
        long rentToPay = payableRent + rollbacksRent + getMismatchesRent(nonExistingCount);


        if(gasRemaining < rentToPay) {
            // todo(fedejinich) this is not the right way to throw an OOG
            throw new Program.OutOfGasException("not enough gasRemaining to pay storage rent. " +
                    "gasRemaining: " + gasRemaining + ", gasNeeded: " + rentToPay);
        }

        // pay storage rent
        long gasAfterPayingRent = GasCost.subtract(gasRemaining, rentToPay);

        // update rent timestamps
        Set<RentedNode> nodesWithRent = rentedNodes.stream()
                .filter(rentedNode -> rentedNode.payableRent(executionBlockTimestamp) > 0 ||
                        rentedNode.getRentTimestamp() == NO_RENT_TIMESTAMP)
                .collect(Collectors.toSet());

        transactionTrack.updateRents(nodesWithRent, executionBlockTimestamp);

        StorageRentResult result = new StorageRentResult(rentedNodes, rollbackNodes,
//                gasAfterPayingRent, mismatchesCount, executionBlockTimestamp);
                gasAfterPayingRent, nonExistingCount, executionBlockTimestamp);

        LOGGER.trace("storage rent - paid rent: {}, payable rent: {}, rollbacks rent: {}",
            result.totalPaidRent(), result.getPayableRent(), result.getRollbacksRent());

        return result;
    }

    public static long getMismatchesRent(long mismatchesCount) {
        return MISMATCH_PENALTY * mismatchesCount;
    }

    @VisibleForTesting
    public static Set<RentedNode> fetchRentedNodes(Map<ByteArrayWrapper, OperationType> nodes,
                                                   MutableRepositoryTracked blockTrack) {
        return nodes.entrySet()
                .stream()
                .map(entry -> blockTrack.fetchRentedNode(entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());
    }

    private static Map<ByteArrayWrapper, OperationType> mergeNodes(Map<ByteArrayWrapper, OperationType> nodes1,
                                                                   Map<ByteArrayWrapper, OperationType> nodes2) {
        Map<ByteArrayWrapper, OperationType> merged = new HashMap<>();

        nodes1.forEach((key, operationType) -> MutableRepositoryTracked.track(key, operationType, merged));
        nodes2.forEach((key, operationType) -> MutableRepositoryTracked.track(key, operationType, merged));

        return merged;
    }

    public static long rentBy(Collection<RentedNode> rentedNodes, Function<RentedNode, Long> rentFunction) {
        Optional<Long> rent = rentedNodes.stream()
                .map(r -> rentFunction.apply(r))
                .reduce(GasCost::add);

        return rentedNodes.isEmpty() || !rent.isPresent() ? 0 : rent.get();
    }
}
