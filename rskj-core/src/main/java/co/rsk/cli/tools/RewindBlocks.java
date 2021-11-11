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
import org.ethereum.db.BlockStore;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;

/**
 * The entry point for rewind blocks state CLI tool
 * This is an experimental/unsupported tool
 *
 * Required cli args:
 * - args[0] - block number
 */
public class RewindBlocks extends CliToolRskContextAware {

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) {
        long blockNumber = Long.parseLong(args[0]);
        BlockStore blockStore = ctx.getBlockStore();

        rewindBlocks(blockNumber, blockStore);
    }

    private void rewindBlocks(long blockNumber, BlockStore blockStore) {
        long maxNumber = blockStore.getMaxNumber();

        printInfo("Highest block number stored in db: {}", maxNumber);
        printInfo("Block number to rewind to: {}", blockNumber);

        if (maxNumber > blockNumber) {
            printInfo("Rewinding...");

            blockStore.rewind(blockNumber);

            maxNumber = blockStore.getMaxNumber();
            printInfo("Done. New highest block number stored in db: {}", maxNumber);
        } else {
            printInfo("No need to rewind");
        }
    }
}
