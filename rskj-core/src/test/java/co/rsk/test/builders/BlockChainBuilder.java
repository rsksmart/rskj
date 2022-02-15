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
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.*;
import co.rsk.db.*;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.*;
import org.ethereum.core.genesis.BlockChainLoader;
import org.ethereum.core.genesis.GenesisLoaderImpl;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.*;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.rpc.Web3;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class BlockChainBuilder {
    private boolean testing;
    private List<Block> blocks;

    private Repository repository;
    private BlockStore blockStore;
    private Genesis genesis;
    private ReceiptStore receiptStore;
    private RskSystemProperties config;
    private EthereumListener listener;
    private StateRootHandler stateRootHandler;
    private BridgeSupportFactory bridgeSupportFactory;
    private TransactionPoolImpl transactionPool;
    private RepositoryLocator repositoryLocator;
    private TrieStore trieStore;

    public BlockChainBuilder setTesting(boolean value) {
        this.testing = value;
        return this;
    }

    public BlockChainBuilder setBlocks(List<Block> blocks) {
        this.blocks = blocks;
        return this;
    }

    public BlockChainBuilder setTrieStore(TrieStore store) {
        this.trieStore = store;
        return this;
    }

    public BlockChainBuilder setBlockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
        return this;
    }

    /** @param genesis a non-finalized genesis info */
    public BlockChainBuilder setGenesis(Genesis genesis) {
        this.genesis = genesis;
        return this;
    }

    public BlockChainBuilder setConfig(RskSystemProperties config) {
        this.config = config;
        return this;
    }

    public ReceiptStore getReceiptStore() {
        return this.receiptStore;
    }

    public BlockChainBuilder setReceiptStore(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
        return this;
    }

    public BlockChainBuilder setListener(EthereumListener listener) {
        this.listener = listener;
        return this;
    }

    public BlockChainBuilder setBtcBlockStoreFactory(BtcBlockStoreWithCache.Factory btcBlockStoreFactory) {
        return this;
    }

    public BlockChainBuilder setStateRootHandler(StateRootHandler stateRootHandler) {
        this.stateRootHandler = stateRootHandler;
        return this;
    }

    public RskSystemProperties getConfig() {
        return config;
    }

    public StateRootHandler getStateRootHandler() {
        return this.stateRootHandler;
    }

    public TrieStore getTrieStore() {
        return trieStore;
    }

    public Repository getRepository() {
        return repository;
    }

    public RepositoryLocator getRepositoryLocator() {
        return repositoryLocator;
    }

    public TransactionPoolImpl getTransactionPool() {
        return transactionPool;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public BlockChainImpl build() {
        BlocksIndex blocksIndex = new HashMapBlocksIndex();

        if (config == null){
            config = new TestSystemProperties();
        }

        if (trieStore == null) {
            trieStore = new TrieStoreImpl(new HashMapDB().setClearOnClose(false));
        }

        if (repository == null) {
            repository = new MutableRepository(trieStore, new Trie(trieStore));
        }

        if (stateRootHandler == null) {
            stateRootHandler = new StateRootHandler(config.getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));
        }

        if (genesis == null) {
            genesis = new BlockGenerator().getGenesisBlock();
        }

        GenesisLoaderImpl.loadGenesisInitalState(repository, genesis);
        repository.commit();
        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();

        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
        if (blockStore == null) {
            blockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), blocksIndex);
        }

        if (receiptStore == null) {
            KeyValueDataSource ds = new HashMapDB();
            ds.init();
            receiptStore = new ReceiptStoreImpl(ds);
        }

        if (listener == null) {
            listener = new BlockExecutorTest.SimpleEthereumListener();
        }

        if (bridgeSupportFactory == null) {
            bridgeSupportFactory = new BridgeSupportFactory(
                    new RepositoryBtcBlockStoreWithCache.Factory(
                            config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                    config.getNetworkConstants().getBridgeConstants(),
                    config.getActivationConfig());
        }

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();

        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(trieStore).blockStore(blockStore);

        BlockValidator blockValidator = validatorBuilder.build();

        ReceivedTxSignatureCache receivedTxSignatureCache = new ReceivedTxSignatureCache();
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                blockStore,
                receiptStore,
                blockFactory,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory),
                blockTxSignatureCache
        );
        repositoryLocator = new RepositoryLocator(trieStore, stateRootHandler);

        transactionPool = new TransactionPoolImpl(
                config, repositoryLocator, this.blockStore, blockFactory, new TestCompositeEthereumListener(),
                transactionExecutorFactory, new ReceivedTxSignatureCache(), 10, 100, Mockito.mock(Web3.class));
        BlockExecutor blockExecutor = new BlockExecutor(
                config.getActivationConfig(),
                repositoryLocator,
                transactionExecutorFactory
        );
        BlockChainImpl blockChain = new BlockChainLoader(
                blockStore,
                receiptStore,
                transactionPool,
                listener,
                blockValidator,
                blockExecutor,
                genesis,
                stateRootHandler,
                repositoryLocator
        ).loadBlockchain();

        if (this.testing) {
            blockChain.setBlockValidator(new DummyBlockValidator());
            blockChain.setNoValidation(true);
        }

        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);
        if (this.blocks != null) {
            for (Block b : this.blocks) {
                blockExecutor.executeAndFillAll(b, blockChain.getBestBlock().getHeader());
                blockChain.tryToConnect(b);
            }
        }

        return blockChain;
    }

    public Blockchain ofSize(int size) {
        return ofSize(size, false);
    }

    public Blockchain ofSize(int size, boolean mining) {
        return ofSize(size, mining, Collections.emptyMap());
    }

    public Blockchain ofSize(int size, boolean mining, Map<RskAddress, AccountState> accounts) {
        BlockGenerator blockGenerator = new BlockGenerator();
        Genesis genesis = blockGenerator.getGenesisBlock(accounts);
        BlockChainImpl blockChain = setGenesis(genesis).build();

        if (size > 0) {
            List<Block> blocks = mining ? blockGenerator.getMinedBlockChain(genesis, size) : blockGenerator.getBlockChain(genesis, size);

            for (Block block: blocks)
                Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block));
        }

        return blockChain;
    }

    public static Blockchain copy(Blockchain original) {
        BlockChainBuilder builder = new BlockChainBuilder();
        BlockChainImpl blockChain = builder.build();

        long height = original.getStatus().getBestBlockNumber();

        for (long k = 0; k <= height; k++)
            blockChain.tryToConnect(original.getBlockByNumber(k));

        return blockChain;
    }

    public static Blockchain copyAndExtend(Blockchain original, int size) {
        return copyAndExtend(original, size, false);
    }

    public static Blockchain copyAndExtend(Blockchain original, int size, boolean mining) {
        Blockchain blockchain = copy(original);
        extend(blockchain, size, false, mining);
        return blockchain;
    }

    public static void extend(Blockchain blockchain, int size, boolean withUncles, boolean mining) {
        Block initial = blockchain.getBestBlock();
        extend(blockchain, size, withUncles, mining, initial);
    }

    public static void extend(Blockchain blockchain, int size, boolean withUncles, boolean mining, long blockNumber) {
        Block initial = blockchain.getBlockByNumber(blockNumber);
        extend(blockchain, size, withUncles, mining, initial);
    }

    private static void extend(Blockchain blockchain, int size, boolean withUncles, boolean mining, Block initialBlock) {
        List<Block> blocks = new BlockGenerator().getBlockChain(initialBlock, size, 0, withUncles, mining, null);

        for (Block block: blocks)
            blockchain.tryToConnect(block);
    }
}
