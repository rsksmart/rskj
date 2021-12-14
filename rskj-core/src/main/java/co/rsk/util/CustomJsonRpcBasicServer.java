package co.rsk.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.jsonrpc4j.ErrorResolver;
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;

import java.io.IOException;
import java.io.OutputStream;

public class CustomJsonRpcBasicServer extends JsonRpcBasicServer {

    public CustomJsonRpcBasicServer(final Object handler, final Class<?> remoteInterface) {
        super(handler, remoteInterface);
    }

    @Override
    protected ErrorResolver.JsonError handleJsonNodeRequest(final JsonNode node, final OutputStream output) throws IOException {
        validateRequestContent(node);

        return super.handleJsonNodeRequest(node, output);
    }

    private void validateRequestContent(JsonNode jsonNode) {
        if (jsonNode.get("id") == null) {
            throw new IllegalArgumentException("missing request id");
        }
    }
}
