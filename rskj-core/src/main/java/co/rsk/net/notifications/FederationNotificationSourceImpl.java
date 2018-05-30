package co.rsk.net.notifications;

import co.rsk.config.RskSystemProperties;
import co.rsk.net.eth.RskMessage;
import co.rsk.net.notifications.utils.FederationNotificationSigner;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/***
 * Generates and broadcasts
 * {@link FederationNotification} notifications with
 * confirmation data: a list of block numbers and associated block hashes. The
 * exact amount of confirmations included in the notification is configurable
 * along with the depth of the blocks used for the confirmations. The generated
 * notifications are signed with the originator private key.
 *
 * @author Diego Masini
 * @author Jose Orlicki
 *
 */

@Component
public class FederationNotificationSourceImpl implements FederationNotificationSource {
    private static final Logger logger = LoggerFactory.getLogger("FederationNotificationSource");

    // Interval in seconds to check for the last notification sent. Notifications
    // are generated on a per
    // block basis, meaning, if no new block is processed notifications will not be
    // generated. We will
    // trigger a notification if no new block is received after
    // LAST_NOTIFICATION_SENT_CHECK_INTERVAL_SECS
    // seconds.
    private static final long LAST_NOTIFICATION_SENT_CHECK_INTERVAL_SECS = 75;

    private final RskSystemProperties config;
    private final Blockchain blockchain;
    private final ChannelManager channelManager;
    private final FederationNotificationSigner signer;
    private final ReentrantLock blockchainLock;

    private ScheduledExecutorService lastNotificationSentChecker;
    private volatile Instant lastNotificationSentTime;

    @Autowired
    public FederationNotificationSourceImpl(RskSystemProperties config, Blockchain blockchain,
                                            ChannelManager channelManager, FederationNotificationSigner signer) {
        this.config = config;
        this.blockchain = blockchain;
        this.channelManager = channelManager;
        this.signer = signer;
        this.blockchainLock = new ReentrantLock();
    }

    /***
     * Generates a FederationNotification with confirmation data (a list of block
     * numbers and associated block hashes) and broadcasts it to the active peers.
     */
    @Override
    public void generateNotification() {
        // Check if it is time to send a new notification
        if (lastNotificationSentTime == null || Duration.between(lastNotificationSentTime, Instant.now())
                .getSeconds() >= config.maxSecondsBetweenNotifications()) {
            FederationNotification federationNotification = buildNotification(false);

            lastNotificationSentTime = Instant.now();

            logger.info("About to broadcast federation notification");
            broadcastFederationNotification(channelManager.getActivePeers(), federationNotification);
        }
    }

    private FederationNotification buildNotification(boolean frozen) {
        Instant timeToLive = Instant.now().plus(config.getFederationNotificationTTLSecs(), ChronoUnit.SECONDS);

        FederationNotification notification = new FederationNotification(config.coinbaseAddress(), timeToLive,
                Instant.now(), frozen);

        long bestBlockNumber = getBestBlock().getNumber();

        for (Integer depth : config.getFederationConfirmationDepths()) {
            long blockNumber = bestBlockNumber - depth;
            if (blockNumber < 0)
                continue;

            Block block = blockchain.getBlockByNumber(blockNumber);
            if (block == null)
                continue;

            notification.addConfirmation(blockNumber, block.getHash());
        }

        signNotification(notification);
        return notification;
    }

    @Override
    public void start() {
        scheduleLastNotificationSentChecker();
    }

    @Override
    public void stop() {
        if (lastNotificationSentChecker != null && !lastNotificationSentChecker.isShutdown()) {
            lastNotificationSentChecker.shutdownNow();
        }
    }

    private void scheduleLastNotificationSentChecker() {
        lastNotificationSentChecker = Executors.newScheduledThreadPool(1);
        lastNotificationSentChecker.scheduleAtFixedRate(() -> {
            Instant now = Instant.now();
            long secondsSinceLastNotification = Duration.between(lastNotificationSentTime, now).getSeconds();

            if (secondsSinceLastNotification > LAST_NOTIFICATION_SENT_CHECK_INTERVAL_SECS) {
                FederationNotification federationNotification = buildNotification(true);
                broadcastFederationNotification(channelManager.getActivePeers(), federationNotification);
            }

        }, 0, LAST_NOTIFICATION_SENT_CHECK_INTERVAL_SECS, TimeUnit.SECONDS);
    }

    private void broadcastFederationNotification(Collection<Channel> activePeers, FederationNotification notification) {
        final EthMessage message = new RskMessage(config, notification);
        if (activePeers.isEmpty()) {
            return;
        }

        activePeers.stream().forEach(c -> c.sendMessage(message));
    }

    private Block getBestBlock() {
        blockchainLock.lock();
        try {
            return blockchain.getBestBlock();
        } finally {
            blockchainLock.unlock();
        }
    }

    private void signNotification(FederationNotification notification) {
        signer.sign(notification);
    }
}
