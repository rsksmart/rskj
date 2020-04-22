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
package co.rsk;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.trie.TrieStore;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

/**
 * The entrypoint for execute blocks CLI util
 */
public class ExecuteBlocks {
    public static void main(String[] args) {
        RskContext ctx = new RskContext(args);

        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber = Long.parseLong(args[1]);

        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block = blockStore.getChainBlockByNumber(n);
            Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());

            System.out.println("block number " + block.getNumber());
            System.out.println("block hash " + Hex.toHexString(block.getHash().getBytes()));
            System.out.println("state root " + Hex.toHexString(block.getStateRoot()));

            System.out.println("parent number " + parent.getNumber());
            System.out.println("parent hash " + Hex.toHexString(parent.getHash().getBytes()));
            System.out.println("parent root " + Hex.toHexString(parent.getStateRoot()));

            blockExecutor.execute(block, parent.getHeader(), false, false);
        }

        trieStore.flush();
        blockStore.flush();
    }
}
