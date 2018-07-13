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

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.notifications.FederationNotificationSender;
import co.rsk.net.notifications.panics.PanicFlag;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FederationFrozenAlertTest {
    private FederationNotificationSender senderMock;
    private FederationFrozenAlert alert;

    @Before
    public void setup() {
        senderMock = mock(FederationNotificationSender.class);
        when(senderMock.getBytes()).thenReturn(Hex.decode("0000000000000000000000000000000000000001"));

        alert = new FederationFrozenAlert(
                senderMock,
                new Keccak256("602fc8caaccb7ba8d9f151d51d380574d591496f6031c052ad6be999170da1fc"),
                123);
    }

    @Test
    public void getters() {
        Assert.assertEquals("0000000000000000000000000000000000000001", Hex.toHexString(alert.getSender().getBytes()));
        Assert.assertEquals("602fc8caaccb7ba8d9f151d51d380574d591496f6031c052ad6be999170da1fc", Hex.toHexString(alert.getConfirmationBlockHash().getBytes()));
        Assert.assertEquals(123, alert.getConfirmationBlockNumber());
    }

    @Test
    public void copy() {
        FederationAlert copy = alert.copy();

        Assert.assertEquals(FederationFrozenAlert.class, copy.getClass());

        FederationFrozenAlert castedCopy = (FederationFrozenAlert) copy;

        Assert.assertEquals("0000000000000000000000000000000000000001", Hex.toHexString(castedCopy.getSender().getBytes()));
        Assert.assertEquals("602fc8caaccb7ba8d9f151d51d380574d591496f6031c052ad6be999170da1fc", Hex.toHexString(castedCopy.getConfirmationBlockHash().getBytes()));
        Assert.assertEquals(123, castedCopy.getConfirmationBlockNumber());
    }

    @Test
    public void flag() {
        PanicFlag flag = alert.getAssociatedPanicFlag(100);
        Assert.assertEquals(PanicFlag.Reason.FEDERATION_FROZEN, flag.getReason());
        Assert.assertEquals(100, flag.getSinceBlockNumber());
    }
}
