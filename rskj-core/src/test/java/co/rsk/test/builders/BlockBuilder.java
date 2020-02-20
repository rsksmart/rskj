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
import co.rsk.config.TestSystemProperties;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.trie.TrieConverter;
import co.rsk.trie.TrieStore;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class BlockBuilder {
    private final Blockchain blockChain;
    private final BlockGenerator blockGenerator;
    private TrieStore trieStore;
    private Block parent;
    private long difficulty;
    private List<Transaction> txs;
    private List<BlockHeader> uncles;
    private BigInteger minGasPrice;
    private byte[] gasLimit;
    private final BridgeSupportFactory bridgeSupportFactory;
    private BlockStore blockStore;

    public BlockBuilder(Blockchain blockChain, BridgeSupportFactory bridgeSupportFactory, BlockStore blockStore) {
        this.blockChain = blockChain;
        this.blockGenerator = new BlockGenerator();
        this.bridgeSupportFactory = bridgeSupportFactory;
        this.blockStore = blockStore;
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

    public BlockBuilder trieStore(TrieStore store) {
        this.trieStore = store;
        return this;
    }

    public Block build() {
        Block block = blockGenerator.createChildBlock(parent, txs, uncles, difficulty, this.minGasPrice, gasLimit);

        if (blockChain != null) {
            final TestSystemProperties config = new TestSystemProperties();
            StateRootHandler stateRootHandler = new StateRootHandler(config.getActivationConfig(), new TrieConverter(), new HashMapDB(), new HashMap<>());
            BlockExecutor executor = new BlockExecutor(
                    config.getActivationConfig(),
                    new RepositoryLocator(trieStore, stateRootHandler),
                    stateRootHandler,
                    new TransactionExecutorFactory(
                            config,
                            blockStore,
                            null,
                            new BlockFactory(config.getActivationConfig()),
                            new ProgramInvokeFactoryImpl(),
                            new PrecompiledContracts(config, bridgeSupportFactory),
                            new BlockTxSignatureCache(new ReceivedTxSignatureCache())
                    )
            );
            executor.executeAndFill(block, parent.getHeader());
        }

        return block;
    }
}
