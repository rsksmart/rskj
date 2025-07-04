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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.Wallet;
import co.rsk.core.WalletFactory;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.debug.trace.DebugTracer;
import co.rsk.rpc.modules.debug.trace.RskTracer;
import co.rsk.rpc.modules.debug.trace.TraceProvider;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.trie.TrieStore;
import co.rsk.util.HexUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.Simples.SimpleConfigCapabilities;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.*;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 30/11/2016.
 */
class Web3ImplLogsTest {

    // Events used:
    // event Incremented(bool indexed odd, uint x);
    // event Created(uint x);
    // event Valued(uint x);


    /// //////////////////////////////////////////////////////////////////////
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
    private static final String GET_VALUED_EVENT_SIGNATURE = "1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b";
    private static final String INC_EVENT_SIGNATURE = "6e61ef44ac2747ff8b84d353a908eb8bd5c3fb118334d57698c5cfc7041196ad";
    private static final String ONE_TOPIC = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String INCREMENT_METHOD_SIGNATURE = "371303c0";
    private static final String GET_VALUE_METHOD_SIGNATURE = "20965255";
    private static final String TRACKED_TEST_BLOCK_HASH = "0x93cdec69f2708ff7d744d607d59d2f59fc059579f7476e487206bc72ef2fd898";
    private static final String UNTRACKED_TEST_BLOCK_HASH = "0xdea168a4f74e51a3eeb6d72b049c4fc7bc750dd51f13a3afa4fee4bece0e85eb";
    private static final String COMPILED_LOG_EXAMPLE = "606060405234610000575b60bd806100186000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063195977a614603c575b6000565b34600057605460048080359060200190919050506056565b005b7ffd99bb34477b313b3e3b452b34d012d8315db36a1d63949d9d8f9d2573b05aff816040518082815260200191505060405180910390a15b505600a165627a7a72305820fb2550735b0655fb2fe03738be375a4c29ef1b6ff51004f869be19de0301f30b0029";
    private static final String COMPILED_SIMPLE_LOG_EMIT_BYTECODE = "608060405234801561000f575f80fd5b506101668061001d5f395ff3fe608060405234801561000f575f80fd5b5060043610610029575f3560e01c806346422b891461002d575b5f80fd5b610035610037565b005b7fccf3c03278b22671760afcb29bf4ab841c8a916185285211b5eeabaa1f384d3e4260405161006691906100bf565b60405180910390a17f08a3e137237eb3d8f53847a2913005de9056dd038b91dcdf8f9cf13bdf31208b3360405161009d9190610117565b60405180910390a1565b5f819050919050565b6100b9816100a7565b82525050565b5f6020820190506100d25f8301846100b0565b92915050565b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f610101826100d8565b9050919050565b610111816100f7565b82525050565b5f60208201905061012a5f830184610108565b9291505056fea2646970667358221220e36ef30a5e6d6e0007b35ec119efa4ac48c35bfa4c9f8cb108c655ea1be7988c64736f6c63430008180033";
    private static final String SIMPLE_LOG_METHOD_SIGNATURE = "46422b89";


    private final TestSystemProperties config = new TestSystemProperties();

    @TempDir
    public Path tempDir;
    private Blockchain blockChain;
    private RepositoryLocator repositoryLocator;
    private TransactionPool transactionPool;
    private Ethereum eth;
    private ReceiptStore receiptStore;
    private Web3Impl web3;
    private TrieStore trieStore;
    private BlockStore blockStore;
    private SignatureCache signatureCache;
    private BlocksBloomStore blocksBloomStore;

    //20965255 getValue()
    //371303c0 inc()

    @BeforeEach
    void setUp() {
        setUpBlockChainForTest(new RskTestFactory(tempDir));
    }

    private void setUpBlockChainForTest(RskTestFactory rskTestFactory) {
        blockChain = rskTestFactory.getBlockchain();
        blockStore = rskTestFactory.getBlockStore();
        trieStore = rskTestFactory.getTrieStore();
        repositoryLocator = rskTestFactory.getRepositoryLocator();
        transactionPool = rskTestFactory.getTransactionPool();
        eth = rskTestFactory.getRsk();
        receiptStore = rskTestFactory.getReceiptStore();
        web3 = createWeb3();
        signatureCache = new ReceivedTxSignatureCache();
    }

    @Test
    void logIndexAreRelativeToBlockInsteadOfTx() throws Exception {
        /*
         3 transactions are generated
         0   -> contract creation
         1-2 -> contract calls generating 2 logs each
         */
        List<Transaction> generatedTxs = generateSimpleLogsTx();
        assertEquals(3, generatedTxs.size(), "There should be 3 transactions");
        Object[] logs = getLogsFromEarliestFilter();

        assertNotNull(logs);
        assertTrue(logs.length > 0, "Logs should not be empty");

        //Two logs should be emitted per TX
        Transaction tx1 = generatedTxs.get(1);
        TxHashParam tx1HashParam = new TxHashParam(tx1.getHash().toHexString());
        TransactionReceiptDTO receiptDTO = web3.eth_getTransactionReceipt(tx1HashParam);
        assertEquals("0x0", receiptDTO.getLogs()[0].logIndex);

        Transaction tx2 = generatedTxs.get(2);
        TxHashParam tx2HashParam = new TxHashParam(tx2.getHash().toHexString());
        TransactionReceiptDTO receiptDTO3 = web3.eth_getTransactionReceipt(tx2HashParam);
        assertEquals(2, receiptDTO3.getLogs().length);
        assertEquals("0x2", receiptDTO3.getLogs()[0].logIndex);
    }

    @Test
    void newFilterInEmptyBlockchain() throws Exception {
        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), null, null, null, null);
        String id = web3.eth_newFilter(fr);

        assertNotNull(id);
    }

    @Test
    void newFilterGetLogsInEmptyBlockchain() throws Exception {
        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), null, null, null, null);
        String id = web3.eth_newFilter(fr);
        Object[] logs = web3.eth_getFilterLogs(new HexIndexParam(id));

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(0, logs.length);
    }


    @Test
    void newFilterGetLogsAfterBlock() throws Exception {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();
        web3.personal_newAccountWithSeed("notDefault");

        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("latest"), null, null, null, null);
        String id = web3.eth_newFilter(fr);

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getContractTransaction(acc1);
        buildAndConnectBlock(genesis,tx);

        Object[] logs = web3.eth_getFilterLogs(new HexIndexParam(id));

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(1, logs.length);

        assertEquals("0x" + tx.getContractAddress().toString(), ((LogFilterElement) logs[0]).address);
    }

    @Test
    void newFilterWithAccountAndTopicsCreatedAfterBlockAndGetLogs() throws Exception {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();

        web3.personal_newAccountWithSeed("notDefault");

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getContractTransaction(acc1);
        buildAndConnectBlock(genesis,tx);

        HexAddressParam[] hexAddressParam = new HexAddressParam[]{new HexAddressParam((tx.getContractAddress().toJsonString()))};
        TopicParam[][] topics = initTopicsArray("06acbfb32bcf8383f3b0a768b70ac9ec234ea0f2d3b9c77fa6a2de69b919aad1");
        FilterRequestParam fr = new FilterRequestParam(null, null, hexAddressParam, topics, null);

        String id = web3.eth_newFilter(fr);

        Object[] logs = web3.eth_getFilterLogs(new HexIndexParam(id));

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(1, logs.length);

        assertEquals("0x" + tx.getContractAddress().toString(), ((LogFilterElement) logs[0]).address);
    }

    @Test
    void newFilterGetLogsTwiceAfterBlock() throws Exception {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();

        web3.personal_newAccountWithSeed("notDefault");

        String id = registerEarliestFilter();

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getContractTransaction(acc1);
        buildAndConnectBlock(genesis,tx);

        HexIndexParam idParam = new HexIndexParam(id);
        web3.eth_getFilterLogs(idParam);
        Object[] logs = web3.eth_getFilterLogs(idParam);

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(1, logs.length);

        assertEquals("0x" + tx.getContractAddress().toString(), ((LogFilterElement) logs[0]).address);
    }

    @Test
    void newFilterGetChangesInEmptyBlockchain() throws Exception {
        String id = registerEarliestFilter();

        HexIndexParam hexIndexParam = new HexIndexParam(id);
        Object[] logs = web3.eth_getFilterChanges(hexIndexParam);

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void newFilterGetChangesAfterBlock() throws Exception {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();

        web3.personal_newAccountWithSeed("notDefault");

        String id = registerEarliestFilter();

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getContractTransaction(acc1);
        buildAndConnectBlock(genesis,tx);

        Object[] logs = web3.eth_getFilterChanges(new HexIndexParam(id));

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(1, logs.length);

        assertEquals("0x" + tx.getContractAddress().toString(), ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsFromEmptyBlockchain() throws Exception {
        Object[] logs = getLogsFromEarliestFilter();

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsFromBlockchainWithThreeEmptyBlocks() throws Exception {
        addTwoEmptyBlocks();

        Object[] logs = getLogsFromEarliestFilter();

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsTwiceFromBlockchainWithThreeEmptyBlocks() throws Exception {
        addTwoEmptyBlocks();

        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), null, null, null, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsFromBlockchainWithContractCreation() throws Exception {
        addContractCreationWithoutEvents();

        Object[] logs = getLogsFromEarliestFilter();

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsTwiceFromBlockchainWithContractCreation() throws Exception {
        addContractCreationWithoutEvents();

        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), null, null, null, null);

        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsFromBlockchainWithEventInContractCreation() throws Exception {
        addEventInContractCreation();

        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), new BlockIdentifierParam("latest"), null, null, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(1, logs.length);

        String txhash = ((LogFilterElement) logs[0]).transactionHash;
        TransactionReceiptDTO txdto = web3.eth_getTransactionReceipt(new TxHashParam(txhash));

        assertEquals(txdto.getContractAddress(), ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsTwiceFromBlockchainWithEventInContractCreation() throws Exception {
        addEventInContractCreation();

        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), null, null, null, null);

        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(1, logs.length);

        String txhash = ((LogFilterElement) logs[0]).transactionHash;
        TransactionReceiptDTO txdto = web3.eth_getTransactionReceipt(new TxHashParam(txhash));

        assertEquals(txdto.getContractAddress(), ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsFromBlockchainWithInvokeContract() throws Exception {
        addContractInvoke();


        Object[] logs = getLogsFromEarliestFilter();

        assertNotNull(logs);
        assertEquals(2, logs.length);

        String txhash = ((LogFilterElement) logs[0]).transactionHash;
        TransactionReceiptDTO txdto = web3.eth_getTransactionReceipt(new TxHashParam(txhash));

        assertEquals(txdto.getContractAddress(), ((LogFilterElement) logs[0]).address);
        assertEquals(txdto.getContractAddress(), ((LogFilterElement) logs[1]).address);
    }

    @Test
    void getLogsKeepsCorrectOrderForReverseSearch() throws Exception {
        List<Transaction> transactions = addContractInvokeManyTxPerBlock();

        Object[] logs = getLogsFromEarliestFilter();

        assertNotNull(logs);
        assertEquals(3, logs.length);

        String txHash1 = "0x" + transactions.get(0).getHash().toHexString();
        TransactionReceiptDTO txReceipt1 = web3.eth_getTransactionReceipt(new TxHashParam(txHash1));
        String contractAddress = txReceipt1.getContractAddress();
        LogFilterElement logs1 = (LogFilterElement) logs[0];
        assertEquals(contractAddress, logs1.address);
        assertEquals(txHash1, logs1.transactionHash);
        LogFilterElement receipt1Logs = txReceipt1.getLogs()[0];
        assertEquals(receipt1Logs.transactionIndex, logs1.transactionIndex);
        assertEquals(receipt1Logs.logIndex, logs1.logIndex);
        assertArrayEquals(receipt1Logs.topics, logs1.topics);

        String txHash2 = "0x" + transactions.get(1).getHash().toHexString();
        TransactionReceiptDTO txReceipt2 = web3.eth_getTransactionReceipt(new TxHashParam(txHash2));
        LogFilterElement logs2 = (LogFilterElement) logs[1];
        assertEquals(contractAddress, logs2.address);
        assertEquals(txHash2, logs2.transactionHash);
        LogFilterElement receipt2Logs = txReceipt2.getLogs()[0];
        assertEquals(receipt2Logs.transactionIndex, logs2.transactionIndex);
        assertEquals(receipt2Logs.logIndex, logs2.logIndex);
        assertArrayEquals(receipt2Logs.topics, logs2.topics);

        String txHash3 = "0x" + transactions.get(2).getHash().toHexString();
        TransactionReceiptDTO txReceipt3 = web3.eth_getTransactionReceipt(new TxHashParam(txHash3));
        LogFilterElement logs3 = (LogFilterElement) logs[2];
        assertEquals(contractAddress, logs3.address);
        assertEquals(txHash3, logs3.transactionHash);
        LogFilterElement receipt3Logs = txReceipt3.getLogs()[0];
        assertEquals(receipt3Logs.transactionIndex, logs3.transactionIndex);
        assertEquals(receipt3Logs.logIndex, logs3.logIndex);
        assertArrayEquals(receipt3Logs.topics, logs3.topics);

        // block1 bloomed after call
        assertTrue(blocksBloomStore.hasBlockNumber(0));
        assertTrue(blocksBloomStore.hasBlockNumber(1));

        // block2 not bloomed after call because the bloom was not complete (1 out of 2 blocks)
        assertFalse(blocksBloomStore.hasBlockNumber(2));
    }

    @Test
    void getLogsTwiceFromBlockchainWithInvokeContract() throws Exception {
        addContractInvoke();

        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), null, null, null, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(2, logs.length);

        String txhash = ((LogFilterElement) logs[0]).transactionHash;
        TransactionReceiptDTO txdto = web3.eth_getTransactionReceipt(new TxHashParam(txhash));

        assertEquals(txdto.getContractAddress(), ((LogFilterElement) logs[0]).address);
        assertEquals(txdto.getContractAddress(), ((LogFilterElement) logs[1]).address);
    }

    @Test
    void getLogsFromBlockchainWithCallContract() throws Exception {
        addContractCall();

        Object[] logs = getLogsFromEarliestFilter();

        assertNotNull(logs);
        assertEquals(3, logs.length);
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContract() throws Exception {
        addContractCall();

        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), null, null, null, null);

        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(3, logs.length);
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterByContractAddress() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        HexAddressParam[] addressParam = new HexAddressParam[]{new HexAddressParam(block1.getTransactionsList().get(0).getContractAddress().toJsonString())};
        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), null, addressParam, null, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(3, logs.length);

        String address = fr.getAddress()[0].getAddress().toJsonString();

        assertEquals(address, ((LogFilterElement) logs[0]).address);
        assertEquals(address, ((LogFilterElement) logs[1]).address);
        assertEquals(address, ((LogFilterElement) logs[2]).address);
    }

    @Test
    void getLogsTwoceFromBlockchainWithCallContractAndFilterByContractAddress() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        HexAddressParam[] hexAddressParam = new HexAddressParam[]{
                new HexAddressParam(block1.getTransactionsList().get(0).getContractAddress().toJsonString())
        };
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, hexAddressParam, null, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(3, logs.length);

        String address = fr.getAddress()[0].getAddress().toJsonString();

        assertEquals(address, ((LogFilterElement) logs[0]).address);
        assertEquals(address, ((LogFilterElement) logs[1]).address);
        assertEquals(address, ((LogFilterElement) logs[2]).address);
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterByUnknownContractAddress() throws Exception {
        addContractCall();

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        HexAddressParam[] hexAddressParam = new HexAddressParam[]{
                new HexAddressParam(ByteUtil.toHexString(new byte[20]))
        };
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, hexAddressParam, null, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContractAndFilterByUnknownContractAddress() throws Exception {
        addContractCall();

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        HexAddressParam[] hexAddressParam = new HexAddressParam[]{
                new HexAddressParam(ByteUtil.toHexString(new byte[20]))
        };
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, hexAddressParam, null, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterByUnknownTopic() throws Exception {
        addContractCall();

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = initTopicsArray("0102030405060102030405060102030405060102030405060102030405060102");
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContractAndFilterByUnknownTopic() throws Exception {
        addContractCall();

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = initTopicsArray("0102030405060102030405060102030405060102030405060102030405060102");
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterByKnownTopic() throws Exception {
        addContractCall();

        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = initTopicsArray(GET_VALUED_EVENT_SIGNATURE);
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);
        assertEquals(address, ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContractAndFilterByKnownTopic() throws Exception {
        addContractCall();

        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = initTopicsArray(GET_VALUED_EVENT_SIGNATURE);
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);
        assertEquals(address, ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterByKnownTopicInList() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = initTopicsArray(GET_VALUED_EVENT_SIGNATURE);
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);
        assertEquals(address, ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContractAndFilterByKnownTopicInList() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = initTopicsArray(GET_VALUED_EVENT_SIGNATURE);
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);
        assertEquals(address, ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterByKnownsTopicInList() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{new TopicParam(GET_VALUED_EVENT_SIGNATURE), new TopicParam(INC_EVENT_SIGNATURE)}};
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(2, logs.length);

        for (int k = 0; k < logs.length; k++) {
            assertEquals(address, ((LogFilterElement) logs[k]).address);
        }
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContractAndFilterByKnownsTopicInList() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{new TopicParam(GET_VALUED_EVENT_SIGNATURE), new TopicParam(INC_EVENT_SIGNATURE)}};
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(2, logs.length);

        for (int k = 0; k < logs.length; k++) {
            assertEquals(address, ((LogFilterElement) logs[k]).address);
        }
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterByKnownTopicInListWithNull() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{new TopicParam(GET_VALUED_EVENT_SIGNATURE)}, {null}};
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);
        assertEquals(address, ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContractAndFilterByKnownTopicInListWithNull() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{new TopicParam(GET_VALUED_EVENT_SIGNATURE)}, {null}};

        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);
        assertEquals(address, ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterWithNullTopic() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{null}};
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(3, logs.length);

        for (int k = 0; k < logs.length; k++) {
            assertEquals(address, ((LogFilterElement) logs[k]).address);
        }
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContractAndFilterWithNullTopic() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{null}};
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(3, logs.length);

        for (int k = 0; k < logs.length; k++) {
            assertEquals(address, ((LogFilterElement) logs[k]).address);
        }
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterWithTwoTopics() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{new TopicParam(INC_EVENT_SIGNATURE)}, {new TopicParam(ONE_TOPIC)}};
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);

        assertEquals(address, ((LogFilterElement) logs[0]).address);
        assertEquals("0x" + INC_EVENT_SIGNATURE, ((LogFilterElement) logs[0]).topics[0]);
        assertEquals("0x" + ONE_TOPIC, ((LogFilterElement) logs[0]).topics[1]);
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContractAndFilterWithTwoTopics() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{new TopicParam(INC_EVENT_SIGNATURE)}, {new TopicParam(ONE_TOPIC)}};
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);

        assertEquals(address, ((LogFilterElement) logs[0]).address);
        assertEquals("0x" + INC_EVENT_SIGNATURE, ((LogFilterElement) logs[0]).topics[0]);
        assertEquals("0x" + ONE_TOPIC, ((LogFilterElement) logs[0]).topics[1]);
    }

    @Test
    void getLogsFromBlockchainWithCallContractAndFilterBySecondTopic() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{null}, {new TopicParam(ONE_TOPIC)}};
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);

        assertEquals(address, ((LogFilterElement) logs[0]).address);
        assertEquals("0x" + INC_EVENT_SIGNATURE, ((LogFilterElement) logs[0]).topics[0]);
        assertEquals("0x" + ONE_TOPIC, ((LogFilterElement) logs[0]).topics[1]);
    }

    @Test
    void getLogsTwiceFromBlockchainWithCallContractAndFilterBySecondTopic() throws Exception {
        addContractCall();
        Block block1 = blockChain.getBlockByNumber(1l);
        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam("earliest");
        TopicParam[][] topics = new TopicParam[][]{{null}, {new TopicParam(ONE_TOPIC)}};
        FilterRequestParam fr = new FilterRequestParam(blockIdentifierParam, null, null, topics, null);
        web3.eth_getLogs(fr);
        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        String address = "0x" + ByteUtil.toHexString(block1.getTransactionsList().get(0).getContractAddress().getBytes());
        assertEquals(1, logs.length);

        assertEquals(address, ((LogFilterElement) logs[0]).address);
        assertEquals("0x" + INC_EVENT_SIGNATURE, ((LogFilterElement) logs[0]).topics[0]);
        assertEquals("0x" + ONE_TOPIC, ((LogFilterElement) logs[0]).topics[1]);
    }

    @Test
    void getLogsFromBlockchainWithEventInContractCreationReturnsAsExpectedWithBlockHashFilter() throws Exception {
        addEventInContractCreation();
        final String blockHash = "0xed4afd31173a73c4c5135aae72b940507b97605a5129790de00510894f58f5ce";
        FilterRequestParam fr = new FilterRequestParam(null, null, null, null, new BlockHashParam(blockHash));

        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(1, logs.length);
        assertEquals(blockHash, ((LogFilterElement) logs[0]).blockHash);
        String txhash = ((LogFilterElement) logs[0]).transactionHash;
        TransactionReceiptDTO txdto = web3.eth_getTransactionReceipt(new TxHashParam(txhash));
        assertEquals(txdto.getContractAddress(), ((LogFilterElement) logs[0]).address);
    }

    @Test
    void getLogsWithBlockHashFilterForNonexistentBlockThrowsException() throws Exception {
        final String blockHash = UNTRACKED_TEST_BLOCK_HASH;
        byte[] blockHashBytes = new Keccak256(HexUtils.stringHexToByteArray(blockHash)).getBytes();
        assertFalse(blockChain.hasBlockInSomeBlockchain(blockHashBytes));
        FilterRequestParam fr = new FilterRequestParam(null, null, null, null, new BlockHashParam(blockHash));


        Object[] logs = web3.eth_getLogs(fr);

        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void getLogsThrowsExceptionWhenBlockHashIsUsedCombinedWithFromBlock() {
        addEventInContractCreation();
        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("earliest"), null, null, null, new BlockHashParam(TRACKED_TEST_BLOCK_HASH));
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> web3.eth_getLogs(fr));
    }

    @Test
    void getLogsThrowsExceptionWhenBlockHashIsUsedCombinedWithToBlock() {
        addEventInContractCreation();
        FilterRequestParam fr = new FilterRequestParam(new BlockIdentifierParam("latest"), null, null, null, new BlockHashParam(TRACKED_TEST_BLOCK_HASH));

        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> web3.eth_getLogs(fr));
    }

    @Test
    void createMainContractWithoutEvents() throws Exception {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();
        web3.personal_newAccountWithSeed("notDefault");

        String id = registerEarliestFilter();

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getMainContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        Object[] logs = web3.eth_getFilterChanges(new HexIndexParam(id));

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(0, logs.length);
    }

    @Test
    void createCallerContractWithEvents() throws Exception {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();
        web3.personal_newAccountWithSeed("notDefault");

        String id = registerEarliestFilter();

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getMainContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        String mainAddress = tx.getContractAddress().toString();

        Transaction tx2;
        tx2 = getCallerContractTransaction(acc1, mainAddress);
        String callerAddress = ByteUtil.toHexString(tx2.getContractAddress().getBytes());

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(tx2);
        Block block2 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(block1).transactions(txs2).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));

        Object[] logs = web3.eth_getFilterChanges(new HexIndexParam(id));

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(2, logs.length);

        assertEquals("0x" + mainAddress, ((LogFilterElement) logs[0]).address);
        assertEquals("0x" + callerAddress, ((LogFilterElement) logs[1]).address);
    }

    @Test
    void createCallerContractWithEventsOnInvoke() throws Exception {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();
        web3.personal_newAccountWithSeed("notDefault");

        String id = registerEarliestFilter();

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getMainContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        String mainAddress = tx.getContractAddress().toString();

        Transaction tx2;
        tx2 = getCallerContractTransaction(acc1, mainAddress);
        String callerAddress = ByteUtil.toHexString(tx2.getContractAddress().getBytes());

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(tx2);
        Block block2 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(block1).transactions(txs2).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));

        Transaction tx3;
        tx3 = getCallerContractTransactionWithInvoke(acc1, tx2.getContractAddress().getBytes(), mainAddress);

        List<Transaction> txs3 = new ArrayList<>();
        txs3.add(tx3);
        Block block3 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(block2).transactions(txs3).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block3));

        Object[] logs = web3.eth_getFilterChanges(new HexIndexParam(id));

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(3, logs.length);

        assertEquals("0x" + mainAddress, ((LogFilterElement) logs[0]).address);
        assertEquals("0x" + callerAddress, ((LogFilterElement) logs[1]).address);
        assertEquals("0x" + mainAddress, ((LogFilterElement) logs[2]).address);
    }

    @Test
    void createCallerContractWithEventsOnInvokeUsingGetFilterLogs() throws Exception {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();
        web3.personal_newAccountWithSeed("notDefault");

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getMainContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        String mainAddress = tx.getContractAddress().toString();

        Transaction tx2;
        tx2 = getCallerContractTransaction(acc1, mainAddress);

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(tx2);
        Block block2 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(block1).transactions(txs2).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));

        Transaction tx3;
        tx3 = getCallerContractTransactionWithInvoke(acc1, tx2.getContractAddress().getBytes(), mainAddress);

        List<Transaction> txs3 = new ArrayList<>();
        txs3.add(tx3);
        Block block3 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(block2).transactions(txs3).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block3));

        HexAddressParam[] address = new HexAddressParam[]{new HexAddressParam("0x" + mainAddress)};
        FilterRequestParam fr = new FilterRequestParam(null, null, address, null, null);
        String id = web3.eth_newFilter(fr);

        Object[] logs = web3.eth_getFilterLogs(new HexIndexParam(id));

        assertNotNull(id);
        assertNotNull(logs);
        assertEquals(1, logs.length);

        assertEquals("0x" + mainAddress, ((LogFilterElement) logs[0]).address);
    }

    private Web3Impl createWeb3() {
        Wallet wallet = WalletFactory.createWallet();
        PersonalModule personalModule = new PersonalModuleWalletEnabled(config, eth, wallet, transactionPool);
        EthModule ethModule = new EthModule(
                config.getNetworkConstants().getBridgeConstants(), config.getNetworkConstants().getChainId(), blockChain, transactionPool,
                null, new ExecutionBlockRetriever(blockChain, null, null),
                null, new EthModuleWalletEnabled(wallet, transactionPool, signatureCache), null,
                new BridgeSupportFactory(
                        null, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), new BlockTxSignatureCache(new ReceivedTxSignatureCache())),
                config.getGasEstimationCap(),
                config.getCallGasCap(),
                config.getActivationConfig(),
                null,
                false,
                null
        );
        TxPoolModule txPoolModule = new TxPoolModuleImpl(transactionPool, signatureCache);
        DebugTracer debugTracer = new RskTracer(null, null, null, null);
        TraceProvider traceProvider = new TraceProvider(List.of(debugTracer));
        DebugModule debugModule = new DebugModuleImpl(traceProvider, Web3Mocks.getMockMessageHandler(), null);
        blocksBloomStore = new BlocksBloomStore(2, 0, new HashMapDB());
        return new Web3RskImpl(
                eth,
                blockChain,
                config,
                Web3Mocks.getMockMinerClient(),
                Web3Mocks.getMockMinerServer(),
                personalModule,
                ethModule,
                null,
                txPoolModule,
                null,
                debugModule,
                null, null,
                Web3Mocks.getMockChannelManager(),
                null,
                null,
                blockStore,
                receiptStore,
                null,
                null,
                null,
                new SimpleConfigCapabilities(),
                null,
                blocksBloomStore,
                mock(Web3InformationRetriever.class),
                null,
                null);
    }

    private void addTwoEmptyBlocks() {
        Block genesis = blockChain.getBlockByNumber(0);
        Block block1 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(genesis).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
        Block block2 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(block1).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));
        assertEquals(3, blockChain.getSize());
    }

    private String compiledGreeter = "60606040525b33600060006101000a81548173ffffffffffffffffffffffffffffffffffffffff02191690836c010000000000000000000000009081020402179055505b610181806100516000396000f360606040526000357c010000000000000000000000000000000000000000000000000000000090048063ead710c41461003c57610037565b610002565b34610002576100956004808035906020019082018035906020019191908080601f016020809104026020016040519081016040528093929190818152602001838380828437820191505050505050909091905050610103565b60405180806020018281038252838181518152602001915080519060200190808383829060006004602084601f0104600302600f01f150905090810190601f1680156100f55780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b6020604051908101604052806000815260200150600060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff1614151561017357610002565b81905061017b565b5b91905056";

    private void addContractCreationWithoutEvents() {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();

        Block genesis = blockChain.getBlockByNumber(0);

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
        Block block1 = new BlockBuilder(blockChain, null,
                blockStore
        ).trieStore(trieStore).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        web3.personal_newAccountWithSeed("notDefault");
    }

    private void addEventInContractCreation() {
        addEmptyBlockToBlockchain(blockChain, blockStore, repositoryLocator, trieStore);

        web3.personal_newAccountWithSeed("notDefault");
    }

    public static void addEmptyBlockToBlockchain(
            Blockchain blockChain,
            BlockStore blockStore,
            RepositoryLocator repositoryLocator,
            TrieStore trieStore) {
        Account acc1 = new AccountBuilder(blockChain, blockStore, repositoryLocator)
                .name("notDefault").balance(Coin.valueOf(10000000)).build();

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(blockChain, null, blockStore, new BlockGenerator(Constants.regtest(), ActivationConfigsForTest.allBut(ConsensusRule.RSKIP351)))
                .trieStore(trieStore).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));
    }

    private void addContractInvoke() {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(trieStore).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        byte[] contractAddress = tx.getContractAddress().getBytes();

        Transaction tx2 = getContractTransactionWithInvoke(acc1, contractAddress);
        List<Transaction> tx2s = new ArrayList<>();
        tx2s.add(tx2);
        Block block2 = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(trieStore).parent(block1).transactions(tx2s).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));

        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");
    }

    private List<Transaction> addContractInvokeManyTxPerBlock() {
        Account acc1 = new AccountBuilder(blockChain,
                blockStore,
                repositoryLocator).name("notDefault").balance(Coin.valueOf(10000000)).build();

        List<Transaction> allTxs = new ArrayList<>();

        Block genesis = blockChain.getBlockByNumber(0);

        // Block 1
        List<Transaction> txs = new ArrayList<>();

        Transaction tx = getContractTransaction(acc1);
        txs.add(tx);
        allTxs.add(tx);

        Block block1 = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(trieStore).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        byte[] contractAddress = tx.getContractAddress().getBytes();

        // Block 2
        txs = new ArrayList<>();

        Transaction tx2 = getContractTransactionWithInvoke(acc1, contractAddress);
        txs.add(tx2);
        allTxs.add(tx2);

        Transaction tx3 = getContractTransactionWithCall(acc1, contractAddress);
        txs.add(tx3);
        allTxs.add(tx3);

        Block block2 = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(trieStore).parent(block1).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));

        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");

        return allTxs;
    }

    private void addContractCall() {
        Account acc1 = new AccountBuilder(blockChain, blockStore, repositoryLocator)
                .name("notDefault").balance(Coin.valueOf(10000000)).build();
        // acc1 Account created address should be 661b05ca9eb621164906671efd2731ce0d7dd8b4

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getContractTransaction(acc1);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        Block block1 = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(trieStore).parent(genesis).transactions(txs).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block1));

        byte[] contractAddress = tx.getContractAddress().getBytes();

        // Now create a transaction that invokes Increment()
        Transaction tx2 = getContractTransactionWithInvoke(acc1, contractAddress);
        List<Transaction> tx2s = new ArrayList<>();
        tx2s.add(tx2);
        Block block2 = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(trieStore).parent(block1).transactions(tx2s).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block2));

        Transaction tx3 = getContractTransactionWithCall(acc1, contractAddress);
        List<Transaction> tx3s = new ArrayList<>();
        tx3s.add(tx3);
        Block block3 = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(trieStore).parent(block2).transactions(tx3s).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block3));

        web3.personal_newAccountWithSeed("default");
        web3.personal_newAccountWithSeed("notDefault");
    }

    //0.4.11+commit.68ef5810.Emscripten.clang WITH optimizations
    static final String COMPILED_0_4_11 = "6060604052341561000c57fe5b5b60466000819055507f06acbfb32bcf8383f3b0a768b70ac9ec234ea0f2d3b9c77fa6a2de69b919aad16000546040518082815260200191505060405180910390a15b5b61014e8061005f6000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680632096525514610046578063371303c01461006c575bfe5b341561004e57fe5b61005661007e565b6040518082815260200191505060405180910390f35b341561007457fe5b61007c6100c2565b005b60007f1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b6000546040518082815260200191505060405180910390a160005490505b90565b60006000815460010191905081905550600160026000548115156100e257fe5b061415157f6e61ef44ac2747ff8b84d353a908eb8bd5c3fb118334d57698c5cfc7041196ad6000546040518082815260200191505060405180910390a25b5600a165627a7a7230582092c7b2c0483b85227396e18149993b33243059af0f3bd0364f1dc36b8bbbcdae0029";
    static final String COMPILED_UNKNOWN = "60606040526046600081905560609081527f06acbfb32bcf8383f3b0a768b70ac9ec234ea0f2d3b9c77fa6a2de69b919aad190602090a160aa8060426000396000f3606060405260e060020a60003504632096525581146024578063371303c0146060575b005b60a36000805460609081527f1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b90602090a1600060005054905090565b6022600080546001908101918290556060828152600290920614907f6e61ef44ac2747ff8b84d353a908eb8bd5c3fb118334d57698c5cfc7041196ad90602090a2565b5060206060f3";

    private static Transaction getContractTransaction(Account acc1) {
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
                .gasLimit(BigInteger.valueOf(500000))
                .gasPrice(BigInteger.ONE)
                .data(COMPILED_0_4_11)
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
                .gasLimit(BigInteger.valueOf(500000))
                .gasPrice(BigInteger.ONE)
                .data(COMPILED_LOG_EXAMPLE)
                .build();
    }


    private List<Transaction> generateSimpleLogsTx() {
        Account acc1 = new AccountBuilder(blockChain, blockStore, repositoryLocator)
                .name("notDefault").balance(Coin.valueOf(10000000)).build();
        // acc1 Account created address should be 661b05ca9eb621164906671efd2731ce0d7dd8b4

        Block genesis = blockChain.getBlockByNumber(0);
        Transaction tx;
        tx = getSimpleLogEmitContractTransaction(acc1);

        Block block1 = buildAndConnectBlock(genesis, List.of(tx));

        byte[] contractAddress = tx.getContractAddress().getBytes();

        // Now create a transaction that invokes Increment()
        Transaction tx2 = simpleLogEmitContractCall(acc1, contractAddress, 1);
        Transaction tx3 = simpleLogEmitContractCall(acc1, contractAddress, 2);

        buildAndConnectBlock(block1, List.of(tx2, tx3));
        return List.of(tx, tx2, tx3);
    }

    private Block buildAndConnectBlock(Block parentBlock, Transaction tx) {
        return buildAndConnectBlock(parentBlock, List.of(tx));
    }

    private Block buildAndConnectBlock(Block parentBlock, List<Transaction> txList) {
        Block block = new BlockBuilder(blockChain, null, blockStore)
                .trieStore(trieStore).parent(parentBlock).transactions(txList).build();
        block.seal();
        assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(block));
        return block;
    }

    private static Transaction simpleLogEmitContractCall(Account acc1, byte[] receiverAddress, long nonce) {
        return new TransactionBuilder()
                .sender(acc1)
                .receiverAddress(receiverAddress)
                .gasLimit(BigInteger.valueOf(100000))
                .gasPrice(BigInteger.ONE)
                .data(SIMPLE_LOG_METHOD_SIGNATURE)   // invoke emitTwoLogs()
                .nonce(nonce)
                .build();
    }

    private static Transaction getSimpleLogEmitContractTransaction(Account acc1) {
        /*
         // SPDX-License-Identifier: MIT
            pragma solidity ^0.8.20;

            contract SimpleLogEmitter {
                event LogOne(uint256 value);
                event LogTwo(address sender);

                function emitTwoLogs() external {
                    emit LogOne(block.timestamp);
                    emit LogTwo(msg.sender);
                }
            }
         */
        return new TransactionBuilder()
                .sender(acc1)
                .gasLimit(BigInteger.valueOf(250304))
                .gasPrice(BigInteger.ONE)
                .data(COMPILED_SIMPLE_LOG_EMIT_BYTECODE)
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
                .gasLimit(BigInteger.valueOf(500000))
                .gasPrice(BigInteger.ONE)
                .data(compiledCaller + address)
                .nonce(1)
                .build();
    }

    private static Transaction getCallerContractTransactionWithInvoke(Account acc1, byte[] receiverAddress, String mainAddress) {
        String address = mainAddress;

        while (address.length() < 64)
            address = "0" + address;

        CallTransaction.Function func = CallTransaction.Function.fromSignature("doSomething", new String[]{"address"}, new String[0]);

        return new TransactionBuilder()
                .sender(acc1)
                .receiverAddress(receiverAddress)
                .gasLimit(BigInteger.valueOf(500000))
                .gasPrice(BigInteger.ONE)
                .data(ByteUtil.toHexString(func.encode("0x" + address)))
                .nonce(2)
                .build();
    }

    private TopicParam[][] initTopicsArray(String topic) {
        return new TopicParam[][]{{new TopicParam(topic)}};
    }

    private Object[] getLogsFromEarliestFilter() throws Exception {
        return web3.eth_getLogs(createEarliestFilter());
    }

    private FilterRequestParam createEarliestFilter(){
        return new FilterRequestParam(new BlockIdentifierParam("earliest"), null, null, null, null);
    }

    private String registerEarliestFilter() throws Exception {
        return web3.eth_newFilter(createEarliestFilter());
    }
}
