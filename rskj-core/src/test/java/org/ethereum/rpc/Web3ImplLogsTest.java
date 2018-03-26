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

package org.ethereum.rpc;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.Wallet;
import co.rsk.core.WalletFactory;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleSolidityDisabled;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.Simples.SimpleConfigCapabilities;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 30/11/2016.
 */
public class Web3ImplLogsTest {


    // Events used:
    // event Incremented(bool indexed odd, uint x);
    // event Created(uint x);
    // event Valued(uint x);


    /////////////////////////////////////////////////////////////////////////
    // IMPORTANT INFORMATION WHEN WORKING WITH SOLIDITY GENERATED TOPICS
    // FROM EVENTS
    // 1. The event MUST be converted to its normalized form first.
    // 2. uint is not a normalized type. uint -> uint256
    // Note the part that "In Solidity: The first topic is the hash of the signature of the event."
    // Canonical types, such as uint256 have to be used in signatures.
    // 3. The signature is built by removing all argument names (only types are left)
    // 4. "indexed"  word must not be present
    // 5. Case is important: do not change upper/lower case
    // 6. The topic is the Keccak-256 hash digest of the signature.
    // 6. web3.sha3() IS NOT SHA3! It's Keccak-256. Solidity signatures use Keccak-256. NOT SHA3.
    //
    // Examples:
    // Incremented(bool indexed odd, uint x) -> Keccak-256("Incremented(bool,uint256)")
    //
    private final static String GET_VALUED_EVENT_SIGNATURE = "1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b";
    private final static String INCREMENT_METHOD_SIGNATURE = "371303c0";
    private final static String GET_VALUE_METHOD_SIGNATURE = "20965255";
    private final TestSystemProperties config = new TestSystemProperties();

    //20965255 getValue()
    //371303c0 inc()

    @Test
    public void newFilterInEmptyBlockchain() throws Exception {
        Web3Impl web3 = getWeb3();
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        String id = web3.eth_newFilter(fr);

        Assert.assertNotNull(id);
    }

    @Test
    public void newFilterGetLogsInEmptyBlockchain() throws Exception {
        Web3Impl web3 = getWeb3();
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        String id = web3.eth_newFilter(fr);
        Object[] logs = web3.eth_getFilterLogs(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void newFilterGetLogsAfterBlock() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), world.getBlockChain().getBlockStore(), null, null, null, 10, 100);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = world.getBlockChain().getRepository();
        eth.blockchain = world.getBlockChain();
        Web3Impl web3 = createWeb3(eth, world.getBlockChain(), transactionPool , WalletFactory.createWallet());

        // TODO tricky link to listener
        world.getBlockChain().setListener(web3.setupListener());

        web3.personal_newAccountWithSeed("notDefault");

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "latest";
        String id = web3.eth_newFilter(fr);

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        world.getBlockChain().tryToConnect(block1);

        Object[] logs = web3.eth_getFilterLogs(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(1, logs.length);

        Assert.assertEquals("0x" + tx.getContractAddress().toString(),((LogFilterElement)logs[0]).address);
    }

    @Test
    public void newFilterWithAccountAndTopicsCreatedAfterBlockAndGetLogs() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), world.getBlockChain().getBlockStore(), null, null, null, 10, 100);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = world.getBlockChain().getRepository();
        eth.blockchain = world.getBlockChain();
        Web3Impl web3 = createWeb3(eth, world.getBlockChain(), transactionPool, WalletFactory.createWallet());

        // TODO tricky link to listener
        world.getBlockChain().setListener(web3.setupListener());

        web3.personal_newAccountWithSeed("notDefault");

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        world.getBlockChain().tryToConnect(block1);

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.address = Hex.toHexString(tx.getContractAddress().getBytes());
        fr.topics = new Object[] { "06acbfb32bcf8383f3b0a768b70ac9ec234ea0f2d3b9c77fa6a2de69b919aad1" };
        String id = web3.eth_newFilter(fr);

        Object[] logs = web3.eth_getFilterLogs(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(1, logs.length);

        Assert.assertEquals("0x" + tx.getContractAddress().toString(),((LogFilterElement)logs[0]).address);
    }

    @Test
    public void newFilterGetLogsTwiceAfterBlock() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), world.getBlockChain().getBlockStore(), null, null, null, 10, 100);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = world.getBlockChain().getRepository();
        eth.blockchain = world.getBlockChain();
        Web3Impl web3 = createWeb3(eth, world.getBlockChain(), transactionPool, WalletFactory.createWallet());

        // TODO tricky link to listener
        world.getBlockChain().setListener(web3.setupListener());

        web3.personal_newAccountWithSeed("notDefault");

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        String id = web3.eth_newFilter(fr);

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        world.getBlockChain().tryToConnect(block1);

        web3.eth_getFilterLogs(id);
        Object[] logs = web3.eth_getFilterLogs(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(1, logs.length);

        Assert.assertEquals("0x" + tx.getContractAddress().toString(),((LogFilterElement)logs[0]).address);
    }

    @Test
    public void newFilterGetChangesInEmptyBlockchain() throws Exception {
        Web3Impl web3 = getWeb3();
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        String id = web3.eth_newFilter(fr);
        Object[] logs = web3.eth_getFilterChanges(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void newFilterGetChangesAfterBlock() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        BlockChainImpl blockChain = world.getBlockChain();
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), blockChain.getBlockStore(), null, null, null, 10, 100);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = world.getBlockChain().getRepository();
        eth.blockchain = world.getBlockChain();
        Web3Impl web3 = createWeb3(eth, world.getBlockChain(), transactionPool, WalletFactory.createWallet());

        // TODO tricky link to listener
        blockChain.setListener(web3.setupListener());

        web3.personal_newAccountWithSeed("notDefault");

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        String id = web3.eth_newFilter(fr);

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        blockChain.tryToConnect(block1);

        Object[] logs = web3.eth_getFilterChanges(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        // TODO Fix
        Assert.assertEquals(1, logs.length);

        Assert.assertEquals("0x" + tx.getContractAddress().toString(),((LogFilterElement)logs[0]).address);
    }

    @Test
    public void getLogsFromEmptyBlockchain() throws Exception {
        Web3Impl web3 = getWeb3();
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void getLogsFromBlockchainWithThreeEmptyBlocks() throws Exception {
        Web3Impl web3 = getWeb3WithThreeEmptyBlocks();

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void getLogsFromBlockchainWithContractCreation() throws Exception {
        Web3Impl web3 = getWeb3WithContractCreationWithoutEvents();

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void getLogsFromBlockchainWithEventInContractCreation() throws Exception {
        Web3Impl web3 = getWeb3WithEventInContractCreation();

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(1, logs.length);

        String txhash = ((LogFilterElement)logs[0]).transactionHash;
        TransactionReceiptDTO txdto = web3.eth_getTransactionReceipt(txhash);

        Assert.assertEquals(txdto.contractAddress,((LogFilterElement)logs[0]).address);
    }

    @Test
    public void getLogsFromBlockchainWithInvokeContract() throws Exception {
        Web3Impl web3 = getWeb3WithContractInvoke();

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(2, logs.length);

        String txhash = ((LogFilterElement)logs[0]).transactionHash;
        TransactionReceiptDTO txdto = web3.eth_getTransactionReceipt(txhash);

        Assert.assertEquals(txdto.contractAddress,((LogFilterElement)logs[0]).address);
        Assert.assertEquals(txdto.contractAddress,((LogFilterElement)logs[1]).address);
    }

    @Test
    public void getLogsFromBlockchainWithCallContract() throws Exception {
        World world = new World();
        Web3Impl web3 = getWeb3WithContractCall(world);

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(3, logs.length);
    }

    @Test
    public void getLogsFromBlockchainWithCallContractAndFilterByContractAddress() throws Exception {
        World world = new World();
        Web3Impl web3 = getWeb3WithContractCall(world);
        Block block1 = world.getBlockChain().getBlockByNumber(1l);
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        fr.address = Hex.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(3, logs.length);

        String address = "0x" + fr.address;

        Assert.assertEquals(address,((LogFilterElement)logs[0]).address);
        Assert.assertEquals(address,((LogFilterElement)logs[1]).address);
        Assert.assertEquals(address,((LogFilterElement)logs[2]).address);
    }

    @Test
    public void getLogsFromBlockchainWithCallContractAndFilterByUnknownContractAddress() throws Exception {
        World world = new World();
        Web3Impl web3 = getWeb3WithContractCall(world);

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        List<String> addresses = new ArrayList<>();
        addresses.add(Hex.toHexString(new byte[20]));
        fr.address = addresses;
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void getLogsFromBlockchainWithCallContractAndFilterByUnknownTopic() throws Exception {
        World world = new World();
        Web3Impl web3 = getWeb3WithContractCall(world);

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        fr.topics = new Object[1];
        fr.topics[0] = "0102030405060102030405060102030405060102030405060102030405060102";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void getLogsFromBlockchainWithCallContractAndFilterByKnownTopic() throws Exception {
        World world = new World();
        Web3Impl web3 = getWeb3WithContractCall(world);

        Block block1 = world.getBlockChain().getBlockByNumber(1l);
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        fr.topics = new Object[1];
        fr.topics[0] = GET_VALUED_EVENT_SIGNATURE;
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        String address = "0x" + Hex.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        Assert.assertEquals(1, logs.length);
        Assert.assertEquals(address,((LogFilterElement)logs[0]).address);
    }

    @Test
    public void getLogsFromBlockchainWithCallContractAndFilterByKnownTopicInList() throws Exception {
        World world = new World();
        Web3Impl web3 = getWeb3WithContractCall(world);
        Block block1 = world.getBlockChain().getBlockByNumber(1l);
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        fr.topics = new Object[1];
        List<String> topics = new ArrayList<>();
        topics.add(GET_VALUED_EVENT_SIGNATURE);
        fr.topics[0] = topics;
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        String address = "0x" + Hex.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        Assert.assertEquals(1, logs.length);
        Assert.assertEquals(address,((LogFilterElement)logs[0]).address);
    }

    @Test
    public void createMainContractWithoutEvents() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();


        BlockChainImpl blockChain = world.getBlockChain();
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), blockChain.getBlockStore(), null, null, null, 10, 100);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = world.getBlockChain().getRepository();
        eth.blockchain = world.getBlockChain();
        Web3Impl web3 = createWeb3(eth, world.getBlockChain(), transactionPool, WalletFactory.createWallet());

        // TODO tricky link to listener
        blockChain.setListener(web3.setupListener());

        web3.personal_newAccountWithSeed("notDefault");

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        String id = web3.eth_newFilter(fr);

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getMainContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        blockChain.tryToConnect(block1);

        Object[] logs = web3.eth_getFilterChanges(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void createCallerContractWithEvents() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        BlockChainImpl blockChain = world.getBlockChain();
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), blockChain.getBlockStore(), null, null, null, 10, 100);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = world.getBlockChain().getRepository();
        eth.blockchain = world.getBlockChain();
        Web3Impl web3 = createWeb3(eth, world.getBlockChain(), transactionPool, WalletFactory.createWallet());

        // TODO tricky link to listener
        blockChain.setListener(web3.setupListener());

        web3.personal_newAccountWithSeed("notDefault");

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        String id = web3.eth_newFilter(fr);

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getMainContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        blockChain.tryToConnect(block1);

        String mainAddress = tx.getContractAddress().toString();

        Transaction tx2;
        tx2 = getCallerContractTransaction(acc1, mainAddress);
        String callerAddress = Hex.toHexString(tx2.getContractAddress().getBytes());

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(tx2);
        Block block2 = new BlockBuilder(world).parent(block1).transactions(txs2).build();
        blockChain.tryToConnect(block2);

        Object[] logs = web3.eth_getFilterChanges(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(2, logs.length);

        Assert.assertEquals("0x" + mainAddress, ((LogFilterElement)logs[0]).address);
        Assert.assertEquals("0x" + callerAddress, ((LogFilterElement)logs[1]).address);
    }

    @Test
    public void createCallerContractWithEventsOnInvoke() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        BlockChainImpl blockChain = world.getBlockChain();
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), blockChain.getBlockStore(), null, null, null, 10, 100);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = world.getBlockChain().getRepository();
        eth.blockchain = world.getBlockChain();
        Web3Impl web3 = createWeb3(eth, world.getBlockChain(), transactionPool, WalletFactory.createWallet());

        // TODO tricky link to listener
        blockChain.setListener(web3.setupListener());

        web3.personal_newAccountWithSeed("notDefault");

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        String id = web3.eth_newFilter(fr);

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getMainContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        blockChain.tryToConnect(block1);

        String mainAddress = tx.getContractAddress().toString();

        Transaction tx2;
        tx2 = getCallerContractTransaction(acc1, mainAddress);
        String callerAddress = Hex.toHexString(tx2.getContractAddress().getBytes());

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(tx2);
        Block block2 = new BlockBuilder(world).parent(block1).transactions(txs2).build();
        blockChain.tryToConnect(block2);

        Transaction tx3;
        tx3 = getCallerContractTransactionWithInvoke(acc1, tx2.getContractAddress().getBytes(), mainAddress);

        List<Transaction> txs3 = new ArrayList<>();
        txs3.add(tx3);
        Block block3 = new BlockBuilder(world).parent(block2).transactions(txs3).build();
        blockChain.tryToConnect(block3);

        Object[] logs = web3.eth_getFilterChanges(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(3, logs.length);

        Assert.assertEquals("0x" + mainAddress, ((LogFilterElement)logs[0]).address);
        Assert.assertEquals("0x" + callerAddress, ((LogFilterElement)logs[1]).address);
        Assert.assertEquals("0x" + mainAddress, ((LogFilterElement)logs[2]).address);
    }

    @Test
    public void createCallerContractWithEventsOnInvokeUsingGetFilterLogs() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), world.getBlockChain().getBlockStore(), null, null, null, 10, 100);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = world.getBlockChain().getRepository();
        eth.blockchain = world.getBlockChain();
        Web3Impl web3 = createWeb3(eth, world.getBlockChain(), transactionPool, WalletFactory.createWallet());

        // TODO tricky link to listener
        world.getBlockChain().setListener(web3.setupListener());

        web3.personal_newAccountWithSeed("notDefault");

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getMainContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        world.getBlockChain().tryToConnect(block1);

        String mainAddress = tx.getContractAddress().toString();

        Transaction tx2;
        tx2 = getCallerContractTransaction(acc1, mainAddress);
        String callerAddress = Hex.toHexString(tx2.getContractAddress().getBytes());

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(tx2);
        Block block2 = new BlockBuilder(world).parent(block1).transactions(txs2).build();
        world.getBlockChain().tryToConnect(block2);

        Transaction tx3;
        tx3 = getCallerContractTransactionWithInvoke(acc1, tx2.getContractAddress().getBytes(), mainAddress);

        List<Transaction> txs3 = new ArrayList<>();
        txs3.add(tx3);
        Block block3 = new BlockBuilder(world).parent(block2).transactions(txs3).build();
        world.getBlockChain().tryToConnect(block3);

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.address = "0x" + mainAddress;
        String id = web3.eth_newFilter(fr);

        Object[] logs = web3.eth_getFilterLogs(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(1, logs.length);

        Assert.assertEquals("0x" + mainAddress, ((LogFilterElement)logs[0]).address);
    }

    private Web3Impl createWeb3(Blockchain blockchain, TransactionPool transactionPool) {
        return createWeb3(blockchain, transactionPool, null);
    }

    private Web3Impl createWeb3(Blockchain blockchain, TransactionPool transactionPool, ReceiptStore receiptStore) {
        return createWeb3(Web3Mocks.getMockEthereum(), blockchain, transactionPool, receiptStore, WalletFactory.createWallet());
    }

    private Web3Impl createWeb3(Ethereum eth, Blockchain blockchain, TransactionPool transactionPool, Wallet wallet) {
        return createWeb3(eth, blockchain, transactionPool, null, wallet);
    }

    private Web3Impl createWeb3(Ethereum eth, Blockchain blockchain, TransactionPool transactionPool, ReceiptStore receiptStore, Wallet wallet) {
        PersonalModule personalModule = new PersonalModuleWalletEnabled(config, eth, wallet, null);
        EthModule ethModule = new EthModule(config, blockchain, null, new ExecutionBlockRetriever(blockchain, null, null), new EthModuleSolidityDisabled(), new EthModuleWalletEnabled(config, eth, wallet, null));
        TxPoolModule txPoolModule = new TxPoolModuleImpl(Web3Mocks.getMockTransactionPool());
        return new Web3RskImpl(
                eth,
                blockchain,
                transactionPool,
                config,
                Web3Mocks.getMockMinerClient(),
                Web3Mocks.getMockMinerServer(),
                personalModule,
                ethModule,
                txPoolModule,
                Web3Mocks.getMockChannelManager(),
                Web3Mocks.getMockRepository(),
                null,
                null,
                blockchain.getBlockStore(),
                receiptStore,
                null,
                null,
                null,
                new SimpleConfigCapabilities()
        );
    }

    private Web3Impl getWeb3() {
        World world = new World();
        Web3Impl web3 = createWeb3(world.getBlockChain(), null);

        return web3;
    }

    private Web3Impl getWeb3WithThreeEmptyBlocks() {
        World world = new World();
        Web3Impl web3 = createWeb3(world.getBlockChain(), null);

        Block genesis = world.getBlockByName("g00");
        Block block1 = new BlockBuilder().parent(genesis).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block2 = new BlockBuilder().parent(block1).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2));

        return web3;
    }

    private String compiledGreeter = "60606040525b33600060006101000a81548173ffffffffffffffffffffffffffffffffffffffff02191690836c010000000000000000000000009081020402179055505b610181806100516000396000f360606040526000357c010000000000000000000000000000000000000000000000000000000090048063ead710c41461003c57610037565b610002565b34610002576100956004808035906020019082018035906020019191908080601f016020809104026020016040519081016040528093929190818152602001838380828437820191505050505050909091905050610103565b60405180806020018281038252838181518152602001915080519060200190808383829060006004602084601f0104600302600f01f150905090810190601f1680156100f55780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b6020604051908101604052806000815260200150600060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff1614151561017357610002565b81905061017b565b5b91905056";

    private Web3Impl getWeb3WithContractCreationWithoutEvents() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");

        /* contract compiled in data attribute of tx
        contract greeter {

            address owner;
            modifier onlyOwner { if (msg.sender != owner) throw; _ ; }

            function greeter() public {
                owner = msg.sender;
            }
            function greet(string param) onlyOwner constant returns (string) {
                return param;
            }
        } */
        Transaction tx = new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data(compiledGreeter)
                .build();

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        BlockChainImpl blockChain = world.getBlockChain();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        blockChain.tryToConnect(block1);

        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), blockChain.getBlockStore(), null, null, null, 10, 100);

        Web3Impl web3 = createWeb3(world.getBlockChain(), transactionPool);
        web3.personal_newAccountWithSeed("notDefault");

        return web3;
    }

    private Web3Impl getWeb3WithEventInContractCreation() {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = getWorld3WithBlockWithEventInContractCreation(config, receiptStore);

        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), world.getBlockChain().getBlockStore(), receiptStore, null, null, 10, 100);

        Web3Impl web3 = createWeb3(world.getBlockChain(), transactionPool, receiptStore);
        web3.personal_newAccountWithSeed("notDefault");

        return web3;
    }

    public static World getWorld3WithBlockWithEventInContractCreation(TestSystemProperties config) {
        return getWorld3WithBlockWithEventInContractCreation(config, null);
    }

    public static World getWorld3WithBlockWithEventInContractCreation(TestSystemProperties config, ReceiptStore receiptStore) {
        World world = new World(receiptStore);
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        BlockChainImpl blockChain = world.getBlockChain();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        blockChain.tryToConnect(block1);

        return world;
    }

    private Web3Impl getWeb3WithContractInvoke() {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        BlockChainImpl blockChain = world.getBlockChain();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        byte[] contractAddress = tx.getContractAddress().getBytes();

        Transaction tx2 = getContractTransactionWithInvoke(acc1, contractAddress);
        List<Transaction> tx2s = new ArrayList<>();
        tx2s.add(tx2);
        Block block2 = new BlockBuilder(world).parent(block1).transactions(tx2s).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));

        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), blockChain.getBlockStore(), receiptStore, null, null, 10, 100);

        Web3Impl web3 = createWeb3(world.getBlockChain(), transactionPool, receiptStore);
        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");
        return web3;
    }

    private Web3Impl getWeb3WithContractCall(World world) {
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();
        // acc1 Account created address should be 661b05ca9eb621164906671efd2731ce0d7dd8b4

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        BlockChainImpl blockChain = world.getBlockChain();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        byte[] contractAddress = tx.getContractAddress().getBytes();

        // Now create a transaction that invokes Increment()
        Transaction tx2 = getContractTransactionWithInvoke(acc1, contractAddress);
        List<Transaction> tx2s = new ArrayList<>();
        tx2s.add(tx2);
        Block block2 = new BlockBuilder(world).parent(block1).transactions(tx2s).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));

        Transaction tx3 = getContractTransactionWithCall(acc1, contractAddress);
        List<Transaction> tx3s = new ArrayList<>();
        tx3s.add(tx3);
        Block block3 = new BlockBuilder(world).parent(block2).transactions(tx3s).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block3));

        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), blockChain.getBlockStore(), null, null, null, 10, 100);

        Web3Impl web3 = createWeb3(world.getBlockChain(), transactionPool);
        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");

        return web3;
    }

    private static Transaction getContractTransaction(Account acc1) {
        return getContractTransaction(acc1,false);
    }

    //0.4.11+commit.68ef5810.Emscripten.clang WITH optimizations
    static final String compiled_0_4_11 = "6060604052341561000c57fe5b5b60466000819055507f06acbfb32bcf8383f3b0a768b70ac9ec234ea0f2d3b9c77fa6a2de69b919aad16000546040518082815260200191505060405180910390a15b5b61014e8061005f6000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680632096525514610046578063371303c01461006c575bfe5b341561004e57fe5b61005661007e565b6040518082815260200191505060405180910390f35b341561007457fe5b61007c6100c2565b005b60007f1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b6000546040518082815260200191505060405180910390a160005490505b90565b60006000815460010191905081905550600160026000548115156100e257fe5b061415157f6e61ef44ac2747ff8b84d353a908eb8bd5c3fb118334d57698c5cfc7041196ad6000546040518082815260200191505060405180910390a25b5600a165627a7a7230582092c7b2c0483b85227396e18149993b33243059af0f3bd0364f1dc36b8bbbcdae0029";
    static final String compiled_unknown = "60606040526046600081905560609081527f06acbfb32bcf8383f3b0a768b70ac9ec234ea0f2d3b9c77fa6a2de69b919aad190602090a160aa8060426000396000f3606060405260e060020a60003504632096525581146024578063371303c0146060575b005b60a36000805460609081527f1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b90602090a1600060005054905090565b6022600080546001908101918290556060828152600290920614907f6e61ef44ac2747ff8b84d353a908eb8bd5c3fb118334d57698c5cfc7041196ad90602090a2565b5060206060f3";

    private static Transaction getContractTransaction(Account acc1,boolean withEvent) {
    /* contract compiled in data attribute of tx
    contract counter {
        event Incremented(bool indexed odd, uint x);
        event Created(uint x);
        event Valued(uint x);

        function counter() {
            x = 70;
            Created(x); // this is logged in initialization code (not left in contract code afterwards)
        }

        function inc() {
            ++x;
            Incremented(x % 2 == 1, x);
        }

        function getValue() constant returns (uint) {
            Valued(x);
            return x;
        }

        uint x;
    } */
        return new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(1000000))
                .gasPrice(BigInteger.ONE)
                .data(compiled_0_4_11)
                .build();
    }

    private static Transaction getContractTransactionWithInvoke(Account acc1, byte[] receiverAddress) {
        return new TransactionBuilder()
                .sender(acc1)
                .receiverAddress(receiverAddress)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data(INCREMENT_METHOD_SIGNATURE)   // invoke incr()
                .nonce(1)
                .build();
    }

    private static Transaction getContractTransactionWithCall(Account acc1, byte[] receiverAddress) {
        return new TransactionBuilder()
                .sender(acc1)
                .receiverAddress(receiverAddress)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data(GET_VALUE_METHOD_SIGNATURE)   // call getValue()
                .nonce(2)
                .build();
    }

    String compiledLogExample ="606060405234610000575b60bd806100186000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063195977a614603c575b6000565b34600057605460048080359060200190919050506056565b005b7ffd99bb34477b313b3e3b452b34d012d8315db36a1d63949d9d8f9d2573b05aff816040518082815260200191505060405180910390a15b505600a165627a7a72305820fb2550735b0655fb2fe03738be375a4c29ef1b6ff51004f869be19de0301f30b0029";
    private Transaction getMainContractTransaction(Account acc1) {
    /* contract compiled in data attribute of tx
contract main {
    event LogExample(uint numb);

    function emit(uint n){
        LogExample(n);
    }
}
} */

        return new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(1000000))
                .gasPrice(BigInteger.ONE)
                .data(compiledLogExample)
                .build();
    }

    private static Transaction getCallerContractTransaction(Account acc1, String mainAddress) {
        String address = mainAddress;

        while (address.length() < 64)
            address = "0" + address;

    /* contract compiled in data attribute of tx
contract caller {
    event LogNumber(uint numb);

    function caller(address mainAddr) {
        main(mainAddr).emit(12345);
		LogNumber(123);
    }

    function doSomething(address mainAddr) {
        main(mainAddr).emit(12346);
    }
}
} */

        String compiledCaller = "606060405234610000576040516020806101f8833981016040528080519060200190919050505b8073ffffffffffffffffffffffffffffffffffffffff1663195977a66130396040518263ffffffff167c010000000000000000000000000000000000000000000000000000000002815260040180828152602001915050600060405180830381600087803b156100005760325a03f115610000575050507f2012ef02e82e91abf55727cc31c3b6e3375003aa9e879f855db72d9e78822c40607b6040518082815260200191505060405180910390a15b505b610111806100e76000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063e60c2d4414603c575b6000565b34600057606a600480803573ffffffffffffffffffffffffffffffffffffffff16906020019091905050606c565b005b8073ffffffffffffffffffffffffffffffffffffffff1663195977a661303a6040518263ffffffff167c010000000000000000000000000000000000000000000000000000000002815260040180828152602001915050600060405180830381600087803b1560005760325a03f1156000575050505b505600a165627a7a72305820f8bc730651ba568de3f84a81088f94a8701c5c41f732d5c7a447077ee40f97a80029";
        return new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(1000000))
                .gasPrice(BigInteger.ONE)
                .data( compiledCaller + address)
                .nonce(1)
                .build();
    }

    private static Transaction getCallerContractTransactionWithInvoke(Account acc1, byte[] receiverAddress, String mainAddress) {
        String address = mainAddress;

        while (address.length() < 64)
            address = "0" + address;

        CallTransaction.Function func = CallTransaction.Function.fromSignature("doSomething", new String[] { "address" }, new String[0]);

        return new TransactionBuilder()
                .sender(acc1)
                .receiverAddress(receiverAddress)
                .gasLimit(BigInteger.valueOf(1000000))
                .gasPrice(BigInteger.ONE)
                .data(Hex.toHexString(func.encode("0x" + address)))
                .nonce(2)
                .build();
    }
}
