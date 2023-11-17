package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class P2shErpRedeemScriptBuilder implements ErpRedeemScriptBuilder{
    private static final Logger logger = LoggerFactory.getLogger(P2shErpRedeemScriptBuilder.class);

    @Override
    public Script createRedeemScriptFromKeys(List<BtcECKey> defaultPublicKeys,
                                             int defaultThreshold,
                                             List<BtcECKey> emergencyPublicKeys,
                                             int emergencyThreshold,
                                             long csvValue) {

        Script defaultRedeemScript = ScriptBuilder.createRedeemScript(defaultThreshold, defaultPublicKeys);
        Script emergencyRedeemScript = ScriptBuilder.createRedeemScript(emergencyThreshold, emergencyPublicKeys);

        ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(defaultRedeemScript, emergencyRedeemScript, csvValue);

        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);
        logger.debug("[createRedeemScriptFromKeys] Creating the redeem script from the scripts");
        Script redeemScript = createRedeemScriptFromScripts(defaultRedeemScript, emergencyRedeemScript, serializedCsvValue);

        logger.debug("[createRedeemScriptFromKeys] Validating redeem script size");
        ScriptValidations.validateScriptSize(redeemScript);

        return redeemScript;
    }
    private static Script createRedeemScriptFromScripts(Script defaultRedeemScript,
                                     Script emergencyRedeemScript,
                                     byte[] serializedCsvValue) {

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
