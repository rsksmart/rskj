package org.ethereum.rpc.exception;

import co.rsk.core.exception.InvalidRskAddressException;
import co.rsk.jsonrpc.JsonRpcError;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.googlecode.jsonrpc4j.ErrorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

/**
 * Created by mario on 17/10/2016.
 */
public class RskErrorResolver implements ErrorResolver {

    private static final Logger logger = LoggerFactory.getLogger("web3");

    @Override
    public JsonError resolveError(Throwable t, Method method, List<JsonNode> arguments) {
        JsonError error;

        if (t instanceof InvalidRskAddressException) {
            error = new JsonError(
                    JsonRpcError.INVALID_PARAMS,
                    "invalid argument 0: hex string has length " + arguments.get(0).asText().replace("0x", "").length() + ", want 40 for RSK address",
                    null);
        } else if (t instanceof RskJsonRpcRequestException) {
            RskJsonRpcRequestException rskJsonRpcRequestException = (RskJsonRpcRequestException) t;
            byte[] revertData = rskJsonRpcRequestException.getRevertData();
            String errorDataHexString = "0x" + printHexBinary(revertData == null ? new byte[]{} : revertData).toLowerCase();
            error = new JsonError(rskJsonRpcRequestException.getCode(), t.getMessage(), errorDataHexString);
        } else if (t instanceof InvalidFormatException) {
            error = new JsonError(JsonRpcError.INTERNAL_ERROR, "Internal server error, probably due to invalid parameter type", null);
        } else if (t instanceof UnrecognizedPropertyException) {
            error = new JsonError(
                    JsonRpcError.INVALID_PARAMS,
                    getExceptionMessage((UnrecognizedPropertyException) t),
                    null);
        } else if (t instanceof JsonMappingException && t.getMessage().contains("Can not construct instance")) {
            error = new JsonError(
                    JsonRpcError.INVALID_PARAMS,
                    "invalid argument 0: json: cannot unmarshal string into value of input",
                    null);
        } else if (t instanceof UnsupportedOperationException || (t.getMessage() != null && t.getMessage().toLowerCase().contains("method not supported"))) {
            error = new JsonError(
                    JsonRpcError.METHOD_NOT_FOUND,
                    "the method " + method.getName() + " does not exist/is not available",
                    null);
        } else {
            logger.error("JsonRPC error when for method {} with arguments {}", method, arguments, t);
            error = new JsonError(JsonRpcError.INTERNAL_ERROR, "Internal server error", null);
        }
        return error;
    }

    private static String getExceptionMessage(UnrecognizedPropertyException ex) {
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
