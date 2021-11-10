/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.rpc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.NetworkStateExporter;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.net.SyncProcessor;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.util.BuildInfo;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class Web3EthModuleTest {

    @Test
    public void eth_chainId() {
        EthModule ethModule = mock(EthModule.class);
        when(ethModule.chainId()).thenReturn("0x21");
        Web3EthModule web3 = new Web3RskImpl(
                mock(Ethereum.class),
                mock(Blockchain.class, RETURNS_DEEP_STUBS),
                mock(RskSystemProperties.class),
                mock(MinerClient.class),
                mock(MinerServer.class),
                mock(PersonalModule.class),
                ethModule,
                mock(EvmModule.class),
                mock(TxPoolModule.class),
                mock(MnrModule.class),
                mock(DebugModule.class),
                null, mock(RskModule.class),
                mock(ChannelManager.class),
                mock(PeerScoringManager.class),
                mock(NetworkStateExporter.class),
                mock(BlockStore.class),
                mock(ReceiptStore.class),
                mock(PeerServer.class),
                mock(BlockProcessor.class),
                mock(HashRateCalculator.class),
                mock(ConfigCapabilities.class),
                mock(BuildInfo.class),
                mock(BlocksBloomStore.class),
                mock(Web3InformationRetriever.class),
                mock(SyncProcessor.class));

        assertThat(web3.eth_chainId(), is("0x21"));
    }
}
