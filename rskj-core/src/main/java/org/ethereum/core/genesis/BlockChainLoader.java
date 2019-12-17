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

package org.ethereum.core.genesis;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.StateRootHandler;
import co.rsk.validators.BlockValidator;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.Genesis;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.EthereumListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * Created by mario on 13/01/17.
 */
public class BlockChainLoader {

    private static final Logger logger = LoggerFactory.getLogger("general");

    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final TransactionPool transactionPool;
    private final EthereumListener listener;
    private final BlockValidator blockValidator;
    private final BlockExecutor blockExecutor;
    private final Genesis genesis;
    private final StateRootHandler stateRootHandler;

    public BlockChainLoader(
            BlockStore blockStore,
            ReceiptStore receiptStore,
            TransactionPool transactionPool,
            EthereumListener listener,
            BlockValidator blockValidator,
            BlockExecutor blockExecutor,
            Genesis genesis,
            StateRootHandler stateRootHandler) {
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.transactionPool = transactionPool;
        this.listener = listener;
        this.blockValidator = blockValidator;
        this.blockExecutor = blockExecutor;
        this.genesis = genesis;
        this.stateRootHandler = stateRootHandler;
    }

    public BlockChainImpl loadBlockchain() {
        BlockDifficulty totalDifficulty;
        Block bestBlock = blockStore.getBestBlock();
        if (bestBlock == null) {
//            logger.info("DB is empty - adding Genesis");

            bestBlock = genesis;
            totalDifficulty = genesis.getCumulativeDifficulty();

            listener.onBlock(genesis, new ArrayList<>());

//            logger.info("Genesis block loaded");
        } else {
            totalDifficulty = blockStore.getTotalDifficultyForHash(bestBlock.getHash().getBytes());

            logger.info("*** Loaded up to block [{}] totalDifficulty [{}] with stateRoot [{}]",
                    bestBlock.getNumber(),
                    totalDifficulty,
                    Hex.toHexString(bestBlock.getStateRoot()));
        }

        BlockChainImpl blockchain = new BlockChainImpl(
                blockStore,
                receiptStore,
                transactionPool,
                listener,
                blockValidator,
                blockExecutor,
                stateRootHandler
        );
        blockchain.setStatus(bestBlock, totalDifficulty);

        return blockchain;
    }
}
