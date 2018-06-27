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

package co.rsk.net.notifications.processing;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.config.BridgeConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryImpl;
import co.rsk.net.BlockProcessor;
import co.rsk.net.eth.RskMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.notifications.Confirmation;
import co.rsk.net.notifications.FederationNotification;
import co.rsk.net.notifications.FederationState;
import co.rsk.net.notifications.alerts.FederationAlert;
import co.rsk.net.notifications.alerts.FederationFrozenAlert;
import co.rsk.net.notifications.alerts.ForkAttackAlert;
import co.rsk.net.notifications.alerts.NodeEclipsedAlert;
import co.rsk.net.notifications.processing.FederationNotificationProcessingResult;
import co.rsk.net.notifications.processing.FederationNotificationProcessor;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationSupport;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.server.Channel;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.naming.ConfigurationException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/***
 * Process {@link FederationNotification} notifications
 * received directly from members of the federation or from peers (forwarded
 * notifications). As a result of the processing this class might generate and
 * broadcast {@link co.rsk.net.notifications.alerts.FederationAlert}
 * notifications when these types of potential attacks are detected:
 *
 * - Potential eclipse attack: the node running this instance of the
 * NodeFederationNotificationProcessor was isolated from the Federation. It could
 * be due to an attack or it could mean the federation nodes are offline. @see
 * {@link NodeEclipsedAlert}
 *
 * - Fork attack: a node running this instance of the
 * NodeFederationNotificationProcessor detects that its best chain diverges from
 * the federation's best chain. @see
 * {@link co.rsk.net.notifications.alerts.ForkAttackAlert}
 *
 * @author Diego Masini
 * @author Jose Orlicki
 *
 */
public class NodeFederationNotificationProcessor implements FederationNotificationProcessor {
    private static final int MAX_NUMBER_OF_NOTIFICATIONS_CACHED = 5000;
    private static final int MAX_NOTIFICATION_SIZE_IN_BYTES_FOR_FULL_BROADCAST = 500;

    private static final Logger logger = LoggerFactory.getLogger("NodeFederationNotificationProcessor");

    private RskSystemProperties config;
    private BlockProcessor blockProcessor;
    private FederationState federationState;

    private NotificationBuffer<FederationNotification> receivedFederationNotifications = new NotificationBuffer<>(
            MAX_NUMBER_OF_NOTIFICATIONS_CACHED);

    public NodeFederationNotificationProcessor(RskSystemProperties config, BlockProcessor blockProcessor, FederationState federationState) {
        this.config = config;
        this.blockProcessor = blockProcessor;
        this.federationState = federationState;
    }

    /**
     * Validates the received FederationNotification, if valid forwards the
     * notification to the activePeers collection and generates ForkAttackAlerts if
     * a fork attack is detected.
     *
     * @throws ConfigurationException
     */
    @Override
    public FederationNotificationProcessingResult processFederationNotification(
            @Nonnull final Collection<Channel> activePeers, @Nonnull final FederationNotification notification)
            throws ConfigurationException {
        final Instant now = Instant.now();

        // Avoid flood attacks
        long timeBetweenNotifications = Duration.between(federationState.getLastNotificationReceivedTime(), now).getSeconds();
        if (timeBetweenNotifications < config.maxSecondsBetweenNotifications()) {
            logger.warn("Federation notification received too fast. Skipping it to avoid flood attacks.");
            return FederationNotificationProcessingResult.NOTIFICATION_RECEIVED_TOO_FAST;
        }

        // Reject expired notifications
        if (notification.isExpired()) {
            logger.warn("Federation notification has expired.");
            return FederationNotificationProcessingResult.NOTIFICATION_EXPIRED;
        }

        // Reject already received notifications
        if (receivedFederationNotifications.containsNotification(notification)) {
            logger.info("Federation notification already processed.");
            return FederationNotificationProcessingResult.NOTIFICATION_ALREADY_PROCESSED;
        }

        // Only accept notifications from valid federation members
        if (!verifyFederationNotificationSignature(notification)) {
            logger.warn("Federation notification signature does not verify.");
            return FederationNotificationProcessingResult.NOTIFICATION_SIGNATURE_DOES_NOT_VERIFY;
        }

        // Cache notification to keep track of already received notifications
        this.receivedFederationNotifications.addNotification(notification);

        // Compare confirmations contained in the notification with local best
        // chain to generate alerts (if needed)
        this.generateAlertIfNeeded(notification);

        // Propagate federation notification to peers
        broadcastFederationNotification(activePeers, notification);

        logger.debug("Federation notification processed successfully.");
        return FederationNotificationProcessingResult.NOTIFICATION_PROCESSED_SUCCESSFULLY;
    }

    /**
     * Checks the time of the last FederationNotification received does not exceed a
     * configurable delta. If so, a NodeEclipsedAlert is generated and processed accordingly.
     * This alert indicates that either the node was isolated from the federation
     * by an attacker or the federation nodes are offline.
     */
    @Override
    public void checkIfNodeWasEclipsed() {
        // Maybe someone is eclipsing the Federation for this node or the federation nodes are offline.
        Duration duration = Duration.between(federationState.getLastNotificationReceivedTime(), Instant.now());
        int maxSilenceSecs = config.getFederationMaxSilenceTimeSecs();
        if (config.federationNotificationsEnabled() && duration.getSeconds() > maxSilenceSecs) {
            FederationAlert alert = new NodeEclipsedAlert(duration.getSeconds());
            federationState.processAlerts(getBestBlockNumber(), Arrays.asList(alert));
        }
    }

    /***
     * checks if a Fork attack is in progress, if so generates a ForkAttackAlert and
     * changes the processor's panic status to the corresponding FEDERATION_FORKED
     * (if the node is a federation member) or NODE_FORKED (if the node is not a
     * federation member)
     *
     * @param notification
     * @throws ConfigurationException
     */
    private void generateAlertIfNeeded(FederationNotification notification) throws ConfigurationException {
        // Ignore notifications while syncing blocks
        // TODO: figure out where to put this. Maybe better @ message processor level?
        if (this.blockProcessor.hasBetterBlockToSync()) {
            return;
        }

        List<FederationAlert> alerts = new ArrayList<>();

        // Get the confirmation we wish to compare from the notification
        int federationConfirmationIndex = this.config.getFederationConfirmationIndex();
        Confirmation confirmation = notification.getConfirmation(federationConfirmationIndex);

        // Is the federation frozen?
        // TODO: 51% algorithm
        if (notification.isFederationFrozen()) {
            alerts.add(new FederationFrozenAlert(notification.getSource(), confirmation.getBlockHash(), confirmation.getBlockNumber()));
        }

        // Get the corresponding block from the local blockchain to compare and
        // see if we are under a fork attack
        long bestBlockNumber = getBestBlockNumber();
        Block block = blockProcessor.getBlockchain().getBlockByNumber(confirmation.getBlockNumber());

        // Are we under a fork attack?
        // TODO: 51% algorithm
        if (block == null || !block.getHash().equals(confirmation.getBlockHash())) {
            Keccak256 blockHash = block == null ? null : block.getHash();
            boolean isFederatedNode = isFederationMember();

            alerts.add(new ForkAttackAlert(notification.getSource(), confirmation.getBlockHash(),
                    confirmation.getBlockNumber(), blockHash, bestBlockNumber, isFederatedNode));
        }

        // Update the state
        federationState.processNotification(notification);
        federationState.processAlerts(bestBlockNumber, alerts);
    }

    private boolean isFederationMember() {
        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        BridgeStorageProvider provider = new BridgeStorageProvider(new RepositoryImpl(config),
                PrecompiledContracts.BRIDGE_ADDR, bridgeConstants);
        FederationSupport federationSupport = new FederationSupport(provider, bridgeConstants, getBestBlock());

        Federation federation = federationSupport.getActiveFederation();
        return federation.hasMemberWithRskAddress(config.coinbaseAddress().getBytes());
    }

    private boolean verifyFederationNotificationSignature(FederationNotification notification) {
        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        BridgeStorageProvider provider = new BridgeStorageProvider(new RepositoryImpl(config),
                PrecompiledContracts.BRIDGE_ADDR, bridgeConstants);
        FederationSupport federationSupport = new FederationSupport(provider, bridgeConstants, getBestBlock());

        Federation federation = federationSupport.getActiveFederation();
        List<BtcECKey> federationKeys = federation.getPublicKeys();

        // Check if the notification signature can be verified by one of the federation
        // members public keys
        for (BtcECKey btcECKey : federationKeys) {
            ECKey publicKey = ECKey.fromPublicOnly(btcECKey.getPubKey());
            if (notification.verifySignature(publicKey)) {
                return true;
            }
        }

        return false;
    }

    private void broadcastFederationNotification(Collection<Channel> activePeers, FederationNotification notification) {
        final EthMessage ethMessage = new RskMessage(config, notification);
        synchronized (activePeers) {
            if (activePeers.isEmpty()) {
                return;
            }

            int notificationSize = notification.getEncodedMessage().length;

            if (notificationSize > MAX_NOTIFICATION_SIZE_IN_BYTES_FOR_FULL_BROADCAST) {
                partialBroadcast(activePeers, ethMessage);
            } else {
                fullBroadcast(activePeers, ethMessage);
            }
        }
    }

    private void partialBroadcast(Collection<Channel> activePeers, EthMessage message) {
        int peerCount = activePeers.size();
        int numberOfPeersToSendNotificationTo = Math.min(10,
                Math.min(Math.max(3, (int) Math.sqrt(peerCount)), peerCount));

        List<Channel> peers = new ArrayList<>(activePeers);
        Collections.shuffle(peers);
        activePeers.stream().limit(numberOfPeersToSendNotificationTo).forEach(c -> c.sendMessage(message));
    }

    private void fullBroadcast(Collection<Channel> activePeers, EthMessage message) {
        activePeers.stream().forEach(c -> c.sendMessage(message));
    }

    private Block getBestBlock() {
        Blockchain blockchain = blockProcessor.getBlockchain();
        if (blockchain == null) {
            return null;
        }

        return blockchain.getBestBlock();
    }

    private long getBestBlockNumber() {
        Block block = getBestBlock();
        if (block == null) {
            return -1;
        }

        return block.getNumber();
    }

    /***
     * Thread safe NotificationBuffer
     *
     * @author Diego Masini
     *
     * @param <T>
     */
    private static class NotificationBuffer<T extends Message> {
        private Set<Keccak256> notifications;

        public NotificationBuffer(int maxCapacity) {
            LinkedHashMap<Keccak256, Boolean> map = new LinkedHashMap<Keccak256, Boolean>() {

                private static final long serialVersionUID = -1178200507142208744L;

                protected boolean removeEldestEntry(Map.Entry<Keccak256, Boolean> eldest) {
                    return size() > maxCapacity;
                }
            };

            this.notifications = Collections.synchronizedSet(Collections.newSetFromMap(map));
        }

        public boolean containsNotification(T notification) {
            Keccak256 encoded = new Keccak256(HashUtil.keccak256(notification.getEncoded()));
            return notifications.contains(encoded);
        }

        public void addNotification(T notification) {
            notifications.add(new Keccak256(HashUtil.keccak256(notification.getEncoded())));
        }
    }
}
