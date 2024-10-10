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

package org.ethereum.rpc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositoryLocator;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.HexUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by ajlopez on 17/01/2018.
 */
class
LogFilterTest {

    @TempDir
    public Path tempDir;

    @Test
    void noEvents() {
        LogFilter filter = new LogFilter.LogFilterBuilder().build();
        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.length);
    }

    @Test
    void noEventsAfterEmptyBlock() {
        LogFilter filter =new LogFilter.LogFilterBuilder().build();

        Block block = new BlockGenerator().getBlock(1);

        filter.newBlockReceived(block);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.length);
    }

    @Test
    void eventAfterBlockWithEvent() {
        RskTestFactory factory = new RskTestFactory(tempDir);
        Blockchain blockchain = factory.getBlockchain();
        BlockStore blockStore = factory.getBlockStore();
        RepositoryLocator repositoryLocator = factory.getRepositoryLocator();
        Web3ImplLogsTest.addEmptyBlockToBlockchain(blockchain, blockStore, repositoryLocator, factory.getTrieStore());
        Block block = blockchain.getBestBlock();

        AddressesTopicsFilter atfilter = new AddressesTopicsFilter(new RskAddress[0], null);

        LogFilter filter = new LogFilter.LogFilterBuilder()
                .addressesTopicsFilter(atfilter)
                .blockchain(blockchain)
                .fromLatestBlock(false)
                .toLatestBlock(true)
                .build();

        filter.newBlockReceived(block);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
    }

    @Test
    void twoEventsAfterTwoBlocksWithEventAndToLatestBlock() {
        RskTestFactory factory = new RskTestFactory(tempDir);
        Blockchain blockchain = factory.getBlockchain();
        BlockStore blockStore = factory.getBlockStore();
        RepositoryLocator repositoryLocator = factory.getRepositoryLocator();
        Web3ImplLogsTest.addEmptyBlockToBlockchain(blockchain, blockStore, repositoryLocator, factory.getTrieStore());
        Block block = blockchain.getBestBlock();

        AddressesTopicsFilter atfilter = new AddressesTopicsFilter(new RskAddress[0], null);

        LogFilter filter = new LogFilter.LogFilterBuilder()
                .addressesTopicsFilter(atfilter)
                .blockchain(blockchain)
                .fromLatestBlock(false)
                .toLatestBlock(true)
                .build();

        filter.newBlockReceived(block);
        filter.newBlockReceived(block);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.length);
    }

    @Test
    void onlyOneEventAfterTwoBlocksWithEventAndFromLatestBlock() {
        RskTestFactory factory = new RskTestFactory(tempDir);
        Blockchain blockchain = factory.getBlockchain();
        BlockStore blockStore = factory.getBlockStore();
        RepositoryLocator repositoryLocator = factory.getRepositoryLocator();
        Web3ImplLogsTest.addEmptyBlockToBlockchain(blockchain, blockStore, repositoryLocator, factory.getTrieStore());
        Block block = blockchain.getBestBlock();

        AddressesTopicsFilter atfilter = new AddressesTopicsFilter(new RskAddress[0], null);

        LogFilter filter = new LogFilter.LogFilterBuilder()
                .addressesTopicsFilter(atfilter)
                .blockchain(blockchain)
                .fromLatestBlock(true)
                .toLatestBlock(true)
                .build();
        filter.newBlockReceived(block);
        filter.newBlockReceived(block);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
    }

    @Test
    void testFromFilterRequest() throws Exception {
        RskTestFactory factory = new RskTestFactory(tempDir);
        Blockchain blockchain = factory.getBlockchain();
        BlockStore blockStore = factory.getBlockStore();
        BlocksBloomStore blocksBloomStore = factory.getBlocksBloomStore();
        TestUtils.setInternalState(blocksBloomStore, "noBlocks", 2);
        TestUtils.setInternalState(blocksBloomStore, "noConfirmations", 1);
        RepositoryLocator repositoryLocator = factory.getRepositoryLocator();

        BlockBuilder blockBuilder = new BlockBuilder(blockchain, null, blockStore)
                .trieStore(factory.getTrieStore());

        Account acc1 = new AccountBuilder(blockchain,blockStore,repositoryLocator)
                .name("acc1").balance(Coin.valueOf(10000000)).build();

        Block genesis = blockchain.getBlockByNumber(0);
        Block block1 = addBlockToBlockchain(genesis, acc1, blockBuilder, blockchain);
        Block block2 = addBlockToBlockchain(block1, acc1, blockBuilder, blockchain);
        Block block3 = addBlockToBlockchain(block2, acc1, blockBuilder, blockchain);

        FilterRequest fr = new FilterRequest();
        fr.setFromBlock("earliest");

        // nothing bloomed before request
        assertFalse(blocksBloomStore.hasBlockNumber(genesis.getNumber()));
        assertFalse(blocksBloomStore.hasBlockNumber(block1.getNumber()));
        assertFalse(blocksBloomStore.hasBlockNumber(block2.getNumber()));
        assertFalse(blocksBloomStore.hasBlockNumber(block3.getNumber()));

        LogFilter logFilter = LogFilter.fromFilterRequest(fr, blockchain, blocksBloomStore);

        List<Filter.FilterEvent> result = logFilter.getEventsInternal();
        Assertions.assertEquals(3, result.size());

        // block1 bloomed after call (enough confirmations and complete bloom)
        assertTrue(blocksBloomStore.hasBlockNumber(genesis.getNumber()));
        assertTrue(blocksBloomStore.hasBlockNumber(block1.getNumber()));
        // blocks 2 not bloomed after call because the bloom was not complete (1 out of 2 blocks) since block 3 is not confirmed
        assertFalse(blocksBloomStore.hasBlockNumber(block2.getNumber()));
        // blocks 3 not bloomed after call because it is not confirmed
        assertFalse(blocksBloomStore.hasBlockNumber(block3.getNumber()));

        fr = new FilterRequest();
        fr.setFromBlock(HexUtils.toQuantityJsonHex(block1.getNumber()));
        fr.setToBlock(HexUtils.toQuantityJsonHex(block2.getNumber()));

        logFilter = LogFilter.fromFilterRequest(fr, blockchain, blocksBloomStore);
        result = logFilter.getEventsInternal();
        Assertions.assertEquals(2, result.size());
    }

    @Test
    void testLogFilterExceptionIsThrownWhenLimitIsReached(){
        //TODO RskTestFactory is deprecated but RskTestContext is not working in the same way
        RskTestFactory rskTestContext = new RskTestFactory(tempDir);
        Blockchain blockchain = rskTestContext.getBlockchain();
        BlockStore blockStore = rskTestContext.getBlockStore();
        BlocksBloomStore blocksBloomStore = rskTestContext.getBlocksBloomStore();
        TestUtils.setInternalState(blocksBloomStore, "noBlocks", 2);
        TestUtils.setInternalState(blocksBloomStore, "noConfirmations", 1);
        RepositoryLocator repositoryLocator = rskTestContext.getRepositoryLocator();

        BlockBuilder blockBuilder = new BlockBuilder(blockchain, null, blockStore)
                .trieStore(rskTestContext.getTrieStore());

        Account acc1 = new AccountBuilder(blockchain,blockStore,repositoryLocator)
                .name("acc1").balance(Coin.valueOf(10000000)).build();

        createBlocksTo(6,blockBuilder,blockchain,acc1);

        FilterRequest filterRequest = new FilterRequest();
        //fromBlock nº1
        filterRequest.setFromBlock("0x1");
        //to block nº5
        filterRequest.setToBlock("0x5");
        //Limit is 3 so exception is expected
        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class, () -> {
            LogFilter.fromFilterRequest(filterRequest, blockchain, blocksBloomStore, 3L, 0L);
        });
        assertEquals(-32012,ex.getCode());
    }

    @Test
    void addFilter_mustFail_whenLimitIsReached(){
        int limit = 2;
        LogFilter filter = new LogFilter.LogFilterBuilder()
                .maxBlocksToReturn(limit)
                .build();
        assertTrue(filter.getEventsInternal().isEmpty());
        filter.add(new FilterTest.FilterEventMock());
        filter.add(new FilterTest.FilterEventMock());

        // the limit is reached
        assertEquals(limit,filter.getEvents().length);

        FilterTest.FilterEventMock thirdEvent = new FilterTest.FilterEventMock();
        assertThrows(RskJsonRpcRequestException.class, () -> filter.add(thirdEvent));
    }

    private void createBlocksTo(int blockNumber, BlockBuilder blockBuilder, Blockchain blockchain, Account acc1) {

        Block parent = blockchain.getBlockByNumber(0);
        for(int i = 0; i<blockNumber; i++) {
            parent = addBlockToBlockchain(parent, acc1, blockBuilder, blockchain);
        }
    }

    public static Block addBlockToBlockchain(Block parent, Account account, BlockBuilder blockBuilder, Blockchain blockchain) {
        final String compiled_0_4_11 = "6060604052341561000c57fe5b5b60466000819055507f06acbfb32bcf8383f3b0a768b70ac9ec234ea0f2d3b9c77fa6a2de69b919aad16000546040518082815260200191505060405180910390a15b5b61014e8061005f6000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680632096525514610046578063371303c01461006c575bfe5b341561004e57fe5b61005661007e565b6040518082815260200191505060405180910390f35b341561007457fe5b61007c6100c2565b005b60007f1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b6000546040518082815260200191505060405180910390a160005490505b90565b60006000815460010191905081905550600160026000548115156100e257fe5b061415157f6e61ef44ac2747ff8b84d353a908eb8bd5c3fb118334d57698c5cfc7041196ad6000546040518082815260200191505060405180910390a25b5600a165627a7a7230582092c7b2c0483b85227396e18149993b33243059af0f3bd0364f1dc36b8bbbcdae0029";

        Transaction tx = new TransactionBuilder()
                .nonce(parent.getNumber()) // just to increase nonce
                .sender(account)
                .gasLimit(BigInteger.valueOf(500000))
                .gasPrice(BigInteger.ONE)
                .data(compiled_0_4_11)
                .build();

        Block block = blockBuilder.parent(parent).transactions(Collections.singletonList(tx)).build();
        assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block));

        return block;
    }

}
