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

import co.rsk.net.discovery.message.DiscoveryMessageType;
import co.rsk.net.discovery.message.PeerDiscoveryMessage;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ethereum.net.rlpx.Node;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Created by mario on 17/02/17.
 */
public class PeerDiscoveryRequest {
    private final String messageId;
    private final PeerDiscoveryMessage message;
    private final InetSocketAddress address;
    private final DiscoveryMessageType expectedResponse;
    private final long expirationDate;
    private final int attemptNumber;
    private final Node relatedNode;

    public PeerDiscoveryRequest(String messageId, PeerDiscoveryMessage message, InetSocketAddress address, DiscoveryMessageType expectedResponse, Long expirationPeriod, int attemptNumber, Node relatedNode) {
        this.messageId = messageId;
        this.message = message;
        this.address = address;
        this.expectedResponse = expectedResponse;
        this.expirationDate = System.currentTimeMillis() + expirationPeriod;
        this.attemptNumber = attemptNumber;
        this.relatedNode = relatedNode;
    }

    public String getMessageId() {
        return messageId;
    }

    public PeerDiscoveryMessage getMessage() {
        return message;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Node getRelatedNode() {
        return relatedNode;
    }

    public boolean validateMessageResponse(InetSocketAddress responseAddress, PeerDiscoveryMessage message) {
        return this.expectedResponse == message.getMessageType() && !this.hasExpired() && getAddress().equals(responseAddress);
    }

    public boolean hasExpired() {
        return System.currentTimeMillis() > expirationDate;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("messageId", this.messageId)
                .append("message", this.message.toString())
                .append("address", this.address.toString())
                .append("relatedNode", Optional.ofNullable(this.relatedNode).map(Node::toString).orElse(null))
                .toString();
    }
}
