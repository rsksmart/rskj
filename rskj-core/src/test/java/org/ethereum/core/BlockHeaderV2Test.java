/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import org.ethereum.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockHeaderV2Test {

    @Test
    void testVersionIs2() {
        BlockHeaderV2 header = createHeaderV2();
        assertEquals(0x2, header.getVersion());
    }

    @Test
    void testGetAndSetSuperChainDataHash() {
        BlockHeaderV2 header = createHeaderV2();
        byte[] hash = new byte[]{1, 2, 3, 4};
        header.setSuperChainDataHash(hash);
        assertArrayEquals(hash, header.getSuperChainDataHash());
    }

    @Test
    void testSetSuperChainDataHashThrowsIfSealed() {
        BlockHeaderV2 header = createHeaderV2();
        header.seal();
        assertThrows(SealedBlockHeaderException.class, () -> header.setSuperChainDataHash(new byte[]{1}));
    }

    @Test
    void testExtensionIsBlockHeaderExtensionV2() {
        BlockHeaderV2 header = createHeaderV2();
        assertInstanceOf(BlockHeaderExtensionV2.class, header.getExtension());
    }

    @Test
    void testSetExtension() {
        BlockHeaderV2 header = createHeaderV2();
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(new byte[]{1}, new short[]{2}, new byte[]{3});
        header.setExtension(ext);
        assertSame(ext, header.getExtension());
    }

    @Test
    void testCreateExtensionData() {
        byte[] hash = new byte[]{9, 8, 7};
        byte[] extData = BlockHeaderV2.createExtensionData(hash);
        assertNotNull(extData);
        assertTrue(extData.length > 0);
    }

    @Test
    void testAddExtraFieldsToEncodedHeader() {
        BlockHeaderV2 header = createHeaderV2();
        java.util.List<byte[]> fields = new java.util.ArrayList<>();
        header.addExtraFieldsToEncodedHeader(false, fields);
        assertFalse(fields.isEmpty());
    }

    @Test
    void createsAnExtensionWithGivenData() {
        byte[] bloom = TestUtils.generateBytes("bloom", 256);
        BlockHeaderV2 header = createHeaderV2(bloom);
        assertArrayEquals(bloom, header.getExtension().getLogsBloom());
    }

    @Test
    void setsExtension() {
        byte[] bloom = TestUtils.generateBytes("bloom", 256);
        short[] edges = new short[]{1, 2, 3, 4};
        BlockHeaderV2 header = createHeaderV2(bloom);
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(bloom, edges, null);
        header.setExtension(extension);
        assertArrayEquals(extension.getEncoded(), header.getExtension().getEncoded());
    }

    private BlockHeaderV2 createHeaderV2() {
        return createHeaderV2(new byte[0]);
    }

    // Helper to create a minimal valid BlockHeaderV2
    private BlockHeaderV2 createHeaderV2(byte[] extensionData) {
        return new BlockHeaderV2(
                new byte[32], // parentHash
                new byte[32], // unclesHash
                new RskAddress(new byte[20]), // coinbase
                new byte[32], // stateRoot
                new byte[32], // txTrieRoot
                new byte[32], // receiptTrieRoot
                extensionData, // extensionData
                BlockDifficulty.ONE, // difficulty
                1L, // number
                new byte[8], // gasLimit
                0L, // gasUsed
                123456789L, // timestamp
                new byte[0], // extraData
                Coin.ZERO, // paidFees
                new byte[80], // bitcoinMergedMiningHeader
                new byte[0], // bitcoinMergedMiningMerkleProof
                new byte[0], // bitcoinMergedMiningCoinbaseTransaction
                new byte[0], // mergedMiningForkDetectionData
                Coin.ZERO, // minimumGasPrice
                0, // uncleCount
                false, // sealed
                false, // useRskip92Encoding
                false, // includeForkDetectionData
                new byte[0], // ummRoot
                new byte[32], // superChainDataHash
                org.ethereum.core.SuperBlockResolver.FALSE, // isSuper
                new short[0], // txExecutionSublistsEdges
                false // compressed
        );
    }
}
