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

import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryImpl;
import co.rsk.net.messages.Message;
import co.rsk.net.notifications.Confirmation;
import co.rsk.net.notifications.FederationNotification;
import co.rsk.net.notifications.FederationNotificationSender;
import co.rsk.net.notifications.FederationState;
import co.rsk.net.notifications.alerts.FederationAlert;
import co.rsk.net.notifications.alerts.FederationFrozenAlert;
import co.rsk.net.notifications.alerts.ForkAttackAlert;
import co.rsk.net.notifications.alerts.NodeEclipsedAlert;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationSupport;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.naming.ConfigurationException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
 * @author Ariel Mendelzon
 *
 */
public class NodeFederationNotificationProcessor implements FederationNotificationProcessor {
    private static final int MAX_NUMBER_OF_NOTIFICATIONS_CACHED = 5000;
    private static final int DEFAULT_CHECK_INTERVAL_SECONDS = 10000;

    private static final Logger logger = LoggerFactory.getLogger("NodeFederationNotificationProcessor");

    private RskSystemProperties config;
    private Blockchain blockchain;
    private FederationState federationState;
    private MockAuthenticator authenticator;

    private ScheduledExecutorService checkTask;
    private boolean running;

    private NotificationBuffer<FederationNotification> receivedFederationNotifications = new NotificationBuffer<>(
            MAX_NUMBER_OF_NOTIFICATIONS_CACHED);
    private final Map<FederationNotificationSender, FederationNotification> latestFederationNotifications;

    private long checkIntervalInSeconds = DEFAULT_CHECK_INTERVAL_SECONDS;

    public NodeFederationNotificationProcessor(RskSystemProperties config, Blockchain blockchain, FederationState federationState, MockAuthenticator authenticator) {
        this.config = config;
        this.blockchain = blockchain;
        this.federationState = federationState;
        this.authenticator = authenticator;
        this.checkTask = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "NodeFederationNotificationProcessor"));
        this.running = false;
        this.latestFederationNotifications = new HashMap<>();
    }

    public long getCheckIntervalInSeconds() {
        return this.checkIntervalInSeconds;
    }

    public void setCheckIntervalInSeconds(long checkIntervalInSeconds) {
        this.checkIntervalInSeconds = checkIntervalInSeconds;
    }

    @Override
    public void start() {
        if (this.running) {
            throw new IllegalStateException("Unable to start: already running");
        }

        this.checkTask.scheduleAtFixedRate(() -> this.checkIfNodeWasEclipsed(), 0, this.getCheckIntervalInSeconds(), TimeUnit.SECONDS);

        this.running = true;
    }

    @Override
    public void stop() {
        if (!this.running) {
            throw new IllegalStateException("Unable to stop: not running");
        }

        if (!this.checkTask.isShutdown()) {
            this.checkTask.shutdownNow();
        }

        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Validates the received FederationNotification, if valid checks
     * for any alerts that need to be triggered as a consequence of
     * the notification itself and forwards those to the federation
     * state for bookkeeping and panic management.
     *
     * @throws ConfigurationException
     */
    @Override
    public FederationNotificationProcessingResult process(@Nonnull final FederationNotification notification)
            throws ConfigurationException {
        // Do not process anything if the service is not running
        if (!this.isRunning()) {
            return FederationNotificationProcessingResult.PROCESSOR_NOT_RUNNING;
        }

        final Instant now = Instant.now();

        // Avoid flood attacks
        long timeBetweenNotifications = Duration.between(federationState.getLastNotificationReceivedTime(), now).getSeconds();
        if (timeBetweenNotifications < config.maxSecondsBetweenNotifications()) {
            logger.warn("Federation notification received too fast. Skipping it to avoid flood attacks.");
            return FederationNotificationProcessingResult.NOTIFICATION_RECEIVED_TOO_FAST;
        }

        // Reject non-current notifications
        if (!notification.isCurrent()) {
            logger.warn("Federation notification has either expired or was emitted in the future.");
            return FederationNotificationProcessingResult.NOTIFICATION_INVALID_IN_TIME;
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

        // Keep track of already received notifications
        receivedFederationNotifications.addNotification(notification);
        latestFederationNotifications.put(notification.getSender(), notification);

        // Update the state
        federationState.processNotification(notification);

        // Compare confirmations contained in the notification with local best
        // chain to generate alerts (if needed)
        generateAlerts();

        logger.debug("Federation notification processed successfully.");
        return FederationNotificationProcessingResult.NOTIFICATION_PROCESSED_SUCCESSFULLY;
    }

    /**
     * Checks the time of the last FederationNotification received does not exceed a
     * configurable delta. If so, a NodeEclipsedAlert is generated and processed accordingly.
     * This alert indicates that either the node was isolated from the federation
     * by an attacker or the federation nodes are offline.
     */
    private void checkIfNodeWasEclipsed() {
        // Maybe someone is eclipsing the Federation for this node or the federation nodes are offline.
        Duration duration = Duration.between(federationState.getLastNotificationReceivedTime(), Instant.now());
        int maxSilenceSecs = config.getFederationMaxSilenceTimeSecs();
        if (config.federationNotificationsEnabled() && duration.getSeconds() > maxSilenceSecs) {
            FederationAlert alert = new NodeEclipsedAlert(Instant.now(), duration.getSeconds());
            federationState.processAlerts(blockchain.getBestBlock().getNumber(), Arrays.asList(alert));
        }
    }

    /***
     * Checks if a Fork attack is in progress, if so generates a ForkAttackAlert and
     * also checks whether the federation is frozen, if so generates a FederationFrozenAlert.
     *
     * @throws ConfigurationException
     */
    private void generateAlerts() throws ConfigurationException {
        List<FederationAlert> alerts = new ArrayList<>();

        // Compute the number of missing notifications
        // for the 51% algorithms' sake
        int missingNotificationsCount = authenticator.getNotifierCount() - latestFederationNotifications.size(); // Normally ZERO

        // Get the current best block
        Block bestBlock = blockchain.getBestBlock();

        // Is the federation frozen? Need more than half of the
        // notifiers to say so to trigger the corresponding alert
        // IMPORTANT: assume that no notification from a given federate
        // implies that member is frozen.
        int frozenFederatesCount = Long.valueOf(latestFederationNotifications.values().stream()
                .filter(n -> n.isFederationFrozen()).count()).intValue();
        frozenFederatesCount += missingNotificationsCount;

        if (frozenFederatesCount > authenticator.getNotifierCount() / 2) {
            alerts.add(new FederationFrozenAlert(Instant.now(), latestFederationNotifications.values().stream()
                    .filter(n -> n.isFederationFrozen()).map(n -> n.getSender()).collect(Collectors.toList())));
        }

        // Are we under a fork attack? Given the local setting indicating
        // which level of confidence we need wrt federate's blockchains (federationConfirmationIndex)
        // check whether at least 51% of the most recent notifications we have from
        // the federate nodes match (at least) that index -- a higher match
        // implies the lower index also matches.

        // Get the confirmation depth
        int federationConfirmationDepth = this.config.getFederationConfirmationDepth();

        // Convert it to specific heights within the local blockchain
        long federationConfirmationHeight = bestBlock.getNumber() - federationConfirmationDepth;

        // Count the number of notifications within the latest received
        // that represent a fork wrt the local blockchain
        // IMPORTANT: assume that no notification from a given federate
        // implies that we are forked wrt that member.
        int forkCount = Long.valueOf(latestFederationNotifications.values().stream()
                .filter(n -> {
                    Optional<Confirmation> confirmation = n.getConfirmationWithHeightAtLeast(federationConfirmationHeight);

                    // If no confirmations are found at the desired depth
                    // then this notification counts towards a fork alert
                    if (!confirmation.isPresent()) {
                        return true;
                    }

                    // Get the block at the confirmation's height in the local blockchain
                    Block block = blockchain.getBlockByNumber(confirmation.get().getBlockNumber());

                    // If hashes at the same height don't match, then we're looking at a fork
                    // wrt the notification at the configured depth
                    return !block.getHash().equals(confirmation.get().getBlockHash());
                }).count()).intValue();
        forkCount += missingNotificationsCount;

        if (forkCount > authenticator.getNotifierCount() / 2) {
            alerts.add(new ForkAttackAlert(Instant.now(), bestBlock, isFederationMember()));
        }

        // Update the state
        federationState.processAlerts(bestBlock.getNumber(), alerts);
    }

    private boolean isFederationMember() {
        // TODO: fix this by comparing to the currently active federation
        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        BridgeStorageProvider provider = new BridgeStorageProvider(new RepositoryImpl(config),
                PrecompiledContracts.BRIDGE_ADDR, bridgeConstants);
        FederationSupport federationSupport = new FederationSupport(provider, bridgeConstants, blockchain.getBestBlock());

        Federation federation = federationSupport.getActiveFederation();
        return federation.hasMemberWithRskAddress(config.coinbaseAddress().getBytes());
    }

    private boolean verifyFederationNotificationSignature(FederationNotification notification) {
//        BridgeConstants bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
//        BridgeStorageProvider provider = new BridgeStorageProvider(new RepositoryImpl(config),
//                PrecompiledContracts.BRIDGE_ADDR, bridgeConstants);
//        FederationSupport federationSupport = new FederationSupport(provider, bridgeConstants, blockchain.getBestBlock());
//
//        Federation federation = federationSupport.getActiveFederation();
//        List<BtcECKey> federationKeys = federation.getPublicKeys();
//
//        // Check if the notification signature can be verified by one of the federation
//        // members public keys
//        for (BtcECKey btcECKey : federationKeys) {
//            ECKey publicKey = ECKey.fromPublicOnly(btcECKey.getPubKey());
//            if (notification.verifySignature(publicKey)) {
//                return true;
//            }
//        }

        // TODO: implement this when implementing authentication
        return true;
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

    // TODO: replace this with actual authenticator
    public static class MockAuthenticator {
        public int getNotifierCount() {
            return BridgeRegTestConstants.getInstance().getGenesisFederation().getSize();
        }
    }
}
