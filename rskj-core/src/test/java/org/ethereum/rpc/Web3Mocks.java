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
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import org.ethereum.core.PendingState;
import org.ethereum.facade.Ethereum;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.server.ChannelManager;
import org.mockito.Mockito;

import java.math.BigInteger;

import static org.mockito.Mockito.*;

public class Web3Mocks {
    public static Ethereum getMockEthereum() {
        WorldManager mockWorldManager = mock(WorldManager.class, RETURNS_DEEP_STUBS);
        when(mockWorldManager.getBlockchain().getBestBlock().getNumber()).thenReturn(0L);
        Ethereum ethMock = mock(Ethereum.class);
        when(ethMock.getWorldManager()).thenReturn(mockWorldManager);
        return ethMock;
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

    public static PendingState getMockPendingState(BigInteger nonce) {
        org.ethereum.core.Repository repository = Mockito.mock(org.ethereum.core.Repository.class);
        Mockito.when(repository.getNonce(Mockito.any())).thenReturn(nonce);
        PendingState pendingState = Mockito.mock(PendingState.class);
        Mockito.when(pendingState.getRepository()).thenReturn(repository);
        return pendingState;
    }
}
