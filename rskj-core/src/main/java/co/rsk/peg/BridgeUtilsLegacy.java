package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated Methods included in this class are to be used only prior to the latest HF activation
 */
@Deprecated
public class BridgeUtilsLegacy {

    private BridgeUtilsLegacy() {}

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
                "Calling BridgeUtils.getAmountSentToAddress method after RSKIP293 activation"
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
     * @param networkParameters
     * @param btcTx
     * @param btcAddress
     * @return the list of UTXOs in a given btcTx for a given address
     */
    @Deprecated
    protected static List<UTXO> getUTXOsForAddress(
        ActivationConfig.ForBlock activations,
        NetworkParameters networkParameters,
        BtcTransaction btcTx,
        Address btcAddress
    ) {
        if (activations.isActive(ConsensusRule.RSKIP293)) {
            throw new DeprecatedMethodCallException(
                "Calling BridgeUtils.getUTXOsForAddress method after RSKIP293 activation"
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
