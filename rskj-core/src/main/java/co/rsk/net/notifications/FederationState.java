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

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.config.BridgeConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryImpl;
import co.rsk.net.eth.RskMessage;
import co.rsk.net.messages.Message;
import co.rsk.net.notifications.alerts.FederationAlert;
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
    private final List<FederationAlert> federationAlerts;
    private final Map<FederationMember, FederationNotification> latestFederationNotifications;
    private volatile Optional<Instant> lastNotificationReceivedTime;

    private RskSystemProperties config;

    public FederationState(RskSystemProperties config) {
        this.config = config;
        this.federationAlerts = new ArrayList<>();
        this.latestFederationNotifications = new HashMap<>();
        this.panicStatus = PanicStatus.NoPanic(0);
        this.lastNotificationReceivedTime = Optional.empty();
    }

    // TODO: alert should be several alerts
    public void updateState(long blockNumber, Optional<FederationNotification> notification, Optional<FederationAlert> alert) {
        // Update latest notification for the corresponding federation member
        if (notification.isPresent()) {
            latestFederationNotifications.put(notification.get().getFederationMember().get(), notification.get());
        }

        // Update timestamp of last notification received to later check if
        // communications with federation are still alive
        this.lastNotificationReceivedTime = Optional.of(Instant.now());

        // Add the alert (if given) and trigger the corresponding panic state
        if (alert.isPresent()) {
            addFederationAlert(alert.get());

            logger.info("Federation alert generated. Panic status is {}. {}", getPanicStatus(), alert.get().getDescription());

            if (config.shouldFederationNotificationsTriggerPanic()) {
                this.panicStatus = alert.get().getAssociatedPanicStatus(blockNumber);
            }
        } else if (config.shouldFederationNotificationsTriggerPanic()) {
            logger.info("Cleaning panic status {}", getPanicStatus());
            this.panicStatus = PanicStatus.NoPanic(blockNumber);
        }
    }

    /***
     * Returns an immutable list with the latest FederationAlerts
     */
    public List<FederationAlert> getFederationAlerts() {
        return Collections.unmodifiableList(federationAlerts);
    }

    /***
     * Returns the current panic status
     */
    public PanicStatus getPanicStatus() {
        return panicStatus;
    }

    /***
     * Tells whether we are currently in panic state
     */
    public boolean inPanic() {
        return getPanicStatus().isPanic();
    }


    private void addFederationAlert(FederationAlert alert) {
        if (federationAlerts.size() > MAX_FEDERATION_ALERTS) {
            federationAlerts.remove(0);
        }
        federationAlerts.add(alert);
    }
}
