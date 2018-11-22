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
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.net.server.ChannelManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RskImpl extends EthereumImpl implements Rsk {

    private boolean isplaying;
    private final NodeBlockProcessor nodeBlockProcessor;

    @Autowired
    public RskImpl(
            ChannelManager channelManager,
            TransactionPool transactionPool,
            CompositeEthereumListener compositeEthereumListener,
            NodeBlockProcessor nodeBlockProcessor,
            Blockchain blockchain) {
        super(
                channelManager,
                transactionPool,
                compositeEthereumListener,
                blockchain
        );
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
