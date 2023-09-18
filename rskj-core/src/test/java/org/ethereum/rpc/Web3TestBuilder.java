/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
import co.rsk.core.NetworkStateExporter;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.net.SyncProcessor;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.trace.TraceModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.Blockchain;
import org.ethereum.core.SignatureCache;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.util.BuildInfo;
import org.mockito.Mockito;

public class Web3TestBuilder {

    Ethereum ethereum = Web3Mocks.getMockEthereum();
    Blockchain blockchain = Web3Mocks.getMockBlockchain();
    BlockStore blockStore = Web3Mocks.getMockBlockStore();
    ReceiptStore receiptStore = Web3Mocks.getMockReceiptStore();
    RskSystemProperties properties = Web3Mocks.getMockProperties();
    MinerClient minerClient = Web3Mocks.getMockMinerClient();
    MinerServer minerServer = Web3Mocks.getMockMinerServer();
    EthModule ethModule = Mockito.mock(EthModule.class);
    PersonalModule personalModule = Mockito.mock(PersonalModule.class);
    EvmModule evmModule = Mockito.mock(EvmModule.class);
    TxPoolModule txPoolModule = Mockito.mock(TxPoolModule.class);
    MnrModule mnrModule = Mockito.mock(MnrModule.class);
    DebugModule debugModule = Mockito.mock(DebugModule.class);
    TraceModule traceModule = Mockito.mock(TraceModule.class);
    RskModule rskModule = Mockito.mock(RskModule.class);
    ChannelManager channelManager = Web3Mocks.getMockChannelManager();
    PeerScoringManager peerScoringManager = Mockito.mock(PeerScoringManager.class);
    PeerServer peerServer = Mockito.mock(PeerServer.class);
    BlockProcessor blockProcessor = Mockito.mock(BlockProcessor.class);
    HashRateCalculator hashRateCalculator = Mockito.mock(HashRateCalculator.class);
    ConfigCapabilities configCapabilities = Mockito.mock(ConfigCapabilities.class);
    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    BlocksBloomStore blocksBloomStore = Mockito.mock(BlocksBloomStore.class);
    Web3InformationRetriever web3InformationRetriever = Mockito.mock(Web3InformationRetriever.class);
    SyncProcessor syncProcessor = Mockito.mock(SyncProcessor.class);
    SignatureCache signatureCache = Mockito.mock(SignatureCache.class);
    NetworkStateExporter networkStateExporter = Mockito.mock(NetworkStateExporter.class);


    public static Web3TestBuilder builder() {
        return new Web3TestBuilder();
    }

    public Web3TestBuilder withEthModule(EthModule ethModule) {
        this.ethModule = ethModule;
        return this;
    }

    public Web3TestBuilder withPersonalModule(PersonalModule personalModule) {
        this.personalModule = personalModule;
        return this;
    }

    public Web3TestBuilder withEvmModule(EvmModule evmModule) {
        this.evmModule = evmModule;
        return this;
    }

    public Web3TestBuilder withTxPoolModule(TxPoolModule txPoolModule) {
        this.txPoolModule = txPoolModule;
        return this;
    }

    public Web3TestBuilder withMnrModule(MnrModule mnrModule) {
        this.mnrModule = mnrModule;
        return this;
    }

    public Web3TestBuilder withDebugModule(DebugModule debugModule) {
        this.debugModule = debugModule;
        return this;
    }

    public Web3TestBuilder withTraceModule(TraceModule traceModule) {
        this.traceModule = traceModule;
        return this;
    }

    public Web3TestBuilder withRskModule(RskModule rskModule) {
        this.rskModule = rskModule;
        return this;
    }

    public Web3TestBuilder withChannelManager(ChannelManager channelManager) {
        this.channelManager = channelManager;
        return this;
    }

    public Web3TestBuilder withPeerScoringManager(PeerScoringManager peerScoringManager) {
        this.peerScoringManager = peerScoringManager;
        return this;
    }

    public Web3TestBuilder withPeerServer(PeerServer peerServer) {
        this.peerServer = peerServer;
        return this;
    }

    public Web3TestBuilder withBlockProcessor(BlockProcessor blockProcessor) {
        this.blockProcessor = blockProcessor;
        return this;
    }

    public Web3TestBuilder withHashRateCalculator(HashRateCalculator hashRateCalculator) {
        this.hashRateCalculator = hashRateCalculator;
        return this;
    }

    public Web3TestBuilder withConfigCapabilities(ConfigCapabilities configCapabilities) {
        this.configCapabilities = configCapabilities;
        return this;
    }

    public Web3TestBuilder withBuildInfo(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
        return this;
    }

    public Web3TestBuilder withBlocksBloomStore(BlocksBloomStore blocksBloomsStore) {
        this.blocksBloomStore = blocksBloomsStore;
        return this;
    }

    public Web3TestBuilder withWeb3InformationRetriever(Web3InformationRetriever web3InformationRetriever) {
        this.web3InformationRetriever = web3InformationRetriever;
        return this;
    }

    public Web3TestBuilder withSyncProcessor(SyncProcessor syncProcessor) {
        this.syncProcessor = syncProcessor;
        return this;
    }

    public Web3TestBuilder withSignatureCache(SignatureCache signatureCache) {
        this.signatureCache = signatureCache;
        return this;
    }

    public Web3TestBuilder withNetworkStateExporter(NetworkStateExporter networkStateExporter) {
        this.networkStateExporter = networkStateExporter;
        return this;
    }

    public Web3TestBuilder withReceiptStore(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
        return this;
    }

    public Web3TestBuilder withBlockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
        return this;
    }

    public Web3TestBuilder withProperties(RskSystemProperties properties) {
        this.properties = properties;
        return this;
    }

    public Web3TestBuilder withMinerClient(MinerClient minerClient) {
        this.minerClient = minerClient;
        return this;
    }

    public Web3TestBuilder withMinerServer(MinerServer minerServer) {
        this.minerServer = minerServer;
        return this;
    }

    public Web3TestBuilder withBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
        return this;
    }

    public Web3TestBuilder withEthereum(Ethereum ethereum) {
        this.ethereum = ethereum;
        return this;
    }

    public Web3RskImpl build() {
        return new Web3RskImpl(
                ethereum,
                blockchain,
                properties,
                minerClient,
                minerServer,
                personalModule,
                ethModule,
                evmModule,
                txPoolModule,
                mnrModule,
                debugModule,
                traceModule,
                rskModule,
                channelManager,
                peerScoringManager,
                networkStateExporter,
                blockStore,
                receiptStore,
                peerServer,
                blockProcessor,
                hashRateCalculator,
                configCapabilities,
                buildInfo,
                blocksBloomStore,
                web3InformationRetriever,
                syncProcessor,
                signatureCache);
    }

    public Ethereum getEthereum() {
        return ethereum;
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public ReceiptStore getReceiptStore() {
        return receiptStore;
    }

    public RskSystemProperties getProperties() {
        return properties;
    }

    public MinerClient getMinerClient() {
        return minerClient;
    }

    public MinerServer getMinerServer() {
        return minerServer;
    }

    public EthModule getEthModule() {
        return ethModule;
    }

    public PersonalModule getPersonalModule() {
        return personalModule;
    }

    public EvmModule getEvmModule() {
        return evmModule;
    }

    public TxPoolModule getTxPoolModule() {
        return txPoolModule;
    }

    public MnrModule getMnrModule() {
        return mnrModule;
    }

    public DebugModule getDebugModule() {
        return debugModule;
    }

    public TraceModule getTraceModule() {
        return traceModule;
    }

    public RskModule getRskModule() {
        return rskModule;
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public PeerScoringManager getPeerScoringManager() {
        return peerScoringManager;
    }

    public PeerServer getPeerServer() {
        return peerServer;
    }

    public BlockProcessor getBlockProcessor() {
        return blockProcessor;
    }

    public HashRateCalculator getHashRateCalculator() {
        return hashRateCalculator;
    }

    public ConfigCapabilities getConfigCapabilities() {
        return configCapabilities;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public BlocksBloomStore getBlocksBloomStore() {
        return blocksBloomStore;
    }

    public Web3InformationRetriever getWeb3InformationRetriever() {
        return web3InformationRetriever;
    }

    public SyncProcessor getSyncProcessor() {
        return syncProcessor;
    }

    public SignatureCache getSignatureCache() {
        return signatureCache;
    }

    public NetworkStateExporter getNetworkStateExporter() {
        return networkStateExporter;
    }
}
