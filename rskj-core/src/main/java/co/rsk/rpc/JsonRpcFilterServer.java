/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.ErrorResolver;
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Created by ajlopez on 19/04/2017.
 */
public class JsonRpcFilterServer extends JsonRpcBasicServer {

    private static final String JSON_RPC_METHOD_FIELD_NAME = "/method";
    private List<ModuleDescription> modules;

    /**
     * Creates the server with a default {@link ObjectMapper} delegating
     * all calls to the given {@code handler} {@link Object} but only
     * methods available on the {@code remoteInterface}.
     * AND enabled in the list of modules
     *
     * @param handler the {@code handler}
     * @param remoteInterface the interface
     * @param modules list of configured modules
     */
    public JsonRpcFilterServer(Object handler, Class<?> remoteInterface, List<ModuleDescription> modules) {
        super(new ObjectMapper(), handler, remoteInterface);

        this.modules = modules;
    }

    @Override
    protected ErrorResolver.JsonError handleJsonNodeRequest(JsonNode node, OutputStream output) throws IOException {
        if (node.hasNonNull(JSON_RPC_METHOD_FIELD_NAME)) {
            checkMethod(node.at(JSON_RPC_METHOD_FIELD_NAME).asText());
        }
        return super.handleJsonNodeRequest(node, output);
    }

    public void checkMethod(String methodName) throws IOException {
        for (ModuleDescription module: this.modules) {
            if (module.methodIsEnable(methodName)) {
                return;
            }
        }

        throw new IOException("Method not supported: " + methodName);
    }
}
