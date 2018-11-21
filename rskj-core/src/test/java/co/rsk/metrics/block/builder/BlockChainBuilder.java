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

package co.rsk.metrics.block.builder;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.*;
import co.rsk.db.RepositoryImpl;
import co.rsk.metrics.block.tests.TestContext;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.metrics.profilers.impl.DummyProfiler;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.core.genesis.InitialAddressState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.*;
import org.ethereum.db.TransactionInfo;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.util.RLP;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Based on ajlopez's BlockchainBuilder, by rlaprida on October 10/1/2018
 */
public class BlockChainBuilder {
    private static final Profiler profiler = ProfilerFactory.getInstance();
    private List<BlockInfo> blocks;
    private List<Block> processedBlocks;
    private List<TransactionInfo> txinfos;

    private Repository repository;
    private BlockStore blockStore;
    private Genesis genesis;
    private ReceiptStore receiptStore;
    private TestSystemProperties config;
    private boolean includeRemasc;


    public BlockChainBuilder(boolean includeRemasc){
        this.includeRemasc = includeRemasc;
    }

    public BlockChainBuilder setBlocks(List<BlockInfo> blocks) {
        this.blocks = blocks;
        return this;
    }

    public BlockChainBuilder setProcessedBlocks(List<Block> blocks) {
        this.processedBlocks = blocks;
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

    public BlockChainBuilder setConfig(TestSystemProperties config) {
        this.config = config;
        return this;
    }

    public BlockChainBuilder setReceiptStore(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
        return this;
    }

    public RskSystemProperties getConfig() {
        return config;
    }

    public BlockChainImpl build() {
        return build(false);
    }





    public BlockChainImpl build(boolean withoutCleaner) {

        if (txinfos != null && !txinfos.isEmpty())
            for (TransactionInfo txinfo : txinfos)
                receiptStore.add(txinfo.getBlockHash(), txinfo.getIndex(), txinfo.getReceipt());

        EthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();

        if(includeRemasc){
            validatorBuilder.addRemascValidationRule();
        }

        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(repository).blockStore(blockStore);


        BlockValidator blockValidator = validatorBuilder.build();

        TransactionPoolImpl transactionPool;
        if (withoutCleaner) {
            transactionPool = new TransactionPoolImplNoCleaner(config, this.repository, this.blockStore, receiptStore, new ProgramInvokeFactoryImpl(), new TestCompositeEthereumListener(), 10, 100);
        } else {
            transactionPool = new TransactionPoolImpl(config, this.repository, this.blockStore, receiptStore, new ProgramInvokeFactoryImpl(), new TestCompositeEthereumListener(), 10, 100);
        }


        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();

        BlockExecutor blockExecutor = new BlockExecutor(this.repository, (tx1, txindex1, coinbase, track1, block1, totalGasUsed1) -> new TransactionExecutor(
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
        ));


        BlockChainImpl blockChain = new BlockChainImpl(this.repository, this.blockStore, receiptStore, transactionPool, listener, blockValidator, true, 20, blockExecutor);


        blockChain.setBlockValidator(blockValidator);
        blockChain.setNoValidation(false);
        //blockChain.setBlockValidator(new DummyBlockValidator());
        //blockChain.setNoValidation(true);

        if (this.genesis != null) {

            /*Repository track = this.repository.startTracking();
            new RepositoryBlockStore(config, track, PrecompiledContracts.BRIDGE_ADDR);
            track.commit();*/

            profiler.newBlock(this.genesis.getNumber(), this.genesis.getTransactionsList().size());
            Metric metric = profiler.start(Profiler.PROFILING_TYPE.GENESIS_GENERATION);
            Repository track = this.repository.startTracking();
            for (RskAddress addr : genesis.getPremine().keySet()) {
                repository.createAccount(addr);
                InitialAddressState initialAddressState = genesis.getPremine().get(addr);
                repository.addBalance(addr, initialAddressState.getAccountState().getBalance());
                AccountState accountState = repository.getAccountState(addr);
                accountState.setNonce(initialAddressState.getAccountState().getNonce());
                // First account state
                repository.updateAccountState(addr, accountState);
                // Then contract details, because they overwrite accountState
                if (initialAddressState.getContractDetails()!=null) {
                    repository.updateContractDetails(addr, initialAddressState.getContractDetails());
                }

            }
            track.commit();
            genesis.setStateRoot(repository.getRoot());
            genesis.flushRLP();
            genesis.seal();
            ImportResult result = blockChain.tryToConnect(genesis);
            if(result != ImportResult.IMPORTED_BEST){
                System.out.println("ERROR GENESIS BLOCK IS NOT BEST");
                System.out.println(result);
                profiler.stop(metric);
                return null;
            }
            profiler.stop(metric);

        }


        if (this.blocks != null) {

            Block lastBlock = blockChain.getBestBlock();
            BlockGenerator blockGenerator = new BlockGenerator();
            BlockMiner miner = new BlockMiner(config);

            for (BlockInfo b : this.blocks) {

                profiler.newBlock(b.getBlockNumber(), b.getTransactions().size());


                //Generate actual block using the last connected best-block's hash

               Block block = blockGenerator.createChildBlock(lastBlock, b.getTransactions(),null, b.getBlockDifficulty().longValue(), TestContext.MIN_GAS_PRICE, b.getBlockGasLimit().toByteArray());

                Metric metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_EXECUTE);
               blockExecutor.executeAndFillAll(block, lastBlock);
                profiler.stop(metric);


                metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_MINING);
               block = miner.mineBlock(block);
               profiler.stop(metric);

               block.seal();

               System.out.println("Connecting block "+block.getNumber());

               metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_CONNECTION);
                ImportResult result = blockChain.tryToConnect(block);
                if(result != ImportResult.IMPORTED_BEST){
                    System.out.println("ERROR BLOCK IS NOT BEST");
                    System.out.println(result);
                    profiler.stop(metric);
                    return null;
                }
                profiler.stop(metric);
                lastBlock = blockChain.getBestBlock();
            }
        }

        //Uncomment if flushEnabled = false
        profiler.newBlock(-2, -1);
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.FINAL_BLOCKCHAIN_FLUSH);
        blockStore.flush();
        repository.flush();
        profiler.stop(metric);
        return blockChain;
    }


    public void play() {

        EthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();

        if(includeRemasc){
            validatorBuilder.addRemascValidationRule();
        }

        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(repository).blockStore(blockStore);


        BlockValidator blockValidator = validatorBuilder.build();

        TransactionPoolImpl transactionPool = new TransactionPoolImpl(config, this.repository, this.blockStore, receiptStore, new ProgramInvokeFactoryImpl(), new TestCompositeEthereumListener(), 10, 100);

        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();

        BlockExecutor blockExecutor = new BlockExecutor(this.repository, (tx1, txindex1, coinbase, track1, block1, totalGasUsed1) -> new TransactionExecutor(
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
        ));


        BlockChainImpl blockChain = new BlockChainImpl(this.repository, this.blockStore, receiptStore, transactionPool, listener, blockValidator, true, 20, blockExecutor);


        blockChain.setBlockValidator(blockValidator);
        blockChain.setNoValidation(false);

        if (this.genesis != null) {

            profiler.newBlock(this.genesis.getNumber(), this.genesis.getTransactionsList().size());
            Metric metric = profiler.start(Profiler.PROFILING_TYPE.GENESIS_GENERATION);

            Repository track = this.repository.startTracking();
            for (RskAddress addr : genesis.getPremine().keySet()) {
                repository.createAccount(addr);
                InitialAddressState initialAddressState = genesis.getPremine().get(addr);
                repository.addBalance(addr, initialAddressState.getAccountState().getBalance());
                AccountState accountState = repository.getAccountState(addr);
                accountState.setNonce(initialAddressState.getAccountState().getNonce());
                // First account state
                repository.updateAccountState(addr, accountState);
                // Then contract details, because they overwrite accountState
                if (initialAddressState.getContractDetails()!=null) {
                    repository.updateContractDetails(addr, initialAddressState.getContractDetails());
                }
            }
            track.commit();
            genesis.setStateRoot(repository.getRoot());
            genesis.flushRLP();
            genesis.seal();
            ImportResult result = blockChain.tryToConnect(genesis);
            if(result != ImportResult.IMPORTED_BEST){
                System.out.println("ERROR GENESIS BLOCK IS NOT BEST");
                System.out.println(result);
                profiler.stop(metric);
                return;
            }
            profiler.stop(metric);
        }

        if (this.processedBlocks != null) {

            for (Block b : this.processedBlocks) {

                profiler.newBlock(b.getNumber(), b.getTransactionsList().size());
                Metric metric = profiler.start(Profiler.PROFILING_TYPE.BLOCK_CONNECTION);
                ImportResult result = blockChain.tryToConnect(b);
                profiler.stop(metric);
                if(result != ImportResult.IMPORTED_BEST){
                    System.out.println("ERROR BLOCK IS NOT BEST");
                    System.out.println(result);
                    return;
                }
            }
        }

        //Activate if flush is disabled (flushEnabled = false)
        profiler.newBlock(-2, -1);
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.FINAL_BLOCKCHAIN_FLUSH);
        blockStore.flush();
        repository.flush();
        profiler.stop(metric);


    }

}
