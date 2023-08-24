package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;

import java.util.List;

public class BitcoinUtils {
    private BitcoinUtils() { }

    public static Sha256Hash getFirstInputSigHash(BtcTransaction btcTx){
        if (btcTx.getInputs().isEmpty()){
            throw new IllegalArgumentException("Btc transaction with no inputs. Cannot obtained sighash for a empty btc tx.");
        }
        TransactionInput txInput = btcTx.getInput(0);
        return btcTx.hashForSignature(
            0,
            getRedeemScript(txInput),
            BtcTransaction.SigHash.ALL,
            false
        );
    }

    private static Script getRedeemScript(TransactionInput txInput) {
        Script inputScript = txInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        // Last chunk of the scriptSig contains the redeem script
        byte[] program = chunks.get(chunks.size() - 1).data;
        return new Script(program);
    }
}
