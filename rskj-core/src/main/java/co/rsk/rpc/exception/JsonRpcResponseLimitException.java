package co.rsk.rpc.exception;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class JsonRpcResponseLimitException extends RskJsonRpcRequestException {
    public final static int ERROR_CODE = -32011;
    private final static String ERROR_MSG = "Response size limit exceeded";
    private final static String ERROR_MSG_WITH_LIMIT = "Response size limit exceeded. Max response size %d bytes";

    public JsonRpcResponseLimitException() {
        super(ERROR_CODE, ERROR_MSG);
    }

    public JsonRpcResponseLimitException(int  max) {
        super(ERROR_CODE, String.format(ERROR_MSG_WITH_LIMIT,max));
    }
}
