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

package org.ethereum.rpc.Simples;

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.Status;
import co.rsk.net.simples.SimpleNode;
import co.rsk.net.simples.SimpleNodeChannel;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Ruben on 09/06/2016.
 */
public class SimpleChannelManager implements ChannelManager {
    private List<Transaction> transactions = new ArrayList<>();
    private Map<NodeID, Peer> simpleChannels = new ConcurrentHashMap<>();

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isRecentlyDisconnected(InetAddress peerAddr) {
        return false;
    }

    @Nonnull
    @Override
    public Set<NodeID> broadcastBlock(@Nonnull Block block) {
        return new HashSet<>();
    }

    @Nonnull
    @Override
    public Set<NodeID> broadcastBlockHash(@Nonnull List<BlockIdentifier> identifiers, @Nullable Set<NodeID> targets) {
        return new HashSet<>();
    }

    @Nonnull
    @Override
    public Set<NodeID> broadcastTransaction(@Nonnull Transaction transaction, @Nullable Set<NodeID> skip) {
        this.transactions.add(transaction);
        return new HashSet<>();
    }

    @Override
    public int broadcastStatus(Status status) {
        return 0;
    }

    @Override
    public void add(Channel peer) {
    }

    public Peer getMessageChannel(SimpleNode sender, SimpleNode receiver) {
        Peer channel = simpleChannels.get(sender.getNodeID());
        if (channel != null){
            return channel;
        }

        channel = new SimpleNodeChannel(sender, receiver);
        simpleChannels.put(channel.getPeerNodeID(), channel);
        return  channel;
    }

    @Override
    public void notifyDisconnect(Channel channel) {
    }

    @Override
    public Collection<Peer> getActivePeers() {
        return simpleChannels.values();
    }

    @Override
    public boolean isAddressBlockAvailable(InetAddress address) {
        return true;
    }

    @Override
    public Set<NodeID> broadcastTransactions(List<Transaction> transactions, Set<NodeID> nodeID) {
        transactions.forEach(tx -> broadcastTransaction(tx, nodeID));
        return new HashSet<>();
    }

    @Override
    public void setActivePeers(Map<NodeID, Channel> newActivePeers) {
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }
}
