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

import co.rsk.Flusher;
import co.rsk.config.InternalService;
import co.rsk.db.StateRootsStore;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.trie.TrieStore;
import co.rsk.util.FormatUtils;
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
public class BlockChainFlusher implements InternalService, Flusher {
    private static final Logger logger = LoggerFactory.getLogger(BlockChainFlusher.class);

    private static final Profiler profiler = ProfilerFactory.getInstance();

    private final int flushNumberOfBlocks;
    private final CompositeEthereumListener emitter;
    private final TrieStore trieStore;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final BlocksBloomStore blocksBloomStore;
    private final StateRootsStore stateRootsStore;

    private final OnBestBlockListener listener = new OnBestBlockListener();

    private int nFlush = 1;

    public BlockChainFlusher(
            int flushNumberOfBlocks,
            CompositeEthereumListener emitter,
            TrieStore trieStore,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            BlocksBloomStore blocksBloomStore,
            StateRootsStore stateRootsStore) {
        this.flushNumberOfBlocks = flushNumberOfBlocks;
        this.emitter = emitter;
        this.trieStore = trieStore;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.blocksBloomStore = blocksBloomStore;
        this.stateRootsStore = stateRootsStore;
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

    public synchronized void forceFlush() {
        flushAll();
        nFlush =1; // postpone
    }
    private synchronized void flush() {
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

        if (logger.isTraceEnabled()) {
            logger.trace("repository flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
        }

        saveTime = System.nanoTime();
        stateRootsStore.flush();
        totalTime = System.nanoTime() - saveTime;

        if (logger.isTraceEnabled()) {
            logger.trace("stateRootsStore flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
        }

        saveTime = System.nanoTime();
        receiptStore.flush();
        totalTime = System.nanoTime() - saveTime;

        if (logger.isTraceEnabled()) {
            logger.trace("receiptstore flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
        }

        saveTime = System.nanoTime();
        blockStore.flush();
        totalTime = System.nanoTime() - saveTime;

        if (logger.isTraceEnabled()) {
            logger.trace("blockstore flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
        }

        saveTime = System.nanoTime();
        blocksBloomStore.flush();
        totalTime = System.nanoTime() - saveTime;

        if (logger.isTraceEnabled()) {
            logger.trace("bloomBlocksStore flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
        }

        profiler.stop(metric);
    }

    private class OnBestBlockListener extends EthereumListenerAdapter {
        @Override
        public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
            flush();
        }
    }
}
