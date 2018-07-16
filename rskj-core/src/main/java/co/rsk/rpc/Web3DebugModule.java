/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc;

import co.rsk.blocks.FileBlockPlayer;
import co.rsk.blocks.FileBlockRecorder;
import co.rsk.rpc.modules.debug.DebugModule;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;

public interface Web3DebugModule {

    default String debug_wireProtocolQueueSize() {
        return getDebugModule().wireProtocolQueueSize();
    }

    default void debug_dumpBlocks(String fileName) throws Exception {
        final Logger logger = getDebugModule().getLogger();
        if (fileName == null || fileName.length() == 0) {
            fileName = "allblocks";
        }

        logger.debug("About to dump blocks into {}", fileName);
        FileBlockRecorder recorder = null;
        try {
            recorder = new FileBlockRecorder(fileName);
            long startTime = System.currentTimeMillis();
            BlockStore blockStore = getDebugModule().getBlockchain().getBlockStore();
            long best = blockStore.getBestBlock().getNumber();
            logger.debug("Dumping blocks");
            for (int i = 1; i <= best; i++) {
                if (i % 1000 == 0) {

                    double done = i;
                    double deltaTime = System.currentTimeMillis() - startTime;
                    double remaining = (deltaTime / done) * ((double) best - done);
                    logger.trace("Dumping block at height {}, remaining {} blocks, expected remaining time {}", i, best - i, remaining);
                }
                Block block = blockStore.getChainBlockByNumber(i);
                recorder.writeBlock(block);
            }
            logger.debug("Finished dumping blocks, took {}ms", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            recorder.close();
            logger.debug("Stop dumping blocks");
        }


    }

    default void debug_readBlocks(String fileName) throws Exception {
        final Logger logger = getDebugModule().getLogger();
        if (fileName == null || fileName.length() == 0) {
            fileName = "allblocks";
        }

        logger.debug("About to read blocks from {}", fileName);
        FileBlockPlayer player = null;
        try {
            player = new FileBlockPlayer(null, fileName);
            long startTime = System.currentTimeMillis();
            Blockchain blockchain = getDebugModule().getBlockchain();
            logger.debug("Reading blocks");
            while (true) {

                Block block = player.readBlock();
                if (block == null) {
                    break;
                }

                blockchain.tryToConnect(block);
            }
            logger.debug("Finished reading blocks, took {}ms", System.currentTimeMillis() - startTime);

        } finally {
            player.close();
            logger.debug("Stop dumping blocks");
        }
    }
    DebugModule getDebugModule();
}

