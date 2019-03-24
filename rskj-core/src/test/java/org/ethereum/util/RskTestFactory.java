package org.ethereum.util;

import co.rsk.RskContext;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.RepositoryImpl;
import co.rsk.db.StateRootHandler;
import co.rsk.net.BlockNodeInformation;
import co.rsk.net.BlockSyncService;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.Genesis;
import org.ethereum.core.Repository;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.*;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.vm.PrecompiledContracts;

import java.util.HashMap;

/**
 * This is the test version of {@link RskContext}.
 *
 * We try to recreate the objects used in production as best as we can,
 * replacing persistent storage with in-memory storage.
 * There are many nulls in place of objects that aren't part of our
 * tests yet.
 */
public class RskTestFactory extends RskContext {
    private final TestSystemProperties config;

    private IndexedBlockStore blockStore;
    private RepositoryImpl repository;
    private ReversibleTransactionExecutor reversibleTransactionExecutor;
    private StateRootHandler stateRootHandler;
    private NodeBlockProcessor blockProcessor;
    private RskImpl rskImpl;
    private CompositeEthereumListener compositeEthereumListener;
    private ReceiptStoreImpl receiptStore;
    private DummyBlockValidator blockValidator;
    private Genesis genesis;
    private BlockExecutor blockExecutor;
    private BlockExecutor.TransactionExecutorFactory transactionExecutorFactory;
    private PrecompiledContracts precompiledContracts;

    public RskTestFactory() {
        this(new TestSystemProperties());
    }

    public RskTestFactory(TestSystemProperties config) {
        super(new String[0]);
        this.config = config;
    }

    @Override
    public RskSystemProperties getRskSystemProperties() {
        return config;
    }

    @Override
    public BlockValidator getBlockValidator() {
        if (blockValidator == null) {
            blockValidator = new DummyBlockValidator();
        }

        return blockValidator;
    }

    @Override
    public ReceiptStore getReceiptStore() {
        if (receiptStore == null) {
            receiptStore = new ReceiptStoreImpl(new HashMapDB());
        }

        return receiptStore;
    }

    @Override
    public BlockStore getBlockStore() {
        if (blockStore == null) {
            blockStore = new IndexedBlockStore(new HashMap<>(), new HashMapDB(), null);
        }

        return blockStore;
    }

    @Override
    public Repository getRepository() {
        if (repository == null) {
            HashMapDB stateStore = new HashMapDB();
            repository = new RepositoryImpl(
                    new Trie(new TrieStoreImpl(stateStore), true),
                    new HashMapDB(),
                    new TrieStorePoolOnMemory(),
                    getRskSystemProperties().detailsInMemoryStorageLimit()
            );
        }

        return repository;
    }

    @Override
    public CompositeEthereumListener getCompositeEthereumListener() {
        if (compositeEthereumListener == null) {
            compositeEthereumListener = new TestCompositeEthereumListener();
        }

        return compositeEthereumListener;
    }

    @Override
    public Genesis getGenesis() {
        if (genesis == null) {
            genesis = new BlockGenerator().getGenesisBlock();
        }

        return genesis;
    }

    @Override
    public StateRootHandler getStateRootHandler() {
        if (stateRootHandler == null) {
            stateRootHandler = new StateRootHandler(getRskSystemProperties(), new HashMapDB(), new HashMap<>());
        }

        return stateRootHandler;
    }

    public NodeBlockProcessor getBlockProcessor() {
        if (blockProcessor == null) {
            co.rsk.net.BlockStore store = new co.rsk.net.BlockStore();
            BlockNodeInformation nodeInformation = new BlockNodeInformation();
            SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
            BlockSyncService blockSyncService = new BlockSyncService(
                    getRskSystemProperties(),
                    store,
                    getBlockchain(),
                    nodeInformation,
                    syncConfiguration
            );
            blockProcessor = new NodeBlockProcessor(
                    store,
                    getBlockchain(),
                    nodeInformation,
                    blockSyncService,
                    syncConfiguration
            );
        }

        return blockProcessor;
    }

    public ReversibleTransactionExecutor getReversibleTransactionExecutor() {
        if (reversibleTransactionExecutor == null) {
            reversibleTransactionExecutor = new ReversibleTransactionExecutor(
                    getRskSystemProperties(),
                    getRepository(),
                    getBlockStore(),
                    getReceiptStore(),
                    getProgramInvokeFactory()
            );
        }

        return reversibleTransactionExecutor;
    }

    public RskImpl getRskImpl() {
        if (rskImpl == null) {
            rskImpl = new RskImpl(
                    null,
                    getTransactionPool(),
                    getCompositeEthereumListener(),
                    getBlockProcessor(),
                    getBlockchain()
            );
        }

        return rskImpl;
    }

    public BlockExecutor getBlockExecutor() {
        if (blockExecutor == null) {
            blockExecutor = new BlockExecutor(
                    getRepository(),
                    getTransactionExecutorFactory(),
                    getStateRootHandler()
            );
        }

        return blockExecutor;
    }

    private BlockExecutor.TransactionExecutorFactory getTransactionExecutorFactory() {
        if (transactionExecutorFactory == null) {
            RskSystemProperties config = getRskSystemProperties();
            transactionExecutorFactory = (tx, txindex, coinbase, track, block, totalGasUsed) -> new TransactionExecutor(
                    tx,
                    txindex,
                    block.getCoinbase(),
                    track,
                    getBlockStore(),
                    getReceiptStore(),
                    getProgramInvokeFactory(),
                    block,
                    getCompositeEthereumListener(),
                    totalGasUsed,
                    config.getVmConfig(),
                    config.getBlockchainConfig(),
                    config.playVM(),
                    config.isRemascEnabled(),
                    config.vmTrace(),
                    getPrecompiledContracts(),
                    config.databaseDir(),
                    config.vmTraceDir(),
                    config.vmTraceCompressed()
            );
        }

        return transactionExecutorFactory;
    }

    private PrecompiledContracts getPrecompiledContracts() {
        if (precompiledContracts == null) {
            precompiledContracts = new PrecompiledContracts(getRskSystemProperties());
        }

        return precompiledContracts;
    }

    public static Genesis getGenesisInstance(RskSystemProperties config) {
        return GenesisLoader.loadGenesis(config.genesisInfo(), config.getBlockchainConfig().getCommonConstants().getInitialNonce(), false);
    }
}
