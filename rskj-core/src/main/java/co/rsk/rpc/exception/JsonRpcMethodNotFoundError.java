package co.rsk.rpc.exception;

import co.rsk.jsonrpc.JsonRpcError;

public class JsonRpcMethodNotFoundError extends JsonRpcThrowableError {

    private static final long serialVersionUID = 2919587893031838269L;
    private static final String MSG = "method not found";

    public JsonRpcMethodNotFoundError(Object requestId) {
        super(MSG, requestId);
    }

    @Override
    public JsonRpcError getErrorResponse() {
        return new JsonRpcError(JsonRpcError.METHOD_NOT_FOUND, getMessage());
    }
}
