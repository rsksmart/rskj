package co.rsk.peg;

import co.rsk.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static co.rsk.peg.ScriptCreationException.Reason.ABOVE_MAX_SCRIPT_ELEMENT_SIZE;

public class ScriptValidations {
    private ScriptValidations() {
    }
    public static void validateScriptSize(Script script) throws ErpRedeemScriptBuilderCreationException {
        // Check if the size of the script does not exceed the maximum size allowed
        int bytesFromScript = script.getProgram().length;
        if (bytesFromScript > Script.MAX_SCRIPT_ELEMENT_SIZE) {
            String message = String.format("The script size is %d, that is above the maximum allowed.",
                bytesFromScript
            );
            throw new ScriptCreationException(message, ABOVE_MAX_SCRIPT_ELEMENT_SIZE);
        }
    }
}
