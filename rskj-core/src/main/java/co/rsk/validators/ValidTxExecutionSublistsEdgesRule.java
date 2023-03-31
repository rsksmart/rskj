package co.rsk.validators;

import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
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
public class ValidTxExecutionSublistsEdgesRule implements BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private final ActivationConfig activationConfig;

    public ValidTxExecutionSublistsEdgesRule(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
    }


    @Override
    public boolean isValid(Block block) {
        if (!activationConfig.isActive(ConsensusRule.RSKIP144, block.getHeader().getNumber())) {
            return true;
        }

        short[] edges = block.getHeader().getTxExecutionSublistsEdges();

        if (edges.length == 0) {
            return true;
        }

        if (edges.length > Constants.getTransactionExecutionThreads()) {
            logger.warn("Invalid block: number of execution lists edges is greater than number of execution threads ({} vs {})",
                        edges.length, Constants.getTransactionExecutionThreads());
            return false;
        }

        if (edges[0] <= 0) {
            logger.warn("Invalid block: execution list edge is out of bounds");
            return false;
        }

        for (int i = 0; i < edges.length - 1; i++) {
            if (edges[i] >= edges[i + 1]) {
                logger.warn("Invalid block: execution lists edges are not in ascending order");
                return false;
            }
        }

        if (edges[edges.length-1] > block.getTransactionsList().size() - 1) {
            logger.warn("Invalid block: execution list edge is out of bounds");
            return false;
        }

        return true;
    }
}
