/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.manager;

import co.rsk.core.NetworkStateExporter;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.net.BlockProcessor;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Blockchain;
import org.ethereum.core.PendingState;
import org.ethereum.core.Repository;
import org.ethereum.core.genesis.BlockChainLoader;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * WorldManager is a singleton containing references to different parts of the system.
 *
 * @author Roman Mandeleil
 * @since 01.06.2014
 */
@Component("worldManager")
public class WorldManagerImpl implements WorldManager {

    private static final Logger logger = LoggerFactory.getLogger("general");

    @Autowired
    private EthereumListener listener;

    @Autowired
    private Blockchain blockchain;

    @Autowired
    private Repository repository;

    @Autowired
    private BlockStore blockStore;

    @Autowired
    private ChannelManager channelManager;

    @Autowired
    private AdminInfo adminInfo;

    @Autowired
    private NodeManager nodeManager;

    @Autowired
    private PendingState pendingState;

    @Autowired
    SystemProperties config;

    @Autowired
    ConfigCapabilities configCapabilities;

    BlockProcessor nodeBlockProcessor;

    @Autowired
    private HashRateCalculator hashRateCalculator;

    @Autowired
    private NetworkStateExporter networkStateExporter;

    @Autowired
    private SolidityCompiler solidityCompiler;

    @PostConstruct
    public void init() {
        BlockChainLoader loader = new BlockChainLoader(this.blockchain, this.config, this.blockStore, this.repository, this.listener);
        loader.loadBlockchain();
    }

    public void addListener(EthereumListener listener) {
        logger.info("Ethereum listener added");
        ((CompositeEthereumListener) this.listener).addListener(listener);
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

   public EthereumListener getListener() {
        return listener;
    }

    public org.ethereum.facade.Repository getRepository() {
        return (org.ethereum.facade.Repository)repository;
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public PendingState getPendingState() {
        return pendingState;
    }

    @PreDestroy
    public void close() {
        repository.close();
        blockchain.close();
    }

    public ConfigCapabilities getConfigCapabilities() { return configCapabilities; }

    public void setNodeBlockProcessor(BlockProcessor nodeBlockProcessor){
        this.nodeBlockProcessor = nodeBlockProcessor;
    }

    public BlockProcessor getNodeBlockProcessor(){
        return this.nodeBlockProcessor;
    }

    public HashRateCalculator getHashRateCalculator() { return hashRateCalculator; }

    @Override
    public NetworkStateExporter getNetworkStateExporter() {
        return networkStateExporter;
    }

    @Override
    public SolidityCompiler getSolidityCompiler() {
        return this.solidityCompiler;
    }

}
