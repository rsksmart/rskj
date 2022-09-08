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

import co.rsk.config.TestSystemProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 19/04/2017.
 */
public class ModuleDescriptionTest {
    @Test
    public void createWithInitialData() {
        ModuleDescription description = new ModuleDescription("evm", "1.0", true, null, null);

        Assertions.assertEquals("evm", description.getName());
        Assertions.assertEquals("1.0", description.getVersion());

        Assertions.assertTrue(description.isEnabled());

        Assertions.assertNotNull(description.getEnabledMethods());
        Assertions.assertTrue(description.getEnabledMethods().isEmpty());

        Assertions.assertNotNull(description.getDisabledMethods());
        Assertions.assertTrue(description.getDisabledMethods().isEmpty());
    }

    @Test
    public void createWithEnabledAndDisabledMethods() {
        List<String> enabledMethods = new ArrayList<>();
        enabledMethods.add("evm_snapshot");
        enabledMethods.add("evm_revert");

        List<String> disabledMethods = new ArrayList<>();
        disabledMethods.add("evm_reset");
        disabledMethods.add("evm_increaseTime");

        ModuleDescription description = new ModuleDescription("evm", "1.0", true, enabledMethods, disabledMethods);

        Assertions.assertEquals("evm", description.getName());
        Assertions.assertEquals("1.0", description.getVersion());

        Assertions.assertTrue(description.isEnabled());

        Assertions.assertNotNull(description.getEnabledMethods());
        Assertions.assertFalse(description.getEnabledMethods().isEmpty());
        Assertions.assertEquals(2, description.getEnabledMethods().size());
        Assertions.assertTrue(description.getEnabledMethods().contains("evm_snapshot"));
        Assertions.assertTrue(description.getEnabledMethods().contains("evm_revert"));

        Assertions.assertNotNull(description.getDisabledMethods());
        Assertions.assertFalse(description.getDisabledMethods().isEmpty());
        Assertions.assertEquals(2, description.getDisabledMethods().size());
        Assertions.assertTrue(description.getDisabledMethods().contains("evm_reset"));
        Assertions.assertTrue(description.getDisabledMethods().contains("evm_increaseTime"));
    }

    @Test
    public void methodIsInModule() {
        ModuleDescription description = new ModuleDescription("evm", "1.0", true, null, null);

        Assertions.assertTrue(description.methodIsInModule("evm_snapshot"));
        Assertions.assertTrue(description.methodIsInModule("evm_do"));

        Assertions.assertFalse(description.methodIsInModule("eth_getBlock"));
        Assertions.assertFalse(description.methodIsInModule("eth"));
        Assertions.assertFalse(description.methodIsInModule("evm"));
        Assertions.assertFalse(description.methodIsInModule("evm2"));
        Assertions.assertFalse(description.methodIsInModule("evmsnapshot"));
        Assertions.assertFalse(description.methodIsInModule(null));
    }

    @Test
    public void methodIsEnabledWhenEmptyNameList() {
        ModuleDescription description = new ModuleDescription("evm", "1.0", true, null, null);

        Assertions.assertTrue(description.methodIsEnable("evm_snapshot"));
        Assertions.assertTrue(description.methodIsEnable("evm_do"));

        Assertions.assertFalse(description.methodIsEnable("eth_getBlock"));
        Assertions.assertFalse(description.methodIsEnable("eth"));
        Assertions.assertFalse(description.methodIsEnable("evm"));
        Assertions.assertFalse(description.methodIsEnable("evm2"));
        Assertions.assertFalse(description.methodIsEnable("evmsnapshot"));
        Assertions.assertFalse(description.methodIsEnable(null));
    }

    @Test
    public void methodIsEnabledWhenEnabledNameList() {
        List<String> enabledMethods = new ArrayList<>();
        enabledMethods.add("evm_snapshot");
        enabledMethods.add("evm_revert");

        ModuleDescription description = new ModuleDescription("evm", "1.0", true, enabledMethods, null);

        Assertions.assertTrue(description.methodIsEnable("evm_snapshot"));
        Assertions.assertTrue(description.methodIsEnable("evm_revert"));

        Assertions.assertFalse(description.methodIsEnable("evm_do"));
        Assertions.assertFalse(description.methodIsEnable("evm_reset"));
        Assertions.assertFalse(description.methodIsEnable("evm_increaseTime"));

        Assertions.assertFalse(description.methodIsEnable("eth_getBlock"));
        Assertions.assertFalse(description.methodIsEnable("eth"));
        Assertions.assertFalse(description.methodIsEnable("evm"));
        Assertions.assertFalse(description.methodIsEnable("evm2"));
        Assertions.assertFalse(description.methodIsEnable("evmsnapshot"));
        Assertions.assertFalse(description.methodIsEnable(null));
    }

    @Test
    public void methodIsEnabledWhenDisabledNameList() {
        List<String> disabledMethods = new ArrayList<>();
        disabledMethods.add("evm_reset");
        disabledMethods.add("evm_increaseTime");

        ModuleDescription description = new ModuleDescription("evm", "1.0", true, null, disabledMethods);

        Assertions.assertTrue(description.methodIsEnable("evm_snapshot"));
        Assertions.assertTrue(description.methodIsEnable("evm_revert"));
        Assertions.assertTrue(description.methodIsEnable("evm_do"));

        Assertions.assertFalse(description.methodIsEnable("evm_reset"));
        Assertions.assertFalse(description.methodIsEnable("evm_increaseTime"));

        Assertions.assertFalse(description.methodIsEnable("eth_getBlock"));
        Assertions.assertFalse(description.methodIsEnable("eth"));
        Assertions.assertFalse(description.methodIsEnable("evm"));
        Assertions.assertFalse(description.methodIsEnable("evm2"));
        Assertions.assertFalse(description.methodIsEnable("evmsnapshot"));
        Assertions.assertFalse(description.methodIsEnable(null));
    }

    @Test
    public void methodIsEnabledWhenEnabledAndDisabledNameLists() {
        List<String> enabledMethods = new ArrayList<>();
        enabledMethods.add("evm_snapshot");
        enabledMethods.add("evm_revert");
        enabledMethods.add("evm_reset");

        List<String> disabledMethods = new ArrayList<>();
        disabledMethods.add("evm_reset");
        disabledMethods.add("evm_increaseTime");

        ModuleDescription description = new ModuleDescription("evm", "1.0", true, enabledMethods, disabledMethods);

        Assertions.assertTrue(description.methodIsEnable("evm_snapshot"));
        Assertions.assertTrue(description.methodIsEnable("evm_revert"));

        Assertions.assertFalse(description.methodIsEnable("evm_do"));
        Assertions.assertFalse(description.methodIsEnable("evm_reset"));
        Assertions.assertFalse(description.methodIsEnable("evm_increaseTime"));

        Assertions.assertFalse(description.methodIsEnable("eth_getBlock"));
        Assertions.assertFalse(description.methodIsEnable("eth"));
        Assertions.assertFalse(description.methodIsEnable("evm"));
        Assertions.assertFalse(description.methodIsEnable("evm2"));
        Assertions.assertFalse(description.methodIsEnable("evmsnapshot"));
        Assertions.assertFalse(description.methodIsEnable(null));
    }

    @Test
    public void getModulesFromTestNewRskSystemProperties() {
        TestSystemProperties config = new TestSystemProperties();
        List<ModuleDescription> modules = config.getRpcModules();

        Assertions.assertNotNull(modules);
        Assertions.assertFalse(modules.isEmpty());
        Assertions.assertEquals(2, modules.size());

        ModuleDescription moduleEth = modules.get(0);

        Assertions.assertEquals("eth", moduleEth.getName());
        Assertions.assertEquals("1.0", moduleEth.getVersion());
        Assertions.assertTrue(moduleEth.isEnabled());
        Assertions.assertNotNull(moduleEth.getEnabledMethods());
        Assertions.assertTrue(moduleEth.getEnabledMethods().isEmpty());
        Assertions.assertNotNull(moduleEth.getDisabledMethods());
        Assertions.assertTrue(moduleEth.getDisabledMethods().isEmpty());

        ModuleDescription moduleEvm = modules.get(1);

        Assertions.assertEquals("evm", moduleEvm.getName());
        Assertions.assertEquals("1.1", moduleEvm.getVersion());
        Assertions.assertFalse(moduleEvm.isEnabled());

        Assertions.assertNotNull(moduleEvm.getEnabledMethods());
        Assertions.assertFalse(moduleEvm.getEnabledMethods().isEmpty());
        Assertions.assertEquals(2, moduleEvm.getEnabledMethods().size());
        Assertions.assertNotNull(moduleEvm.getDisabledMethods());
        Assertions.assertFalse(moduleEvm.getDisabledMethods().isEmpty());
        Assertions.assertEquals(2, moduleEvm.getDisabledMethods().size());
    }
}
