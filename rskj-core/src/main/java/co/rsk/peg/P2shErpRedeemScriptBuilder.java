package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class P2shErpRedeemScriptBuilder implements ErpRedeemScriptBuilder{
    private static final Logger logger = LoggerFactory.getLogger(P2shErpRedeemScriptBuilder.class);

    public static Script createRedeemScript(Script defaultRedeemScript,
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
    public Script createRedeemScript(List<BtcECKey> defaultPublicKeys,
                                     List<BtcECKey> emergencyPublicKeys,
                                     long csvValue) {
        Script defaultRedeemScript = ScriptBuilder.createRedeemScript(
            defaultPublicKeys.size() / 2 + 1,
            defaultPublicKeys);
        Script emergencyRedeemScript = ScriptBuilder.createRedeemScript(
            emergencyPublicKeys.size() / 2 + 1,
            emergencyPublicKeys);
        validateRedeemScriptValues(defaultRedeemScript, emergencyRedeemScript, csvValue);

        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);
        return createRedeemScript(defaultRedeemScript, emergencyRedeemScript, serializedCsvValue);
    }

    private static void validateRedeemScriptValues(
        Script defaultFederationRedeemScript,
        Script erpFederationRedeemScript,
        Long csvValue
    ) {
        if (!defaultFederationRedeemScript.isSentToMultiSig() || !erpFederationRedeemScript.isSentToMultiSig()) {

            String message = "Provided redeem scripts have an invalid structure, not standard";
            logger.debug(
                "[validateP2shErpRedeemScriptValues] {}. Default script {}. Emergency script {}",
                message,
                defaultFederationRedeemScript,
                erpFederationRedeemScript
            );
            throw new VerificationException(message);
        }

        if (csvValue <= 0 || csvValue > MAX_CSV_VALUE) {
            String message = String.format(
                "Provided csv value %d must be between 0 and %d",
                csvValue,
                MAX_CSV_VALUE
            );
            logger.warn("[validateP2shErpRedeemScriptValues] {}", message);
            throw new VerificationException(message);
        }
    }
}
