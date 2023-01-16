/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.rpc.netty;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.googlecode.jsonrpc4j.*;

import io.netty.channel.ChannelHandler;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError.INTERNAL_ERROR;

@ChannelHandler.Sharable
public class JsonRpcCustomServer extends JsonRpcBasicServer {

    private final ObjectMapper mapper;
    private final Function<String, Integer> getTimeoutFn;
    private final TimeUnit timeoutUnit;

    public JsonRpcCustomServer(final Object handler, final Class<?> remoteInterface, Function<String, Integer> getTimeoutFn, TimeUnit timeoutUnit) {
        super(new ObjectMapper(), handler, remoteInterface);

        this.getTimeoutFn = getTimeoutFn;
        this.timeoutUnit = timeoutUnit;
        this.mapper = new ObjectMapper();
    }

    private JsonResponse createResponseError(String jsonRpc, Object id, ErrorResolver.JsonError errorObject) {
        ObjectNode response = mapper.createObjectNode();
        response.put(JSONRPC, jsonRpc);

        response.put(ID, (String) id);

        int responseCode = ErrorResolver.JsonError.OK.code;
        if (errorObject != null) {
            ObjectNode error = mapper.createObjectNode();
            error.put(ERROR_CODE, errorObject.code);
            error.put(ERROR_MESSAGE, errorObject.message);
            if (errorObject.data != null) {
                error.set(DATA, mapper.valueToTree(errorObject.data));
            }
            responseCode = errorObject.code;
            response.set(ERROR, error);
        } else {
            response.set(RESULT, null);
        }

        return new JsonResponse(response, responseCode);
    }

    @Override
    protected JsonResponse handleJsonNodeRequest(final JsonNode node) throws JsonParseException, JsonMappingException {
        int timeout = getTimeoutFn.apply(
                Optional.ofNullable(node.get("method")).map(JsonNode::asText).orElse("")
        );

        if (node.isObject() && timeout > 0) {
            JsonResponse response;

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Future<JsonResponse> future = executorService.submit(() -> super.handleJsonNodeRequest(node));

            try {
                response = future.get(timeout, timeoutUnit);
            } catch (Throwable t) {
                future.cancel(true);
                ErrorResolver.JsonError jsonError = new ErrorResolver.JsonError(INTERNAL_ERROR.code, t.getMessage(), t.getClass().getName());
                response = createResponseError(VERSION, NULL, jsonError);
            }

            return response;
        }

        return super.handleJsonNodeRequest(node);
    }
}
