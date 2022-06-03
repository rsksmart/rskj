package co.rsk.validators;

import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
    Validates that:
        - There are no more defined transaction subsets than the max number of execution threads
        - All the edges are within the range of the list of transactions
        - The edges do not define any empty subset
        - The edges are in ascending order
 */
public class ValidTxExecutionListsEdgesRule implements BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    @Override
    public boolean isValid(Block block) {
        short[] edges = block.getHeader().getTxExecutionListsEdges();

        if (edges == null || edges.length == 0) {
            return true;
        }

        if (edges.length > Constants.getMaxTransactionExecutionThreads()) {
            logger.warn("Invalid block: number of execution lists edges is greater than number of execution threads ({} vs {})",
                        edges.length, Constants.getMaxTransactionExecutionThreads());
            return false;
        }

        short prev = 0;
        for (short edge : edges) {
            if (edge <= prev) {
                logger.warn("Invalid block: execution lists edges are not in ascending order");
                return false;
            }
            prev = edge;
        }

        int txListSize = block.getTransactionsList().size();
        if (edges[edges.length-1] > txListSize) {
            logger.warn("Invalid block: execution list edge is out of bounds");
            return false;
        }
        return true;
    }
}
