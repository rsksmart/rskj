package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.script.Script;

import static co.rsk.peg.bitcoin.ScriptCreationException.Reason.ABOVE_MAX_SCRIPT_ELEMENT_SIZE;

public class ScriptValidations {
    private ScriptValidations() {
    }

    public static void validateScriptSigElementSize(Script script) throws ScriptCreationException {
        // Check if the size of the script does not exceed the maximum size allowed
        int bytesFromScript = script.getProgram().length;
        if (bytesFromScript > Script.MAX_SCRIPT_ELEMENT_SIZE) {
            String message = String.format("The script size is %d, that is above the maximum allowed.",
                bytesFromScript
            );
            throw new ScriptCreationException(message, ABOVE_MAX_SCRIPT_ELEMENT_SIZE);
        }
    }

    public static void validateWitnessScriptSize(Script redeemScript, int numberOfSignaturesRequired) throws ScriptCreationException {
        // Check if the size of the script does not exceed the maximum size allowed
        int bytesFromScript = redeemScript.getProgram().length;
        int bytesFromSigning = numberOfSignaturesRequired * Script.SIG_SIZE;
        int bytesForOpCheckMultisigBug = 1;
        int bytesForOpNotif = 1;
        if (bytesFromScript + bytesFromSigning + bytesForOpCheckMultisigBug + bytesForOpNotif > Script.MAX_STANDARD_P2WSH_SCRIPT_SIZE) {
            String message = String.format("The witness script size is %d, that is above the maximum allowed.",
                bytesFromScript
            );
            throw new ScriptCreationException(message, ABOVE_MAX_SCRIPT_ELEMENT_SIZE);
        }
    }
}
