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

import co.rsk.config.InternalService;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by ajlopez on 01/10/2020.
 */
public class BlocksBloomService implements InternalService {
    private static final Logger logger = LoggerFactory.getLogger("blooms");

    private final CompositeEthereumListener emitter;
    private final BlocksBloomProcessor blocksBloomProcessor;

    private final BlocksBloomService.OnBlockListener listener = new BlocksBloomService.OnBlockListener();

    public BlocksBloomService(CompositeEthereumListener emitter, BlocksBloomStore blocksBloomStore, BlockStore blockStore) {
        this.emitter = emitter;
        this.blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, blockStore);
    }

    @Override
    public void start() {
        logger.info("blocks bloom service started");

        emitter.addListener(listener);
    }

    @Override
    public void stop() {
        logger.info("blocks bloom service stopped");

        emitter.removeListener(listener);
    }

    public void processNewBlock(long blockNumber) {
        this.blocksBloomProcessor.processNewBlockNumber(blockNumber);
    }

    private class OnBlockListener extends EthereumListenerAdapter {
        @Override
        public void onBlock(Block block, List<TransactionReceipt> receipts) {
            processNewBlock(block.getNumber());
        }
    }
}
