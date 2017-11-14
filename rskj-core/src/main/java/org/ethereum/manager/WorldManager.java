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
import org.ethereum.core.Blockchain;
import org.ethereum.core.PendingState;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;

/**
 * WorldManager is a singleton containing references to different parts of the system.
 * This interface facilitate the writing of unit tests
 *
 * @author Ruben Altman
 * @since 09.06.2016
 */

public interface WorldManager {

    void init();

    void addListener(EthereumListener listener);

    ChannelManager getChannelManager();

    org.ethereum.facade.Repository getRepository();

    Blockchain getBlockchain();

    BlockStore getBlockStore();

    PendingState getPendingState();

    void close() ;

    ConfigCapabilities getConfigCapabilities();

    BlockProcessor getNodeBlockProcessor();

    HashRateCalculator getHashRateCalculator();

    NetworkStateExporter getNetworkStateExporter();

}
