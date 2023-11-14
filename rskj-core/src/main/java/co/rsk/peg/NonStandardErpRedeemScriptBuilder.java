package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NonStandardErpRedeemScriptBuilder implements ErpRedeemScriptBuilder {
    private static final Logger logger = LoggerFactory.getLogger(NonStandardErpRedeemScriptBuilder.class);

    public Script createRedeemScript(List<BtcECKey> defaultPublicKeys,
                                     List<BtcECKey> emergencyPublicKeys,
                                     long csvValue) {

        Script defaultRedeemScript = ScriptBuilder.createRedeemScript(
            defaultPublicKeys.size() / 2 + 1,
            defaultPublicKeys);
        Script emergencyRedeemScript = ScriptBuilder.createRedeemScript(
            emergencyPublicKeys.size() / 2 + 1,
            emergencyPublicKeys);

        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);

        logger.debug("[getRedeemScript] Creating the redeem script from the keys");
        Script redeemScript = createRedeemScript(defaultRedeemScript, emergencyRedeemScript, serializedCsvValue);

        logger.debug("[getRedeemScript] Validating redeem script values");
        validateRedeemScriptValues(defaultRedeemScript, emergencyRedeemScript, csvValue, redeemScript);
        return redeemScript;

    }

    private static void validateRedeemScriptValues(
        Script defaultRedeemScript,
        Script emergencyRedeemScript,
        Long csvValue,
        Script redeemScript
    ) {
        if (!defaultRedeemScript.isSentToMultiSig() || !emergencyRedeemScript.isSentToMultiSig()) {
            String message = "Provided redeem scripts inside the erp one have an invalid structure, not standards";
            logger.debug(
                "[validateLegacyErpRedeemScriptValues] {}. Default script {}. Emergency script {}",
                message,
                defaultRedeemScript,
                emergencyRedeemScript
            );
            throw new VerificationException(message);
        }

        if (csvValue <= 0 || csvValue > MAX_CSV_VALUE) {
            String message = String.format(
                "Provided csv value %d must be between 0 and %d",
                csvValue,
                MAX_CSV_VALUE
            );
            logger.warn("[validateLegacyErpRedeemScriptValues] {}", message);
            throw new VerificationException(message);
        }

        FederationUtils.validateScriptSize(redeemScript);
    }

    public static Script createRedeemScript(Script defaultRedeemScript,
                                            Script emergencyRedeemScript,
                                            byte[] serializedCsvValue) {

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        return scriptBuilder
            .op(ScriptOpCodes.OP_NOTIF)
            .addChunks(removeOpCheckMultisig(defaultRedeemScript))
            .op(ScriptOpCodes.OP_ELSE)
            .data(serializedCsvValue)
            .op(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY)
            .op(ScriptOpCodes.OP_DROP)
            .addChunks(removeOpCheckMultisig(emergencyRedeemScript))
            .op(ScriptOpCodes.OP_ENDIF)
            .op(ScriptOpCodes.OP_CHECKMULTISIG)
            .build();
    }

    protected static List<ScriptChunk> removeOpCheckMultisig(Script redeemScript) {
        return redeemScript.getChunks().subList(0, redeemScript.getChunks().size() - 1);
    }
}