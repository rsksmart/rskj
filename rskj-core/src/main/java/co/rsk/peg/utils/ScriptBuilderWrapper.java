package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;

import java.util.List;

// TODO:I Refactor remaining usages of ScriptBuilder so they use this class
public class ScriptBuilderWrapper {

    private static ScriptBuilderWrapper instance;

    static ScriptBuilderWrapper getInstance() {
        if (instance == null) {
            instance = new ScriptBuilderWrapper();
        }

        return instance;
    }

    private ScriptBuilderWrapper() {
    }

    public Script createP2SHOutputScript(Script redeemScript) {
        return ScriptBuilder.createP2SHOutputScript(redeemScript);
    }

    public Script createP2SHOutputScript(int threshold, List<BtcECKey> pubkeys) {
        return ScriptBuilder.createP2SHOutputScript(threshold, pubkeys);
    }

    public Script createRedeemScript(int threshold, List<BtcECKey> pubkeys) {
        return ScriptBuilder.createRedeemScript(threshold, pubkeys);
    }

    public Script createP2SHOutputScript(byte[] hash) {
        return ScriptBuilder.createP2SHOutputScript(hash);
    }

    public Script updateScriptWithSignature(Script scriptSig, byte[] signature, int targetIndex, int sigsPrefixCount, int sigsSuffixCount) {
        return ScriptBuilder.updateScriptWithSignature(scriptSig, signature, targetIndex, sigsPrefixCount, sigsSuffixCount);
    }

    // TODO Add remaining ScriptBuilder methods when required

}
