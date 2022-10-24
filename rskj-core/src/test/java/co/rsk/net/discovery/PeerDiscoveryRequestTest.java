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
import co.rsk.net.discovery.message.PingPeerMessage;
import co.rsk.net.discovery.message.PongPeerMessage;
import co.rsk.net.discovery.table.PeerDiscoveryRequestBuilder;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Created by mario on 20/02/17.
 */
class PeerDiscoveryRequestTest {

    public static final int NETWORK_ID = 1;

    @Test
    void create() {
        ECKey key = new ECKey();
        String check = UUID.randomUUID().toString();
        PingPeerMessage pingPeerMessage = PingPeerMessage.create("localhost", 80, check, key, NETWORK_ID);
        PongPeerMessage pongPeerMessage = PongPeerMessage.create("localhost", 80, check, key, NETWORK_ID);
        InetSocketAddress address = new InetSocketAddress("localhost", 8080);

        PeerDiscoveryRequest request = PeerDiscoveryRequestBuilder.builder().messageId(check)
                .message(pingPeerMessage).address(address).expectedResponse(DiscoveryMessageType.PONG)
                .expirationPeriod(1000).attemptNumber(1).build();

        Assertions.assertNotNull(request);
        Assertions.assertTrue(request.validateMessageResponse(address, pongPeerMessage));
        Assertions.assertFalse(request.validateMessageResponse(address, pingPeerMessage));
    }
}
