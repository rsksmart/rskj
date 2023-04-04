package co.rsk.rpc.exception;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError.INTERNAL_ERROR;

public class JsonRpcTimeoutError extends Error {
    public static final int ERROR_CODE = INTERNAL_ERROR.code;

    public JsonRpcTimeoutError(String  msg) {
        super(msg);
    }

}
