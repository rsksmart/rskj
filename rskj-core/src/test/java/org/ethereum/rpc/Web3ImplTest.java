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
import co.rsk.mine.MinerClient;
import co.rsk.net.simples.SimpleBlockProcessor;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleSolidityDisabled;
import co.rsk.rpc.modules.eth.EthModuleSolidityEnabled;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletDisabled;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.TestContract;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.Repository;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.rpc.Simples.*;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.ethereum.rpc.exception.JsonRpcInvalidParamException;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.spongycastle.util.encoders.Hex;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

/**
 * Created by Ruben Altman on 09/06/2016.
 */
public class Web3ImplTest {

    Wallet wallet;

    @Test
    public void web3_clientVersion() throws Exception {
        Web3 web3 = createWeb3();

        String clientVersion = web3.web3_clientVersion();

        Assert.assertTrue("client version is not including rsk!", clientVersion.toLowerCase().contains("rsk"));
    }

    @Test
    public void net_version() throws Exception {
        Web3Impl web3 = createWeb3();
        web3.worldManager = new SimpleWorldManager();

        String netVersion = web3.net_version();

        Assert.assertTrue("RSK net version different than expected", netVersion.compareTo(Byte.toString(RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getChainId())) == 0);
    }

    @Test
    public void eth_protocolVersion() throws Exception {
        Web3Impl web3 = createWeb3();
        web3.worldManager = new SimpleWorldManager();

        String netVersion = web3.eth_protocolVersion();

        Assert.assertTrue("RSK net version different than one", netVersion.compareTo("1") == 0);
    }


    @Test
    public void net_peerCount() throws Exception {
        Web3Impl web3 = createWeb3();

        String peerCount  = web3.net_peerCount();

        Assert.assertEquals("Different number of peers than expected",
                "0x2", peerCount);
    }


    @Test
    public void web3_sha3() throws Exception {
        String toHash = "RSK";

        Web3 web3 = createWeb3();

        String result = web3.web3_sha3(toHash);

        // Function must apply the Keccak-256 algorithm
        // Result taken from https://emn178.github.io/online-tools/keccak_256.html
        Assert.assertTrue("hash does not match", result.compareTo("0x80553b6b348ae45ab8e8bf75e77064818c0a772f13cf8d3a175d3815aec59b56") == 0);
    }

    @Test
    public void eth_syncing_returnFalseWhenNotSyncing()  {
        Web3Impl web3 = createWeb3();
        web3.worldManager = new SimpleWorldManager();
        SimpleBlockProcessor nodeProcessor = new SimpleBlockProcessor();
        web3.worldManager = new SimpleWorldManager(nodeProcessor);
        nodeProcessor.lastKnownBlockNumber = 0;

        Object result = web3.eth_syncing();

        Assert.assertTrue("Node is not syncing, must return false", !(boolean)result);
    }

    @Test
    public void eth_syncing_returnSyncingResultWhenSyncing()  {

        Web3Impl web3 = createWeb3();
        web3.worldManager = new SimpleWorldManager();
        SimpleBlockProcessor nodeProcessor = new SimpleBlockProcessor();
        web3.worldManager = new SimpleWorldManager(nodeProcessor);
        nodeProcessor.lastKnownBlockNumber = 5;

        Object result = web3.eth_syncing();

        Assert.assertTrue("Node is syncing, must return sync manager", result instanceof Web3.SyncingResult);
        Assert.assertTrue("Highest block is 5", ((Web3.SyncingResult)result).highestBlock.compareTo("0x5") == 0);
        Assert.assertTrue("Simple blockchain starts from genesis block", ((Web3.SyncingResult)result).currentBlock.compareTo("0x0") == 0);
    }

    @Test
    public void getBalanceWithAccount() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(10000)).build();

        Web3Impl web3 = createWeb3();

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        web3.worldManager = worldManager;

        org.junit.Assert.assertEquals("0x" + Hex.toHexString(BigInteger.valueOf(10000).toByteArray()), web3.eth_getBalance(Hex.toHexString(acc1.getAddress())));
    }

    @Test
    public void getBalanceWithAccountAndLatestBlock() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(10000)).build();

        Web3Impl web3 = createWeb3();

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        web3.worldManager = worldManager;

        org.junit.Assert.assertEquals("0x" + Hex.toHexString(BigInteger.valueOf(10000).toByteArray()), web3.eth_getBalance(Hex.toHexString(acc1.getAddress()), "latest"));
    }

    @Test
    public void getBalanceWithAccountAndGenesisBlock() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(10000)).build();

        Web3Impl web3 = createWeb3();

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        web3.worldManager = worldManager;

        String accountAddress = Hex.toHexString(acc1.getAddress());
        String balanceString = "0x" + Hex.toHexString(BigInteger.valueOf(10000).toByteArray());

        org.junit.Assert.assertEquals(balanceString, web3.eth_getBalance(accountAddress, "0x0"));
    }

    @Test
    public void getBalanceWithAccountAndBlock() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(10000)).build();
        Block genesis = world.getBlockByName("g00");

        Block block1 = new BlockBuilder().parent(genesis).build();
        world.getBlockChain().tryToConnect(block1);

        Web3Impl web3 = createWeb3();

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        web3.worldManager = worldManager;

        String accountAddress = Hex.toHexString(acc1.getAddress());
        String balanceString = "0x" + Hex.toHexString(BigInteger.valueOf(10000).toByteArray());

        org.junit.Assert.assertEquals(balanceString, web3.eth_getBalance(accountAddress, "0x1"));
    }

    @Test
    public void getBalanceWithAccountAndBlockWithTransaction() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(10000000)).build();
        Account acc2 = new AccountBuilder(world).name("acc2").build();
        Block genesis = world.getBlockByName("g00");

        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(10000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        Web3Impl web3 = createWeb3();

        web3.repository = (Repository) world.getBlockChain().getRepository();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);
        web3.worldManager = worldManager;

        String accountAddress = Hex.toHexString(acc2.getAddress());
        String balanceString = "0x" + Hex.toHexString(BigInteger.valueOf(10000).toByteArray());

        org.junit.Assert.assertEquals("0x0", web3.eth_getBalance(accountAddress, "0x0"));
        org.junit.Assert.assertEquals(balanceString, web3.eth_getBalance(accountAddress, "0x1"));
        org.junit.Assert.assertEquals(balanceString, web3.eth_getBalance(accountAddress, "pending"));
    }

    @Test
    public void eth_mining()  {
        Ethereum ethMock = Web3Mocks.getMockEthereum();
        RskSystemProperties mockProperties = Web3Mocks.getMockProperties();
        MinerClient minerClient = new SimpleMinerClient();
        PersonalModule personalModule = new PersonalModuleWalletDisabled();
        Web3 web3 = new Web3Impl(ethMock, mockProperties, minerClient, null, personalModule, null, Web3Mocks.getMockChannelManager());

        Assert.assertTrue("Node is not mining", !web3.eth_mining());

        minerClient.mine();
        Assert.assertTrue("Node is mining", web3.eth_mining());

        minerClient.stop();
        Assert.assertTrue("Node is not mining", !web3.eth_mining());
    }

    @Test
    public void getGasPrice()  {
        Web3Impl web3 = createWeb3();
        web3.eth = new SimpleEthereum();
        String expectedValue = Hex.toHexString(new BigInteger("20000000000").toByteArray());
        expectedValue = "0x" + (expectedValue.startsWith("0") ? expectedValue.substring(1) : expectedValue);
        org.junit.Assert.assertEquals(expectedValue, web3.eth_gasPrice());
    }

    @Test
    public void sendRawTransaction() throws Exception {
        Web3Impl web3 = createWeb3();
        SimpleEthereum eth = new SimpleEthereum();
        web3.eth = eth;

        Account acc1 = new AccountBuilder().name("acc1").build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();

        String rawData = Hex.toHexString(tx.getEncoded());

        String trxHash = web3.eth_sendRawTransaction(rawData);

        org.junit.Assert.assertNotNull(trxHash);
        org.junit.Assert.assertNotNull(eth.tx);
        org.junit.Assert.assertArrayEquals(acc1.getAddress(), eth.tx.getSender());
        org.junit.Assert.assertArrayEquals(acc2.getAddress(), eth.tx.getReceiveAddress());
        org.junit.Assert.assertEquals(BigInteger.valueOf(1000000), new BigInteger(1, eth.tx.getValue()));
    }

    @Test
    public void getUnknownTransactionReceipt() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder().name("acc1").build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();

        String hashString = Hex.toHexString(tx.getHash());

        Assert.assertNull(web3.eth_getTransactionReceipt(hashString));
    }

    @Test
    public void getTransactionReceipt() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        worldManager.setBlockStore(world.getBlockChain().getBlockStore());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String hashString = Hex.toHexString(tx.getHash());

        TransactionReceiptDTO tr = web3.eth_getTransactionReceipt(hashString);

        org.junit.Assert.assertNotNull(tr);
        org.junit.Assert.assertEquals("0x" + hashString, tr.transactionHash);
        String trxFrom = TypeConverter.toJsonHex(tx.getSender());
        org.junit.Assert.assertEquals(trxFrom, tr.from);
        String trxTo = TypeConverter.toJsonHex(tx.getReceiveAddress());
        org.junit.Assert.assertEquals(trxTo, tr.to);

        String blockHashString = "0x" + Hex.toHexString(block1.getHash());
        org.junit.Assert.assertEquals(blockHashString, tr.blockHash);

        String blockNumberAsHex = "0x" + Long.toHexString(block1.getNumber());
        org.junit.Assert.assertEquals(blockNumberAsHex, tr.blockNumber);
    }

    @Test
    public void getTransactionReceiptNotInMainBlockchain() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        worldManager.setBlockStore(world.getBlockChain().getBlockStore());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world).parent(genesis).build();
        Block block2b = new BlockBuilder(world).parent(block1b).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        String hashString = Hex.toHexString(tx.getHash());

        TransactionReceiptDTO tr = web3.eth_getTransactionReceipt(hashString);

        Assert.assertNull(tr);
    }

    @Test
    public void getTransactionByHash() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String hashString = Hex.toHexString(tx.getHash());

        TransactionResultDTO tr = web3.eth_getTransactionByHash(hashString);

        Assert.assertNotNull(tr);
        org.junit.Assert.assertEquals("0x" + hashString, tr.hash);

        String blockHashString = "0x" + Hex.toHexString(block1.getHash());
        org.junit.Assert.assertEquals(blockHashString, tr.blockHash);

        org.junit.Assert.assertEquals("0x00", tr.input);
        org.junit.Assert.assertEquals("0x" + Hex.toHexString(tx.getReceiveAddress()), tr.to);
    }

    @Test
    public void getPendingTransactionByHash() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();

        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        pendingState.addPendingTransaction(tx);
        worldManager.setPendingState(pendingState);

        String hashString = Hex.toHexString(tx.getHash());

        TransactionResultDTO tr = web3.eth_getTransactionByHash(hashString);

        Assert.assertNotNull(tr);

        org.junit.Assert.assertEquals("0x" + hashString, tr.hash);
        org.junit.Assert.assertEquals("0", tr.nonce);
        org.junit.Assert.assertEquals(null, tr.blockHash);
        org.junit.Assert.assertEquals(null, tr.transactionIndex);
        org.junit.Assert.assertEquals("0x00", tr.input);
        org.junit.Assert.assertEquals("0x" + Hex.toHexString(tx.getReceiveAddress()), tr.to);
    }

    @Test
    public void getTransactionByHashNotInMainBlockchain() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world).parent(genesis).build();
        Block block2b = new BlockBuilder(world).parent(block1b).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        String hashString = Hex.toHexString(tx.getHash());

        TransactionResultDTO tr = web3.eth_getTransactionByHash(hashString);

        Assert.assertNull(tr);
    }

    @Test
    public void getTransactionByBlockHashAndIndex() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String hashString = Hex.toHexString(tx.getHash());
        String blockHashString = Hex.toHexString(block1.getHash());

        TransactionResultDTO tr = web3.eth_getTransactionByBlockHashAndIndex(blockHashString, "0x0");

        Assert.assertNotNull(tr);
        org.junit.Assert.assertEquals("0x" + hashString, tr.hash);

        org.junit.Assert.assertEquals("0x" + blockHashString, tr.blockHash);
    }

    @Test
    public void getUnknownTransactionByBlockHashAndIndex() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String blockHashString = Hex.toHexString(block1.getHash());

        TransactionResultDTO tr = web3.eth_getTransactionByBlockHashAndIndex(blockHashString, "0x0");

        Assert.assertNull(tr);
    }

    @Test
    public void getTransactionByBlockNumberAndIndex() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String hashString = Hex.toHexString(tx.getHash());
        String blockHashString = Hex.toHexString(block1.getHash());

        TransactionResultDTO tr = web3.eth_getTransactionByBlockNumberAndIndex("0x01", "0x0");

        Assert.assertNotNull(tr);
        org.junit.Assert.assertEquals("0x" + hashString, tr.hash);

        org.junit.Assert.assertEquals("0x" + blockHashString, tr.blockHash);
    }

    @Test
    public void getUnknownTransactionByBlockNumberAndIndex() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        TransactionResultDTO tr = web3.eth_getTransactionByBlockNumberAndIndex("0x1", "0x0");

        Assert.assertNull(tr);
    }

    @Test
    public void getTransactionCount() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;
        web3.repository = (Repository) world.getRepository();

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(100000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String accountAddress = Hex.toHexString(acc1.getAddress());

        String count = web3.eth_getTransactionCount(accountAddress, "0x1");

        Assert.assertNotNull(count);
        org.junit.Assert.assertEquals("0x1", count);

        count = web3.eth_getTransactionCount(accountAddress, "0x0");

        Assert.assertNotNull(count);
        org.junit.Assert.assertEquals("0x0", count);
    }

    @Test
    public void getBlockByNumber() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world).parent(genesis).build();
        block1b.setBitcoinMergedMiningHeader(new byte[]{0x01});
        Block block2b = new BlockBuilder(world).parent(block1b).build();
        block2b.setBitcoinMergedMiningHeader(new byte[] { 0x02 });
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        Web3.BlockResult bresult = web3.eth_getBlockByNumber("0x1", false);

        Assert.assertNotNull(bresult);

        String blockHash = "0x" + Hex.toHexString(block1b.getHash());
        org.junit.Assert.assertEquals(blockHash, bresult.hash);

        bresult = web3.eth_getBlockByNumber("0x2", true);

        Assert.assertNotNull(bresult);

        blockHash = "0x" + Hex.toHexString(block2b.getHash());
        org.junit.Assert.assertEquals(blockHash, bresult.hash);
    }

    @Test
    public void getBlocksByNumber() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world).parent(genesis).build();
        block1b.setBitcoinMergedMiningHeader(new byte[]{0x01});
        Block block2b = new BlockBuilder(world).parent(block1b).build();
        block2b.setBitcoinMergedMiningHeader(new byte[] { 0x02 });
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        Web3.BlockInformationResult[] bresult = web3.eth_getBlocksByNumber("0x1");

        Assert.assertNotNull(bresult);

        org.junit.Assert.assertEquals(2, bresult.length);
        org.junit.Assert.assertEquals(TypeConverter.toJsonHex(block1.getHash()), bresult[0].hash);
        org.junit.Assert.assertEquals(TypeConverter.toJsonHex(block1b.getHash()), bresult[1].hash);
    }

    @Test
    public void getBlockByNumberRetrieveLatestBlock() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Block genesis = world.getBlockChain().getBestBlock();

        Block block1 = new BlockBuilder(world).parent(genesis).build();
        block1.setBitcoinMergedMiningHeader(new byte[] { 0x01 });
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        Web3.BlockResult blockResult = web3.eth_getBlockByNumber("latest", false);

        Assert.assertNotNull(blockResult);
        String blockHash = TypeConverter.toJsonHex(Hex.toHexString(block1.getHash()));
        org.junit.Assert.assertEquals(blockHash, blockResult.hash);
    }

    @Test
    public void getBlockByNumberRetrieveEarliestBlock() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Block genesis = world.getBlockChain().getBestBlock();

        Block block1 = new BlockBuilder(world).parent(genesis).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        Web3.BlockResult blockResult = web3.eth_getBlockByNumber("earliest", false);

        Assert.assertNotNull(blockResult);
        String blockHash = TypeConverter.toJsonHex(genesis.getHash());
        org.junit.Assert.assertEquals(blockHash, blockResult.hash);
    }

    @Test
    public void getBlockByNumberBlockDoesNotExists() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Web3.BlockResult blockResult = web3.eth_getBlockByNumber("0x1234", false);

        Assert.assertNull(blockResult);
    }

    @Test
    public void getBlockByHash() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).build();
        block1.setBitcoinMergedMiningHeader(new byte[] { 0x01 });
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world).parent(genesis).build();
        block1b.setBitcoinMergedMiningHeader(new byte[] { 0x01 });
        Block block2b = new BlockBuilder(world).parent(block1b).build();
        block2b.setBitcoinMergedMiningHeader(new byte[] { 0x02 });
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        String block1HashString = "0x" + Hex.toHexString(block1.getHash());
        String block1bHashString = "0x" + Hex.toHexString(block1b.getHash());
        String block2bHashString = "0x" + Hex.toHexString(block2b.getHash());

        Web3.BlockResult bresult = web3.eth_getBlockByHash(block1HashString, false);

        Assert.assertNotNull(bresult);
        org.junit.Assert.assertEquals(block1HashString, bresult.hash);
        org.junit.Assert.assertEquals("0x00", bresult.extraData);
        org.junit.Assert.assertEquals(0, bresult.transactions.length);
        org.junit.Assert.assertEquals(0, bresult.uncles.length);

        bresult = web3.eth_getBlockByHash(block1bHashString, true);

        Assert.assertNotNull(bresult);
        org.junit.Assert.assertEquals(block1bHashString, bresult.hash);

        bresult = web3.eth_getBlockByHash(block2bHashString, true);

        Assert.assertNotNull(bresult);
        org.junit.Assert.assertEquals(block2bHashString, bresult.hash);
    }

    @Test
    public void getBlockByHashWithFullTransactionsAsResult() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(220000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(0)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        block1.setBitcoinMergedMiningHeader(new byte[]{0x01});
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String block1HashString = TypeConverter.toJsonHex(block1.getHash());

        Web3.BlockResult bresult = web3.eth_getBlockByHash(block1HashString, true);

        Assert.assertNotNull(bresult);
        org.junit.Assert.assertEquals(block1HashString, bresult.hash);
        org.junit.Assert.assertEquals(1, bresult.transactions.length);
        org.junit.Assert.assertEquals(block1HashString, ((TransactionResultDTO) bresult.transactions[0]).blockHash);
        org.junit.Assert.assertEquals(0, bresult.uncles.length);
    }

    @Test
    public void getBlockByHashWithTransactionsHashAsResult() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(220000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(0)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        block1.setBitcoinMergedMiningHeader(new byte[] { 0x01 });
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String block1HashString = TypeConverter.toJsonHex(block1.getHash());

        Web3.BlockResult bresult = web3.eth_getBlockByHash(block1HashString, false);

        Assert.assertNotNull(bresult);
        org.junit.Assert.assertEquals(block1HashString, bresult.hash);
        org.junit.Assert.assertEquals(1, bresult.transactions.length);
        org.junit.Assert.assertEquals(TypeConverter.toJsonHex(tx.getHash()), bresult.transactions[0]);
        org.junit.Assert.assertEquals(0, bresult.uncles.length );
    }

    @Test
    public void getBlockByHashBlockDoesNotExists() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;

        Web3.BlockResult blockResult = web3.eth_getBlockByHash("0x1234", false);

        Assert.assertNull(blockResult);
    }

    @Test
    public void getCode() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;
        web3.repository = (Repository) world.getRepository();

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(100000000)).build();
        byte[] code = new byte[] { 0x01, 0x02, 0x03 };
        world.getRepository().saveCode(acc1.getAddress(), code);
        Block genesis = world.getBlockChain().getBestBlock();
        genesis.setStateRoot(world.getRepository().getRoot());
        genesis.flushRLP();
        world.getBlockChain().getBlockStore().saveBlock(genesis, genesis.getCumulativeDifficulty(), true);
        Block block1 = new BlockBuilder(world).parent(genesis).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String accountAddress = Hex.toHexString(acc1.getAddress());

        String scode = web3.eth_getCode(accountAddress, "0x1");

        Assert.assertNotNull(scode);
        org.junit.Assert.assertEquals("0x" + Hex.toHexString(code), scode);
    }

    @Test
    public void callFromDefaultAddressInWallet() throws Exception {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("default").balance(BigInteger.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");
        TestContract greeter = TestContract.greeter();
        Transaction tx = new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data(greeter.data)
                .build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world).parent(genesis).transactions(txs).build();
        world.getBlockChain().tryToConnect(block1);

        Web3Impl web3 = createWeb3Mocked(world, block1);

        Web3.CallArguments argsForCall = new Web3.CallArguments();
        argsForCall.to = TypeConverter.toJsonHex(tx.getContractAddress());
        argsForCall.data = greeter.functions.get("greet").formatSignature();

        String result = web3.eth_call(argsForCall, "latest");

        org.junit.Assert.assertEquals("0x0000000000000000000000000000000000000000000000000000000064617665", result);
    }

    @Test
    public void callFromAddressInWallet() throws Exception {
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

        Web3Impl web3 = createWeb3Mocked(world, block1);

        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");

        Web3.CallArguments argsForCall = new Web3.CallArguments();
        argsForCall.from = TypeConverter.toJsonHex(acc1.getAddress());
        argsForCall.to = TypeConverter.toJsonHex(tx.getContractAddress());
        argsForCall.data = "0xead710c40000000000000000000000000000000000000000000000000000000064617665";

        String result = web3.eth_call(argsForCall, "latest");

        org.junit.Assert.assertEquals("0x0000000000000000000000000000000000000000000000000000000064617665", result);
    }

    @Test
    public void getCodeBlockDoesNotExist() throws Exception {
        World world = new World();
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());

        Web3Impl web3 = createWeb3();
        web3.worldManager = worldManager;
        web3.repository = (Repository) world.getRepository();

        Account acc1 = new AccountBuilder(world).name("acc1").balance(BigInteger.valueOf(100000000)).build();
        byte[] code = new byte[] { 0x01, 0x02, 0x03 };
        world.getRepository().saveCode(acc1.getAddress(), code);

        String accountAddress = Hex.toHexString(acc1.getAddress());

        String resultCode = web3.eth_getCode(accountAddress, "0x100");

        org.junit.Assert.assertNull(resultCode);
    }

    @Test
    public void net_listening()  {
        SimpleEthereum eth = new SimpleEthereum();
        SimplePeerServer peerServer = new SimplePeerServer();
        eth.peerServer = peerServer;

        Web3Impl web3 = createWeb3();
        web3.eth = eth;

        Assert.assertTrue("Node is not listening", !web3.net_listening());

        peerServer.isListening = true;
        Assert.assertTrue("Node is listening", web3.net_listening());
    }

    @Test
    public void eth_coinbase()  {
        String originalCoibase = "1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347";
        SimpleMinerServer minerServer= new SimpleMinerServer();
        minerServer.coinbase = originalCoibase;

        Ethereum ethMock = Web3Mocks.getMockEthereum();
        RskSystemProperties mockProperties = Web3Mocks.getMockProperties();
        PersonalModule personalModule = new PersonalModuleWalletDisabled();
        Web3 web3 = new Web3Impl(ethMock, mockProperties, null, minerServer, personalModule, null, Web3Mocks.getMockChannelManager());

        Assert.assertTrue("Not returning coinbase specified on miner server", web3.eth_coinbase().compareTo("0x" + originalCoibase) == 0);
    }

    @Test
    public void eth_accounts()
    {
        Web3Impl web3 = createWeb3();
        int originalAccounts = web3.personal_listAccounts().length;

        String addr1 = web3.personal_newAccountWithSeed("sampleSeed1");
        String addr2 = web3.personal_newAccountWithSeed("sampleSeed2");

        Set<String> accounts = Arrays.stream(web3.eth_accounts()).collect(Collectors.toSet());

        Assert.assertEquals("Not all accounts are being retrieved", originalAccounts + 2, accounts.size());

        Assert.assertTrue(accounts.contains(addr1));
        Assert.assertTrue(accounts.contains(addr2));
    }

    @Test
    public void eth_sign()
    {
        Web3Impl web3 = createWeb3();

        String addr1 = web3.personal_newAccountWithSeed("sampleSeed1");
        String addr2 = web3.personal_newAccountWithSeed("sampleSeed2");

        byte[] hash = SHA3Helper.sha3("this is the data to hash".getBytes());

        String signature = "";
        try {
            signature = web3.eth_sign(addr1, "0x" + Hex.toHexString(hash));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        String expectedSignature = "0x" + wallet.getAccount(stringHexToByteArray(addr1)).getEcKey().sign(hash).r.toString() + wallet.getAccount(stringHexToByteArray(addr1)).getEcKey().sign(hash).s.toString() + wallet.getAccount(stringHexToByteArray(addr1)).getEcKey().sign(hash).v;

        Assert.assertTrue("Signature is not the same one returned by the key", expectedSignature.compareTo(signature) == 0);
    }

    @Test
    public void createNewAccount()
    {
        Web3Impl web3 = createWeb3();

        String addr = web3.personal_newAccount("passphrase1");

        Account account = null;

        try {
            account = wallet.getAccount(stringHexToByteArray(addr), "passphrase1");
        }
        catch (Exception e) {
            e.printStackTrace();
            return;
        }

        org.junit.Assert.assertNotNull(account);
        org.junit.Assert.assertEquals(addr, "0x" + Hex.toHexString(account.getAddress()));
    }

    @Test
    public void listAccounts()
    {
        Web3Impl web3 = createWeb3();
        int originalAccounts = web3.personal_listAccounts().length;

        String addr1 = web3.personal_newAccount("passphrase1");
        String addr2 = web3.personal_newAccount("passphrase2");

        Set<String> addresses = Arrays.stream(web3.personal_listAccounts()).collect(Collectors.toSet());

        org.junit.Assert.assertNotNull(addresses);
        org.junit.Assert.assertEquals(originalAccounts + 2, addresses.size());
        org.junit.Assert.assertTrue(addresses.contains(addr1));
        org.junit.Assert.assertTrue(addresses.contains(addr2));
    }

    @Test
    public void importAccountUsingRawKey()
    {
        Web3Impl web3 = createWeb3();

        ECKey eckey = new ECKey();

        String address = web3.personal_importRawKey(Hex.toHexString(eckey.getPrivKeyBytes()), "passphrase1");

        org.junit.Assert.assertNotNull(address);

        Account account0 = wallet.getAccount(stringHexToByteArray(address));

        org.junit.Assert.assertNull(account0);

        Account account = wallet.getAccount(stringHexToByteArray(address), "passphrase1");

        org.junit.Assert.assertNotNull(account);
        org.junit.Assert.assertEquals(address, "0x" + Hex.toHexString(account.getAddress()));
        org.junit.Assert.assertArrayEquals(eckey.getPrivKeyBytes(), account.getEcKey().getPrivKeyBytes());
    }

    @Test
    public void dumpRawKey() throws Exception {
        Web3Impl web3 = createWeb3();

        ECKey eckey = new ECKey();

        String address = web3.personal_importRawKey(Hex.toHexString(eckey.getPrivKeyBytes()), "passphrase1");
        org.junit.Assert.assertTrue(web3.personal_unlockAccount(address, "passphrase1", ""));

        String rawKey = web3.personal_dumpRawKey(address).substring(2);

        org.junit.Assert.assertArrayEquals(eckey.getPrivKeyBytes(), Hex.decode(rawKey));
    }


    @Test
    public void sendPersonalTransaction()
    {
        Web3Impl web3 = createWeb3();

        // **** Initializes data ******************
        String addr1 = web3.personal_newAccount("passphrase1");
        String addr2 = web3.personal_newAccount("passphrase2");

        String toAddress = addr2;
        BigInteger value = BigInteger.valueOf(7);
        BigInteger gasPrice = BigInteger.valueOf(8);
        BigInteger gasLimit = BigInteger.valueOf(9);
        String data = "0xff";
        BigInteger nonce = BigInteger.ONE;

        // ***** Executes the transaction *******************
        Web3.CallArguments args = new Web3.CallArguments();
        args.from = addr1;
        args.to = addr2;
        args.data = data;
        args.gas = TypeConverter.toJsonHex(gasLimit);
        args.gasPrice= TypeConverter.toJsonHex(gasPrice);
        args.value = value.toString();
        args.nonce = nonce.toString();

        String txHash = null;
        try {
            txHash = web3.personal_sendTransaction(args, "passphrase1");
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // ***** Verifies tx hash
        Transaction tx = Transaction.create(toAddress.substring(2), value, nonce, gasPrice, gasLimit, args.data);
        Account account = wallet.getAccount(stringHexToByteArray(addr1), "passphrase1");
        tx.sign(account.getEcKey().getPrivKeyBytes());

        String expectedHash = TypeConverter.toJsonHex(tx.getHash());

        Assert.assertTrue("Method is not creating the expected transaction", expectedHash.compareTo(txHash) == 0);
    }

    @Test
    public void unlockAccount()
    {
        Web3Impl web3 = createWeb3();

        String addr = web3.personal_newAccount("passphrase1");

        Account account0 = wallet.getAccount(stringHexToByteArray(addr));

        org.junit.Assert.assertNull(account0);

        org.junit.Assert.assertTrue(web3.personal_unlockAccount(addr, "passphrase1", ""));

        Account account = wallet.getAccount(stringHexToByteArray(addr));

        org.junit.Assert.assertNotNull(account);
    }

    @Test(expected = JsonRpcInvalidParamException.class)
    public void unlockAccountInvalidDuration()
    {
        Web3Impl web3 = createWeb3();

        String addr = web3.personal_newAccount("passphrase1");

        Account account0 = wallet.getAccount(stringHexToByteArray(addr));

        org.junit.Assert.assertNull(account0);

        web3.personal_unlockAccount(addr, "passphrase1", "K");

        org.junit.Assert.fail("This should fail");
    }

    @Test
    public void lockAccount()
    {
        Web3Impl web3 = createWeb3();

        String addr = web3.personal_newAccount("passphrase1");

        Account account0 = wallet.getAccount(stringHexToByteArray(addr));

        org.junit.Assert.assertNull(account0);

        org.junit.Assert.assertTrue(web3.personal_unlockAccount(addr, "passphrase1", ""));

        Account account = wallet.getAccount(stringHexToByteArray(addr));

        org.junit.Assert.assertNotNull(account);

        org.junit.Assert.assertTrue(web3.personal_lockAccount(addr));

        Account account1 = wallet.getAccount(stringHexToByteArray(addr));

        org.junit.Assert.assertNull(account1);
    }

    @Test
    public void eth_sendTransaction()
    {
        BigInteger nonce = BigInteger.ONE;
        Web3Impl web3 = createWeb3();

        // **** Initializes data ******************
        String addr1 = web3.personal_newAccountWithSeed("sampleSeed1");
        String addr2 = web3.personal_newAccountWithSeed("sampleSeed2");

        String toAddress = addr2;
        BigInteger value = BigInteger.valueOf(7);
        BigInteger gasPrice = BigInteger.valueOf(8);
        BigInteger gasLimit = BigInteger.valueOf(9);
        String data = "0xff";

        // ***** Executes the transaction *******************
        Web3.CallArguments args = new Web3.CallArguments();
        args.from = addr1;
        args.to = addr2;
        args.data = data;
        args.gas = TypeConverter.toJsonHex(gasLimit);
        args.gasPrice= TypeConverter.toJsonHex(gasPrice);
        args.value = value.toString();
        args.nonce = nonce.toString();

        String txHash = null;
        try {
            txHash = web3.eth_sendTransaction(args);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // ***** Verifies tx hash
        Transaction tx = Transaction.create(toAddress.substring(2), value, nonce, gasPrice, gasLimit, args.data);
        tx.sign(wallet.getAccount(stringHexToByteArray(addr1)).getEcKey().getPrivKeyBytes());

        String expectedHash = TypeConverter.toJsonHex(tx.getHash());

        Assert.assertTrue("Method is not creating the expected transaction", expectedHash.compareTo(txHash) == 0);
    }

    private Web3Impl createWeb3() {
        return createWeb3(Web3Mocks.getMockEthereum());
    }

    private Web3Impl createWeb3Mocked(World world, Block block1) {
        SimpleWorldManager worldManager = new SimpleWorldManager();
        worldManager.setBlockchain(world.getBlockChain());
        PendingState pendingState = new PendingStateImpl(world.getBlockChain(), world.getRepository(), world.getBlockChain().getBlockStore(), null, null, 10, 100);
        worldManager.setPendingState(pendingState);
        Ethereum ethMock = Mockito.mock(Ethereum.class);
        ProgramResult res = new ProgramResult();
        res.setHReturn(TypeConverter.stringHexToByteArray("0x0000000000000000000000000000000000000000000000000000000064617665"));
        Mockito.when(ethMock.callConstant(Matchers.any())).thenReturn(res);
        Mockito.when(ethMock.getWorldManager()).thenReturn(worldManager);
        return createWeb3(ethMock);
    }

    private Web3Impl createWeb3(Ethereum eth) {
        wallet = WalletFactory.createWallet();
        PersonalModuleWalletEnabled personalModule = new PersonalModuleWalletEnabled(eth, wallet);
        EthModule ethModule = new EthModule(eth, new EthModuleSolidityDisabled(), new EthModuleWalletEnabled(eth, wallet));
        MinerClient minerClient = new SimpleMinerClient();
        ChannelManager channelManager = new SimpleChannelManager();
        return new Web3RskImpl(eth, RskSystemProperties.CONFIG, minerClient, Web3Mocks.getMockMinerServer(), personalModule, ethModule, channelManager);
    }

    @Test
    @Ignore
    public void eth_compileSolidity() throws Exception {
        RskSystemProperties systemProperties = Mockito.mock(RskSystemProperties.class);
        String solc = System.getProperty("solc");
        if(StringUtils.isEmpty(solc))
            solc = "/usr/bin/solc";

        Mockito.when(systemProperties.customSolcPath()).thenReturn(solc);
        Ethereum eth = Mockito.mock(Ethereum.class);
        EthModule ethModule = new EthModule(eth, new EthModuleSolidityEnabled(new SolidityCompiler(systemProperties)), null);
        PersonalModule personalModule = new PersonalModuleWalletDisabled();
        Web3Impl web3 = new Web3RskImpl(eth, systemProperties, null, null, personalModule, ethModule, Web3Mocks.getMockChannelManager());
        String contract = "pragma solidity ^0.4.1; contract rsk { function multiply(uint a) returns(uint d) {   return a * 7;   } }";

        Map<String, CompilationResultDTO> result = web3.eth_compileSolidity(contract);

        org.junit.Assert.assertNotNull(result);

        CompilationResultDTO dto = result.get("rsk");

        if (dto == null)
            dto = result.get("<stdin>:rsk");

        org.junit.Assert.assertEquals(contract , dto.info.getSource());
    }

    @Test
    public void eth_compileSolidityWithoutSolidity() throws Exception {
        SystemProperties systemProperties = Mockito.mock(SystemProperties.class);
        String solc = System.getProperty("solc");
        if(StringUtils.isEmpty(solc))
            solc = "/usr/bin/solc";

        Mockito.when(systemProperties.customSolcPath()).thenReturn(solc);

        Wallet wallet = WalletFactory.createWallet();
        Ethereum eth = Mockito.mock(Ethereum.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(eth.getWorldManager().getBlockchain().getBestBlock().getNumber()).thenReturn(1L);
        EthModule ethModule = new EthModule(eth, new EthModuleSolidityDisabled(), new EthModuleWalletEnabled(eth, wallet));
        Web3Impl web3 = new Web3RskImpl(eth, RskSystemProperties.CONFIG, null, null, new PersonalModuleWalletDisabled(), ethModule, Web3Mocks.getMockChannelManager());

        String contract = "pragma solidity ^0.4.1; contract rsk { function multiply(uint a) returns(uint d) {   return a * 7;   } }";

        Map<String, CompilationResultDTO> result = web3.eth_compileSolidity(contract);

        org.junit.Assert.assertNotNull(result);
        org.junit.Assert.assertEquals(0, result.size());
    }

}
