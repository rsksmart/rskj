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

package org.ethereum.core;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BlockHeaderBuilderTest {
    private static final byte[] EMPTY_UNCLES_LIST_HASH = HashUtil.keccak256(RLP.encodeList(new byte[0]));

    private BlockHeaderBuilder blockHeaderBuilder;

    @BeforeEach
    void setup() {
        blockHeaderBuilder = new BlockHeaderBuilder(ActivationConfigsForTest.all());
    }

    @Test
    void createsHeaderWithParentHash() {
        Keccak256 parentHash = TestUtils.randomHash("parentHash");

        BlockHeader header = blockHeaderBuilder
                                .setParentHash(parentHash.getBytes())
                                .build();

        assertEquals(parentHash, header.getParentHash());
    }

    @Test
    void createsHeaderWithUnclesHash() {
        byte[] unclesHash = TestUtils.randomHash("unclesHash").getBytes();

        BlockHeader header = blockHeaderBuilder
                .setUnclesHash(unclesHash)
                .build();

        assertArrayEquals(unclesHash, header.getUnclesHash());
    }

    @Test
    void createsHeaderWithEmptyUnclesHash() {
        BlockHeader header = blockHeaderBuilder
                .setEmptyUnclesHash()
                .build();

        assertArrayEquals(EMPTY_UNCLES_LIST_HASH, header.getUnclesHash());
    }

    @Test
    void createsHeaderWithCoinbase() {
        RskAddress coinbase = TestUtils.randomAddress("coinbase");

        BlockHeader header = blockHeaderBuilder
                .setCoinbase(coinbase)
                .build();

        assertEquals(coinbase, header.getCoinbase());
    }

    @Test
    void createsHeaderWithStateRoot() {
        byte[] stateRoot = TestUtils.randomHash("stateRoot").getBytes();

        BlockHeader header = blockHeaderBuilder
                .setStateRoot(stateRoot)
                .build();

        assertArrayEquals(stateRoot, header.getStateRoot());
    }

    @Test
    void createsHeaderWithEmptyStateRoot() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(HashUtil.EMPTY_TRIE_HASH, header.getStateRoot());
    }

    @Test
    void createsHeaderWithTxTrieRoot() {
        byte[] txTrieRoot = TestUtils.randomHash("txTrieRoot").getBytes();

        BlockHeader header = blockHeaderBuilder
                .setTxTrieRoot(txTrieRoot)
                .build();

        assertArrayEquals(txTrieRoot, header.getTxTrieRoot());
    }

    @Test
    void createsHeaderWithEmptyTxTrieRoot() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(HashUtil.EMPTY_TRIE_HASH, header.getTxTrieRoot());
    }

    @Test
    void createsHeaderWithReceiptTrieRoot() {
        byte[] receiptTrieRoot = TestUtils.randomHash("receiptTrieRoot").getBytes();

        BlockHeader header = blockHeaderBuilder
                .setReceiptTrieRoot(receiptTrieRoot)
                .build();

        assertArrayEquals(receiptTrieRoot, header.getReceiptsRoot());
    }

    @Test
    void createsHeaderWithEmptyReceiptTrieRoot() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(HashUtil.EMPTY_TRIE_HASH, header.getReceiptsRoot());
    }

    @Test
    void createsHeaderWithLogsBloom() {
        byte[] logsBloom = TestUtils.randomHash("logsBloom").getBytes();

        BlockHeader header = blockHeaderBuilder
                .setLogsBloom(logsBloom)
                .build();

        assertArrayEquals(logsBloom, header.getLogsBloom());
    }

    @Test
    void createsHeaderWithEmptyLogsBloom() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(new Bloom().getData(), header.getLogsBloom());
    }

    @Test
    void createsHeaderWithDifficulty() {
        BlockDifficulty bDiff = new BlockDifficulty(BigInteger.valueOf(10));

        BlockHeader header = blockHeaderBuilder
                .setDifficulty(bDiff)
                .build();

        assertEquals(bDiff, header.getDifficulty());
    }

    @Test
    void createsHeaderWithDifficultyFromBytes() {
        byte[] bDiffData = new byte[] { 0, 16 };

        BlockHeader header = blockHeaderBuilder
                .setDifficultyFromBytes(bDiffData)
                .build();

        BlockDifficulty bDiff = new BlockDifficulty(BigInteger.valueOf(16));
        assertEquals(bDiff, header.getDifficulty());
    }

    @Test
    void createsHeaderWithPaidFees() {
        Coin fees = new Coin(BigInteger.valueOf(10));

        BlockHeader header = blockHeaderBuilder
                .setPaidFees(fees)
                .build();

        assertEquals(fees, header.getPaidFees());
    }

    @Test
    void createsHeaderWithEmptyPaidFees() {
        BlockHeader header = blockHeaderBuilder.build();

        assertEquals(Coin.valueOf(0), header.getPaidFees());
    }

    @Test
    void createsHeaderWithMininmumGasPrice() {
        Coin minGasPrice = new Coin(BigInteger.valueOf(10));

        BlockHeader header = blockHeaderBuilder
                .setMinimumGasPrice(minGasPrice)
                .build();

        assertEquals(minGasPrice, header.getMinimumGasPrice());
    }

    @Test
    void createsHeaderWithEmptyMinimumGasPrice() {
        BlockHeader header = blockHeaderBuilder.build();

        assertEquals(Coin.valueOf(0), header.getMinimumGasPrice());
    }

    @Test
    void createsHeaderWithMiningFields() {
        byte[] btcCoinbase = TestUtils.generateBytes(BlockHeaderBuilderTest.class, "btcCoinbase",128);
        byte[] btcHeader = TestUtils.generateBytes(BlockHeaderBuilderTest.class, "btcHeader",80);
        byte[] merkleProof = TestUtils.generateBytes(BlockHeaderBuilderTest.class, "merkleProof",32);
        byte[] extraData = TestUtils.generateBytes(BlockHeaderBuilderTest.class, "extraData",32);

        BlockHeader header = blockHeaderBuilder
                .setBitcoinMergedMiningHeader(btcHeader)
                .setBitcoinMergedMiningMerkleProof(merkleProof)
                .setBitcoinMergedMiningCoinbaseTransaction(btcCoinbase)
                .setExtraData(extraData)
                .build();

        assertArrayEquals(btcCoinbase, header.getBitcoinMergedMiningCoinbaseTransaction());
        assertArrayEquals(btcHeader, header.getBitcoinMergedMiningHeader());
        assertArrayEquals(merkleProof, header.getBitcoinMergedMiningMerkleProof());
        assertArrayEquals(extraData, header.getExtraData());
    }

    @Test
    void createsHeaderWithEmptyMergedMiningFields() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(new byte[0], header.getBitcoinMergedMiningMerkleProof());
        assertArrayEquals(new byte[0], header.getBitcoinMergedMiningHeader());
        assertArrayEquals(new byte[0], header.getBitcoinMergedMiningCoinbaseTransaction());
        assertArrayEquals(new byte[0], header.getExtraData());
    }

    @ParameterizedTest(name = "createHeader: when createConsensusCompliantHeader {0} and useRskip92Encoding {1} then expectedSize {2}")
    @ArgumentsSource(CreateHeaderArgumentsProvider.class)
    void createsHeaderWith(boolean createConsensusCompliantHeader, boolean useRskip92Encoding, int expectedSize) {
        byte[] btcCoinbase = TestUtils.generateBytes(BlockHeaderBuilderTest.class, "btcCoinbase",128);
        byte[] btcHeader = TestUtils.generateBytes(BlockHeaderBuilderTest.class, "btcHeader",80);
        byte[] merkleProof = TestUtils.generateBytes(BlockHeaderBuilderTest.class, "merkleProof",32);

        BlockHeader header = blockHeaderBuilder
                .setCreateConsensusCompliantHeader(createConsensusCompliantHeader)
                .setBitcoinMergedMiningHeader(btcHeader)
                .setBitcoinMergedMiningMerkleProof(merkleProof)
                .setBitcoinMergedMiningCoinbaseTransaction(btcCoinbase)
                .setUseRskip92Encoding(useRskip92Encoding)
                .build();

        RLPList rlpList = RLP.decodeList(header.getEncoded());
        assertEquals(expectedSize, rlpList.size());
    }

    @Test
    void createsHeaderWithIncludeForkDetectionDataOn() {
        byte[] expectedForkDetectionData = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

        BlockHeader header = blockHeaderBuilder
                .setCreateConsensusCompliantHeader(false)
                .setMergedMiningForkDetectionData(expectedForkDetectionData)
                .setIncludeForkDetectionData(true)
                .build();

        byte[] hashForMergedMining = header.getHashForMergedMining();
        byte[] forkDetectionData = Arrays.copyOfRange(hashForMergedMining, 20, 32);

        assertArrayEquals(expectedForkDetectionData, forkDetectionData);
    }

    @Test
    void createsHeaderWithIncludeForkDetectionDataOff() {
        byte[] expectedForkDetectionData = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

        BlockHeader header = blockHeaderBuilder
                .setCreateConsensusCompliantHeader(false)
                .setMergedMiningForkDetectionData(expectedForkDetectionData)
                .setIncludeForkDetectionData(false)
                .build();

        byte[] hashForMergedMining = header.getHashForMergedMining();
        byte[] retrievedForkDetectionData = Arrays.copyOfRange(hashForMergedMining, 20, 32);

        assertFalse(Arrays.equals(expectedForkDetectionData, retrievedForkDetectionData));
    }

    @Test
    void createsHeaderWithIncludeForkDetectionDataOffButConsensusCompliantOn() {
        byte[] expectedForkDetectionData = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

        BlockHeader header = blockHeaderBuilder
                .setCreateConsensusCompliantHeader(true)
                .setMergedMiningForkDetectionData(expectedForkDetectionData)
                .setIncludeForkDetectionData(false)
                .build();

        byte[] hashForMergedMining = header.getHashForMergedMining();
        byte[] retrievedForkDetectionData = Arrays.copyOfRange(hashForMergedMining, 20, 32);

        assertArrayEquals(expectedForkDetectionData, retrievedForkDetectionData);
    }

    @Test
    void createsHeaderWithEmptyMergedMiningForkDetectionData() {
        BlockHeader header = blockHeaderBuilder
                .setEmptyMergedMiningForkDetectionData()
                .build();

        assertArrayEquals(new byte[12], header.getMiningForkDetectionData());
    }

    @Test
    void createsHeaderWithUmmRoot() {
        byte[] ummRoot = TestUtils.generateBytes(BlockHeaderBuilderTest.class, "ummRoot",20);
        BlockHeader header = blockHeaderBuilder
                .setUmmRoot(ummRoot)
                .build();

        assertArrayEquals(ummRoot, header.getUmmRoot());
    }

    @Test
    void createsHeaderWithEmptyUmmRootAndRskipUmmOn() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(new byte[0], header.getUmmRoot());
    }

    @Test
    void createsHeaderWithEmptyUmmRootAndRskipUmmOff() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.allBut(ConsensusRule.RSKIPUMM));
        BlockHeader header = builder.build();

        assertNull(header.getUmmRoot());
    }

    @Test
    void createsHeaderWithNullUmmrootButUmmCompliantHeaderOn() {
        BlockHeader header = blockHeaderBuilder
                .setCreateUmmCompliantHeader(true)
                .setUmmRoot(null)
                .build();

        assertArrayEquals(new byte[0], header.getUmmRoot());
    }

    @Test
    void createsHeaderWithNullUmmrootButUmmCompliantHeaderOff() {
        BlockHeader header = blockHeaderBuilder
                .setCreateUmmCompliantHeader(false)
                .setUmmRoot(null)
                .build();

        assertArrayEquals(null, header.getUmmRoot());
    }

    @Test
    void createsHeaderWithNullUmmrootButUmmCompliantHeaderOnAndRskipUmmOff() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.allBut(ConsensusRule.RSKIPUMM));

        BlockHeader header = builder
                .setCreateUmmCompliantHeader(true)
                .setUmmRoot(null)
                .build();

        assertNull(header.getUmmRoot());
    }

    @Test
    void createsHeaderWithNullUmmrootButUmmCompliantHeaderOffAndRskipUmmOff() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.allBut(ConsensusRule.RSKIPUMM));

        BlockHeader header = builder
                .setCreateUmmCompliantHeader(false)
                .setUmmRoot(null)
                .build();

        assertArrayEquals(null, header.getUmmRoot());
    }

    private static class CreateHeaderArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(false, false, 20),
                    Arguments.of(false, true, 18),
                    Arguments.of(true, false, 18)
            );
        }
    }
}
