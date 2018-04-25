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
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 19/04/2017.
 */
public class ModuleDescriptionTest {
    @Test
    public void createWithInitialData() {
        ModuleDescription description = new ModuleDescription("evm", "1.0", true, null, null);

        Assert.assertEquals("evm", description.getName());
        Assert.assertEquals("1.0", description.getVersion());

        Assert.assertTrue(description.isEnabled());

        Assert.assertNotNull(description.getEnabledMethods());
        Assert.assertTrue(description.getEnabledMethods().isEmpty());

        Assert.assertNotNull(description.getDisabledMethods());
        Assert.assertTrue(description.getDisabledMethods().isEmpty());
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

        Assert.assertEquals("evm", description.getName());
        Assert.assertEquals("1.0", description.getVersion());

        Assert.assertTrue(description.isEnabled());

        Assert.assertNotNull(description.getEnabledMethods());
        Assert.assertFalse(description.getEnabledMethods().isEmpty());
        Assert.assertEquals(2, description.getEnabledMethods().size());
        Assert.assertTrue(description.getEnabledMethods().contains("evm_snapshot"));
        Assert.assertTrue(description.getEnabledMethods().contains("evm_revert"));

        Assert.assertNotNull(description.getDisabledMethods());
        Assert.assertFalse(description.getDisabledMethods().isEmpty());
        Assert.assertEquals(2, description.getDisabledMethods().size());
        Assert.assertTrue(description.getDisabledMethods().contains("evm_reset"));
        Assert.assertTrue(description.getDisabledMethods().contains("evm_increaseTime"));
    }

    @Test
    public void methodIsInModule() {
        ModuleDescription description = new ModuleDescription("evm", "1.0", true, null, null);

        Assert.assertTrue(description.methodIsInModule("evm_snapshot"));
        Assert.assertTrue(description.methodIsInModule("evm_do"));

        Assert.assertFalse(description.methodIsInModule("eth_getBlock"));
        Assert.assertFalse(description.methodIsInModule("eth"));
        Assert.assertFalse(description.methodIsInModule("evm"));
        Assert.assertFalse(description.methodIsInModule("evm2"));
        Assert.assertFalse(description.methodIsInModule("evmsnapshot"));
        Assert.assertFalse(description.methodIsInModule(null));
    }

    @Test
    public void methodIsEnabledWhenEmptyNameList() {
        ModuleDescription description = new ModuleDescription("evm", "1.0", true, null, null);

        Assert.assertTrue(description.methodIsEnable("evm_snapshot"));
        Assert.assertTrue(description.methodIsEnable("evm_do"));

        Assert.assertFalse(description.methodIsEnable("eth_getBlock"));
        Assert.assertFalse(description.methodIsEnable("eth"));
        Assert.assertFalse(description.methodIsEnable("evm"));
        Assert.assertFalse(description.methodIsEnable("evm2"));
        Assert.assertFalse(description.methodIsEnable("evmsnapshot"));
        Assert.assertFalse(description.methodIsEnable(null));
    }

    @Test
    public void methodIsEnabledWhenEnabledNameList() {
        List<String> enabledMethods = new ArrayList<>();
        enabledMethods.add("evm_snapshot");
        enabledMethods.add("evm_revert");

        ModuleDescription description = new ModuleDescription("evm", "1.0", true, enabledMethods, null);

        Assert.assertTrue(description.methodIsEnable("evm_snapshot"));
        Assert.assertTrue(description.methodIsEnable("evm_revert"));

        Assert.assertFalse(description.methodIsEnable("evm_do"));
        Assert.assertFalse(description.methodIsEnable("evm_reset"));
        Assert.assertFalse(description.methodIsEnable("evm_increaseTime"));

        Assert.assertFalse(description.methodIsEnable("eth_getBlock"));
        Assert.assertFalse(description.methodIsEnable("eth"));
        Assert.assertFalse(description.methodIsEnable("evm"));
        Assert.assertFalse(description.methodIsEnable("evm2"));
        Assert.assertFalse(description.methodIsEnable("evmsnapshot"));
        Assert.assertFalse(description.methodIsEnable(null));
    }

    @Test
    public void methodIsEnabledWhenDisabledNameList() {
        List<String> disabledMethods = new ArrayList<>();
        disabledMethods.add("evm_reset");
        disabledMethods.add("evm_increaseTime");

        ModuleDescription description = new ModuleDescription("evm", "1.0", true, null, disabledMethods);

        Assert.assertTrue(description.methodIsEnable("evm_snapshot"));
        Assert.assertTrue(description.methodIsEnable("evm_revert"));
        Assert.assertTrue(description.methodIsEnable("evm_do"));

        Assert.assertFalse(description.methodIsEnable("evm_reset"));
        Assert.assertFalse(description.methodIsEnable("evm_increaseTime"));

        Assert.assertFalse(description.methodIsEnable("eth_getBlock"));
        Assert.assertFalse(description.methodIsEnable("eth"));
        Assert.assertFalse(description.methodIsEnable("evm"));
        Assert.assertFalse(description.methodIsEnable("evm2"));
        Assert.assertFalse(description.methodIsEnable("evmsnapshot"));
        Assert.assertFalse(description.methodIsEnable(null));
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

        Assert.assertTrue(description.methodIsEnable("evm_snapshot"));
        Assert.assertTrue(description.methodIsEnable("evm_revert"));

        Assert.assertFalse(description.methodIsEnable("evm_do"));
        Assert.assertFalse(description.methodIsEnable("evm_reset"));
        Assert.assertFalse(description.methodIsEnable("evm_increaseTime"));

        Assert.assertFalse(description.methodIsEnable("eth_getBlock"));
        Assert.assertFalse(description.methodIsEnable("eth"));
        Assert.assertFalse(description.methodIsEnable("evm"));
        Assert.assertFalse(description.methodIsEnable("evm2"));
        Assert.assertFalse(description.methodIsEnable("evmsnapshot"));
        Assert.assertFalse(description.methodIsEnable(null));
    }

    @Test
    public void getModulesFromTestNewRskSystemProperties() {
        TestSystemProperties config = new TestSystemProperties();
        List<ModuleDescription> modules = config.getRpcModules();

        Assert.assertNotNull(modules);
        Assert.assertFalse(modules.isEmpty());
        Assert.assertEquals(2, modules.size());

        ModuleDescription moduleEth = modules.get(0);

        Assert.assertEquals("eth", moduleEth.getName());
        Assert.assertEquals("1.0", moduleEth.getVersion());
        Assert.assertTrue(moduleEth.isEnabled());
        Assert.assertNotNull(moduleEth.getEnabledMethods());
        Assert.assertTrue(moduleEth.getEnabledMethods().isEmpty());
        Assert.assertNotNull(moduleEth.getDisabledMethods());
        Assert.assertTrue(moduleEth.getDisabledMethods().isEmpty());

        ModuleDescription moduleEvm = modules.get(1);

        Assert.assertEquals("evm", moduleEvm.getName());
        Assert.assertEquals("1.1", moduleEvm.getVersion());
        Assert.assertFalse(moduleEvm.isEnabled());

        Assert.assertNotNull(moduleEvm.getEnabledMethods());
        Assert.assertFalse(moduleEvm.getEnabledMethods().isEmpty());
        Assert.assertEquals(2, moduleEvm.getEnabledMethods().size());
        Assert.assertNotNull(moduleEvm.getDisabledMethods());
        Assert.assertFalse(moduleEvm.getDisabledMethods().isEmpty());
        Assert.assertEquals(2, moduleEvm.getDisabledMethods().size());
    }
}
