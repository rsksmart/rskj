package org.ethereum.rpc.validation;

import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class HexIndexValidator {
    private HexIndexValidator() {

    }

    public static boolean isValid(String index) {
        if(!HexUtils.hasHexPrefix(index)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid argument: " + index + ": param should be a hex value string.");
        }

        return true;
    }
}
