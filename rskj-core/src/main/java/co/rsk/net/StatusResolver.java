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

package co.rsk.net;

import co.rsk.core.BlockDifficulty;
import org.ethereum.core.Block;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;

public class StatusResolver {

    private final BlockStore blockStore;
    private final Genesis genesis;

    public StatusResolver(BlockStore blockStore, Genesis genesis) {

        this.blockStore = blockStore;
        this.genesis = genesis;
    }


    /**
     * Resolves the current status to broadcast and send to peers.
     */
    public Status currentStatus() {
        Status status;
        if (blockStore.getMinNumber() != 0) {
            status = new Status(
                    genesis.getNumber(),
                    genesis.getHash().getBytes(),
                    genesis.getParentHash().getBytes(),
                    genesis.getCumulativeDifficulty());
        } else {
            status = getStatusFromBestBlock();
        }
        return status;
    }

    /**
     * Unlike #currentStatus(), this method will return best block info even if genesis is not connected
     */
    public Status currentStatusLenient() {
        return getStatusFromBestBlock();
    }

    private Status getStatusFromBestBlock() {
        Status status;
        Block block = blockStore.getBestBlock();
        BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(block.getHash().getBytes());

        status = new Status(block.getNumber(),
                block.getHash().getBytes(),
                block.getParentHash().getBytes(),
                totalDifficulty);
        return status;
    }
}
