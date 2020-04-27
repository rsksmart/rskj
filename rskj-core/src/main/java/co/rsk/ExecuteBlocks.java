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

import co.rsk.core.bc.BlockExecutor;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

/**
 * The entry point for execute blocks CLI tool
 */
public class ExecuteBlocks {
    public static void main(String[] args) {
        RskContext ctx = new RskContext(args);

        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();
        
        execute(args, blockExecutor, blockStore, trieStore);
    }
    
    public static void execute(String[] args, BlockExecutor blockExecutor, BlockStore blockStore, TrieStore trieStore) {
        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber = Long.parseLong(args[1]);

        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block = blockStore.getChainBlockByNumber(n);
            Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());

            blockExecutor.execute(block, parent.getHeader(), false, false);
        }

        trieStore.flush();
        blockStore.flush();
    }
}
