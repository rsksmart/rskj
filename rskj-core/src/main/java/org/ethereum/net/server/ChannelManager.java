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
import co.rsk.net.messages.Message;
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
     * Propagates the transactions message across active peers with exclusion of
     * 'receivedFrom' peer.
     *
     * @param tx           transactions to be sent
     * @param receivedFrom the peer which sent original message or null if
     *                     the transactions were originated by this peer
     */
    void broadcastTransactionMessage(List<Transaction> tx, Channel receivedFrom);


    /**
     * broadcastBlock Propagates a block message across active peers with exclusion of
     * the peers with an id belonging to the skip set.
     *
     * @param block new Block to be sent
     * @param skip  the set of peers to avoid sending the message.
     * @return a set containing the ids of the peers that received the block.
     */
    @Nonnull
    Set<NodeID> broadcastBlock(@Nonnull final Block block, @Nullable final Set<NodeID> skip);

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

    /**
     * Propagates the new block message across active peers with exclusion of
     * 'receivedFrom' peer.
     * @param block  new Block to be sent
     * @param receivedFrom the peer which sent original message or null if
     *                     the block has been mined by us
     */

    @Deprecated // Use broadcastBlock
    void sendNewBlock(Block block, Channel receivedFrom);

    void add(Channel peer);

    void notifyDisconnect(Channel channel);

    void onSyncDone(boolean done) ;

    Collection<Channel> getActivePeers();

    boolean sendMessageTo(NodeID nodeID, Message message);
}
