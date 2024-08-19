package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.crypto.Keccak256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static co.rsk.peg.bitcoin.FlyoverRedeemScriptCreationException.Reason.INVALID_FLYOVER_DERIVATION_HASH;
import static java.util.Objects.isNull;

public class FlyoverRedeemScriptBuilderImpl implements FlyoverRedeemScriptBuilder {
    private static final Logger logger = LoggerFactory.getLogger(FlyoverRedeemScriptBuilderImpl.class);
    private static final Keccak256 zeroHash = Keccak256.ZERO_HASH;
    private static final int OP_DROP_CODE = 117;

    public Script addFlyoverDerivationHashToRedeemScript(Keccak256 flyoverDerivationHash, Script redeemScript) {
        validateFlyoverDerivationHash(flyoverDerivationHash);

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        byte[] flyoverDerivationHashSerialized = flyoverDerivationHash.getBytes();
        return scriptBuilder
            .data(flyoverDerivationHashSerialized)
            .op(OP_DROP_CODE)
            .addChunks(redeemScript.getChunks())
            .build();
    }

    private void validateFlyoverDerivationHash(Keccak256 flyoverDerivationHash) {
        if (isNull(flyoverDerivationHash) || flyoverDerivationHash.equals(zeroHash)) {
            String message = "Provided flyover derivation hash is invalid.";
            logger.warn("[validateFlyoverDerivationHash] {} {}", message, flyoverDerivationHash);
            throw new FlyoverRedeemScriptCreationException(message, INVALID_FLYOVER_DERIVATION_HASH);
        }
    }
}
