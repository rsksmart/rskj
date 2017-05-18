package org.ethereum.rpc.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.googlecode.jsonrpc4j.ErrorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by mario on 17/10/2016.
 */
public class RskErrorResolver implements ErrorResolver {

    private static final Logger logger = LoggerFactory.getLogger("web3");

    @Override
    public JsonError resolveError(Throwable t, Method method, List<JsonNode> arguments) {
        JsonError error = null;
        if(t instanceof  RskJsonRpcRequestException) {
            error =  new JsonError(((RskJsonRpcRequestException) t).getCode(), t.getMessage(), null);
        } else if (t instanceof InvalidFormatException) {
            error = new JsonError(-32603, "Internal server error, probably due to invalid parameter type", null);
        } else {
            logger.error("JsonRPC error when for method " + method + " with arguments " + arguments, t);
            error = new JsonError(-32603, "Internal server error", null);
        }
        return error;
    }
}
