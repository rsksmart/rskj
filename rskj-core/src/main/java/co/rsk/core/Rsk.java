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

import co.rsk.mine.MinerServer;
import co.rsk.mine.MinerClient;
import co.rsk.net.MessageHandler;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.facade.Ethereum;

/**
 * Created by ajlopez on 3/3/2016.
 */
public interface Rsk extends Ethereum {
    MinerClient getMinerClient();

    MinerServer getMinerServer();

    MessageHandler getMessageHandler();

    PeerScoringManager getPeerScoringManager();

    NodeBlockProcessor getNodeBlockProcessor();

    boolean isPlayingBlocks();

    boolean isSyncingBlocks();

    boolean isBlockchainEmpty();

    boolean hasBetterBlockToSync();
}
