package co.rsk.peg.bitcoin;

import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_FLYOVER_DERIVATION_HASH;
import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_INTERNAL_REDEEM_SCRIPTS;
import static java.util.Objects.isNull;

import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.crypto.Keccak256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlyoverRedeemScriptBuilderImpl implements FlyoverRedeemScriptBuilder {
    private static final Logger logger = LoggerFactory.getLogger(FlyoverRedeemScriptBuilderImpl.class);

    private FlyoverRedeemScriptBuilderImpl() {}

    public static FlyoverRedeemScriptBuilderImpl builder() {
        return new FlyoverRedeemScriptBuilderImpl();
    }

    @Override
    public Script of(Keccak256 flyoverDerivationHash, Script redeemScript) {
        validateFlyoverDerivationHash(flyoverDerivationHash);
        validateInternalRedeemScript(redeemScript);

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        byte[] flyoverDerivationHashSerialized = flyoverDerivationHash.getBytes();

        return scriptBuilder
            .data(flyoverDerivationHashSerialized)
            .op(ScriptOpCodes.OP_DROP)
            .addChunks(redeemScript.getChunks())
            .build();
    }

    private void validateFlyoverDerivationHash(Keccak256 flyoverDerivationHash) {
        if (isNull(flyoverDerivationHash) || flyoverDerivationHash.equals(Keccak256.ZERO_HASH)) {
            String message = String.format("Provided flyover derivation hash %s is invalid.", flyoverDerivationHash);
            logger.warn("[validateFlyoverDerivationHash] {}", message);
            throw new RedeemScriptCreationException(message, INVALID_FLYOVER_DERIVATION_HASH);
        }
    }

    private void validateInternalRedeemScript(Script internalRedeemScript) {
        if (isNull(internalRedeemScript)) {
            String message = "Provided redeem script is null.";
            logger.warn("[validateRedeemScript] {}", message);
            throw new RedeemScriptCreationException(message, INVALID_INTERNAL_REDEEM_SCRIPTS);
        }

        try {
            // Check it can be parsed, so it has a valid structure
            RedeemScriptParserFactory.get(internalRedeemScript.getChunks());
        } catch (Exception e) {
            String message = "Provided redeem script has an invalid structure.";
            logger.warn("[validateRedeemScript] {}", message);
            throw new RedeemScriptCreationException(message, INVALID_INTERNAL_REDEEM_SCRIPTS);
        }
    }
}
