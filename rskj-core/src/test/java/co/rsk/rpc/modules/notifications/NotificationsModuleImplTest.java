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
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3Mocks;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.time.Instant;
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
                getSenderMock("0000000000000000000000000000000000000001"),
                new Keccak256("602fc8caaccb7ba8d9f151d51d380574d591496f6031c052ad6be999170da1fc"), 123));
        alerts.add(new ForkAttackAlert(
                getSenderMock("0000000000000000000000000000000000000002"),
                new Keccak256("5cef9acdc362bba00ddbbd524e1e490902c6ff0bd9754b5caf60c6e27c51c3f2"), 456,
                new Keccak256("df1cf7182920d5a7b6c9a9c4c846b672d9dbd47692c2bf79807606c5f26202e0"),
                511, true));
        alerts.add(new NodeEclipsedAlert(4567));

        List<NotificationsModule.FederationAlert> result = notificationsModule.getAlerts();

        Assert.assertEquals(3, result.size());

        Assert.assertEquals("federation_frozen", result.get(0).code);
        NotificationsModule.FederationFrozenAlert a0 = (NotificationsModule.FederationFrozenAlert) result.get(0);
        Assert.assertEquals("0000000000000000000000000000000000000001", a0.source);
        Assert.assertEquals("602fc8caaccb7ba8d9f151d51d380574d591496f6031c052ad6be999170da1fc", a0.confirmationBlockHash);
        Assert.assertEquals(123, a0.confirmationBlockNumber);

        Assert.assertEquals("fork_attack", result.get(1).code);
        NotificationsModule.ForkAttackAlert a1 = (NotificationsModule.ForkAttackAlert) result.get(1);
        Assert.assertEquals("0000000000000000000000000000000000000002", a1.source);
        Assert.assertEquals("5cef9acdc362bba00ddbbd524e1e490902c6ff0bd9754b5caf60c6e27c51c3f2", a1.confirmationBlockHash);
        Assert.assertEquals(456, a1.confirmationBlockNumber);
        Assert.assertEquals("df1cf7182920d5a7b6c9a9c4c846b672d9dbd47692c2bf79807606c5f26202e0", a1.inBestChainBlockHash);
        Assert.assertEquals(511, a1.bestBlockNumber);
        Assert.assertEquals(true, a1.isFederatedNode);

        Assert.assertEquals("node_eclipsed", result.get(2).code);
        NotificationsModule.NodeEclipsedAlert a2 = (NotificationsModule.NodeEclipsedAlert) result.get(2);
        Assert.assertEquals(4567, a2.timeWithoutFederationNotifications);
    }

    @Test
    public void getLastNotificationReceivedTime() {
        Whitebox.setInternalState(federationState, "lastNotificationReceivedTime", Instant.ofEpochMilli(5_000_000));

        long result = notificationsModule.getLastNotificationReceivedTime();

        Assert.assertEquals(5_000_000, result);
    }

    private FederationNotificationSender getSenderMock(String bytesAsHex) {
        FederationNotificationSender result = mock(FederationNotificationSender.class);
        when(result.getBytes()).thenReturn(Hex.decode("0000000000000000000000000000000000000001"));
        return result;
    }
}
