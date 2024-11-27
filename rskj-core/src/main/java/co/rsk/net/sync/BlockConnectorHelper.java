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

    public BlockConnectorHelper(BlockStore blockStore) {
        this.blockStore = blockStore;
    }

    public void startConnecting(List<Pair<Block, BlockDifficulty>> blockAndDifficultiesList) {
        if (blockAndDifficultiesList.isEmpty()) {
            logger.warn("Block list is empty, nothing to connect");
            return;
        }

        logger.info("Start connecting blocks ranging from [{}] to [{}] - Total: [{}]",
                blockAndDifficultiesList.get(0).getKey().getNumber(),
                blockAndDifficultiesList.get(blockAndDifficultiesList.size() - 1).getKey().getNumber(),
                blockAndDifficultiesList.size());

        int totalSaved = 0;
        for (Pair<Block, BlockDifficulty> pair : blockAndDifficultiesList) {
            Block currentBlock = pair.getLeft();
            logger.trace("Connecting block number: {}", currentBlock.getNumber());

            if (!blockStore.isBlockExist(currentBlock.getHash().getBytes())) {
                blockStore.saveBlock(currentBlock, pair.getRight(), true);
                totalSaved++;
            } else {
                logger.warn("Block: [{}/{}] already exists. Skipping", currentBlock.getNumber(), currentBlock.getHash());
            }
        }

        logger.info("Finished connecting blocks. Total saved: [{}]. ", totalSaved);
    }
}
