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

import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockStore;
import co.rsk.net.MessageHandler;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.net.NodeMessageHandler;
import co.rsk.net.handler.TxHandlerImpl;
import org.ethereum.facade.EthereumImpl;
import org.springframework.stereotype.Component;

/**
 * Created by ajlopez on 3/3/2016.
 */
@Component
public class RskImpl extends EthereumImpl implements Rsk {
    private boolean isplaying;

    private NodeBlockProcessor nodeBlockProcessor;

    private MessageHandler messageHandler;

    @Override
    public MinerClient getMinerClient() {
        return getWorldManager().getMinerClient();
    }

    @Override
    public MinerServer getMinerServer() {
        return getWorldManager().getMinerServer();
    }

    @Override
    public MessageHandler getMessageHandler() {
        if (this.messageHandler == null) {
            this.nodeBlockProcessor = getNodeBlockProcessor(); // Initialize nodeBlockProcessor if not done already.
            NodeMessageHandler handler = new NodeMessageHandler(this.nodeBlockProcessor, getChannelManager(),
                    getWorldManager().getPendingState(), new TxHandlerImpl(getWorldManager()));
            handler.start();
            this.messageHandler = handler;
        }

        return this.messageHandler;
    }

    @Override
    public NodeBlockProcessor getNodeBlockProcessor() {
        if (this.nodeBlockProcessor == null) {
            this.nodeBlockProcessor = new NodeBlockProcessor(new BlockStore(), this.getWorldManager().getBlockchain(), this.getWorldManager());
        }
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
