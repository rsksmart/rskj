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

import co.rsk.cli.PicoCliToolRskContextAware;
import co.rsk.logfilter.BlocksBloom;
import co.rsk.logfilter.BlocksBloomStore;
import org.ethereum.core.Block;
import org.ethereum.core.Bloom;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

/**
 * The entry point for indexing block blooms
 * This is an experimental/unsupported tool
 */
@CommandLine.Command(name = "index-blooms", mixinStandardHelpOptions = true, version = "index-blooms 1.0",
        description = "Indexes blooms for a specific block range")
public class IndexBlooms extends PicoCliToolRskContextAware {

    @CommandLine.Option(names = {"-fb", "--fromBlock"}, description = "From block number", required = true)
    private String fromBlockNumber;

    @CommandLine.Option(names = {"-tb", "--toBlock"}, description = "To block number", required = true)
    private String toBlockNumber;

    private static final Logger logger = LoggerFactory.getLogger(IndexBlooms.class);

    private static final String EARLIEST = "earliest";
    private static final String LATEST = "latest";

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        BlockStore blockStore = ctx.getBlockStore();
        BlocksBloomStore blocksBloomStore = ctx.getBlocksBloomStore();

        execute(makeBlockRange(fromBlockNumber, toBlockNumber, blockStore), blockStore, blocksBloomStore);

        return 0;
    }

    /**
     * Creates a block range by extract from/to values from {@code args}.
     */
    @Nonnull
    static Range makeBlockRange(String fromBlock, String toBlock, @Nonnull BlockStore blockStore) {
        if (fromBlock == null || toBlock == null) {
            throw new IllegalArgumentException("Missing 'from' and/or 'to' block number(s)");
        }

        long minNumber = blockStore.getMinNumber();
        long maxNumber = blockStore.getMaxNumber();

        long fromBlockNumber = EARLIEST.equals(fromBlock) ? minNumber : Long.parseLong(fromBlock);
        long toBlockNumber = LATEST.equals(toBlock) ? maxNumber : Long.parseLong(toBlock);

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
                auxiliaryBlocksBloom = BlocksBloom.createEmpty();
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
