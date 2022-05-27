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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the counter of messages per peer
 */
public class MessageCounter {

    private static final Logger logger = LoggerFactory.getLogger(MessageCounter.class);

    private static final AtomicInteger ZERO = new AtomicInteger(0);

    private static final String COUNTER_ERROR = "Counter for {} is null or negative: {}.";

    private final Map<NodeID, AtomicInteger> messagesPerNode = new ConcurrentHashMap<>();


    int getValue(Peer sender) {
        return Optional.ofNullable(messagesPerNode.get(sender.getPeerNodeID())).orElse(ZERO).intValue();
    }

    void increment(Peer sender) {
        messagesPerNode
            .computeIfAbsent(sender.getPeerNodeID(), this::createAtomicInteger)
            .incrementAndGet();
    }

    private AtomicInteger createAtomicInteger(NodeID nodeId) {
        return new AtomicInteger();
    }

    void decrement(Peer sender) {

        NodeID peerNodeID = sender.getPeerNodeID();
        AtomicInteger cnt = messagesPerNode.get(peerNodeID);

        if(cnt == null || cnt.get() < 0) {
            logger.error(COUNTER_ERROR, peerNodeID, cnt);
            return;
        }

        int newValue = cnt.decrementAndGet();

        // if this counter is zero or negative: remove key from map
        if(newValue < 1) {
            messagesPerNode.remove(peerNodeID);
        }

    }

    boolean hasCounter(Peer sender) {
        return messagesPerNode.containsKey(sender.getPeerNodeID());
    }

}
