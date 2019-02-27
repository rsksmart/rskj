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
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.*;
import co.rsk.db.RepositoryImpl;
import co.rsk.db.TrieStorePoolOnMemory;
import co.rsk.peg.RepositoryBlockStore;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.*;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class BlockChainBuilder {
    private boolean testing;
    private List<Block> blocks;
    private List<TransactionInfo> txinfos;

    private Repository repository;
    private BlockStore blockStore;
    private Genesis genesis;
    private ReceiptStore receiptStore;
    private RskSystemProperties config;
    private EthereumListener listener;

    public BlockChainBuilder setTesting(boolean value) {
        this.testing = value;
        return this;
    }

    public BlockChainBuilder setBlocks(List<Block> blocks) {
        this.blocks = blocks;
        return this;
    }

    public BlockChainBuilder setRepository(Repository repository) {
        this.repository = repository;
        return this;
    }

    public BlockChainBuilder setBlockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
        return this;
    }

    public BlockChainBuilder setTransactionInfos(List<TransactionInfo> txinfos) {
        this.txinfos = txinfos;
        return this;
    }

    public BlockChainBuilder setGenesis(Genesis genesis) {
        this.genesis = genesis;
        return this;
    }

    public BlockChainBuilder setConfig(RskSystemProperties config) {
        this.config = config;
        return this;
    }

    public BlockChainBuilder setReceiptStore(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
        return this;
    }

    public BlockChainBuilder setListener(EthereumListener listener) {
        this.listener = listener;
        return this;
    }

    public RskSystemProperties getConfig() {
        return config;
    }

    public BlockChainImpl build() {
        if (config == null){
            config = new TestSystemProperties();
        }

        if (repository == null)
            repository = new RepositoryImpl(new TrieImpl(new TrieStoreImpl(new HashMapDB().setClearOnClose(false)), true), new HashMapDB(), new TrieStorePoolOnMemory(), config.detailsInMemoryStorageLimit());

        if (blockStore == null) {
            blockStore = new IndexedBlockStore(new HashMap<>(), new HashMapDB(), null);
        }

        if (receiptStore == null) {
            KeyValueDataSource ds = new HashMapDB();
            ds.init();
            receiptStore = new ReceiptStoreImpl(ds);
        }

        if (txinfos != null && !txinfos.isEmpty())
            for (TransactionInfo txinfo : txinfos)
                receiptStore.add(txinfo.getBlockHash(), txinfo.getIndex(), txinfo.getReceipt());

        if (listener == null) {
            listener = new BlockExecutorTest.SimpleEthereumListener();
        }

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();

        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(repository).blockStore(blockStore);

        BlockValidator blockValidator = validatorBuilder.build();

        TransactionPoolImpl transactionPool = new TransactionPoolImpl(config, this.repository, this.blockStore, receiptStore, new ProgramInvokeFactoryImpl(), new TestCompositeEthereumListener(), 10, 100);

        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();
        BlockChainImpl blockChain = new BlockChainImpl(this.repository, this.blockStore, receiptStore, transactionPool, listener, blockValidator, false, 1, new BlockExecutor(this.repository, (tx1, txindex1, coinbase, track1, block1, totalGasUsed1) -> new TransactionExecutor(
                tx1,
                txindex1,
                block1.getCoinbase(),
                track1,
                this.blockStore,
                receiptStore,
                programInvokeFactory,
                block1,
                listener,
                totalGasUsed1,
                config.getVmConfig(),
                config.getBlockchainConfig(),
                config.playVM(),
                config.isRemascEnabled(),
                config.vmTrace(),
                new PrecompiledContracts(config),
                config.databaseDir(),
                config.vmTraceDir(),
                config.vmTraceCompressed()
        )));

        if (this.testing) {
            blockChain.setBlockValidator(new DummyBlockValidator());
            blockChain.setNoValidation(true);
        }

        if (this.genesis != null) {
            for (Map.Entry<RskAddress, AccountState> accountsEntry : genesis.getAccounts().entrySet()) {
                RskAddress accountAddress = accountsEntry.getKey();
                repository.createAccount(accountAddress);
                repository.addBalance(accountAddress, accountsEntry.getValue().getBalance());
            }

            Repository track = this.repository.startTracking();
            new RepositoryBlockStore(config, track, PrecompiledContracts.BRIDGE_ADDR);
            track.commit();

            this.genesis.setStateRoot(this.repository.getRoot());
            this.genesis.flushRLP();
            blockChain.setStatus(this.genesis, this.genesis.getCumulativeDifficulty());
        }

        if (this.blocks != null) {
            final ProgramInvokeFactoryImpl programInvokeFactory1 = new ProgramInvokeFactoryImpl();
            BlockExecutor blockExecutor = new BlockExecutor(repository, (tx1, txindex1, coinbase, track1, block1, totalGasUsed1) -> new TransactionExecutor(
                    tx1,
                    txindex1,
                    block1.getCoinbase(),
                    track1,
                    blockStore,
                    receiptStore,
                    programInvokeFactory1,
                    block1,
                    listener,
                    totalGasUsed1,
                    config.getVmConfig(),
                    config.getBlockchainConfig(),
                    config.playVM(),
                    config.isRemascEnabled(),
                    config.vmTrace(),
                    new PrecompiledContracts(config),
                    config.databaseDir(),
                    config.vmTraceDir(),
                    config.vmTraceCompressed()
            ));

            for (Block b : this.blocks) {
                blockExecutor.executeAndFillAll(b, blockChain.getBestBlock());
                blockChain.tryToConnect(b);
            }
        }

        return blockChain;
    }

    public static Blockchain ofSize(int size) {
        return ofSize(size, false);
    }

    public static Blockchain ofSize(int size, boolean mining) {
        return ofSize(size, mining, null, null);
    }

    public static Blockchain ofSize(int size, boolean mining, List<Account> accounts, List<Coin> balances) {
        BlockChainBuilder builder = new BlockChainBuilder();
        BlockChainImpl blockChain = builder.build();

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();

        if (accounts != null)
            for (int k = 0; k < accounts.size(); k++) {
                Account account = accounts.get(k);
                Coin balance = balances.get(k);
                blockChain.getRepository().createAccount(account.getAddress());
                blockChain.getRepository().addBalance(account.getAddress(), balance);
            }

        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

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
        List<Block> blocks = new BlockGenerator().getBlockChain(initial, size, 0, withUncles, mining, null);

        for (Block block: blocks)
            blockchain.tryToConnect(block);
    }
}
