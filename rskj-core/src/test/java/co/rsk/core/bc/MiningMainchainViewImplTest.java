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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class MiningMainchainViewImplTest {

    @Test
    void creationIsCorrect() {
        BlockStore blockStore = createBlockStore(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                448);

        List<BlockHeader> result = testBlockchain.get();

        assertNotNull(result);

        Block bestBlock = blockStore.getBestBlock();
        assertThat(result.get(0).getNumber(), is(2L));
        assertThat(result.get(0).getHash(), is(bestBlock.getHash()));

        Block bestBlockParent = blockStore.getBlockByHash(bestBlock.getParentHash().getBytes());
        assertThat(result.get(1).getNumber(), is(1L));
        assertThat(result.get(1).getHash(), is(bestBlockParent.getHash()));

        Block genesisBlock = blockStore.getBlockByHash(bestBlockParent.getParentHash().getBytes());
        assertThat(result.get(2).getNumber(), is(0L));
        assertThat(result.get(2).getHash(), is(genesisBlock.getHash()));
    }

    @Test
    void createWithLessBlocksThanMaxHeight() {
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                createBlockStore(10),
                11);

        List<BlockHeader> result = testBlockchain.get();

        assertNotNull(result);
        assertThat(result.size(), is(10));
    }

    @Test
    void createWithBlocksEqualToMaxHeight() {
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                createBlockStore(4),
                4);

        List<BlockHeader> result = testBlockchain.get();

        assertNotNull(result);
        assertThat(result.size(), is(4));
    }

    @Test
    void createWithMoreBlocksThanMaxHeight() {
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                createBlockStore(8),
                6);

        List<BlockHeader> result = testBlockchain.get();

        assertNotNull(result);
        assertThat(result.size(), is(6));
    }

    @Test
    void createWithOnlyGenesisAndHeightOne() {
        BlockStore blockStore = createBlockStore(1);
        Block genesis = blockStore.getChainBlockByNumber(0L);
        when(blockStore.getBestBlock()).thenReturn(genesis);

        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                1);

        List<BlockHeader> result = testBlockchain.get();

        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertThat(result.get(0).isGenesis(), is(true));
    }

    @Test
    void createWithOnlyGenesisAndHeightGreaterThanOne() {
        BlockStore blockStore = createBlockStore(1);
        Block genesis = blockStore.getChainBlockByNumber(0L);
        when(blockStore.getBestBlock()).thenReturn(genesis);

        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                10);

        List<BlockHeader> result = testBlockchain.get();

        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertThat(result.get(0).isGenesis(), is(true));
    }

    /**
     * Blockchain has blocks A (genesis) -> B -> C (best block)
     * A new block D has been added to the real blockchain triggering an add on the abstract blockchain
     * After the add, abstract blockchain must be B -> C -> D (best block) because max height is 3
     */
    @Test
    void addBlockToTheTipOfTheBlockchainGettingOverMaxHeight() {
        BlockStore blockStore = createBlockStore(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                3);

        Block newBestBlockD = createBlock(3, blockStore.getBestBlock().getHash());
        testBlockchain.addBest(newBestBlockD.getHeader());

        List<BlockHeader> result = testBlockchain.get();

        assertThat(result.size(), is(3));
        BlockHeader bestHeader = result.get(0);
        assertThat(bestHeader.getNumber(), is(3L));
        assertThat(bestHeader.getHash(), is(newBestBlockD.getHash()));
    }

    /**
     * Blockchain has blocks A (genesis) -> B -> C (best block)
     * A new block D has been added to the real blockchain triggering an add on the abstract blockchain
     * After the add, abstract blockchain must be A (genesis) -> B -> C -> D (best block)
     */
    @Test
    void addBlockToTheTipOfTheBlockchain() {
        BlockStore blockStore = createBlockStore(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                448);

        Block newBestBlockD = createBlock(3, blockStore.getBestBlock().getHash());
        testBlockchain.addBest(newBestBlockD.getHeader());

        List<BlockHeader> result = testBlockchain.get();

        assertThat(result.size(), is(4));
        assertThat(result.get(0).getNumber(), is(3L));
        assertThat(result.get(0).getHash(), is(newBestBlockD.getHash()));
    }

    /**
     * Blockchain has blocks A (genesis) -> B -> C (best block)
     * A new block B' has been added to the real blockchain triggering an add on the abstract blockchain
     * After the add, abstract blockchain must be A (genesis) -> B'(best block)
     */
    @Test
    void addNewBestBlockAtLowerHeight() {
        BlockStore blockStore = createBlockStore(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                448);

        Block newBestBlockB = createBlock(1, blockStore.getChainBlockByNumber(0L).getHash());
        testBlockchain.addBest(newBestBlockB.getHeader());

        List<BlockHeader> result = testBlockchain.get();

        assertThat(result.size(), is(2));
        assertThat(result.get(0).getNumber(), is(1L));
        assertThat(result.get(0).getHash(), is(newBestBlockB.getHash()));
    }

    /**
     * Blockchain has blocks A (genesis) -> B -> C (best block)
     * A new block C' has been added to the real blockchain triggering an add on the abstract blockchain
     * After the add, abstract blockchain must be  A (genesis) -> B' -> C' (best block)
     */
    @Test
    void addNewBestBlockAndItsBranchToTheTipOfTheBlockchain() {
        BlockStore blockStore = createBlockStore(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                448);

        Block newBlockB = createBlock(1, blockStore.getChainBlockByNumber(0L).getHash());
        when(blockStore.getBlockByHash(newBlockB.getHash().getBytes())).thenReturn(newBlockB);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(newBlockB);

        Block newBestBlockC = createBlock(2, newBlockB.getHash());
        testBlockchain.addBest(newBestBlockC.getHeader());

        List<BlockHeader> result = testBlockchain.get();

        assertThat(result.size(), is(3));
        assertThat(result.get(0).getNumber(), is(2L));
        assertThat(result.get(0).getHash(), is(newBestBlockC.getHash()));
    }

    /**
     * Real Blockchain has blocks A -> B -> C -> D -> E -> F (best block)
     * The abstract chain has a certain block window, suppose D -> E -> F (best)
     * A new block child of C, D', has been added to the real blockchain triggering an add on the abstract blockchain
     * After the add, abstract blockchain must roll back and recover older blocks B and C, which would result in
     * the abstract chain B -> C -> D' (best block)
     *
     * While the test addNewBestBlockAndItsBranchToTheTipOfTheBlockchain covers branch situations
     * this test tries to address a specific performance improvement in the addBest logic, which involves
     * searching for ancestor in the chain and appending the new best header to it instead of redoing a
     * complete retrieval of the chain
     */
    @Test
    void addNewBestBlockAndItsNotChildOfTheTipButHasAParentInTheChain() {
        BlockStore blockStore = createBlockStore(10);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                4);

        Block ancestor = blockStore.getChainBlockByNumber(6L);

        Block newBestBlock = createBlock(7, ancestor.getHash());
        when(blockStore.getBlockByHash(newBestBlock.getHash().getBytes())).thenReturn(newBestBlock);
        when(blockStore.getChainBlockByNumber(7L)).thenReturn(newBestBlock);

        BlockHeader ancestorHeader = ancestor.getHeader();
        BlockHeader bestHeader = newBestBlock.getHeader();
        when(ancestorHeader.isParentOf(bestHeader)).thenReturn(true);

        List<BlockHeader> result = testBlockchain.get();
        assertThat(result.get(0).getNumber(), is(9L));
        assertThat(result.get(1).getNumber(), is(8L));
        assertThat(result.get(2).getNumber(), is(7L));
        assertThat(result.get(3).getNumber(), is(6L));

        testBlockchain.addBest(newBestBlock.getHeader());

        result = testBlockchain.get();

        assertThat(result.size(), is(4));

        assertThat(result.get(0).getNumber(), is(7L));
        assertThat(result.get(1).getNumber(), is(6L));
        assertThat(result.get(2).getNumber(), is(5L));
        assertThat(result.get(3).getNumber(), is(4L));

        assertThat(result.get(0).getHash(), is(newBestBlock.getHash()));
    }


    /**
     * Real Blockchain has blocks A -> B -> C -> D -> E -> F (best block)
     * The abstract chain has a certain block window, suppose D -> E -> F (best)
     * A new block child of genesis, G, has been added to the real blockchain triggering an add on the abstract blockchain
     * After the add, the result is the abstract chain Genesis -> G (best block)
     *
     * While the test addNewBestBlockAndItsBranchToTheTipOfTheBlockchain covers branching situations
     * this test tries to address a specific performance improvement in the addBest logic, which involves
     * searching for ancestor in the chain instead of performing a complete recalculation
     *
     * This particular test is for corner cases involving the genesis which would happen in development environments
     */
    @Test
    void addNewBestBlockAndItsNotChildOfTheTipButHasGenesisAsParent() {
        BlockStore blockStore = createBlockStore(10);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                4);

        Block ancestor = blockStore.getChainBlockByNumber(0L);

        Block newBestBlock = createBlock(1, ancestor.getHash());
        when(blockStore.getBlockByHash(newBestBlock.getHash().getBytes())).thenReturn(newBestBlock);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(newBestBlock);

        BlockHeader ancestorHeader = ancestor.getHeader();
        BlockHeader bestHeader = newBestBlock.getHeader();
        when(ancestorHeader.isParentOf(bestHeader)).thenReturn(true);

        List<BlockHeader> result = testBlockchain.get();
        assertThat(result.get(0).getNumber(), is(9L));
        assertThat(result.get(1).getNumber(), is(8L));
        assertThat(result.get(2).getNumber(), is(7L));
        assertThat(result.get(3).getNumber(), is(6L));

        testBlockchain.addBest(newBestBlock.getHeader());

        result = testBlockchain.get();

        assertThat(result.size(), is(2));

        assertThat(result.get(0).getNumber(), is(1L));
        assertThat(result.get(1).getNumber(), is(0L));

        assertThat(result.get(1).isGenesis(), is(true));

        assertThat(result.get(0).getHash(), is(newBestBlock.getHash()));
    }

    /**
     * Corner case found though other test cases. In production it's nigh impossible for this situation to take place
     */
    @Test
    void addBlockWhenBlockStoreHasOnlyGenesisAndHeightIsOne() {
        BlockStore blockStore = createBlockStore(1);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                1);

        Block ancestor = blockStore.getChainBlockByNumber(0L);

        Block newBestBlock = createBlock(1, ancestor.getHash());
        when(blockStore.getBlockByHash(newBestBlock.getHash().getBytes())).thenReturn(newBestBlock);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(newBestBlock);

        BlockHeader ancestorHeader = ancestor.getHeader();
        BlockHeader bestHeader = newBestBlock.getHeader();
        when(ancestorHeader.isParentOf(bestHeader)).thenReturn(true);

        List<BlockHeader> result = testBlockchain.get();
        assertThat(result.size(), is(1));
        assertThat(result.get(0).getNumber(), is(0L));
        assertThat(result.get(0).isGenesis(), is(true));

        testBlockchain.addBest(newBestBlock.getHeader());

        result = testBlockchain.get();

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getNumber(), is(1L));
        assertThat(result.get(0).getHash(), is(newBestBlock.getHash()));
    }

    /**
     * This is a corner case to avoid errors due to block having a null parent
     *
     * Note that it shouldn't be possible for a block of this kind to reach the MiningMainchainViewImpl
     */
    @Test
    void addNewBestBlockWithMissingParent() {
        BlockStore blockStore = createBlockStore(10);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                blockStore,
                4);

        Block newBestBlock = createBlock(13, TestUtils.generateHash("newBestBlock"));
        when(blockStore.getBlockByHash(newBestBlock.getHash().getBytes())).thenReturn(newBestBlock);
        when(blockStore.getChainBlockByNumber(13L)).thenReturn(newBestBlock);

        List<BlockHeader> result = testBlockchain.get();
        assertThat(result.get(0).getNumber(), is(9L));
        assertThat(result.get(1).getNumber(), is(8L));
        assertThat(result.get(2).getNumber(), is(7L));
        assertThat(result.get(3).getNumber(), is(6L));

        testBlockchain.addBest(newBestBlock.getHeader());

        result = testBlockchain.get();

        assertThat(result.size(), is(1));

        assertThat(result.get(0).getNumber(), is(13L));

        assertThat(result.get(0).getHash(), is(newBestBlock.getHash()));
    }

    private BlockStore createBlockStore(int height) {
        BlockStore blockStore = mock(BlockStore.class);

        Block previousBlock = createGenesisBlock();
        when(blockStore.getBlockByHash(previousBlock.getHash().getBytes())).thenReturn(previousBlock);
        when(blockStore.getChainBlockByNumber(0L)).thenReturn(previousBlock);
        when(blockStore.getBestBlock()).thenReturn(previousBlock);

        for(long i = 1; i < height; i++) {
            Block block = createBlock(i, previousBlock.getHash());
            when(blockStore.getBlockByHash(block.getHash().getBytes())).thenReturn(block);
            when(blockStore.getChainBlockByNumber(block.getNumber())).thenReturn(block);

            if(i == height - 1) {
                when(blockStore.getBestBlock()).thenReturn(block);
            }

            previousBlock = block;
        }

        return blockStore;
    }

    private Block createGenesisBlock(){
        BlockHeader header = createGenesisHeader();

        Block block = mock(Block.class);

        when(block.getHeader()).thenReturn(header);

        when(block.getNumber()).thenReturn(0L);

        Keccak256 headerHash = header.getHash();
        when(block.getHash()).thenReturn(headerHash);

        return block;
    }

    private BlockHeader createGenesisHeader() {
        BlockHeader header = mock(BlockHeader.class);

        when(header.isGenesis()).thenReturn(Boolean.TRUE);
        when(header.getNumber()).thenReturn(Long.valueOf(0));
        Keccak256 blockHash = TestUtils.generateHash("genesis");
        when(header.getHash()).thenReturn(blockHash);

        return header;
    }

    private Block createBlock(long number, Keccak256 parentHash) {
        BlockHeader header = createHeader(number, parentHash);

        Block block = mock(Block.class);

        when(block.getHeader()).thenReturn(header);

        when(block.getNumber()).thenReturn(number);
        when(block.getParentHash()).thenReturn(parentHash);

        Keccak256 headerHash = header.getHash();
        when(block.getHash()).thenReturn(headerHash);

        return block;
    }

    private BlockHeader createHeader(long number, Keccak256 parentHash){
        BlockHeader header = mock(BlockHeader.class);
        Keccak256 blockHash = TestUtils.generateHash("hash"+number);

        when(header.getNumber()).thenReturn(number);
        when(header.getHash()).thenReturn(blockHash);
        when(header.getParentHash()).thenReturn(parentHash);

        return header;
    }

}
