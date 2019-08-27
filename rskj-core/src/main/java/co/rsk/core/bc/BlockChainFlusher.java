/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
package co.rsk.core.bc;

import co.rsk.trie.TrieStore;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flushes the repository and block store after every flushNumberOfBlocks invocations
 */
public class BlockChainFlusher {
    private static final Logger logger = LoggerFactory.getLogger(BlockChainFlusher.class);

    private final boolean flushEnabled;
    private final int flushNumberOfBlocks;
    private final TrieStore trieStore;
    private final BlockStore blockStore;

    private int nFlush = 1;

    public BlockChainFlusher(
            boolean flushEnabled,
            int flushNumberOfBlocks,
            TrieStore trieStore,
            BlockStore blockStore) {
        this.flushEnabled = flushEnabled;
        this.flushNumberOfBlocks = flushNumberOfBlocks;
        this.trieStore = trieStore;
        this.blockStore = blockStore;
    }

    public void flush() {
        if (flushEnabled && nFlush == 0) {
            long saveTime = System.nanoTime();
            trieStore.flush();
            long totalTime = System.nanoTime() - saveTime;
            logger.trace("repository flush: [{}]nano", totalTime);
            saveTime = System.nanoTime();
            blockStore.flush();
            totalTime = System.nanoTime() - saveTime;
            logger.trace("blockstore flush: [{}]nano", totalTime);
        }

        nFlush++;
        nFlush = nFlush % flushNumberOfBlocks;
    }
}
