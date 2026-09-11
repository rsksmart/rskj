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

package co.rsk.util;

import co.rsk.crypto.Keccak256;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.datasource.KeyValueDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BlockStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(BlockStateHandler.class);

    private static final int CACHE_SIZE = 10_000_000;
    private static final int BATCH_SIZE = 1_000_000;

    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private final StateRootHandler stateRootHandler;

    public BlockStateHandler(@Nonnull Blockchain blockchain, @Nonnull TrieStore trieStore,
                             @Nonnull StateRootHandler stateRootHandler) {
        this.blockchain = Objects.requireNonNull(blockchain);
        this.trieStore = Objects.requireNonNull(trieStore);
        this.stateRootHandler = Objects.requireNonNull(stateRootHandler);
    }

    public void copyState(long lastNumOfBlocksToPreserve, @Nonnull KeyValueDataSource destDataSource) {
        if (lastNumOfBlocksToPreserve <= 0) {
            throw new IllegalArgumentException("lastNumOfBlocksToPreserve: " + lastNumOfBlocksToPreserve);
        }

        Block bestBlock = Objects.requireNonNull(blockchain.getBestBlock());
        long toBlock = bestBlock.getNumber();
        long fromBlock = Math.max(0, toBlock - lastNumOfBlocksToPreserve + 1);
        long curBlock = fromBlock;
        long blockCount = toBlock - fromBlock + 1;
        List<Block> blocks;

        TrieHandler trieHandler = new TrieHandler(destDataSource, CACHE_SIZE, BATCH_SIZE);

        logger.info("Start copying states for {} block(s) of range: [{}; {}]", blockCount, fromBlock, toBlock);

        long startTime = System.currentTimeMillis();

        do {
            blocks = Objects.requireNonNull(blockchain.getBlocksByNumber(curBlock));
            for (Block block : blocks) {
                Keccak256 stateRoot = stateRootHandler.translate(block.getHeader());

                Optional<Trie> trieOpt = trieStore.retrieve(stateRoot.getBytes());
                if (trieOpt.isPresent()) {
                    Trie trie = trieOpt.get();
                    trieHandler.copyTrie(curBlock, trie);
                } else {
                    logger.info("No trie found at block height: {}. Moving to next one", curBlock);
                }
            }

            curBlock++;
        } while (curBlock <= toBlock || !blocks.isEmpty());

        long endTime = System.currentTimeMillis();
        Duration duration = Duration.of(endTime - startTime, ChronoUnit.MILLIS);

        logger.info("Finished copying states. Processed {} block(s). Duration: {}", blockCount, duration);
    }

    public static class MissingBlockException extends RuntimeException {

        public MissingBlockException(String message) {
            super(message);
        }
    }

    public static class InvalidBlockException extends RuntimeException {

        public InvalidBlockException(String message) {
            super(message);
        }
    }

    public static class MissingTrieException extends RuntimeException {

        public MissingTrieException(String message) {
            super(message);
        }
    }
}
