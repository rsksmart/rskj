package co.rsk.peg.utils;

import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.bitcoin.Standardness;

public class FederationUtils {

    public static boolean isScriptSizeValid(Script script) {
        // we have to check if the size of the script does not exceed the maximum size allowed
        int bytesFromScript = script.getProgram().length;
        return bytesFromScript <= Standardness.MAX_SCRIPT_ELEMENT_SIZE;
    }

    private FederationUtils() {
    }
}
