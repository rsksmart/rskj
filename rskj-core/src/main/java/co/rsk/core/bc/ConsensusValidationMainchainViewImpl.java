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

package co.rsk.core.bc;

import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConsensusValidationMainchainViewImpl implements ConsensusValidationMainchainView {

    private static final Logger logger = LoggerFactory.getLogger("consensusmainchainview");

    private final BlockStore blockStore;

    private Map<Keccak256, BlockHeader> pendingHeadersByHash;

    public ConsensusValidationMainchainViewImpl(BlockStore blockStore) {
        this.blockStore = blockStore;
    }

    /**
     * Design decision note. headers for blocks not yet added to the blockchain are not in the BlockStore.
     * These headers are in a collection used by the SyncProcessor and friends.
     * Sync process creates a new version of this collection periodically and that is why a setter is needed.
     */
    public void setPendingHeaders(Map<Keccak256, BlockHeader> pendingHeadersByHash) {
        this.pendingHeadersByHash = pendingHeadersByHash;
    }

    @Override
    public synchronized List<BlockHeader> get(Keccak256 startingHashToGetMainchainFrom, int height) {
        List<BlockHeader> headers = new ArrayList<>();

        Keccak256 currentHash = startingHashToGetMainchainFrom;
        for(int i = 0; i < height; i++) {
            Block block = blockStore.getBlockByHash(currentHash.getBytes());
            BlockHeader header;
            if (block != null) {
                header = block.getHeader();
            } else {
                if(pendingHeadersByHash == null) {
                    logger.error("Pending headers by hash has not been set.");
                    return new ArrayList<>();
                }
                header = pendingHeadersByHash.get(currentHash);
            }

            if(header == null) {
                return new ArrayList<>();
            }

            headers.add(header);
            currentHash = header.getParentHash();
        }

        return headers;
    }
}
