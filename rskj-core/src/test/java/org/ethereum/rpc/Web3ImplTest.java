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
import co.rsk.config.TestSystemProperties;
import co.rsk.core.*;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.core.bc.MiningMainchainViewImpl;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.net.TransactionGateway;
import co.rsk.net.simples.SimpleBlockProcessor;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleTransactionBase;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletDisabled;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.rsk.RskModuleImpl;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.TestContract;
import co.rsk.vm.BytecodeCompiler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.rpc.Simples.*;
import org.ethereum.rpc.dto.BlockParsedRequestDTO;
import org.ethereum.rpc.dto.BlockResultDTO;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ethereum.rpc.TypeConverter.toJsonHex;
import static org.ethereum.rpc.TypeConverter.toQuantityJsonHex;
import static org.ethereum.rpc.TypeConverter.toUnformattedJsonHex;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Created by Ruben Altman on 09/06/2016.
 */
public class Web3ImplTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    Wallet wallet;

    @Test
    public void web3_clientVersion() {
        Web3 web3 = createWeb3();

        String clientVersion = web3.web3_clientVersion();

        assertTrue("client version is not including rsk!", clientVersion.toLowerCase().contains("rsk"));
    }

    @Test
    public void net_version() {
        Web3Impl web3 = createWeb3();

        String netVersion = web3.net_version();

        assertTrue("RSK net version different than expected", netVersion.compareTo(Byte.toString(config.getNetworkConstants().getChainId())) == 0);
    }

    @Test
    public void eth_protocolVersion() {
        World world = new World();
        Web3Impl web3 = createWeb3(world);

        String netVersion = web3.eth_protocolVersion();

        assertTrue("RSK net version different than one", netVersion.compareTo("1") == 0);
    }

    @Test
    public void net_peerCount() {
        Web3Impl web3 = createWeb3();

        String peerCount  = web3.net_peerCount();

        assertEquals("Different number of peers than expected",
                "0x0", peerCount);
    }

    @Test
    public void web3_sha3() throws Exception {
        String toHash = "RSK";

        Web3 web3 = createWeb3();

        String result = web3.web3_sha3(toHash);

        // Function must apply the Keccak-256 algorithm
        // Result taken from https://emn178.github.io/online-tools/keccak_256.html
        assertTrue("hash does not match", result.compareTo("0x80553b6b348ae45ab8e8bf75e77064818c0a772f13cf8d3a175d3815aec59b56") == 0);
    }

    @Test
    public void eth_syncing_returnFalseWhenNotSyncing()  {
        World world = new World();
        SimpleBlockProcessor nodeProcessor = new SimpleBlockProcessor();
        nodeProcessor.lastKnownBlockNumber = 0;
        Web3Impl web3 = createWeb3(world, nodeProcessor, null);

        Object result = web3.eth_syncing();

        assertTrue("Node is not syncing, must return false", !(boolean)result);
    }

    @Test
    public void eth_syncing_returnSyncingResultWhenSyncing()  {
        World world = new World();
        SimpleBlockProcessor nodeProcessor = new SimpleBlockProcessor();
        nodeProcessor.lastKnownBlockNumber = 5;
        Web3Impl web3 = createWeb3(world, nodeProcessor, null);

        Object result = web3.eth_syncing();

        assertTrue("Node is syncing, must return sync manager", result instanceof Web3.SyncingResult);
        assertTrue("Highest block is 5", ((Web3.SyncingResult)result).highestBlock.compareTo("0x5") == 0);
        assertTrue("Simple blockchain starts from genesis block", ((Web3.SyncingResult)result).currentBlock.compareTo("0x0") == 0);
    }

    @Test
    public void getBalanceWithAccount() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(10000)).build();

        Web3Impl web3 = createWeb3(world);

        assertEquals("0x" + ByteUtil.toHexString(BigInteger.valueOf(10000).toByteArray()), web3.eth_getBalance(ByteUtil.toHexString(acc1.getAddress().getBytes())));
    }

    @Test
    public void getBalanceWithAccountAndLatestBlock() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(10000)).build();

        Web3Impl web3 = createWeb3(world);

        assertEquals("0x" + ByteUtil.toHexString(BigInteger.valueOf(10000).toByteArray()), web3.eth_getBalance(ByteUtil.toHexString(acc1.getAddress().getBytes()), "latest"));
    }

    @Test
    public void getBalanceWithAccountAndGenesisBlock() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(10000)).build();

        Web3Impl web3 = createWeb3(world);

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());
        String balanceString = "0x" + ByteUtil.toHexString(BigInteger.valueOf(10000).toByteArray());

        assertEquals(balanceString, web3.eth_getBalance(accountAddress, "0x0"));
    }

    @Test
    public void getBalanceWithAccountAndBlock() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(10000)).build();
        Block genesis = world.getBlockByName("g00");

        Block block1 = new BlockBuilder(null, null, null).parent(genesis).build();
        world.getBlockChain().tryToConnect(block1);

        Web3Impl web3 = createWeb3(world);

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());
        String balanceString = "0x" + ByteUtil.toHexString(BigInteger.valueOf(10000).toByteArray());

        assertEquals(balanceString, web3.eth_getBalance(accountAddress, "0x1"));
    }

    @Test
    public void getBalanceWithAccountAndBlockWithTransaction() {
        World world = new World();
        BlockChainImpl blockChain = world.getBlockChain();
        TransactionPool transactionPool = world.getTransactionPool();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(10000000)).build();
        Account acc2 = new AccountBuilder(world).name("acc2").build();
        Block genesis = world.getBlockByName("g00");

        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(10000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        Web3Impl web3 = createWeb3(world, transactionPool, null);

        String accountAddress = ByteUtil.toHexString(acc2.getAddress().getBytes());
        String balanceString = "0x" + ByteUtil.toHexString(BigInteger.valueOf(10000).toByteArray());

        assertEquals("0x0", web3.eth_getBalance(accountAddress, "0x0"));
        assertEquals(balanceString, web3.eth_getBalance(accountAddress, "0x1"));
        assertEquals(balanceString, web3.eth_getBalance(accountAddress, "pending"));
    }

    @Test
    public void getBalance_blockParsedRequest_withBlockNumber() {
        Integer coinBalance = 10000;

        World world = new World();
        Account acc1 = new AccountBuilder(world)
                .name("acc1")
                .balance(Coin.valueOf(coinBalance))
                .build();
        Block genesis = world.getBlockByName("g00");

        Block block1 = new BlockBuilder(null, null, null)
                .parent(genesis)
                .build();
        world.getBlockChain().tryToConnect(block1);

        Web3Impl web3 = createWeb3(world);

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());
        String balanceString = "0x" + ByteUtil.toHexString(BigInteger.valueOf(coinBalance).toByteArray());

        BlockParsedRequestDTO blockParsedRequestDto = new BlockParsedRequestDTO("0x1");

        assertEquals(balanceString, web3.eth_getBalance(accountAddress, blockParsedRequestDto));
    }

    @Test
    public void getBalance_blockParsedRequest_withBlockHashCanonical() {
        World world = new World();
        Web3Impl web3 = createWeb3(world);
        Integer coinBalance = 2000000;

        Account acc1 = new AccountBuilder(world)
                .name("acc1")
                .balance(Coin.valueOf(coinBalance))
                .build();

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());
        String balanceString = "0x" + ByteUtil.toHexString(BigInteger.valueOf(coinBalance).toByteArray());

        Block genesis = world.getBlockChain().getBestBlock();

        // Short Chain
        long block1aDifficulty = 10;
        Block block1a = createBlockForCanonical(world, genesis, block1aDifficulty, null);
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1a));

        // Long Chain
        long difficulty1bDifficulty = block1aDifficulty - 1;
        Block block1b = createBlockForCanonical(world, genesis, difficulty1bDifficulty, null);
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));

        long difficulty1b1Difficulty = block1aDifficulty + 1;
        Block block1b1 = createBlockForCanonical(world, block1b, difficulty1b1Difficulty, null);
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1b1));

        String hashAsString1a = toJsonHex(block1a.getHash().getBytes());
        String hashAsString1b1 = toJsonHex(block1b1.getHash().getBytes());

        // TEST CASES
        // Short Chain - Check canonical false for the short chain
        BlockParsedRequestDTO blockParsedRequestDto1aOne = new BlockParsedRequestDTO(null, hashAsString1a, false);
        String result1aOne = web3.eth_getBalance(accountAddress, blockParsedRequestDto1aOne);
        Assert.assertNotNull(result1aOne);
        Assert.assertEquals(balanceString, result1aOne);

        // Short Chain - Check canonical true for the short chain (an exception should be thrown since the block was not found)
        BlockParsedRequestDTO blockParsedRequestDto1aTwo = new BlockParsedRequestDTO(null, hashAsString1a, true);
        TestUtils.assertThrows(RskJsonRpcRequestException.class,
           () -> web3.eth_getBalance(accountAddress, blockParsedRequestDto1aTwo));

        // Long Chain - Check canonical false
        BlockParsedRequestDTO blockParsedRequestDto1b1One = new BlockParsedRequestDTO(null, hashAsString1b1, false);
        String result1b1One = web3.eth_getBalance(accountAddress, blockParsedRequestDto1b1One);
        Assert.assertNotNull(result1b1One);
        Assert.assertEquals(balanceString, result1b1One);

        // Long Chain - Check canonical long
        BlockParsedRequestDTO blockParsedRequestDto1b1Two = new BlockParsedRequestDTO(null, hashAsString1b1, true);
        String result1b1Two = web3.eth_getBalance(accountAddress, blockParsedRequestDto1b1Two);
        Assert.assertNotNull(result1b1Two);
        Assert.assertEquals(balanceString, result1b1Two);
    }

    @Test
    public void eth_mining()  {
        Ethereum ethMock = Web3Mocks.getMockEthereum();
        Blockchain blockchain = Web3Mocks.getMockBlockchain();
        BlockStore blockStore = Web3Mocks.getMockBlockStore();
        RskSystemProperties mockProperties = Web3Mocks.getMockProperties();
        MinerClient minerClient = new SimpleMinerClient();
        PersonalModule personalModule = new PersonalModuleWalletDisabled();
        TxPoolModule txPoolModule = new TxPoolModuleImpl(Web3Mocks.getMockTransactionPool());
        DebugModule debugModule = new DebugModuleImpl(null, null, Web3Mocks.getMockMessageHandler(), null);
        Web3 web3 = new Web3Impl(
                ethMock,
                blockchain,
                blockStore,
                null,
                mockProperties,
                minerClient,
                null,
                personalModule,
                null,
                null,
                txPoolModule,
                null,
                debugModule,
                null, null,
                Web3Mocks.getMockChannelManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mock(Web3InformationRetriever.class));

        assertTrue("Node is not mining", !web3.eth_mining());
        try {
            minerClient.start();

            assertTrue("Node is mining", web3.eth_mining());
        } finally {
            minerClient.stop();
        }

        assertTrue("Node is not mining", !web3.eth_mining());
    }

    @Test
    public void getGasPrice()  {
        Web3Impl web3 = createWeb3();
        web3.eth = new SimpleEthereum();
        String expectedValue = ByteUtil.toHexString(new BigInteger("20000000000").toByteArray());
        expectedValue = "0x" + (expectedValue.startsWith("0") ? expectedValue.substring(1) : expectedValue);
        assertEquals(expectedValue, web3.eth_gasPrice());
    }

    @Test
    public void getUnknownTransactionReceipt() {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        Web3Impl web3 = createWeb3(world, receiptStore);

        Account acc1 = new AccountBuilder().name("acc1").build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();

        String hashString = tx.getHash().toHexString();

        Assert.assertNull(web3.eth_getTransactionReceipt(hashString));
        Assert.assertNull(web3.rsk_getRawTransactionReceiptByHash(hashString));
    }

    @Test
    public void getTransactionReceipt() {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        Web3Impl web3 = createWeb3(world, receiptStore);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String hashString = tx.getHash().toHexString();

        TransactionReceiptDTO tr = web3.eth_getTransactionReceipt(hashString);

        assertNotNull(tr);
        assertEquals("0x" + hashString, tr.getTransactionHash());
        String trxFrom = TypeConverter.toJsonHex(tx.getSender().getBytes());
        assertEquals(trxFrom, tr.getFrom());
        String trxTo = TypeConverter.toJsonHex(tx.getReceiveAddress().getBytes());
        assertEquals(trxTo, tr.getTo());

        String blockHashString = "0x" + block1.getHash();
        assertEquals(blockHashString, tr.getBlockHash());

        String blockNumberAsHex = "0x" + Long.toHexString(block1.getNumber());
        assertEquals(blockNumberAsHex, tr.getBlockNumber());

        String rawTransactionReceipt = web3.rsk_getRawTransactionReceiptByHash(hashString);
        String expectedRawTxReceipt = "0xf9010c01825208b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c082520801";
        assertEquals(expectedRawTxReceipt, rawTransactionReceipt);

        String[] transactionReceiptNodes = web3.rsk_getTransactionReceiptNodesByHash(blockHashString, hashString);
        ArrayList<String> expectedRawTxReceiptNodes = new ArrayList<>();
        expectedRawTxReceiptNodes.add("0x70078048ee76b19fc451dba9dbee8b3e73084f79ea540d3940b3b36b128e8024e9302500010f");
        assertEquals(1, transactionReceiptNodes.length);
        assertEquals(expectedRawTxReceiptNodes.get(0), transactionReceiptNodes[0]);
    }

    @Test
    public void getTransactionReceiptNotInMainBlockchain() {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        Web3Impl web3 = createWeb3(world, receiptStore);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).difficulty(3l).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis)
                .difficulty(block1.getDifficulty().asBigInteger().longValue()-1).build();
        Block block2b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(2).parent(block1b).build();
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        String hashString = tx.getHash().toHexString();

        TransactionReceiptDTO tr = web3.eth_getTransactionReceipt(hashString);

        Assert.assertNull(tr);
    }

    @Test
    public void getTransactionByHash() {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        Web3Impl web3 = createWeb3(world, receiptStore);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String hashString = tx.getHash().toHexString();

        TransactionResultDTO tr = web3.eth_getTransactionByHash(hashString);

        assertNotNull(tr);
        assertEquals("0x" + hashString, tr.hash);

        String blockHashString = "0x" + block1.getHash();
        assertEquals(blockHashString, tr.blockHash);

        assertEquals("0x", tr.input);
        assertEquals("0x" + ByteUtil.toHexString(tx.getReceiveAddress().getBytes()), tr.to);

        // Check the v value used to encode the transaction
        // NOT the v value used in signature
        // the encoded value includes chain id
        Assert.assertArrayEquals(new byte[] {tx.getEncodedV()}, TypeConverter.stringHexToByteArray(tr.v));
        Assert.assertThat(TypeConverter.stringHexToBigInteger(tr.s), is(tx.getSignature().getS()));
        Assert.assertThat(TypeConverter.stringHexToBigInteger(tr.r), is(tx.getSignature().getR()));
    }

    @Test
    public void getPendingTransactionByHash() {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        BlockChainImpl blockChain = world.getBlockChain();
        BlockStore blockStore = world.getBlockStore();
        TransactionExecutorFactory transactionExecutorFactory = buildTransactionExecutorFactory(blockStore, world.getBlockTxSignatureCache());
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepositoryLocator(), blockStore, blockFactory, null, transactionExecutorFactory, world.getReceivedTxSignatureCache(), 10, 100);
        transactionPool.processBest(blockChain.getBestBlock());
        Web3Impl web3 = createWeb3(world, transactionPool, receiptStore);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        transactionPool.addTransaction(tx);

        String hashString = tx.getHash().toHexString();

        TransactionResultDTO tr = web3.eth_getTransactionByHash(hashString);

        assertNotNull(tr);

        assertEquals("0x" + hashString, tr.hash);
        assertEquals("0x0", tr.nonce);
        assertEquals(null, tr.blockHash);
        assertEquals(null, tr.transactionIndex);
        assertEquals("0x", tr.input);
        assertEquals("0x" + ByteUtil.toHexString(tx.getReceiveAddress().getBytes()), tr.to);
    }

    @Test
    public void getTransactionByHashNotInMainBlockchain() {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);

        Web3Impl web3 = createWeb3(world, receiptStore);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(10).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(block1.getDifficulty().asBigInteger().longValue()-1).parent(genesis).build();
        Block block2b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(block1.getDifficulty().asBigInteger().longValue()+1).parent(block1b).build();
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        String hashString = tx.getHash().toHexString();

        TransactionResultDTO tr = web3.eth_getTransactionByHash(hashString);

        Assert.assertNull(tr);
    }

    @Test
    public void getTransactionByBlockHashAndIndex() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String hashString = tx.getHash().toHexString();
        String blockHashString = block1.getHash().toHexString();

        TransactionResultDTO tr = web3.eth_getTransactionByBlockHashAndIndex(blockHashString, "0x0");

        assertNotNull(tr);
        assertEquals("0x" + hashString, tr.hash);

        assertEquals("0x" + blockHashString, tr.blockHash);
    }

    @Test
    public void getUnknownTransactionByBlockHashAndIndex() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String blockHashString = block1.getHash().toString();

        TransactionResultDTO tr = web3.eth_getTransactionByBlockHashAndIndex(blockHashString, "0x0");

        Assert.assertNull(tr);
    }

    @Test
    public void getTransactionByBlockNumberAndIndex() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String hashString = tx.getHash().toHexString();
        String blockHashString = block1.getHash().toHexString();

        TransactionResultDTO tr = web3.eth_getTransactionByBlockNumberAndIndex("0x01", "0x0");

        assertNotNull(tr);
        assertEquals("0x" + hashString, tr.hash);

        assertEquals("0x" + blockHashString, tr.blockHash);
    }

    @Test
    public void getUnknownTransactionByBlockNumberAndIndex() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        TransactionResultDTO tr = web3.eth_getTransactionByBlockNumberAndIndex("0x1", "0x0");

        Assert.assertNull(tr);
    }

    @Test
    public void getTransactionCount() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(100000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());

        String count = web3.eth_getTransactionCount(accountAddress, "0x1");

        assertNotNull(count);
        assertEquals("0x1", count);

        count = web3.eth_getTransactionCount(accountAddress, "0x0");

        assertNotNull(count);
        assertEquals("0x0", count);
    }

    @Test
    public void getTransactionCount_byBlockHash() {
        World world = new World();
        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world)
                .name("acc1")
                .balance(Coin.valueOf(100000000))
                .build();

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());

        Account acc2 = new AccountBuilder()
                .name("acc2")
                .build();

        Transaction tx1 = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        Transaction tx2 = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000)).build();

        Block genesis = world.getBlockChain().getBestBlock();

        // Short Chain
        long block1aDifficulty = 10;
        List<Transaction> txs1a = new ArrayList<>(Arrays.asList(tx1));
        Block block1a = createBlockForCanonical(world, genesis, block1aDifficulty, txs1a);
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1a));

        // Long Chain
        long block1bDifficulty = block1aDifficulty - 1;
        List<Transaction> txs1b = new ArrayList<>(Arrays.asList());
        Block block1b = createBlockForCanonical(world, genesis, block1bDifficulty, txs1b);
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));

        long block1b1Difficulty = block1aDifficulty + 1;
        Block block1b1 = createBlockForCanonical(world, block1b, block1b1Difficulty, null);
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1b1));

        long block1b2Difficulty = block1b1Difficulty + 1;
        List<Transaction> txs1b2 = new ArrayList<>(Arrays.asList(tx2));
        Block block1b2 = createBlockForCanonical(world, block1b1, block1b2Difficulty, txs1b2);
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1b2));

        String hashAsString1a = toJsonHex(block1a.getHash().getBytes());
        String hashAsString1b2 = toJsonHex(block1b2.getHash().getBytes());


        // Short Chain - non canonical
        BlockParsedRequestDTO blockParsedRequestDto1aOne = new BlockParsedRequestDTO(null, hashAsString1a, false);
        String count1a = web3.eth_getTransactionCount(accountAddress, blockParsedRequestDto1aOne);
        String expectedTransactionCount1a = toQuantityJsonHex(1L);
        assertEquals(expectedTransactionCount1a, count1a);

        // Short chain - canonical
        BlockParsedRequestDTO blockParsedRequestDto1aTwo = new BlockParsedRequestDTO(null, hashAsString1a, true);
        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                               () -> web3.eth_getTransactionCount(accountAddress, blockParsedRequestDto1aTwo));

        // Long Chain - non canonical
        BlockParsedRequestDTO blockParsedRequestDto1b2One = new BlockParsedRequestDTO(null, hashAsString1b2, false);
        String count1b2One = web3.eth_getTransactionCount(accountAddress, blockParsedRequestDto1b2One);
        String expectedTransactionCount1b2One = toQuantityJsonHex(1L);
        assertEquals(expectedTransactionCount1b2One, count1b2One);

        // Long Chain - canonical
        BlockParsedRequestDTO blockParsedRequestDto1b2Two = new BlockParsedRequestDTO(null, hashAsString1b2, true);
        String count1b2Two = web3.eth_getTransactionCount(accountAddress, blockParsedRequestDto1b2Two);
        String expectedTransactionCount1b2Two = toQuantityJsonHex(1L);
        assertEquals(expectedTransactionCount1b2Two, count1b2Two);
    }

    @Test
    public void getTransactionCount_byBlockNumber() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(100000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());

        BlockParsedRequestDTO blockParsedRequestDto = new BlockParsedRequestDTO("0x1");
        String count = web3.eth_getTransactionCount(accountAddress, blockParsedRequestDto);

        assertNotNull(count);
        assertEquals("0x1", count);

        count = web3.eth_getTransactionCount(accountAddress, "0x0");

        assertNotNull(count);
        assertEquals("0x0", count);
    }

    @Test
    public void getBlockByNumber() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(2).parent(genesis).build();
        block1b.setBitcoinMergedMiningHeader(new byte[]{0x01});
        Block block2b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(11).parent(block1b).build();
        block2b.setBitcoinMergedMiningHeader(new byte[] { 0x02 });
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        BlockResultDTO bresult = web3.eth_getBlockByNumber("0x1", false);

        assertNotNull(bresult);

        String blockHash = "0x" + block1b.getHash();
        assertEquals(blockHash, bresult.getHash());

        String bnOrId = "0x2";
        bresult = web3.eth_getBlockByNumber("0x2", true);

        assertNotNull(bresult);

        blockHash = "0x" + block2b.getHash();
        assertEquals(blockHash, bresult.getHash());

        String hexString = web3.rsk_getRawBlockHeaderByNumber(bnOrId).replace("0x","");
        Keccak256  obtainedBlockHash = new Keccak256(HashUtil.keccak256(Hex.decode(hexString)));
        assertEquals(blockHash, obtainedBlockHash.toJsonString());
    }

    @Test
    public void getBlocksByNumber() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(block1.getDifficulty().asBigInteger().longValue()-1).parent(genesis).build();
        block1b.setBitcoinMergedMiningHeader(new byte[]{0x01});
        Block block2b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(2).parent(block1b).build();
        block2b.setBitcoinMergedMiningHeader(new byte[] { 0x02 });
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        Web3.BlockInformationResult[] bresult = web3.eth_getBlocksByNumber("0x1");

        String hashBlock1String = block1.getHashJsonString();
        String hashBlock1bString = block1b.getHashJsonString();

        assertNotNull(bresult);

        assertEquals(2, bresult.length);
        assertEquals(hashBlock1String, bresult[0].hash);
        assertEquals(hashBlock1bString, bresult[1].hash);
    }

    @Test
    public void getBlockByNumberRetrieveLatestBlock() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();

        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).build();
        block1.setBitcoinMergedMiningHeader(new byte[] { 0x01 });
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        BlockResultDTO blockResult = web3.eth_getBlockByNumber("latest", false);

        assertNotNull(blockResult);
        String blockHash = TypeConverter.toJsonHex(block1.getHash().toString());
        assertEquals(blockHash, blockResult.getHash());
    }

    @Test
    public void getBlockByNumberRetrieveEarliestBlock() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();

        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String bnOrId = "earliest";
        BlockResultDTO blockResult = web3.eth_getBlockByNumber(bnOrId, false);

        assertNotNull(blockResult);

        String blockHash = genesis.getHashJsonString();
        assertEquals(blockHash, blockResult.getHash());

        String hexString = web3.rsk_getRawBlockHeaderByNumber(bnOrId).replace("0x","");
        Keccak256  obtainedBlockHash = new Keccak256(HashUtil.keccak256(Hex.decode(hexString)));
        assertEquals(blockHash, obtainedBlockHash.toJsonString());
    }

    @Test
    public void getBlockByNumberBlockDoesNotExists() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        String bnOrId = "0x1234";
        BlockResultDTO blockResult = web3.eth_getBlockByNumber(bnOrId, false);

        Assert.assertNull(blockResult);

        String hexString = web3.rsk_getRawBlockHeaderByNumber(bnOrId);
        Assert.assertNull(hexString);
    }

    @Test(expected=org.ethereum.rpc.exception.RskJsonRpcRequestException.class)
    public void getBlockByNumberWhenNumberIsInvalidThrowsException() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        String bnOrId = "991234";
        web3.eth_getBlockByNumber(bnOrId, false);
    }

    @Test
    public void getBlockByHash() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        block1.setBitcoinMergedMiningHeader(new byte[] { 0x01 });
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        Block block1b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(block1.getDifficulty().asBigInteger().longValue()-1).parent(genesis).build();
        block1b.setBitcoinMergedMiningHeader(new byte[] { 0x01 });
        Block block2b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                         world.getBlockStore()).trieStore(world.getTrieStore()).difficulty(2).parent(block1b).build();
        block2b.setBitcoinMergedMiningHeader(new byte[] { 0x02 });
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2b));

        String block1HashString = "0x" + block1.getHash();
        String block1bHashString = "0x" + block1b.getHash();
        String block2bHashString = "0x" + block2b.getHash();

        BlockResultDTO bresult = web3.eth_getBlockByHash(block1HashString, false);

        assertNotNull(bresult);
        assertEquals(block1HashString, bresult.getHash());
        assertEquals("0x", bresult.getExtraData());
        assertEquals(0, bresult.getTransactions().size());
        assertEquals(0, bresult.getUncles().size());
        assertEquals("0xa", bresult.getDifficulty());
        assertEquals("0xb", bresult.getTotalDifficulty());
        bresult = web3.eth_getBlockByHash(block1bHashString, true);

        assertNotNull(bresult);
        assertEquals(block1bHashString, bresult.getHash());

        String hexString = web3.rsk_getRawBlockHeaderByHash(block1bHashString).replace("0x","");
        Keccak256  blockHash = new Keccak256(HashUtil.keccak256(Hex.decode(hexString)));
        assertEquals(blockHash.toJsonString(), block1bHashString);

        bresult = web3.eth_getBlockByHash(block2bHashString, true);

        assertNotNull(bresult);
        assertEquals(block2bHashString, bresult.getHash());

        hexString = web3.rsk_getRawBlockHeaderByHash(block2bHashString).replace("0x","");
        blockHash = new Keccak256(HashUtil.keccak256(Hex.decode(hexString)));
        assertEquals(blockHash.toJsonString(), block2bHashString);
    }

    @Test
    public void getBlockByHashWithFullTransactionsAsResult() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(220000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(0)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        block1.setBitcoinMergedMiningHeader(new byte[]{0x01});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String block1HashString = block1.getHashJsonString();

        BlockResultDTO bresult = web3.eth_getBlockByHash(block1HashString, true);

        assertNotNull(bresult);
        assertEquals(block1HashString, bresult.getHash());
        assertEquals(1, bresult.getTransactions().size());
        assertEquals(block1HashString, ((TransactionResultDTO) bresult.getTransactions().get(0)).blockHash);
        assertEquals(0, bresult.getUncles().size());
        assertEquals("0x0", ((TransactionResultDTO) bresult.getTransactions().get(0)).value);
    }

    @Test
    public void getBlockByHashWithTransactionsHashAsResult() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(220000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(0)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        block1.setBitcoinMergedMiningHeader(new byte[] { 0x01 });
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String block1HashString = block1.getHashJsonString();

        BlockResultDTO bresult = web3.eth_getBlockByHash(block1HashString, false);

        assertNotNull(bresult);
        assertEquals(block1HashString, bresult.getHash());
        assertEquals(1, bresult.getTransactions().size());
        assertEquals(tx.getHash().toJsonString(), bresult.getTransactions().get(0));
        assertEquals(0, bresult.getUncles().size());
    }

    @Test
    public void getBlockByHashBlockDoesNotExists() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        String blockHash = "0x1234000000000000000000000000000000000000000000000000000000000000";
        BlockResultDTO blockResult = web3.eth_getBlockByHash(blockHash, false);

        Assert.assertNull(blockResult);

        String hexString = web3.rsk_getRawBlockHeaderByHash(blockHash);
        Assert.assertNull(hexString);
    }

    @Test
    public void getBlockByHashBlockWithUncles() {
        World world = new World();
        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .difficulty(20)
                .parent(genesis)
                .build();
        block1.setBitcoinMergedMiningHeader(new byte[]{0x01});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        Block block1b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .difficulty(10)
                .parent(genesis)
                .build();
        block1b.setBitcoinMergedMiningHeader(new byte[]{0x02});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));

        Block block1c = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .difficulty(10)
                .parent(genesis)
                .build();
        block1c.setBitcoinMergedMiningHeader(new byte[]{0x03});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1c));

        ArrayList<BlockHeader> uncles = new ArrayList<>();
        uncles.add(block1b.getHeader());
        uncles.add(block1c.getHeader());

        Block block2 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .difficulty(10)
                .parent(block1)
                .uncles(uncles)
                .build();
        block2.setBitcoinMergedMiningHeader(new byte[]{0x04});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2));

        String block1HashString = "0x" + block1.getHash();
        String block1bHashString = "0x" + block1b.getHash();
        String block1cHashString = "0x" + block1c.getHash();
        String block2HashString = "0x" + block2.getHash();

        BlockResultDTO result = web3.eth_getBlockByHash(block2HashString, false);

        assertEquals(block2HashString, result.getHash());
        assertEquals(block1HashString, result.getParentHash());
        assertTrue(result.getUncles().contains(block1bHashString));
        assertTrue(result.getUncles().contains(block1cHashString));
        assertEquals(TypeConverter.toQuantityJsonHex(30), result.getCumulativeDifficulty());
    }

    @Test
    public void getBlockByNumberBlockWithUncles() {
        World world = new World();
        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .difficulty(20)
                .parent(genesis)
                .build();
        block1.setBitcoinMergedMiningHeader(new byte[]{0x01});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        Block block1b = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .difficulty(10)
                .parent(genesis)
                .build();
        block1b.setBitcoinMergedMiningHeader(new byte[]{0x02});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));

        Block block1c = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .difficulty(10)
                .parent(genesis)
                .build();
        block1c.setBitcoinMergedMiningHeader(new byte[]{0x03});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1c));

        ArrayList<BlockHeader> uncles = new ArrayList<>();
        uncles.add(block1b.getHeader());
        uncles.add(block1c.getHeader());

        Block block2 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .difficulty(10)
                .parent(block1)
                .uncles(uncles)
                .build();
        block2.setBitcoinMergedMiningHeader(new byte[]{0x04});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2));

        String block1HashString = "0x" + block1.getHash();
        String block1bHashString = "0x" + block1b.getHash();
        String block1cHashString = "0x" + block1c.getHash();
        String block2HashString = "0x" + block2.getHash();

        BlockResultDTO result = web3.eth_getBlockByNumber("0x02", false);

        assertEquals(block2HashString, result.getHash());
        assertEquals(block1HashString, result.getParentHash());
        assertTrue(result.getUncles().contains(block1bHashString));
        assertTrue(result.getUncles().contains(block1cHashString));
        assertEquals(TypeConverter.toQuantityJsonHex(30), result.getCumulativeDifficulty());
    }

    @Test
    public void getUncleByBlockHashAndIndexBlockWithUncles() {
        /* Structure:
         *    Genesis
         * |     |     |
         * A     B     C
         * | \  / ____/
         * D  E
         * | /
         * F
         *
         * A-D-F mainchain
         * B and C uncles of E
         * E uncle of F
         * */
        World world = new World();
        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block blockA = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockA.setBitcoinMergedMiningHeader(new byte[]{0x01});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockA));

        Block blockB = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockB.setBitcoinMergedMiningHeader(new byte[]{0x02});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockB));

        Block blockC = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockC.setBitcoinMergedMiningHeader(new byte[]{0x03});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockC));

        // block D must have a higher difficulty than block E and its uncles so it doesn't fall behind due to a reorg
        Block blockD = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(100).parent(blockA).build();
        blockD.setBitcoinMergedMiningHeader(new byte[]{0x04});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockD));

        List<BlockHeader> blockEUncles = Arrays.asList(blockB.getHeader(), blockC.getHeader());
        Block blockE = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(blockA).uncles(blockEUncles).build();
        blockE.setBitcoinMergedMiningHeader(new byte[]{0x05});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockE));

        List<BlockHeader> blockFUncles = Arrays.asList(blockE.getHeader());
        Block blockF = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(blockD).uncles(blockFUncles).build();
        blockF.setBitcoinMergedMiningHeader(new byte[]{0x06});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockF));

        String blockFhash = "0x" + blockF.getHash();
        String blockEhash = "0x" + blockE.getHash();
        String blockBhash = "0x" + blockB.getHash();
        String blockChash = "0x" + blockC.getHash();

        BlockResultDTO result = web3.eth_getUncleByBlockHashAndIndex(blockFhash, "0x00");

        assertEquals(blockEhash, result.getHash());
        assertEquals(2, result.getUncles().size());
        assertTrue(result.getUncles().contains(blockBhash));
        assertTrue(result.getUncles().contains(blockChash));
        assertEquals(TypeConverter.toQuantityJsonHex(30), result.getCumulativeDifficulty());
    }

    @Test
    public void getUncleByBlockHashAndIndexBlockWithUnclesCorrespondingToAnUnknownBlock() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(10000)).build();

        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block blockA = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockA.setBitcoinMergedMiningHeader(new byte[]{0x01});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockA));

        Block blockB = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockB.setBitcoinMergedMiningHeader(new byte[]{0x02});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockB));

        Block blockC = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockC.setBitcoinMergedMiningHeader(new byte[]{0x03});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockC));

        // block D must have a higher difficulty than block E and its uncles so it doesn't fall behind due to a reorg
        Block blockD = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(100).parent(blockA).build();
        blockD.setBitcoinMergedMiningHeader(new byte[]{0x04});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockD));

        Transaction tx = new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        List<BlockHeader> blockEUncles = Arrays.asList(blockB.getHeader(), blockC.getHeader());
        Block blockE = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(blockA).uncles(blockEUncles)
                .transactions(txs).buildWithoutExecution();

        blockE.setBitcoinMergedMiningHeader(new byte[]{0x05});

        assertEquals(1, blockE.getTransactionsList().size());
        Assert.assertFalse(Arrays.equals(blockC.getTxTrieRoot(), blockE.getTxTrieRoot()));

        List<BlockHeader> blockFUncles = Arrays.asList(blockE.getHeader());
        Block blockF = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(blockD).uncles(blockFUncles).build();
        blockF.setBitcoinMergedMiningHeader(new byte[]{0x06});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockF));

        String blockFhash = "0x" + blockF.getHash();
        String blockEhash = "0x" + blockE.getHash();

        BlockResultDTO result = web3.eth_getUncleByBlockHashAndIndex(blockFhash, "0x00");

        assertEquals(blockEhash, result.getHash());
        assertEquals(0, result.getUncles().size());
        assertEquals(0, result.getTransactions().size());
        assertEquals("0x" + ByteUtil.toHexString(blockE.getTxTrieRoot()), result.getTransactionsRoot());
    }

    @Test
    public void getUncleByBlockNumberAndIndexBlockWithUncles() {
        /* Structure:
         *    Genesis
         * |     |     |
         * A     B     C
         * | \  / ____/
         * D  E
         * | /
         * F
         *
         * A-D-F mainchain
         * B and C uncles of E
         * E uncle of F
         * */
        World world = new World();
        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block blockA = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockA.setBitcoinMergedMiningHeader(new byte[]{0x01});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockA));

        Block blockB = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockB.setBitcoinMergedMiningHeader(new byte[]{0x02});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockB));

        Block blockC = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockC.setBitcoinMergedMiningHeader(new byte[]{0x03});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockC));

        // block D must have a higher difficulty than block E and its uncles so it doesn't fall behind due to a reorg
        Block blockD = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(100).parent(blockA).build();
        blockD.setBitcoinMergedMiningHeader(new byte[]{0x04});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockD));

        List<BlockHeader> blockEUncles = Arrays.asList(blockB.getHeader(), blockC.getHeader());
        Block blockE = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(blockA).uncles(blockEUncles).build();
        blockE.setBitcoinMergedMiningHeader(new byte[]{0x05});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockE));

        List<BlockHeader> blockFUncles = Arrays.asList(blockE.getHeader());
        Block blockF = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(blockD).uncles(blockFUncles).build();
        blockF.setBitcoinMergedMiningHeader(new byte[]{0x06});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockF));

        String blockEhash = "0x" + blockE.getHash();
        String blockBhash = "0x" + blockB.getHash();
        String blockChash = "0x" + blockC.getHash();

        BlockResultDTO result = web3.eth_getUncleByBlockNumberAndIndex("0x03", "0x00");

        assertEquals(blockEhash, result.getHash());
        assertEquals(2, result.getUncles().size());
        assertTrue(result.getUncles().contains(blockBhash));
        assertTrue(result.getUncles().contains(blockChash));
        assertEquals(TypeConverter.toQuantityJsonHex(30), result.getCumulativeDifficulty());
    }

    @Test
    public void getUncleByBlockNumberAndIndexBlockWithUnclesCorrespondingToAnUnknownBlock() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(10000)).build();

        Web3Impl web3 = createWeb3(world);

        Block genesis = world.getBlockChain().getBestBlock();
        Block blockA = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockA.setBitcoinMergedMiningHeader(new byte[]{0x01});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockA));

        Block blockB = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockB.setBitcoinMergedMiningHeader(new byte[]{0x02});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockB));

        Block blockC = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(genesis).build();
        blockC.setBitcoinMergedMiningHeader(new byte[]{0x03});
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(blockC));

        // block D must have a higher difficulty than block E and its uncles so it doesn't fall behind due to a reorg
        Block blockD = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(100).parent(blockA).build();
        blockD.setBitcoinMergedMiningHeader(new byte[]{0x04});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockD));

        Transaction tx = new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        List<BlockHeader> blockEUncles = Arrays.asList(blockB.getHeader(), blockC.getHeader());
        Block blockE = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(blockA).uncles(blockEUncles)
                .transactions(txs).buildWithoutExecution();

        blockE.setBitcoinMergedMiningHeader(new byte[]{0x05});

        assertEquals(1, blockE.getTransactionsList().size());
        Assert.assertFalse(Arrays.equals(blockC.getTxTrieRoot(), blockE.getTxTrieRoot()));

        List<BlockHeader> blockFUncles = Arrays.asList(blockE.getHeader());
        Block blockF = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore()).difficulty(10).parent(blockD).uncles(blockFUncles).build();
        blockF.setBitcoinMergedMiningHeader(new byte[]{0x06});
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(blockF));

        String blockEhash = "0x" + blockE.getHash();

        BlockResultDTO result = web3.eth_getUncleByBlockNumberAndIndex("0x" + blockF.getNumber(), "0x00");

        assertEquals(blockEhash, result.getHash());
        assertEquals(0, result.getUncles().size());
        assertEquals(0, result.getTransactions().size());
        assertEquals("0x" + ByteUtil.toHexString(blockE.getTxTrieRoot()), result.getTransactionsRoot());
    }

    @Test
    public void getCode() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(100000000)).build();
        byte[] code = new byte[] { 0x01, 0x02, 0x03 };
        world.getRepository().saveCode(acc1.getAddress(), code);
        Block genesis = world.getBlockChain().getBestBlock();
        genesis.setStateRoot(world.getRepository().getRoot());
        genesis.flushRLP();
        world.getBlockStore().saveBlock(genesis, genesis.getCumulativeDifficulty(), true);
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());

        String scode = web3.eth_getCode(accountAddress, "0x1");

        assertNotNull(scode);
        assertEquals("0x" + ByteUtil.toHexString(code), scode);
    }

    @Test
    public void getCode_blockParsedRequest_withBlockNumber() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(100000000)).build();
        byte[] code = new byte[] { 0x01, 0x02, 0x03 };
        world.getRepository().saveCode(acc1.getAddress(), code);
        Block genesis = world.getBlockChain().getBestBlock();
        genesis.setStateRoot(world.getRepository().getRoot());
        genesis.flushRLP();
        world.getBlockStore().saveBlock(genesis, genesis.getCumulativeDifficulty(), true);
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).build();
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());

        BlockParsedRequestDTO blockParsedRequestDto = new BlockParsedRequestDTO("0x1");
        String scode = web3.eth_getCode(accountAddress, blockParsedRequestDto);

        assertNotNull(scode);
        assertEquals("0x" + ByteUtil.toHexString(code), scode);
    }

    @Test
    public void getCode_blockParsedRequest_withBlockHash() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world)
                .name("acc1")
                .balance(Coin.valueOf(100000000))
                .build();
        byte[] code = new byte[] { 0x01, 0x02, 0x03 };

        world.getRepository().saveCode(acc1.getAddress(), code);
        Block genesis = world.getBlockChain().getBestBlock();
        genesis.setStateRoot(world.getRepository().getRoot());
        genesis.flushRLP();

        world.getBlockStore()
                .saveBlock(genesis, genesis.getCumulativeDifficulty(), true);

        // Short Chain
        long block1aDifficulty = 10;
        Block block1a = createBlockForCanonical(world, genesis, block1aDifficulty, null);
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1a));

        // Long Chain
        long difficulty1bDifficulty = block1aDifficulty - 1;
        Block block1b = createBlockForCanonical(world, genesis, difficulty1bDifficulty, null);
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));

        long difficulty1b1Difficulty = block1aDifficulty + 1;
        Block block1b1 = createBlockForCanonical(world, block1b, difficulty1b1Difficulty, null);
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1b1));

        String hashAsString1a = toJsonHex(block1a.getHash().getBytes());
        String hashAsString1b1 = toJsonHex(block1b1.getHash().getBytes());

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());

        BlockParsedRequestDTO blockParsedRequestDto1aOne = new BlockParsedRequestDTO(null, hashAsString1a, false);
        String scode1aOne = web3.eth_getCode(accountAddress, blockParsedRequestDto1aOne);
        assertNotNull(scode1aOne);
        assertEquals(toUnformattedJsonHex(code), scode1aOne);

        BlockParsedRequestDTO blockParsedRequestDto1aTwo = new BlockParsedRequestDTO(null, hashAsString1a, true);
        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                               () -> web3.eth_getCode(accountAddress, blockParsedRequestDto1aTwo));

        BlockParsedRequestDTO blockParsedRequestDto1b1One = new BlockParsedRequestDTO(null, hashAsString1b1, false);
        String scode1b1One = web3.eth_getCode(accountAddress, blockParsedRequestDto1b1One);
        assertNotNull(scode1b1One);
        assertEquals(toUnformattedJsonHex(code), scode1b1One);

        BlockParsedRequestDTO blockParsedRequestDto1b1Two = new BlockParsedRequestDTO(null, hashAsString1b1, true);
        String scode1b1Two = web3.eth_getCode(accountAddress, blockParsedRequestDto1b1Two);
        assertNotNull(scode1b1Two);
        assertEquals(toUnformattedJsonHex(code), scode1b1Two);
    }

    @Test
    public void callFromDefaultAddressInWallet() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("default").balance(Coin.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");

        /* contract compiled in data attribute of tx
        contract Greeter {
            address owner;

            function greeter() public {
                owner = msg.sender;
            }

            function greet(string memory param) public pure returns (string memory) {
                return param;
            }
        }
        */

        Transaction tx = new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(500000))
                .gasPrice(BigInteger.ONE)
                .data("608060405234801561001057600080fd5b506101fa806100206000396000f3fe608060405234801561001057600080fd5b50600436106100365760003560e01c80631c8499e51461003b578063ead710c414610045575b600080fd5b610043610179565b005b6100fe6004803603602081101561005b57600080fd5b810190808035906020019064010000000081111561007857600080fd5b82018360208201111561008a57600080fd5b803590602001918460018302840111640100000000831117156100ac57600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600081840152601f19601f8201169050808301925050505050505091929192905050506101bb565b6040518080602001828103825283818151815260200191508051906020019080838360005b8381101561013e578082015181840152602081019050610123565b50505050905090810190601f16801561016b5780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b336000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550565b606081905091905056fea265627a7a723158207cbf5ab8312143442836de7909c83aec5160dae50224ecc7c16d7f35a306901e64736f6c63430005100032")
                .build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertTrue(world.getBlockChain().tryToConnect(block1).isSuccessful());

        Web3Impl web3 = createWeb3Mocked(world);

        Web3.CallArguments argsForCall = new Web3.CallArguments();
        argsForCall.to = TypeConverter.toJsonHex(tx.getContractAddress().getBytes());
        argsForCall.data = "ead710c40000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000568656c6c6f000000000000000000000000000000000000000000000000000000";

        String result = web3.eth_call(argsForCall, "latest");

        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000568656c6c6f000000000000000000000000000000000000000000000000000000", result);
    }

    @Test
    public void callFromAddressInWallet() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");

        /* contract compiled in data attribute of tx
        contract Greeter {
            address owner;

            function greeter() public {
                owner = msg.sender;
            }

            function greet(string memory param) public pure returns (string memory) {
                return param;
            }
        }
        */
        Transaction tx = new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(500000))
                .gasPrice(BigInteger.ONE)
                .data("608060405234801561001057600080fd5b506101fa806100206000396000f3fe608060405234801561001057600080fd5b50600436106100365760003560e01c80631c8499e51461003b578063ead710c414610045575b600080fd5b610043610179565b005b6100fe6004803603602081101561005b57600080fd5b810190808035906020019064010000000081111561007857600080fd5b82018360208201111561008a57600080fd5b803590602001918460018302840111640100000000831117156100ac57600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600081840152601f19601f8201169050808301925050505050505091929192905050506101bb565b6040518080602001828103825283818151815260200191508051906020019080838360005b8381101561013e578082015181840152602081019050610123565b50505050905090810190601f16801561016b5780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b336000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550565b606081905091905056fea265627a7a723158207cbf5ab8312143442836de7909c83aec5160dae50224ecc7c16d7f35a306901e64736f6c63430005100032")
                .build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                                        world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertTrue(world.getBlockChain().tryToConnect(block1).isSuccessful());

        Web3Impl web3 = createWeb3Mocked(world);

        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");

        Web3.CallArguments argsForCall = new Web3.CallArguments();
        argsForCall.from = TypeConverter.toJsonHex(acc1.getAddress().getBytes());
        argsForCall.to = TypeConverter.toJsonHex(tx.getContractAddress().getBytes());
        argsForCall.data = "ead710c40000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000568656c6c6f000000000000000000000000000000000000000000000000000000";

        String result = web3.eth_call(argsForCall, "latest");

        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000568656c6c6f000000000000000000000000000000000000000000000000000000", result);
    }

    @Test
    public void callNoneContractReturn() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("default").balance(Coin.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");

        TestContract contract = TestContract.noReturn();

        Transaction tx = new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(500000))
                .gasPrice(BigInteger.ONE)
                .data(Hex.decode(contract.bytecode))
                .build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        assertTrue(world.getBlockChain().tryToConnect(block1).isSuccessful());

        Web3Impl web3 = createWeb3Mocked(world);

        CallTransaction.Function func = contract.functions.get("noreturn");
        Web3.CallArguments argsForCall = new Web3.CallArguments();
        argsForCall.to = TypeConverter.toUnformattedJsonHex(tx.getContractAddress().getBytes());
        argsForCall.data = TypeConverter.toUnformattedJsonHex(func.encode());

        String result = web3.eth_call(argsForCall, "latest");

        assertEquals("0x", result);
    }

    @Test
    public void callFromAddressInWallet_withBlockHash() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");

        Transaction tx = new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(500000))
                .gasPrice(BigInteger.ONE)
                .data("608060405234801561001057600080fd5b506101fa806100206000396000f3fe608060405234801561001057600080fd5b50600436106100365760003560e01c80631c8499e51461003b578063ead710c414610045575b600080fd5b610043610179565b005b6100fe6004803603602081101561005b57600080fd5b810190808035906020019064010000000081111561007857600080fd5b82018360208201111561008a57600080fd5b803590602001918460018302840111640100000000831117156100ac57600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600081840152601f19601f8201169050808301925050505050505091929192905050506101bb565b6040518080602001828103825283818151815260200191508051906020019080838360005b8381101561013e578082015181840152602081019050610123565b50505050905090810190601f16801561016b5780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b336000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550565b606081905091905056fea265627a7a723158207cbf5ab8312143442836de7909c83aec5160dae50224ecc7c16d7f35a306901e64736f6c63430005100032")
                .build();

        List<Transaction> txs = Arrays.asList(tx);

        Block block1a = createBlockForCanonical(world, genesis, 4, txs);
        Block block1b = createBlockForCanonical(world, genesis, 1, txs);
        Block block1b1 = createBlockForCanonical(world, block1b, 5, txs);

        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1a));
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1b1));

        Web3Impl web3 = createWeb3Mocked(world);

        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");

        Web3.CallArguments argsForCall = new Web3.CallArguments();
        argsForCall.from = toJsonHex(acc1.getAddress().getBytes());
        argsForCall.to = toJsonHex(tx.getContractAddress().getBytes());
        argsForCall.data = "ead710c40000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000568656c6c6f000000000000000000000000000000000000000000000000000000";

        String expectedResult = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000568656c6c6f000000000000000000000000000000000000000000000000000000";

        // Short Chain - require Canonical false
        BlockParsedRequestDTO blockParsedRequest1aOne = new BlockParsedRequestDTO(null, block1a.getHashJsonString(), false);
        String result1aOne = web3.eth_call(argsForCall, blockParsedRequest1aOne);
        assertEquals(expectedResult, result1aOne);

        // Short Chain - require Canonical true
        BlockParsedRequestDTO blockParsedRequest1aTwo = new BlockParsedRequestDTO(null, block1a.getHashJsonString(), true);
        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                               () -> web3.eth_call(argsForCall, blockParsedRequest1aTwo));

        // Main Chain - require Canonical false
        BlockParsedRequestDTO blockParsedRequest1bOne = new BlockParsedRequestDTO(null, block1b1.getHashJsonString(), false);
        String result1b1One = web3.eth_call(argsForCall, blockParsedRequest1bOne);
        assertEquals(expectedResult, result1b1One);

        // Main Chain - require Canonical true
        BlockParsedRequestDTO blockParsedRequest1b1Two = new BlockParsedRequestDTO(null, block1b1.getHashJsonString(), true);
        String result1b1Two = web3.eth_call(argsForCall, blockParsedRequest1b1Two);
        assertEquals(expectedResult, result1b1Two);
    }

    @Test
    public void getStorageAt_withBlockNumber() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());
        String storageIdx = "0x01";

        Block genesis = world.getBlockByName("g00");

        Block block1 = createBlockForCanonical(world, genesis, 4, null);
        Block block2 = createBlockForCanonical(world, block1, 5, null);

        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2));

        Web3Impl web3 = createWeb3Mocked(world);

        String blockNumber = toQuantityJsonHex(block2.getNumber());
        BlockParsedRequestDTO blockParsedRequest = new BlockParsedRequestDTO(blockNumber);
        String result = web3.eth_getStorageAt(accountAddress, storageIdx, blockParsedRequest);
        Assert.assertEquals("0x0", result);
    }

    @Test
    public void getStorageAt_withBlockHash() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("notDefault").balance(Coin.valueOf(10000000)).build();

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());
        String storageIdx = "0x01";

        Block genesis = world.getBlockByName("g00");

        Block block1a = createBlockForCanonical(world, genesis, 4, null);
        Block block1b = createBlockForCanonical(world, genesis, 1, null);
        Block block1b1 = createBlockForCanonical(world, block1b, 5, null);

        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1a));
        assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1b1));

        Web3Impl web3 = createWeb3Mocked(world);

        BlockParsedRequestDTO blockParsedRequest1aOne = new BlockParsedRequestDTO(null, block1a.getHashJsonString(), false);
        String result1aOne = web3.eth_getStorageAt(accountAddress, storageIdx, blockParsedRequest1aOne);
        Assert.assertEquals("0x0", result1aOne);

        BlockParsedRequestDTO blockParsedRequest1aTwo = new BlockParsedRequestDTO(null, block1a.getHashJsonString(), true);
        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                               () -> web3.eth_getStorageAt(accountAddress, storageIdx, blockParsedRequest1aTwo));

        BlockParsedRequestDTO blockParsedRequest1b1One = new BlockParsedRequestDTO(null, block1b1.getHashJsonString(), false);
        String result1bOne = web3.eth_getStorageAt(accountAddress, storageIdx, blockParsedRequest1b1One);
        Assert.assertEquals("0x0", result1bOne);

        BlockParsedRequestDTO blockParsedRequest1b1Two = new BlockParsedRequestDTO(null, block1b1.getHashJsonString(), true);
        String result1bTwo = web3.eth_getStorageAt(accountAddress, storageIdx, blockParsedRequest1b1Two);
        Assert.assertEquals("0x0", result1bTwo);
    }

    @Test
    public void getCodeBlockDoesNotExist() {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(100000000)).build();
        byte[] code = new byte[] { 0x01, 0x02, 0x03 };
        world.getRepository().saveCode(acc1.getAddress(), code);

        String accountAddress = ByteUtil.toHexString(acc1.getAddress().getBytes());

        String resultCode = web3.eth_getCode(accountAddress, "0x100");

        assertNull(resultCode);
    }

    @Test
    public void net_listening()  {
        World world = new World();

        SimpleEthereum eth = new SimpleEthereum(world.getBlockChain());
        SimplePeerServer peerServer = new SimplePeerServer();

        Web3Impl web3 = createWeb3(eth, peerServer);

        assertTrue("Node is not listening", !web3.net_listening());

        peerServer.isListening = true;
        assertTrue("Node is listening", web3.net_listening());
    }

    @Test
    public void eth_coinbase()  {
        String originalCoinbase = "1dcc4de8dec75d7aab85b513f0a142fd40d49347";
        MinerServer minerServerMock = mock(MinerServer.class);
        when(minerServerMock.getCoinbaseAddress()).thenReturn(new RskAddress(originalCoinbase));

        Ethereum ethMock = Web3Mocks.getMockEthereum();
        Blockchain blockchain = Web3Mocks.getMockBlockchain();
        TransactionPool transactionPool = Web3Mocks.getMockTransactionPool();
        BlockStore blockStore = Web3Mocks.getMockBlockStore();
        RskSystemProperties mockProperties = Web3Mocks.getMockProperties();
        PersonalModule personalModule = new PersonalModuleWalletDisabled();
        Web3 web3 = new Web3Impl(
                ethMock,
                blockchain,
                blockStore,
                null,
                mockProperties,
                null,
                minerServerMock,
                personalModule,
                null,
                null,
                null,
                null,
                null,
                null, null,
                Web3Mocks.getMockChannelManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mock(Web3InformationRetriever.class));

        assertEquals("0x" + originalCoinbase, web3.eth_coinbase());
        verify(minerServerMock, times(1)).getCoinbaseAddress();
    }

    @Test
    public void eth_accounts()
    {
        Web3Impl web3 = createWeb3();
        int originalAccounts = web3.personal_listAccounts().length;

        String addr1 = web3.personal_newAccountWithSeed("sampleSeed1");
        String addr2 = web3.personal_newAccountWithSeed("sampleSeed2");

        Set<String> accounts = Arrays.stream(web3.eth_accounts()).collect(Collectors.toSet());

        assertEquals("Not all accounts are being retrieved", originalAccounts + 2, accounts.size());

        assertTrue(accounts.contains(addr1));
        assertTrue(accounts.contains(addr2));
    }

    @Test
    public void eth_sign()
    {
        Web3Impl web3 = createWeb3();

        String addr1 = web3.personal_newAccountWithSeed("sampleSeed1");

        byte[] hash = Keccak256Helper.keccak256("this is the data to hash".getBytes());

        String signature = web3.eth_sign(addr1, "0x" + ByteUtil.toHexString(hash));

        Assert.assertThat(
                signature,
                is("0xc8be87722c6452172a02a62fdea70c8b25cfc9613d28647bf2aeb3c7d1faa1a91b861fccc05bb61e25ff4300502812750706ca8df189a0b8163540b9bccabc9f1b")
        );
    }

    @Test
    public void createNewAccount()
    {
        Web3Impl web3 = createWeb3();

        String addr = web3.personal_newAccount("passphrase1");

        Account account = null;

        try {
            account = wallet.getAccount(new RskAddress(addr), "passphrase1");
        }
        catch (Exception e) {
            e.printStackTrace();
            return;
        }

        assertNotNull(account);
        assertEquals(addr, "0x" + ByteUtil.toHexString(account.getAddress().getBytes()));
    }

    @Test
    public void listAccounts()
    {
        Web3Impl web3 = createWeb3();
        int originalAccounts = web3.personal_listAccounts().length;

        String addr1 = web3.personal_newAccount("passphrase1");
        String addr2 = web3.personal_newAccount("passphrase2");

        Set<String> addresses = Arrays.stream(web3.personal_listAccounts()).collect(Collectors.toSet());

        assertNotNull(addresses);
        assertEquals(originalAccounts + 2, addresses.size());
        assertTrue(addresses.contains(addr1));
        assertTrue(addresses.contains(addr2));
    }

    @Test
    public void importAccountUsingRawKey()
    {
        Web3Impl web3 = createWeb3();

        ECKey eckey = new ECKey();

        byte[] privKeyBytes = eckey.getPrivKeyBytes();

        ECKey privKey = ECKey.fromPrivate(privKeyBytes);

        RskAddress addr = new RskAddress(privKey.getAddress());

        Account account = wallet.getAccount(addr);

        assertNull(account);

        String address = web3.personal_importRawKey(ByteUtil.toHexString(privKeyBytes), "passphrase1");

        assertNotNull(address);

        account = wallet.getAccount(addr);

        assertNotNull(account);
        assertEquals(address, "0x" + ByteUtil.toHexString(account.getAddress().getBytes()));
        assertArrayEquals(privKeyBytes, account.getEcKey().getPrivKeyBytes());
    }

    @Test
    public void dumpRawKey() throws Exception {
        Web3Impl web3 = createWeb3();

        ECKey eckey = new ECKey();

        String address = web3.personal_importRawKey(ByteUtil.toHexString(eckey.getPrivKeyBytes()), "passphrase1");
        assertTrue(web3.personal_unlockAccount(address, "passphrase1", ""));

        String rawKey = web3.personal_dumpRawKey(address).substring(2);

        assertArrayEquals(eckey.getPrivKeyBytes(), Hex.decode(rawKey));
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
        args.gas = TypeConverter.toQuantityJsonHex(gasLimit);
        args.gasPrice= TypeConverter.toQuantityJsonHex(gasPrice);
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
        Transaction tx = Transaction
                .builder()
                .destination(toAddress.substring(2))
                .value(value)
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gasLimit)
                .data(args.data)
                .chainId(config.getNetworkConstants().getChainId())
                .build();
        Account account = wallet.getAccount(new RskAddress(addr1), "passphrase1");
        tx.sign(account.getEcKey().getPrivKeyBytes());

        String expectedHash = tx.getHash().toJsonString();

        assertEquals("Method is not creating the expected transaction", 0, expectedHash.compareTo(txHash));
    }

    @Test
    public void unlockAccount()
    {
        Web3Impl web3 = createWeb3();

        String addr = web3.personal_newAccount("passphrase1");

        web3.personal_lockAccount(addr);

        assertTrue(web3.personal_unlockAccount(addr, "passphrase1", ""));

        Account account = wallet.getAccount(new RskAddress(addr));

        assertNotNull(account);
    }

    @Test
    public void unlockAccountInvalidDuration()
    {
        Web3Impl web3 = createWeb3();

        String addr = web3.personal_newAccount("passphrase1");

        web3.personal_lockAccount(addr);

        RskJsonRpcRequestException e = TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> web3.personal_unlockAccount(addr, "passphrase1", "K"));
        assertEquals(-32602, (int) e.getCode());
    }

    @Test
    public void lockAccount()
    {
        Web3Impl web3 = createWeb3();

        String addr = web3.personal_newAccount("passphrase1");

        Account account = wallet.getAccount(new RskAddress(addr));

        assertNotNull(account);

        assertTrue(web3.personal_lockAccount(addr));

        Account account1 = wallet.getAccount(new RskAddress(addr));

        assertNull(account1);
    }

    private Web3Impl createWeb3() {
        return createWeb3(
                Web3Mocks.getMockEthereum(), Web3Mocks.getMockBlockchain(), Web3Mocks.getMockRepositoryLocator(), Web3Mocks.getMockTransactionPool(),
                Web3Mocks.getMockBlockStore(), null, null, null
        );
    }

    @Test
    public void eth_sendTransactionWithValidChainId() {
        checkSendTransaction(config.getNetworkConstants().getChainId());
    }

    @Test
    public void eth_sendTransactionWithZeroChainId() {
        checkSendTransaction((byte) 0);
    }

    @Test
    public void eth_sendTransactionWithNoChainId() {
        checkSendTransaction(null);
    }

    @Test(expected = RskJsonRpcRequestException.class)
    public void eth_sendTransactionWithInvalidChainId() {
        checkSendTransaction((byte) 1); // chain id of Ethereum Mainnet
    }

    @Test
    public void createNewAccountWithoutDuplicates(){
        Web3Impl web3 = createWeb3();
        int originalAccountSize = wallet.getAccountAddresses().size();
        String testAccountAddress = web3.personal_newAccountWithSeed("testAccount");

        assertEquals("The number of accounts was not increased", originalAccountSize + 1, wallet.getAccountAddresses().size());

        web3.personal_newAccountWithSeed("testAccount");

        assertEquals("The number of accounts was increased", originalAccountSize + 1, wallet.getAccountAddresses().size());
    }

    private void checkSendTransaction(Byte chainId) {
        BigInteger nonce = BigInteger.ONE;
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        BlockChainImpl blockChain = world.getBlockChain();
        BlockStore blockStore = world.getBlockStore();
        TransactionExecutorFactory transactionExecutorFactory = buildTransactionExecutorFactory(blockStore, world.getBlockTxSignatureCache());
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepositoryLocator(), blockStore, blockFactory, null, transactionExecutorFactory, world.getReceivedTxSignatureCache(), 10, 100);
        Web3Impl web3 = createWeb3(world, transactionPool, receiptStore);

        // **** Initializes data ******************
        String[] accounts = web3.personal_listAccounts();
        String addr1 = accounts[0];
        String addr2 = accounts[1];
        transactionPool.processBest(blockChain.getBestBlock());

        String toAddress = addr2;
        BigInteger value = BigInteger.valueOf(7);
        BigInteger gasPrice = BigInteger.valueOf(8);
        BigInteger gasLimit = BigInteger.valueOf(300000);
        String data = "0xff";

        // ***** Executes the transaction *******************
        Web3.CallArguments args = new Web3.CallArguments();
        args.from = addr1;
        args.to = addr2;
        args.data = data;
        args.gas = TypeConverter.toQuantityJsonHex(gasLimit);
        args.gasPrice = TypeConverter.toQuantityJsonHex(gasPrice);
        args.value = value.toString();
        args.nonce = nonce.toString();
        if (chainId != null) {
            args.chainId = TypeConverter.toJsonHex(new byte[] { chainId });
        }

        String txHash = web3.eth_sendTransaction(args);

        // ***** Verifies tx hash
        String to = toAddress.substring(2);
        Transaction tx = Transaction
                .builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gasLimit)
                .destination(Hex.decode(to))
                .data(args.data == null ? null : Hex.decode(args.data))
                .chainId(config.getNetworkConstants().getChainId())
                .value(value)
                .build();
        tx.sign(wallet.getAccount(new RskAddress(addr1)).getEcKey().getPrivKeyBytes());

        String expectedHash = tx.getHash().toJsonString();

        assertEquals("Method is not creating the expected transaction", 0, expectedHash.compareTo(txHash));
    }

    @Test
    public void getNoCode() throws Exception {
        World world = new World();

        Web3Impl web3 = createWeb3(world);

        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(100000000)).build();
        Block genesis = world.getBlockChain().getBestBlock();
        genesis.setStateRoot(world.getRepository().getRoot());
        genesis.flushRLP();
        world.getBlockStore().saveBlock(genesis, genesis.getCumulativeDifficulty(), true);
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).build();
        org.junit.Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1));

        String accountAddress = Hex.toHexString(acc1.getAddress().getBytes());

        String scode = web3.eth_getCode(accountAddress, "0x1");

        assertNotNull(scode);
        org.junit.Assert.assertEquals("0x", scode);
    }

    @Test
    public void callWithoutReturn() {
        World world = new World();
        Account acc1 = new AccountBuilder(world).name("default").balance(Coin.valueOf(10000000)).build();

        Block genesis = world.getBlockByName("g00");
        TestContract noreturn = TestContract.noreturn();
        Transaction tx = new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data(noreturn.bytecode)
                .build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                world.getBlockStore()).trieStore(world.getTrieStore()).parent(genesis).transactions(txs).build();
        world.getBlockChain().tryToConnect(block1);

        Web3Impl web3 = createWeb3MockedCallNoReturn(world);

        Web3.CallArguments argsForCall = new Web3.CallArguments();
        argsForCall.to = TypeConverter.toJsonHex(tx.getContractAddress().getBytes());
        argsForCall.data = TypeConverter.toJsonHex(noreturn.functions.get("noreturn").encodeSignature());

        String result = web3.eth_call(argsForCall, "latest");

        org.junit.Assert.assertEquals("0x", result);
    }

    private Web3Impl createWeb3(World world) {
        return createWeb3(world, null);
    }

    private Web3Impl createWeb3(World world, ReceiptStore receiptStore) {
        return createWeb3(Web3Mocks.getMockEthereum(), world, Web3Mocks.getMockTransactionPool(), receiptStore);
    }

    private Web3Impl createWeb3Mocked(World world) {
        Ethereum ethMock = mock(Ethereum.class);
        return createWeb3(ethMock, world, null);
    }

    private Web3Impl createWeb3MockedCallNoReturn(World world) {
        Ethereum ethMock = mock(Ethereum.class);
        return createWeb3CallNoReturn(ethMock, world, null);
    }

    private Web3Impl createWeb3(World world, TransactionPool transactionPool, ReceiptStore receiptStore) {
        return createWeb3(Web3Mocks.getMockEthereum(), world, transactionPool, receiptStore);
    }

    private Web3Impl createWeb3(SimpleEthereum eth, PeerServer peerServer) {
        wallet = WalletFactory.createWallet();
        Blockchain blockchain = Web3Mocks.getMockBlockchain();
        BlockStore blockStore = Web3Mocks.getMockBlockStore();
        MiningMainchainView mainchainView = new MiningMainchainViewImpl(blockStore, 449);
        TransactionPool transactionPool = Web3Mocks.getMockTransactionPool();
        PersonalModuleWalletEnabled personalModule = new PersonalModuleWalletEnabled(config, eth, wallet, null);
        EthModule ethModule = new EthModule(
                config.getNetworkConstants().getBridgeConstants(), config.getNetworkConstants().getChainId(), blockchain, transactionPool,
                null, new ExecutionBlockRetriever(mainchainView, blockchain, null, null),
                null, new EthModuleWalletEnabled(wallet), null,
                new BridgeSupportFactory(
                        null, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig())
        );
        TxPoolModule txPoolModule = new TxPoolModuleImpl(Web3Mocks.getMockTransactionPool());
        DebugModule debugModule = new DebugModuleImpl(null, null, Web3Mocks.getMockMessageHandler(), null);
        MinerClient minerClient = new SimpleMinerClient();
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
                null, null,
                channelManager,
                null,
                null,
                null,
                null,
                peerServer,
                null,
                null,
                null,
                null,
                null,
                new Web3InformationRetriever(
                        transactionPool,
                        blockchain,
                        mock(RepositoryLocator.class)));
    }

    private Web3Impl createWeb3(Ethereum eth, World world, ReceiptStore receiptStore) {
        BlockStore blockStore = world.getBlockStore();
        TransactionExecutorFactory transactionExecutorFactory = buildTransactionExecutorFactory(blockStore, world.getBlockTxSignatureCache());
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepositoryLocator(), blockStore,
                                                                  blockFactory, null, transactionExecutorFactory, world.getReceivedTxSignatureCache(), 10, 100);
        return createWeb3(eth, world, transactionPool, receiptStore);
    }

    private Web3Impl createWeb3CallNoReturn(Ethereum eth, World world, ReceiptStore receiptStore) {
        BlockStore blockStore = world.getBlockStore();
        TransactionExecutorFactory transactionExecutorFactory = buildTransactionExecutorFactory(blockStore, null);
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepositoryLocator(), blockStore,
                blockFactory, null, transactionExecutorFactory, null, 10, 100);
        return createWeb3CallNoReturn(eth, world, transactionPool, receiptStore);
    }

    private Web3Impl createWeb3(Ethereum eth, World world, TransactionPool transactionPool, ReceiptStore receiptStore) {
        RepositoryLocator repositoryLocator = world.getRepositoryLocator();
        return createWeb3(
                eth, world.getBlockChain(), repositoryLocator, transactionPool, world.getBlockStore(),
                null, new SimpleConfigCapabilities(), receiptStore
        );
    }

    private Web3Impl createWeb3CallNoReturn(Ethereum eth, World world, TransactionPool transactionPool, ReceiptStore receiptStore) {
        RepositoryLocator repositoryLocator = world.getRepositoryLocator();
        return createWeb3CallNoReturn(
                eth, world.getBlockChain(), repositoryLocator, transactionPool, world.getBlockStore(),
                null, new SimpleConfigCapabilities(), receiptStore
        );
    }

    private Web3Impl createWeb3(World world, BlockProcessor blockProcessor, ReceiptStore receiptStore) {
        BlockChainImpl blockChain = world.getBlockChain();
        BlockStore blockStore = world.getBlockStore();
        TransactionExecutorFactory transactionExecutorFactory = buildTransactionExecutorFactory(blockStore, world.getBlockTxSignatureCache());
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepositoryLocator(),
                                                                  blockStore, blockFactory, null, transactionExecutorFactory, world.getReceivedTxSignatureCache(), 10, 100);
        RepositoryLocator repositoryLocator = new RepositoryLocator(world.getTrieStore(), world.getStateRootHandler());
        return createWeb3(
                Web3Mocks.getMockEthereum(), blockChain, repositoryLocator, transactionPool,
                blockStore, blockProcessor,
                new SimpleConfigCapabilities(), receiptStore
        );
    }

    private Web3Impl createWeb3(
            Ethereum eth,
            Blockchain blockchain,
            RepositoryLocator repositoryLocator,
            TransactionPool transactionPool,
            BlockStore blockStore,
            BlockProcessor nodeBlockProcessor,
            ConfigCapabilities configCapabilities,
            ReceiptStore receiptStore) {
        MiningMainchainView miningMainchainViewMock = mock(MiningMainchainView.class);
        wallet = WalletFactory.createWallet();
        PersonalModuleWalletEnabled personalModule = new PersonalModuleWalletEnabled(config, eth, wallet, transactionPool);

        ReversibleTransactionExecutor executor = new ReversibleTransactionExecutor(
            repositoryLocator,
            buildTransactionExecutorFactory(blockStore, null)
        );

        Web3InformationRetriever retriever = new Web3InformationRetriever(transactionPool, blockchain, repositoryLocator);
        TransactionGateway transactionGateway = new TransactionGateway(new SimpleChannelManager(), transactionPool);
        EthModule ethModule = new EthModule(
                config.getNetworkConstants().getBridgeConstants(), config.getNetworkConstants().getChainId(), blockchain, transactionPool, executor,
                new ExecutionBlockRetriever(miningMainchainViewMock, blockchain, null, null), repositoryLocator, new EthModuleWalletEnabled(wallet),
                new EthModuleTransactionBase(config.getNetworkConstants(), wallet, transactionPool, transactionGateway),
                new BridgeSupportFactory(
                        null, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig()));
        TxPoolModule txPoolModule = new TxPoolModuleImpl(transactionPool);
        DebugModule debugModule = new DebugModuleImpl(null, null, Web3Mocks.getMockMessageHandler(), null);
        RskModule rskModule = new RskModuleImpl(blockchain, blockStore, receiptStore, retriever);
        MinerClient minerClient = new SimpleMinerClient();
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
                rskModule,
                channelManager,
                null,
                null,
                blockStore,
                receiptStore,
                null,
                nodeBlockProcessor,
                null,
                configCapabilities,
                new BuildInfo("test", "test"),
                null,
                retriever
        );
    }

    private Web3Impl createWeb3CallNoReturn(
            Ethereum eth,
            Blockchain blockchain,
            RepositoryLocator repositoryLocator,
            TransactionPool transactionPool,
            BlockStore blockStore,
            BlockProcessor nodeBlockProcessor,
            ConfigCapabilities configCapabilities,
            ReceiptStore receiptStore) {
        MiningMainchainView miningMainchainViewMock = mock(MiningMainchainView.class);
        wallet = WalletFactory.createWallet();
        PersonalModuleWalletEnabled personalModule = new PersonalModuleWalletEnabled(config, eth, wallet, transactionPool);
        ReversibleTransactionExecutor executor = mock(ReversibleTransactionExecutor.class);
        ProgramResult res = new ProgramResult();
        res.setHReturn(new byte[0]);
        when(executor.executeTransaction(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(res);
        Web3InformationRetriever retriever = new Web3InformationRetriever(transactionPool, blockchain, repositoryLocator);
        EthModule ethModule = new EthModule(
                config.getNetworkConstants().getBridgeConstants(), config.getNetworkConstants().getChainId(), blockchain, transactionPool, executor,
                new ExecutionBlockRetriever(miningMainchainViewMock, blockchain, null, null), repositoryLocator,
                new EthModuleWalletEnabled(wallet),
                new EthModuleTransactionBase(config.getNetworkConstants(), wallet, transactionPool, null),
                new BridgeSupportFactory(
                        null, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig()));
        TxPoolModule txPoolModule = new TxPoolModuleImpl(transactionPool);
        DebugModule debugModule = new DebugModuleImpl(null, null, Web3Mocks.getMockMessageHandler(), null);
        RskModule rskModule = new RskModuleImpl(blockchain, blockStore, receiptStore, retriever);
        MinerClient minerClient = new SimpleMinerClient();
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
                rskModule,
                channelManager,
                null,
                null,
                blockStore,
                receiptStore,
                null,
                nodeBlockProcessor,
                null,
                configCapabilities,
                new BuildInfo("test", "test"),
                null,
                retriever);
    }

    private Block createBlockForCanonical(
            World world,
            Block parent,
            long difficulty,
            List<Transaction> txs) {
        BlockBuilder block1bWorldBuilder = new BlockBuilder(
                world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore());

        BlockBuilder blockBuilder = block1bWorldBuilder
                .trieStore(world.getTrieStore())
                .difficulty(difficulty)
                .parent(parent);

        Block block = (txs != null && txs.size() > 0)
            ? blockBuilder
                .transactions(txs)
                .build()
            : blockBuilder
                .build();

        return block;
    }

    private TransactionExecutorFactory buildTransactionExecutorFactory(
            BlockStore blockStore, BlockTxSignatureCache blockTxSignatureCache) {
        return new TransactionExecutorFactory(
                config,
                blockStore,
                null,
                blockFactory,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, null),
                blockTxSignatureCache);
    }
}
