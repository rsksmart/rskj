/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.core.bc;

import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class ConsensusValidationMainchainViewImplTest {

    @Test
    void getWithHeightZeroReturnsEmpty() {
        BlockStore blockStore = mock(BlockStore.class);
        ConsensusValidationMainchainView view = new ConsensusValidationMainchainViewImpl(blockStore);

        List<BlockHeader> result = view.get(TestUtils.generateHash("blockStore"), 0);

        assertNotNull(result);
        assertThat(result.size(), is(0));
    }

    @Test
    void getThatFindsAllBlocksOnBlockStore() {
        BlockStore blockStore = createBlockStore(10);
        ConsensusValidationMainchainView view = new ConsensusValidationMainchainViewImpl(blockStore);

        Block bestBlock = blockStore.getBestBlock();
        List<BlockHeader> result = view.get(bestBlock.getHash(), 5);

        assertNotNull(result);
        assertThat(result.size(), is(5));

        assertThat(result.get(0).getHash(), is(bestBlock.getHash()));

        byte[] bestBlockParentHash = bestBlock.getParentHash().getBytes();
        assertThat(result.get(1).getHash(), is(blockStore.getBlockByHash(bestBlockParentHash).getHash()));

        byte[] blockOneParentHash = result.get(1).getParentHash().getBytes();
        assertThat(result.get(2).getHash(), is(blockStore.getBlockByHash(blockOneParentHash).getHash()));

        byte[] blockTwoParentHash = result.get(2).getParentHash().getBytes();
        assertThat(result.get(3).getHash(), is(blockStore.getBlockByHash(blockTwoParentHash).getHash()));

        byte[] blockThreeParentHash = result.get(3).getParentHash().getBytes();
        assertThat(result.get(4).getHash(), is(blockStore.getBlockByHash(blockThreeParentHash).getHash()));
    }

    @Test
    void getAllBlocksOnBlockStoreAreAllRequestedBlocks() {
        BlockStore blockStore = createBlockStore(5);
        ConsensusValidationMainchainView view = new ConsensusValidationMainchainViewImpl(blockStore);

        Block bestBlock = blockStore.getBestBlock();
        List<BlockHeader> result = view.get(bestBlock.getHash(), 5);

        assertNotNull(result);
        assertThat(result.size(), is(5));

        assertThat(result.get(0).getHash(), is(bestBlock.getHash()));

        byte[] bestBlockParentHash = bestBlock.getParentHash().getBytes();
        assertThat(result.get(1).getHash(), is(blockStore.getBlockByHash(bestBlockParentHash).getHash()));

        byte[] blockOneParentHash = result.get(1).getParentHash().getBytes();
        assertThat(result.get(2).getHash(), is(blockStore.getBlockByHash(blockOneParentHash).getHash()));

        byte[] blockTwoParentHash = result.get(2).getParentHash().getBytes();
        assertThat(result.get(3).getHash(), is(blockStore.getBlockByHash(blockTwoParentHash).getHash()));

        byte[] blockThreeParentHash = result.get(3).getParentHash().getBytes();
        assertThat(result.get(4).getHash(), is(blockStore.getBlockByHash(blockThreeParentHash).getHash()));
    }

    @Test
    void getWithMissingBlockInTheMiddleOfChain() {
        BlockStore blockStore = createBlockStore(10);
        ConsensusValidationMainchainView view = new ConsensusValidationMainchainViewImpl(blockStore);

        Block bestBlock = blockStore.getBestBlock();

        byte[] bestBlockParentHash = bestBlock.getParentHash().getBytes();
        when(blockStore.getBlockByHash(bestBlockParentHash)).thenReturn(null);

        Map<Keccak256, BlockHeader> pendingHeadersByHash = new ConcurrentHashMap<>();
        view.setPendingHeaders(pendingHeadersByHash);

        List<BlockHeader> result = view.get(bestBlock.getHash(), 5);

        assertNotNull(result);
        assertThat(result.size(), is(0));
    }

    @Test
    void getAsksForMoreBlocksThanExist() {
        BlockStore blockStore = createBlockStore(10);
        ConsensusValidationMainchainView view = new ConsensusValidationMainchainViewImpl(blockStore);

        Block bestBlock = blockStore.getBestBlock();

        byte[] bestBlockParentHash = bestBlock.getParentHash().getBytes();
        when(blockStore.getBlockByHash(bestBlockParentHash)).thenReturn(null);

        Map<Keccak256, BlockHeader> pendingHeadersByHash = new ConcurrentHashMap<>();
        view.setPendingHeaders(pendingHeadersByHash);

        List<BlockHeader> result = view.get(bestBlock.getHash(), 42);

        assertNotNull(result);
        assertThat(result.size(), is(0));
    }

    @Test
    void getThatFindsBlocksOnBlockStoreAndPendingHeaders() {
        BlockStore blockStore = createBlockStore(10);
        ConsensusValidationMainchainView view = new ConsensusValidationMainchainViewImpl(blockStore);

        Block bestBlock = blockStore.getBestBlock();

        Map<Keccak256, BlockHeader> pendingHeadersByHash = new ConcurrentHashMap<>();
        BlockHeader headerOnTopOfBestBlock = mock(BlockHeader.class);
        when(headerOnTopOfBestBlock.getHash()).thenReturn(TestUtils.generateHash("headerOnTopOfBestBlock"));
        Keccak256 bestBlockHash = bestBlock.getHash();
        when(headerOnTopOfBestBlock.getParentHash()).thenReturn(bestBlockHash);
        pendingHeadersByHash.put(headerOnTopOfBestBlock.getHash(), headerOnTopOfBestBlock);

        BlockHeader headerOnTopOfHeader = mock(BlockHeader.class);
        when(headerOnTopOfHeader.getHash()).thenReturn(TestUtils.generateHash("headerOnTopOfHeader"));
        Keccak256 headerOnTopOfBestBlockHash = headerOnTopOfBestBlock.getHash();
        when(headerOnTopOfHeader.getParentHash()).thenReturn(headerOnTopOfBestBlockHash);
        pendingHeadersByHash.put(headerOnTopOfHeader.getHash(), headerOnTopOfHeader);

        view.setPendingHeaders(pendingHeadersByHash);

        List<BlockHeader> result = view.get(headerOnTopOfHeader.getHash(), 5);

        assertNotNull(result);
        assertThat(result.size(), is(5));

        assertThat(result.get(0).getHash(), is(headerOnTopOfHeader.getHash()));

        assertThat(result.get(1).getHash(), is(headerOnTopOfBestBlock.getHash()));

        assertThat(result.get(2).getHash(), is(bestBlockHash));

        byte[] bestBlockParentHash = bestBlock.getParentHash().getBytes();
        assertThat(result.get(3).getHash(), is(blockStore.getBlockByHash(bestBlockParentHash).getHash()));

        byte[] blockOneParentHash = result.get(3).getParentHash().getBytes();
        assertThat(result.get(4).getHash(), is(blockStore.getBlockByHash(blockOneParentHash).getHash()));
    }

    private BlockStore createBlockStore(int numberOfBlocks) {
        BlockStore blockstore = mock(BlockStore.class);

        Block previousBlock = createBlock(420, TestUtils.generateHash("previousBlock"));
        when(blockstore.getBlockByHash(previousBlock.getHash().getBytes())).thenReturn(previousBlock);

        for(long i = 421; i < 420 + numberOfBlocks; i++) {
            Block block = createBlock(i, previousBlock.getHash());
            when(blockstore.getBlockByHash(block.getHash().getBytes())).thenReturn(block);

            if(i == 420 + numberOfBlocks - 1) {
                when(blockstore.getBestBlock()).thenReturn(block);
            }

            previousBlock = block;
        }

        return blockstore;
    }

    private Block createBlock(long number, Keccak256 parentHash){
        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(number);
        Keccak256 blockHash = TestUtils.generateHash("blockHash");
        when(block.getHash()).thenReturn(blockHash);
        when(block.getParentHash()).thenReturn(parentHash);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getHash()).thenReturn(blockHash);
        when(header.getParentHash()).thenReturn(parentHash);
        when(block.getHeader()).thenReturn(header);

        return block;
    }

}
