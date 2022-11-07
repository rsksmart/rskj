/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.net.rlpx;

import co.rsk.net.NodeID;
import co.rsk.util.MaxSizeHashMap;

import java.net.InetSocketAddress;
import java.util.Map;

public class NodeAddressCache {

    private static final int MAX_CACHE_SIZE = 40; // ~ max active peers + margin

    private final Map<NodeID, InetSocketAddress> cache;

    public NodeAddressCache() {
        this.cache = new MaxSizeHashMap<>(MAX_CACHE_SIZE, true);
    }

    public InetSocketAddress get(NodeID node) {
        return cache.get(node);
    }

    public void set(NodeID node, InetSocketAddress address) {
        this.cache.put(node, address);
    }
}
