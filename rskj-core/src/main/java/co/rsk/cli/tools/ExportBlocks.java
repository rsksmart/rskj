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
import co.rsk.core.BlockDifficulty;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;

import java.io.PrintStream;

/**
 * The entry point for export blocks CLI tool
 * This is an experimental/unsupported tool
 */
public class ExportBlocks {
    public static void main(String[] args) {
        RskContext ctx = new RskContext(args);
        BlockStore blockStore = ctx.getBlockStore();

        execute(args, blockStore, System.out);
    }

    public static void execute(String[] args, BlockStore blockStore, PrintStream writer) {
        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber = Long.parseLong(args[1]);

        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block = blockStore.getChainBlockByNumber(n);
            BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(block.getHash().getBytes());

            writer.println(
                block.getNumber() + "," +
                ByteUtil.toHexString(block.getHash().getBytes()) + "," +
                ByteUtil.toHexString(totalDifficulty.getBytes()) + "," +
                ByteUtil.toHexString(block.getEncoded())
            );
        }
    }
}
