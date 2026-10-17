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
import co.rsk.cli.PicoCliToolRskContextAware;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.crypto.Keccak256;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.TrieStore;
import co.rsk.trie.segbuild.SegbuildTrieStore;
import co.rsk.util.NodeStopper;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * The entry point for execute blocks CLI tool
 * This is an experimental/unsupported tool
 *
 * <p>Re-executes blocks that are already in the node's own databases -- they are not fetched from
 * peers -- and checks that each one still produces the state root its header claims. That makes it
 * a way to check that a change did not break consensus over past history.
 *
 * <p><b>It does not write to any database unless asked to.</b> Replay is a read activity: writing
 * while checking history would alter the very thing being checked, and the databases involved are
 * often archival artifacts or shared snapshots. Every store is opened read-only unless
 * {@code --allowWrites} is given, and with it off any write attempt fails loudly rather than
 * silently succeeding.
 *
 * <p>The trie backend is selectable with {@code --trieStore}, so the same replay can be run against
 * different stores and their performance compared.
 */
@CommandLine.Command(name = "execute-blocks", mixinStandardHelpOptions = true, version = "execute-blocks 1.0",
        description = "Executes blocks for a specified block range")
public class ExecuteBlocks extends PicoCliToolRskContextAware {

    @CommandLine.Option(names = {"-fb", "--fromBlock"}, description = "From block number", required = true)
    private Long fromBlockNumber;

    @CommandLine.Option(names = {"-tb", "--toBlock"}, description = "To block number", required = true)
    private Long toBlockNumber;

    @CommandLine.Option(names = {"-ts", "--trieStore"}, defaultValue = "UNITRIE",
            description = "Which trie store to read state from: ${COMPLETION-CANDIDATES} "
                    + "(default: ${DEFAULT-VALUE}). UNITRIE reads the node's own unitrie under the "
                    + "database directory. SEGBUILD reads a segbuild segment store, which needs --segbuildDir.")
    private ReplayRskContext.TrieBackend trieBackend;

    @CommandLine.Option(names = {"-sd", "--segbuildDir"},
            description = "Root directory of the segbuild chunk set. Required for --trieStore=SEGBUILD.")
    private String segbuildDir;

    @CommandLine.Option(names = {"-sf", "--segbuildFallback"}, defaultValue = "false",
            description = "When a segbuild chunk does not hold a node, read it from the node's own "
                    + "unitrie and count it, instead of failing (default: ${DEFAULT-VALUE}). A chunk holds "
                    + "what its producer read; another client may touch state the producer did not. "
                    + "Timings taken with this on are not representative of segbuild alone -- the "
                    + "reported fallback count says how far the chunk fell short.")
    private boolean segbuildFallback;

    @CommandLine.Option(names = {"-aw", "--allowWrites"}, defaultValue = "false",
            description = "Permit writing to the databases (default: ${DEFAULT-VALUE}). Without this "
                    + "every store is opened read-only and no database is modified. Required before "
                    + "--saveState can be used.")
    private boolean allowWrites;

    @CommandLine.Option(names = {"-ss", "--saveState"}, arity = "1", defaultValue = "false",
            description = "Whether to persist the state produced by each block (default: ${DEFAULT-VALUE}). "
                    + "Verifying does not need it: the state roots are still checked, because executing a "
                    + "block only needs its parent's state, which is already in the store. Requires --allowWrites.")
    private boolean saveState;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    /**
     * Builds the context from the options rather than taking the default one, because whether the
     * databases may be written to, and which trie backend to open, must be decided before any store
     * is opened.
     */
    @Override
    public void execute(String[] args, NodeStopper nodeStopper) {
        preParseOptions(args);
        execute(args, () -> new ReplayRskContext(args, trieBackend, segbuildRoot(), allowWrites, segbuildFallback),
                nodeStopper);
    }

    private void preParseOptions(String[] args) {
        try {
            new CommandLine(this).setUnmatchedArgumentsAllowed(true).parseArgs(args);
        } catch (RuntimeException e) {
            // Leave the fields at their defaults and let the normal parse report the problem.
        }
    }

    @Nullable
    private Path segbuildRoot() {
        return segbuildDir == null ? null : Paths.get(segbuildDir);
    }

    @Override
    public Integer call() throws IOException {
        if (saveState && !allowWrites) {
            printError("--saveState writes to the database and needs --allowWrites; refusing to run");
            return 1;
        }

        if (trieBackend == ReplayRskContext.TrieBackend.SEGBUILD && segbuildDir == null) {
            printError("--trieStore=SEGBUILD needs --segbuildDir to say where the chunk set is");
            return 1;
        }

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
        SegbuildTrieStore segbuildStore = trieStore instanceof SegbuildTrieStore
                ? (SegbuildTrieStore) trieStore
                : null;

        printInfo("Executing blocks {} to {} (trieStore={}, allowWrites={}, saveState={})",
                fromBlockNumber, toBlockNumber, trieBackend, allowWrites, saveState);

        boolean succeeded = true;
        long executed = 0;

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

            if (segbuildStore != null) {
                // One chunk at a time is the intended access pattern: a chunk is self-contained
                // for its own range, including the state root of the block before its first.
                segbuildStore.positionAt(n);
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
                // A node the store could not serve is reported as a mismatch, because execution
                // treats the failed read as an invalid transaction. Say what really happened.
                if (segbuildStore != null && segbuildStore.getLastMiss() != null) {
                    printError("Block number {} could not be executed: {}", n,
                            segbuildStore.getLastMiss().getMessage());
                } else {
                    printError("Invalid state root block number " + n);
                }
                succeeded = false;
                break;
            }

            executed++;
        }

        if (allowWrites) {
            trieStore.flush();
            blockStore.flush();
        }

        if (succeeded) {
            printInfo("Executed {} blocks from {} to {}; every state root matched",
                    executed, fromBlockNumber, toBlockNumber);
            if (segbuildStore != null) {
                printInfo("Segbuild chunks opened: {}", segbuildStore.getChunkOpenCount());
                if (segbuildStore.hasFallback()) {
                    printInfo("Segbuild reads served by the fallback: {} (timings are not "
                            + "representative of segbuild alone)", segbuildStore.getFallbackHitCount());
                }
            }
        }

        return succeeded;
    }

    /**
     * Describes why execution produced no usable result, or null if it produced one.
     */
    @VisibleForTesting
    static String rejectionReasonOf(BlockResult blockResult) {
        if (blockResult == BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT) {
            return "execution was interrupted by an invalid transaction";
        }

        if (blockResult == null || blockResult.getFinalState() == null) {
            return "execution produced no final state";
        }

        return null;
    }

    @VisibleForTesting
    void setContext(RskContext context) {
        this.ctx = context;
    }
}
