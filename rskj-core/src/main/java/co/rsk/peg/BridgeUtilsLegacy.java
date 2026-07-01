package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationFormatVersion;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated Methods included in this class are to be used only prior to the latest HF activation
 */
@Deprecated
public class BridgeUtilsLegacy {

    private BridgeUtilsLegacy() {
        throw new IllegalAccessError("Utility class, do not instantiate it");
    }

    @Deprecated
    protected static int simulatePegoutTxSize(
        ActivationConfig.ForBlock activations,
        Federation federation,
        int inputsCount,
        int outputsCount
    ) {
        if (inputsCount < 1 || outputsCount < 1) {
            throw new IllegalArgumentException("Inputs and outputs should be at least than 1");
        }

        if (activations.isActive(ConsensusRule.RSKIP378)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtilsLegacy.simulatePegoutTxSize method after RSKIP378 activation"
            );
        }

        if (!activations.isActive(ConsensusRule.RSKIP271)) {
            return simulatePegoutTxSizePreHOP(federation, inputsCount, outputsCount);
        }

        return simulatePegoutTxSizePreTBD100(federation, inputsCount, outputsCount);
    }

    private static int simulatePegoutTxSizePreHOP(Federation federation, int inputsCount, int outputsCount) {
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
            (scriptSigChunk + INPUT_ADDITIONAL_DATA_SIZE) * inputsCount +
            (OUTPUT_SIZE + 1 + OUTPUT_ADDITIONAL_DATA_SIZE) * outputsCount;
    }

    private static int simulatePegoutTxSizePreTBD100(Federation federation, int inputsCount, int outputsCount) {
        boolean isLegacyFed = federation.getFormatVersion() != FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION.getFormatVersion();

        return isLegacyFed
            ? simulateLegacyTxSizePreTBD100(federation, inputsCount, outputsCount)
            : simulateSegwitTxSizePreTBD100(federation, inputsCount, outputsCount);
    }

    private static int simulateLegacyTxSizePreTBD100(Federation federation, int inputsCount, int outputsCount) {
        BtcTransaction tx = new BtcTransaction(federation.getBtcParams());

        for (int i = 0; i < inputsCount; i++) {
            tx.addInput(Sha256Hash.ZERO_HASH, 0, federation.getRedeemScript());
        }

        for (int i = 0; i < outputsCount; i++) {
            tx.addOutput(Coin.ZERO, federation.getAddress());
        }

        int baseSize = calculateTxBaseSize(tx, inputsCount, false);
        int signingSize = getSigningSize(federation.getNumberOfSignaturesRequired(), inputsCount);
        return baseSize + signingSize;
    }

    private static int simulateSegwitTxSizePreTBD100(Federation federation, int inputsCount, int outputsCount) {
        BtcTransaction tx = new BtcTransaction(federation.getBtcParams());

        for (int i = 0; i < outputsCount; i++) {
            tx.addOutput(Coin.ZERO, federation.getAddress());
        }

        int baseSize = calculateTxBaseSize(tx, inputsCount, true);
        int signingSize = getSigningSize(federation.getNumberOfSignaturesRequired(), inputsCount);
        int totalSize = baseSize + signingSize + (inputsCount * federation.getRedeemScript().getProgram().length);
        // As described in BIP141
        int txWeight = totalSize + (3 * baseSize);
        return txWeight / 4;
    }

    private static int calculateTxBaseSize(BtcTransaction tx, int inputsCount, boolean isSegwit) {
        int baseSize = tx.bitcoinSerialize().length;

        if (isSegwit) {
            final int SEGWIT_COMPATIBLE_SCRIPT_SIG_SIZE = 36;
            baseSize += inputsCount * SEGWIT_COMPATIBLE_SCRIPT_SIG_SIZE;
        }

        return baseSize;
    }

    private static int getSigningSize(int numberOfSignaturesRequired, int inputsCount) {
        int signatureSize = 72;
        return numberOfSignaturesRequired * inputsCount * signatureSize;
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
     *
     * @param activations
     * @param networkParameters
     * @param btcTx
     * @param btcAddress
     * @return total amount sent to the given address.
     */
    @Deprecated
    protected static Coin getAmountSentToAddress(
        ActivationConfig.ForBlock activations,
        NetworkParameters networkParameters,
        BtcTransaction btcTx,
        Address btcAddress
    ) {
        if (activations.isActive(ConsensusRule.RSKIP293)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtilsLegacy.getAmountSentToAddress method after RSKIP293 activation"
            );
        }
        Coin value = Coin.ZERO;
        for (TransactionOutput output : btcTx.getOutputs()) {
            if (output.getScriptPubKey().getToAddress(networkParameters).equals(btcAddress)) {
                value = value.add(output.getValue());
            }
        }
        return value;
    }

    /**
     * @param activations
     * @param networkParameters
     * @param btcTx
     * @param btcAddress
     * @return the list of UTXOs in a given btcTx for a given address
     */
    @Deprecated
    protected static List<UTXO> getUTXOsSentToAddress(
        ActivationConfig.ForBlock activations,
        NetworkParameters networkParameters,
        BtcTransaction btcTx,
        Address btcAddress
    ) {
        if (activations.isActive(ConsensusRule.RSKIP293)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtilsLegacy.getUTXOsSentToAddress method after RSKIP293 activation"
            );
        }
        List<UTXO> utxosList = new ArrayList<>();
        for (TransactionOutput o : btcTx.getOutputs()) {
            if (o.getScriptPubKey().getToAddress(networkParameters).equals(btcAddress)) {
                utxosList.add(
                    new UTXO(
                        btcTx.getHash(),
                        o.getIndex(),
                        o.getValue(),
                        0,
                        btcTx.isCoinBase(),
                        o.getScriptPubKey()
                    )
                );
            }
        }
        return utxosList;
    }
}
