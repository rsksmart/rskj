package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.ScriptException;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static co.rsk.peg.BridgeUtils.getMinimumPegInTxValue;
import static co.rsk.peg.BridgeUtils.isAnyUTXOAmountBelowMinimum;
import static co.rsk.peg.BridgeUtils.scriptCorrectlySpendsTx;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP293;

/**
 * @deprecated Methods included in this class are to be used only prior to the latest HF activation
 */
@Deprecated
public class BridgeUtilsLegacy {

    private static final Logger logger = LoggerFactory.getLogger("BridgeUtilsLegacy");

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

    /**
     * It checks if the tx doesn't spend any of the federations' funds and if it sends more than
     * the minimum ({@see BridgeConstants::getMinimumLockTxValue}) to any of the federations
     * @param tx the BTC transaction to check
     * @param activeFederations the active federations
     * @param retiredFederationP2SHScript the retired federation P2SHScript. Could be {@code null}.
     * @param btcContext the BTC Context
     * @param bridgeConstants the Bridge constants
     * @param activations the network HF activations configuration
     * @return true if this is a valid peg-in transaction
     */
    @Deprecated
    public static boolean isValidPegInTx(
        BtcTransaction tx,
        List<Federation> activeFederations,
        Script retiredFederationP2SHScript,
        Context btcContext,
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations) {
        if (activations.isActive(ConsensusRule.RSKIP379)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtilsLegacy.isValidPegInTx method after RSKIP379 activation"
            );
        }

        // First, check tx is not a typical release tx (tx spending from any of the federation addresses and
        // optionally sending some change to any of the federation addresses)
        for (int i = 0; i < tx.getInputs().size(); i++) {
            final int index = i;
            if (activeFederations.stream().anyMatch(federation -> scriptCorrectlySpendsTx(tx, index, federation.getP2SHScript()))) {
                return false;
            }

            if (retiredFederationP2SHScript != null && scriptCorrectlySpendsTx(tx, index, retiredFederationP2SHScript)) {
                return false;
            }

            // Check if the registered utxo is not change from an utxo spent from either a fast bridge federation,
            // erp federation, or even a retired fast bridge or erp federation
            if (activations.isActive(ConsensusRule.RSKIP201)) {
                RedeemScriptParser redeemScriptParser = RedeemScriptParserFactory.get(tx.getInput(index).getScriptSig().getChunks());
                try {
                    // Consider transactions that have an input with a redeem script of type P2SH ERP FED
                    // to be "future transactions" that should not be pegins. These are gonna be considered pegouts.
                    // This is only for backwards compatibility reasons. As soon as RSKIP353 activates,
                    // pegins to the new federation should be valid again.
                    // There's no reason for someone to send an actual pegin of this type before the new fed is active.
                    // TODO: Remove this if block after RSKIP353 activation
                    if (!activations.isActive(ConsensusRule.RSKIP353) &&
                            (redeemScriptParser.getMultiSigType() == RedeemScriptParser.MultiSigType.P2SH_ERP_FED ||
                                 redeemScriptParser.getMultiSigType() == RedeemScriptParser.MultiSigType.FAST_BRIDGE_P2SH_ERP_FED)) {
                        String message = "Tried to register a transaction with a P2SH ERP federation redeem script before RSKIP353 activation";
                        logger.warn("[isValidPegInTx] {}", message);
                        throw new ScriptException(message);
                    }
                    Script inputStandardRedeemScript = redeemScriptParser.extractStandardRedeemScript();
                    if (activeFederations.stream().anyMatch(federation -> federation.getStandardRedeemScript().equals(inputStandardRedeemScript))) {
                        return false;
                    }

                    Script outputScript = ScriptBuilder.createP2SHOutputScript(inputStandardRedeemScript);
                    if (outputScript.equals(retiredFederationP2SHScript)) {
                        return false;
                    }
                } catch (ScriptException e) {
                    // There is no redeem script, could be a peg-in from a P2PKH address
                }
            }
        }

        Wallet federationsWallet = BridgeUtils.getFederationsNoSpendWallet(
            btcContext,
            activeFederations,
            false,
            null
        );
        Coin valueSentToMe = tx.getValueSentToMe(federationsWallet);
        Coin minimumPegInTxValue = getMinimumPegInTxValue(activations, bridgeConstants);

        boolean isUTXOsOrTxAmountBelowMinimum =
            activations.isActive(RSKIP293) ? isAnyUTXOAmountBelowMinimum(
                activations,
                bridgeConstants,
                tx,
                federationsWallet
            ) : valueSentToMe.isLessThan(minimumPegInTxValue); // Legacy minimum validation against the total amount

        if (!isUTXOsOrTxAmountBelowMinimum) {
            return true;
        }

        logger.warn(
            activations.isActive(RSKIP293)?
                "[btctx:{}] Someone sent to the federation UTXOs amount less than {} satoshis":
                "[btctx:{}] Someone sent to the federation less than {} satoshis",
            tx.getHash(),
            minimumPegInTxValue
        );
        return false;
    }

    /**
     * It checks if the tx doesn't spend any of the federations' funds and if it sends more than
     * the minimum ({@see BridgeConstants::getMinimumLockTxValue}) to any of the federations
     * @param tx the BTC transaction to check
     * @param federation the active federation
     * @param btcContext the BTC Context
     * @param bridgeConstants the Bridge constants
     * @param activations the network HF activations configuration
     * @return true if this is a valid peg-in transaction
     */
    @Deprecated
    public static boolean isValidPegInTx(
        BtcTransaction tx,
        Federation federation,
        Context btcContext,
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations) {
        if (activations.isActive(ConsensusRule.RSKIP379)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtilsLegacy.isValidPegInTx method after RSKIP379 activation"
            );
        }

        return isValidPegInTx(
            tx,
            Collections.singletonList(federation),
            null,
            btcContext,
            bridgeConstants,
            activations
        );
    }
}
