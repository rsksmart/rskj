package co.rsk.validators;

import org.ethereum.core.Block;

/**
 * BlockValidatorRule performs all the validations needed for a block to be considered
 * a reasonable continuation of the chain.
 * This class performs **all** validations, in contrast with SyncBlockValidatorRule.
 */
public class BlockValidatorRule implements BlockValidationRule {
    private final BlockCompositeRule blockCompositeRule;

    public BlockValidatorRule(TxsMinGasPriceRule txsMinGasPriceRule,
                              BlockTxsMaxGasPriceRule blockTxsMaxGasPriceRule,
                              BlockUnclesValidationRule blockUnclesValidationRule,
                              BlockRootValidationRule blockRootValidationRule,
                              ProofOfWorkRule proofOfWorkRule,
                              RemascValidationRule remascValidationRule,
                              BlockTimeStampValidationRule blockTimeStampValidationRule,
                              GasLimitRule gasLimitRule,
                              ExtraDataRule extraDataRule,
                              ForkDetectionDataRule forkDetectionDataRule) {
        blockCompositeRule = new BlockCompositeRule(txsMinGasPriceRule,
                blockTxsMaxGasPriceRule,
                blockUnclesValidationRule,
                blockRootValidationRule,
                proofOfWorkRule,
                remascValidationRule,
                blockTimeStampValidationRule,
                gasLimitRule,
                extraDataRule,
                forkDetectionDataRule
        );
    }

    public boolean isValid(Block block) {
        return blockCompositeRule.isValid(block);
    }

}
