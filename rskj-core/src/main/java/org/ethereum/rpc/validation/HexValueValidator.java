package org.ethereum.rpc.validation;

import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class HexValueValidator {
    private HexValueValidator(){}

    public static boolean isValid(String hexNumber){
        if (HexUtils.isHex(hexNumber)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid block number: " + hexNumber);
        }
        return true;
    }

}
