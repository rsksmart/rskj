package co.rsk.storagerent;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepositoryTracked;
import org.ethereum.db.OperationType;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static co.rsk.storagerent.StorageRentUtil.payableRent;
import static co.rsk.storagerent.StorageRentUtil.rollbackFee;
import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;

/**
 * StorageRentManager, responsible for paying StorageRent (RSKIP240)
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 * */
public class StorageRentManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("execute"); // NOSONAR

     /**
     * Pay storage rent.
     *
     * @param gasRemaining            remaining gas amount to pay storage rent
     * @param executionBlockTimestamp execution block timestamp
     * @param blockTrack              repository to fetch the relevant data before the transaction execution
     * @param transactionTrack        repository to update the rent timestamps
     * @return a storage rent result
     */
    public StorageRentResult pay(long gasRemaining, long executionBlockTimestamp, MutableRepositoryTracked blockTrack,
                                 MutableRepositoryTracked transactionTrack) {
        // get trie-nodes used within transaction execution
        Map<ByteArrayWrapper, OperationType> storageRentKeys = mergeNodes(blockTrack.getStorageRentNodes(),
                transactionTrack.getStorageRentNodes());
        Map<ByteArrayWrapper, OperationType> rollbackKeys = mergeNodes(blockTrack.getRollBackNodes(),
                transactionTrack.getRollBackNodes());

        if(storageRentKeys.isEmpty() && rollbackKeys.isEmpty()) {
            throw new IllegalStateException("there should be rented nodes or rollback nodes");
        }

        // map tracked nodes to RentedNode to fetch nodeSize and rentTimestamp
        Set<RentedNode> rentedNodes = fetchRentedNodes(storageRentKeys, blockTrack);
        Set<RentedNode> rollbackNodes = fetchRentedNodes(rollbackKeys, blockTrack);

        LOGGER.debug("storage rent - rented nodes: {}, rollback nodes: {}",
                rentedNodes.size(), rollbackKeys.size());

        int mismatchesCount = blockTrack.getMismatchesCount() + transactionTrack.getMismatchesCount();

        StorageRentResult result = payRent(mismatchesCount, rentedNodes, rollbackNodes, gasRemaining, executionBlockTimestamp);

        if(result.isOutOfGas()) {
            LOGGER.debug("out of gas at rent payment - storage rent result: {}", result);

            return result;
        }

        // update rent timestamps
        Set<RentedNode> nodesWithRent = rentedNodes.stream()
                .filter(rentedNode -> hasEnoughRent(executionBlockTimestamp, rentedNode))
                .collect(Collectors.toSet());
        transactionTrack.updateRents(nodesWithRent, executionBlockTimestamp);

        LOGGER.debug("storage rent result: {}", result);

        return result;
    }

    private static boolean hasEnoughRent(long executionBlockTimestamp, RentedNode rentedNode) {
        return payableRent(executionBlockTimestamp, rentedNode) > 0 ||
                rentedNode.getRentTimestamp() == NO_RENT_TIMESTAMP;
    }

    private StorageRentResult payRent(long mismatchesCount, Set<RentedNode> rentedNodes, Set<RentedNode> rollbackNodes,
                                      long gasRemaining, long executionBlockTimestamp) {
        long payableRent = StorageRentUtil.rentBy(rentedNodes, rentedNode ->
                payableRent(executionBlockTimestamp, rentedNode));
        long rollbacksRent = StorageRentUtil.rentBy(rollbackNodes, rentedNode ->
                rollbackFee(executionBlockTimestamp, rentedNodes, rentedNode));

        long rentToPay = payableRent + rollbacksRent + StorageRentUtil.mismatchesRent(mismatchesCount);

        // not enough gas to pay rent
        if(gasRemaining < rentToPay) {
            return StorageRentResult.outOfGas(rentedNodes, rollbackNodes,
                    mismatchesCount, payableRent, rollbacksRent);
        }

        long gasAfterPayingRent = GasCost.subtract(gasRemaining, rentToPay);

        return StorageRentResult.ok(rentedNodes, rollbackNodes,
                gasAfterPayingRent, mismatchesCount, rentToPay, payableRent, rollbacksRent);
    }

    private Set<RentedNode> fetchRentedNodes(Map<ByteArrayWrapper, OperationType> nodes,
                                             MutableRepositoryTracked blockTrack) {
        return nodes.entrySet()
                .stream()
                .map(entry -> blockTrack.fetchRentedNode(entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());
    }

    private Map<ByteArrayWrapper, OperationType> mergeNodes(Map<ByteArrayWrapper, OperationType> nodes1,
                                                                   Map<ByteArrayWrapper, OperationType> nodes2) {
        Map<ByteArrayWrapper, OperationType> merged = new HashMap<>();

        nodes1.forEach((key, operationType) -> MutableRepositoryTracked.track(key, operationType, merged));
        nodes2.forEach((key, operationType) -> MutableRepositoryTracked.track(key, operationType, merged));

        return merged;
    }
}
