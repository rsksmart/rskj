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

import co.rsk.config.RskSystemProperties;
import co.rsk.rpc.ModuleDescription;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.googlecode.jsonrpc4j.*;

import io.netty.channel.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError.INTERNAL_ERROR;

@ChannelHandler.Sharable
public class JsonRpcCustomServer extends JsonRpcBasicServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("JsonRpcCustomServer");
    private final RskSystemProperties rskSystemProperties;
    private final List<ModuleDescription> modules;
    private final ObjectMapper mapper;

    public JsonRpcCustomServer(final Object handler, final Class<?> remoteInterface, RskSystemProperties rskSystemProperties) {
        super(new ObjectMapper(), handler, remoteInterface);

        this.rskSystemProperties = rskSystemProperties;
        this.modules = rskSystemProperties.getRpcModules();
        this.mapper = new ObjectMapper();
    }

    private int getTimeoutByModuleAndMethod(String method) {
        if (method.isEmpty()) {
            return this.rskSystemProperties.getRpcTimeout();
        }

        String[] methodParts = method.split("_");
        String moduleName = methodParts[0];
        String methodName = methodParts[1];
        Optional<ModuleDescription> optModule = modules.stream()
                .filter(m -> m.getName().equals(moduleName) && m.getTimeout() > 0)
                .findFirst();

        Optional<Integer> optMethodTimeout = optModule.map(ModuleDescription::getMethodTimeoutMap).map(m -> m.get(methodName));

        return optMethodTimeout.orElseGet(
                () -> optModule.map(ModuleDescription::getTimeout).orElseGet(this.rskSystemProperties::getRpcTimeout)
        );

    }

    private JsonResponse createResponseError(String jsonRpc, Object id, ErrorResolver.JsonError errorObject) {
        return createResponse(jsonRpc, id, null, errorObject);
    }

    private JsonResponse createResponse(String jsonRpc, Object id, JsonNode result, ErrorResolver.JsonError errorObject) {
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
            response.set(RESULT, result);
        }

        return new JsonResponse(response, responseCode);
    }

    @Override
    protected JsonResponse handleJsonNodeRequest(final JsonNode node) throws JsonParseException, JsonMappingException {
        int timeout = this.getTimeoutByModuleAndMethod(
                Optional.ofNullable(node.get("method")).map(JsonNode::asText).orElse("")
        );

        if (node.isObject() && timeout > 0) {
            Set<Exception> exceptions = new HashSet<>();
            JsonResponse response;

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Future<JsonResponse> future = executorService.submit(() -> {
                try {
                    return super.handleJsonNodeRequest(node);
                } catch (Exception e) {
                    exceptions.add(e);
                }

                return null;
            });

            try {
                response = future.get(timeout, TimeUnit.SECONDS);
            } catch (Throwable t) {
                future.cancel(true);
                ErrorResolver.JsonError jsonError = new ErrorResolver.JsonError(INTERNAL_ERROR.code, t.getMessage(), t.getClass().getName());
                response = createResponseError(VERSION, NULL, jsonError);
            }

            if (!exceptions.isEmpty()) {
                Exception exception = exceptions.iterator().next();

                ErrorResolver.JsonError jsonError = new ErrorResolver.JsonError(INTERNAL_ERROR.code, exception.getMessage(), exception.getClass().getName());
                response = createResponseError(VERSION, NULL, jsonError);
            }

            return response;
        }

        return super.handleJsonNodeRequest(node);
    }
}
