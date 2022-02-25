package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.config.BridgeConstants;
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

    @Deprecated
    protected static Address deserializeBtcAddressWithVersionLegacy(
        NetworkParameters networkParameters,
        ActivationConfig.ForBlock activations,
        byte[] addressBytes) throws BridgeIllegalArgumentException {

        if (activations.isActive(ConsensusRule.RSKIP284)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtils.deserializeBtcAddressWithVersionLegacy method after RSKIP284 activation"
            );
        }

        if (addressBytes == null || addressBytes.length == 0) {
            throw new BridgeIllegalArgumentException("Can't get an address version if the bytes are empty");
        }

        int version = addressBytes[0];
        byte[] hashBytes = new byte[20];
        System.arraycopy(addressBytes, 1, hashBytes, 0, 20);

        return new Address(networkParameters, version, hashBytes);
    }

    /**
     * Legacy version for getting the amount sent to a btc address.
     *
     * @param constants
     * @param btcTx
     * @param btcAddress
     * @return total amount sent to the given address.
     */
    @Deprecated
    protected static Coin getAmountSentToAddress(BridgeConstants constants, BtcTransaction btcTx, Address btcAddress) {
        Coin value = Coin.ZERO;
        for (TransactionOutput output : btcTx.getOutputs()) {
            if (output.getScriptPubKey().getToAddress(constants.getBtcParams()).equals(btcAddress)) {
                value = value.add(output.getValue());
            }
        }
        return value;
    }
}
