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

import co.rsk.net.NodeBlockProcessor;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.PendingState;
import org.ethereum.core.Repository;
import org.ethereum.db.EventsStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;

public class RskImpl extends EthereumImpl implements Rsk {

    private boolean isplaying;
    private final NodeBlockProcessor nodeBlockProcessor;

    public RskImpl(WorldManager worldManager,
                   ChannelManager channelManager,
                   PeerServer peerServer,
                   ProgramInvokeFactory programInvokeFactory,
                   PendingState pendingState,
                   SystemProperties config,
                   CompositeEthereumListener compositeEthereumListener,
                   ReceiptStore receiptStore,
                   EventsStore eventsStore,
                   NodeBlockProcessor nodeBlockProcessor,
                   Repository repository) {
        super(worldManager, channelManager, peerServer, programInvokeFactory, pendingState, config, compositeEthereumListener, receiptStore, eventsStore,repository);
        this.nodeBlockProcessor = nodeBlockProcessor;
    }

    @Override
    public boolean isPlayingBlocks() {
        return isplaying;
    }

    @Override
    public boolean isBlockchainEmpty() {
        return this.nodeBlockProcessor.getBestBlockNumber() == 0;
    }

    public void setIsPlayingBlocks(boolean value) {
        isplaying = value;
    }

    @Override
    public boolean hasBetterBlockToSync() {
        return this.nodeBlockProcessor.hasBetterBlockToSync();
    }
}
