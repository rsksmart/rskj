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
import co.rsk.config.TestSystemProperties;
import co.rsk.core.*;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.rpc.ExecutionBlockRetriever;
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
import co.rsk.validators.BlockUnclesValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
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
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.time.Clock;

public class TransactionModuleTest {
    Wallet wallet;
    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void sendTransactionMustNotBeMined() {
        World world = new World();
        BlockChainImpl blockchain = world.getBlockChain();

        Repository repository = world.getRepository();

        BlockStore blockStore = world.getBlockChain().getBlockStore();

        TransactionPool transactionPool = new TransactionPoolImpl(config, repository, blockStore, null, null, null, 10, 100);

        Web3Impl web3 = createEnvironment(blockchain, null, repository, transactionPool, blockStore, false);

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

        Repository repository = blockchain.getRepository();

        BlockStore blockStore = world.getBlockChain().getBlockStore();

        TransactionPool transactionPool = new TransactionPoolImpl(config, repository, blockStore, null, null, null, 10, 100);

        Web3Impl web3 = createEnvironment(blockchain, null, repository, transactionPool, blockStore, true);

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

        Repository repository = blockchain.getRepository();

        BlockStore blockStore = world.getBlockChain().getBlockStore();

        TransactionPool transactionPool = new TransactionPoolImpl(config, repository, blockStore, receiptStore, null, null, 10, 100);

        Web3Impl web3 = createEnvironment(blockchain, receiptStore, repository, transactionPool, blockStore, true);

        for (int i = 1; i < 100; i++) {
            String tx = sendTransaction(web3, repository);
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

        Repository repository = blockchain.getRepository();

        BlockStore blockStore = world.getBlockChain().getBlockStore();

        TransactionPool transactionPool = new TransactionPoolImpl(config, repository, blockStore, receiptStore, null, null, 10, 100);

        Web3Impl web3 = createEnvironment(blockchain, receiptStore, repository, transactionPool, blockStore, true);

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

        Repository repository = blockchain.getRepository();

        BlockStore blockStore = world.getBlockChain().getBlockStore();

        TransactionPool transactionPool = new TransactionPoolImpl(config, repository, blockStore, receiptStore, null, null, 10, 100);

        Web3Impl web3 = createEnvironment(blockchain, receiptStore, repository, transactionPool, blockStore, false);

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

    private String sendTransaction(Web3Impl web3, Repository repository) {

        Web3.CallArguments args = getTransactionParameters(web3, repository);

        return web3.eth_sendTransaction(args);
    }

    private Web3.CallArguments getTransactionParameters(Web3Impl web3, Repository repository) {
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
        args.gas = TypeConverter.toJsonHex(gasLimit);
        args.gasPrice = TypeConverter.toJsonHex(gasPrice);
        args.value = value.toString();
        args.nonce = repository.getAccountState(addr1).getNonce().toString();

        return args;
    }

    private Web3Impl createEnvironment(BlockChainImpl blockchain, ReceiptStore receiptStore, Repository repository, TransactionPool transactionPool, BlockStore blockStore, boolean mineInstant) {

        ConfigCapabilities configCapabilities = new SimpleConfigCapabilities();
        CompositeEthereumListener compositeEthereumListener = new CompositeEthereumListener();
        Ethereum eth = new EthereumImpl(new ChannelManagerImpl(config, new SyncPool(compositeEthereumListener, blockchain, config, null)), transactionPool, compositeEthereumListener, blockchain);
        MinerClock minerClock = new MinerClock(true, Clock.systemUTC());

        MinerServer minerServer = new MinerServerImpl(
                config,
                eth,
                blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                new BlockToMineBuilder(
                        ConfigUtils.getDefaultMiningConfig(),
                        repository,
                        blockStore,
                        transactionPool,
                        new DifficultyCalculator(config),
                        new GasLimitCalculator(config),
                        Mockito.mock(BlockUnclesValidationRule.class),
                        config,
                        receiptStore,
                        minerClock
                ),
                minerClock,
                ConfigUtils.getDefaultMiningConfig()
        );

        wallet = WalletFactory.createWallet();
        PersonalModuleWalletEnabled personalModule = new PersonalModuleWalletEnabled(config, eth, wallet, transactionPool);
        MinerClient minerClient = new MinerClientImpl(null, minerServer, config.minerClientDelayBetweenBlocks(), config.minerClientDelayBetweenRefreshes());
        EthModuleTransaction transactionModule = null;

        ReversibleTransactionExecutor reversibleTransactionExecutor1 = new ReversibleTransactionExecutor(config, repository, blockStore, receiptStore, null);

        if (mineInstant) {
            transactionModule = new EthModuleTransactionInstant(config, wallet, transactionPool, minerServer, minerClient, blockchain);
        } else {
            transactionModule = new EthModuleTransactionBase(config, wallet, transactionPool);
        }

        EthModule ethModule = new EthModule(config, blockchain, reversibleTransactionExecutor1, new ExecutionBlockRetriever(blockchain, null, null), new EthModuleSolidityDisabled(), new EthModuleWalletEnabled(wallet), transactionModule);
        TxPoolModule txPoolModule = new TxPoolModuleImpl(transactionPool);
        DebugModule debugModule = new DebugModuleImpl(Web3Mocks.getMockMessageHandler());

        ChannelManager channelManager = new SimpleChannelManager();
        return new Web3RskImpl(
                eth,
                blockchain,
                transactionPool,
                config,
                minerClient,
                Web3Mocks.getMockMinerServer(),
                personalModule,
                ethModule,
                null,
                txPoolModule,
                null,
                debugModule,
                channelManager,
                Web3Mocks.getMockRepository(),
                null,
                null,
                blockStore,
                receiptStore,
                null,
                null,
                null,
                configCapabilities,
                null
        );
    }
}
