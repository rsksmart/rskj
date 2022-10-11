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

import co.rsk.cli.PicoCliToolRskContextAware;
import co.rsk.core.BlockDifficulty;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;
import picocli.CommandLine;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;

/**
 * The entry point for export blocks CLI tool
 * This is an experimental/unsupported tool
 *
 * Required cli args:
 * - args[0] - from block number
 * - args[1] - to block number
 * - args[2] - file path
 */
@CommandLine.Command(name = "export-blocks", mixinStandardHelpOptions = true, version = "export-blocks 1.0",
        description = "The entry point for export blocks CLI tool")
public class ExportBlocks extends PicoCliToolRskContextAware {
    @CommandLine.Option(names = {"-fb", "--fromblock"}, description = "From block number", required = true)
    private Long fromBlockNumber;

    @CommandLine.Option(names = {"-tb", "--toblock"}, description = "To block number", required = true)
    private Long toBlockNumber;

    @CommandLine.Option(names = {"-f", "--file"}, description = "File path", required = true)
    private String filePath;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        BlockStore blockStore = ctx.getBlockStore();

        try (PrintStream writer = new PrintStream(new BufferedOutputStream(new FileOutputStream(filePath)))) {
            exportBlocks(blockStore, writer);
        }

        return 0;
    }

    private void exportBlocks(BlockStore blockStore, PrintStream writer) {
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
