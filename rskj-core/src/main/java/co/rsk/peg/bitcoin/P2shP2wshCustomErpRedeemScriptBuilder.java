package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static co.rsk.peg.bitcoin.ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE;
import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_CSV_VALUE;

public class P2shP2wshCustomErpRedeemScriptBuilder implements ErpRedeemScriptBuilder {
    private static final Logger logger = LoggerFactory.getLogger(P2shP2wshCustomErpRedeemScriptBuilder.class);

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
        logger.debug("[of] Creating the redeem script from the scripts");

        Script customRedeemScript = ScriptBuilder.createCustomRedeemScript(defaultThreshold, defaultPublicKeys);
        Script emergencyRedeemScript = ScriptBuilder.createRedeemScript(emergencyThreshold, emergencyPublicKeys);
        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);

        if (csvValue <= 0 || csvValue > MAX_CSV_VALUE) {
            String message = String.format(
                "Provided csv value %d must be larger than 0 and lower than %d",
                csvValue,
                MAX_CSV_VALUE
            );
            logger.warn("[of] {}", message);
            throw new RedeemScriptCreationException(message, INVALID_CSV_VALUE);
        }

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        return scriptBuilder
            .op(ScriptOpCodes.OP_NOTIF)
            .addChunks(customRedeemScript.getChunks())
            .op(ScriptOpCodes.OP_ELSE)
            .data(serializedCsvValue)
            .op(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY)
            .op(ScriptOpCodes.OP_DROP)
            .addChunks(emergencyRedeemScript.getChunks())
            .op(ScriptOpCodes.OP_ENDIF)
            .build();
    }
}


