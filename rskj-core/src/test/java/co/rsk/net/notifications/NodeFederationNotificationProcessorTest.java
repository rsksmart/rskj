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

import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.net.BlockProcessor;
import co.rsk.net.notifications.NotificationTestsUtils.FederationMember;
import co.rsk.net.notifications.alerts.FederationFrozenAlert;
import co.rsk.net.notifications.alerts.ForkAttackAlert;
import co.rsk.net.notifications.processing.FederationNotificationProcessingResult;
import co.rsk.net.notifications.processing.FederationNotificationProcessor;
import co.rsk.net.notifications.processing.NodeFederationNotificationProcessor;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.naming.ConfigurationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/***
 *
 * @author Diego Masini
 * @author Jose Orlicki
 *
 */
public class NodeFederationNotificationProcessorTest {
    private static long BEST_BLOCK = 100000l;
    private static RskSystemProperties config;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Constants constants = mock(Constants.class);
        when(constants.getBridgeConstants()).thenReturn(BridgeRegTestConstants.getInstance());

        BlockchainNetConfig blockchainNetConfig = mock(BlockchainNetConfig.class);
        when(blockchainNetConfig.getCommonConstants()).thenReturn(constants);

        config = mock(RskSystemProperties.class);
        when(config.maxSecondsBetweenNotifications()).thenReturn(1);
        when(config.getFederationMaxSilenceTimeSecs()).thenReturn(1);
        when(config.federationNotificationsEnabled()).thenReturn(true);
        when(config.shouldFederationNotificationsTriggerPanic()).thenReturn(true);
        when(config.getFederationConfirmationIndex()).thenReturn(1);
        when(config.getBlockchainConfig()).thenReturn(blockchainNetConfig);
        when(config.coinbaseAddress()).thenReturn(RskAddress.nullAddress());
    }

    @Test
    public void processFederationNotificationSuccessfully() {
        // Get a BlockProcessor.
        BlockProcessor blockProcessor = NotificationTestsUtils.buildInSyncBlockProcessor(BEST_BLOCK);

        // Get a member of the federation that will act as the source of the
        // notification.
        FederationMember federationMember = NotificationTestsUtils.getFederationMember();

        // Build FederationNotification with two confirmation, one for block 10 and
        // another for block 100.
        FederationNotification notification = new FederationNotification(federationMember.getAddress(),
                Instant.now().plus(60, ChronoUnit.SECONDS), Instant.now());
        notification.addConfirmation(10l, NotificationTestsUtils.hash(10l));
        notification.addConfirmation(100l, NotificationTestsUtils.hash(100l));

        // Sign the notification
        byte[] hash = notification.getHash();
        notification.setSignature(federationMember.getKey().sign(hash));

        // Create the FederationNotificationProcessor test instance.
        FederationNotificationProcessor notificationProcessor = new NodeFederationNotificationProcessor(config,
                blockProcessor);

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control.
        NotificationTestsUtils.waitMillis(1500);

        // Process the notification.
        FederationNotificationProcessingResult result = null;
        try {
            result = notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification);
        } catch (ConfigurationException e) {
            fail();
        }

        // Notification processing should have succeeded without triggering any alerts
        // nor activating any panic status.
        assertEquals(FederationNotificationProcessingResult.NOTIFICATION_PROCESSED_SUCCESSFULLY, result);
        assertFalse(notificationProcessor.inPanicState());
        assertEquals(0, notificationProcessor.getPanicSinceBlockNumber());
        assertEquals(0, notificationProcessor.getFederationAlerts().size());
    }

    @Test
    public void processFrozenFederationNotification() {
        // Get a BlockProcessor.
        BlockProcessor blockProcessor = NotificationTestsUtils.buildInSyncBlockProcessor(BEST_BLOCK);

        // Get a member of the federation that will act as the source of the
        // notification.
        FederationMember federationMember = NotificationTestsUtils.getFederationMember();

        // Build FederationNotification with two confirmation, one for block 10 and
        // another for block 100.
        FederationNotification notification = new FederationNotification(federationMember.getAddress(),
                Instant.now().plus(60, ChronoUnit.SECONDS), Instant.now(), true);
        notification.addConfirmation(10l, NotificationTestsUtils.hash(10l));
        notification.addConfirmation(100l, NotificationTestsUtils.hash(100l));

        // Sign the notification
        byte[] hash = notification.getHash();
        notification.setSignature(federationMember.getKey().sign(hash));

        // Create the FederationNotificationProcessor test instance.
        FederationNotificationProcessor notificationProcessor = new NodeFederationNotificationProcessor(config,
                blockProcessor);

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control.
        NotificationTestsUtils.waitMillis(1500);

        // Process the notification.
        FederationNotificationProcessingResult result = null;
        try {
            result = notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification);
        } catch (ConfigurationException e) {
            fail();
        }

        // Notification processing should have succeeded without triggering any alerts
        // nor activating any panic status.
        assertEquals(FederationNotificationProcessingResult.NOTIFICATION_PROCESSED_SUCCESSFULLY, result);
        assertTrue(notificationProcessor.inPanicState());
        assertEquals(PanicStatusReason.FEDERATION_FROZEN, notificationProcessor.getPanicStatus().getReason());
        assertEquals(BEST_BLOCK, notificationProcessor.getPanicSinceBlockNumber());
        assertEquals(1, notificationProcessor.getFederationAlerts().size());

        FederationFrozenAlert alert = (FederationFrozenAlert) notificationProcessor.getFederationAlerts().get(0);

        assertEquals(federationMember.getAddress(), alert.getSource());
        assertEquals(NotificationTestsUtils.hash(100l), alert.getConfirmationBlockHash());
        assertEquals(100l, alert.getConfirmationBlockNumber());
    }

    @Test
    public void processFederationNotificationFloodAttack() {
        // Get a BlockProcessor.
        BlockProcessor blockProcessor = NotificationTestsUtils.buildInSyncBlockProcessor(BEST_BLOCK);

        // Get a member of the federation that will act as the source of the
        // notification.
        FederationMember federationMember = NotificationTestsUtils.getFederationMember();

        // Build two federation notifications.
        FederationNotification notification1 = new FederationNotification(federationMember.getAddress(),
                Instant.now().plus(60, ChronoUnit.SECONDS), Instant.now());
        notification1.addConfirmation(10l, NotificationTestsUtils.hash(10l));
        notification1.addConfirmation(100l, NotificationTestsUtils.hash(100l));

        // Sign the notification
        byte[] hash1 = notification1.getHash();
        notification1.setSignature(federationMember.getKey().sign(hash1));

        // Build two federation notifications.
        FederationNotification notification2 = new FederationNotification(federationMember.getAddress(),
                Instant.now().plus(60, ChronoUnit.SECONDS), Instant.now());
        notification2.addConfirmation(20l, NotificationTestsUtils.hash(20l));
        notification2.addConfirmation(200l, NotificationTestsUtils.hash(200l));

        // Sign the notification
        byte[] hash2 = notification2.getHash();
        notification2.setSignature(federationMember.getKey().sign(hash2));

        // Create the FederationNotificationProcessor test instance.
        FederationNotificationProcessor notificationProcessor = new NodeFederationNotificationProcessor(config,
                blockProcessor);

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control.
        NotificationTestsUtils.waitMillis(1500);

        // Process first notification
        FederationNotificationProcessingResult result1 = null;
        try {
            result1 = notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification1);
        } catch (ConfigurationException e) {
            fail();
        }

        // Notification processing should have succeeded without triggering any alerts
        // nor activating any panic status.
        assertEquals(FederationNotificationProcessingResult.NOTIFICATION_PROCESSED_SUCCESSFULLY, result1);
        assertFalse(notificationProcessor.inPanicState());
        assertEquals(0, notificationProcessor.getPanicSinceBlockNumber());
        assertEquals(0, notificationProcessor.getFederationAlerts().size());

        // Process second notification
        FederationNotificationProcessingResult result2 = null;
        try {
            result2 = notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification2);
        } catch (ConfigurationException e) {
            fail();
        }

        // Notification processing should have stopped since notifications were received
        // too fast (anti-flooding control). No alerts or panic status should have been
        // triggered.
        assertEquals(FederationNotificationProcessingResult.NOTIFICATION_RECEIVED_TOO_FAST, result2);
        assertFalse(notificationProcessor.inPanicState());
        assertEquals(0, notificationProcessor.getPanicSinceBlockNumber());
        assertEquals(0, notificationProcessor.getFederationAlerts().size());
    }

    @Test
    public void attemptToProcessExpiredFederationNotification() {
        // Get a BlockProcessor.
        BlockProcessor blockProcessor = NotificationTestsUtils.buildInSyncBlockProcessor(BEST_BLOCK);

        // Get a member of the federation that will act as the source of the
        // notification.
        FederationMember federationMember = NotificationTestsUtils.getFederationMember();

        // Build FederationNotification with a TTL in the past and two confirmation, one
        // for block 10 and another for block 100.
        FederationNotification notification = new FederationNotification(federationMember.getAddress(),
                Instant.now().minus(60, ChronoUnit.SECONDS), Instant.now());
        notification.addConfirmation(10l, NotificationTestsUtils.hash(10l));
        notification.addConfirmation(100l, NotificationTestsUtils.hash(100l));

        // Sign the notification
        byte[] hash = notification.getHash();
        notification.setSignature(federationMember.getKey().sign(hash));

        // Create the FederationNotificationProcessor test instance.
        FederationNotificationProcessor notificationProcessor = new NodeFederationNotificationProcessor(config,
                blockProcessor);

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control.
        NotificationTestsUtils.waitMillis(1500);

        // Process the notification.
        FederationNotificationProcessingResult result = null;
        try {
            result = notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification);
        } catch (ConfigurationException e) {
            fail();
        }

        // Notification processing should have stopped since notification already
        // expired. No alerts or panic status should have been triggered.
        assertEquals(FederationNotificationProcessingResult.NOTIFICATION_EXPIRED, result);
        assertFalse(notificationProcessor.inPanicState());
        assertEquals(0, notificationProcessor.getPanicSinceBlockNumber());
        assertEquals(0, notificationProcessor.getFederationAlerts().size());
    }

    @Test
    public void attemptToProcessSameFederationNotificationTwice() {
        // Get a BlockProcessor.
        BlockProcessor blockProcessor = NotificationTestsUtils.buildInSyncBlockProcessor(BEST_BLOCK);

        // Get a member of the federation that will act as the source of the
        // notification.
        FederationMember federationMember = NotificationTestsUtils.getFederationMember();

        // Build FederationNotification with a TTL in the past and two confirmation, one
        // for block 10 and another for block 100.
        FederationNotification notification = new FederationNotification(federationMember.getAddress(),
                Instant.now().plus(60, ChronoUnit.SECONDS), Instant.now());
        notification.addConfirmation(10l, NotificationTestsUtils.hash(10l));
        notification.addConfirmation(100l, NotificationTestsUtils.hash(100l));

        // Sign the notification
        byte[] hash = notification.getHash();
        notification.setSignature(federationMember.getKey().sign(hash));

        // Create the FederationNotificationProcessor test instance.
        FederationNotificationProcessor notificationProcessor = new NodeFederationNotificationProcessor(config,
                blockProcessor);

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control.
        NotificationTestsUtils.waitMillis(1500);

        // Process the notification.
        FederationNotificationProcessingResult result1 = null;
        try {
            result1 = notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification);
        } catch (ConfigurationException e1) {
            fail();
        }

        // Notification processing should have succeeded without triggering any alerts
        // nor activating any panic status.
        assertEquals(FederationNotificationProcessingResult.NOTIFICATION_PROCESSED_SUCCESSFULLY, result1);
        assertFalse(notificationProcessor.inPanicState());
        assertEquals(0, notificationProcessor.getPanicSinceBlockNumber());
        assertEquals(0, notificationProcessor.getFederationAlerts().size());

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control.
        NotificationTestsUtils.waitMillis(1500);

        // Attempt to process the same notification again.
        FederationNotificationProcessingResult result2 = null;
        try {
            result2 = notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification);
        } catch (ConfigurationException e) {
            fail();
        }

        // Notification processing should have stopped since notification was already
        // processed. No alerts or panic status should have been triggered.
        assertEquals(FederationNotificationProcessingResult.NOTIFICATION_ALREADY_PROCESSED, result2);
        assertFalse(notificationProcessor.inPanicState());
        assertEquals(0, notificationProcessor.getPanicSinceBlockNumber());
        assertEquals(0, notificationProcessor.getFederationAlerts().size());
    }

    @Test
    public void processNotificationWithInvalidSignature() {
        // Get a BlockProcessor.
        BlockProcessor blockProcessor = NotificationTestsUtils.buildInSyncBlockProcessor(BEST_BLOCK);

        // Get a member of the federation that will act as the source of the
        // notification.
        FederationMember federationMember = NotificationTestsUtils.getFederationMember();

        // Build FederationNotification with a TTL in the past and two confirmation, one
        // for block 10 and another for block 100.
        FederationNotification notification = new FederationNotification(federationMember.getAddress(),
                Instant.now().plus(60, ChronoUnit.SECONDS), Instant.now());
        notification.addConfirmation(10l, NotificationTestsUtils.hash(10l));
        notification.addConfirmation(100l, NotificationTestsUtils.hash(100l));

        // Sign the notification with a bogus key
        ECKey bogusKey = ECKey.fromPrivate(HashUtil.keccak256("bogusKey".getBytes(StandardCharsets.UTF_8)));
        byte[] hash = notification.getHash();
        notification.setSignature(bogusKey.sign(hash));

        // Create the FederationNotificationProcessor test instance.
        FederationNotificationProcessor notificationProcessor = new NodeFederationNotificationProcessor(config,
                blockProcessor);

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control.
        NotificationTestsUtils.waitMillis(1500);

        // Process the notification.
        FederationNotificationProcessingResult result = null;
        try {
            result = notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification);
        } catch (ConfigurationException e) {
            fail();
        }

        // Notification processing should have stopped since notification signature does
        // not verify. No alerts or panic status should have been triggered.
        assertEquals(FederationNotificationProcessingResult.NOTIFICATION_SIGNATURE_DOES_NOT_VERIFY, result);
        assertFalse(notificationProcessor.inPanicState());
        assertEquals(0, notificationProcessor.getPanicSinceBlockNumber());
        assertEquals(0, notificationProcessor.getFederationAlerts().size());
    }

    @Test
    public void processFederationNotificationNodeForkDetected() {
        // Get a BlockProcessor.
        BlockProcessor blockProcessor = NotificationTestsUtils.buildForkedBlockProcessor(BEST_BLOCK);

        // Get a member of the federation that will act as the source of the
        // notification.
        FederationMember federationMember = NotificationTestsUtils.getFederationMember();

        // Build FederationNotification with two confirmation, one for block 10 and
        // another for block 100.
        FederationNotification notification = new FederationNotification(federationMember.getAddress(),
                Instant.now().plus(60, ChronoUnit.SECONDS), Instant.now());
        notification.addConfirmation(10l, NotificationTestsUtils.hash(10l));
        notification.addConfirmation(100l, NotificationTestsUtils.hash(100l));

        // Sign the notification
        byte[] hash = notification.getHash();
        notification.setSignature(federationMember.getKey().sign(hash));

        // Create the FederationNotificationProcessor test instance.
        FederationNotificationProcessor notificationProcessor = new NodeFederationNotificationProcessor(config,
                blockProcessor);

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control.
        NotificationTestsUtils.waitMillis(1500);

        // Process the notification.
        FederationNotificationProcessingResult result = null;
        try {
            result = notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification);
        } catch (ConfigurationException e) {
            fail();
        }

        // Notification processing should have succeeded without triggering any alerts
        // nor activating any panic status.
        assertEquals(FederationNotificationProcessingResult.NOTIFICATION_PROCESSED_SUCCESSFULLY, result);
        assertEquals(PanicStatusReason.NODE_FORKED, notificationProcessor.getPanicStatus().getReason());
        assertEquals(BEST_BLOCK, notificationProcessor.getPanicSinceBlockNumber());
        assertEquals(1, notificationProcessor.getFederationAlerts().size());

        ForkAttackAlert alert = (ForkAttackAlert) notificationProcessor.getFederationAlerts().get(0);

        assertFalse(alert.isFederatedNode());
        assertEquals(BEST_BLOCK, alert.getBestBlockNumber());
        assertEquals(federationMember.getAddress(), alert.getSource());
        assertEquals(NotificationTestsUtils.hash(100l), alert.getConfirmationBlockHash());
        assertEquals(null, alert.getInBestChainBlockHash());
        assertEquals(100l, alert.getConfirmationBlockNumber());
    }

    @Test
    public void checkIfUnderEclipseAttack() {
        // Get a BlockProcessor.
        BlockProcessor blockProcessor = NotificationTestsUtils.buildInSyncBlockProcessor(BEST_BLOCK);

        // Get a member of the federation that will act as the source of the
        // notifications.
        FederationMember federationMember = NotificationTestsUtils.getFederationMember();

        // Build FederationNotification with two confirmation, one for block 10 and
        // another for block 100.
        FederationNotification notification = new FederationNotification(federationMember.getAddress(),
                Instant.now().plus(60, ChronoUnit.SECONDS), Instant.now());
        notification.addConfirmation(10l, NotificationTestsUtils.hash(10l));
        notification.addConfirmation(100l, NotificationTestsUtils.hash(100l));

        // Sign the notification
        byte[] hash = notification.getHash();
        notification.setSignature(federationMember.getKey().sign(hash));

        // Create the FederationNotificationProcessor test instance and process the
        // notification
        FederationNotificationProcessor notificationProcessor = new NodeFederationNotificationProcessor(config,
                blockProcessor);

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control.
        NotificationTestsUtils.waitMillis(1500);

        // Process the notification.
        try {
            notificationProcessor.processFederationNotification(NotificationTestsUtils.getActivePeers(10),
                    notification);
        } catch (ConfigurationException e) {
            fail();
        }

        // Check if an eclipse attack is in progress (should not change
        // FederationNotificationProcessor panic status)
        notificationProcessor.checkIfNodeWasEclipsed();
        assertFalse(notificationProcessor.inPanicState());

        // Wait to prevent triggering the FederationNotificationProcessor's
        // anti-flooding control
        NotificationTestsUtils.waitMillis(3000);

        // Check if an eclipse attack is in progress (should change
        // FederationNotificationProcessor panic status to UNDER_ECLIPSE_ATTACK)
        notificationProcessor.checkIfNodeWasEclipsed();
        assertEquals(PanicStatusReason.FEDERATION_ECLIPSED, notificationProcessor.getPanicStatus().getReason());
        assertEquals(BEST_BLOCK, notificationProcessor.getPanicSinceBlockNumber());
    }
}
