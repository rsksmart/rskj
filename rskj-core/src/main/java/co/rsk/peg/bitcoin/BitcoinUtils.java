package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.BridgeUtils;

import java.util.Optional;

public class BitcoinUtils {
    private static final int FIRST_INPUT_INDEX = 0;

    private BitcoinUtils() { }

    public static Optional<Sha256Hash> getFirstInputSigHash(BtcTransaction btcTx){
        if (btcTx.getInputs().isEmpty()){
            return Optional.empty();
        }
        TransactionInput txInput = btcTx.getInput(FIRST_INPUT_INDEX);
        Optional<Script> redeemScript = BridgeUtils.extractRedeemScriptFromInput(txInput);
        if (!redeemScript.isPresent()) {
            return Optional.empty();
        }

        return Optional.of(btcTx.hashForSignature(
            FIRST_INPUT_INDEX,
            redeemScript.get(),
            BtcTransaction.SigHash.ALL,
            false
        ));
    }
}
