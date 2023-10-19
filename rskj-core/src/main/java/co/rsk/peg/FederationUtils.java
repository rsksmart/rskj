package co.rsk.peg;

import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.bitcoin.Standardness;

import static co.rsk.peg.FederationCreationException.Reason.ABOVE_MAX_SCRIPT_ELEMENT_SIZE;

public class FederationUtils {
    private FederationUtils() {
    }

    public static void isScriptSizeValid(Script script) throws FederationCreationException {
        // Check if the size of the script does not exceed the maximum size allowed
        int bytesFromScript = script.getProgram().length;
        if (bytesFromScript > Standardness.MAX_SCRIPT_ELEMENT_SIZE) {
            String message = String.format( "The script size is %d, that is above the maximum allowed.",
                bytesFromScript
            );
            throw new FederationCreationException(message, ABOVE_MAX_SCRIPT_ELEMENT_SIZE);
        }
    }
}
