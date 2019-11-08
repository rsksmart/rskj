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

package co.rsk;

import co.rsk.config.InternalService;
import co.rsk.config.NodeCliFlags;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainFlusher;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.*;
import co.rsk.net.discovery.PeerExplorer;
import co.rsk.net.discovery.UDPServer;
import co.rsk.net.discovery.upnp.UpnpService;
import co.rsk.net.sync.PeersInformation;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.validators.BlockCompositeRule;
import co.rsk.validators.BlockUnclesHashValidationRule;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.core.TransactionPool;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.BlockStore;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.util.RskTestContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest(RskTestContext.class)
public class RskContextTest {

    private static final String FAKE_PUBLIC_IP = "255.255.255.100";
    private static final long START_THREAD_TIMEOUT = 5000;

    @Test
    public void getCliArgsSmokeTest() {
        RskTestContext rskContext = new RskTestContext(new String[] { "--devnet" });
        assertThat(rskContext.getCliArgs(), notNullValue());
        assertThat(rskContext.getCliArgs().getFlags(), contains(NodeCliFlags.NETWORK_DEVNET));
    }

    @Test
    public void getBuildInfoSmokeTest() {
        RskTestContext rskContext = new RskTestContext(new String[0]);
        mockBuildInfoResource(new ByteArrayInputStream("build.hash=c0ffee\nbuild.branch=HEAD".getBytes()));
        assertThat(rskContext.getBuildInfo(), notNullValue());
        assertThat(rskContext.getBuildInfo().getBuildHash(), is("c0ffee"));
    }

    @Test
    public void getBuildInfoMissingPropertiesSmokeTest() {
        RskTestContext rskContext = new RskTestContext(new String[0]);
        mockBuildInfoResource(null);
        assertThat(rskContext.getBuildInfo(), notNullValue());
        assertThat(rskContext.getBuildInfo().getBuildHash(), is("dev"));
    }

    private void mockBuildInfoResource(InputStream buildInfoStream) {
        mockStatic(RskContext.class);
        ClassLoader classLoader = mock(ClassLoader.class);
        when(classLoader.getResourceAsStream("build-info.properties")).thenReturn(buildInfoStream);
        when(RskContext.class.getClassLoader()).thenReturn(classLoader);
    }

    @Test
    public void testPeerDiscoveryWithUpnpEnabled() throws Exception {
        testPeerDiscoveryWithUpnp(Boolean.TRUE);
    }

    @Test
    public void testPeerDiscoveryWithUpnpDisabled() throws Exception {
        testPeerDiscoveryWithUpnp(Boolean.FALSE);
    }

    private void testPeerDiscoveryWithUpnp(boolean enabled) throws Exception {
        // create mocks
        UpnpService mockUpnpService = mock(UpnpService.class);
        RskTestContext context = spy(new RskTestContext(new String[0]));
        RskSystemProperties properties = spy(context.buildRskSystemProperties());

        // configure mocks
        getStubbedContext(context, properties);
        doReturn(enabled).when(properties).isPeerDiscoveryByUpnpEnabled();
        doReturn(Boolean.TRUE).when(properties).isPeerDiscoveryEnabled();
        whenNew(UpnpService.class).withAnyArguments().thenReturn(mockUpnpService);

        List<InternalService> internalServices = context.buildInternalServices();
        Optional<UDPServer> udpServerFromList = internalServices.stream()
                .filter(UDPServer.class::isInstance)
                .map(UDPServer.class::cast)
                .findFirst();
        Optional<UpnpService> upnpServiceFromList = internalServices.stream()
                .filter(UpnpService.class::isInstance)
                .map(UpnpService.class::cast)
                .findFirst();

        // UDPServer should always be present
        Assert.assertTrue("Expected an instance of UDPServer.", udpServerFromList.isPresent());

        // start UDPServer but prevent it from opening a channel
        UDPServer udpServerSpy = spy(udpServerFromList.get());
        doNothing().when(udpServerSpy).startUDPServer();
        udpServerSpy.start();

        // UpnpService is only present when enabled
        if (enabled) {
            Assert.assertTrue("Expected an instance of UpnpService.", upnpServiceFromList.isPresent());
            verify(mockUpnpService, timeout(START_THREAD_TIMEOUT).description("Expected UDPServer to use UPnP."))
                    .findGateway(anyString());
        } else {
            Assert.assertFalse("Did not expect an instance of UpnpService.", upnpServiceFromList.isPresent());
            verify(mockUpnpService, after(START_THREAD_TIMEOUT).never().description("Did not expect UDPServer to use UPnP."))
                    .findGateway(anyString());
        }
    }

    /**
     * Performs stubbing of mock context and properties objects.<br/>
     * Currently stubbing just enough of RskContext to get buildInternalServices() to execute.
     *
     * @param context
     * @param properties
     * @return
     * @throws Exception
     */
    private void getStubbedContext(RskContext context, RskSystemProperties properties) throws Exception {
        // properties stubbing
        doReturn(Boolean.FALSE).when(properties).isPeerDiscoveryEnabled();
        doReturn(Boolean.FALSE).when(properties).isRpcHttpEnabled();
        doReturn(Boolean.FALSE).when(properties).isRpcWebSocketEnabled();
        doReturn(Boolean.FALSE).when(properties).isSyncEnabled();
        doReturn(Boolean.FALSE).when(properties).isMinerServerEnabled();
        doReturn(Boolean.FALSE).when(properties).isMinerClientEnabled();
        doReturn(mock(ActivationConfig.class)).when(properties).getActivationConfig();
        doReturn(mock(Constants.class)).when(properties).getNetworkConstants();
        doReturn(mock(ECKey.class)).when(properties).getMyKey();
        doReturn(FAKE_PUBLIC_IP).when(properties).getPublicIp();
        doReturn(properties).when(context).getRskSystemProperties();

        // mocking objects returned by public getters
        doReturn(mock(Blockchain.class)).when(context).getBlockchain();
        doReturn(mock(BlockFactory.class)).when(context).getBlockFactory();
        doReturn(mock(BlockStore.class)).when(context).getBlockStore();
        doReturn(mock(BlockValidationRule.class)).when(context).getBlockValidationRule();
        doReturn(mock(ChannelManager.class)).when(context).getChannelManager();
        doReturn(mock(ConsensusValidationMainchainView.class)).when(context).getConsensusValidationMainchainView();
        doReturn(mock(Genesis.class)).when(context).getGenesis();
        doReturn(mock(NodeBlockProcessor.class)).when(context).getNodeBlockProcessor();
        doReturn(mock(PeerScoringManager.class)).when(context).getPeerScoringManager();
        doReturn(mock(PeerServer.class)).when(context).getPeerServer();
        doReturn(mock(TransactionPool.class)).when(context).getTransactionPool();

        // mocking objects created directly by `new`
        whenNew(BlockCompositeRule.class).withAnyArguments().thenReturn(mock(BlockCompositeRule.class));
        whenNew(BlockChainFlusher.class).withAnyArguments().thenReturn(mock(BlockChainFlusher.class));
        whenNew(BlockNodeInformation.class).withAnyArguments().thenReturn(mock(BlockNodeInformation.class));
        whenNew(BlockSyncService.class).withAnyArguments().thenReturn(mock(BlockSyncService.class));
        whenNew(BlockUnclesHashValidationRule.class).withAnyArguments().thenReturn(mock(BlockUnclesHashValidationRule.class));
        whenNew(DifficultyCalculator.class).withAnyArguments().thenReturn(mock(DifficultyCalculator.class));
        whenNew(NetBlockStore.class).withAnyArguments().thenReturn(mock(NetBlockStore.class));
        whenNew(Node.class).withAnyArguments().thenReturn(mock(Node.class));
        whenNew(NodeMessageHandler.class).withAnyArguments().thenReturn(mock(NodeMessageHandler.class));
        whenNew(PeerExplorer.class).withAnyArguments().thenReturn(mock(PeerExplorer.class));
        whenNew(PeersInformation.class).withAnyArguments().thenReturn(mock(PeersInformation.class));
        whenNew(ProofOfWorkRule.class).withAnyArguments().thenReturn(mock(ProofOfWorkRule.class));
        whenNew(StatusResolver.class).withAnyArguments().thenReturn(mock(StatusResolver.class));
        whenNew(SyncProcessor.class).withAnyArguments().thenReturn(mock(SyncProcessor.class));
        whenNew(TransactionGateway.class).withAnyArguments().thenReturn(mock(TransactionGateway.class));
    }
}