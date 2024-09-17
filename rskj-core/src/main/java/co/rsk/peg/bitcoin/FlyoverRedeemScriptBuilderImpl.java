package co.rsk.peg.bitcoin;

import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_FLYOVER_DERIVATION_HASH;
import static java.util.Objects.isNull;

import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.crypto.Keccak256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlyoverRedeemScriptBuilderImpl implements FlyoverRedeemScriptBuilder {
    private static final Logger logger = LoggerFactory.getLogger(FlyoverRedeemScriptBuilderImpl.class);

    @Override
    public Script of(Keccak256 flyoverDerivationHash, Script redeemScript) {
        validateFlyoverDerivationHash(flyoverDerivationHash);

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
}
