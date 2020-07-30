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

import static java.lang.System.arraycopy;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class BlockHeaderTest {
    @Test
    public void getHashForMergedMiningWithForkDetectionDataAndIncludedOnAndMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], true, new byte[0]);

        byte[] encodedBlock = header.getEncoded(false, false);
        byte[] hashForMergedMining = Arrays.copyOfRange(HashUtil.keccak256(encodedBlock), 0, 20);
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        byte[] coinbase = concatenate(hashForMergedMining, forkDetectionData);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        byte[] hashForMergedMiningResult = header.getHashForMergedMining();

        assertThat(coinbase, is(hashForMergedMiningResult));
    }

    @Test
    public void getHashForMergedMiningWithNoForkDetectionDataAndIncludedOffAndMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, new byte[0]);

        Keccak256 hash = header.getHash();
        byte[] hashForMergedMining = header.getHashForMergedMining();

        assertNotEquals(hash.getBytes(), is(hashForMergedMining));
    }

    @Test
    public void getHashForMergedMiningWithForkDetectionDataAndIncludedOn() {
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(forkDetectionData, true, new byte[0]);

        byte[] hash = header.getHash().getBytes();
        byte[] hashFirst20Elements = Arrays.copyOfRange(hash, 0, 20);
        byte[] expectedHash = Bytes.concat(hashFirst20Elements, forkDetectionData);

        byte[] hashForMergedMining = header.getHashForMergedMining();

        assertThat(expectedHash, is(hashForMergedMining));
    }

    @Test
    public void getHashForMergedMiningWithForkDetectionDataAndIncludedOff() {
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(forkDetectionData, false, new byte[0]);

        byte[] hash = header.getHash().getBytes();
        byte[] hashForMergedMining = header.getHashForMergedMining();

        assertThat(hash, is(hashForMergedMining));
    }


    @Test
    public void getEncodedWithUmmRootWithMergedMiningFields() {
        byte[] ummRoot = TestUtils.randomBytes(20);
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, ummRoot);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(20));
    }

    @Test
    public void getEncodedWithUmmRootWithoutMergedMiningFields() {
        byte[] ummRoot = TestUtils.randomBytes(20);
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(new byte[0], false, ummRoot);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(17));
    }

    @Test
    public void getEncodedNullUmmRootWithMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, null);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(19));
    }

    @Test
    public void getEncodedNullUmmRootWithoutMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(new byte[0], false, null);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(16));
    }

    @Test
    public void getEncodedEmptyUmmRootWithMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, new byte[0]);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(20));
    }

    @Test
    public void getEncodedEmptyUmmRootWithoutMergedMiningFields() {
        BlockHeader header = createBlockHeaderWithNoMergedMiningFields(new byte[0], false, new byte[0]);

        byte[] headerEncoded = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(headerEncoded);

        assertThat(headerRLP.size(), is(17));
    }

    @Test
    public void getMiningForkDetectionData() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], true, new byte[0]);

        byte[] encodedBlock = header.getEncoded(false, false);
        byte[] hashForMergedMining = Arrays.copyOfRange(HashUtil.keccak256(encodedBlock), 0, 20);
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        byte[] coinbase = concatenate(hashForMergedMining, forkDetectionData);
        coinbase = concatenate(RskMiningConstants.RSK_TAG, coinbase);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        assertThat(forkDetectionData, is(header.getMiningForkDetectionData()));
    }

    @Test
    public void getUmmRoot() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        assertThat(header.getUmmRoot(), is(ummRoot));
    }

    @Test
    public void isUMMBlockWhenUmmRoot() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        assertThat(header.isUMMBlock(), is(true));
    }

    @Test
    public void isUMMBlockWhenNullUmmRoot() {
        byte[] ummRoot = null;
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        assertThat(header.isUMMBlock(), is(false));
    }

    @Test
    public void isUMMBlockWhenEmptyUmmRoot() {
        byte[] ummRoot = new byte[0];
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        assertThat(header.isUMMBlock(), is(false));
    }

    @Test
    public void getHashForMergedMiningWhenUmmRoot() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        byte[] forkDetectionData = new byte[]{20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot, forkDetectionData);

        byte[] encodedBlock = header.getEncoded(false, false);
        byte[] oldHashForMergedMining = HashUtil.keccak256(encodedBlock);
        byte[] leftHash = Arrays.copyOf(oldHashForMergedMining, 20);
        byte[] hashRoot = HashUtil.keccak256(concatenate(leftHash, ummRoot));
        byte[] newHashForMergedMining = concatenate(
                java.util.Arrays.copyOfRange(hashRoot, 0, 20),
                forkDetectionData
        );

        assertThat(header.getHashForMergedMining(), is(newHashForMergedMining));
    }

    @Test
    public void getHashForMergedMiningWhenEmptyUmmRoot() {
        byte[] ummRoot = new byte[0];

        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        byte[] encodedBlock = header.getEncoded(false, false);
        byte[] hashForMergedMining = HashUtil.keccak256(encodedBlock);

        assertThat(header.getHashForMergedMining(), is(hashForMergedMining));
    }

    @Test(expected = IllegalStateException.class)
    public void getHashForMergedMiningWhenUmmRootWithLengthUnder20() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        header.getHashForMergedMining();
    }

    @Test(expected = IllegalStateException.class)
    public void getHashForMergedMiningWhenUmmRootWithLengthOver20() {
        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        BlockHeader header = createBlockHeaderWithUmmRoot(ummRoot);

        header.getHashForMergedMining();
    }

    /**
     * This case is an error and should never happen in production
     */
    @Test(expected = IllegalStateException.class)
    public void getMiningForkDetectionDataNoDataCanBeFound() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], true, new byte[0]);

        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        byte[] coinbase = concatenate(RskMiningConstants.RSK_TAG, forkDetectionData);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        header.getMiningForkDetectionData();
    }

    @Test
    public void getMiningForkDetectionDataNoDataMustBeIncluded() {
        BlockHeader header = createBlockHeaderWithMergedMiningFields(new byte[0], false, new byte[0]);

        assertThat(new byte[0], is(header.getMiningForkDetectionData()));
    }

    private BlockHeader createBlockHeaderWithMergedMiningFields(
            byte[] forkDetectionData,
            boolean includeForkDetectionData, byte[] ummRoot){
        return createBlockHeader(new byte[80], new byte[32], new byte[128],
                forkDetectionData, includeForkDetectionData, ummRoot, false);
    }

    private BlockHeader createBlockHeaderWithNoMergedMiningFields(
            byte[] forkDetectionData,
            boolean includeForkDetectionData, byte[] ummRoot) {
        return createBlockHeader(null, null, null,
                forkDetectionData, includeForkDetectionData, ummRoot, true);
    }

    private BlockHeader createBlockHeaderWithUmmRoot(byte[] ummRoot) {
        return createBlockHeader(null, null, null,
                new byte[0], false, ummRoot, true);
    }

    private BlockHeader createBlockHeaderWithUmmRoot(byte[] ummRoot, byte[] forkDetectionData) {
        return createBlockHeader(null, null, null,
                                 forkDetectionData, true, ummRoot, true);
    }

    private BlockHeader createBlockHeader(byte[] bitcoinMergedMiningHeader, byte[] bitcoinMergedMiningMerkleProof,
                                          byte[] bitcoinMergedMiningCoinbaseTransaction, byte[] forkDetectionData,
                                          boolean includeForkDetectionData, byte[] ummRoot, boolean sealed) {
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
                bitcoinMergedMiningHeader,
                bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction,
                forkDetectionData,
                Coin.valueOf(10L),
                0,
                sealed,
                true,
                includeForkDetectionData,
                ummRoot);
    }

    private byte[] concatenate(byte[] left, byte[] right) {
        byte[] leftRight = Arrays.copyOf(left, left.length + right.length);
        arraycopy(right, 0, leftRight, left.length, right.length);
        return leftRight;
    }
}
