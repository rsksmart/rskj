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

package co.rsk.test.builders;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.test.World;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;

import java.math.BigInteger;
import java.util.List;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class BlockBuilder {
    private BlockChainImpl blockChain;
    private Block parent;
    private long difficulty;
    private List<Transaction> txs;
    private List<BlockHeader> uncles;
    private BigInteger minGasPrice;

    public BlockBuilder() { }

    public BlockBuilder(World world) {
        this(world.getBlockChain());
    }

    public BlockBuilder(BlockChainImpl blockChain) {
        this.blockChain = blockChain;
    }

    public BlockBuilder parent(Block parent) {
        this.parent = parent;
        return this;
    }

    public BlockBuilder difficulty(long difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public BlockBuilder transactions(List<Transaction> txs) {
        this.txs = txs;
        return this;
    }

    public BlockBuilder uncles(List<BlockHeader> uncles) {
        this.uncles = uncles;
        return this;
    }

    public BlockBuilder minGasPrice(BigInteger minGasPrice) {
        this.minGasPrice = minGasPrice;
        return this;
    }

    public Block build() {
        Block block = BlockGenerator.createChildBlock(parent, txs, uncles, difficulty, this.minGasPrice);

        if (blockChain != null) {
            BlockExecutor executor = new BlockExecutor(blockChain.getRepository(), blockChain, blockChain.getBlockStore(), blockChain.getListener());
            executor.executeAndFill(block, parent);
        }

        return block;
    }
}
