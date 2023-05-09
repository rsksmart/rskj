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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.googlecode.jsonrpc4j.RequestInterceptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class JsonRpcMethodFilterTest {

    private static JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;

    @Test
    void checkModuleNames() throws Throwable {
        RequestInterceptor jsonRpcMethodFilter = new JsonRpcMethodFilter(getModules());

        jsonRpcMethodFilter.interceptRequest(getMethodInvocation("evm_snapshot"));
        jsonRpcMethodFilter.interceptRequest(getMethodInvocation("evm_revert"));

        try {
            jsonRpcMethodFilter.interceptRequest(getMethodInvocation("evm_reset"));
            Assertions.fail("evm_reset is enabled AND disabled, disabled take precedence");
        } catch (IOException ex) {
            // expected fail
        }

        try {
            jsonRpcMethodFilter.interceptRequest(getMethodInvocation("evm_increaseTime"));
            Assertions.fail("evm_increaseTime is disabled");
        } catch (IOException ex) {
            // expected fail
        }

        try {
            jsonRpcMethodFilter.interceptRequest(getMethodInvocation("eth_getBlock"));
            Assertions.fail("The whole eth namespace is disabled");
        } catch (IOException ex) {
            // expected fail
        }
    }

    private JsonNode getMethodInvocation(String methodName) {
        Map<String, JsonNode> errorProperties = new HashMap<>();
        errorProperties.put("method", JSON_NODE_FACTORY.textNode(methodName));
        return JSON_NODE_FACTORY.objectNode().setAll(errorProperties);
    }

    private static List<ModuleDescription> getModules() {
        List<String> enabledMethods = new ArrayList<>();
        enabledMethods.add("evm_snapshot");
        enabledMethods.add("evm_revert");
        enabledMethods.add("evm_reset");

        List<String> disabledMethods = new ArrayList<>();
        disabledMethods.add("evm_reset");
        disabledMethods.add("evm_increaseTime");

        ModuleDescription enabledModule = new ModuleDescription("evm", "1.0", true, enabledMethods, disabledMethods, 0, new HashMap<>());

        List<ModuleDescription> modules = new ArrayList<>();

        ModuleDescription disabledModule = new ModuleDescription("eth", "1.0", false, null, null, 0, new HashMap<>());

        modules.add(enabledModule);
        modules.add(disabledModule);

        return modules;
    }
}
