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
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.mine.BlockToMineBuilder;
import co.rsk.mine.MinerServer;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.exception.JsonRpcInvalidParamException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class ExecutionBlockRetrieverTest {

    private MiningMainchainView miningMainchainView;
    private Blockchain blockchain;
    private MinerServer minerServer;
    private BlockToMineBuilder builder;
    private ExecutionBlockRetriever retriever;

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
        when(builder.build(new ArrayList<>(Collections.singleton(bestHeader)), null))
                .thenReturn(builtBlock);

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

        Block builtBlock = mock(Block.class);
        when(bestBlock.isParentOf(builtBlock))
                .thenReturn(true);
        when(builder.build(mainchainHeaders, null))
                .thenReturn(builtBlock);

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
        when(bestBlock1.isParentOf(builtBlock1))
                .thenReturn(true);
        when(builder.build(new ArrayList<>(Collections.singleton(bestHeader1)), null))
                .thenReturn(builtBlock1);
        Block builtBlock2 = mock(Block.class);
        when(bestBlock2.isParentOf(builtBlock2))
                .thenReturn(true);
        when(builder.build(new ArrayList<>(Collections.singleton(bestHeader2)), null))
                .thenReturn(builtBlock2);

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

    @Test(expected = JsonRpcInvalidParamException.class)
    public void getByNumberInvalidBlockNumberHex() {
        when(blockchain.getBlockByNumber(123))
                .thenReturn(null);

        retriever.getExecutionBlock("0x7B");
    }

    @Test(expected = JsonRpcInvalidParamException.class)
    public void getByNumberInvalidBlockNumberDec() {
        when(blockchain.getBlockByNumber(123))
                .thenReturn(null);

        retriever.getExecutionBlock("123");
    }

    @Test(expected = JsonRpcInvalidParamException.class)
    public void getByNumberInvalidHex() {
        retriever.getExecutionBlock("0xzz");

        verify(blockchain, never()).getBlockByNumber(any(long.class));
    }

    @Test(expected = JsonRpcInvalidParamException.class)
    public void getByNumberInvalidDec() {
        retriever.getExecutionBlock("zz");

        verify(blockchain, never()).getBlockByNumber(any(long.class));
    }

    @Test(expected = JsonRpcInvalidParamException.class)
    public void getOtherThanPendingLatestOrNumberThrows() {
        retriever.getExecutionBlock("other");
    }
}
