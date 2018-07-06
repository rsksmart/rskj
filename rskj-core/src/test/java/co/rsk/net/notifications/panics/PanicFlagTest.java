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

package co.rsk.net.notifications.panics;

import org.junit.Assert;
import org.junit.Test;

public class PanicFlagTest {
    @Test
    public void NodeEclipsed() {
        PanicFlag flag = PanicFlag.NodeEclipsed(100);
        Assert.assertEquals(PanicFlag.Reason.NODE_ECLIPSED, flag.getReason());
        Assert.assertEquals("node_eclipsed", flag.getReason().code);
        Assert.assertEquals("Node eclipsed", flag.getReason().description);
        Assert.assertEquals(100, flag.getSinceBlockNumber());
    }

    @Test
    public void FederationBlockchainForked() {
        PanicFlag flag = PanicFlag.FederationBlockchainForked(100);
        Assert.assertEquals(PanicFlag.Reason.FEDERATION_FORKED, flag.getReason());
        Assert.assertEquals("federation_forked", flag.getReason().code);
        Assert.assertEquals("Federation forked", flag.getReason().description);
        Assert.assertEquals(100, flag.getSinceBlockNumber());
    }

    @Test
    public void NodeBlockchainForked() {
        PanicFlag flag = PanicFlag.NodeBlockchainForked(100);
        Assert.assertEquals(PanicFlag.Reason.NODE_FORKED, flag.getReason());
        Assert.assertEquals("node_forked", flag.getReason().code);
        Assert.assertEquals("Node forked", flag.getReason().description);
        Assert.assertEquals(100, flag.getSinceBlockNumber());
    }

    @Test
    public void FederationFrozen() {
        PanicFlag flag = PanicFlag.FederationFrozen(100);
        Assert.assertEquals(PanicFlag.Reason.FEDERATION_FROZEN, flag.getReason());
        Assert.assertEquals("federation_frozen", flag.getReason().code);
        Assert.assertEquals("Federation frozen", flag.getReason().description);
        Assert.assertEquals(100, flag.getSinceBlockNumber());
    }
}
