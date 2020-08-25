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

import co.rsk.config.InternalService;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Flushes the repository and block store after every flushNumberOfBlocks invocations
 */
public class BlockChainFlusher implements InternalService {
    private static final Logger logger = LoggerFactory.getLogger(BlockChainFlusher.class);

    private static final Profiler profiler = ProfilerFactory.getInstance();

    private final int flushNumberOfBlocks;
    private final CompositeEthereumListener emitter;
    private final TrieStore trieStore;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    private final OnBestBlockListener listener = new OnBestBlockListener();

    private int nFlush = 1;

    public BlockChainFlusher(
            int flushNumberOfBlocks,
            CompositeEthereumListener emitter,
            TrieStore trieStore,
            BlockStore blockStore, ReceiptStore receiptStore) {
        this.flushNumberOfBlocks = flushNumberOfBlocks;
        this.emitter = emitter;
        this.trieStore = trieStore;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
    }

    @Override
    public void start() {
        emitter.addListener(listener);
    }

    @Override
    public void stop() {
        emitter.removeListener(listener);
        flushAll();
    }

    private void flush() {
        if (nFlush == 0) {
            flushAll();
        }

        nFlush++;
        nFlush = nFlush % flushNumberOfBlocks;
    }

    private void flushAll() {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BLOCKCHAIN_FLUSH);

        long saveTime = System.nanoTime();
        trieStore.flush();
        long totalTime = System.nanoTime() - saveTime;
        logger.trace("repository flush: [{}]nano", totalTime);
        saveTime = System.nanoTime();
        blockStore.flush();
        totalTime = System.nanoTime() - saveTime;
        logger.trace("blockstore flush: [{}]nano", totalTime);
        saveTime = System.nanoTime();
        receiptStore.flush();
        totalTime = System.nanoTime() - saveTime;
        logger.trace("receiptstore flush: [{}]nano", totalTime);

        profiler.stop(metric);
    }

    private class OnBestBlockListener extends EthereumListenerAdapter {
        @Override
        public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
            flush();
        }
    }
}
