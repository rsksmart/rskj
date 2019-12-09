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

package co.rsk.core;

import co.rsk.config.RskMiningConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegTestUtils;
import com.google.common.primitives.Bytes;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Bloom;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

public class BlockHeaderTest {
    @Test
    public void getHashForMergedMiningWithForkDetectionDataAndIncludedOnAndMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], true);

        byte[] encodedBlock = header.getEncoded(false, false);
        byte[] hashForMergedMining = Arrays.copyOfRange(HashUtil.keccak256(encodedBlock), 0, 20);
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        byte[] coinbase = org.bouncycastle.util.Arrays.concatenate(hashForMergedMining, forkDetectionData);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        byte[] hashForMergedMiningResult = header.getHashForMergedMining();

        assertThat(coinbase, is(hashForMergedMiningResult));
    }

    @Test
    public void getHashForMergedMiningWithNoForkDetectionDataAndIncludedOffAndMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false);

        Keccak256 hash = header.getHash();
        byte[] hashForMergedMining = header.getHashForMergedMining();

        assertNotEquals(hash.getBytes(), is(hashForMergedMining));
    }

    @Test
    public void getHashForMergedMiningWithForkDetectionDataAndIncludedOn() {
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        BlockHeader header = createBlockHeader(forkDetectionData, true);

        byte[] hash = header.getHash().getBytes();
        byte[] hashFirst20Elements = Arrays.copyOfRange(hash, 0, 20);
        byte[] expectedHash = Bytes.concat(hashFirst20Elements, forkDetectionData);

        byte[] hashForMergedMining = header.getHashForMergedMining();

        assertThat(expectedHash, is(hashForMergedMining));
    }

    @Test
    public void getHashForMergedMiningWithForkDetectionDataAndIncludedOff() {
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        BlockHeader header = createBlockHeader(forkDetectionData, false);

        byte[] hash = header.getHash().getBytes();
        byte[] hashForMergedMining = header.getHashForMergedMining();

        assertThat(hash, is(hashForMergedMining));
    }

    @Test
    public void getEncodedWithMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(19));
    }

    @Test
    public void getEncodedWithoutMergedMiningFields() {
        BlockHeader header = createBlockHeader(new byte[0], false);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(16));
    }

    @Test
    public void getEncodedPartitionEnds() {
        BlockHeader header1 = createBlockHeader(new byte[0], false, false);

        byte[] headerEncoded1 = header1.getFullEncoded();
        RLPList headerRLP1 = RLP.decodeList(headerEncoded1);

        assertThat(headerRLP1.size(), is(16));

        BlockHeader header2 = createBlockHeader(new byte[0], false, true);

        byte[] headerEncoded2 = header2.getFullEncoded();
        RLPList headerRLP2 = RLP.decodeList(headerEncoded2);

        assertThat(headerRLP2.size(), is(17));

        BlockHeader header3 = createBlockHeaderWithMergedMiningFields(new byte[0], false);

        byte[] headerEncoded3 = header3.getFullEncoded();
        RLPList headerRLP3 = RLP.decodeList(headerEncoded3);

        assertThat(headerRLP3.size(), is(19));

        int[] ref_partitionEnds4 = new int[] {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
        BlockHeader header4 = createBlockHeaderWithMergedMiningFields(new byte[0], false, true);
        header4.setPartitionEnds(ref_partitionEnds4);

        byte[] headerEncoded4 = header4.getFullEncoded();
        RLPList headerRLP4 = RLP.decodeList(headerEncoded4);

        assertThat(headerRLP4.size(), is(20));

        RLPElement lastItem4 = headerRLP4.get(headerRLP4.size() - 1);
        Assert.assertTrue(lastItem4 instanceof RLPList);

        int[] decodedPartitionEnds4 = BlockFactory.decodePartitionEnds(lastItem4);

        Assert.assertArrayEquals(ref_partitionEnds4, decodedPartitionEnds4);

        int[] ref_partitionEnds5 = new int[] {0,255, 256, 65535, 65536};
        BlockHeader header5 = createBlockHeaderWithPartitionEnds(new byte[0], false, ref_partitionEnds5);

        byte[] headerEncoded5 = header5.getFullEncoded();
        RLPList headerRLP5 = RLP.decodeList(headerEncoded5);

        assertThat(headerRLP5.size(), is(17));

        RLPElement lastItem5 = headerRLP5.get(headerRLP5.size() - 1);
        Assert.assertTrue(lastItem5 instanceof RLPList);

        int[] decodedPartitionEnds5 = BlockFactory.decodePartitionEnds(lastItem5);

        Assert.assertArrayEquals(ref_partitionEnds5, decodedPartitionEnds5);
    }

    @Test
    public void getMiningForkDetectionData() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], true);

        byte[] encodedBlock = header.getEncoded(false, false);
        byte[] hashForMergedMining = Arrays.copyOfRange(HashUtil.keccak256(encodedBlock), 0, 20);
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        byte[] coinbase = org.bouncycastle.util.Arrays.concatenate(hashForMergedMining, forkDetectionData);
        coinbase = org.bouncycastle.util.Arrays.concatenate(RskMiningConstants.RSK_TAG, coinbase);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        assertThat(forkDetectionData, is(header.getMiningForkDetectionData()));
    }

    /**
     * This case is an error and should never happen in production
     */
    @Test(expected = IllegalStateException.class)
    public void getMiningForkDetectionDataNoDataCanBeFound() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], true);

        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        byte[] coinbase = org.bouncycastle.util.Arrays.concatenate(RskMiningConstants.RSK_TAG, forkDetectionData);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        header.getMiningForkDetectionData();
    }

    @Test
    public void getMiningForkDetectionDataNoDataMustBeIncluded() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false);

        assertThat(new byte[0], is(header.getMiningForkDetectionData()));
    }

    private BlockHeader createBlockHeaderWithMergedMiningFields(
            byte[] forkDetectionData,
            boolean includeForkDetectionData) {
        return createBlockHeaderWithMergedMiningFields(forkDetectionData, includeForkDetectionData, false);
    }

    private BlockHeader createBlockHeaderWithMergedMiningFields(
            byte[] forkDetectionData,
            boolean includeForkDetectionData,
            boolean useParallelTxExecution){
        BlockDifficulty difficulty = new BlockDifficulty(BigInteger.ONE);
        long number = 1;
        BigInteger gasLimit = BigInteger.valueOf(6800000);
        long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

        return new BlockHeader(
                PegTestUtils.createHash3().getBytes(),
                HashUtil.keccak256(RLP.encodeList()),
                new RskAddress(TestUtils.randomAddress().getBytes()),
                HashUtil.EMPTY_TRIE_HASH,
                "tx_trie_root".getBytes(),
                HashUtil.EMPTY_TRIE_HASH,
                new Bloom().getData(),
                difficulty,
                number,
                gasLimit.toByteArray(),
                3000000,
                timestamp,
                new byte[0],
                Coin.ZERO,
                new byte[80],
                new byte[32],
                new byte[128],
                forkDetectionData,
                Coin.valueOf(10L),
                0,
                new int[]{},
                false,
                true,
                includeForkDetectionData,
                useParallelTxExecution);
    }

    private BlockHeader createBlockHeader(
            byte[] forkDetectionData,
            boolean includeForkDetectionData) {
     return createBlockHeader(forkDetectionData, includeForkDetectionData, false);
    }

    private BlockHeader createBlockHeader(
            byte[] forkDetectionData,
            boolean includeForkDetectionData,
            boolean useParallelTxExecution){
            BlockDifficulty difficulty = new BlockDifficulty(BigInteger.ONE);
            long number = 1;
            BigInteger gasLimit = BigInteger.valueOf(6800000);
            long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

            return new BlockHeader(
                    PegTestUtils.createHash3().getBytes(),
                    HashUtil.keccak256(RLP.encodeList()),
                    new RskAddress(TestUtils.randomAddress().getBytes()),
                    HashUtil.EMPTY_TRIE_HASH,
                    "tx_trie_root".getBytes(),
                    HashUtil.EMPTY_TRIE_HASH,
                    new Bloom().getData(),
                    difficulty,
                    number,
                    gasLimit.toByteArray(),
                    3000000,
                    timestamp,
                    new byte[0],
                    Coin.ZERO,
                    null,
                    null,
                    null,
                    forkDetectionData,
                    Coin.valueOf(10L),
                    0,
                    new int[]{},
                    true,
                    true,
                    includeForkDetectionData,
                    useParallelTxExecution);
    }

    private BlockHeader createBlockHeaderWithPartitionEnds(
            byte[] forkDetectionData,
            boolean includeForkDetectionData,
            int[] partitionEnds){
        BlockDifficulty difficulty = new BlockDifficulty(BigInteger.ONE);
        long number = 1;
        BigInteger gasLimit = BigInteger.valueOf(6800000);
        long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

        return new BlockHeader(
                PegTestUtils.createHash3().getBytes(),
                HashUtil.keccak256(RLP.encodeList()),
                new RskAddress(TestUtils.randomAddress().getBytes()),
                HashUtil.EMPTY_TRIE_HASH,
                "tx_trie_root".getBytes(),
                HashUtil.EMPTY_TRIE_HASH,
                new Bloom().getData(),
                difficulty,
                number,
                gasLimit.toByteArray(),
                3000000,
                timestamp,
                new byte[0],
                Coin.ZERO,
                null,
                null,
                null,
                forkDetectionData,
                Coin.valueOf(10L),
                0,
                partitionEnds,
                true,
                true,
                includeForkDetectionData,
                true);
    }
}
