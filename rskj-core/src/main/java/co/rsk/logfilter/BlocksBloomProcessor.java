/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.logfilter;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Bloom;
import org.ethereum.db.BlockStore;

/**
 * Process bloom filters from blocks
 *
 * It collects bloom filter from flocks with enough confirmations
 * grouping then in a BlocksBloom instance
 *
 * When a new height should be processed, the
 * BlocksBloom instance is feeded with the block blooms
 * up to that height
 *
 * When the BlocksBloom instance is filled, it is saved into the BlocksBloomStore
 * and a new instance starts to be processed
 *
 * Created by ajlopez on 29/09/2020.
 */
public class BlocksBloomProcessor {
    private final BlocksBloomStore blocksBloomStore;
    private final BlockStore blockStore;

    private BlocksBloom blocksBloomInProcess = null;

    public BlocksBloomProcessor(BlocksBloomStore blocksBloomStore, BlockStore blockStore) {
        this.blocksBloomStore = blocksBloomStore;
        this.blockStore = blockStore;
    }

    @VisibleForTesting
    public BlocksBloom getBlocksBloomInProcess() {
        return this.blocksBloomInProcess;
    }

    /**
     * Receives the new height to process.
     * Processes the block blooms up to that height minus the number of needed confirmations
     *
     * @param newBlockNumber    the new height to process
     */
    public synchronized void processNewBlockNumber(long newBlockNumber) {
        if (newBlockNumber < this.blocksBloomStore.getNoConfirmations()) {
            return;
        }

        long blockNumber = newBlockNumber - this.blocksBloomStore.getNoConfirmations();

        addBlocksUpToNumber(blockNumber);
    }

    /**
     * Reads and collect block blooms up the the provided block number
     *
     * @param blockNumber top block number to process
     */
    private void addBlocksUpToNumber(long blockNumber) {
        if (this.blocksBloomStore.hasBlockNumber(blockNumber)) {
            return;
        }

        long fromBlock;

        if (this.blocksBloomInProcess == null) {
            this.blocksBloomInProcess = new BlocksBloom();
            fromBlock = this.blocksBloomStore.
                    firstNumberInRange(blockNumber);
        }
        else {
            fromBlock = this.blocksBloomInProcess.toBlock() + 1;
        }

        if (this.blocksBloomInProcess.hasBlockBloom(blockNumber)) {
            return;
        }

        for (long nb = fromBlock; nb <= blockNumber; nb++) {
            this.addBlock(nb);
        }
    }

    /**
     * Reads and collect the bloom from the block that corresponds to the provided block number
     *
     * If the BlocksBloom instance is fulfilled, it is saved into the store
     * and a new instance will be created at the process of the next block number
     *
     * @param blockNumber block number to process
     */
    private void addBlock(long blockNumber) {
        Bloom bloom;

        if (blockNumber > 0) {
            bloom = new Bloom(this.blockStore.getChainBlockByNumber(blockNumber).getLogBloom());
        }
        else {
            bloom = new Bloom();
        }

        if (this.blocksBloomInProcess == null) {
            this.blocksBloomInProcess = new BlocksBloom();
        }

        this.blocksBloomInProcess.addBlockBloom(blockNumber, bloom);

        if (blockNumber == this.blocksBloomStore.lastNumberInRange(blockNumber)) {
            this.blocksBloomStore.addBlocksBloom(this.blocksBloomInProcess);
            this.blocksBloomInProcess = null;
        }
    }
}
