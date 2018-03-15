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
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;
import com.googlecode.jsonrpc4j.RequestInterceptor;

import java.io.IOException;
import java.util.List;

public class JsonRpcMethodFilter implements RequestInterceptor {

    private List<ModuleDescription> modules;

    /**
     * This checks the JSON RPC invoked method against the received list of modules
     *
     * @see co.rsk.rpc.ModuleDescription#methodIsEnable
     *
     * @param modules list of configured modules
     */
    public JsonRpcMethodFilter(List<ModuleDescription> modules) {
        this.modules = modules;
    }

    @Override
    public void interceptRequest(JsonNode node) throws IOException {
        if (node.hasNonNull(JsonRpcBasicServer.METHOD)) {
            checkMethod(node.get(JsonRpcBasicServer.METHOD).asText());
        }
    }

    private void checkMethod(String methodName) throws IOException {
        for (ModuleDescription module: this.modules) {
            if (module.methodIsEnable(methodName)) {
                return;
            }
        }

        throw new IOException("Method not supported: " + methodName);
    }
}
