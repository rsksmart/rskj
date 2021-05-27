package org.ethereum.rpc.exception;

import co.rsk.jsonrpc.JsonRpcError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.googlecode.jsonrpc4j.ErrorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
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
            error = new JsonError(JsonRpcError.INTERNAL_ERROR, "Internal server error, probably due to invalid parameter type", null);
        } else if (t instanceof UnrecognizedPropertyException) {
            error = new JsonError(
                    JsonRpcError.INVALID_PARAMS,
                    getExceptionMessage((UnrecognizedPropertyException) t),
                    null);
        } else {
            logger.error("JsonRPC error when for method {} with arguments {}", method, arguments, t);
            error = new JsonError(JsonRpcError.INTERNAL_ERROR, "Internal server error", null);
        }
        return error;
    }

    private String getExceptionMessage(UnrecognizedPropertyException ex) {
        if (ex.getPropertyName() == null || ex.getKnownPropertyIds() == null) return "Invalid parameters";

        StringBuilder stringBuilder = new StringBuilder("Unrecognized field \"");
        stringBuilder.append(ex.getPropertyName());
        stringBuilder.append("\" (");
        stringBuilder.append(ex.getKnownPropertyIds().size());
        stringBuilder.append(" known properties: [");

        Iterator<Object> iterator = ex.getKnownPropertyIds().iterator();
        while (iterator.hasNext()) {
            stringBuilder.append("\"");
            stringBuilder.append(iterator.next());
            stringBuilder.append("\"");
            if (iterator.hasNext()) {
                stringBuilder.append(", ");
            }
        }

        stringBuilder.append("])");

        return stringBuilder.toString();
    }
}
