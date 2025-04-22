package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class P2shP2wshCustomErpRedeemScriptBuilder implements ErpRedeemScriptBuilder {

    private P2shP2wshCustomErpRedeemScriptBuilder() {}

    public static P2shP2wshCustomErpRedeemScriptBuilder builder() {
        return new P2shP2wshCustomErpRedeemScriptBuilder();
    }

    @Override
    public Script of(
        List<BtcECKey> defaultPublicKeys,
        int defaultThreshold,
        List<BtcECKey> emergencyPublicKeys,
        int emergencyThreshold,
        long csvValue
    ) {
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        Script customRedeemScript = ScriptBuilder.createCustomRedeemScript(defaultThreshold, defaultPublicKeys);
        scriptBuilder.addChunks(customRedeemScript.getChunks());
        return scriptBuilder.op(ScriptOpCodes.OP_NOTIF).build();
    }
}


