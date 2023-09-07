package org.ethereum.rpc.parameters;

import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class HexStringParam {
    public void validateHexString(String hexString) {
        if (!HexUtils.hasHexPrefix(hexString) || !HexUtils.isHex(hexString,2)) {
            throw RskJsonRpcRequestException.invalidParamError("Invalid argument \"" + hexString + "\": param should be a hex value string.");
        }
    }
}
