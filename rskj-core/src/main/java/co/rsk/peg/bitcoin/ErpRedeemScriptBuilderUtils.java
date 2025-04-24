package co.rsk.peg.bitcoin;

import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_CSV_VALUE;
import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_INTERNAL_REDEEM_SCRIPTS;

import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErpRedeemScriptBuilderUtils {
    private static final Logger logger = LoggerFactory.getLogger(ErpRedeemScriptBuilderUtils.class);
    public static final long MAX_CSV_VALUE = 65_535L; // 2^16 - 1, since bitcoin will interpret up to 16 bits as the CSV value

    private ErpRedeemScriptBuilderUtils() {}

    public static List<ScriptChunk> removeOpCheckMultisig(Script redeemScript) {
        return redeemScript.getChunks().subList(0, redeemScript.getChunks().size() - 1);
    }

    public static void validateRedeemScriptValues(
        Script defaultFederationRedeemScript,
        Script erpFederationRedeemScript,
        Long csvValue
    ) {
        if (!defaultFederationRedeemScript.isSentToMultiSig() || !erpFederationRedeemScript.isSentToMultiSig()) {
            String message = "Provided redeem scripts have an invalid structure, not standard";
            logger.debug(
                "[validateRedeemScriptValues] {}. Default script {}. Emergency script {}",
                message,
                defaultFederationRedeemScript,
                erpFederationRedeemScript
            );
            throw new RedeemScriptCreationException(message, INVALID_INTERNAL_REDEEM_SCRIPTS);
        }

        validateCSVRedeemScriptValue(csvValue);
    }

    public static void validateCSVRedeemScriptValue(Long csvValue) {
        if (csvValue <= 0 || csvValue > MAX_CSV_VALUE) {
            String message = String.format(
                "Provided csv value %d must be larger than 0 and lower than %d",
                csvValue,
                MAX_CSV_VALUE
            );
            logger.warn("[validateRedeemScriptValues] {}", message);
            throw new RedeemScriptCreationException(message, INVALID_CSV_VALUE);
        }
    }
}
