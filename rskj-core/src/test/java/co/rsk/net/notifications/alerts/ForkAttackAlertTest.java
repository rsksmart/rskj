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

import co.rsk.crypto.Keccak256;
import co.rsk.net.notifications.FederationNotificationSender;
import co.rsk.net.notifications.panics.PanicFlag;
import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ForkAttackAlertTest {
    private Block mockBlock;
    private ForkAttackAlert alert;

    @Before
    public void setup() {
        mockBlock = mock(Block.class);
        when(mockBlock.getHash()).thenReturn(new Keccak256("602fc8caaccb7ba8d9f151d51d380574d591496f6031c052ad6be999170da1fc"));
        when(mockBlock.getNumber()).thenReturn(444L);

        alert = new ForkAttackAlert(
                Instant.ofEpochMilli(55_123_456L),
                mockBlock,
                true);
    }

    @Test
    public void getters() {
        Assert.assertEquals(Instant.ofEpochMilli(55_123_456L), alert.getCreated());
        Assert.assertEquals("602fc8caaccb7ba8d9f151d51d380574d591496f6031c052ad6be999170da1fc", Hex.toHexString(alert.getBestBlockHash().getBytes()));
        Assert.assertEquals(444L, alert.getBestBlockNumber());
        Assert.assertEquals(true, alert.isFederatedNode());
    }

    @Test
    public void flag() {
        PanicFlag flag = alert.getAssociatedPanicFlag(100);
        Assert.assertEquals(PanicFlag.Reason.FEDERATION_FORKED, flag.getReason());
        Assert.assertEquals(100, flag.getSinceBlockNumber());

        alert = new ForkAttackAlert(
                Instant.ofEpochMilli(5678L),
                mockBlock,
                false);

        flag = alert.getAssociatedPanicFlag(200);
        Assert.assertEquals(PanicFlag.Reason.NODE_FORKED, flag.getReason());
        Assert.assertEquals(200, flag.getSinceBlockNumber());
    }
}
