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

import co.rsk.net.notifications.FederationNotificationSender;
import co.rsk.net.notifications.panics.PanicFlag;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.mockito.Mockito.mock;

public class FederationFrozenAlertTest {
    private FederationNotificationSender s1, s2;
    private FederationFrozenAlert alert;

    @Before
    public void setup() {
        s1 = mock(FederationNotificationSender.class);
        s2 = mock(FederationNotificationSender.class);
        alert = new FederationFrozenAlert(
                Instant.ofEpochMilli(111_222L),
                Arrays.asList(s1, s2));
    }

    @Test
    public void getters() {
        Assert.assertEquals(Instant.ofEpochMilli(111_222L), alert.getCreated());
        Assert.assertEquals(2, alert.getFrozenMembers().size());
        Assert.assertEquals(s1, alert.getFrozenMembers().get(0));
        Assert.assertEquals(s2, alert.getFrozenMembers().get(1));
    }

    @Test
    public void flag() {
        PanicFlag flag = alert.getAssociatedPanicFlag(100);
        Assert.assertEquals(PanicFlag.Reason.FEDERATION_FROZEN, flag.getReason());
        Assert.assertEquals(100, flag.getSinceBlockNumber());
    }
}
