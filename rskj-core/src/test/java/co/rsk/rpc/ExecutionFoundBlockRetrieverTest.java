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
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.mine.BlockToMineBuilder;
import co.rsk.mine.MinerServer;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class ExecutionFoundBlockRetrieverTest {

    private MiningMainchainView miningMainchainView;
    private Blockchain blockchain;
    private MinerServer minerServer;
    private BlockToMineBuilder builder;
    private ExecutionBlockRetriever retriever;
    private final static int INVALID_PARAM_ERROR_CODE = -32602;

    @Before
    public void setUp() {
        blockchain = mock(BlockChainImpl.class);
        miningMainchainView = mock(MiningMainchainView.class);
        minerServer = mock(MinerServer.class);
        builder = mock(BlockToMineBuilder.class);
        retriever = new ExecutionBlockRetriever(miningMainchainView, blockchain, minerServer, builder);
    }

    @Test
    public void getLatest() {
        Block latest = mock(Block.class);
        when(blockchain.getBestBlock())
                .thenReturn(latest);

        assertThat(retriever.getExecutionBlock("latest"), is(latest));
    }

    @Test
    public void getLatestIsUpToDate() {
        Block latest1 = mock(Block.class);
        Block latest2 = mock(Block.class);
        when(blockchain.getBestBlock())
                .thenReturn(latest1)
                .thenReturn(latest2);

        assertThat(retriever.getExecutionBlock("latest"), is(latest1));
        assertThat(retriever.getExecutionBlock("latest"), is(latest2));
    }

    @Test
    public void getPendingUsesMinerServerLatestBlock() {
        Block latest = mock(Block.class);
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.of(latest));

        assertThat(retriever.getExecutionBlock("pending"), is(latest));
    }

    @Test
    public void getPendingUsesMinerServerAndIsUpToDate() {
        Block latest1 = mock(Block.class);
        Block latest2 = mock(Block.class);
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.of(latest1))
                .thenReturn(Optional.of(latest2));

        assertThat(retriever.getExecutionBlock("pending"), is(latest1));
        assertThat(retriever.getExecutionBlock("pending"), is(latest2));
    }

    @Test
    public void getPendingBuildsPendingBlockIfMinerServerHasNoWork() {
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.empty());

        BlockHeader bestHeader = mock(BlockHeader.class);
        Block bestBlock = mock(Block.class);
        when(bestBlock.getHeader()).thenReturn(bestHeader);
        when(blockchain.getBestBlock())
                .thenReturn(bestBlock);

        when(miningMainchainView.get())
                .thenReturn(new ArrayList<>(Collections.singleton(bestHeader)));

        Block builtBlock = mock(Block.class);
        BlockResult blockResult = mock(BlockResult.class);
        when(builder.build(new ArrayList<>(Collections.singleton(bestHeader)), null))
                .thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(builtBlock);

        assertThat(retriever.getExecutionBlock("pending"), is(builtBlock));
    }

    @Test
    public void getPendingReturnsCachedBlockIfMinerServerHasNoWork() {
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        BlockHeader bestHeader = mock(BlockHeader.class);
        Block bestBlock = mock(Block.class);
        when(bestBlock.getHeader()).thenReturn(bestHeader);
        when(blockchain.getBestBlock())
                .thenReturn(bestBlock)
                .thenReturn(bestBlock);

        List<BlockHeader> mainchainHeaders = new ArrayList<>();
        mainchainHeaders.add(bestBlock.getHeader());
        mainchainHeaders.add(bestBlock.getHeader());
        when(miningMainchainView.get())
                .thenReturn(mainchainHeaders);

        BlockResult blockResult = mock(BlockResult.class);
        Block builtBlock = mock(Block.class);
        when(bestBlock.isParentOf(builtBlock))
                .thenReturn(true);
        when(builder.build(mainchainHeaders, null))
                .thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(builtBlock);

        assertThat(retriever.getExecutionBlock("pending"), is(builtBlock));
        assertThat(retriever.getExecutionBlock("pending"), is(builtBlock));
        verify(builder, times(1)).build(mainchainHeaders, null);
    }

    @Test
    public void getPendingDoesntUseCacheIfBestBlockHasChanged() {
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        BlockHeader bestHeader1 = mock(BlockHeader.class);
        Block bestBlock1 = mock(Block.class);
        when(bestBlock1.getHeader()).thenReturn(bestHeader1);

        BlockHeader bestHeader2 = mock(BlockHeader.class);
        Block bestBlock2 = mock(Block.class);
        when(bestBlock2.getHeader()).thenReturn(bestHeader2);

        when(blockchain.getBestBlock())
                .thenReturn(bestBlock1)
                .thenReturn(bestBlock2);

        when(miningMainchainView.get())
                .thenReturn(new ArrayList<>(Collections.singleton(bestHeader1)))
                .thenReturn(new ArrayList<>(Collections.singleton(bestHeader2)));

        Block builtBlock1 = mock(Block.class);
        when(bestBlock1.isParentOf(builtBlock1)).thenReturn(true);
        BlockResult blockResult1 = mock(BlockResult.class);
        when(blockResult1.getBlock()).thenReturn(builtBlock1);
        when(builder.build(new ArrayList<>(Collections.singleton(bestHeader1)), null)).thenReturn(blockResult1);

        Block builtBlock2 = mock(Block.class);
        when(bestBlock2.isParentOf(builtBlock2)).thenReturn(true);
        BlockResult blockResult2 = mock(BlockResult.class);
        when(blockResult2.getBlock()).thenReturn(builtBlock2);
        when(builder.build(new ArrayList<>(Collections.singleton(bestHeader2)), null)).thenReturn(blockResult2);

        assertThat(retriever.getExecutionBlock("pending"), is(builtBlock1));
        assertThat(retriever.getExecutionBlock("pending"), is(builtBlock2));
    }

    @Test
    public void getByNumberBlockExistsHex() {
        Block myBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(123))
                .thenReturn(myBlock);

        assertThat(retriever.getExecutionBlock("0x7B"), is(myBlock));
        assertThat(retriever.getExecutionBlock("0x7b"), is(myBlock));
    }

    @Test
    public void getByNumberBlockExistsDec() {
        Block myBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(123))
                .thenReturn(myBlock);

        assertThat(retriever.getExecutionBlock("123"), is(myBlock));
    }

    @Test
    public void getByNumberInvalidBlockNumberHex() {
        when(blockchain.getBlockByNumber(123))
                .thenReturn(null);

        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.getExecutionBlock("0x7B"));
        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());

    }

    @Test
    public void getByNumberInvalidBlockNumberDec() {
        when(blockchain.getBlockByNumber(123))
                .thenReturn(null);

        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.getExecutionBlock("123"));
        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
    }

    @Test
    public void getByNumberInvalidHex() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.getExecutionBlock("0xzz"));
        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());

        verify(blockchain, never()).getBlockByNumber(any(long.class));
    }

    @Test
    public void getByNumberInvalidDec() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.getExecutionBlock("zz"));
        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());

        verify(blockchain, never()).getBlockByNumber(any(long.class));
    }

    @Test
    public void getOtherThanPendingLatestOrNumberThrows() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.getExecutionBlock("other"));

        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
    }

    @Test
    public void getLatest_workaround() {
        Block latest = mock(Block.class);
        when(blockchain.getBestBlock())
                .thenReturn(latest);

        assertThat(retriever.retrieveExecutionBlock("latest").getBlock(), is(latest));
    }

    @Test
    public void getLatestIsUpToDate_workaround() {
        Block latest1 = mock(Block.class);
        Block latest2 = mock(Block.class);
        when(blockchain.getBestBlock())
                .thenReturn(latest1)
                .thenReturn(latest2);

        assertThat(retriever.retrieveExecutionBlock("latest").getBlock(), is(latest1));
        assertThat(retriever.retrieveExecutionBlock("latest").getBlock(), is(latest2));
    }

    @Test
    public void getPendingUsesMinerServerLatestBlock_workaround() {
        Block latest = mock(Block.class);
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.of(latest));

        assertThat(retriever.retrieveExecutionBlock("pending").getBlock(), is(latest));
    }

    @Test
    public void getPendingUsesMinerServerAndIsUpToDate_workaround() {
        Block latest1 = mock(Block.class);
        Block latest2 = mock(Block.class);
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.of(latest1))
                .thenReturn(Optional.of(latest2));

        assertThat(retriever.retrieveExecutionBlock("pending").getBlock(), is(latest1));
        assertThat(retriever.retrieveExecutionBlock("pending").getBlock(), is(latest2));
    }

    @Test
    public void getPendingBuildsPendingBlockIfMinerServerHasNoWork_workaround() {
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.empty());

        BlockHeader bestHeader = mock(BlockHeader.class);
        Block bestBlock = mock(Block.class);
        when(bestBlock.getHeader()).thenReturn(bestHeader);
        when(blockchain.getBestBlock())
                .thenReturn(bestBlock);

        when(miningMainchainView.get())
                .thenReturn(new ArrayList<>(Collections.singleton(bestHeader)));

        Block builtBlock = mock(Block.class);
        BlockResult blockResult = mock(BlockResult.class);
        when(builder.build(new ArrayList<>(Collections.singleton(bestHeader)), null))
                .thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(builtBlock);

        assertThat(retriever.retrieveExecutionBlock("pending").getBlock(), is(builtBlock));
    }

    @Test
    public void getPendingReturnsCachedBlockIfMinerServerHasNoWork_workaround() {
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        BlockHeader bestHeader = mock(BlockHeader.class);
        Block bestBlock = mock(Block.class);
        when(bestBlock.getHeader()).thenReturn(bestHeader);
        when(blockchain.getBestBlock())
                .thenReturn(bestBlock)
                .thenReturn(bestBlock);

        List<BlockHeader> mainchainHeaders = new ArrayList<>();
        mainchainHeaders.add(bestBlock.getHeader());
        mainchainHeaders.add(bestBlock.getHeader());
        when(miningMainchainView.get())
                .thenReturn(mainchainHeaders);

        BlockResult blockResult = mock(BlockResult.class);
        Block builtBlock = mock(Block.class);
        when(bestBlock.isParentOf(builtBlock))
                .thenReturn(true);
        when(builder.build(mainchainHeaders, null))
                .thenReturn(blockResult);
        when(blockResult.getBlock()).thenReturn(builtBlock);

        assertThat(retriever.retrieveExecutionBlock("pending"), is(blockResult));
        assertThat(retriever.retrieveExecutionBlock("pending"), is(blockResult));
        // TODO(mc): the cache doesn't work properly in getExecutionBlock_workaround.
        //           this is a known bug in version 1.0.1, and should be fixed in master
        verify(builder, times(2)).build(mainchainHeaders, null);
    }

    @Test
    public void getPendingDoesntUseCacheIfBestBlockHasChanged_workaround() {
        when(minerServer.getLatestBlock())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        BlockHeader bestHeader1 = mock(BlockHeader.class);
        Block bestBlock1 = mock(Block.class);
        when(bestBlock1.getHeader()).thenReturn(bestHeader1);

        BlockHeader bestHeader2 = mock(BlockHeader.class);
        Block bestBlock2 = mock(Block.class);
        when(bestBlock2.getHeader()).thenReturn(bestHeader2);

        when(blockchain.getBestBlock())
                .thenReturn(bestBlock1)
                .thenReturn(bestBlock2);

        when(miningMainchainView.get())
                .thenReturn(new ArrayList<>(Collections.singleton(bestHeader1)))
                .thenReturn(new ArrayList<>(Collections.singleton(bestHeader2)));

        Block builtBlock1 = mock(Block.class);
        when(bestBlock1.isParentOf(builtBlock1)).thenReturn(true);
        BlockResult blockResult1 = mock(BlockResult.class);
        when(blockResult1.getBlock()).thenReturn(builtBlock1);
        when(builder.build(new ArrayList<>(Collections.singleton(bestHeader1)), null)).thenReturn(blockResult1);

        Block builtBlock2 = mock(Block.class);
        when(bestBlock2.isParentOf(builtBlock2)).thenReturn(true);
        BlockResult blockResult2 = mock(BlockResult.class);
        when(blockResult2.getBlock()).thenReturn(builtBlock2);
        when(builder.build(new ArrayList<>(Collections.singleton(bestHeader2)), null)).thenReturn(blockResult2);

        assertThat(retriever.retrieveExecutionBlock("pending").getBlock(), is(builtBlock1));
        assertThat(retriever.retrieveExecutionBlock("pending").getBlock(), is(builtBlock2));
    }

    @Test
    public void getByNumberBlockExistsHex_workaround() {
        Block myBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(123))
                .thenReturn(myBlock);

        assertThat(retriever.retrieveExecutionBlock("0x7B").getBlock(), is(myBlock));
        assertThat(retriever.retrieveExecutionBlock("0x7b").getBlock(), is(myBlock));
    }

    @Test
    public void getByNumberBlockExistsDec_workaround() {
        Block myBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(123))
                .thenReturn(myBlock);

        assertThat(retriever.retrieveExecutionBlock("123").getBlock(), is(myBlock));
    }

    @Test
    public void getByNumberInvalidBlockNumberHex_workaround() {
        when(blockchain.getBlockByNumber(123))
                .thenReturn(null);

        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("0x7B"));
        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
    }

    @Test
    public void getByNumberInvalidBlockNumberDec_workaround() {
        when(blockchain.getBlockByNumber(123))
                .thenReturn(null);
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("123"));
        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
    }

    @Test
    public void getByNumberInvalidHex_workaround() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("0xzz"));
        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());

        verify(blockchain, never()).getBlockByNumber(any(long.class));
    }

    @Test
    public void getByNumberInvalidDec_workaround() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("zz"));
        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
        verify(blockchain, never()).getBlockByNumber(any(long.class));
    }

    @Test
    public void getOtherThanPendingLatestOrNumberThrows_workaround() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> retriever.retrieveExecutionBlock("other"));
        Assert.assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
    }
}
