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

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 20/04/2017.
 */
public class JsonRpcFilterServerTest {
    @Test
    public void checkModuleNames() {
        JsonRpcFilterServer server = new JsonRpcFilterServer(null, null, getModules());

        try {
            server.checkMethod("evm_snapshot");
            server.checkMethod("evm_revert");
        }
        catch (IOException ex) {
            Assert.fail();
        }

        try {
            server.checkMethod("evm_reset");
            Assert.fail();
        }
        catch (IOException ex) {
        }

        try {
            server.checkMethod("evm_increaseTime");
            Assert.fail();
        }
        catch (IOException ex) {
        }

        try {
            server.checkMethod("eth_getBlock");
            Assert.fail();
        }
        catch (IOException ex) {
        }
    }

    private static List<ModuleDescription> getModules() {
        List<String> enabledMethods = new ArrayList<>();
        enabledMethods.add("evm_snapshot");
        enabledMethods.add("evm_revert");
        enabledMethods.add("evm_reset");

        List<String> disabledMethods = new ArrayList<>();
        disabledMethods.add("evm_reset");
        disabledMethods.add("evm_increaseTime");

        ModuleDescription enabledModule = new ModuleDescription("evm", "1.0", true, enabledMethods, disabledMethods);

        List<ModuleDescription> modules = new ArrayList<>();

        ModuleDescription disabledModule = new ModuleDescription("eth", "1.0", false, null, null);

        modules.add(enabledModule);
        modules.add(disabledModule);

        return modules;
    }
}
