package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.script.Script;

import static co.rsk.peg.bitcoin.ScriptCreationException.Reason.ABOVE_MAX_SCRIPTSIG_ELEMENT_SIZE;
import static co.rsk.peg.bitcoin.ScriptCreationException.Reason.ABOVE_MAX_SCRIPT_FOR_WITNESS_SIZE;

public class ScriptValidations {

    public static final int FLYOVER_SCRIPT_BYTES = 34;
    public static final long MAX_P2SH_REDEEM_SCRIPT_SIZE = Script.MAX_SCRIPT_ELEMENT_SIZE - FLYOVER_SCRIPT_BYTES;
    public static final long MAX_P2WSH_REDEEM_SCRIPT_SIZE = Script.MAX_STANDARD_P2WSH_SCRIPT_SIZE - FLYOVER_SCRIPT_BYTES;

    private ScriptValidations() {
    }

    public static void validateSizeOfRedeemScriptForScriptSig(Script redeemScript) throws ScriptCreationException {
        int bytesCountFromRedeemScript = redeemScript.getProgram().length;
        if (bytesCountFromRedeemScript > MAX_P2SH_REDEEM_SCRIPT_SIZE) {
            String message = String.format("The size of the redeem script for scriptSig is %d, which is above the maximum allowed (%s).",
                bytesCountFromRedeemScript,
                MAX_P2SH_REDEEM_SCRIPT_SIZE
            );
            throw new ScriptCreationException(message, ABOVE_MAX_SCRIPTSIG_ELEMENT_SIZE);
        }
    }

    public static void validateSizeOfRedeemScriptForWitness(Script redeemScript) throws ScriptCreationException {
        int bytesCountFromRedeemScript = redeemScript.getProgram().length;
        if (bytesCountFromRedeemScript > MAX_P2WSH_REDEEM_SCRIPT_SIZE) {
            String message = String.format("The size of the redeem script for witness is %d, which is above the maximum allowed (%s).",
                bytesCountFromRedeemScript,
                MAX_P2WSH_REDEEM_SCRIPT_SIZE
            );
            throw new ScriptCreationException(message, ABOVE_MAX_SCRIPT_FOR_WITNESS_SIZE);
        }
    }
}
