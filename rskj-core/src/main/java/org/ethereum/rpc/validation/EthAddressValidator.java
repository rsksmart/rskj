package org.ethereum.rpc.validation;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.util.regex.Pattern;

public class EthAddressValidator {
    private static final String ETH_ADDRESS_PATTERN = "^0x[a-fA-F0-9]{40}$";
    private static final Pattern PATTERN = Pattern.compile(ETH_ADDRESS_PATTERN, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final String MESSAGE = "Invalid address: ";

    private EthAddressValidator() { }

    public static boolean isValid(String parameter){
        if (!PATTERN.matcher(parameter).matches()) {
            throw RskJsonRpcRequestException.invalidParamError(MESSAGE + parameter);
        }
        return true;
    }
}
