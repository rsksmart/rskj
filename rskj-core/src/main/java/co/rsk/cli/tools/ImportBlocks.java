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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.db.BlockStore;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;

/**
 * The entry point for import blocks CLI tool
 * This is an experimental/unsupported tool
 */
public class ImportBlocks {
    public static void main(String[] args) throws IOException {
        RskContext ctx = new RskContext(args);
        BlockFactory blockFactory = ctx.getBlockFactory();
        BlockStore blockStore = ctx.getBlockStore();

        String filename = args[0];

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            execute(blockFactory, blockStore, reader);
        }
    }

    public static void execute(BlockFactory blockFactory, BlockStore blockStore, BufferedReader reader) throws IOException {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            String[] parts = line.split(",");

            if (parts.length < 4) {
                continue;
            }

            byte[] encoded = Hex.decode(parts[3]);

            Block block = blockFactory.decodeBlock(encoded);

            BlockDifficulty totalDifficulty = new BlockDifficulty(new BigInteger(Hex.decode(parts[2])));

            blockStore.saveBlock(block, totalDifficulty, true);
        }

        blockStore.flush();
    }
}
