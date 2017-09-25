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

import co.rsk.core.Rsk;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.MessageHandler;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Blockchain;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.Repository;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.manager.AdminInfo;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.vm.program.ProgramResult;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by ajlopez on 12/07/2017.
 */
public class SimpleRsk extends SimpleEthereum implements Rsk {
    private PeerScoringManager peerScoringManager;

    @Override
    public MinerClient getMinerClient() {
        return null;
    }

    @Override
    public MinerServer getMinerServer() {
        return null;
    }

    @Override
    public MessageHandler getMessageHandler() {
        return null;
    }

    public void setPeerScoringManager(PeerScoringManager peerScoringManager) {
        this.peerScoringManager = peerScoringManager;
    }

    @Override
    public PeerScoringManager getPeerScoringManager() {
        return this.peerScoringManager;
    }

    @Override
    public NodeBlockProcessor getNodeBlockProcessor() {
        return null;
    }

    @Override
    public boolean isPlayingBlocks() {
        return false;
    }

    @Override
    public boolean isSyncingBlocks() {
        return false;
    }

    @Override
    public boolean isBlockchainEmpty() {
        return false;
    }

    @Override
    public boolean hasBetterBlockToSync() {
        return false;
    }
}
