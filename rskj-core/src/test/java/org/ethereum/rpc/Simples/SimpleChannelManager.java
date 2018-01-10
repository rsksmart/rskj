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
import co.rsk.net.Status;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.*;

/**
 * Created by Ruben on 09/06/2016.
 */
public class SimpleChannelManager implements ChannelManager {
    private List<Transaction> transactions = new ArrayList<>();
    private Set<NodeID> lastSkip;

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

    @Override
    public void sendTransaction(List<Transaction> tx, Channel receivedFrom) {

    }

    @Nonnull
    @Override
    public Set<NodeID> broadcastBlock(@Nonnull Block block, @Nullable Set<NodeID> skip) {
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
        this.lastSkip = skip;
        return new HashSet<>();
    }

    @Override
    public int broadcastStatus(Status status) {
        return 0;
    }

    @Override
    public void sendNewBlock(Block block, Channel receivedFrom) {

    }

    @Override
    public void add(Channel peer) {

    }

    @Override
    public void notifyDisconnect(Channel channel) {

    }

    @Override
    public void onSyncDone(boolean done) {

    }

    @Override
    public Collection<Channel> getActivePeers() {
        Collection<Channel> channels = new ArrayList<Channel>();
        channels.add(new Channel(null, null, null, null, null, null, null, null));
        channels.add(new Channel(null, null, null, null, null, null, null, null));
        return channels;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public Set<NodeID> getLastSkip() {
        return lastSkip;
    }

    public void setLastSkip(Set<NodeID> value) { lastSkip = value; }
}
