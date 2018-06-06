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
 * @since 08.08.2014
 */
public class EthereumListenerAdapter implements EthereumListener {

    @Override
    public void trace(String output) {
    }

    @Override
    public void onBlock(Block block, List<TransactionReceipt> receipts) {
    }

    @Override
    public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
    }

    @Override
    public void onRecvMessage(Channel channel, Message message) {
    }

    @Override
    public void onPeerDisconnect(String host, long port) {
    }

    @Override
    public void onPendingTransactionsReceived(List<Transaction> transactions) {
    }

    @Override
    public void onTransactionPoolChanged(TransactionPool transactionPool) {
    }

    @Deprecated
    @Override
    public void onSyncDone() {
    }

    @Override
    public void onHandShakePeer(Channel channel, HelloMessage helloMessage) {

    }

    @Override
    public void onNoConnections() {

    }


    @Override
    public void onVMTraceCreated(String transactionHash, ProgramTrace trace) {

    }

    @Override
    public void onNodeDiscovered(Node node) {

    }

    @Override
    public void onEthStatusUpdated(Channel channel, StatusMessage statusMessage) {

    }

    @Override
    public void onTransactionExecuted(TransactionExecutionSummary summary) {

    }

    @Override
    public void onPeerAddedToSyncPool(Channel peer) {

    }

    @Override
    public void onLongSyncDone() {

    }

    @Override
    public void onLongSyncStarted() {

    }
}
