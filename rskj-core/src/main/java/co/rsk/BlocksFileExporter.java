/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class BlocksFileExporter {
    private final BlockStore sourceBlockStore;
    private final String filename;

    private long blockNumber;

    private BlocksFileExporter(String filename, RskContext objects) {
        this.sourceBlockStore = objects.getBlockStore();
        this.filename = filename;
        this.blockNumber = 1; // skip the genesis block
    }

    private void exportBlocks() throws IOException {
        try (FileWriter fileWriter = new FileWriter(filename);
             BufferedWriter writer = new BufferedWriter(fileWriter)) {
            for (Block block = nextBlock(); block != null; block = nextBlock()) {
                writer.write(String.valueOf(block.getNumber()));
                writer.write(",");
                writer.write(Hex.toHexString(block.getEncoded()));
                writer.newLine();
            }

            System.out.printf("Best block exported is %7d%n", blockNumber);
        }
    }

    private Block nextBlock() {
        return sourceBlockStore.getChainBlockByNumber(blockNumber++);
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage: FileBlockExporter [<node cli args>] <target file>");
            System.exit(0);
            return;
        }

        args = new String[]{"-base-path","/Users/julian/workspace/rskj-projects/dbs/database","/Users/julian/workspace/rskj-projects/dbs/fileExporter"}; // db src + fileExporter
        String[] nodeCliArgs = Arrays.copyOf(args, args.length - 1);
        RskContext objects = new RskContext(nodeCliArgs);
        BlocksFileExporter bplayer = new BlocksFileExporter(args[args.length - 1], objects);
        bplayer.exportBlocks();
        System.exit(0);
    }
}
