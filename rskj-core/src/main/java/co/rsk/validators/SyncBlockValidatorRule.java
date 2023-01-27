package co.rsk.validators;

import co.rsk.core.bc.BlockExecutor;
import org.ethereum.core.Block;

/**
 * A class to validate blocks received while a node is syncing.
 * Performs only basic, preemptive validations (contrast with BlockValidationRule).
 */
public class SyncBlockValidatorRule implements BlockValidationRule{
    private final BlockCompositeRule blockCompositeRule;

    public SyncBlockValidatorRule(BlockUnclesHashValidationRule blockUnclesHashValidationRule,
                                  BlockRootValidationRule blockRootValidationRule) {
        blockCompositeRule = new BlockCompositeRule(
                blockUnclesHashValidationRule,
                blockRootValidationRule
        );
    }

    public boolean isValid(Block block, BlockExecutor blockExecutor) {
        return blockCompositeRule.isValid(block, null);
    }
}
