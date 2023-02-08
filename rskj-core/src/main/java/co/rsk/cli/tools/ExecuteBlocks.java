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
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.crypto.Keccak256;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import picocli.CommandLine;

import java.io.IOException;
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
@CommandLine.Command(name = "execute-blocks", mixinStandardHelpOptions = true, version = "execute-blocks 1.0",
        description = "Executes blocks for a specified block range")
public class ExecuteBlocks extends PicoCliToolRskContextAware {
    @CommandLine.Option(names = {"-fb", "--fromBlock"}, description = "From block number", required = true)
    private Long fromBlockNumber;

    @CommandLine.Option(names = {"-tb", "--toBlock"}, description = "To block number", required = true)
    private Long toBlockNumber;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();
        StateRootHandler stateRootHandler = ctx.getStateRootHandler();

        executeBlocks(blockExecutor, blockStore, trieStore, stateRootHandler);

        return 0;
    }

    private void executeBlocks(BlockExecutor blockExecutor, BlockStore blockStore, TrieStore trieStore,
                               StateRootHandler stateRootHandler) {
        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block = blockStore.getChainBlockByNumber(n);
            Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());

            BlockResult blockResult = blockExecutor.execute(null, 0, block, parent.getHeader(), false, false, true);

            Keccak256 stateRootHash = stateRootHandler.translate(block.getHeader());
            if (!Arrays.equals(blockResult.getFinalState().getHash().getBytes(), stateRootHash.getBytes())) {
                printError("Invalid state root block number " + n);
                break;
            }
        }

        trieStore.flush();
        blockStore.flush();
    }
}
