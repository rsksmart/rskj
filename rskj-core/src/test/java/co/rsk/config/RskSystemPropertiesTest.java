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

    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void defaultValues() {
        Assert.assertEquals(false, config.isMinerClientEnabled());
        Assert.assertEquals(false, config.isMinerServerEnabled());
        Assert.assertEquals(0, config.minerMinGasPrice());
        Assert.assertEquals(0, config.minerGasUnitInDollars(), 0.001);
        Assert.assertEquals(0, config.minerMinFeesNotifyInDollars(), 0.001);
        Assert.assertTrue(config.isFlushEnabled());
    }

    @Test
    public void hasMessagesConfiguredInTestConfig() {
        Assert.assertTrue(config.hasMessageRecorderEnabled());

        List<String> commands = config.getMessageRecorderCommands();
        Assert.assertNotNull(commands);
        Assert.assertEquals(2, commands.size());
        Assert.assertTrue(commands.contains("TRANSACTIONS"));
        Assert.assertTrue(commands.contains("RSK_MESSAGE:BLOCK_MESSAGE"));
    }
}
