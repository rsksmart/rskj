package org.ethereum.rpc.exception;

public class LockAccountException extends RskJsonRpcRequestException{

    public static final Integer ERROR_CODE = -32603;

    public LockAccountException(String message) {
        super(ERROR_CODE, message);
    }
}
