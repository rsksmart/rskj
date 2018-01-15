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

package co.rsk.config;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 3/16/2016.
 */
public class RskSystemPropertiesTest {
    @Test
    public void defaultValues() {
        Assert.assertEquals(false, ConfigHelper.CONFIG.minerClientEnabled());
        Assert.assertEquals(false, ConfigHelper.CONFIG.minerServerEnabled());
        Assert.assertEquals(0, ConfigHelper.CONFIG.minerMinGasPrice());
        Assert.assertEquals(0, ConfigHelper.CONFIG.minerGasUnitInDollars(), 0.001);
        Assert.assertEquals(0, ConfigHelper.CONFIG.minerMinFeesNotifyInDollars(), 0.001);
        Assert.assertTrue(ConfigHelper.CONFIG.isFlushEnabled());
    }

    @Test
    public void hasMessagesConfiguredInTestConfig() {
        Assert.assertTrue(ConfigHelper.CONFIG.hasMessageRecorderEnabled());

        List<String> commands = ConfigHelper.CONFIG.getMessageRecorderCommands();
        Assert.assertNotNull(commands);
        Assert.assertEquals(2, commands.size());
        Assert.assertTrue(commands.contains("TRANSACTIONS"));
        Assert.assertTrue(commands.contains("RSK_MESSAGE:BLOCK_MESSAGE"));
    }
}
