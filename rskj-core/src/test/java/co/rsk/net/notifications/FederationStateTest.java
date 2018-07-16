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

package co.rsk.net.notifications;

import co.rsk.config.RskSystemProperties;
import co.rsk.net.notifications.alerts.FederationAlert;
import co.rsk.net.notifications.panics.PanicFlag;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FederationStateTest {
    private RskSystemProperties config;
    private FederationState federationState;

    @Before
    public void setup() throws Exception {
        config = mock(RskSystemProperties.class);
        federationState = new FederationState(config);
    }

    @Test
    public void processNotification_panicsEnabled() {
        when(config.shouldFederationNotificationsTriggerPanic()).thenReturn(true);

        FederationNotification notification = mock(FederationNotification.class);
        FederationNotificationSender member = mock(FederationNotificationSender.class);
        when(notification.getSender()).thenReturn(member);

        // Set a panic which is intended to be cleared
        federationState.getPanicStatus().set(PanicFlag.NodeEclipsed(123));
        Assert.assertTrue(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_ECLIPSED));

        federationState.processNotification(notification);
        long difference = Instant.now().toEpochMilli() - federationState.getLastNotificationReceivedTime().toEpochMilli();
        Assert.assertTrue(difference < 100);
        // Panic is cleared
        Assert.assertFalse(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_ECLIPSED));
    }

    @Test
    public void processNotification_panicsDisabled() {
        when(config.shouldFederationNotificationsTriggerPanic()).thenReturn(false);

        FederationNotification notification = mock(FederationNotification.class);
        FederationNotificationSender member = mock(FederationNotificationSender.class);
        when(notification.getSender()).thenReturn(member);

        // Set a panic which is intended to stay there
        federationState.getPanicStatus().set(PanicFlag.NodeEclipsed(123));
        Assert.assertTrue(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_ECLIPSED));

        federationState.processNotification(notification);
        long difference = Instant.now().toEpochMilli() - federationState.getLastNotificationReceivedTime().toEpochMilli();
        Assert.assertTrue(difference < 100);
        // Panic is not cleared
        Assert.assertTrue(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_ECLIPSED));
    }

    @Test
    public void processAlerts_panicsEnabled() {
        when(config.shouldFederationNotificationsTriggerPanic()).thenReturn(true);

        // Mock alerts
        List<FederationAlert> alerts = new ArrayList<>();

        FederationAlert alert = mock(FederationAlert.class);
        when(alert.getAssociatedPanicFlag(200)).thenReturn(PanicFlag.FederationFrozen(300));
        alerts.add(alert);

        alert = mock(FederationAlert.class);
        when(alert.getAssociatedPanicFlag(200)).thenReturn(PanicFlag.FederationBlockchainForked(400));
        alerts.add(alert);

        Assert.assertEquals(0, federationState.getAlerts().size());

        // Set the only panic state that shouldn't be altered by this method
        federationState.getPanicStatus().set(PanicFlag.of(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertTrue(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_ECLIPSED));

        federationState.processAlerts(200, alerts);

        // Alerts have been added
        List<FederationAlert> actualAlerts = federationState.getAlerts();
        Assert.assertEquals(2, actualAlerts.size());
        Assert.assertEquals(alerts.get(0), actualAlerts.get(0));
        Assert.assertEquals(alerts.get(1), actualAlerts.get(1));

        // Corresponding panic flags have been set
        Assert.assertTrue(federationState.getPanicStatus().has(PanicFlag.Reason.FEDERATION_FROZEN));
        Assert.assertEquals(federationState.getPanicStatus().get(PanicFlag.Reason.FEDERATION_FROZEN).getSinceBlockNumber(), 300);
        Assert.assertTrue(federationState.getPanicStatus().has(PanicFlag.Reason.FEDERATION_FORKED));
        Assert.assertEquals(federationState.getPanicStatus().get(PanicFlag.Reason.FEDERATION_FORKED).getSinceBlockNumber(), 400);

        // Panic flags left as is
        Assert.assertTrue(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertFalse(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_FORKED));
    }

    @Test
    public void processAlerts_panicsDisabled() {
        when(config.shouldFederationNotificationsTriggerPanic()).thenReturn(false);

        // Mock alerts
        List<FederationAlert> alerts = new ArrayList<>();

        FederationAlert alert = mock(FederationAlert.class);
        alerts.add(alert);

        alert = mock(FederationAlert.class);
        alerts.add(alert);

        Assert.assertEquals(0, federationState.getAlerts().size());

        // Set the only panic state that shouldn't be altered by this method
        federationState.getPanicStatus().set(PanicFlag.of(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertTrue(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_ECLIPSED));

        federationState.processAlerts(200, alerts);

        // Alerts have been added
        List<FederationAlert> actualAlerts = federationState.getAlerts();
        Assert.assertEquals(2, actualAlerts.size());
        Assert.assertEquals(alerts.get(0), actualAlerts.get(0));
        Assert.assertEquals(alerts.get(1), actualAlerts.get(1));

        // Panic flags left as is
        Assert.assertTrue(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_ECLIPSED));
        Assert.assertFalse(federationState.getPanicStatus().has(PanicFlag.Reason.FEDERATION_FROZEN));
        Assert.assertFalse(federationState.getPanicStatus().has(PanicFlag.Reason.FEDERATION_FORKED));
        Assert.assertFalse(federationState.getPanicStatus().has(PanicFlag.Reason.NODE_FORKED));
    }
}
