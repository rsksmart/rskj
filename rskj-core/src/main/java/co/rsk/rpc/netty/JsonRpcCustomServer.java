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
import com.googlecode.jsonrpc4j.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JsonRpcCustomServer extends JsonRpcBasicServer {
    private final List<ModuleDescription> modules;

    public JsonRpcCustomServer(final Object handler, final Class<?> remoteInterface, List<ModuleDescription> modules) {
        super(new ObjectMapper(), handler, remoteInterface);

        this.modules = new ArrayList<>(modules);
    }

    @Override
    protected JsonResponse handleJsonNodeRequest(final JsonNode node) throws JsonParseException, JsonMappingException {
        if (!node.isObject()) {
            return super.handleJsonNodeRequest(node);
        }

        String method = Optional.ofNullable(node.get("method")).map(JsonNode::asText).orElse("");

        String[] methodParts = method.split("_");

        if (methodParts.length < 2) {
            return super.handleJsonNodeRequest(node);
        }

        String moduleName = methodParts[0];

        Optional<ModuleDescription> optModule = modules.stream()
                .filter(m -> m.getName().equals(moduleName))
                .findFirst();

        long timeout = optModule
                .map(m -> m.getTimeout(method))
                .orElse(0L);

        JsonResponse response;

        if (timeout <= 0) {
            response = super.handleJsonNodeRequest(node);
            ExecTimeoutContext.checkIfExpired();

            return response;
        }

        try (ExecTimeoutContext ignored = ExecTimeoutContext.create(timeout)) {
            response = super.handleJsonNodeRequest(node);
            ExecTimeoutContext.checkIfExpired();
        }

        return response;
    }
}
