package org.ethereum.rpc.validation;

import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class HexValueValidator {
    private HexValueValidator(){}

    private static boolean isValid(String input){
        if (!HexUtils.isHex(input) && !HexUtils.isHexWithPrefix(input)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid argument: " + input + ": param should be a hex value string.");
        }
        return true;
    }

    public static String getValidHex(String input) {
        isValid(input);

        if (HexUtils.isHex(input) && !HexUtils.hasHexPrefix(input)) {
             return "0x" + input;
        }
        return input;
    }

}
