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
import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.test.World;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;
import java.util.List;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class BlockBuilder {
    private BlockChainImpl blockChain;
    private final BlockGenerator blockGenerator;
    private Block parent;
    private long difficulty;
    private List<Transaction> txs;
    private List<BlockHeader> uncles;
    private BigInteger minGasPrice;
    private byte[] gasLimit;

    public BlockBuilder() {
        this.blockGenerator = new BlockGenerator();
    }

    public BlockBuilder(World world) {
        this(world.getBlockChain(), new BlockGenerator());
    }

    public BlockBuilder(BlockChainImpl blockChain, BlockGenerator blockGenerator) {
        this.blockChain = blockChain;
        this.blockGenerator = blockGenerator;
        // sane defaults
        this.parent(blockChain.getBestBlock());
    }

    public BlockBuilder parent(Block parent) {
        this.parent = parent;
        this.gasLimit = parent.getGasLimit();
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

    /**
     * This has to be called after .parent() in order to have any effect
     */
    public BlockBuilder gasLimit(BigInteger gasLimit) {
        this.gasLimit = BigIntegers.asUnsignedByteArray(gasLimit);
        return this;
    }

    public Block build() {
        Block block = blockGenerator.createChildBlock(parent, txs, uncles, difficulty, this.minGasPrice, gasLimit);

        if (blockChain != null) {
            BlockExecutor executor = new BlockExecutor(new RskSystemProperties(), blockChain.getRepository(), blockChain.getReceiptStore(), blockChain.getBlockStore(), blockChain.getListener());
            executor.executeAndFill(block, parent);
        }

        return block;
    }
}
