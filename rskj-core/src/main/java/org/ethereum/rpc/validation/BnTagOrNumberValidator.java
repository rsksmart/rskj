package org.ethereum.rpc.validation;

import co.rsk.util.HexUtils;
import org.ethereum.core.genesis.BlockTag;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class BnTagOrNumberValidator {
    private BnTagOrNumberValidator() {

    }

    public static boolean isValid(String parameter) {
        if (parameter == null) {
            throw RskJsonRpcRequestException.invalidParamError("Cannot process null parameter");
        }

        parameter = parameter.toLowerCase();

        if (BlockTag.fromString(parameter) != null) {
            return true;
        }

        if (HexUtils.isHexWithPrefix(parameter)) {
            return true;
        }

        throw RskJsonRpcRequestException.invalidParamError("Invalid block number: " + parameter);
    }
}
