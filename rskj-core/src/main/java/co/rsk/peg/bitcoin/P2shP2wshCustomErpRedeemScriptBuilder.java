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
        if (defaultThreshold <= 0) {
            throw new IllegalArgumentException("Default threshold must be greater than 0");
        }

        if (defaultThreshold > defaultPublicKeys.size()) {
            throw new IllegalArgumentException("The number of default public keys must be greater or equal than default threshold");
        }

        if (defaultThreshold > 66) {
            throw new IllegalArgumentException("The protocol only supports 66 signers");
        }

        return scriptBuilder.op(ScriptOpCodes.OP_NOTIF).build();
    }
}


