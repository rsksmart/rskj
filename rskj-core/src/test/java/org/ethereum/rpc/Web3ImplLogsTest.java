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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Wallet;
import co.rsk.core.WalletFactory;
import co.rsk.core.bc.PendingStateImpl;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleSolidityDisabled;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.Repository;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.ethereum.rpc.Simples.SimpleWorldManager;
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
    @Test
    public void newFilterInEmptyBlockchain() throws Exception {
        Web3Impl web3 = getWeb3();
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        String id = web3.eth_newFilter(fr);

        Assert.assertNotNull(id);
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
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        worldManager.setBlockStore(world.getBlockChain().getBlockStore());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = (Repository) world.getBlockChain().getRepository();
        eth.worldManager = worldManager;
        Web3Impl web3 = createWeb3(eth, WalletFactory.createPersistentWallet("wallet"));

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

        Object[] logs = web3.eth_getFilterChanges(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        // TODO Fix
        Assert.assertEquals(1, logs.length);

        Assert.assertEquals("0x" + Hex.toHexString(tx.getContractAddress()),((LogFilterElement)logs[0]).address);
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
        Web3Impl web3 = getWeb3WithContractCall();

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(3, logs.length);
    }

    @Test
    public void getLogsFromBlockchainWithCallContractAndFilterByContractAddress() throws Exception {
        Web3Impl web3 = getWeb3WithContractCall();
        Block block1 = web3.worldManager.getBlockchain().getBlockByNumber(1l);
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        fr.address = Hex.toHexString(block1.getTransactionsList().get(0).getContractAddress());
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
        Web3Impl web3 = getWeb3WithContractCall();

        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        List<String> addresses = new ArrayList<>();
        addresses.add(Hex.toHexString(new byte[] { 1, 2, 3 }));
        fr.address = addresses;
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void getLogsFromBlockchainWithCallContractAndFilterByUnknownTopic() throws Exception {
        Web3Impl web3 = getWeb3WithContractCall();
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        fr.topics = new Object[1];
        fr.topics[0] = "0102";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void getLogsFromBlockchainWithCallContractAndFilterByKnownTopic() throws Exception {
        Web3Impl web3 = getWeb3WithContractCall();
        Block block1 = web3.worldManager.getBlockchain().getBlockByNumber(1l);
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        fr.topics = new Object[1];
        fr.topics[0] = "1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b";
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        String address = "0x" + Hex.toHexString(block1.getTransactionsList().get(0).getContractAddress());
        Assert.assertEquals(1, logs.length);
        Assert.assertEquals(address,((LogFilterElement)logs[0]).address);
    }

    @Test
    public void getLogsFromBlockchainWithCallContractAndFilterByKnownTopicInList() throws Exception {
        Web3Impl web3 = getWeb3WithContractCall();
        Block block1 = web3.worldManager.getBlockchain().getBlockByNumber(1l);
        Web3.FilterRequest fr = new Web3.FilterRequest();
        fr.fromBlock = "earliest";
        fr.topics = new Object[1];
        List<String> topics = new ArrayList<>();
        topics.add("1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b");
        fr.topics[0] = topics;
        Object[] logs = web3.eth_getLogs(fr);

        Assert.assertNotNull(logs);
        String address = "0x" + Hex.toHexString(block1.getTransactionsList().get(0).getContractAddress());
        Assert.assertEquals(1, logs.length);
        Assert.assertEquals(address,((LogFilterElement)logs[0]).address);
    }

    @Test
    public void createMainContractWithoutEvents() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        worldManager.setBlockStore(world.getBlockChain().getBlockStore());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = (Repository) world.getBlockChain().getRepository();
        eth.worldManager = worldManager;
        Web3Impl web3 = createWeb3(eth, WalletFactory.createPersistentWallet("testwallet"));

        // TODO tricky link to listener
        world.getBlockChain().setListener(web3.setupListener());

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
        world.getBlockChain().tryToConnect(block1);

        Object[] logs = web3.eth_getFilterChanges(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(0, logs.length);
    }

    @Test
    public void createCallerContractWithEvents() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        worldManager.setBlockStore(world.getBlockChain().getBlockStore());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = (Repository) world.getBlockChain().getRepository();
        eth.worldManager = worldManager;
        Web3Impl web3 = createWeb3(eth, WalletFactory.createPersistentWallet("testwallet2"));

        // TODO tricky link to listener
        world.getBlockChain().setListener(web3.setupListener());

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
        world.getBlockChain().tryToConnect(block1);

        String mainAddress = Hex.toHexString(tx.getContractAddress());

        Transaction tx2;
        tx2 = getCallerContractTransaction(acc1, mainAddress);

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(tx2);
        Block block2 = new BlockBuilder(world).parent(block1).transactions(txs2).build();
        world.getBlockChain().tryToConnect(block2);

        Object[] logs = web3.eth_getFilterChanges(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(4, logs.length);

        for (int k = 0; k < 4; k++) {
            LogFilterElement log = (LogFilterElement)logs[0];
            if (k % 2 == 0)
                Assert.assertEquals("0x" + mainAddress, ((LogFilterElement)log).address);
        }
    }

    @Test
    public void createCallerContractWithEventsOnInvoke() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        worldManager.setBlockStore(world.getBlockChain().getBlockStore());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);

        SimpleEthereum eth = new SimpleEthereum();
        eth.repository = (Repository) world.getBlockChain().getRepository();
        eth.worldManager = worldManager;
        Web3Impl web3 = createWeb3(eth, WalletFactory.createPersistentWallet("testwallet3"));

        // TODO tricky link to listener
        world.getBlockChain().setListener(web3.setupListener());

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
        world.getBlockChain().tryToConnect(block1);

        String mainAddress = Hex.toHexString(tx.getContractAddress());

        Transaction tx2;
        tx2 = getCallerContractTransaction(acc1, mainAddress);

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(tx2);
        Block block2 = new BlockBuilder(world).parent(block1).transactions(txs2).build();
        world.getBlockChain().tryToConnect(block2);

        Transaction tx3;
        tx3 = getCallerContractTransactionWithInvoke(acc1, tx2.getContractAddress(), mainAddress);

        List<Transaction> txs3 = new ArrayList<>();
        txs3.add(tx3);
        Block block3 = new BlockBuilder(world).parent(block2).transactions(txs3).build();
        world.getBlockChain().tryToConnect(block3);

        Object[] logs = web3.eth_getFilterChanges(id);

        Assert.assertNotNull(id);
        Assert.assertNotNull(logs);
        Assert.assertEquals(5, logs.length);

        for (int k = 0; k < 5; k++) {
            LogFilterElement log = (LogFilterElement)logs[0];
            if (k % 2 == 0 || k == 4)
                Assert.assertEquals("0x" + mainAddress, ((LogFilterElement)log).address);
        }
    }

    private Web3Impl createWeb3() {
        return createWeb3(Web3Mocks.getMockEthereum(), WalletFactory.createWallet());
    }

    private Web3Impl createWeb3(Ethereum eth, Wallet wallet) {
        PersonalModule personalModule = new PersonalModuleWalletEnabled(eth, wallet);
        EthModule ethModule = new EthModule(eth, new EthModuleSolidityDisabled(), new EthModuleWalletEnabled(eth, wallet));
        return new Web3RskImpl(eth, RskSystemProperties.CONFIG, Web3Mocks.getMockMinerClient(), Web3Mocks.getMockMinerServer(), personalModule, ethModule, Web3Mocks.getMockChannelManager());
    }

    private Web3Impl getWeb3() {
        World world = new World();
        Web3Impl web3 = createWeb3();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        web3.worldManager = worldManager;
        return web3;
    }

    private Web3Impl getWeb3WithThreeEmptyBlocks() {
        World world = new World();
        Web3Impl web3 = createWeb3();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");
        Block block1 = new BlockBuilder().parent(genesis).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block2 = new BlockBuilder().parent(block1).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2));

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        web3.worldManager = worldManager;
        return web3;
    }

    private Web3Impl getWeb3WithContractCreationWithoutEvents() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

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
                .data("60606040525b33600060006101000a81548173ffffffffffffffffffffffffffffffffffffffff02191690836c010000000000000000000000009081020402179055505b610181806100516000396000f360606040526000357c010000000000000000000000000000000000000000000000000000000090048063ead710c41461003c57610037565b610002565b34610002576100956004808035906020019082018035906020019191908080601f016020809104026020016040519081016040528093929190818152602001838380828437820191505050505050909091905050610103565b60405180806020018281038252838181518152602001915080519060200190808383829060006004602084601f0104600302600f01f150905090810190601f1680156100f55780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b6020604051908101604052806000815260200150600060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff1614151561017357610002565b81905061017b565b5b91905056")
                .build();

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        world.getBlockChain().tryToConnect(block1);

        Web3Impl web3 = createWeb3();
        web3.personal_newAccountWithSeed("notDefault");

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);
        web3.worldManager = worldManager;
        return web3;
    }

    private Web3Impl getWeb3WithEventInContractCreation() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        world.getBlockChain().tryToConnect(block1);

        Web3Impl web3 = createWeb3();
        web3.personal_newAccountWithSeed("notDefault");

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        worldManager.setBlockStore(world.getBlockChain().getBlockStore());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);
        web3.worldManager = worldManager;
        return web3;
    }

    private Web3Impl getWeb3WithContractInvoke() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        byte[] contractAddress = tx.getContractAddress();

        Transaction tx2 = getContractTransactionWithInvoke(acc1, contractAddress);
        List<Transaction> tx2s = new ArrayList<>();
        tx2s.add(tx2);
        Block block2 = new BlockBuilder(world).parent(block1).transactions(tx2s).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2));

        Web3Impl web3 = createWeb3();
        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        worldManager.setBlockStore(world.getBlockChain().getBlockStore());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);
        web3.worldManager = worldManager;
        return web3;
    }

    private Web3Impl getWeb3WithContractCall() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(BigInteger.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        byte[] contractAddress = tx.getContractAddress();

        Transaction tx2 = getContractTransactionWithInvoke(acc1, contractAddress);
        List<Transaction> tx2s = new ArrayList<>();
        tx2s.add(tx2);
        Block block2 = new BlockBuilder(world).parent(block1).transactions(tx2s).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2));

        Transaction tx3 = getContractTransactionWithCall(acc1, contractAddress);
        List<Transaction> tx3s = new ArrayList<>();
        tx3s.add(tx3);
        Block block3 = new BlockBuilder(world).parent(block2).transactions(tx3s).build();
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block3));

        Web3Impl web3 = createWeb3();
        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        worldManager.setBlockStore(world.getBlockChain().getBlockStore());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);
        web3.worldManager = worldManager;
        return web3;
    }

    private Transaction getContractTransaction(Account acc1) {
    /* contract compiled in data attribute of tx
    contract counter {
        event Incremented(bool indexed odd, uint x);
        event Created(uint x);

        function counter() {
            x = 70;
            Created(x);
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
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data("60606040526046600081905560609081527f06acbfb32bcf8383f3b0a768b70ac9ec234ea0f2d3b9c77fa6a2de69b919aad190602090a160aa8060426000396000f3606060405260e060020a60003504632096525581146024578063371303c0146060575b005b60a36000805460609081527f1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b90602090a1600060005054905090565b6022600080546001908101918290556060828152600290920614907f6e61ef44ac2747ff8b84d353a908eb8bd5c3fb118334d57698c5cfc7041196ad90602090a2565b5060206060f3")
                .build();
    }

    private Transaction getContractTransactionWithInvoke(Account acc1, byte[] receiverAddress) {
        return new TransactionBuilder()
                .sender(acc1)
                .receiverAddress(receiverAddress)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data("371303c0")   // invoke incr()
                .nonce(1)
                .build();
    }

    private Transaction getContractTransactionWithCall(Account acc1, byte[] receiverAddress) {
        return new TransactionBuilder()
                .sender(acc1)
                .receiverAddress(receiverAddress)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data("20965255")   // call getValue()
                .nonce(2)
                .build();
    }

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
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data("606060405234610000575b60bd806100186000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063195977a614603c575b6000565b34600057605460048080359060200190919050506056565b005b7ffd99bb34477b313b3e3b452b34d012d8315db36a1d63949d9d8f9d2573b05aff816040518082815260200191505060405180910390a15b505600a165627a7a72305820fb2550735b0655fb2fe03738be375a4c29ef1b6ff51004f869be19de0301f30b0029")
                .build();
    }

    private Transaction getCallerContractTransaction(Account acc1, String mainAddress) {
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

        return new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(1000000))
                .gasPrice(BigInteger.ONE)
                .data("606060405234610000576040516020806101f8833981016040528080519060200190919050505b8073ffffffffffffffffffffffffffffffffffffffff1663195977a66130396040518263ffffffff167c010000000000000000000000000000000000000000000000000000000002815260040180828152602001915050600060405180830381600087803b156100005760325a03f115610000575050507f2012ef02e82e91abf55727cc31c3b6e3375003aa9e879f855db72d9e78822c40607b6040518082815260200191505060405180910390a15b505b610111806100e76000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063e60c2d4414603c575b6000565b34600057606a600480803573ffffffffffffffffffffffffffffffffffffffff16906020019091905050606c565b005b8073ffffffffffffffffffffffffffffffffffffffff1663195977a661303a6040518263ffffffff167c010000000000000000000000000000000000000000000000000000000002815260040180828152602001915050600060405180830381600087803b1560005760325a03f1156000575050505b505600a165627a7a72305820f8bc730651ba568de3f84a81088f94a8701c5c41f732d5c7a447077ee40f97a80029" + address)
                .nonce(1)
                .build();
    }

    private Transaction getCallerContractTransactionWithInvoke(Account acc1, byte[] receiverAddress, String mainAddress) {
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
