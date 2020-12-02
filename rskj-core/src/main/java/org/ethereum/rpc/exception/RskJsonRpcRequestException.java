package org.ethereum.rpc.exception;

public class RskJsonRpcRequestException extends RuntimeException {

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

    public static RskJsonRpcRequestException transactionRevertedExecutionError(String revertReason) {
        return executionError(revertReason);
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

    public static RskJsonRpcRequestException invalidParamError(String message) {
        return new RskJsonRpcRequestException(-32602, message);
    }

    public static RskJsonRpcRequestException invalidParamError(String message, Exception e) {
        return new RskJsonRpcRequestException(-32602, message, e);
    }

    public static RskJsonRpcRequestException unimplemented(String message) {
        return new RskJsonRpcRequestException(-32201, message);
    }

    public static RskJsonRpcRequestException blockNotFound(String message) {
        return new RskJsonRpcRequestException(-32600, message);
    }

    public static RskJsonRpcRequestException stateNotFound(String message) {
        return new RskJsonRpcRequestException(-32600, message);
    }
}
