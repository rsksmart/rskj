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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception {
        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();
        StateRootHandler stateRootHandler = ctx.getStateRootHandler();

        executeBlocks(args, blockExecutor, blockStore, trieStore, stateRootHandler);
    }

    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    void consoleLog(String s) {
        LocalDateTime now = LocalDateTime.now();
        System.out.println(dtf.format(now)+": "+s);
    }
    private void printArgs(String[] args) {
        System.out.print("args: ");
        for(int i=0;i<args.length;i++) {
            System.out.print(args[i]+" ");
        }
        System.out.println();
    }

    private void executeBlocks(String[] args, BlockExecutor blockExecutor, BlockStore blockStore, TrieStore trieStore,
                               StateRootHandler stateRootHandler) {
        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber = Long.parseLong(args[1]);
        printArgs(args);
        long start = System.currentTimeMillis();
        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            consoleLog("executing :"+n);
            long estart = System.currentTimeMillis();
            Block block = blockStore.getChainBlockByNumber(n);
            Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());

            BlockResult blockResult = blockExecutor.execute(block, parent.getHeader(), false, false);
            long eend = System.currentTimeMillis();
            consoleLog(" time: "+(eend-estart)+ " msec");
            Keccak256 stateRootHash = stateRootHandler.translate(block.getHeader());
            if (!Arrays.equals(blockResult.getFinalState().getHash().getBytes(), stateRootHash.getBytes())) {
                printError("Invalid state root block number " + n);
                break;
            }
        }
        long stop = System.currentTimeMillis();
        consoleLog("Total time: "+(stop-start)/1000+" secs");
        trieStore.flush();
        blockStore.flush();
    }
}
