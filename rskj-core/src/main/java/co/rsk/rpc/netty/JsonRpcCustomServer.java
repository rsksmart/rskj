/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
import co.rsk.rpc.exception.JsonRpcMethodNotFoundError;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.JsonResponse;
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class JsonRpcCustomServer extends JsonRpcBasicServer {
    private final List<ModuleDescription> modules;
    private final Set<String> methodNames;

    public JsonRpcCustomServer(final Object handler, final Class<?> remoteInterface, List<ModuleDescription> modules) {
        super(new ObjectMapper(), handler, remoteInterface);
        this.modules = new ArrayList<>(modules);
        this.methodNames = extractMethodNames(remoteInterface);
    }

    @Override
    protected JsonResponse handleJsonNodeRequest(final JsonNode node) throws JsonParseException, JsonMappingException {
        if (!node.isObject()) {
            return super.handleJsonNodeRequest(node);
        }

        String method = Optional.ofNullable(node.get("method")).map(JsonNode::asText).orElse("");

        if(!methodNames.contains(method)) {
            throw new JsonRpcMethodNotFoundError(method);
        }

        String[] methodParts = method.split("_");
        JsonResponse response;
        if (methodParts.length >= 2) {
            String moduleName = methodParts[0];
            String methodName = methodParts[1];

            long timeout = getTimeout(moduleName, methodName);

            if (timeout <= 0) {
                response = super.handleJsonNodeRequest(node);
                ExecTimeoutContext.checkIfExpired();
            } else {
                try (ExecTimeoutContext ignored = ExecTimeoutContext.create(timeout)) {
                    response = super.handleJsonNodeRequest(node);
                    ExecTimeoutContext.checkIfExpired();
                }
            }
        } else {
            response = super.handleJsonNodeRequest(node);
            ExecTimeoutContext.checkIfExpired();
        }

        ResponseSizeLimitContext.addResponse(response.getResponse());
        return response;
    }

    private long getTimeout(String moduleName, String methodName) {
        ModuleDescription moduleDescription = modules.stream()
                .filter(m -> m.getName().equals(moduleName))
                .findFirst()
                .orElse(null);
        if (moduleDescription == null) {
            return 0;
        }
        return moduleDescription.getTimeout(methodName);
    }

    private Set<String> extractMethodNames(Class<?> remoteInterface) {
        if (remoteInterface == null) {
            return Collections.emptySet();
        }

        return Arrays.stream(remoteInterface.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
