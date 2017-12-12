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

package co.rsk.net.discovery.table;

import co.rsk.net.discovery.PeerDiscoveryRequest;
import co.rsk.net.discovery.message.DiscoveryMessageType;
import co.rsk.net.discovery.message.PeerDiscoveryMessage;
import org.ethereum.net.rlpx.Node;

import java.net.InetSocketAddress;

/**
 * Created by mario on 22/02/17.
 */
public class PeerDiscoveryRequestBuilder {
    private String messageId;
    private PeerDiscoveryMessage message;
    private InetSocketAddress address;
    private DiscoveryMessageType expectedResponse;
    private long expirationPeriod;
    private int attemptNumber;
    private Node relatedNode;

    private PeerDiscoveryRequestBuilder() {
    }

    public static PeerDiscoveryRequestBuilder builder() {
        return new PeerDiscoveryRequestBuilder();
    }

    public PeerDiscoveryRequestBuilder messageId(String check) {
        this.messageId = check;
        return this;
    }

    public PeerDiscoveryRequestBuilder message(PeerDiscoveryMessage message) {
        this.message = message;
        return this;
    }

    public PeerDiscoveryRequestBuilder address(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public PeerDiscoveryRequestBuilder expectedResponse(DiscoveryMessageType expectedResponse) {
        this.expectedResponse = expectedResponse;
        return this;
    }

    public PeerDiscoveryRequestBuilder expirationPeriod(long expirationPeriod) {
        this.expirationPeriod = expirationPeriod;
        return this;
    }

    public PeerDiscoveryRequestBuilder attemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
        return this;
    }

    public PeerDiscoveryRequestBuilder relatedNode(Node relatedNode) {
        this.relatedNode = relatedNode;
        return this;
    }

    public PeerDiscoveryRequest build() {
        return new PeerDiscoveryRequest(this.messageId, this.message, this.address, this.expectedResponse, this.expirationPeriod, this.attemptNumber, this.relatedNode);
    }


}
