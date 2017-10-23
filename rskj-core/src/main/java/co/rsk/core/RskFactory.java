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

import co.rsk.blocks.FileBlockPlayer;
import co.rsk.blocks.FileBlockRecorder;
import co.rsk.config.RskSystemProperties;
import co.rsk.net.*;
import co.rsk.net.eth.RskWireProtocol;
import co.rsk.net.handler.TxHandlerImpl;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PunishmentParameters;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.core.PendingState;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.EthereumChannelInitializerFactory;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.eth.handler.EthHandlerFactory;
import org.ethereum.net.eth.handler.EthHandlerFactoryImpl;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.rlpx.HandshakeHandler;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.server.*;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

@Configuration
@ComponentScan("org.ethereum")
public class RskFactory {

    private static final Logger logger = LoggerFactory.getLogger("general");

    @Bean
    public Rsk getRsk(WorldManager worldManager,
                      AdminInfo adminInfo,
                      ChannelManager channelManager,
                      PeerServer peerServer,
                      ProgramInvokeFactory programInvokeFactory,
                      PendingState pendingState,
                      SystemProperties config,
                      CompositeEthereumListener compositeEthereumListener,
                      ReceiptStore receiptStore,
                      EthereumImpl.PeerClientFactory peerClientFactory,
                      PeerScoringManager peerScoringManager,
                      NodeBlockProcessor nodeBlockProcessor,
                      NodeMessageHandler nodeMessageHandler,
                      RskSystemProperties rskSystemProperties) {

        logger.info("Running {},  core version: {}-{}", config.genesisInfo(), config.projectVersion(), config.projectVersionModifier());
        BuildInfo.printInfo();

        RskImpl rsk = new RskImpl(worldManager, adminInfo, channelManager, peerServer, programInvokeFactory,
                pendingState, config, compositeEthereumListener, receiptStore, peerScoringManager, nodeBlockProcessor, nodeMessageHandler, peerClientFactory);

        rsk.init();
        rsk.getBlockchain().setRsk(true);  //TODO: check if we can remove this field from org.ethereum.facade.Blockchain
        if (logger.isInfoEnabled()) {
            String versions = EthVersion.supported().stream().map(EthVersion::name).collect(Collectors.joining(", "));
            logger.info("Capability eth version: [{}]", versions);
        }
        if (rskSystemProperties.isBlocksEnabled()) {
            setupRecorder(rsk, rskSystemProperties.blocksRecorder());
            setupPlayer(rsk, rskSystemProperties.blocksPlayer());
        }
        return rsk;
    }

    private void setupRecorder(RskImpl rsk, String blocksRecorderFileName) {
        if (blocksRecorderFileName != null) {
            rsk.getBlockchain().setBlockRecorder(new FileBlockRecorder(blocksRecorderFileName));
        }
    }

    private void setupPlayer(RskImpl rsk, String blocksPlayerFileName) {
        if (blocksPlayerFileName != null) {
            new Thread(() -> {
                try (FileBlockPlayer bplayer = new FileBlockPlayer(blocksPlayerFileName)) {
                    rsk.setIsPlayingBlocks(true);

                    Blockchain bc = rsk.getWorldManager().getBlockchain();
                    ChannelManager cm = rsk.getChannelManager();

                    connectBlocks(bplayer, bc, cm);
                } catch (Exception e) {
                    logger.error("Error", e);
                } finally {
                    rsk.setIsPlayingBlocks(false);
                }
            }).start();
        }
    }

    private void connectBlocks(FileBlockPlayer bplayer, Blockchain bc, ChannelManager cm) {
        for (Block block = bplayer.readBlock(); block != null; block = bplayer.readBlock()) {
            ImportResult tryToConnectResult = bc.tryToConnect(block);
            if (BlockProcessResult.importOk(tryToConnectResult)) {
                cm.broadcastBlock(block, null);
            }
        }
    }

    @Bean
    public PeerScoringManager getPeerScoringManager(SystemProperties config) {
        int nnodes = config.scoringNumberOfNodes();

        long nodePunishmentDuration = config.scoringNodesPunishmentDuration();
        int nodePunishmentIncrement = config.scoringNodesPunishmentIncrement();
        long nodePunhishmentMaximumDuration = config.scoringNodesPunishmentMaximumDuration();

        long addressPunishmentDuration = config.scoringAddressesPunishmentDuration();
        int addressPunishmentIncrement = config.scoringAddressesPunishmentIncrement();
        long addressPunishmentMaximunDuration = config.scoringAddressesPunishmentMaximumDuration();

        return new PeerScoringManager(nnodes, new PunishmentParameters(nodePunishmentDuration, nodePunishmentIncrement,
                nodePunhishmentMaximumDuration), new PunishmentParameters(addressPunishmentDuration, addressPunishmentIncrement, addressPunishmentMaximunDuration));
    }

    @Bean
    public NodeBlockProcessor getNodeBlockProcessor(WorldManager worldManager) {
        return new NodeBlockProcessor(new BlockStore(), worldManager.getBlockchain(), worldManager);
    }

    @Bean
    public NodeMessageHandler getNodeMessageHandler(NodeBlockProcessor nodeBlockProcessor, ChannelManager channelManager, WorldManager worldManager, PeerScoringManager peerScoringManager) {
        NodeMessageHandler nodeMessageHandler = new NodeMessageHandler(nodeBlockProcessor, channelManager, worldManager.getPendingState(), new TxHandlerImpl(worldManager), peerScoringManager);
        nodeMessageHandler.start();
        return nodeMessageHandler;
    }

    @Bean
    public EthereumImpl.PeerClientFactory getPeerClientFactory(SystemProperties config,
                                                               EthereumListener ethereumListener,
                                                               EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        return () -> new PeerClient(config, ethereumListener, ethereumChannelInitializerFactory);
    }

    @Bean
    public EthereumChannelInitializerFactory getEthereumChannelInitializerFactory(ChannelManager channelManager, EthereumChannelInitializer.ChannelFactory channelFactory) {
        return remoteId -> new EthereumChannelInitializer(remoteId, channelManager, channelFactory);
    }

    @Bean
    public EthereumChannelInitializer.ChannelFactory getChannelFactory(SystemProperties config,
                                                                       EthereumListener ethereumListener,
                                                                       ConfigCapabilities configCapabilities,
                                                                       NodeManager nodeManager,
                                                                       EthHandlerFactory ethHandlerFactory,
                                                                       StaticMessages staticMessages,
                                                                       PeerScoringManager peerScoringManager) {
        return () -> {
            HandshakeHandler handshakeHandler = new HandshakeHandler(config, peerScoringManager);
            MessageQueue messageQueue = new MessageQueue();
            P2pHandler p2pHandler = new P2pHandler(ethereumListener, configCapabilities, config);
            MessageCodec messageCodec = new MessageCodec(ethereumListener, config);
            return new Channel(config, messageQueue, p2pHandler, messageCodec, handshakeHandler, nodeManager, ethHandlerFactory, staticMessages);
        };
    }

    @Bean
    public EthHandlerFactoryImpl.RskWireProtocolFactory getRskWireProtocolFactory (PeerScoringManager peerScoringManager,
                                                                                   MessageHandler messageHandler,
                                                                                   Blockchain blockchain,
                                                                                   SystemProperties config,
                                                                                   CompositeEthereumListener ethereumListener){
        return () -> new RskWireProtocol(peerScoringManager, messageHandler, blockchain, config, ethereumListener);
    }

    @Bean
    public PeerServer getPeerServer(SystemProperties config,
                                    EthereumListener ethereumListener,
                                    EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        return new PeerServerImpl(config, ethereumListener, ethereumChannelInitializerFactory);
    }
}
