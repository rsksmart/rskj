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

import co.rsk.panic.PanicProcessor;
import org.ethereum.core.*;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.Message;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.vm.trace.ProgramTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * @author Roman Mandeleil
 * @since 12.11.2014
 */
public class CompositeEthereumListener implements EthereumListener {
    private static final Logger logger = LoggerFactory.getLogger("events");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    // Using a concurrent list
    // (the add and remove methods copy an internal array,
    // but the iterator directly use the internal array)
    private final List<EthereumListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(EthereumListener listener) {
        listeners.add(listener);
    }
    public void removeListener(EthereumListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void trace(String output) {
        scheduleListenerCallbacks(listener -> listener.trace(output));
    }

    @Override
    public void onBlock(Block block, List<TransactionReceipt> receipts) {
        scheduleListenerCallbacks(listener -> listener.onBlock(block, receipts));
    }

    @Override
    public void onRecvMessage(Channel channel, Message message) {
        scheduleListenerCallbacks(listener -> listener.onRecvMessage(channel, message));
    }

    @Override
    public void onPeerDisconnect(String host, long port) {
        scheduleListenerCallbacks(listener -> listener.onPeerDisconnect(host, port));
    }

    @Override
    public void onPendingTransactionsReceived(List<Transaction> transactions) {
        scheduleListenerCallbacks(listener -> listener.onPendingTransactionsReceived(transactions));
    }

    @Override
    public void onTransactionPoolChanged(TransactionPool transactionPool) {
        scheduleListenerCallbacks(listener -> listener.onTransactionPoolChanged(transactionPool));
    }

    @Override
    public void onSyncDone() {
        scheduleListenerCallbacks(EthereumListener::onSyncDone);
    }

    @Override
    public void onNoConnections() {
        scheduleListenerCallbacks(EthereumListener::onNoConnections);
    }

    @Override
    public void onHandShakePeer(Channel channel, HelloMessage helloMessage) {
        scheduleListenerCallbacks(listener -> listener.onHandShakePeer(channel, helloMessage));
    }

    @Override
    public void onVMTraceCreated(String transactionHash, ProgramTrace trace) {
        scheduleListenerCallbacks(listener -> listener.onVMTraceCreated(transactionHash, trace));
    }

    @Override
    public void onNodeDiscovered(Node node) {
        scheduleListenerCallbacks(listener -> listener.onNodeDiscovered(node));
    }

    @Override
    public void onEthStatusUpdated(Channel channel, StatusMessage status) {
        scheduleListenerCallbacks(listener -> listener.onEthStatusUpdated(channel, status));
    }

    @Override
    public void onTransactionExecuted(TransactionExecutionSummary summary) {
        scheduleListenerCallbacks(listener -> listener.onTransactionExecuted(summary));
    }

    @Override
    public void onPeerAddedToSyncPool(Channel peer) {
        scheduleListenerCallbacks(listener -> listener.onPeerAddedToSyncPool(peer));
    }

    @Override
    public void onLongSyncDone() {
        scheduleListenerCallbacks(EthereumListener::onLongSyncDone);
    }

    @Override
    public void onLongSyncStarted() {
        scheduleListenerCallbacks(EthereumListener::onLongSyncStarted);
    }

    private void scheduleListenerCallbacks(Consumer<EthereumListener> callback) {
        for (EthereumListener listener : listeners) {
            try {
                callback.accept(listener);
            } catch (Throwable e) {
                logger.error("Listener callback failed with exception", e);
                panicProcessor.panic("thread", String.format("Listener callback failed with exception %s", e.getMessage()));
            }
        }
    }
}
