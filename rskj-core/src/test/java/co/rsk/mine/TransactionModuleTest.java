/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.mine;

import co.rsk.config.ConfigUtils;
import co.rsk.config.MiningConfig;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.*;
import co.rsk.core.bc.*;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.db.StateRootHandler;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.eth.*;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.trie.TrieConverter;
import co.rsk.trie.TrieStore;
import co.rsk.validators.BlockUnclesValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.ChannelManagerImpl;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.rpc.Simples.SimpleConfigCapabilities;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.Web3Impl;
import org.ethereum.rpc.Web3Mocks;
import org.ethereum.sync.SyncPool;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.time.Clock;
import java.util.HashMap;

public class TransactionModuleTest {
    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private TransactionExecutorFactory transactionExecutorFactory;

    @Test
    public void sendTransactionMustNotBeMined() {
        World world = new World();
        BlockChainImpl blockchain = world.getBlockChain();

        TrieStore trieStore = world.getTrieStore();
        RepositoryLocator repositoryLocator = world.getRepositoryLocator();
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());

        BlockStore blockStore = world.getBlockStore();

        TransactionPool transactionPool = new TransactionPoolImpl(config, repositoryLocator, blockStore, blockFactory, null, buildTransactionExecutorFactory(blockStore, null), 10, 100);

        Web3Impl web3 = createEnvironment(blockchain, null, trieStore, transactionPool, blockStore, false);

        String tx = sendTransaction(web3, repository);

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        Transaction txInBlock = getTransactionFromBlockWhichWasSend(blockchain, tx);

        //Transaction tx must not be in block
        Assert.assertNull(txInBlock);
    }

    @Test
    public void sendTransactionMustBeMined() {
        World world = new World();
        BlockChainImpl blockchain = world.getBlockChain();

        TrieStore trieStore = world.getTrieStore();
        RepositoryLocator repositoryLocator = world.getRepositoryLocator();
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());

        BlockStore blockStore = world.getBlockStore();

        TransactionPool transactionPool = new TransactionPoolImpl(config, repositoryLocator, blockStore, blockFactory, null, buildTransactionExecutorFactory(blockStore, null), 10, 100);

        Web3Impl web3 = createEnvironment(blockchain, null, trieStore, transactionPool, blockStore, true);

        String tx = sendTransaction(web3, repository);

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(2, blockchain.getBestBlock().getTransactionsList().size());

        Transaction txInBlock = getTransactionFromBlockWhichWasSend(blockchain, tx);

        //Transaction tx must be in the block mined.
        Assert.assertEquals(tx, txInBlock.getHash().toJsonString());
    }

    /**
     * This test send a several transactions, and should be mine 1 transaction in each block.
     */
    @Test
    public void sendSeveralTransactionsWithAutoMining() {

        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        BlockChainImpl blockchain = world.getBlockChain();

        MiningMainchainView mainchainView = new MiningMainchainViewImpl(world.getBlockStore(), 1);

        StateRootHandler stateRootHandler = world.getStateRootHandler();
        RepositoryLocator repositoryLocator = world.getRepositoryLocator();

        BlockStore blockStore = world.getBlockStore();

        TransactionPool transactionPool = world.getTransactionPool();

        Web3Impl web3 = createEnvironment(blockchain, mainchainView, receiptStore, transactionPool, blockStore, true, stateRootHandler, repositoryLocator);

        for (int i = 1; i < 100; i++) {
            String tx = sendTransaction(web3, repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader()));
            // The goal of this test is transaction testing and not block mining testing
            // Hence, there is no setup for listeners and best blocks must be added manually
            // to mainchain view object that is used by miner server to build new blocks.
            mainchainView.addBest(blockchain.getBestBlock().getHeader());
            Transaction txInBlock = getTransactionFromBlockWhichWasSend(blockchain, tx);
            Assert.assertEquals(i, blockchain.getBestBlock().getNumber());
            Assert.assertEquals(2, blockchain.getBestBlock().getTransactionsList().size());
            Assert.assertEquals(tx, txInBlock.getHash().toJsonString());
        }
    }

    @Test
    public void sendRawTransactionWithAutoMining() throws Exception {

        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        BlockChainImpl blockchain = world.getBlockChain();

        TrieStore trieStore = world.getTrieStore();
        RepositoryLocator repositoryLocator = world.getRepositoryLocator();

        BlockStore blockStore = world.getBlockStore();

        TransactionPool transactionPool = new TransactionPoolImpl(config, repositoryLocator, blockStore, blockFactory, null, buildTransactionExecutorFactory(blockStore, receiptStore), 10, 100);

        Web3Impl web3 = createEnvironment(blockchain, receiptStore, trieStore, transactionPool, blockStore, true);

        String txHash = sendRawTransaction(web3);

        Assert.assertEquals(1, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(2, blockchain.getBestBlock().getTransactionsList().size());

        Transaction txInBlock = getTransactionFromBlockWhichWasSend(blockchain, txHash);

        //Transaction tx must be in the block mined.
        Assert.assertEquals(txHash, txInBlock.getHash().toJsonString());
    }

    @Test
    public void sendRawTransactionWithoutAutoMining() {

        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        BlockChainImpl blockchain = world.getBlockChain();

        TrieStore trieStore = world.getTrieStore();
        RepositoryLocator repositoryLocator = world.getRepositoryLocator();

        BlockStore blockStore = world.getBlockStore();

        TransactionPool transactionPool = new TransactionPoolImpl(config, repositoryLocator, blockStore, blockFactory, null, buildTransactionExecutorFactory(blockStore, receiptStore), 10, 100);

        Web3Impl web3 = createEnvironment(blockchain, receiptStore, trieStore, transactionPool, blockStore, false);

        String txHash = sendRawTransaction(web3);

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(1, transactionPool.getPendingTransactions().size());
        Assert.assertEquals(txHash, transactionPool.getPendingTransactions().get(0).getHash().toJsonString());
    }

    private String sendRawTransaction(Web3Impl web3) {
        Account sender = new AccountBuilder().name("cow").build();
        Account receiver = new AccountBuilder().name("addr2").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .gasPrice(BigInteger.valueOf(8))
                .gasLimit(BigInteger.valueOf(50000))
                .value(BigInteger.valueOf(7))
                .nonce(0)
                .build();

        String rawData = Hex.toHexString(tx.getEncoded());

        return web3.eth_sendRawTransaction(rawData);
    }

    private Transaction getTransactionFromBlockWhichWasSend(BlockChainImpl blockchain, String tx) {
        Transaction txInBlock = null;

        for (Transaction x : blockchain.getBestBlock().getTransactionsList()) {
            if (x.getHash().toJsonString().equals(tx)) {
                txInBlock = x;
                break;
            }
        }
        return txInBlock;
    }

    private String sendTransaction(Web3Impl web3, RepositorySnapshot repository) {

        Web3.CallArguments args = getTransactionParameters(web3, repository);

        return web3.eth_sendTransaction(args);
    }

    private Web3.CallArguments getTransactionParameters(Web3Impl web3, RepositorySnapshot repository) {
        RskAddress addr1 = new RskAddress(ECKey.fromPrivate(Keccak256Helper.keccak256("cow".getBytes())).getAddress());
        String addr2 = web3.personal_newAccountWithSeed("addr2");
        BigInteger value = BigInteger.valueOf(7);
        BigInteger gasPrice = BigInteger.valueOf(8);
        BigInteger gasLimit = BigInteger.valueOf(50000);
        String data = "0xff";

        Web3.CallArguments args = new Web3.CallArguments();
        args.from = TypeConverter.toJsonHex(addr1.getBytes());
        args.to = addr2;
        args.data = data;
        args.gas = TypeConverter.toQuantityJsonHex(gasLimit);
        args.gasPrice = TypeConverter.toQuantityJsonHex(gasPrice);
        args.value = value.toString();
        args.nonce = repository.getAccountState(addr1).getNonce().toString();

        return args;
    }

    private Web3Impl createEnvironment(Blockchain blockchain,
                                       ReceiptStore receiptStore,
                                       TrieStore store,
                                       TransactionPool transactionPool,
                                       BlockStore blockStore,
                                       boolean mineInstant) {
        StateRootHandler stateRootHandler = new StateRootHandler(
                config.getActivationConfig(),
                new TrieConverter(),
                new HashMapDB(),
                new HashMap<>()
        );
        return createEnvironment(blockchain,
                new MiningMainchainViewImpl(blockStore, 1),
                receiptStore,
                transactionPool,
                blockStore,
                mineInstant,
                stateRootHandler,
                new RepositoryLocator(store, stateRootHandler));
    }

    private Web3Impl createEnvironment(Blockchain blockchain, MiningMainchainView mainchainView, ReceiptStore receiptStore, TransactionPool transactionPool, BlockStore blockStore, boolean mineInstant, StateRootHandler stateRootHandler, RepositoryLocator repositoryLocator) {
        transactionPool.processBest(blockchain.getBestBlock());

        ConfigCapabilities configCapabilities = new SimpleConfigCapabilities();
        CompositeEthereumListener compositeEthereumListener = new CompositeEthereumListener();
        Ethereum eth = new EthereumImpl(
                new ChannelManagerImpl(
                        config,
                        new SyncPool(
                                compositeEthereumListener,
                                blockchain,
                                config,
                                null,
                                null,
                                null
                        )
                ),
                transactionPool,
                compositeEthereumListener,
                blockchain
        );
        MinerClock minerClock = new MinerClock(true, Clock.systemUTC());

        transactionExecutorFactory = buildTransactionExecutorFactory(blockStore, receiptStore);
        MiningConfig miningConfig = ConfigUtils.getDefaultMiningConfig();
        MinerServer minerServer = new MinerServerImpl(
                config,
                eth,
                mainchainView,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        config.getActivationConfig(),
                        miningConfig,
                        repositoryLocator,
                        blockStore,
                        transactionPool,
                        new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants()),
                        new GasLimitCalculator(config.getNetworkConstants()),
                        new ForkDetectionDataCalculator(),
                        Mockito.mock(BlockUnclesValidationRule.class),
                        minerClock,
                        blockFactory,
                        new BlockExecutor(
                                config,
                                repositoryLocator,
                                stateRootHandler,
                                transactionExecutorFactory
                        ),
                        new MinimumGasPriceCalculator(Coin.valueOf(miningConfig.getMinGasPriceTarget())),
                        new MinerUtils()
                ),
                minerClock,
                blockFactory,
                miningConfig
        );

        Wallet wallet = WalletFactory.createWallet();
        PersonalModuleWalletEnabled personalModule = new PersonalModuleWalletEnabled(config, eth, wallet, transactionPool);
        MinerClient minerClient = new MinerClientImpl(null, minerServer, config.minerClientDelayBetweenBlocks(), config.minerClientDelayBetweenRefreshes());
        EthModuleTransaction transactionModule;

        ReversibleTransactionExecutor reversibleTransactionExecutor1 = new ReversibleTransactionExecutor(
                repositoryLocator,
                transactionExecutorFactory
        );

        if (mineInstant) {
            transactionModule = new EthModuleTransactionInstant(config.getNetworkConstants(), wallet, transactionPool, minerServer, minerClient, blockchain);
        } else {
            transactionModule = new EthModuleTransactionBase(config.getNetworkConstants(), wallet, transactionPool);
        }

        final RepositoryBtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                config.getNetworkConstants().getBridgeConstants().getBtcParams());
        EthModule ethModule = new EthModule(
                config.getNetworkConstants().getBridgeConstants(), config.getNetworkConstants().getChainId(), blockchain, transactionPool,
                reversibleTransactionExecutor1, new ExecutionBlockRetriever(mainchainView, blockchain, null, null),
                repositoryLocator, new EthModuleSolidityDisabled(), new EthModuleWalletEnabled(wallet), transactionModule,
                new BridgeSupportFactory(
                        btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(),
                        config.getActivationConfig())
        );
        TxPoolModule txPoolModule = new TxPoolModuleImpl(transactionPool);
        DebugModule debugModule = new DebugModuleImpl(null, null, Web3Mocks.getMockMessageHandler(), null);

        ChannelManager channelManager = new SimpleChannelManager();
        return new Web3RskImpl(
                eth,
                blockchain,
                config,
                minerClient,
                Web3Mocks.getMockMinerServer(),
                personalModule,
                ethModule,
                null,
                txPoolModule,
                null,
                debugModule,
                null,
                channelManager,
                null,
                null,
                blockStore,
                receiptStore,
                null,
                null,
                null,
                configCapabilities,
                null,
                null,
                null);
    }

    private TransactionExecutorFactory buildTransactionExecutorFactory(BlockStore blockStore, ReceiptStore receiptStore) {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig());
        return new TransactionExecutorFactory(
                config,
                blockStore,
                receiptStore,
                blockFactory,
                null,
                new PrecompiledContracts(config, bridgeSupportFactory)
        );
    }
}
