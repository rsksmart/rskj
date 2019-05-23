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
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class MiningMainchainViewImplTest {

    @Test
    public void creationIsCorrect() {
        Blockchain realBlockchain = createBlockchain(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                realBlockchain,
                448);

        List<Block> result = testBlockchain.get();

        assertNotNull(result);

        Block bestBlock = realBlockchain.getBestBlock();
        assertThat(result.get(0).getNumber(), is(2L));
        assertThat(result.get(0).getHash(), is(bestBlock.getHash()));

        Block bestBlockParent = realBlockchain.getBlockByHash(bestBlock.getParentHash().getBytes());
        assertThat(result.get(1).getNumber(), is(1L));
        assertThat(result.get(1).getHash(), is(bestBlockParent.getHash()));

        Block genesisBlock = realBlockchain.getBlockByHash(bestBlockParent.getParentHash().getBytes());
        assertThat(result.get(2).getNumber(), is(0L));
        assertThat(result.get(2).getHash(), is(genesisBlock.getHash()));
    }

    @Test
    public void createWithLessBlocksThanMaxHeight() {
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                createBlockchain(10),
                11);

        List<Block> result = testBlockchain.get();

        assertNotNull(result);
        assertThat(result.size(), is(10));
    }

    @Test
    public void createWithBlocksEqualToMaxHeight() {
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                createBlockchain(4),
                4);

        List<Block> result = testBlockchain.get();

        assertNotNull(result);
        assertThat(result.size(), is(4));
    }

    @Test
    public void createWithMoreBlocksThanMaxHeight() {
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                createBlockchain(8),
                6);

        List<Block> result = testBlockchain.get();

        assertNotNull(result);
        assertThat(result.size(), is(6));
    }

    /**
     * Blockchain has blocks A (genesis) -> B -> C (best block)
     * A new block D has been added to the real blockchain triggering an add on the abstract blockchain
     * After the add, abstract blockchain must be B -> C -> D (best block) because max height is 3
     */
    @Test
    public void addBlockToTheTipOfTheBlockchainGettingOverMaxHeight() {
        Blockchain realBlockchain = createBlockchain(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                realBlockchain,
                3);

        Block newBestBlockD = createBlock(3, realBlockchain.getBestBlock().getHash());
        testBlockchain.addBestBlock(newBestBlockD);

        List<Block> result = testBlockchain.get();

        assertThat(result.size(), is(3));
        Block bestBlock = result.get(0);
        assertThat(bestBlock.getNumber(), is(3L));
        assertThat(bestBlock.getHash(), is(newBestBlockD.getHash()));
    }

    /**
     * Blockchain has blocks A (genesis) -> B -> C (best block)
     * A new block D has been added to the real blockchain triggering an add on the abstract blockchain
     * After the add, abstract blockchain must be A (genesis) -> B -> C -> D (best block)
     */
    @Test
    public void addBlockToTheTipOfTheBlockchain() {
        Blockchain realBlockchain = createBlockchain(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                realBlockchain,
                448);

        Block newBestBlockD = createBlock(3, realBlockchain.getBestBlock().getHash());
        testBlockchain.addBestBlock(newBestBlockD);

        List<Block> result = testBlockchain.get();

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
    public void addNewBestBlockAtLowerHeight() {
        Blockchain realBlockchain = createBlockchain(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                realBlockchain,
                448);

        Block newBestBlockB = createBlock(1, realBlockchain.getBlockByNumber(0L).getHash());
        testBlockchain.addBestBlock(newBestBlockB);

        List<Block> result = testBlockchain.get();

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
    public void addNewBestBlockAndItsBranchToTheTipOfTheBlockchain() {
        Blockchain realBlockchain = createBlockchain(3);
        MiningMainchainViewImpl testBlockchain = new MiningMainchainViewImpl(
                realBlockchain,
                448);

        Block newBlockB = createBlock(1, realBlockchain.getBlockByNumber(0L).getHash());
        when(realBlockchain.getBlockByHash(newBlockB.getHash().getBytes())).thenReturn(newBlockB);
        when(realBlockchain.getBlockByNumber(1L)).thenReturn(newBlockB);

        Block newBestBlockC = createBlock(2, newBlockB.getHash());
        testBlockchain.addBestBlock(newBestBlockC);

        List<Block> result = testBlockchain.get();

        assertThat(result.size(), is(3));
        assertThat(result.get(0).getNumber(), is(2L));
        assertThat(result.get(0).getHash(), is(newBestBlockC.getHash()));
    }

    private Blockchain createBlockchain(int height) {
        Blockchain blockchain = mock(Blockchain.class);

        Block previousBlock = createGenesisBlock();
        when(blockchain.getBlockByHash(previousBlock.getHash().getBytes())).thenReturn(previousBlock);
        when(blockchain.getBlockByNumber(0L)).thenReturn(previousBlock);

        for(long i = 1; i < height; i++) {
            Block block = createBlock(i, previousBlock.getHash());
            when(blockchain.getBlockByHash(block.getHash().getBytes())).thenReturn(block);
            when(blockchain.getBlockByNumber(block.getNumber())).thenReturn(block);

            if(i == height - 1) {
                when(blockchain.getBestBlock()).thenReturn(block);
            }

            previousBlock = block;
        }

        return blockchain;
    }

    private Block createGenesisBlock(){
        Block block = mock(Block.class);
        when(block.isGenesis()).thenReturn(Boolean.TRUE);
        when(block.getNumber()).thenReturn(Long.valueOf(0));
        byte[] rawBlockHash = getRandomHash();
        Keccak256 blockHash = new Keccak256(rawBlockHash);
        when(block.getHash()).thenReturn(blockHash);

        return block;
    }

    private Block createBlock(long number, Keccak256 parentHash){
        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(number);
        byte[] rawBlockHash = getRandomHash();
        Keccak256 blockHash = new Keccak256(rawBlockHash);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getParentHash()).thenReturn(parentHash);

        return block;
    }

    private byte[] getRandomHash() {
        byte[] byteArray = new byte[32];
        new Random().nextBytes(byteArray);

        return byteArray;
    }
}
