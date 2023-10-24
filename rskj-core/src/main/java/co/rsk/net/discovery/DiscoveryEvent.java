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

import co.rsk.net.discovery.message.PeerDiscoveryMessage;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.net.InetSocketAddress;

public class DiscoveryEvent {
    private final PeerDiscoveryMessage message;
    private final InetSocketAddress address;

    public DiscoveryEvent(PeerDiscoveryMessage m, InetSocketAddress a) {
        message = m;
        address = a;
    }

    public PeerDiscoveryMessage getMessage() {
        return message;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("message", this.message.toString())
                .append("address", this.address.toString())
                .toString();
    }
}
