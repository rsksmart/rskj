package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_INTERNAL_REDEEM_SCRIPTS;

public class P2shErpRedeemScriptBuilder implements ErpRedeemScriptBuilder{
    private static final Logger logger = LoggerFactory.getLogger(P2shErpRedeemScriptBuilder.class);

    private P2shErpRedeemScriptBuilder() {}

    public static P2shErpRedeemScriptBuilder builder() {
        return new P2shErpRedeemScriptBuilder();
    }

    @Override
    public Script of(
        List<BtcECKey> defaultPublicKeys,
        int defaultThreshold,
        List<BtcECKey> emergencyPublicKeys,
        int emergencyThreshold,
        long csvValue
    ) {
        Script defaultRedeemScript;
        Script emergencyRedeemScript;

        try {
            defaultRedeemScript = ScriptBuilder.createRedeemScript(defaultThreshold, defaultPublicKeys);
            emergencyRedeemScript = ScriptBuilder.createRedeemScript(emergencyThreshold, emergencyPublicKeys);
        } catch (IllegalArgumentException e) {
            throw new RedeemScriptCreationException(
                String.format("There was an error creating the redeem scripts. %s", e),
                INVALID_INTERNAL_REDEEM_SCRIPTS
            );
        }

        ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(defaultRedeemScript, emergencyRedeemScript, csvValue);
        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);
        logger.debug("[createRedeemScriptFromKeys] Creating the redeem script from the scripts");
        return createRedeemScriptFromScripts(defaultRedeemScript, emergencyRedeemScript, serializedCsvValue);
    }

    private Script createRedeemScriptFromScripts(
        Script defaultRedeemScript,
        Script emergencyRedeemScript,
        byte[] serializedCsvValue
    ) {
        ScriptBuilder scriptBuilder = new ScriptBuilder();

        return scriptBuilder
            .op(ScriptOpCodes.OP_NOTIF)
            .addChunks(defaultRedeemScript.getChunks())
            .op(ScriptOpCodes.OP_ELSE)
            .data(serializedCsvValue)
            .op(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY)
            .op(ScriptOpCodes.OP_DROP)
            .addChunks(emergencyRedeemScript.getChunks())
            .op(ScriptOpCodes.OP_ENDIF)
            .build();
    }
}
