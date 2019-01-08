package org.ethereum.rpc.exception;

/**
 * Created by mario on 17/10/2016.
 */
public class RskJsonRpcRequestException extends RuntimeException{

    private final Integer code;

    protected RskJsonRpcRequestException(Integer code, String message, Exception e) {
        super(message, e);
        this.code = code;
    }

    public RskJsonRpcRequestException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public static RskJsonRpcRequestException transactionRevertedExecutionError() {
        return executionError("transaction reverted");
    }

    public static RskJsonRpcRequestException unknownError(String message) {
        return new RskJsonRpcRequestException(-32009, message);
    }

    private static RskJsonRpcRequestException executionError(String message) {
        return new RskJsonRpcRequestException(-32015, String.format("VM execution error: %s", message));
    }

    public static RskJsonRpcRequestException transactionError(String message) {
        return new RskJsonRpcRequestException(-32010, message);
    }

}
