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

package co.rsk.mine;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.MessageSerializer;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.crypto.Keccak256;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class ForkDataDetectionCalculatorTest {
    private static final int MAX_UNCLES = 10;

    private static MessageSerializer serializer;

    @BeforeAll
     static void setUp() {
        NetworkParameters params = RegTestParams.get();
        new Context(params);
        serializer = params.getDefaultSerializer();
    }

    @Test
    void calculateWithMinPossibleBlockchainHeight() {
        List<Block> lastBlockchainBlocks = createBlockchainAsList(449);

        ForkDetectionDataCalculator builder = new ForkDetectionDataCalculator();

        byte[] forkDetectionData = builder.calculate(lastBlockchainBlocks);

        assertThat(forkDetectionData.length, is(12));

        assertThat(forkDetectionData[0],
                is(getBtcBlockHashLeastSignificantByte(lastBlockchainBlocks.get(0).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[1],
                is(getBtcBlockHashLeastSignificantByte(lastBlockchainBlocks.get(64).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[2],
                is(getBtcBlockHashLeastSignificantByte(lastBlockchainBlocks.get(128).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[3],
                is(getBtcBlockHashLeastSignificantByte(lastBlockchainBlocks.get(192).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[4],
                is(getBtcBlockHashLeastSignificantByte(lastBlockchainBlocks.get(256).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[5],
                is(getBtcBlockHashLeastSignificantByte(lastBlockchainBlocks.get(320).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[6],
                is(getBtcBlockHashLeastSignificantByte(lastBlockchainBlocks.get(384).getBitcoinMergedMiningHeader())));

        assertThat(forkDetectionData[7], is((byte)0));

        assertThat(forkDetectionData[8], is((byte)0));
        assertThat(forkDetectionData[9], is((byte)0));
        assertThat(forkDetectionData[10], is((byte)1));
        assertThat(forkDetectionData[11], is((byte)193));
    }

    @Test
    void calculateReturnsEmptyWhenNotEnoughBlocks() {
        List<Block> lastBlockchainBlocks = createBlockchainAsList(250);

        ForkDetectionDataCalculator builder = new ForkDetectionDataCalculator();

        byte[] forkDetectionData = builder.calculate(lastBlockchainBlocks);

        assertThat(forkDetectionData.length, is(0));
    }

    @Test
    void calculateWithDivisibleBy64height() {
        List<Block> lastBlockchainBlocks = createBlockchainAsList(512);
        List<Block> trimmedBlocks = lastBlockchainBlocks.subList(0, 449);

        ForkDetectionDataCalculator builder = new ForkDetectionDataCalculator();

        byte[] forkDetectionData = builder.calculate(trimmedBlocks);

        assertThat(forkDetectionData.length, is(12));

        assertThat(forkDetectionData[0],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(63).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[1],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(127).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[2],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(191).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[3],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(255).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[4],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(319).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[5],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(383).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[6],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(446).getBitcoinMergedMiningHeader())));

        assertThat(forkDetectionData[7], is((byte)0));

        assertThat(forkDetectionData[8], is((byte)0));
        assertThat(forkDetectionData[9], is((byte)0));
        assertThat(forkDetectionData[10], is((byte)2));
        assertThat(forkDetectionData[11], is((byte)0));
    }

    @Test
    void calculateWithUnclesOnPreviousBlocks() {
        List<Block> lastBlockchainBlocks = createBlockchainWithUnclesAsList(512, false);
        List<Block> trimmedBlocks = lastBlockchainBlocks.subList(0, 449);

        ForkDetectionDataCalculator builder = new ForkDetectionDataCalculator();

        byte[] forkDetectionData = builder.calculate(trimmedBlocks);

        assertThat(forkDetectionData.length, is(12));

        assertThat(forkDetectionData[0],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(63).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[1],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(127).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[2],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(191).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[3],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(255).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[4],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(319).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[5],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(383).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[6],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(446).getBitcoinMergedMiningHeader())));

        assertThat(forkDetectionData[7], is((byte)-120));

        assertThat(forkDetectionData[8], is((byte)0));
        assertThat(forkDetectionData[9], is((byte)0));
        assertThat(forkDetectionData[10], is((byte)2));
        assertThat(forkDetectionData[11], is((byte)0));
    }

    @Test
    void calculateWithMaxUnclesOnPreviousBlocks() {
        List<Block> lastBlockchainBlocks = createBlockchainWithMaxUnclesAsList(564);
        List<Block> trimmedBlocks = lastBlockchainBlocks.subList(0, 449);

        ForkDetectionDataCalculator builder = new ForkDetectionDataCalculator();

        byte[] forkDetectionData = builder.calculate(trimmedBlocks);

        assertThat(forkDetectionData.length, is(12));

        assertThat(forkDetectionData[0],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(52).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[1],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(116).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[2],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(180).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[3],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(244).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[4],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(308).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[5],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(372).getBitcoinMergedMiningHeader())));
        assertThat(forkDetectionData[6],
                is(getBtcBlockHashLeastSignificantByte(trimmedBlocks.get(436).getBitcoinMergedMiningHeader())));

        assertThat(forkDetectionData[7], is((byte)-1));

        assertThat(forkDetectionData[8], is((byte)0));
        assertThat(forkDetectionData[9], is((byte)0));
        assertThat(forkDetectionData[10], is((byte)2));
        assertThat(forkDetectionData[11], is((byte)52));
    }

    private List<Block> createBlockchainWithMaxUnclesAsList(int height) {
        return createBlockchainWithUnclesAsList(height, true);
    }

    private List<Block> createBlockchainWithUnclesAsList(int height, boolean maxUncles) {
        List<Block> blocksUncles = createBlockchainAsList(height);
        int i = 0;
        for(Block block : blocksUncles) {
            when(block.getHeader().getUncleCount()).thenReturn(maxUncles ? MAX_UNCLES : i % MAX_UNCLES);
            i++;
        }

        return blocksUncles;
    }

    private List<Block> createBlockchainAsList(int height) {
        List<Block> blockchainAsList = new ArrayList<>();

        Block previousBlock = createGenesisBlock();
        blockchainAsList.add(previousBlock);

        long bitcoinBlockTime = 1557185216L;
        for(long i = 1; i < height; i++) {
            Block block = createBlock(i, previousBlock.getHash(), bitcoinBlockTime);
            blockchainAsList.add(block);

            // There are 20 RSK blocks per BTC block
            if(i % 20 == 0) {
                bitcoinBlockTime++;
            }

            previousBlock = block;
        }

        Collections.reverse(blockchainAsList);

        return blockchainAsList;
    }

    private Block createGenesisBlock(){
        BlockHeader header =  mock(BlockHeader.class);
        when(header.isGenesis()).thenReturn(Boolean.TRUE);
        when(header.getNumber()).thenReturn(Long.valueOf(0));

        Keccak256 blockHash = TestUtils.randomHash("rawBH");
        when(header.getHash()).thenReturn(blockHash);
        byte[] randomHash = TestUtils.generateBytes("rh",32);
        when(header.getBitcoinMergedMiningHeader()).thenReturn(randomHash);

        Block block = mock(Block.class);
        when(block.getHeader()).thenReturn(header);
        when(block.getBitcoinMergedMiningHeader()).thenReturn(randomHash);

        return block;
    }

    private Block createBlock(long number, Keccak256 parentHash, long bitcoinBlockTime){
        BlockHeader header =  mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(number);
        Keccak256 blockHash = TestUtils.randomHash("rawBH");
        when(header.getHash()).thenReturn(blockHash);
        when(header.getParentHash()).thenReturn(parentHash);
        byte[] bitcoinHeader = getBtcBlock(bitcoinBlockTime).cloneAsHeader().bitcoinSerialize();
        when(header.getBitcoinMergedMiningHeader()).thenReturn(bitcoinHeader);

        Block block = mock(Block.class);
        when(block.getHeader()).thenReturn(header);
        when(block.getBitcoinMergedMiningHeader()).thenReturn(bitcoinHeader);

        return block;
    }

    private BtcBlock getBtcBlock(long blockTime) {
        // Use a BTC mainnet header as template for creating BTC blocks
        String bitcoinBlockHeaderHex = "0000002031dfbd80218f575b9155c7dabe7245519e7f308fb61f0e0000000000000000003b" +
                "feed041498b5ea8227871ddee7a19a0bba806a2fe755d48a1fa48b6a04dc89c0c2d05c38ff29172ca971f7";
        byte[] bitcoinBlockByteArray = Hex.decode(bitcoinBlockHeaderHex);

        BtcBlock bitcoinBlock = serializer.makeBlock(bitcoinBlockByteArray);
        bitcoinBlock.setTime(blockTime);

        return bitcoinBlock;
    }

    private byte getBtcBlockHashLeastSignificantByte(byte[] array) {
        byte[] blockHash = serializer.makeBlock(array).getHash().getBytes();

        return blockHash[blockHash.length - 1];
    }
}
