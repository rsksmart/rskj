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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockFactoryTest {

    private ActivationConfig activationConfig;
    private BlockFactory factory;

    @BeforeEach
    void setUp() {
        activationConfig = mock(ActivationConfig.class);
        factory = new BlockFactory(activationConfig);
    }

    @Test
    void decodeGenesisBlock() {
        enableRulesAt(0L, RSKIP92);
        assertThat(factory.decodeBlock(genesisRaw()).getHash().getBytes(), is(genesisRawHash()));
    }

    @Test
    void newHeaderWithNoForkDetectionDataAndRskip110On() {
        long number = 20L;
        enableRulesAt(number, RSKIP92, RSKIP110);

        BlockHeader header = createBlockHeader(number, new byte[0], new byte[0], null);

        Keccak256 hash = header.getHash();
        byte[] hashForMergedMining = header.getHashForMergedMining();

        assertThat(hash.getBytes(), is(hashForMergedMining));
    }

    @Test
    void decodeBlockPriorToHeight449AndRskip110On() {
        long number = 20L;
        enableRulesAt(number, RSKIP92, RSKIP110);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], null, null);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getMiningForkDetectionData(), is(decodedHeader.getMiningForkDetectionData()));
    }

    @Test
    void decodeBlockPriorToHeight449AndRskip110Off() {
        long number = 20L;
        enableRulesAt(number, RSKIP92);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], null, null);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getMiningForkDetectionData(), is(decodedHeader.getMiningForkDetectionData()));
    }

    @Test
    void decodeBlockAfterHeight449AndRskip110OFF() {
        long number = 457L;
        enableRulesAt(number, RSKIP92);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], null, null);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getMiningForkDetectionData(), is(decodedHeader.getMiningForkDetectionData()));
    }

    @Test
    void decodeBlockAfterHeight449AndRskip110On() {
        long number = 457L;
        enableRulesAt(number, RSKIP92, RSKIP110);
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, forkDetectionData, null, null);

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
    void decodeWithNoMergedMiningDataAndRskip110OffAndNoForkDetectionData() {
        long number = 20L;
        enableRulesAt(number, RSKIP92);

        BlockHeader header = createBlockHeader(number, new byte[0], null, null);

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
    void decodeWithNoMergedMiningDataAndRskip110OffAndForkDetectionData() {
        long number = 20L;
        enableRulesAt(number, RSKIP92);

        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        BlockHeader header = createBlockHeader(number, forkDetectionData, null, null);

        byte[] encodedHeader = header.getEncoded(false, false);
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(16));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
    }

    @Test
    void decodeBlockRskip110OffRskipUMMOnAndNoMergedMiningFieldsValidUMMRoot() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM);

        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        BlockHeader header = createBlockHeader(number, new byte[0], ummRoot, null);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(17));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getUmmRoot(), is(decodedHeader.getUmmRoot()));
    }

    @Test
    void decodeBlockRskip110OffRskipUMMOnAndNoMergedMiningFieldsEmptyUMMRoot() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM);

        BlockHeader header = createBlockHeader(number, new byte[0], new byte[0], null);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(17));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getUmmRoot(), is(decodedHeader.getUmmRoot()));
    }

    @Test
    void decodeBlockRskip110OffRskipUMMOnAndNoMergedMiningFieldsNullUMMRoot() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM);

        // this should not be possible after the activation of UMM
        // blocks are expected to have an empty byte array
        BlockHeader header = createBlockHeader(number, new byte[0], null, null);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(16));

        Assertions.assertThrows(IllegalArgumentException.class, () -> factory.decodeHeader(encodedHeader));
    }

    @Test
    void decodeBlockRskip110OffRskipUMMOnAndMergedMiningFieldsValidUMMRoot() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM);

        byte[] ummRoot = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], ummRoot, null);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(20));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getUmmRoot(), is(decodedHeader.getUmmRoot()));
    }

    @Test
    void decodeBlockRskip110OffRskipUMMOnAndMergedMiningFieldsEmptyUmmRoot() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], new byte[0], null);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(20));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getUmmRoot(), is(decodedHeader.getUmmRoot()));
    }

    @Test
    void decodeBlockRskip110OffRskipUMMOnAndMergedMiningFieldsNullUmmRoot() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM);

        // this should not be possible after the activation of UMM
        // blocks are expected to have an empty byte array
        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], null, null);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19));

        Assertions.assertThrows(IllegalArgumentException.class, () -> factory.decodeHeader(encodedHeader));
    }

    @Test
    public void decodeBlockRskip144OnRskipUMMOnAndMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIPUMM, RSKIP144);
        short[] edges = TestUtils.randomShortArray(4);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], new byte[0], edges);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(21));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getTxExecutionSublistsEdges(), is(edges));
    }

    @Test
    public void decodeBlockRskip144OnRskipUMMOnAndNoMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIPUMM, RSKIP144);
        short[] edges = TestUtils.randomShortArray(4);

        BlockHeader header = createBlockHeader(number, new byte[0], new byte[0], edges);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(18));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getTxExecutionSublistsEdges(), is(edges));
    }

    @Test
    public void decodeBlockRskip144OnRskipUMMOffAndMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIP144);
        short[] edges = TestUtils.randomShortArray(4);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], null, edges);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(20));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getTxExecutionSublistsEdges(), is(edges));
    }

    @Test
    public void decodeBlockRskip144OnRskipUMMOffAndNoMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIP144);
        short[] edges = TestUtils.randomShortArray(4);

        BlockHeader header = createBlockHeader(number, new byte[0], null, edges);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(17));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getTxExecutionSublistsEdges(), is(edges));
    }

    private void enableRulesAt(long number, ConsensusRule... consensusRules) {
        for (ConsensusRule consensusRule : consensusRules) {
            when(activationConfig.isActive(eq(consensusRule), geq(number))).thenReturn(true);
        }
    }

    private BlockHeader createBlockHeaderWithMergedMiningFields(
            long number,
            byte[] forkDetectionData,
            byte[] ummRoot,
            short[] edges) {
        byte[] difficulty = BigInteger.ONE.toByteArray();
        byte[] gasLimit = BigInteger.valueOf(6800000).toByteArray();
        long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

        return factory.getBlockHeaderBuilder()
                .setParentHash(TestUtils.randomHash().getBytes())
                .setEmptyUnclesHash()
                .setCoinbase(TestUtils.randomAddress())
                .setEmptyStateRoot()
                .setTxTrieRoot("tx_trie_root".getBytes())
                .setEmptyLogsBloom()
                .setEmptyReceiptTrieRoot()
                .setDifficultyFromBytes(difficulty)
                .setNumber(number)
                .setGasLimit(gasLimit)
                .setGasUsed( 3000000L)
                .setTimestamp(timestamp)
                .setEmptyExtraData()
                .setBitcoinMergedMiningHeader(new byte[80])
                .setBitcoinMergedMiningMerkleProof(new byte[32])
                .setBitcoinMergedMiningCoinbaseTransaction(new byte[128])
                .setMergedMiningForkDetectionData(forkDetectionData)
                .setMinimumGasPrice(Coin.valueOf(10L))
                .setUncleCount(0)
                .setCreateUmmCompliantHeader(ummRoot != null)
                .setUmmRoot(ummRoot)
                .setTxExecutionSublistsEdges(edges)
                .build();
    }

    private BlockHeader createBlockHeader(
            long number,
            byte[] forkDetectionData,
            byte[] ummRoot,
            short[] edges) {
        byte[] difficulty = BigInteger.ONE.toByteArray();
        byte[] gasLimit = BigInteger.valueOf(6800000).toByteArray();
        long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

        return factory.getBlockHeaderBuilder()
                .setParentHash(TestUtils.randomHash().getBytes())
                .setEmptyUnclesHash()
                .setCoinbase(TestUtils.randomAddress())
                .setEmptyStateRoot()
                .setTxTrieRoot("tx_trie_root".getBytes())
                .setEmptyLogsBloom()
                .setEmptyReceiptTrieRoot()
                .setDifficultyFromBytes(difficulty)
                .setNumber(number)
                .setGasLimit(gasLimit)
                .setGasUsed( 3000000L)
                .setTimestamp(timestamp)
                .setEmptyExtraData()
                .setMergedMiningForkDetectionData(forkDetectionData)
                .setMinimumGasPrice(Coin.valueOf(10L))
                .setUncleCount(0)
                .setCreateUmmCompliantHeader(ummRoot != null)
                .setUmmRoot(ummRoot)
                .setTxExecutionSublistsEdges(edges)
                .build();
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
