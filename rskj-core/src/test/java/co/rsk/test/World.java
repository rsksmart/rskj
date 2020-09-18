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

package co.rsk.test;

import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockChainImplTest;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.net.BlockNodeInformation;
import co.rsk.net.BlockSyncService;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.net.NetBlockStore;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.trie.TrieConverter;
import co.rsk.trie.TrieStore;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 8/7/2016.
 */
public class World {
    private RskSystemProperties config;
    private BlockChainImpl blockChain;
    private NodeBlockProcessor blockProcessor;
    private BlockExecutor blockExecutor;
    private Map<String, Block> blocks = new HashMap<>();
    private Map<String, Account> accounts = new HashMap<>();
    private Map<String, Transaction> transactions = new HashMap<>();
    private StateRootHandler stateRootHandler;
    private BlockStore blockStore;
    private TrieStore trieStore;
    private ReceiptStore receiptStore;
    private Repository repository;
    private TransactionPool transactionPool;
    private BridgeSupportFactory bridgeSupportFactory;
    private BlockTxSignatureCache blockTxSignatureCache;
    private ReceivedTxSignatureCache receivedTxSignatureCache;

    public World() {
        this(new BlockChainBuilder());
    }

    public World(RskSystemProperties config) {
        this(new BlockChainBuilder().setConfig(config));
    }

    public World(ReceiptStore receiptStore) {
        this(new BlockChainBuilder().setReceiptStore(receiptStore));
    }

    private World(BlockChainBuilder blockChainBuilder) {
        this(blockChainBuilder.build(), blockChainBuilder.getBlockStore(), blockChainBuilder.getReceiptStore(), blockChainBuilder.getTrieStore(), blockChainBuilder.getRepository(), blockChainBuilder.getTransactionPool(), null,
                blockChainBuilder.getConfig() != null ? blockChainBuilder.getConfig() : new TestSystemProperties());
    }

    public World(
            BlockChainImpl blockChain,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            TrieStore trieStore,
            Repository repository,
            TransactionPool transactionPool,
            Genesis genesis
            ) {
        this(blockChain, blockStore, receiptStore, trieStore, repository, transactionPool, genesis, new TestSystemProperties());
    }

    public World(
            BlockChainImpl blockChain,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            TrieStore trieStore,
            Repository repository,
            TransactionPool transactionPool,
            Genesis genesis,
            RskSystemProperties config
            ) {
        this.blockChain = blockChain;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.trieStore = trieStore;
        this.repository = repository;
        this.transactionPool = transactionPool;
        this.config = config;

        if (genesis == null) {
            genesis = (Genesis) BlockChainImplTest.getGenesisBlock(trieStore);
            this.blockChain.setStatus(genesis, genesis.getCumulativeDifficulty());
        }
        this.saveBlock("g00", genesis);

        NetBlockStore store = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockChain, nodeInformation, syncConfiguration);
        this.blockProcessor = new NodeBlockProcessor(store, blockChain, nodeInformation, blockSyncService, syncConfiguration);
        this.stateRootHandler = new StateRootHandler(config.getActivationConfig(), new TrieConverter(), new HashMapDB(), new HashMap<>());

        this.bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(
                        config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig());
        this.receivedTxSignatureCache = new ReceivedTxSignatureCache();
        this.blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);
    }

    public NodeBlockProcessor getBlockProcessor() { return this.blockProcessor; }

    public BlockExecutor getBlockExecutor() {
        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();

        Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                config.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig());

        if (this.blockExecutor == null) {
            this.blockExecutor = new BlockExecutor(
                    config.getActivationConfig(),
                    new RepositoryLocator(getTrieStore(), stateRootHandler),
                    stateRootHandler,
                    new TransactionExecutorFactory(
                            config,
                            blockStore,
                            null,
                            new BlockFactory(config.getActivationConfig()),
                            programInvokeFactory,
                            new PrecompiledContracts(config, bridgeSupportFactory),
                            blockTxSignatureCache
                    )
            );
        }

        return this.blockExecutor;
    }

    public StateRootHandler getStateRootHandler() {
        return this.stateRootHandler;
    }

    public BlockChainImpl getBlockChain() { return this.blockChain; }

    public Block getBlockByName(String name) {
        return blocks.get(name);
    }

    public Block getBlockByHash(Keccak256 hash) {
        for (Block block : blocks.values()) {
            if (block.getHash().equals(hash)) {
                return block;
            }
        }

        return null;
    }

    public void saveBlock(String name, Block block) {
        blocks.put(name, block);
    }

    public Account getAccountByName(String name) { return accounts.get(name); }

    public void saveAccount(String name, Account account) { accounts.put(name, account); }

    public Transaction getTransactionByName(String name) { return transactions.get(name); }

    public TransactionReceipt getTransactionReceiptByName(String name) {
        Transaction transaction = this.getTransactionByName(name);

        TransactionInfo transactionInfo = this.receiptStore.get(transaction.getHash().getBytes());

        if(transactionInfo == null) {
            return null;
        }
        TransactionReceipt transactionReceipt = transactionInfo.getReceipt();

        transactionReceipt.setTransaction(transaction);

        return transactionReceipt;
    }

    public void saveTransaction(String name, Transaction transaction) { transactions.put(name, transaction); }

    public Repository getRepository() {
        return repository;
    }

    public RepositoryLocator getRepositoryLocator() {
        return new RepositoryLocator(getTrieStore(), getStateRootHandler());
    }

    public TrieStore getTrieStore() {
        return trieStore;
    }

    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    public BridgeSupportFactory getBridgeSupportFactory() {
        return bridgeSupportFactory;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public BlockTxSignatureCache getBlockTxSignatureCache() { return blockTxSignatureCache; }

    public ReceivedTxSignatureCache getReceivedTxSignatureCache() { return receivedTxSignatureCache; }

}
