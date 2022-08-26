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
import co.rsk.db.MapDBBlocksIndex;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

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
        TrieStoreImpl.forcePerformanceLogging = true;
        DataSourceWithCache.forcePerformanceLogging = true;
        DataSourceWithCache.forcePerformanceCacheLogging = true;
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    public static BlockStore getBlockStoreFromPath(String blocksDir, BlockFactory blockFactory) {
        File blockIndexDirectory = new File(blocksDir);
        File dbFile = new File(blockIndexDirectory, "index");
        if (!blockIndexDirectory.exists()) {
            throw new IllegalArgumentException(String.format(
                    "Unable to create blocks directory in read-only mode: %s", blockIndexDirectory
            ));
        }

        DB indexDB;
        indexDB = DBMaker.fileDB(dbFile).readOnly().make();
        DbKind currentDbKind = DbKind.LEVEL_DB; // fixed now
        KeyValueDataSource blocksDB = KeyValueDataSourceUtils.makeDataSource(Paths.get(blocksDir),
                currentDbKind, true);

        return new IndexedBlockStore(blockFactory, blocksDB,
                new MapDBBlocksIndex(indexDB, true));
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception {
        BlockExecutor blockExecutor = ctx.getBlockExecutor();

        TrieStore trieStore = ctx.getTrieStore();
        StateRootHandler stateRootHandler = ctx.getStateRootHandler();

        executeBlocks(args,ctx, blockExecutor,  trieStore, stateRootHandler);
    }

    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    void ConsoleLogList(String msg,List<String> list) {
        consoleLog(msg);
        for(int i=0;i<list.size();i++) {
            System.out.println(" "+list.get(i));
        }
    }

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

    private void executeBlocks(String[] args,     RskContext ctx,BlockExecutor blockExecutor,  TrieStore trieStore,
                               StateRootHandler stateRootHandler) {
        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber;
        if (args[1].charAt(0)=='+') {
            toBlockNumber =fromBlockNumber+Long.parseLong(args[1]);
        }  else
            toBlockNumber =Long.parseLong(args[1]);
        BlockStore blockStore;
        if (args[2].equals("(db)")) {
            blockStore = ctx.getBlockStore();
        } else {
            blockStore = getBlockStoreFromPath(args[2],ctx.getBlockFactory());
        }

        printArgs(args);

        long start = System.currentTimeMillis();
        long sig_start = System.currentTimeMillis();
        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            consoleLog("executing :"+n);
            Block block = blockStore.getChainBlockByNumber(n);
            SignatureCache sc = ctx.getBlockTxSignatureCache();
            Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());
            for (Transaction tx : block.getTransactionsList()) {
                tx.getSender(sc);
                sc.storeSender(tx);
            }
        }
        long sig_end = System.currentTimeMillis();
        consoleLog("sigcache fill time: "+(sig_end-sig_start)+ " msec");

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
                printError(" execution result: "+blockResult.getFinalState().getHash().toHexString());
                printError(" stored state: "+stateRootHash.toHexString());
                break;
            }
        }
        long stop = System.currentTimeMillis();
        consoleLog("Total time: "+(stop-start)/1000+" secs");
        // Save cache even if we have opened our blockchain is readonly mode
        //trieStore.saveCache();

        trieStore.flush();
        blockStore.flush();
        List<String> stats =ctx.getTrieStore().getStats();
        ConsoleLogList("Stats:",stats);

    }
}
