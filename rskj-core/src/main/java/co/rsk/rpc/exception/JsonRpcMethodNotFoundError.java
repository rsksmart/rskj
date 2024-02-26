package co.rsk.rpc.exception;

import co.rsk.jsonrpc.JsonRpcError;

public class JsonRpcMethodNotFoundError extends JsonRpcThrowableError {
    public JsonRpcMethodNotFoundError(String methodName) {
        super("The method " + methodName + " does not exist/is not available");
    }

    @Override
    public JsonRpcError getErrorResponse() {
        return new JsonRpcError(JsonRpcError.METHOD_NOT_FOUND, getMessage());
    }
}
