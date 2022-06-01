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
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.*;

public class BlockHeaderBuilderTest {
    private static final byte[] EMPTY_UNCLES_LIST_HASH = HashUtil.keccak256(RLP.encodeList(new byte[0]));

    private BlockHeaderBuilder blockHeaderBuilder;

    @Before
    public void setup() {
        blockHeaderBuilder = new BlockHeaderBuilder(ActivationConfigsForTest.all());
    }

    @Test
    public void createsHeaderWithParentHash() {
        Keccak256 parentHash = TestUtils.randomHash();

        BlockHeader header = blockHeaderBuilder
                                .setParentHash(parentHash.getBytes())
                                .build();

        assertEquals(parentHash, header.getParentHash());
    }

    @Test
    public void createsHeaderWithUnclesHash() {
        byte[] unclesHash = TestUtils.randomHash().getBytes();

        BlockHeader header = blockHeaderBuilder
                .setUnclesHash(unclesHash)
                .build();

        assertArrayEquals(unclesHash, header.getUnclesHash());
    }

    @Test
    public void createsHeaderWithEmptyUnclesHash() {
        BlockHeader header = blockHeaderBuilder
                .setEmptyUnclesHash()
                .build();

        assertTrue(Arrays.equals(EMPTY_UNCLES_LIST_HASH, header.getUnclesHash()));
    }

    @Test
    public void createsHeaderWithCoinbase() {
        RskAddress coinbase = TestUtils.randomAddress();

        BlockHeader header = blockHeaderBuilder
                .setCoinbase(coinbase)
                .build();

        assertEquals(coinbase, header.getCoinbase());
    }

    @Test
    public void createsHeaderWithStateRoot() {
        byte[] stateRoot = TestUtils.randomHash().getBytes();

        BlockHeader header = blockHeaderBuilder
                .setStateRoot(stateRoot)
                .build();

        assertArrayEquals(stateRoot, header.getStateRoot());
    }

    @Test
    public void createsHeaderWithEmptyStateRoot() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(HashUtil.EMPTY_TRIE_HASH, header.getStateRoot());
    }

    @Test
    public void createsHeaderWithTxTrieRoot() {
        byte[] txTrieRoot = TestUtils.randomHash().getBytes();

        BlockHeader header = blockHeaderBuilder
                .setTxTrieRoot(txTrieRoot)
                .build();

        assertArrayEquals(txTrieRoot, header.getTxTrieRoot());
    }

    @Test
    public void createsHeaderWithEmptyTxTrieRoot() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(HashUtil.EMPTY_TRIE_HASH, header.getTxTrieRoot());
    }

    @Test
    public void createsHeaderWithReceiptTrieRoot() {
        byte[] receiptTrieRoot = TestUtils.randomHash().getBytes();

        BlockHeader header = blockHeaderBuilder
                .setReceiptTrieRoot(receiptTrieRoot)
                .build();

        assertArrayEquals(receiptTrieRoot, header.getReceiptsRoot());
    }

    @Test
    public void createsHeaderWithEmptyReceiptTrieRoot() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(HashUtil.EMPTY_TRIE_HASH, header.getReceiptsRoot());
    }

    @Test
    public void createsHeaderWithLogsBloom() {
        byte[] logsBloom = TestUtils.randomHash().getBytes();

        BlockHeader header = blockHeaderBuilder
                .setLogsBloom(logsBloom)
                .build();

        assertArrayEquals(logsBloom, header.getLogsBloom());
    }

    @Test
    public void createsHeaderWithEmptyLogsBloom() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(new Bloom().getData(), header.getLogsBloom());
    }

    @Test
    public void createsHeaderWithDifficulty() {
        BlockDifficulty bDiff = new BlockDifficulty(BigInteger.valueOf(10));

        BlockHeader header = blockHeaderBuilder
                .setDifficulty(bDiff)
                .build();

        assertEquals(bDiff, header.getDifficulty());
    }

    @Test
    public void createsHeaderWithDifficultyFromBytes() {
        byte[] bDiffData = new byte[] { 0, 16 };

        BlockHeader header = blockHeaderBuilder
                .setDifficultyFromBytes(bDiffData)
                .build();

        BlockDifficulty bDiff = new BlockDifficulty(BigInteger.valueOf(16));
        assertEquals(bDiff, header.getDifficulty());
    }

    @Test
    public void createsHeaderWithPaidFees() {
        Coin fees = new Coin(BigInteger.valueOf(10));

        BlockHeader header = blockHeaderBuilder
                .setPaidFees(fees)
                .build();

        assertEquals(fees, header.getPaidFees());
    }

    @Test
    public void createsHeaderWithEmptyPaidFees() {
        BlockHeader header = blockHeaderBuilder.build();

        assertEquals(Coin.valueOf(0), header.getPaidFees());
    }

    @Test
    public void createsHeaderWithMininmumGasPrice() {
        Coin minGasPrice = new Coin(BigInteger.valueOf(10));

        BlockHeader header = blockHeaderBuilder
                .setMinimumGasPrice(minGasPrice)
                .build();

        assertEquals(minGasPrice, header.getMinimumGasPrice());
    }

    @Test
    public void createsHeaderWithEmptyMinimumGasPrice() {
        BlockHeader header = blockHeaderBuilder.build();

        assertEquals(Coin.valueOf(0), header.getMinimumGasPrice());
    }

    @Test
    public void createsHeaderWithMiningFields() {
        byte[] btcCoinbase = TestUtils.randomBytes(128);
        byte[] btcHeader = TestUtils.randomBytes(80);
        byte[] merkleProof = TestUtils.randomBytes(32);
        byte[] extraData = TestUtils.randomBytes(32);

        BlockHeader header = blockHeaderBuilder
                .setBitcoinMergedMiningHeader(btcHeader)
                .setBitcoinMergedMiningMerkleProof(merkleProof)
                .setBitcoinMergedMiningCoinbaseTransaction(btcCoinbase)
                .setExtraData(extraData)
                .build();

        assertTrue(Arrays.equals(btcCoinbase, header.getBitcoinMergedMiningCoinbaseTransaction()));
        assertTrue(Arrays.equals(btcHeader, header.getBitcoinMergedMiningHeader()));
        assertTrue(Arrays.equals(merkleProof, header.getBitcoinMergedMiningMerkleProof()));
        assertTrue(Arrays.equals(extraData, header.getExtraData()));
    }

    @Test
    public void createsHeaderWithEmptyMergedMiningFields() {
        BlockHeader header = blockHeaderBuilder.build();

        assertTrue(Arrays.equals(new byte[0], header.getBitcoinMergedMiningMerkleProof()));
        assertTrue(Arrays.equals(new byte[0], header.getBitcoinMergedMiningHeader()));
        assertTrue(Arrays.equals(new byte[0], header.getBitcoinMergedMiningCoinbaseTransaction()));
        assertTrue(Arrays.equals(new byte[0], header.getExtraData()));
    }

    @Test
    public void createsHeaderWithUseRRSKIP92EncodingOn() {
        byte[] btcCoinbase = TestUtils.randomBytes(128);
        byte[] btcHeader = TestUtils.randomBytes(80);
        byte[] merkleProof = TestUtils.randomBytes(32);

        BlockHeader header = blockHeaderBuilder
                .setCreateConsensusCompliantHeader(false)
                .setBitcoinMergedMiningHeader(btcHeader)
                .setBitcoinMergedMiningMerkleProof(merkleProof)
                .setBitcoinMergedMiningCoinbaseTransaction(btcCoinbase)
                .setUseRskip92Encoding(true)
                .build();

        RLPList rlpList = RLP.decodeList(header.getEncoded());
        assertEquals(19, rlpList.size());
    }

    @Test
    public void createsHeaderWithUseRRSKIP92EncodingOff() {
        byte[] btcCoinbase = TestUtils.randomBytes(128);
        byte[] btcHeader = TestUtils.randomBytes(80);
        byte[] merkleProof = TestUtils.randomBytes(32);

        BlockHeader header = blockHeaderBuilder
                .setCreateConsensusCompliantHeader(false)
                .setBitcoinMergedMiningHeader(btcHeader)
                .setBitcoinMergedMiningMerkleProof(merkleProof)
                .setBitcoinMergedMiningCoinbaseTransaction(btcCoinbase)
                .setUseRskip92Encoding(false)
                .build();

        RLPList rlpList = RLP.decodeList(header.getEncoded());
        assertEquals(21, rlpList.size());
    }

    @Test
    public void createsHeaderWithUseRRSKIP92EncodingOffButConsensusCompliantOn() {
        byte[] btcCoinbase = TestUtils.randomBytes(128);
        byte[] btcHeader = TestUtils.randomBytes(80);
        byte[] merkleProof = TestUtils.randomBytes(32);

        BlockHeader header = blockHeaderBuilder
                .setCreateConsensusCompliantHeader(true)
                .setBitcoinMergedMiningHeader(btcHeader)
                .setBitcoinMergedMiningMerkleProof(merkleProof)
                .setBitcoinMergedMiningCoinbaseTransaction(btcCoinbase)
                .setUseRskip92Encoding(false)
                .build();

        // the useRskip92Field should be true, hence the merkle proof and coinbase are not included
        RLPList rlpList = RLP.decodeList(header.getEncoded());
        assertEquals(19, rlpList.size());
    }

    @Test
    public void createsHeaderWithIncludeForkDetectionDataOn() {
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
    public void createsHeaderWithIncludeForkDetectionDataOff() {
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
    public void createsHeaderWithIncludeForkDetectionDataOffButConsensusCompliantOn() {
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
    public void createsHeaderWithEmptyMergedMiningForkDetectionData() {
        BlockHeader header = blockHeaderBuilder
                .setEmptyMergedMiningForkDetectionData()
                .build();

        assertArrayEquals(new byte[12], header.getMiningForkDetectionData());
    }

    @Test
    public void createsHeaderWithUmmRoot() {
        byte[] ummRoot = TestUtils.randomBytes(20);
        BlockHeader header = blockHeaderBuilder
                .setUmmRoot(ummRoot)
                .build();

        assertArrayEquals(ummRoot, header.getUmmRoot());
    }

    @Test
    public void createsHeaderWithEmptyUmmRootAndRskipUmmOn() {
        BlockHeader header = blockHeaderBuilder.build();

        assertArrayEquals(new byte[0], header.getUmmRoot());
    }

    @Test
    public void createsHeaderWithEmptyUmmRootAndRskipUmmOff() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.allBut(ConsensusRule.RSKIPUMM));
        BlockHeader header = builder.build();

        assertNull(header.getUmmRoot());
    }

    @Test
    public void createsHeaderWithNullUmmrootButUmmCompliantHeaderOn() {
        BlockHeader header = blockHeaderBuilder
                .setCreateUmmCompliantHeader(true)
                .setUmmRoot(null)
                .build();

        assertArrayEquals(new byte[0], header.getUmmRoot());
    }

    @Test
    public void createsHeaderWithNullUmmrootButUmmCompliantHeaderOff() {
        BlockHeader header = blockHeaderBuilder
                .setCreateUmmCompliantHeader(false)
                .setUmmRoot(null)
                .build();

        assertArrayEquals(null, header.getUmmRoot());
    }

    @Test
    public void createsHeaderWithNullUmmrootButUmmCompliantHeaderOnAndRskipUmmOff() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.allBut(ConsensusRule.RSKIPUMM));

        BlockHeader header = builder
                .setCreateUmmCompliantHeader(true)
                .setUmmRoot(null)
                .build();

        assertNull(header.getUmmRoot());
    }

    @Test
    public void createsHeaderWithNullUmmrootButUmmCompliantHeaderOffAndRskipUmmOff() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.allBut(ConsensusRule.RSKIPUMM));

        BlockHeader header = builder
                .setCreateUmmCompliantHeader(false)
                .setUmmRoot(null)
                .build();

        assertArrayEquals(null, header.getUmmRoot());
    }

    @Test
    public void createsHeaderWithParallelCompliant() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.all());

        BlockHeader header = builder
                .setCreateParallelCompliantHeader(true)
                .build();

        assertArrayEquals(new short[0], header.getTxExecutionListsEdges());
    }

    @Test
    public void createsHeaderWithoutParallelCompliant() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.all());

        BlockHeader header = builder
                .setCreateParallelCompliantHeader(false)
                .build();

        assertArrayEquals(null, header.getTxExecutionListsEdges());
    }

    @Test
    public void createsHeaderWithEdges() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.all());
        short[] edges = TestUtils.randomShortArray(4);

        BlockHeader header = builder
                .setTxExecutionListsEdges(edges)
                .build();

        assertArrayEquals(edges, header.getTxExecutionListsEdges());
    }

    @Test
    public void createsHeaderWithNullEdges() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.all());

        BlockHeader header = builder
                .setTxExecutionListsEdges(null)
                .build();

        assertArrayEquals(null, header.getTxExecutionListsEdges());
    }

    @Test
    public void createsHeaderWithNullEdgesButParallelCompliant() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.all());

        BlockHeader header = builder
                .setTxExecutionListsEdges(null)
                .setCreateParallelCompliantHeader(true)
                .build();

        assertArrayEquals(new short[0], header.getTxExecutionListsEdges());
    }

    @Test
    public void createsHeaderWithoutParallelCompliantButWithEdges() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.all());
        short[] edges = TestUtils.randomShortArray(4);

        BlockHeader header = builder
                .setCreateParallelCompliantHeader(false)
                .setTxExecutionListsEdges(edges)
                .build();

        assertArrayEquals(edges, header.getTxExecutionListsEdges());
    }

    @Test
    public void createsHeaderWithEdgesButWithoutParallelCompliant() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.all());
        short[] edges = TestUtils.randomShortArray(4);

        BlockHeader header = builder
                .setTxExecutionListsEdges(edges)
                .setCreateParallelCompliantHeader(false)
                .build();

        assertArrayEquals(null, header.getTxExecutionListsEdges());
    }

    @Test
    public void createsHeaderWithParallelCompliantButWithNullEdges() {
        BlockHeaderBuilder builder = new BlockHeaderBuilder(ActivationConfigsForTest.all());

        BlockHeader header = builder
                .setCreateParallelCompliantHeader(true)
                .setTxExecutionListsEdges(null)
                .build();

        assertArrayEquals(null, header.getTxExecutionListsEdges());
    }
}
