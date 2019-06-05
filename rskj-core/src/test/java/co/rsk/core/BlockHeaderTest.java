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
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Bloom;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
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

        byte[] headerEncoded = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(19));
    }

    @Test
    public void getEncodedWithoutMergedMiningFields() {
        BlockHeader header = createBlockHeader(new byte[0], false);

        byte[] headerEncoded = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(16));
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
            boolean includeForkDetectionData){
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
                false,
                true,
                includeForkDetectionData);
    }

    private BlockHeader createBlockHeader(
            byte[] forkDetectionData,
            boolean includeForkDetectionData){
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
                    true,
                    true,
                    includeForkDetectionData);
    }
}
