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

package co.rsk.rpc;

import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockResult;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.BlockToMineBuilder;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ExecutionFoundBlockRetrieverTest {

    private static final Keccak256 HASH1 = new Keccak256("133e83bb305ef21ea7fc86fcced355db2300887274961a136ca5e8c8763687d9");
    private static final Keccak256 HASH2 = new Keccak256("ee5c851e70650111887bb6c04e18ef4353391abe37846234c17895a9ca2b33d5");
    private static final int INVALID_PARAM_ERROR_CODE = -32602;

    private Blockchain blockchain;
    private BlockToMineBuilder builder;
    private CompositeEthereumListener emitter;
    private ExecutionBlockRetriever retriever;

    @BeforeEach
    void setUp() {
        blockchain = mock(BlockChainImpl.class);
        builder = mock(BlockToMineBuilder.class);
        emitter = mock(CompositeEthereumListener.class);
        retriever = new ExecutionBlockRetriever(blockchain, builder, emitter);
    }

    @Test
    void getLatest() {
        Block latest = mock(Block.class);
        when(blockchain.getBestBlock())
                .thenReturn(latest);

        assertThat(retriever.retrieveExecutionBlock("latest").getBlock(), is(latest));
    }

    @Test
    void getLatestIsUpToDate() {
        Block latest1 = mock(Block.class);
        Block latest2 = mock(Block.class);
        when(blockchain.getBestBlock())
                .thenReturn(latest1)
                .thenReturn(latest2);

        assertThat(retriever.retrieveExecutionBlock("latest").getBlock(), is(latest1));
        assertThat(retriever.retrieveExecutionBlock("latest").getBlock(), is(latest2));
    }

    @Test
    void getPendingBuildsPendingBlockIfNoCachedResult() {
        BlockHeader bestHeader = mock(BlockHeader.class);
        Block bestBlock = mock(Block.class);
        when(bestBlock.getHeader()).thenReturn(bestHeader);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        Block builtBlock = mock(Block.class);
        BlockResult blockResult = mock(BlockResult.class);
        when(builder.buildPending(bestHeader)).thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(builtBlock);

        assertNull(retriever.getCachedPendingBlockResult());
        assertEquals(builtBlock, retriever.retrieveExecutionBlock("pending").getBlock());
        verify(builder, times(1)).buildPending(any());
    }

    @Test
    void getPendingReturnsCachedBlockNextTime() {
        BlockHeader bestHeader = mock(BlockHeader.class);
        Block bestBlock = mock(Block.class);
        when(bestBlock.getHeader()).thenReturn(bestHeader);
        when(bestBlock.getHash()).thenReturn(HASH1);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        Block builtBlock = mock(Block.class);
        when(builtBlock.getParentHash()).thenReturn(HASH1);
        BlockResult blockResult = mock(BlockResult.class);
        when(builder.buildPending(bestHeader)).thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(builtBlock);

        assertNull(retriever.getCachedPendingBlockResult());
        retriever.retrieveExecutionBlock("pending");
        assertEquals(blockResult.getBlock(), retriever.getCachedPendingBlockResult().getBlock());
        assertEquals(blockResult.getFinalState(), retriever.getCachedPendingBlockResult().getFinalState());
        assertEquals(builtBlock, retriever.retrieveExecutionBlock("pending").getBlock());
        verify(builder, times(1)).buildPending(any());
    }

    @Test
    void getPendingBuildsNewPendingBlockIfPendingTxsArrive() {
        BlockHeader bestHeader = mock(BlockHeader.class);
        Block bestBlock = mock(Block.class);
        when(bestBlock.getHeader()).thenReturn(bestHeader);
        when(bestBlock.getHash()).thenReturn(HASH1);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        Block builtBlock = mock(Block.class);
        when(builtBlock.getParentHash()).thenReturn(HASH1);
        BlockResult blockResult = mock(BlockResult.class);
        when(builder.buildPending(bestHeader)).thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(builtBlock);
        ArgumentCaptor<EthereumListener> captor = ArgumentCaptor.forClass(EthereumListener.class);
        EthereumListener listener;

        assertNull(retriever.getCachedPendingBlockResult());
        retriever.retrieveExecutionBlock("pending");
        assertEquals(blockResult.getBlock(), retriever.getCachedPendingBlockResult().getBlock());
        assertEquals(blockResult.getFinalState(), retriever.getCachedPendingBlockResult().getFinalState());
        assertEquals(builtBlock, retriever.retrieveExecutionBlock("pending").getBlock());
        verify(builder, times(1)).buildPending(any());

        retriever.start();
        verify(emitter, times(1)).addListener(captor.capture());
        listener = captor.getValue();
        listener.onPendingTransactionsReceived(Collections.emptyList());
        assertNull(retriever.getCachedPendingBlockResult());

        assertEquals(builtBlock, retriever.retrieveExecutionBlock("pending").getBlock());
        verify(builder, times(2)).buildPending(any());

        retriever.stop();
        verify(emitter, times(1)).removeListener(listener);
    }

    @Test
    void getPendingBuildsNewPendingBlockIfNewBestBlockArrives() {
        BlockHeader bestHeader = mock(BlockHeader.class);
        Block bestBlock = mock(Block.class);
        when(bestBlock.getHeader()).thenReturn(bestHeader);
        when(bestBlock.getHash()).thenReturn(HASH1);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        BlockHeader newBestHeader = mock(BlockHeader.class);
        Block newBestBlock = mock(Block.class);
        when(newBestBlock.getHeader()).thenReturn(newBestHeader);
        when(newBestBlock.getHash()).thenReturn(HASH2);

        Block builtBlock = mock(Block.class);
        when(builtBlock.getParentHash()).thenReturn(HASH1);
        BlockResult blockResult = mock(BlockResult.class);
        when(builder.buildPending(bestHeader)).thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(builtBlock);

        Block anotherBuiltBlock = mock(Block.class);
        when(anotherBuiltBlock.getParentHash()).thenReturn(HASH2);
        BlockResult anotherBlockResult = mock(BlockResult.class);
        when(builder.buildPending(newBestHeader)).thenReturn(anotherBlockResult);
        when(anotherBlockResult.getBlock()).thenReturn(anotherBuiltBlock);

        ArgumentCaptor<EthereumListener> captor = ArgumentCaptor.forClass(EthereumListener.class);
        EthereumListener listener;

        assertNull(retriever.getCachedPendingBlockResult());
        retriever.retrieveExecutionBlock("pending");
        assertEquals(blockResult.getBlock(), retriever.getCachedPendingBlockResult().getBlock());
        assertEquals(blockResult.getFinalState(), retriever.getCachedPendingBlockResult().getFinalState());
        assertEquals(builtBlock, retriever.retrieveExecutionBlock("pending").getBlock());
        verify(builder, times(1)).buildPending(any());

        retriever.start();
        verify(emitter, times(1)).addListener(captor.capture());
        listener = captor.getValue();
        when(blockchain.getBestBlock()).thenReturn(newBestBlock);
        listener.onBestBlock(newBestBlock, Collections.emptyList());
        assertNull(retriever.getCachedPendingBlockResult());

        assertEquals(anotherBuiltBlock, retriever.retrieveExecutionBlock("pending").getBlock());
        verify(builder, times(2)).buildPending(any());

        retriever.stop();
        verify(emitter, times(1)).removeListener(listener);
    }

    @Test
    void getByNumberBlockExistsHex() {
        Block myBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(123))
                .thenReturn(myBlock);

        assertThat(retriever.retrieveExecutionBlock("0x7B").getBlock(), is(myBlock));
        assertThat(retriever.retrieveExecutionBlock("0x7b").getBlock(), is(myBlock));
    }

    @Test
    void getByNumberBlockExistsDec() {
        Block myBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(123))
                .thenReturn(myBlock);

        assertThat(retriever.retrieveExecutionBlock("123").getBlock(), is(myBlock));
    }

    @Test
    void getByNumberInvalidBlockNumberHex() {
        when(blockchain.getBlockByNumber(123))
                .thenReturn(null);

        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("0x7B"));
        Assertions.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
    }

    @Test
    void getByNumberInvalidBlockNumberDec() {
        when(blockchain.getBlockByNumber(123))
                .thenReturn(null);
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("123"));
        Assertions.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
    }

    @Test
    void getByNumberInvalidHex() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("0xzz"));
        Assertions.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());

        verify(blockchain, never()).getBlockByNumber(any(long.class));
    }

    @Test
    void getByNumberInvalidDec() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("zz"));
        Assertions.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
        verify(blockchain, never()).getBlockByNumber(any(long.class));
    }

    @Test
    void getOtherThanPendingLatestOrNumberThrows() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("other"));
        Assertions.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
    }

    @Test
    void testRetrieveExecutionBlockEarliest() {
        // Mocking behavior for blockchain.getBlockByNumber(0)
        Block mockBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(0)).thenReturn(mockBlock);

        ExecutionBlockRetriever.Result result = retriever.retrieveExecutionBlock("earliest");

        assertEquals(mockBlock, result.getBlock());
        assertNull(result.getFinalState());
    }
}
