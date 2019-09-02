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

package co.rsk.db;

import co.rsk.config.InternalService;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;

import java.util.List;

/**
 * Handles garbage collection and flush behavior based on RSKIP64 rules
 */
public class GarbageCollector implements InternalService {
    private final int blocksPerEpoch;
    private final int blocksPerFlush;
    private final CompositeEthereumListener emitter;
    private final RepositoryLocator repositoryLocator;
    private final TrieStore trieStore;
    private final BlockStore blockStore;

    private final OnBestBlockListener listener = new OnBestBlockListener();

    public GarbageCollector(
            int blocksPerEpoch,
            int blocksPerFlush,
            CompositeEthereumListener emitter,
            RepositoryLocator repositoryLocator,
            TrieStore trieStore,
            BlockStore blockStore) {
        this.blocksPerEpoch = blocksPerEpoch;
        this.blocksPerFlush = blocksPerFlush;
        this.emitter = emitter;
        this.repositoryLocator = repositoryLocator;
        this.trieStore = trieStore;
        this.blockStore = blockStore;
    }

    @Override
    public void start() {
        emitter.addListener(listener);
    }

    @Override
    public void stop() {
        emitter.removeListener(listener);
    }

    private void flush() {
        trieStore.flush();
        blockStore.flush();
    }

    private void collect(Trie lastFrontierTrie, long lastEpoch) {
        trieStore.collect(lastFrontierTrie, lastEpoch);
    }

    private class OnBestBlockListener extends EthereumListenerAdapter {
        @Override
        public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
            if (isFrontierBlock(block.getNumber())) {
                long previousFrontierBlock = previousFrontierBlock(block.getNumber());
                BlockHeader lastFrontierHeader = blockStore.getChainBlockByNumber(previousFrontierBlock).getHeader();
                Trie lastFrontierTrie = repositoryLocator.trieAt(lastFrontierHeader);
                collect(lastFrontierTrie, previousFrontierBlock / blocksPerEpoch);
            } else if (isFlushBlock(block.getNumber())) {
                flush();
            }
        }

        private boolean isFrontierBlock(long blockNumber) {
            return blockNumber != 0 && blockNumber % blocksPerEpoch == 0;
        }

        private long previousFrontierBlock(long frontierBlockNumber) {
            return frontierBlockNumber - blocksPerEpoch;
        }

        private boolean isFlushBlock(long blockNumber) {
            return blockNumber != 0 && blockNumber % blocksPerFlush == 0;
        }
    }
}

