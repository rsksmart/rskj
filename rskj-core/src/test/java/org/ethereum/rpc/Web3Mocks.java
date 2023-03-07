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

package org.ethereum.rpc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.RepositoryLocator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.MessageHandler;
import co.rsk.net.NodeMessageHandler;
import co.rsk.util.NodeStopper;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.server.ChannelManager;

import static org.mockito.Mockito.*;

public class Web3Mocks {
    public static Ethereum getMockEthereum() {
        return mock(Ethereum.class);
    }

    public static Blockchain getMockBlockchain() {
        Blockchain mockBlockchain = mock(Blockchain.class, RETURNS_DEEP_STUBS);
        when(mockBlockchain.getBestBlock().getNumber()).thenReturn(0L);
        return mockBlockchain;
    }

    public static RskSystemProperties getMockProperties() {
        return mock(RskSystemProperties.class);
    }

    public static MinerClient getMockMinerClient() {
        return mock(MinerClient.class);
    }

    public static MinerServer getMockMinerServer() {
        return mock(MinerServer.class);
    }

    public static ChannelManager getMockChannelManager() {
        return mock(ChannelManager.class);
    }

    public static RepositoryLocator getMockRepositoryLocator() {
        return mock(RepositoryLocator.class);
    }

    public static TransactionPool getMockTransactionPool() {
        return mock(TransactionPool.class);
    }

    public static BlockStore getMockBlockStore() {
        return mock(BlockStore.class, RETURNS_DEEP_STUBS);
    }

    public static MessageHandler getMockMessageHandler() {
        return mock(NodeMessageHandler.class);
    }

    public static ReceiptStore getMockReceiptStore() {
        return mock(ReceiptStore.class);
    }

    public static BlockExecutor getMockBlockExecutor() {
        return mock(BlockExecutor.class);
    }

    public static NodeStopper getMockNodeStopper() {
        return mock(NodeStopper.class);
    }
}
