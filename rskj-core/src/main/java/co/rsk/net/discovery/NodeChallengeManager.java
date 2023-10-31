/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net.discovery;

import co.rsk.net.discovery.message.PingPeerMessage;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.net.rlpx.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by mario on 22/02/17.
 */
public class NodeChallengeManager {
    private static final Logger logger = LoggerFactory.getLogger(NodeChallengeManager.class);

    private Map<String, NodeChallenge> activeChallenges = new ConcurrentHashMap<>();

    public NodeChallenge startChallenge(Node challengedNode, Node challenger, PeerExplorer explorer) {
        logger.debug("startChallenge - Starting challenge for node: [{}] by challenger: [{}]",
                challengedNode.getHexId(), challenger.getHexId());

        PingPeerMessage pingMessage = explorer.sendPing(challengedNode.getAddress(), 1, challengedNode);
        String messageId = pingMessage.getMessageId();
        NodeChallenge challenge = new NodeChallenge(challengedNode, challenger, messageId);
        activeChallenges.put(messageId, challenge);
        return challenge;
    }

    public NodeChallenge removeChallenge(String challengeId) {
        NodeChallenge removedChallenge = activeChallenges.remove(challengeId);

        logger.debug("removeChallenge - Removed challenge: [{}]", removedChallenge);

        return removedChallenge;
    }

    @VisibleForTesting
    public int activeChallengesCount() {
        return activeChallenges.size();
    }
}
