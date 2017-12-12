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

package co.rsk.net.discovery.message;

/**
 * Created by mario on 13/02/17.
 */
public class PeerDiscoveryMessageFactory {
    private PeerDiscoveryMessageFactory() {}

    public static PeerDiscoveryMessage createMessage(byte[] wire, byte[] mdc, byte[] signature, byte[] type, byte[] data) {
        DiscoveryMessageType msgType = DiscoveryMessageType.valueOfType(type[0]);

        if (msgType == DiscoveryMessageType.PING) {
            return new PingPeerMessage(wire, mdc, signature, type, data);
        }

        if (msgType == DiscoveryMessageType.PONG) {
            return new PongPeerMessage(wire, mdc, signature, type, data);
        }

        if (msgType == DiscoveryMessageType.FIND_NODE) {
            return new FindNodePeerMessage(wire, mdc, signature, type, data);
        }

        return new NeighborsPeerMessage(wire, mdc, signature, type, data);
    }
}
