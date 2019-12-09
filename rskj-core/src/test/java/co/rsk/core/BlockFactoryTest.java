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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Bloom;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockFactoryTest {

    private ActivationConfig activationConfig;
    private BlockFactory factory;

    @Before
    public void setUp() {
        activationConfig = mock(ActivationConfig.class);
        factory = new BlockFactory(activationConfig);
    }

    @Test
    public void decodeGenesisBlock() {
        enableRulesAt(0L, RSKIP92);
        assertThat(factory.decodeBlock(genesisRaw()).getHash().getBytes(), is(genesisRawHash()));
    }

    @Test
    public void newHeaderWithNoForkDetectionDataAndRskip110On() {
        long number = 20L;
        enableRulesAt(number, RSKIP92, RSKIP110);

        BlockHeader header = createBlockHeader(number, new byte[0]);

        Keccak256 hash = header.getHash();
        byte[] hashForMergedMining = header.getHashForMergedMining();

        assertThat(hash.getBytes(), is(hashForMergedMining));
    }

    @Test
    public void decodeBlockPriorToHeight449AndRskip110On() {
        long number = 20L;
        enableRulesAt(number, RSKIP92, RSKIP110);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0]);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getMiningForkDetectionData(), is(decodedHeader.getMiningForkDetectionData()));
    }

    @Test
    public void decodeBlockPriorToHeight449AndRskip110Off() {
        long number = 20L;
        enableRulesAt(number, RSKIP92);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0]);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getMiningForkDetectionData(), is(decodedHeader.getMiningForkDetectionData()));
    }

    @Test
    public void decodeBlockAfterHeight449AndRskip110OFF() {
        long number = 457L;
        enableRulesAt(number, RSKIP92);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0]);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getMiningForkDetectionData(), is(decodedHeader.getMiningForkDetectionData()));
    }

    @Test
    public void decodeBlockAfterHeight449AndRskip110On() {
        long number = 457L;
        enableRulesAt(number, RSKIP92, RSKIP110);
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, forkDetectionData);

        byte[] encodedBlock = header.getEncoded(false, false);
        byte[] hashForMergedMining = Arrays.copyOfRange(HashUtil.keccak256(encodedBlock), 0, 20);
        byte[] coinbase = org.bouncycastle.util.Arrays.concatenate(hashForMergedMining, forkDetectionData);
        coinbase = org.bouncycastle.util.Arrays.concatenate(RskMiningConstants.RSK_TAG, coinbase);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getMiningForkDetectionData(), is(decodedHeader.getMiningForkDetectionData()));
    }

    /**
     * This case can happen when a solution is submitted by a miner in mainnet
     * and prior to the RSKIP 110 activation.
     */
    @Test
    public void decodeWithNoMergedMiningDataAndRskip110OffAndNoForkDetectionData() {
        long number = 20L;
        enableRulesAt(number, RSKIP92);

        BlockHeader header = createBlockHeader(number, new byte[0]);

        byte[] encodedHeader = header.getEncoded(false, false);
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(16));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
    }

    /**
     *  If RSKIP 110 is off there should not be fork detection data in the block even if
     *  a valid array with that data is passed to the BlockHeader constructor.
     *  This case should not happen in real life.
     */
    @Test
    public void decodeWithNoMergedMiningDataAndRskip110OffAndForkDetectionData() {
        long number = 20L;
        enableRulesAt(number, RSKIP92);

        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        BlockHeader header = createBlockHeader(number, forkDetectionData);

        byte[] encodedHeader = header.getEncoded(false, false);
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(16));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
    }

    @Test
    public void decodeBlockRskip144On() {
        long number = 20L;
        enableRulesAt(number, RSKIP144);
        int[] ref_partitionEnds1 = new int[] {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};

        BlockHeader header1 = createBlockHeaderWithMergedMiningFields(number, new byte[0], ref_partitionEnds1);

        byte[] encodedHeader1 = header1.getFullEncoded();
        RLPList headerRLP1 = RLP.decodeList(encodedHeader1);
        assertThat(headerRLP1.size(), is(20));

        BlockHeader decodedHeader1 = factory.decodeHeader(encodedHeader1);
        assertThat(header1.getHash(), is(decodedHeader1.getHash()));
        assertThat(header1.getMiningForkDetectionData(), is(decodedHeader1.getMiningForkDetectionData()));

        int[] partitionEnds1 = decodedHeader1.getPartitionEnds();
        Assert.assertArrayEquals(ref_partitionEnds1, partitionEnds1);

        int[] ref_partitionEnds2 = new int[] {0,255,256,65535,65536};

        BlockHeader header2 = createBlockHeader(number, new byte[0], ref_partitionEnds2);

        byte[] encodedHeader2 = header2.getFullEncoded();
        RLPList headerRLP2 = RLP.decodeList(encodedHeader2);
        assertThat(headerRLP2.size(), is(17));

        BlockHeader decodedHeader2 = factory.decodeHeader(encodedHeader2);
        assertThat(header2.getHash(), is(decodedHeader2.getHash()));

        int[] partitionEnds2 = decodedHeader2.getPartitionEnds();
        Assert.assertArrayEquals(ref_partitionEnds2, partitionEnds2);
    }

    private void enableRulesAt(long number, ConsensusRule... consensusRules) {
        for (ConsensusRule consensusRule : consensusRules) {
            when(activationConfig.isActive(eq(consensusRule), geq(number))).thenReturn(true);
        }
    }

    private BlockHeader createBlockHeaderWithMergedMiningFields(
            long number,
            byte[] forkDetectionData) {
        return createBlockHeaderWithMergedMiningFields(number, forkDetectionData, new int[]{});
    }
    private BlockHeader createBlockHeaderWithMergedMiningFields(
            long number,
            byte[] forkDetectionData,
            int[] partitionEnds) {
        byte[] difficulty = BigInteger.ONE.toByteArray();
        byte[] gasLimit = BigInteger.valueOf(6800000).toByteArray();
        long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

        return factory.newHeader(
                PegTestUtils.createHash3().getBytes(),
                HashUtil.keccak256(RLP.encodeList()),
                TestUtils.randomAddress().getBytes(),
                HashUtil.EMPTY_TRIE_HASH,
                "tx_trie_root".getBytes(),
                HashUtil.EMPTY_TRIE_HASH,
                new Bloom().getData(),
                difficulty,
                number,
                gasLimit,
                3000000L,
                timestamp,
                null,
                Coin.ZERO,
                new byte[80],
                new byte[32],
                new byte[128],
                forkDetectionData,
                Coin.valueOf(10L).getBytes(),
                0,
                partitionEnds);
    }

    private BlockHeader createBlockHeader(
            long number,
            byte[] forkDetectionData) {
        return createBlockHeader(number, forkDetectionData, new int[]{});
    }

    private BlockHeader createBlockHeader(
            long number,
            byte[] forkDetectionData,
            int[] partitionEnds) {
        byte[] difficulty = BigInteger.ONE.toByteArray();
        byte[] gasLimit = BigInteger.valueOf(6800000).toByteArray();
        long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

        return factory.newHeader(
                PegTestUtils.createHash3().getBytes(),
                HashUtil.keccak256(RLP.encodeList()),
                TestUtils.randomAddress().getBytes(),
                HashUtil.EMPTY_TRIE_HASH,
                "tx_trie_root".getBytes(),
                HashUtil.EMPTY_TRIE_HASH,
                new Bloom().getData(),
                difficulty,
                number,
                gasLimit,
                3000000L,
                timestamp,
                null,
                Coin.ZERO,
                null,
                null,
                null,
                forkDetectionData,
                Coin.valueOf(10L).getBytes(),
                0,
                partitionEnds);
    }


    private static byte[] genesisRawHash() {
        return Hex.decode("cabb7fbe88cd6d922042a32ffc08ce8b1fbb37d650b9d4e7dbfe2a7469adfa42");
    }

    private static byte[] genesisRaw() {
        return Hex.decode("f901dbf901d6a00000000000000000000000000000000000000000000000000000000000000000a01dcc" +
                "4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d493479433333333333333333333333333333333333" +
                "33333a045bce5168430c42b3d568331753f900a32457b4f3748697cbd8375ff4da72641a056e81f171bcc55a6ff8345e6" +
                "92c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb" +
                "5e363b421b901000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000000000000000000000000000000085000010000080834c4b40808085434d272841800080000000c0c0");

    }
}