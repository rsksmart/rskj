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
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.crypto.Keccak256;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

/**
 * The entry point for execute blocks CLI tool
 * This is an experimental/unsupported tool
 *
 * Required cli args:
 * - args[0] - from block number
 * - args[1] - to block number
 */
public class ExecuteBlocks extends CliToolRskContextAware {

    public static void main(String[] args) {
        execute(args, MethodHandles.lookup().lookupClass());
    }

    public static void executeBlocks(String[] args, BlockExecutor blockExecutor, BlockStore blockStore, TrieStore trieStore,
                                     StateRootHandler stateRootHandler) {
        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber = Long.parseLong(args[1]);

        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block = blockStore.getChainBlockByNumber(n);
            Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());

            BlockResult blockResult = blockExecutor.execute(block, parent.getHeader(), false, false);

            Keccak256 stateRootHash = stateRootHandler.translate(block.getHeader());
            if (!Arrays.equals(blockResult.getFinalState().getHash().getBytes(), stateRootHash.getBytes())) {
                logger.error("Invalid state root block number " + n);
                break;
            }
        }

        trieStore.flush();
        blockStore.flush();
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception {
        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();
        StateRootHandler stateRootHandler = ctx.getStateRootHandler();

        executeBlocks(args, blockExecutor, blockStore, trieStore, stateRootHandler);
    }
}
