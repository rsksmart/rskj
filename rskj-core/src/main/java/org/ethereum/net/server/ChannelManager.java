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

package org.ethereum.net.server;

import co.rsk.net.NodeID;
import co.rsk.net.Status;
import co.rsk.net.messages.MessageWithId;
import org.ethereum.core.Block;
import org.ethereum.core.BlockIdentifier;
import org.ethereum.core.Transaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Ruben Altman
 * @since 9/6/2016
 * Added to make unit testing easier
 */

public interface ChannelManager {

    void start();
    void stop();

    boolean isRecentlyDisconnected(InetAddress peerAddr);


    /**
     * broadcastBlock Propagates a block message across active peers with exclusion of
     * the peers with an id belonging to the skip set.
     *
     * @param block new Block to be sent
     * @return a set containing the ids of the peers that received the block.
     */
    @Nonnull
    Set<NodeID> broadcastBlock(@Nonnull final Block block);

    @Nonnull
    Set<NodeID> broadcastBlockHash(@Nonnull final List<BlockIdentifier> identifiers, @Nullable final Set<NodeID> targets);

    /**
     * broadcastTransaction Propagates a transaction message across active peers with exclusion of
     * the peers with an id belonging to the skip set.
     *
     * @param transaction new Transaction to be sent
     * @param skip  the set of peers to avoid sending the message.
     * @return a set containing the ids of the peers that received the transaction.
     */
    @Nonnull
    Set<NodeID> broadcastTransaction(@Nonnull final Transaction transaction, @Nullable final Set<NodeID> skip);

    int broadcastStatus(@Nonnull final Status status);

    void add(Channel peer);

    void notifyDisconnect(Channel channel);

    void onSyncDone(boolean done) ;

    Collection<Channel> getActivePeers();

    boolean sendMessageTo(NodeID nodeID, MessageWithId message);

    boolean isAddressBlockAvailable(InetAddress address);
}
