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
import co.rsk.net.eth.RskMessage;
import co.rsk.net.messages.MessageType;
import co.rsk.net.notifications.NotificationTestsUtils.FederationMember;
import co.rsk.net.notifications.utils.FederationNotificationSigner;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.server.ChannelManager;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/***
 *
 * @author Diego Masini
 * @author Jose Orlicki
 *
 */
public class NodeFederationNotificationSourceTest {
    private static long BEST_BLOCK = 100000l;
    private static int FIRST_CONFIRMATION_DEPTH = 10;
    private static int SECOND_CONFIRMATION_DEPTH = 100;
    private static RskSystemProperties config;
    private static FederationMember federationMember = NotificationTestsUtils.getFederationMember();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Constants constants = mock(Constants.class);
        when(constants.getBridgeConstants()).thenReturn(BridgeRegTestConstants.getInstance());

        BlockchainNetConfig blockchainNetConfig = mock(BlockchainNetConfig.class);
        when(blockchainNetConfig.getCommonConstants()).thenReturn(constants);

        config = mock(RskSystemProperties.class);
        when(config.maxSecondsBetweenNotifications()).thenReturn(1);
        when(config.getFederationMaxSilenceTimeSecs()).thenReturn(60);
        when(config.getFederationNotificationTTLSecs()).thenReturn(120);
        when(config.federationNotificationsEnabled()).thenReturn(true);
        when(config.shouldFederationNotificationsTriggerPanic()).thenReturn(true);
        when(config.getFederationConfirmationDepths())
                .thenReturn(Arrays.asList(FIRST_CONFIRMATION_DEPTH, SECOND_CONFIRMATION_DEPTH));
        when(config.getFederationConfirmationIndex()).thenReturn(1);
        when(config.getBlockchainConfig()).thenReturn(blockchainNetConfig);
        when(config.localCoinbaseAccount()).thenReturn(new Account(federationMember.getKey()));
        when(config.coinbaseAddress()).thenReturn(federationMember.getAddress());
    }

    @Test
    public void generateFederationNotification() {
        // Create an Answer for the ChannelManager mock to execute all the required asserts on
        // the notification.
        Answer<Void> asserts = new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                RskMessage message = (RskMessage) args[0];

                FederationNotification notification = (FederationNotification) message.getMessage();
                ECKey publicKey = ECKey.fromPublicOnly(federationMember.getKey().getPubKey());

                assertEquals(MessageType.FEDERATION_NOTIFICATION, notification.getMessageType());
                assertEquals(federationMember.getAddress(), notification.getSource());
                assertTrue(notification.verifySignature(publicKey));
                assertFalse(notification.isExpired());
                assertTrue(notification.hasConfirmations());
                assertEquals(BEST_BLOCK - FIRST_CONFIRMATION_DEPTH, notification.getConfirmation(0).getBlockNumber());
                assertEquals(NotificationTestsUtils.hash(BEST_BLOCK - FIRST_CONFIRMATION_DEPTH),
                        notification.getConfirmation(0).getBlockHash());
                assertEquals(BEST_BLOCK - SECOND_CONFIRMATION_DEPTH, notification.getConfirmation(1).getBlockNumber());
                assertEquals(NotificationTestsUtils.hash(BEST_BLOCK - SECOND_CONFIRMATION_DEPTH),
                        notification.getConfirmation(1).getBlockHash());

                return null;
            }
        };

        // Get a Blockchain.
        Blockchain blockchain = NotificationTestsUtils.buildInSyncBlockchain(BEST_BLOCK);

        // Get a ChannelManager
        ChannelManager channelManager = NotificationTestsUtils.getChannelManagerWithAsserts(1, asserts);

        // Get a signer
        FederationNotificationSigner signer = NotificationTestsUtils.getSigner(config);

        // Create the FederationNotificationSource test instance.
        FederationNotificationSource notificationSource = new FederationNotificationSourceImpl(config, blockchain, channelManager, signer);

        // Generate the notification.
        notificationSource.generateNotification();
    }

    @Test(expected = FederationNotificationException.class)
    public void failedToSignFederationNotification() {
        // Create a config object local to this test without localCoinbaseAccount and
        // coinbaseAddress.
        Constants constants = mock(Constants.class);
        when(constants.getBridgeConstants()).thenReturn(BridgeRegTestConstants.getInstance());

        BlockchainNetConfig blockchainNetConfig = mock(BlockchainNetConfig.class);
        when(blockchainNetConfig.getCommonConstants()).thenReturn(constants);

        RskSystemProperties config = mock(RskSystemProperties.class);
        when(config.maxSecondsBetweenNotifications()).thenReturn(1);
        when(config.getFederationNotificationTTLSecs()).thenReturn(120);
        when(config.federationNotificationsEnabled()).thenReturn(true);
        when(config.getFederationConfirmationDepths())
                .thenReturn(Arrays.asList(FIRST_CONFIRMATION_DEPTH, SECOND_CONFIRMATION_DEPTH));
        when(config.getBlockchainConfig()).thenReturn(blockchainNetConfig);

        // Commented out the next two lines to cause the FederationNotificationSource
        // failed to find a valid account for signing
        // the notification. This should cause a FederationNotificationException to be
        // thrown.
        // when(config.localCoinbaseAccount()).thenReturn(new
        // Account(federationMember.getKey()));
        // when(config.coinbaseAddress()).thenReturn(federationMember.getAddress());

        // Get a Blockchain.
        Blockchain blockchain = NotificationTestsUtils.buildInSyncBlockchain(BEST_BLOCK);

        // Get a ChannelManager
        ChannelManager channelManager = NotificationTestsUtils.getChannelManager(1);

        // Get a signer
        FederationNotificationSigner signer = NotificationTestsUtils.getSigner(config);

        // Create the FederationNotificationSource test instance.
        FederationNotificationSource notificationSource = new FederationNotificationSourceImpl(config, blockchain, channelManager, signer);

        // Generate the notification.
        notificationSource.generateNotification();
    }

    @Test(expected = IllegalArgumentException.class)
    public void tryToAccessConfirmationFromNotificationWithInvalidIndex() {
        // Create an Answer for the ChannelManager mock to execute all the required asserts on
        // the notification.
        Answer<Void> asserts = new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                RskMessage message = (RskMessage) args[0];

                FederationNotification notification = (FederationNotification) message.getMessage();
                ECKey publicKey = ECKey.fromPublicOnly(federationMember.getKey().getPubKey());

                assertEquals(MessageType.FEDERATION_NOTIFICATION, notification.getMessageType());
                assertEquals(federationMember.getAddress(), notification.getSource());
                assertTrue(notification.verifySignature(publicKey));
                assertFalse(notification.isExpired());
                assertTrue(notification.hasConfirmations());

                // Notification only has 2 confirmations, indexes 0 and 1, trying to access a
                // confirmation
                // with index 2 should throw an IllegalArgumentException.
                notification.getConfirmation(2).getBlockNumber();

                return null;
            }
        };

        // Get a Blockchain.
        Blockchain blockchain = NotificationTestsUtils.buildInSyncBlockchain(BEST_BLOCK);

        // Get a ChannelManager
        ChannelManager channelManager = NotificationTestsUtils.getChannelManagerWithAsserts(1, asserts);

        // Get a signer
        FederationNotificationSigner signer = NotificationTestsUtils.getSigner(config);

        // Create the FederationNotificationSource test instance.
        FederationNotificationSource notificationSource = new FederationNotificationSourceImpl(config, blockchain, channelManager, signer);

        // Generate the notification.
        notificationSource.generateNotification();
    }

    @Test
    public void generateNotificationFromForkedBlockchain() {
        // Create an Answer for the ChannelManager mock to execute all the required asserts on
        // the notification.
        Answer<Void> asserts = new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                RskMessage message = (RskMessage) args[0];

                FederationNotification notification = (FederationNotification) message.getMessage();
                ECKey publicKey = ECKey.fromPublicOnly(federationMember.getKey().getPubKey());

                assertEquals(MessageType.FEDERATION_NOTIFICATION, notification.getMessageType());
                assertEquals(federationMember.getAddress(), notification.getSource());
                assertTrue(notification.verifySignature(publicKey));
                assertFalse(notification.isExpired());

                // Notification generated from the forked blockchain will not have confirmations
                // due to
                // the blockchain mock configuration (it returns null to all getBlockByNumber
                // requests).
                assertFalse(notification.hasConfirmations());

                return null;
            }
        };
        // Get a blockchain.
        Blockchain blockchain = NotificationTestsUtils.buildForkedBlockchain(BEST_BLOCK);

        // Get a ChannelManager
        ChannelManager channelManager = NotificationTestsUtils.getChannelManagerWithAsserts(1, asserts);

        // Get a signer
        FederationNotificationSigner signer = NotificationTestsUtils.getSigner(config);

        // Create the FederationNotificationSource test instance.
        FederationNotificationSource notificationSource = new FederationNotificationSourceImpl(config, blockchain, channelManager, signer);

        // Generate the notification.
        notificationSource.generateNotification();
    }
}
