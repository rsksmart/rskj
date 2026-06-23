/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.ethereum.rpc;

import co.rsk.Flusher;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.Wallet;
import co.rsk.core.WalletFactory;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.net.SyncProcessor;
import co.rsk.net.handler.quota.TxQuotaChecker;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.debug.trace.RskTracer;
import co.rsk.rpc.modules.debug.trace.TraceProvider;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleTransactionBase;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.rsk.RskModuleImpl;
import co.rsk.rpc.modules.trace.TraceModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.util.NodeStopper;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Genesis;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.rpc.Simples.SimpleConfigCapabilities;
import org.ethereum.rpc.Simples.SimpleMinerClient;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.mockito.Mockito;

import java.util.List;

/**
 * Minimal {@link Web3Impl} wiring for chain-backed RPC integration tests (no mocked blockchain).
 */
final class ForkSafeRpcTestSupport {

    private ForkSafeRpcTestSupport() {
    }

    static Web3Impl createWeb3(World world) {
        RskSystemProperties config = world.getConfig();
        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
        BlockStore blockStore = world.getBlockStore();
        BlockChainImpl blockchain = world.getBlockChain();
        ReceiptStore receiptStore = null;

        BridgeSupportFactory bridgeSupportFactory = world.getBridgeSupportFactory();
        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                blockStore,
                receiptStore,
                blockFactory,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory, signatureCache),
                signatureCache);

        TransactionPool transactionPool = world.getTransactionPool() != null
                ? world.getTransactionPool()
                : new TransactionPoolImpl(
                        config,
                        world.getRepositoryLocator(),
                        blockStore,
                        blockFactory,
                        null,
                        transactionExecutorFactory,
                        world.getReceivedTxSignatureCache(),
                        10,
                        100,
                        Mockito.mock(TxQuotaChecker.class),
                        Mockito.mock(GasPriceTracker.class));

        Ethereum eth = Mockito.mock(Ethereum.class);
        Wallet wallet = WalletFactory.createWallet();
        PersonalModule personalModule = new PersonalModuleWalletEnabled(config, eth, wallet, transactionPool);
        ReversibleTransactionExecutor executor = new ReversibleTransactionExecutor(
                world.getRepositoryLocator(),
                transactionExecutorFactory);
        Web3InformationRetriever retriever = new Web3InformationRetriever(
                transactionPool,
                blockchain,
                world.getRepositoryLocator(),
                new ExecutionBlockRetriever(blockchain, null, null));

        EthModule ethModule = new EthModule(
                config.getNetworkConstants().getBridgeConstants(),
                config.getNetworkConstants().getChainId(),
                blockchain,
                transactionPool,
                executor,
                new ExecutionBlockRetriever(blockchain, null, null),
                world.getRepositoryLocator(),
                new EthModuleWalletEnabled(wallet, transactionPool, signatureCache),
                new EthModuleTransactionBase(config.getNetworkConstants(), wallet, transactionPool, null),
                bridgeSupportFactory,
                config.getGasEstimationCap(),
                config.getCallGasCap(),
                config.getActivationConfig(),
                new PrecompiledContracts(config, bridgeSupportFactory, signatureCache),
                false,
                null);

        return new Web3RskImpl(
                eth,
                blockchain,
                config,
                new SimpleMinerClient(),
                Mockito.mock(MinerServer.class),
                personalModule,
                ethModule,
                Mockito.mock(EvmModule.class),
                new TxPoolModuleImpl(transactionPool, signatureCache),
                Mockito.mock(MnrModule.class),
                new DebugModuleImpl(new TraceProvider(List.of(new RskTracer(null, null, null, null))),
                        Web3Mocks.getMockMessageHandler(), null),
                Mockito.mock(TraceModule.class),
                new RskModuleImpl(blockchain, blockStore, receiptStore, retriever,
                        Mockito.mock(Flusher.class), Mockito.mock(NodeStopper.class)),
                new SimpleChannelManager(),
                Mockito.mock(PeerScoringManager.class),
                null,
                blockStore,
                receiptStore,
                Mockito.mock(PeerServer.class),
                Mockito.mock(BlockProcessor.class),
                Mockito.mock(HashRateCalculator.class),
                new SimpleConfigCapabilities(),
                new BuildInfo("test", "test"),
                Mockito.mock(BlocksBloomStore.class),
                retriever,
                Mockito.mock(SyncProcessor.class),
                signatureCache);
    }

    static World worldFromBuilder(BlockChainImpl chain, BlockChainBuilder builder, Genesis genesis) {
        return new World(
                chain,
                builder.getBlockStore(),
                builder.getReceiptStore(),
                builder.getTrieStore(),
                builder.getRepository(),
                builder.getTransactionPool(),
                genesis,
                builder.getConfig());
    }
}
