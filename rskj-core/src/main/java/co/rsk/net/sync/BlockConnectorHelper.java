/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BlockConnectorHelper {
    private static final Logger logger = LoggerFactory.getLogger("SnapBlockConnector");
    private final BlockStore blockStore;
    private final List<Pair<Block,BlockDifficulty>> blockAndDifficultiesList;

    public BlockConnectorHelper(BlockStore blockStore, List<Pair<Block,BlockDifficulty>> blockAndDifficultiesList) {
        this.blockStore = blockStore;
        this.blockAndDifficultiesList = blockAndDifficultiesList;
        blockAndDifficultiesList.sort(new BlockAndDiffComparator());
    }

    public void startConnecting() {
        Block child = null;
        logger.info("Start connecting Blocks");
        if (blockAndDifficultiesList.isEmpty()) {
            logger.debug("Block list is empty, nothing to connect");
            return;
        }
        int blockIndex = blockAndDifficultiesList.size() - 1;
        if (blockStore.isEmpty()) {
            Pair<Block,BlockDifficulty> blockAndDifficulty = blockAndDifficultiesList.get(blockIndex);
            child = blockAndDifficulty.getLeft();
            logger.debug("BlockStore is empty, setting child block number the last block from the list: {}", child.getNumber());
            blockStore.saveBlock(child, blockAndDifficulty.getRight(), true);
            blockIndex--;
        } else {
            logger.debug("BlockStore is not empty, getting best block");
            child = blockStore.getBestBlock();
            logger.debug("Best block number: {}", child.getNumber());
        }
        while (blockIndex >= 0) {
            Pair<Block,BlockDifficulty> currentBlockAndDifficulty = blockAndDifficultiesList.get(blockIndex);
            Block currentBlock = currentBlockAndDifficulty.getLeft();
            logger.info("Connecting block number: {}", currentBlock.getNumber());

            if (!currentBlock.isParentOf(child)) {
                throw new BlockConnectorException(currentBlock.getNumber(), child.getNumber());
            }
            blockStore.saveBlock(currentBlock, currentBlockAndDifficulty.getRight(), true);
            child = currentBlock;
            blockIndex--;
        }
        logger.info("Finished connecting blocks");
    }

    static class BlockAndDiffComparator implements java.util.Comparator<Pair<Block,BlockDifficulty>> {
        @Override
        public int compare(Pair<Block,BlockDifficulty> o1, Pair<Block,BlockDifficulty> o2) {
            return Long.compare(o1.getLeft().getNumber(),o2.getLeft().getNumber());
        }
    }
}
