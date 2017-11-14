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

package co.rsk.core;

import co.rsk.net.MessageHandler;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.net.NodeMessageHandler;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.PendingState;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.manager.AdminInfo;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;

public class RskImpl extends EthereumImpl implements Rsk {

    private boolean isplaying;
    private NodeBlockProcessor nodeBlockProcessor;

    private MessageHandler messageHandler;
    private PeerScoringManager peerScoringManager;

    public RskImpl(WorldManager worldManager,
                   AdminInfo adminInfo,
                   ChannelManager channelManager,
                   PeerServer peerServer,
                   ProgramInvokeFactory programInvokeFactory,
                   PendingState pendingState,
                   SystemProperties config,
                   CompositeEthereumListener compositeEthereumListener,
                   ReceiptStore receiptStore,
                   PeerScoringManager peerScoringManager,
                   NodeBlockProcessor nodeBlockProcessor,
                   NodeMessageHandler messageHandler) {
        super(worldManager, adminInfo, channelManager, peerServer, programInvokeFactory, pendingState, config, compositeEthereumListener, receiptStore);
        this.peerScoringManager = peerScoringManager;
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.messageHandler = messageHandler;
    }

    @Override
    public PeerScoringManager getPeerScoringManager() {
        return this.peerScoringManager;
    }

    @Override
    public MessageHandler getMessageHandler() {
        return this.messageHandler;
    }

    @Override
    public NodeBlockProcessor getNodeBlockProcessor() {
        return this.nodeBlockProcessor;
    }

    @Override
    public boolean isPlayingBlocks() {
        return isplaying;
    }

    @Override
    public boolean isSyncingBlocks() {
        return this.getNodeBlockProcessor().isSyncingBlocks();
    }

    @Override
    public boolean isBlockchainEmpty() {
        return this.getNodeBlockProcessor().getBestBlockNumber() == 0;
    }

    public void setIsPlayingBlocks(boolean value) {
        isplaying = value;
    }

    @Override
    public boolean hasBetterBlockToSync() {
            return this.getNodeBlockProcessor().hasBetterBlockToSync();
    }
}
