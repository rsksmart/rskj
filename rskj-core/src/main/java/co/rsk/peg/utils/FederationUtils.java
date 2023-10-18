package co.rsk.peg.utils;

import co.rsk.bitcoinj.script.Script;
import co.rsk.rules.Standardness;

public class FederationUtils {

    public static boolean isRedeemScriptSizeValid(Script redeemScript) {
        // we have to check if the size of every script inside the scriptSig is not above the maximum
        // this scriptSig contains the signatures, the redeem script and some other bytes
        // so it is ok to just check the redeem script size

        int bytesFromRedeemScript = redeemScript.getProgram().length;
        return bytesFromRedeemScript <= Standardness.MAX_SCRIPT_ELEMENT_SIZE;
    }

    private FederationUtils() {
    }
}
