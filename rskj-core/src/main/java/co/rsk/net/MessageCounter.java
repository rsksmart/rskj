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
package co.rsk.net;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps the counter of messages per peer
 */
public class MessageCounter {

    private static final AtomicInteger ZERO = new AtomicInteger(0);

    private Map<NodeID, AtomicInteger> messagesPerNode = new ConcurrentHashMap<>();

    public int getValue(Peer sender) {
        return Optional.ofNullable(messagesPerNode.get(sender.getPeerNodeID())).orElse(ZERO).intValue();
    }

    public void increment(Peer sender) {
        messagesPerNode
            .computeIfAbsent(sender.getPeerNodeID(), this::createAtomicInteger)
            .incrementAndGet();
    }

    private AtomicInteger createAtomicInteger(NodeID nodeId) {
        return new AtomicInteger();
    }

    public void decrement(Peer sender) {

        Optional.ofNullable(messagesPerNode.get(sender.getPeerNodeID()))
            .ifPresent(AtomicInteger::decrementAndGet);

        // if this counter is zero or negative: remove key from map
        Optional.ofNullable(messagesPerNode.get(sender.getPeerNodeID()))
            .filter(counter -> counter.get() < 1)
            .ifPresent(counter -> messagesPerNode.remove(sender.getPeerNodeID()));

    }

    public boolean hasCounter(Peer sender) {
        return messagesPerNode.containsKey(sender.getPeerNodeID());
    }

}
