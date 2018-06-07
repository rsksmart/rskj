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

package org.ethereum.listener;

import org.ethereum.core.*;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.Message;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.vm.trace.ProgramTrace;

import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 27.07.2014
 */
public interface EthereumListener {

    void trace(String output);

    void onNodeDiscovered(Node node);

    void onHandShakePeer(Channel channel, HelloMessage helloMessage);

    void onEthStatusUpdated(Channel channel, StatusMessage status);

    void onRecvMessage(Channel channel, Message message);

    void onBlock(Block block, List<TransactionReceipt> receipts);

    void onBestBlock(Block block, List<TransactionReceipt> receipts);

    void onPeerDisconnect(String host, long port);

    void onPendingTransactionsReceived(List<Transaction> transactions);

    void onTransactionPoolChanged(TransactionPool transactionPool);

    /**
     * @deprecated Check Rsk.hasBetterBlockToSync() and isPlayingBlocks()
     */
    @Deprecated
    void onSyncDone();

    void onNoConnections();

    void onVMTraceCreated(String transactionHash, ProgramTrace trace);

    void onTransactionExecuted(TransactionExecutionSummary summary);

    void onPeerAddedToSyncPool(Channel peer);

    void onLongSyncDone();

    void onLongSyncStarted();
}
