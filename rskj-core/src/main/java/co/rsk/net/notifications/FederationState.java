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
import co.rsk.net.notifications.panics.PanicStatus;
import co.rsk.net.notifications.processing.FederationNotificationProcessor;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/***
 * Keep track of the current state of the node regarding
 * {@link FederationNotification} notifications.
 *
 * Current state is composed of a history of alerts triggered by these notifications
 * and a panic status into which the node can go into depending on
 * the latest notifications received. It also keeps track of the latest notifications
 * received from each of the federation members.
 *
 * This is the single source-of-truth wrt the node's state in this regard, and
 * its updated upon processing of notifications by the
 * {@link FederationNotificationProcessor}, and queried when needed by other
 * components of the system.
 *
 * @author Diego Masini
 * @author Jose Orlicki
 * @author Ariel Mendelzon
 *
 */
public class FederationState {
    private static final int MAX_FEDERATION_ALERTS = 100;

    private static final Logger logger = LoggerFactory.getLogger("FederationState");

    private PanicStatus panicStatus;
    private final Queue<FederationAlert> alerts;
    private final Map<FederationMember, FederationNotification> latestFederationNotifications;
    private volatile Instant lastNotificationReceivedTime;

    private RskSystemProperties config;

    public FederationState(RskSystemProperties config) {
        this.config = config;
        this.alerts = new CircularFifoQueue<>(MAX_FEDERATION_ALERTS);
        this.latestFederationNotifications = new HashMap<>();
        this.panicStatus = new PanicStatus();

        // Assume we received a notification some time ago for simplicity's sake
        this.lastNotificationReceivedTime = Instant.now().minusSeconds(config.maxSecondsBetweenNotifications() * 2);
    }

    /**
     * Updates the current state wrt the latest notification received.
     *
     * @param notification The notification received.
     */
    public void processNotification(FederationNotification notification) {
        latestFederationNotifications.put(notification.getFederationMember().get(), notification);

        // Update timestamp of last notification received to later check if
        // communications with federation are still alive
        lastNotificationReceivedTime = Instant.now();

        // Panic processing
        if (config.shouldFederationNotificationsTriggerPanic()) {
            // Receiving a notification triggers clearing the NODE_ECLIPSED flag
            panicStatus.unset(PanicFlag.of(PanicFlag.Reason.NODE_ECLIPSED));
        }
    }

    /**
     * Updates the current state of the federation wrt the latest alerts generated at a given block number.
     * This includes updating the corresponding panic flags.
     *
     * @param receivedBlockNumber The block number at which the alerts were generated.
     * @param triggeredAlerts The alerts triggered.
     */
    public void processAlerts(long receivedBlockNumber, List<FederationAlert> triggeredAlerts) {
        // Add the alerts
        alerts.addAll(triggeredAlerts);

        // Panic processing
        if (config.shouldFederationNotificationsTriggerPanic()) {
            // Unless stated in an alert, panic flags FEDERATION_FORKED, NODE_FORKED and FEDERATION_FROZEN
            // should be off
            panicStatus.unset(PanicFlag.of(PanicFlag.Reason.FEDERATION_FORKED));
            panicStatus.unset(PanicFlag.of(PanicFlag.Reason.NODE_FORKED));
            panicStatus.unset(PanicFlag.of(PanicFlag.Reason.FEDERATION_FROZEN));

            // Process alerts to set panic flags
            triggeredAlerts.forEach(alert -> {
                panicStatus.set(alert.getAssociatedPanicFlag(receivedBlockNumber));
            });
        }

        // Log
        logger.info("Federation alerts generated ({}). Panic status is now {}", triggeredAlerts.size(), getPanicStatus());
    }

    /***
     * Returns an immutable list with the latest FederationAlerts
     */
    public List<FederationAlert> getAlerts() {
        return Collections.unmodifiableList(new ArrayList(alerts));
    }

    /***
     * Returns the current panic status
     */
    public PanicStatus getPanicStatus() {
        return panicStatus;
    }

    /**
     * Returns the last time at which a notification was received
     */
    public Instant getLastNotificationReceivedTime() {
        return lastNotificationReceivedTime;
    }
}
