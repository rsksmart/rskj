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

import co.rsk.config.RskSystemProperties;
import co.rsk.net.eth.RskMessage;
import co.rsk.net.notifications.FederationNotification;
import org.ethereum.net.eth.message.EthMessage;
import org.ethereum.net.server.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/***
 * Broadcast {@link FederationNotification} notifications
 * to active peers.
 *
 * @author Diego Masini
 * @author Jose Orlicki
 * @author Ariel Mendelzon
 *
 */
public class NodeFederationNotificationBroadcaster implements FederationNotificationBroadcaster {
    private static final int MAX_NOTIFICATION_SIZE_IN_BYTES_FOR_FULL_BROADCAST = 500;

    private static final Logger logger = LoggerFactory.getLogger("NodeFederationNotificationBroadcaster");

    private RskSystemProperties config;

    public NodeFederationNotificationBroadcaster(RskSystemProperties config) {
        this.config = config;
    }

    @Override
    public void broadcast(Collection<Channel> activePeers, FederationNotification notification) {
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
}
