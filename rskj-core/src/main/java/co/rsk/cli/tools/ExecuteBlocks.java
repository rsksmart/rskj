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
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

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
        
        execute(args, blockExecutor, blockStore, trieStore);
    }

    public static void execute(String[] args, BlockExecutor blockExecutor, BlockStore blockStore, TrieStore trieStore) {
        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber = Long.parseLong(args[1]);
        long started = System.currentTimeMillis();
        long lastTime = started;
        int printInterval = 10;
        int bcount = 0;
        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block = blockStore.getChainBlockByNumber(n);
            //System.out.println("Block: "+n+" ("+(n*100/toBlockNumber)+"%)");
            Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());
            bcount++;
            if (bcount>=printInterval) {
                System.out.println("Block: "+n+" ("+(n*100/toBlockNumber)+"%)");

            }
            if (!blockExecutor.executeAndValidate(block,parent.getHeader())) {
                System.out.println("out of consensus at block: "+n);
                break;
            }
            if (bcount >=printInterval) {
                long currentTime = System.currentTimeMillis();
                long deltaTime = (currentTime - started);
                long deltaBlock = (n-fromBlockNumber);
                System.out.println("Time[s]: " + (deltaTime / 1000));
                if (currentTime>started)
                    System.out.println("total blocks/sec: " +deltaBlock*1000/(currentTime-started));
                if (currentTime>lastTime) {
                    System.out.println("current blocks/sec: " +bcount*1000/(currentTime-lastTime));
                    lastTime = currentTime;
                }
                bcount =0;
            }
        }

        trieStore.flush();
        //blockStore.flush();
    }
}
