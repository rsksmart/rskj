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
        int nTxs = block.getTransactionsList().size();

        if (edges == null) {
            return true;
        }
        if (edges.length > Constants.getTransactionExecutionThreads()) {
            logger.warn("Invalid block: number of execution lists edges is greater than number of execution threads ({} vs {})",
                        edges.length, Constants.getTransactionExecutionThreads());
            return false;
        }
        short prev = 0;

        for (short edge : edges) {
            if (edge <= prev) {
                logger.warn("Invalid block: execution lists edges are not in ascending order");
                return false;
            }
            if (edge > nTxs) {
                logger.warn("Invalid block: execution list edge is out of bounds: {}", edge);
                return false;
            }
            prev = edge;
        }
        return true;
    }
}
