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

import co.rsk.rpc.ModuleDescription;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.googlecode.jsonrpc4j.*;

import io.netty.channel.ChannelHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError.INTERNAL_ERROR;

@ChannelHandler.Sharable
public class JsonRpcCustomServer extends JsonRpcBasicServer {

    private final ObjectMapper mapper;
    private final List<ModuleDescription> modules;
    private final int defaultTimeout;

    public JsonRpcCustomServer(final Object handler, final Class<?> remoteInterface, List<ModuleDescription> modules, int defaultTimeout) {
        super(new ObjectMapper(), handler, remoteInterface);

        this.modules = new ArrayList<>(modules);
        this.defaultTimeout = defaultTimeout;
        this.mapper = new ObjectMapper();
    }

    private JsonResponse customCreateResponseError(String jsonRpc, Object id, ErrorResolver.JsonError errorObject) {
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
        String method = Optional.ofNullable(node.get("method")).map(JsonNode::asText).orElse("");

        String[] methodParts = method.split("_");

        String moduleName = methodParts[0];
        String methodName = methodParts[1];

        Optional<ModuleDescription> optModule = modules.stream()
                .filter(m -> m.getName().equals(moduleName))
                .findFirst();

        int timeout = optModule
                .map(m -> m.getTimeout(methodName, defaultTimeout))
                .orElse(defaultTimeout);

        if (!node.isObject() || timeout <= 0) {
            return super.handleJsonNodeRequest(node);
        }

        JsonResponse response;

        try (ExecTimeoutContext ignored = ExecTimeoutContext.create(timeout)) {
            response = super.handleJsonNodeRequest(node);
            ExecTimeoutContext.checkIfExpired();
        } catch (ExecTimeoutContext.TimeoutException e) {
            ErrorResolver.JsonError jsonError = new ErrorResolver.JsonError(INTERNAL_ERROR.code, e.getMessage(), e.getClass().getName());
            response = customCreateResponseError(VERSION, NULL, jsonError);
        }

        return response;
    }
}
