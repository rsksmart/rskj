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

package co.rsk.net.notifications.alerts;

import co.rsk.net.notifications.panics.PanicFlag;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NodeEclipsedAlertTest {
    private NodeEclipsedAlert alert;

    @Before
    public void setup() {
        alert = new NodeEclipsedAlert(1_000_000);
    }

    @Test
    public void getters() {
        Assert.assertEquals(1_000_000, alert.getTimeWithoutFederationNotifications());
    }

    @Test
    public void copy() {
        FederationAlert copy = alert.copy();

        Assert.assertEquals(NodeEclipsedAlert.class, copy.getClass());

        NodeEclipsedAlert castedCopy = (NodeEclipsedAlert) copy;

        Assert.assertEquals(1_000_000, castedCopy.getTimeWithoutFederationNotifications());
    }

    @Test
    public void flag() {
        PanicFlag flag = alert.getAssociatedPanicFlag(100);
        Assert.assertEquals(PanicFlag.Reason.NODE_ECLIPSED, flag.getReason());
        Assert.assertEquals(100, flag.getSinceBlockNumber());
    }
}
