/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
import co.rsk.cli.CliToolRskContextAware;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;

/**
 * The entry point for rewind blocks state CLI tool
 * This is an experimental/unsupported tool
 *
 * Required cli args:
 * - args[0] - block number or "fmi"/"rbc" options ("find min inconsistent block" / "rewind to best consistent block" respectively)
 *
 * Notes:
 * - inconsistent block is the one with missing state in the states db (this can happen in case of improper node shutdown);
 * - "fmi" option can be used for finding minimum inconsistent block number and printing it to stdout. It'll print -1, if no such block is found;
 * - "rbc" option does two things: it looks for minimum inconsistent block and, if there's such, rewinds blocks from top one till the found one inclusively.
 */
public class RewindBlocks extends CliToolRskContextAware {

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    private final Printer printer;

    @SuppressWarnings("unused")
    public RewindBlocks() { // used via reflection
        this(RewindBlocks::printInfo);
    }

    @VisibleForTesting
    RewindBlocks(@Nonnull Printer printer) {
        this.printer = Objects.requireNonNull(printer);
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) {
        BlockStore blockStore = ctx.getBlockStore();
        String blockNumOrOp = args[0];

        if ("fmi".equals(blockNumOrOp)) {
            RepositoryLocator repositoryLocator = ctx.getRepositoryLocator();

            printMinInconsistentBlock(blockStore, repositoryLocator);
        } else if ("rbc".equals(blockNumOrOp)) {
            RepositoryLocator repositoryLocator = ctx.getRepositoryLocator();

            rewindInconsistentBlocks(blockStore, repositoryLocator);
        } else {
            long blockNumber = Long.parseLong(blockNumOrOp);

            rewindBlocks(blockNumber, blockStore);
        }
    }

    private void printMinInconsistentBlock(@Nonnull BlockStore blockStore, @Nonnull RepositoryLocator repositoryLocator) {
        printInfo("Highest block number stored in db: {}", Optional.ofNullable(blockStore.getBestBlock()).map(Block::getNumber).orElse(-1L));

        long minInconsistentBlockNum = findMinInconsistentBlock(blockStore, repositoryLocator);
        if (minInconsistentBlockNum == -1) {
            printer.println("No inconsistent block has been found");
        } else {
            printer.println("Min inconsistent block number: " + minInconsistentBlockNum);
        }
    }

    private void rewindInconsistentBlocks(@Nonnull BlockStore blockStore, @Nonnull RepositoryLocator repositoryLocator) {
        long minInconsistentBlockNum = findMinInconsistentBlock(blockStore, repositoryLocator);
        if (minInconsistentBlockNum == -1) {
            printer.println("No inconsistent block has been found. Nothing to do");
        } else if (minInconsistentBlockNum < blockStore.getMinNumber()) {
            printer.println("Cannot rewind to " + minInconsistentBlockNum + ", such block is not in store.");
        } else {
            printer.println("Min inconsistent block number: " + minInconsistentBlockNum);
            rewindBlocks(minInconsistentBlockNum - 1, blockStore);
        }
    }

    private long findMinInconsistentBlock(@Nonnull BlockStore blockStore, @Nonnull RepositoryLocator repositoryLocator) {
        long minInconsistentBlockNum = -1L;
        Block block = blockStore.getBestBlock();

        if (block != null) { // block index is not empty
            while (!repositoryLocator.findSnapshotAt(block.getHeader()).isPresent()) {
                minInconsistentBlockNum = block.getNumber();

                Keccak256 parentHash = Objects.requireNonNull(block.getParentHash());

                block = blockStore.getBlockByHash(parentHash.getBytes());
                if (block == null) { // parent of genesis / non-indexed block
                    break;
                }
            }
        }

        return minInconsistentBlockNum;
    }

    private void rewindBlocks(long blockNumber, BlockStore blockStore) {
        long maxNumber = tryGetMaxNumber(blockStore);

        printInfo("Highest block number stored in db: {}", maxNumber);
        printInfo("Block number to rewind to: {}", blockNumber);

        if (maxNumber > blockNumber) {
            printInfo("Rewinding...");

            blockStore.rewind(blockNumber);

            printer.println("Done");

            maxNumber = tryGetMaxNumber(blockStore);
            printer.println("New highest block number stored in db: " + maxNumber);
        } else {
            printer.println("No need to rewind");
        }
    }

    private static long tryGetMaxNumber(BlockStore blockStore) {
        long maxNumber;
        try {
            maxNumber = blockStore.getMaxNumber();
        } catch (IllegalStateException ise) {
            maxNumber = -1;
        }
        return maxNumber;
    }
}
