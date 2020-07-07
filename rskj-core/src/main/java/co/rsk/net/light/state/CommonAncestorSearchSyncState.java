/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light.state;

import co.rsk.net.light.LightPeer;
import co.rsk.net.light.LightSyncProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CommonAncestorSearchSyncState implements LightSyncState {
    public static final int MAX_REQUESTED_HEADERS = 192; //Based in max_chunks, this number should be in the config file in some light section
    private final LightSyncProcessor lightSyncProcessor;
    private final LightPeer lightPeer;
    private final byte[] bestBlockHash;
    private final long bestBlockNumber;
    private final Blockchain blockchain;
    private static final Logger logger = LoggerFactory.getLogger("lightprocessor");

    public CommonAncestorSearchSyncState(LightSyncProcessor lightSyncProcessor, LightPeer lightPeer, byte[] bestBlockHash, long bestBlockNumber, Blockchain blockchain) {
        this.lightSyncProcessor = lightSyncProcessor;
        this.lightPeer = lightPeer;
        this.bestBlockHash = bestBlockHash.clone();
        this.bestBlockNumber = bestBlockNumber;
        this.blockchain = blockchain;
    }

    @Override
    public void sync() {
        int max = bestBlockNumber < MAX_REQUESTED_HEADERS ? (int) bestBlockNumber : MAX_REQUESTED_HEADERS;
        lightSyncProcessor.sendBlockHeadersMessage(lightPeer, bestBlockHash, max, 0, true);
    }

    @Override
    public void newBlockHeaders(LightPeer lightPeer, List<BlockHeader> blockHeaders) {
        for (BlockHeader bh : blockHeaders) {
            if (isKnown(bh)) {
                logger.trace("Found common ancestor with best chain");
                lightSyncProcessor.foundCommonAncestor();
                return;
            }
        }

        long newStart = bestBlockNumber - blockHeaders.size();
        if (newStart != 0) {
            final Block newStartBlock = blockchain.getBlockByNumber(newStart);
            lightSyncProcessor.startAncestorSearchFrom(lightPeer, newStartBlock.getHash().getBytes(), newStartBlock.getNumber());
        }
    }

    private boolean isKnown(BlockHeader bh) {
        return blockchain.getBlockByHash(bh.getHash().getBytes()) != null;
    }
}