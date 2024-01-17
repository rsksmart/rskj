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

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ethereum.net.rlpx.Node;

/**
 * Created by mario on 22/02/17.
 */
public class NodeChallenge {
    private final Node challengedNode;
    private final Node challenger;
    private final String challengeId;

    public NodeChallenge(Node challengedNode, Node challenger, String challengeId) {
        this.challengedNode = challengedNode;
        this.challenger = challenger;
        this.challengeId = challengeId;
    }

    public Node getChallengedNode() {
        return challengedNode;
    }

    public Node getChallenger() {
        return challenger;
    }

    public String getChallengeId() {
        return challengeId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("challengedNode", this.challengedNode)
                .append("challenger", this.challenger)
                .append("challengeId", this.challengeId)
                .toString();
    }
}
