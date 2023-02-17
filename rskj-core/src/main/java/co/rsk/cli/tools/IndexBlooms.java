/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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
package co.rsk.cli.tools;

import co.rsk.RskContext;
import co.rsk.logfilter.BlocksBloom;
import co.rsk.logfilter.BlocksBloomStore;
import org.ethereum.core.Block;
import org.ethereum.core.Bloom;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * The entry point for indexing block blooms
 * This is an experimental/unsupported tool
 */
public class IndexBlooms {

    private static final Logger logger = LoggerFactory.getLogger(IndexBlooms.class);

    private static final String EARLIEST = "earliest";
    private static final String LATEST = "latest";

    public static void main(String[] args) {
        try (RskContext ctx = new RskContext(args)) {
            BlockStore blockStore = ctx.getBlockStore();
            BlocksBloomStore blocksBloomStore = ctx.getBlocksBloomStore();

            execute(makeBlockRange(args, blockStore), blockStore, blocksBloomStore);
        }
    }

    /**
     * Creates a block range by extract from/to values from {@code args}.
     */
    @Nonnull
    static Range makeBlockRange(@Nonnull String[] args, @Nonnull BlockStore blockStore) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Missing 'from' and/or 'to' block number(s)");
        }

        long minNumber = blockStore.getMinNumber();
        long maxNumber = blockStore.getMaxNumber();

        long fromBlockNumber = EARLIEST.equals(args[0]) ? minNumber : Long.parseLong(args[0]);
        long toBlockNumber = LATEST.equals(args[1]) ? maxNumber : Long.parseLong(args[1]);

        if (fromBlockNumber < 0 || fromBlockNumber > toBlockNumber) {
            throw new IllegalArgumentException("Invalid 'from' and/or 'to' block number");
        }

        if (fromBlockNumber < minNumber) {
            throw new IllegalArgumentException("'from' block number is lesser than the min block number stored");
        }

        if (toBlockNumber > maxNumber) {
            throw new IllegalArgumentException("'to' block number is greater than the best block number");
        }

        return new Range(fromBlockNumber, toBlockNumber);
    }

    /**
     * Indexes block blooms in the {@link blockRange} range.
     */
    static void execute(Range blockRange,
                        BlockStore blockStore,
                        BlocksBloomStore blocksBloomStore) {
        BlocksBloom auxiliaryBlocksBloom = null;
        long curProgress = 0L;

        for (long blockNum = blockRange.fromBlockNumber; blockNum <= blockRange.toBlockNumber; blockNum++) {
            if (blocksBloomStore.firstNumberInRange(blockNum) == blockNum) {
                auxiliaryBlocksBloom = new BlocksBloom();
            }

            if (auxiliaryBlocksBloom == null) {
                continue;
            }

            Block block = blockStore.getChainBlockByNumber(blockNum);

            auxiliaryBlocksBloom.addBlockBloom(blockNum, new Bloom(block.getLogBloom()));

            if (blocksBloomStore.lastNumberInRange(blockNum) == blockNum) {
                blocksBloomStore.addBlocksBloom(auxiliaryBlocksBloom);
            }

            long progress = 100 * (blockNum - blockRange.fromBlockNumber + 1) / (blockRange.toBlockNumber - blockRange.fromBlockNumber + 1);
            if (progress > curProgress) {
                curProgress = progress;
                logger.info("Processed {}% of blocks", progress);
            }
        }
    }

    /**
     * Represents a block range in a form of [from..to].
     */
    static class Range {
        public final long fromBlockNumber;
        public final long toBlockNumber;

        Range(long fromBlockNumber, long toBlockNumber) {
            this.fromBlockNumber = fromBlockNumber;
            this.toBlockNumber = toBlockNumber;
        }
    }
}
