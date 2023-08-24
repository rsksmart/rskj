package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.BridgeUtils;

import java.util.Optional;

public class BitcoinUtils {
    private BitcoinUtils() { }

    public static Optional<Sha256Hash> getFirstInputSigHash(BtcTransaction btcTx){
        if (btcTx.getInputs().isEmpty()){
            return Optional.empty();
        }
        TransactionInput txInput = btcTx.getInput(0);
        Optional<Script> redeemScriptOptional = BridgeUtils.extractRedeemScriptFromInput(txInput);
        if (!redeemScriptOptional.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(btcTx.hashForSignature(
            0,
            redeemScriptOptional.get(),
            BtcTransaction.SigHash.ALL,
            false
        ));
    }
}
