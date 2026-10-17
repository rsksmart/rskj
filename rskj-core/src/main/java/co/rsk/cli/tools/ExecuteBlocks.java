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
import com.google.common.annotations.VisibleForTesting;
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
 * Re-executes blocks that are already in this node's own block store -- they are not fetched from
 * peers -- and checks that each one still produces the state root its header claims. That makes it
 * a way to check that a change did not break consensus over past history.
 *
 * Exits non-zero if any block fails, so it can be used as a pass/fail check.
 */
@CommandLine.Command(name = "execute-blocks", mixinStandardHelpOptions = true, version = "execute-blocks 1.0",
        description = "Executes blocks for a specified block range")
public class ExecuteBlocks extends PicoCliToolRskContextAware {
    @CommandLine.Option(names = {"-fb", "--fromBlock"}, description = "From block number", required = true)
    private Long fromBlockNumber;

    @CommandLine.Option(names = {"-tb", "--toBlock"}, description = "To block number", required = true)
    private Long toBlockNumber;

    @CommandLine.Option(names = {"-ss", "--saveState"}, arity = "1", defaultValue = "false",
            description = "Whether to persist the state produced by each block (default: ${DEFAULT-VALUE}). "
                    + "Verifying does not need it: the state roots are still checked, because executing a "
                    + "block only needs its parent's state, which is already in the store. Pass "
                    + "--saveState=true to write the produced state back to the database.")
    private boolean saveState;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();
        StateRootHandler stateRootHandler = ctx.getStateRootHandler();

        boolean succeeded = executeBlocks(blockExecutor, blockStore, trieStore, stateRootHandler);

        return succeeded ? 0 : 1;
    }

    /**
     * @return true if every block in the range executed and matched its header's state root.
     */
    private boolean executeBlocks(BlockExecutor blockExecutor, BlockStore blockStore, TrieStore trieStore,
                                  StateRootHandler stateRootHandler) {
        printInfo("Executing blocks {} to {} (saveState={})", fromBlockNumber, toBlockNumber, saveState);

        boolean succeeded = true;

        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block = blockStore.getChainBlockByNumber(n);

            if (block == null) {
                printError("Block number {} is not in the block store", n);
                succeeded = false;
                break;
            }

            Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());

            if (parent == null) {
                printError("Parent of block number {} is not in the block store", n);
                succeeded = false;
                break;
            }

            BlockResult blockResult = blockExecutor.execute(null, 0, block, parent.getHeader(), false, false, saveState);

            String rejection = rejectionReasonOf(blockResult);
            if (rejection != null) {
                // A rejected block carries no final state, so the state root comparison below would
                // fail with a NullPointerException and hide the reason it was rejected.
                printError("Block number {} [{}] was rejected: {}", n, block.getPrintableHash(), rejection);
                succeeded = false;
                break;
            }

            Keccak256 stateRootHash = stateRootHandler.translate(block.getHeader());
            if (!Arrays.equals(blockResult.getFinalState().getHash().getBytes(), stateRootHash.getBytes())) {
                printError("Invalid state root block number " + n);
                succeeded = false;
                break;
            }
        }

        trieStore.flush();
        blockStore.flush();

        if (succeeded) {
            printInfo("All blocks from {} to {} executed and matched their state roots", fromBlockNumber, toBlockNumber);
        }

        return succeeded;
    }

    /**
     * Describes why execution produced no usable result, or null if it produced one.
     */
    @VisibleForTesting
    static String rejectionReasonOf(BlockResult blockResult) {
        if (blockResult == BlockResult.SUPPLY_VIOLATION_BLOCK_RESULT) {
            return "it creates native currency out of nothing (supply conservation). "
                    + "See the supplyconservation log for the amount and the accounts that gained";
        }

        if (blockResult == BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT) {
            return "execution was interrupted by an invalid transaction";
        }

        if (blockResult == null || blockResult.getFinalState() == null) {
            return "execution produced no final state";
        }

        return null;
    }
}
