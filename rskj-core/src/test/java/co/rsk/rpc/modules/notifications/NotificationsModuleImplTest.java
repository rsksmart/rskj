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
package co.rsk.rpc.modules.notifications;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.MessageHandler;
import co.rsk.net.notifications.FederationNotificationSender;
import co.rsk.net.notifications.FederationState;
import co.rsk.net.notifications.alerts.FederationAlert;
import co.rsk.net.notifications.alerts.FederationFrozenAlert;
import co.rsk.net.notifications.alerts.ForkAttackAlert;
import co.rsk.net.notifications.alerts.NodeEclipsedAlert;
import co.rsk.net.notifications.panics.PanicFlag;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import org.ethereum.core.Block;
import org.ethereum.crypto.ECKey;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3Mocks;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotificationsModuleImplTest {
    private RskSystemProperties config;
    private FederationState federationState;
    private NotificationsModuleImpl notificationsModule;

    @Before
    public void setup() throws Exception {
        config = mock(RskSystemProperties.class);
        federationState = new FederationState(config);
        notificationsModule = new NotificationsModuleImpl(federationState);
    }

    @Test
    public void getPanicStatus() {
        federationState.getPanicStatus().set(PanicFlag.NodeEclipsed(400));
        federationState.getPanicStatus().set(PanicFlag.FederationFrozen(200));
        federationState.getPanicStatus().set(PanicFlag.FederationBlockchainForked(300));
        federationState.getPanicStatus().set(PanicFlag.NodeBlockchainForked(100));

        List<NotificationsModule.PanicFlag> result = notificationsModule.getPanicStatus();

        Assert.assertEquals(4, result.size());

        Assert.assertEquals(PanicFlag.Reason.NODE_FORKED.getCode(), result.get(0).code);
        Assert.assertEquals(PanicFlag.Reason.NODE_FORKED.getDescription(), result.get(0).description);
        Assert.assertEquals(100, result.get(0).sinceBlockNumber);

        Assert.assertEquals(PanicFlag.Reason.FEDERATION_FROZEN.getCode(), result.get(1).code);
        Assert.assertEquals(PanicFlag.Reason.FEDERATION_FROZEN.getDescription(), result.get(1).description);
        Assert.assertEquals(200, result.get(1).sinceBlockNumber);

        Assert.assertEquals(PanicFlag.Reason.FEDERATION_FORKED.getCode(), result.get(2).code);
        Assert.assertEquals(PanicFlag.Reason.FEDERATION_FORKED.getDescription(), result.get(2).description);
        Assert.assertEquals(300, result.get(2).sinceBlockNumber);

        Assert.assertEquals(PanicFlag.Reason.NODE_ECLIPSED.getCode(), result.get(3).code);
        Assert.assertEquals(PanicFlag.Reason.NODE_ECLIPSED.getDescription(), result.get(3).description);
        Assert.assertEquals(400, result.get(3).sinceBlockNumber);
    }

    @Test
    public void getAlerts() {
        Queue<FederationAlert> alerts = (Queue<FederationAlert>) Whitebox.getInternalState(federationState, "alerts");
        alerts.add(new FederationFrozenAlert(
                Instant.ofEpochMilli(444_555L),
                Arrays.asList(new FederationNotificationSender(ECKey.fromPrivate(BigInteger.valueOf(10L))),
                        new FederationNotificationSender(ECKey.fromPrivate(BigInteger.valueOf(20L))),
                        new FederationNotificationSender(ECKey.fromPrivate(BigInteger.valueOf(30L))))));
        Block bestBlockMock = mock(Block.class);
        when(bestBlockMock.getHash()).thenReturn(new Keccak256("332bc0a14d12eff7fcf6a3e63ca6a043d6ae4ecf721af0cf48d2be013b86ef72"));
        when(bestBlockMock.getNumber()).thenReturn(789L);
        alerts.add(new ForkAttackAlert(
                Instant.ofEpochMilli(666_777L),
                bestBlockMock,
                true));
        alerts.add(new NodeEclipsedAlert(Instant.ofEpochMilli(888_999L), 4567));

        List<NotificationsModule.FederationAlert> result = notificationsModule.getAlerts();

        Assert.assertEquals(3, result.size());

        Assert.assertEquals("federation_frozen", result.get(0).code);
        Assert.assertEquals(444_555L, result.get(0).created);
        NotificationsModule.FederationFrozenAlert a0 = (NotificationsModule.FederationFrozenAlert) result.get(0);
        Assert.assertEquals(3, a0.frozenMembers.size());
        Assert.assertEquals(Hex.toHexString(ECKey.fromPrivate(BigInteger.valueOf(10L)).getPubKey(true)), a0.frozenMembers.get(0));
        Assert.assertEquals(Hex.toHexString(ECKey.fromPrivate(BigInteger.valueOf(20L)).getPubKey(true)), a0.frozenMembers.get(1));
        Assert.assertEquals(Hex.toHexString(ECKey.fromPrivate(BigInteger.valueOf(30L)).getPubKey(true)), a0.frozenMembers.get(2));

        Assert.assertEquals("fork_attack", result.get(1).code);
        Assert.assertEquals(666_777L, result.get(1).created);
        NotificationsModule.ForkAttackAlert a1 = (NotificationsModule.ForkAttackAlert) result.get(1);
        Assert.assertEquals("332bc0a14d12eff7fcf6a3e63ca6a043d6ae4ecf721af0cf48d2be013b86ef72", a1.bestBlockHash);
        Assert.assertEquals(789L, a1.bestBlockNumber);

        Assert.assertEquals("node_eclipsed", result.get(2).code);
        Assert.assertEquals(888_999L, result.get(2).created);
        NotificationsModule.NodeEclipsedAlert a2 = (NotificationsModule.NodeEclipsedAlert) result.get(2);
        Assert.assertEquals(4567, a2.timeWithoutFederationNotifications);
    }

    @Test
    public void getLastNotificationReceivedTime() {
        Whitebox.setInternalState(federationState, "lastNotificationReceivedTime", Instant.ofEpochMilli(5_000_000));

        long result = notificationsModule.getLastNotificationReceivedTime();

        Assert.assertEquals(5_000_000, result);
    }
}
