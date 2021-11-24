package co.rsk.peg;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

/**
 * @deprecated Methods included in this class are to be used only prior to the latest HF activation
 */
@Deprecated
public class BridgeUtilsLegacy {

    private BridgeUtilsLegacy() {
        throw new IllegalAccessError("Utility class, do not instantiate it");
    }

    @Deprecated
    protected static int calculatePegoutTxSize(ActivationConfig.ForBlock activations,
                                               Federation federation,
                                               int inputs,
                                               int outputs) {

        if (inputs < 1 || outputs < 1) {
            throw new IllegalArgumentException("Inputs or outputs should be more than 1");
        }

        if (activations.isActive(ConsensusRule.RSKIP271)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtilsLegacy.calculatePegoutTxSize method after RSKIP271 activation"
            );
        }

        final int SIGNATURE_MULTIPLIER = 71;
        final int OUTPUT_SIZE = 25;
        // This data accounts for txid+vout+sequence
        final int INPUT_ADDITIONAL_DATA_SIZE = 40;
        // This data accounts for the value+index
        final int OUTPUT_ADDITIONAL_DATA_SIZE = 9;
        // This data accounts for the version field
        final int TX_ADDITIONAL_DATA_SIZE = 4;
        // The added ones are to account for the data size
        final int scriptSigChunk = federation.getNumberOfSignaturesRequired() * (SIGNATURE_MULTIPLIER + 1) +
            federation.getRedeemScript().getProgram().length + 1;
        return TX_ADDITIONAL_DATA_SIZE +
            (scriptSigChunk + INPUT_ADDITIONAL_DATA_SIZE) * inputs +
            (OUTPUT_SIZE + 1 + OUTPUT_ADDITIONAL_DATA_SIZE) * outputs;
    }
}
