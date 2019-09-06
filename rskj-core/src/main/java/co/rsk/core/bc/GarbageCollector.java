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
import co.rsk.db.RepositoryLocator;
import co.rsk.trie.MultiTrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;

import java.util.List;

public class GarbageCollector implements InternalService {
    private final CompositeEthereumListener emitter;
    private final MultiTrieStore multiTrieStore;
    private final BlockStore blockStore;
    private final RepositoryLocator repositoryLocator;
    private final EthereumListener garbageCollectorInvoker;

    public GarbageCollector(CompositeEthereumListener emitter,
                            int blocksPerEpoch,
                            int numberOfEpochs,
                            MultiTrieStore multiTrieStore,
                            BlockStore blockStore,
                            RepositoryLocator repositoryLocator) {
        this.emitter = emitter;
        this.multiTrieStore = multiTrieStore;
        this.blockStore = blockStore;
        this.repositoryLocator = repositoryLocator;
        this.garbageCollectorInvoker = new GarbageCollectorInvoker(blocksPerEpoch, numberOfEpochs);
    }

    @Override
    public void start() {
        emitter.addListener(garbageCollectorInvoker);
    }

    @Override
    public void stop() {
        emitter.removeListener(garbageCollectorInvoker);
    }

    private void collect(long untilBlock) {
        BlockHeader untilHeader = blockStore.getChainBlockByNumber(untilBlock).getHeader();
        multiTrieStore.collect(repositoryLocator.snapshotAt(untilHeader).getRoot());
    }

    private class GarbageCollectorInvoker extends EthereumListenerAdapter {
        private final int blocksPerEpoch;
        private final int numberOfEpochs;

        public GarbageCollectorInvoker(int blocksPerEpoch, int numberOfEpochs) {
            this.blocksPerEpoch = blocksPerEpoch;
            this.numberOfEpochs = numberOfEpochs;
        }

        /**
         * It'll collect all states older than <code>blocksPerEpoch * numberOfEpochs - 1</code> from
         * the store (if there are enough blocks)
         */
        @Override
        public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
            int statesToKeep = blocksPerEpoch * numberOfEpochs - 1;
            long currentBlockNumber = block.getNumber();
            if(currentBlockNumber % blocksPerEpoch == 0 && currentBlockNumber >= statesToKeep) {
                collect(currentBlockNumber - statesToKeep);
            }
        }
    }
}
