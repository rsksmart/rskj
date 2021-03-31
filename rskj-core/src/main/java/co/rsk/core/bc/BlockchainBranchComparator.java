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

import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import java.util.LinkedList;
import java.util.List;

public class BlockchainBranchComparator {
    private final BlockStore blockStore;

    public BlockchainBranchComparator(BlockStore blockStore) {
        this.blockStore = blockStore;
    }

    /**
     * Returns the branches of both blocks up to a common ancestor
     */
    public BlockFork calculateFork(Block fromBlock, Block toBlock) {
        List<Block> oldBlocks = new LinkedList<>();
        List<Block> newBlocks = new LinkedList<>();

        Block oldBlock = fromBlock;
        Block newBlock = toBlock;

        if (oldBlock.isParentOf(newBlock)) {
            newBlocks.add(newBlock);
            return new BlockFork(oldBlock, oldBlocks, newBlocks);
        }

        while (newBlock.getNumber() > oldBlock.getNumber()) {
            newBlocks.add(0, newBlock);
            newBlock = blockStore.getBlockByHash(newBlock.getParentHash().getBytes());
        }

        while (oldBlock.getNumber() > newBlock.getNumber()) {
            oldBlocks.add(0, oldBlock);
            oldBlock = blockStore.getBlockByHash(oldBlock.getParentHash().getBytes());
        }

        while (!oldBlock.getHash().equals(newBlock.getHash())) {
            newBlocks.add(0, newBlock);
            newBlock = blockStore.getBlockByHash(newBlock.getParentHash().getBytes());
            oldBlocks.add(0, oldBlock);
            oldBlock = blockStore.getBlockByHash(oldBlock.getParentHash().getBytes());
        }

        return new BlockFork(oldBlock, oldBlocks, newBlocks);
    }
}
