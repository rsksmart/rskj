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
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.BlockStore;

import java.util.Arrays;

public class BlockstoreBlockPlayer {
    private final Blockchain targetBlockchain;
    private final BlockStore blockStore;

    private long blockNumber;

    private BlockstoreBlockPlayer(BlockStore blockStore, Blockchain targetBlockchain) {
        this.blockStore = blockStore;
        this.targetBlockchain = targetBlockchain;
        this.blockNumber = this.targetBlockchain.getBestBlock().getNumber() + 1;
    }

    private void connectBlocks() {
        for (Block block = nextBlock(blockStore); block != null; block = nextBlock(blockStore)) {
            if (!connectBlock(block)) {
                System.err.printf("Import failed at block %s\n", block.getNumber());
                System.exit(1);
                return;
            }

            if (block.getNumber() % 100 == 0) {
                System.out.printf("Imported block with number %7d\n", block.getNumber());
            }
        }

        System.out.printf("Best block is now %7d%n", targetBlockchain.getBestBlock().getNumber());
    }

    private boolean connectBlock(Block block) {
        ImportResult tryToConnectResult = targetBlockchain.tryToConnect(block);
        return BlockProcessResult.importOk(tryToConnectResult);
    }

    private Block nextBlock(BlockStore sourceBlockStore) {
        return sourceBlockStore.getChainBlockByNumber(blockNumber++);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("usage: BlockstoreBlockPlayer [<node cli args>] <block store source dir>");
            System.exit(0);
            return;
        }

        String[] nodeCliArgs = Arrays.copyOf(args, args.length - 1);
        RskContext objects = new RskContext(nodeCliArgs);
        BlockStore blockStore = objects.buildBlockStore(args[args.length - 1], false);
        Blockchain blockchain = objects.getBlockchain();
        BlockstoreBlockPlayer blockPlayer = new BlockstoreBlockPlayer(blockStore, blockchain);
        blockPlayer.connectBlocks();
        System.exit(0);
    }
}
