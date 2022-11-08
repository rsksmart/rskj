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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Created by mario on 22/02/17.
 */
public class NodeChallengeManager {
    private Map<String, NodeChallenge> activeChallenges = new ConcurrentHashMap<>();

    public NodeChallenge startChallenge(Node challengedNode, Node challenger, Function<Node, PingPeerMessage> challengeFunc) {
        PingPeerMessage pingMessage = challengeFunc.apply(challengedNode);
        String messageId = pingMessage.getMessageId();
        NodeChallenge challenge = new NodeChallenge(challengedNode, challenger, messageId);
        activeChallenges.put(messageId, challenge);
        return challenge;
    }

    public NodeChallenge removeChallenge(String challengeId) {
        return activeChallenges.remove(challengeId);
    }

    @VisibleForTesting
    public int activeChallengesCount() {
        return activeChallenges.size();
    }
}
