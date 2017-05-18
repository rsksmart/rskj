package org.ethereum.rpc.exception;

public class JsonRpcUnimplementedMethodException extends RskJsonRpcRequestException{

    public static final Integer ERROR_CODE = -32201;

    public JsonRpcUnimplementedMethodException(String message) {
        super(ERROR_CODE, message);
    }
}
