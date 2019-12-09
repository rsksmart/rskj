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

import co.rsk.net.BlockProcessResult;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.BlockStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

public class BlocksFilePlayer {
    private final Blockchain targetBlockchain;
    private final BlockFactory blockFactory;
    private final String filename;
    private final BlockStore blockstore;

    private BlocksFilePlayer(String filename, RskContext objects) {
        this.targetBlockchain = objects.getBlockchain();
        this.blockFactory = objects.getBlockFactory();
        this.filename = filename;
        this.blockstore = objects.getBlockStore();
    }

    private void connectBlocks() throws IOException {
        try (Stream<String> lines = Files.lines(Paths.get(filename))) {
            long blocksToSkip = targetBlockchain.getBestBlock().getNumber();
            lines.skip(blocksToSkip).map(this::readBlock).limit(150000).forEach(this::connectBlock);
            blockstore.flush();
            System.out.printf("Best block is now %7d%n", targetBlockchain.getBestBlock().getNumber());
        }
    }

    private Block readBlock(String line) {
        String[] parts = line.split(",");
        return blockFactory.decodeBlock(Hex.decode(parts[parts.length - 1]));
    }

    private void connectBlock(Block block) {
        ImportResult tryToConnectResult = targetBlockchain.tryToConnect(block);
        if (!BlockProcessResult.importOk(tryToConnectResult)) {
            System.err.printf("Import failed at block %7d%n", block.getNumber());
            System.exit(1);
            return;
        }

        if (block.getNumber() % 100 == 0) {
            System.out.printf("Imported block with number %7d%n", block.getNumber());
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage: FileBlockPlayer [<node cli args>] <source file>");
            System.exit(0);
            return;
        }

        String[] nodeCliArgs = Arrays.copyOf(args, args.length - 1);
        final boolean useSnappy = false;
        RskContext objects = new RskContext(nodeCliArgs, useSnappy);
        BlocksFilePlayer bplayer = new BlocksFilePlayer(args[args.length - 1], objects);
        bplayer.connectBlocks();
        System.exit(0);
    }
}
