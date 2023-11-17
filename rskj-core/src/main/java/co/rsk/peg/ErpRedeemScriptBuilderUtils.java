package co.rsk.peg;

import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static co.rsk.peg.ErpRedeemScriptBuilderCreationException.Reason.INVALID_CSV_VALUE;

public class ErpRedeemScriptBuilderUtils {
    private ErpRedeemScriptBuilderUtils() {
    }
    private static final Logger logger = LoggerFactory.getLogger(ErpRedeemScriptBuilderUtils.class);
    private static final long MAX_CSV_VALUE = 65535L; // 2^16 - 1, since bitcoin will interpret up to 16 bits as the CSV value


    static void validateRedeemScriptValues(
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
            throw new VerificationException(message);
        }

        if (csvValue <= 0 || csvValue > MAX_CSV_VALUE) {
            String message = String.format(
                "Provided csv value %d must be larger than 0 and lower than %d",
                csvValue,
                MAX_CSV_VALUE
            );
            logger.warn("[validateRedeemScriptValues] {}", message);
            throw new ErpRedeemScriptBuilderCreationException(message, INVALID_CSV_VALUE);
        }
    }
}
