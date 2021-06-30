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
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.crypto.Keccak256;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import java.util.Arrays;

/**
 * The entry point for execute blocks CLI tool
 * This is an experimental/unsupported tool
 */
public class ExecuteBlocks {
    public static void main(String[] args) {
        RskContext ctx = new RskContext(args);

        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();
        StateRootHandler stateRootHandler = ctx.getStateRootHandler();

        execute(args, blockExecutor, blockStore, trieStore, stateRootHandler);
    }
    
    public static void execute(String[] args, BlockExecutor blockExecutor, BlockStore blockStore, TrieStore trieStore,
                               StateRootHandler stateRootHandler) {
        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber = Long.parseLong(args[1]);

        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block = blockStore.getChainBlockByNumber(n);
            Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());

            BlockResult blockResult = blockExecutor.execute(block, parent.getHeader(), false, false);

            Keccak256 stateRootHash = stateRootHandler.translate(block.getHeader());
            if (!Arrays.equals(blockResult.getFinalState().getHash().getBytes(), stateRootHash.getBytes())) {
                System.err.println("Invalid state root block number " + n);
                break;
            }
        }

        trieStore.flush();
        blockStore.flush();
    }
}
