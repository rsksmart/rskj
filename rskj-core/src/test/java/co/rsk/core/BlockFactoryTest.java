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
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.BlockHeaderV1;
import org.ethereum.core.Bloom;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP110;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP144;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP351;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP535;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP92;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIPUMM;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.AdditionalMatchers.lt;
import static org.mockito.ArgumentMatchers.anyLong;
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

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);
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

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);
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

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getMiningForkDetectionData(), is(decodedHeader.getMiningForkDetectionData()));
    }

    @Test
    void decodeBlockAfterHeight449AndRskip110On() {
        long number = 457L;
        enableRulesAt(number, RSKIP92, RSKIP110);
        byte[] forkDetectionData = TestUtils.generateBytes("forkDetectionData", 12);

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, forkDetectionData, null, null);

        boolean compressed = false;
        byte[] encodedBlock = header.getEncoded(false, false, compressed);
        byte[] hashForMergedMining = Arrays.copyOfRange(HashUtil.keccak256(encodedBlock), 0, 20);
        byte[] coinbase = org.bouncycastle.util.Arrays.concatenate(hashForMergedMining, forkDetectionData);
        coinbase = org.bouncycastle.util.Arrays.concatenate(RskMiningConstants.RSK_TAG, coinbase);
        header.setBitcoinMergedMiningCoinbaseTransaction(coinbase);
        header.seal();

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, compressed);
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

        boolean compressed = false;
        byte[] encodedHeader = header.getEncoded(false, false, compressed);
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(16));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, compressed);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
    }

    /**
     * If RSKIP 110 is off there should not be fork detection data in the block even
     * if
     * a valid array with that data is passed to the BlockHeader constructor.
     * This case should not happen in real life.
     */
    @Test
    void decodeWithNoMergedMiningDataAndRskip110OffAndForkDetectionData() {
        long number = 20L;
        enableRulesAt(number, RSKIP92);

        byte[] forkDetectionData = TestUtils.generateBytes("forkDetectionData", 12);
        BlockHeader header = createBlockHeader(number, forkDetectionData, null, null);

        boolean compressed = false;
        byte[] encodedHeader = header.getEncoded(false, false, compressed);
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(16));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, compressed);
        assertThat(header.getHash(), is(decodedHeader.getHash()));
    }

    @Test
    void decodeBlockRskip110OffRskipUMMOnAndNoMergedMiningFieldsValidUMMRoot() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM);

        byte[] ummRoot = TestUtils.generateBytes("ummRoot", 20);
        BlockHeader header = createBlockHeader(number, new byte[0], ummRoot, null);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(17));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

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

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

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

        assertThrows(IllegalArgumentException.class, () -> factory.decodeHeader(encodedHeader, false));
    }

    @Test
    void decodeBlockRskip110OffRskipUMMOnAndMergedMiningFieldsValidUMMRoot() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM);

        byte[] ummRoot = TestUtils.generateBytes("ummRoot", 20);
        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], ummRoot, null);

        byte[] encodedHeader = header.getFullEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(20));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

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

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

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

        assertThrows(IllegalArgumentException.class, () -> factory.decodeHeader(encodedHeader, false));
    }

    @Test
    void genesisHasVersion0() {
        assertEquals((byte) 0x0, factory.decodeBlock(genesisRaw()).getHeader().getVersion());
    }

    @Test
    void headerIsVersion0Before351Activation() {
        long number = 20L;
        enableRskip351At(number);
        BlockHeader header = factory.getBlockHeaderBuilder().setNumber(number - 1).build();
        assertEquals(0, header.getVersion());
    }

    @Test
    void headerIsVersion1After351AndBefore535Activation() {
        long number = 20L;
        enableRskip351At(number);
        BlockHeader header = factory.getBlockHeaderBuilder().setNumber(number).build();
        assertEquals(1, header.getVersion());
    }

    private BlockHeader testRSKIP351FullHeaderEncoding(byte[] encoded, byte expectedVersion, byte[] expectedLogsBloom,
                                                       short[] expectedEdges) {
        return testRSKIP351CompressedHeaderEncoding(encoded, expectedVersion, expectedLogsBloom, expectedEdges, false);
    }

    private BlockHeader testRSKIP351CompressedHeaderEncoding(byte[] encoded, byte expectedVersion,
                                                             byte[] expectedLogsBloom, short[] expectedEdges) {
        return testRSKIP351CompressedHeaderEncoding(encoded, expectedVersion, expectedLogsBloom, expectedEdges, true);
    }

    private BlockHeader testRSKIP351CompressedHeaderEncoding(byte[] encoded, byte expectedVersion,
                                                             byte[] expectedLogsBloom, short[] expectedEdges, boolean compressed) {
        BlockHeader decodedHeader = factory.decodeHeader(encoded, compressed);

        assertEquals(expectedVersion, decodedHeader.getVersion());
        assertArrayEquals(expectedLogsBloom, decodedHeader.getLogsBloom());
        assertArrayEquals(expectedEdges, decodedHeader.getTxExecutionSublistsEdges());

        return decodedHeader;
    }

    @Test
    void decodeCompressedBefore351() {
        long number = 20L;
        enableRulesAt(number, RSKIP144);
        enableRskip351At(number);

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        BlockHeader header = factory.getBlockHeaderBuilder()
                .setLogsBloom(logsBloom)
                .setNumber(number - 1)
                .build();

        byte[] encoded = header.getEncodedCompressed();

        BlockHeader decodedHeader = testRSKIP351CompressedHeaderEncoding(encoded, (byte) 0, logsBloom, null);
        assertArrayEquals(logsBloom, decodedHeader.getExtensionData());
    }

    @Test
    void decodeCompressedWithNoEdgesAndMergedMiningFields() {
        long blockNumber = 20L;
        enableRulesAt(blockNumber, RSKIP144, RSKIP92, RSKIPUMM);
        enableRskip351At(blockNumber);

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        BlockHeader header = createBlockHeaderWithMergedMiningFields(blockNumber, new byte[0], new byte[0], null,
                logsBloom);

        byte[] encoded = header.getEncodedCompressed();

        BlockHeader decodedHeader = testRSKIP351CompressedHeaderEncoding(encoded, (byte) 1, null, null);

        assertArrayEquals(header.getExtensionData(), decodedHeader.getExtensionData());
        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getUmmRoot(), is(decodedHeader.getUmmRoot()));
    }

    /**
     * note on decodeCompressedOfExtendedBefore351 &
     * decodeCompressedOfExtendedBefore351WithEdges:
     * while nodes activate hf, the new nodes will decode
     * blocks headers v0 with compressed=true when old nodes send
     * block headers encoded in the old fashion. in consequence,
     * decode(compressed=true) needs to handle encoded(compress=false)
     * for blocks v0
     */

    @Test
    void decodeCompressedOfExtendedBefore351() {
        long number = 20L;
        enableRulesAt(number, RSKIP144);
        enableRskip351At(number);

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        BlockHeader header = factory.getBlockHeaderBuilder()
                .setLogsBloom(logsBloom)
                .setNumber(number - 1)
                .build();

        byte[] encoded = header.getFullEncoded(); // used before hf

        BlockHeader decodedHeader = testRSKIP351CompressedHeaderEncoding(encoded, (byte) 0, logsBloom, null);
        assertArrayEquals(logsBloom, decodedHeader.getExtensionData());
    }

    @Test
    void decodeCompressedAfter351WithEdges() {
        long blockNumber = 20L;
        enableRulesAt(blockNumber, RSKIP144);
        enableRskip351At(blockNumber);

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        short[] edges = {1, 2, 3, 4};

        BlockHeader header = factory.getBlockHeaderBuilder()
                .setLogsBloom(logsBloom)
                .setTxExecutionSublistsEdges(edges)
                .setNumber(blockNumber)
                .build();

        byte[] encoded = header.getEncodedCompressed();

        BlockHeader decodedHeader = testRSKIP351CompressedHeaderEncoding(encoded, (byte) 1, null, null);
        assertArrayEquals(BlockHeaderV1.createExtensionData(header.getExtension()), decodedHeader.getExtensionData());
    }

    @Test
    void decodeFullBefore351And144() {
        long number = 20L;
        long blockNumber = number - 1;
        enableRulesAt(number, RSKIP144);
        enableRskip351At(number);

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        BlockHeader header = factory.getBlockHeaderBuilder()
                .setLogsBloom(logsBloom)
                .setNumber(blockNumber)
                .build();

        byte[] encoded = header.getFullEncoded();

        BlockHeader decodedHeader = testRSKIP351FullHeaderEncoding(encoded, (byte) 0, logsBloom, null);
        assertArrayEquals(header.getLogsBloom(), decodedHeader.getExtensionData());
    }

    @Test
    void decodeFullAfter351WithEdges() {
        long blockNumber = 20L;
        enableRulesAt(blockNumber, RSKIP144, RSKIP351);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod(); // Should return BlockHeaderV1 as RSKIP535 is not active

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        short[] edges = {1, 2, 3, 4};

        BlockHeader header = factory.getBlockHeaderBuilder()
                .setLogsBloom(logsBloom)
                .setTxExecutionSublistsEdges(edges)
                .setNumber(blockNumber)
                .build();

        byte[] encoded = header.getFullEncoded();

        BlockHeader decodedHeader = testRSKIP351FullHeaderEncoding(encoded, (byte) 1, logsBloom, edges);
        assertArrayEquals(BlockHeaderV1.createExtensionData(header.getExtension()), decodedHeader.getExtensionData());
    }

    @Test
    void decodeBlockRskip144OnRskipUMMOnAndMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIPUMM, RSKIP144, RSKIP351);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod(); // Should return BlockHeaderV1 as RSKIP535 is not active

        short[] edges = TestUtils.randomShortArray("edges", 4);

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], new byte[0], edges, logsBloom);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(22));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getTxExecutionSublistsEdges(), is(edges));
    }

    @Test
    void decodeBlockHeaderRSKIP1444ON_UMMOn_MergedMiningFieldsOFF_RSKIP5350FF() {
        long number = 500L;
        enableRulesAt(number, RSKIPUMM, RSKIP144, RSKIP351);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod(); // Should return BlockHeaderV1 as RSKIP535 is not active

        short[] edges = TestUtils.randomShortArray("edges", 4);

        BlockHeader header = createBlockHeader(number, new byte[0], new byte[0], edges);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(19)); // defaults(16) + UMM + EDGES + VERSION

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getTxExecutionSublistsEdges(), is(edges));
    }

    @Test
    void decodeBlockRskip144OnRskipUMMOffAndMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIP144, RSKIP351);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();  // Should return BlockHeaderV1 as RSKIP535 is not active

        short[] edges = TestUtils.randomShortArray("edges", 4);


        BlockHeader header = createBlockHeaderWithMergedMiningFields(number, new byte[0], null, edges, new byte[Bloom.BLOOM_BYTES]);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(21));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getTxExecutionSublistsEdges(), is(edges));
    }

    @Test
    void decodeBlockRskip144OnRskipUMMOffAndNoMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIP144, RSKIP351);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod(); // Should return BlockHeaderV1 as RSKIP535 is not active

        short[] edges = TestUtils.randomShortArray("edges", 4);

        BlockHeader header = createBlockHeader(number, new byte[0], null, edges);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(18));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getTxExecutionSublistsEdges(), is(edges));
    }

    /**
     * RSKIP535 requires RSKIP351 to be active. When RSKIP535 is active but RSKIP351
     * is not,
     * the header should be version 0x0 and baseEvent should not be set (should be
     * null)
     * because RSKIP535 cannot work properly without RSKIP351.
     */
    @Test
    void decodeBlockRskip535WithoutRskip351() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIP535);
        when(activationConfig.getHeaderVersion(geq(number))).thenReturn((byte) 0x0);
        when(activationConfig.isActive(eq(RSKIP351), geq(number))).thenReturn(false);

        BlockHeader header = createBlockHeader(number, new byte[0], null, null);

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        // Size calculation: 20 (base) - 1 (UMM) - 1 (RSKIP144) - 1 (RSKIP351 version) -
        // 1 (RSKIP535 baseEvent not included without RSKIP351) = 16
        assertThat(headerRLP.size(), is(16));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getVersion(), is((byte) 0x0));
        // When RSKIP351 is not active, baseEvent should be null even if RSKIP535 is
        // active
        // This verifies that RSKIP535 requires RSKIP351 to properly work
        assertThat(decodedHeader.getBaseEvent(), is((byte[]) null));
    }

    @Test
    void decodeBlockRskip535WithoutRskip144() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIP351, RSKIP535);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();

        byte[] baseEvent = TestUtils.generateBytes("baseEvent", 32);
        BlockHeader header = new TestBlockHeaderBuilder(number)
                .withBaseEvent(baseEvent)
                .build();

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(expectedHeaderSize(false, true, false, true, false)));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getVersion(), is((byte) 0x2));
        assertArrayEquals(baseEvent, decodedHeader.getBaseEvent());
        assertThat(decodedHeader.getTxExecutionSublistsEdges(), is((short[]) null));
    }

    @Test
    void decodeBlockRskip535WithRskip144AndNoMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIP351, RSKIP535, RSKIP144);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();

        byte[] baseEvent = TestUtils.generateBytes("baseEvent", 32);
        short[] edges = TestUtils.randomShortArray("edges", 4);

        BlockHeader header = new TestBlockHeaderBuilder(number)
                .withBaseEvent(baseEvent)
                .withEdges(edges)
                .build();

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(expectedHeaderSize(false, true, true, true, false)));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getVersion(), is((byte) 0x2));
        assertArrayEquals(baseEvent, decodedHeader.getBaseEvent());
        assertArrayEquals(edges, decodedHeader.getTxExecutionSublistsEdges());
    }

    @Test
    void decodeBlockRskip535WithUMMAndNoMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM, RSKIP351, RSKIP535);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();

        byte[] baseEvent = TestUtils.generateBytes("baseEvent", 32);
        byte[] ummRoot = TestUtils.generateBytes("ummRoot", 20);
        BlockHeader header = new TestBlockHeaderBuilder(number)
                .withBaseEvent(baseEvent)
                .withUmmRoot(ummRoot)
                .build();

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(expectedHeaderSize(true, true, false, true, false)));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getVersion(), is((byte) 0x2));
        assertArrayEquals(baseEvent, decodedHeader.getBaseEvent());
        assertArrayEquals(ummRoot, decodedHeader.getUmmRoot());
    }

    @Test
    void decodeBlockRskip535WithRskip144AndUMMAndNoMergedMiningFields() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM, RSKIP351, RSKIP535, RSKIP144);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();

        byte[] baseEvent = TestUtils.generateBytes("baseEvent", 32);
        byte[] ummRoot = TestUtils.generateBytes("ummRoot", 20);
        short[] edges = TestUtils.randomShortArray("edges", 4);
        BlockHeader header = new TestBlockHeaderBuilder(number)
                .withBaseEvent(baseEvent)
                .withUmmRoot(ummRoot)
                .withEdges(edges)
                .build();

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(expectedHeaderSize(true, true, true, true, false)));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getVersion(), is((byte) 0x2));
        assertArrayEquals(baseEvent, decodedHeader.getBaseEvent());
        assertArrayEquals(ummRoot, decodedHeader.getUmmRoot());
        assertArrayEquals(edges, decodedHeader.getTxExecutionSublistsEdges());
    }

    @Test
    void decodeBlockRskip535WithEmptyBaseEvent() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIP351, RSKIP535);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();

        byte[] baseEvent = new byte[0];
        BlockHeader header = new TestBlockHeaderBuilder(number)
                .withBaseEvent(baseEvent)
                .build();

        byte[] encodedHeader = header.getEncoded();
        RLPList headerRLP = RLP.decodeList(encodedHeader);
        assertThat(headerRLP.size(), is(expectedHeaderSize(false, true, false, true, false)));

        BlockHeader decodedHeader = factory.decodeHeader(encodedHeader, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(header.getVersion(), is((byte) 0x2));
        assertArrayEquals(baseEvent, decodedHeader.getBaseEvent());
    }

    @Test
    void decodeCompressedBlockRskip535WithRskip144() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIP351, RSKIP535, RSKIP144);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        byte[] baseEvent = TestUtils.generateBytes("baseEvent", 32);
        short[] edges = TestUtils.randomShortArray("edges", 4);
        BlockHeader header = new TestBlockHeaderBuilder(number)
                .withBaseEvent(baseEvent)
                .withEdges(edges)
                .withLogsBloom(logsBloom)
                .build();

        byte[] encoded = header.getEncodedCompressed();

        BlockHeader decodedHeader = factory.decodeHeader(encoded, true);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(decodedHeader.getVersion(), is((byte) 0x2));
        assertArrayEquals(header.getExtensionData(), decodedHeader.getExtensionData());
        assertThat(decodedHeader.getTxExecutionSublistsEdges(), is((short[]) null));
        assertThat(decodedHeader.getBaseEvent(), is((byte[]) null));
    }

    @Test
    void decodeCompressedBlockRskip535WithoutRskip144() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIP351, RSKIP535);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        byte[] baseEvent = TestUtils.generateBytes("baseEvent", 32);
        BlockHeader header = new TestBlockHeaderBuilder(number)
                .withBaseEvent(baseEvent)
                .withLogsBloom(logsBloom)
                .build();

        byte[] encoded = header.getEncodedCompressed();

        BlockHeader decodedHeader = factory.decodeHeader(encoded, true);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(decodedHeader.getVersion(), is((byte) 0x2));
        assertArrayEquals(header.getExtensionData(), decodedHeader.getExtensionData());
        assertThat(decodedHeader.getTxExecutionSublistsEdges(), is((short[]) null));
        assertThat(decodedHeader.getBaseEvent(), is((byte[]) null));
    }

    @Test
    void decodeCompressedBlockRskip535WithUMMWithoutRskip144() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIPUMM, RSKIP351, RSKIP535);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        byte[] baseEvent = TestUtils.generateBytes("baseEvent", 32);
        byte[] ummRoot = TestUtils.generateBytes("ummRoot", 20);
        BlockHeader header = new TestBlockHeaderBuilder(number)
                .withBaseEvent(baseEvent)
                .withUmmRoot(ummRoot)
                .withLogsBloom(logsBloom)
                .build();

        byte[] encoded = header.getEncodedCompressed();

        BlockHeader decodedHeader = factory.decodeHeader(encoded, true);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(decodedHeader.getVersion(), is((byte) 0x2));
        assertArrayEquals(header.getExtensionData(), decodedHeader.getExtensionData());
        assertArrayEquals(ummRoot, decodedHeader.getUmmRoot());
        assertThat(decodedHeader.getTxExecutionSublistsEdges(), is((short[]) null));
        assertThat(decodedHeader.getBaseEvent(), is((byte[]) null));
    }

    @Test
    void decodeFullBlockRskip535WithRskip144() {
        long number = 500L;
        enableRulesAt(number, RSKIP92, RSKIP351, RSKIP535, RSKIP144);
        when(activationConfig.getHeaderVersion(anyLong())).thenCallRealMethod();

        byte[] logsBloom = new byte[Bloom.BLOOM_BYTES];
        logsBloom[0] = 1;
        logsBloom[1] = 1;
        logsBloom[2] = 1;
        logsBloom[3] = 1;

        byte[] baseEvent = TestUtils.generateBytes("baseEvent", 32);
        short[] edges = TestUtils.randomShortArray("edges", 4);
        BlockHeader header = new TestBlockHeaderBuilder(number)
                .withBaseEvent(baseEvent)
                .withEdges(edges)
                .withLogsBloom(logsBloom)
                .build();

        byte[] encoded = header.getFullEncoded();

        BlockHeader decodedHeader = factory.decodeHeader(encoded, false);

        assertThat(header.getHash(), is(decodedHeader.getHash()));
        assertThat(decodedHeader.getVersion(), is((byte) 0x2));
        assertArrayEquals(logsBloom, decodedHeader.getLogsBloom());
        assertArrayEquals(edges, decodedHeader.getTxExecutionSublistsEdges());
        assertArrayEquals(baseEvent, decodedHeader.getBaseEvent());
    }

    @Test
    void buildHeaderWithEdges_shouldSucceedWhenRskip144AndRskip351AreActive() {
        long blockNumber = 20L;
        enableRskip351At(blockNumber);
        enableRulesAt(blockNumber, RSKIP144);

        short[] edges = {1, 2, 3, 4};

        BlockHeader header = factory.getBlockHeaderBuilder()
                .setNumber(blockNumber)
                .setTxExecutionSublistsEdges(edges)
                .build();

        assertArrayEquals(edges, header.getTxExecutionSublistsEdges());
        assertEquals(1, header.getVersion());
    }


    @Test
    void buildHeaderWithoutEdges_shouldSucceedBeforeRskip351() {
        long rskip351Activation = 20L;
        long blockNumber = rskip351Activation - 1;

        when(activationConfig.getHeaderVersion(lt(rskip351Activation))).thenReturn((byte) 0x0);
        when(activationConfig.getHeaderVersion(geq(rskip351Activation))).thenReturn((byte) 0x1);
        when(activationConfig.isActive(eq(RSKIP351), geq(blockNumber))).thenReturn(false);
        // Do NOT enable 144

        BlockHeader header = factory.getBlockHeaderBuilder()
                .setNumber(blockNumber)
                .build();

        assertEquals(0, header.getVersion());
        assertEquals(null, header.getTxExecutionSublistsEdges());
    }

    private void enableRulesAt(long number, ConsensusRule... consensusRules) {
        for (ConsensusRule consensusRule : consensusRules) {
            when(activationConfig.isActive(eq(consensusRule), geq(number))).thenReturn(true);

        }
    }

    private void enableRskip351At(long number) {
        when(activationConfig.getHeaderVersion(lt(number))).thenReturn((byte) 0x0);
        when(activationConfig.getHeaderVersion(geq(number))).thenReturn((byte) 0x1);
        when(activationConfig.isActive(eq(RSKIP351), geq(number))).thenReturn(true);
    }

    private BlockHeader createBlockHeaderWithMergedMiningFields(
            long number,
            byte[] forkDetectionData,
            byte[] ummRoot,
            short[] edges) {
        return createBlockHeaderWithMergedMiningFields(number, forkDetectionData, ummRoot, edges, null);
    }

    private BlockHeader createBlockHeaderWithMergedMiningFields(
            long number,
            byte[] forkDetectionData,
            byte[] ummRoot,
            short[] edges,
            byte[] logsBloom) {
        byte[] difficulty = BigInteger.ONE.toByteArray();
        byte[] gasLimit = BigInteger.valueOf(6800000).toByteArray();
        long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

        return factory.getBlockHeaderBuilder()
                .setParentHash(TestUtils.generateHash("parentHash" + number).getBytes())
                .setEmptyUnclesHash()
                .setCoinbase(TestUtils.generateAddress("coinbase" + number))
                .setEmptyStateRoot()
                .setTxTrieRoot("tx_trie_root".getBytes())
                .setEmptyLogsBloom()
                .setEmptyReceiptTrieRoot()
                .setDifficultyFromBytes(difficulty)
                .setNumber(number)
                .setGasLimit(gasLimit)
                .setGasUsed(3000000L)
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
                .setLogsBloom(logsBloom)
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
                .setParentHash(TestUtils.generateHash("hash" + number).getBytes())
                .setEmptyUnclesHash()
                .setCoinbase(TestUtils.generateAddress("coinbase" + number))
                .setEmptyStateRoot()
                .setTxTrieRoot("tx_trie_root".getBytes())
                .setEmptyLogsBloom()
                .setEmptyReceiptTrieRoot()
                .setDifficultyFromBytes(difficulty)
                .setNumber(number)
                .setGasLimit(gasLimit)
                .setGasUsed(3000000L)
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

    /**
     * Helper method to calculate expected RLP header size based on active features.
     */
    private int expectedHeaderSize(boolean umm, boolean version, boolean edges, boolean baseEvent,
                                   boolean mergedMining) {
        int size = 16; // Base fields
        if (umm)
            size++;
        if (version)
            size++;
        if (edges)
            size++;
        if (baseEvent)
            size++;
        if (mergedMining)
            size++;
        return size;
    }

    /**
     * Test helper builder for creating block headers with various configurations.
     */
    private class TestBlockHeaderBuilder {
        private final long number;
        private byte[] forkDetectionData = new byte[0];
        private byte[] ummRoot = null;
        private short[] edges = null;
        private byte[] baseEvent = null;
        private byte[] logsBloom = null;
        private boolean withMergedMining = false;

        TestBlockHeaderBuilder(long number) {
            this.number = number;
        }

        TestBlockHeaderBuilder withForkDetectionData(byte[] forkDetectionData) {
            this.forkDetectionData = forkDetectionData;
            return this;
        }

        TestBlockHeaderBuilder withUmmRoot(byte[] ummRoot) {
            this.ummRoot = ummRoot;
            return this;
        }

        TestBlockHeaderBuilder withEdges(short[] edges) {
            this.edges = edges;
            return this;
        }

        TestBlockHeaderBuilder withBaseEvent(byte[] baseEvent) {
            this.baseEvent = baseEvent;
            return this;
        }

        TestBlockHeaderBuilder withLogsBloom(byte[] logsBloom) {
            this.logsBloom = logsBloom;
            return this;
        }

        TestBlockHeaderBuilder withMergedMining() {
            this.withMergedMining = true;
            return this;
        }

        BlockHeader build() {
            byte[] difficulty = BigInteger.ONE.toByteArray();
            byte[] gasLimit = BigInteger.valueOf(6800000).toByteArray();
            long timestamp = 7731067; // Friday, 10 May 2019 6:04:05

            String hashPrefix = withMergedMining ? "parentHash" : "hash";
            BlockHeaderBuilder builder = factory.getBlockHeaderBuilder()
                    .setParentHash(TestUtils.generateHash(hashPrefix + number).getBytes())
                    .setEmptyUnclesHash()
                    .setCoinbase(TestUtils.generateAddress("coinbase" + number))
                    .setEmptyStateRoot()
                    .setTxTrieRoot("tx_trie_root".getBytes())
                    .setEmptyLogsBloom()
                    .setEmptyReceiptTrieRoot()
                    .setDifficultyFromBytes(difficulty)
                    .setNumber(number)
                    .setGasLimit(gasLimit)
                    .setGasUsed(3000000L)
                    .setTimestamp(timestamp)
                    .setEmptyExtraData()
                    .setMergedMiningForkDetectionData(forkDetectionData)
                    .setMinimumGasPrice(Coin.valueOf(10L))
                    .setUncleCount(0)
                    .setCreateUmmCompliantHeader(ummRoot != null)
                    .setUmmRoot(ummRoot)
                    .setTxExecutionSublistsEdges(edges)
                    .setBaseEvent(baseEvent);

            if (withMergedMining) {
                builder.setBitcoinMergedMiningHeader(new byte[80])
                        .setBitcoinMergedMiningMerkleProof(new byte[32])
                        .setBitcoinMergedMiningCoinbaseTransaction(new byte[128]);
            }

            if (logsBloom != null) {
                builder.setLogsBloom(logsBloom);
            }

            return builder.build();
        }
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
