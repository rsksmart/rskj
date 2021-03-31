package co.rsk.validators;

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

    public boolean isValid(Block block) {
        return blockCompositeRule.isValid(block);
    }
}
