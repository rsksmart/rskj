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
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
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
@Component
public class WorldManagerImpl implements WorldManager {

    private static final Logger logger = LoggerFactory.getLogger("general");

    private final Blockchain blockchain;
    private final BlockStore blockStore;
    private final PendingState pendingState;
    private final Repository repository;
    private final NetworkStateExporter networkStateExporter;
    private final HashRateCalculator hashRateCalculator;
    private final ConfigCapabilities configCapabilities;
    private final SystemProperties config;
    private final ChannelManager channelManager;
    private final EthereumListener listener;
    private final BlockProcessor nodeBlockProcessor;

    @Autowired
    public WorldManagerImpl(Blockchain blockchain,
                            BlockStore blockStore,
                            PendingState pendingState,
                            Repository repository,
                            NetworkStateExporter networkStateExporter,
                            HashRateCalculator hashRateCalculator,
                            ConfigCapabilities configCapabilities,
                            SystemProperties config,
                            ChannelManager channelManager,
                            EthereumListener listener,
                            BlockProcessor nodeBlockProcessor) {
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.pendingState = pendingState;
        this.repository = repository;
        this.networkStateExporter = networkStateExporter;
        this.hashRateCalculator = hashRateCalculator;
        this.configCapabilities = configCapabilities;
        this.config = config;
        this.channelManager = channelManager;
        this.listener = listener;
        this.nodeBlockProcessor = nodeBlockProcessor;

        BlockChainLoader loader = new BlockChainLoader(this.blockchain, this.config, this.blockStore, this.repository, this.listener);
        loader.loadBlockchain();
    }

    @Override
    public void addListener(EthereumListener listener) {
        logger.info("Ethereum listener added");
        ((CompositeEthereumListener) this.listener).addListener(listener);
    }

    @Override
    public void removeListener(EthereumListener listener) {
        logger.info("Ethereum listener removed");
        ((CompositeEthereumListener) this.listener).removeListener(listener);
    }

    @Override
    public Blockchain getBlockchain() {
        return blockchain;
    }

    @Override
    public BlockStore getBlockStore() {
        return blockStore;
    }

    @Override
    public PendingState getPendingState() {
        return pendingState;
    }

    @Override
    @PreDestroy
    public void close() {
        repository.close();
        blockchain.close();
    }

    @Override
    public ConfigCapabilities getConfigCapabilities() { return configCapabilities; }

    @Override
    public BlockProcessor getNodeBlockProcessor(){
        return this.nodeBlockProcessor;
    }

    @Override
    public HashRateCalculator getHashRateCalculator() { return hashRateCalculator; }

}
