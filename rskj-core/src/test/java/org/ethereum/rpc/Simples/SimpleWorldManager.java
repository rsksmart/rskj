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

package org.ethereum.rpc.Simples;

import co.rsk.core.NetworkStateExporter;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.net.BlockProcessor;
import co.rsk.net.simples.SimpleBlockProcessor;
import org.ethereum.core.PendingState;
import org.ethereum.db.BlockStore;
import org.ethereum.facade.Repository;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;

/**
 * Created by Ruben Altman on 09/06/2016.
 */
public class SimpleWorldManager implements WorldManager {

    BlockProcessor nodeBlockProcessor;
    org.ethereum.core.Blockchain blockChain;
    PendingState pendingState;
    BlockStore blockStore;
    EthereumListener listener;

    public SimpleWorldManager() { }

    public SimpleWorldManager(SimpleBlockProcessor nodeBlockProcessor) {
        this.nodeBlockProcessor = nodeBlockProcessor;
    }

    @Override
    public void init() {

    }

    public void setListener(EthereumListener listener) {
        this.listener = listener;
    }

    @Override
    public void addListener(EthereumListener listener) {
        if (this.listener == null) {
            this.listener = new CompositeEthereumListener();
        }
        ((CompositeEthereumListener) this.listener).addListener(listener);
    }

    @Override
    public ChannelManager getChannelManager() {
        return new SimpleChannelManager();
    }

    @Override
    public Repository getRepository() {
        return null;
    }

    @Override
    public org.ethereum.core.Blockchain getBlockchain() {
        if (blockChain != null)
            return blockChain;

        org.ethereum.rpc.Simples.SimpleBlockChain blockChain = new org.ethereum.rpc.Simples.SimpleBlockChain();
        return blockChain;
    }

    public void setBlockchain(org.ethereum.core.Blockchain blockchain) {
        this.blockChain = blockchain;
    }

    @Override
    public BlockStore getBlockStore() {
        return blockStore;
    }

    public void setBlockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
    }

    public void setNodeBlockProcessor(BlockProcessor nodeBlockProcessor) { this.nodeBlockProcessor = nodeBlockProcessor;}

    @Override
    public PendingState getPendingState() {
        return pendingState;
    }

    public void setPendingState(PendingState pendingState) {
        this.pendingState = pendingState;
    }

    @Override
    public void close() {

    }

    @Override
    public ConfigCapabilities getConfigCapabilities() {
        ConfigCapabilities configCapabilities = new SimpleConfigCapabilities();

        return configCapabilities;
    }

    @Override
    public BlockProcessor getNodeBlockProcessor(){
        return this.nodeBlockProcessor;
    }

    @Override
    public HashRateCalculator getHashRateCalculator() {
        return null;
    }

    @Override
    public NetworkStateExporter getNetworkStateExporter() {
        return null;
    }

}
