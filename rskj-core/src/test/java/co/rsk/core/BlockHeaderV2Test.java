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
package co.rsk.core;

import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeaderExtensionV2;
import org.ethereum.core.BlockHeaderV2;
import org.ethereum.core.exception.FieldMaxSizeBlockHeaderException;
import org.ethereum.core.exception.SealedBlockHeaderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockHeaderV2Test {

    @Test
    void testVersionIs2() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        // when
        int version = header.getVersion();
        // then
        assertEquals(0x2, version);
    }

    @Test
    void testGetAndSetBaseEvent() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        byte[] hash = new byte[]{1, 2, 3, 4};
        // when
        header.setBaseEvent(hash);
        // then
        assertArrayEquals(hash, header.getBaseEvent());
    }

    @Test
    void testSetBaseEventThrowsIfSealed() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        header.seal();
        // when / then
        assertThrows(SealedBlockHeaderException.class, () -> header.setBaseEvent(new byte[]{1}));
    }

    @Test
    void testExtensionIsBlockHeaderExtensionV2() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        // when
        Object ext = header.getExtension();
        // then
        assertInstanceOf(BlockHeaderExtensionV2.class, ext);
    }

    @Test
    void testSetExtension() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(new byte[]{1}, new short[]{2}, new byte[]{3});
        // when
        header.setExtension(ext);
        // then
        assertSame(ext, header.getExtension());
    }

    @Test
    void testCreateExtensionData() {
        // given
        byte[] hash = new byte[]{9, 8, 7};
        // when
        byte[] extData = BlockHeaderV2.createExtensionData(hash);
        // then
        assertNotNull(extData);
        assertTrue(extData.length > 0);
    }

    @Test
    void testAddExtraFieldsToEncodedHeader() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        java.util.List<byte[]> fields = new java.util.ArrayList<>();
        // when
        header.addExtraFieldsToEncodedHeader(false, fields);
        // then
        assertFalse(fields.isEmpty());
    }

    @Test
    void createsAnExtensionWithGivenData() {
        // given
        byte[] bloom = TestUtils.generateBytes("bloom", 256);
        BlockHeaderV2 header = createHeaderV2(bloom);
        // when
        byte[] logsBloom = header.getExtension().getLogsBloom();
        // then
        assertArrayEquals(bloom, logsBloom);
    }

    @Test
    void setsExtension() {
        // given
        byte[] bloom = TestUtils.generateBytes("bloom", 256);
        short[] edges = new short[]{1, 2, 3, 4};
        BlockHeaderV2 header = createHeaderV2(bloom);
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(bloom, edges, null);
        // when
        header.setExtension(extension);
        byte[] result = header.getExtension().getEncoded();
        // then
        assertArrayEquals(extension.getEncoded(), result);
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
                new byte[32], // baseEvent
                new short[0], // txExecutionSublistsEdges
                false // compressed
        );
    }

    @Test
    void testSetBaseEventWithEmptyArray() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        byte[] emptyArray = new byte[0];
        // when
        header.setBaseEvent(emptyArray);
        // then
        assertArrayEquals(emptyArray, header.getBaseEvent());
    }

    @Test
    void testSetBaseEventWithMaxSizeForTheEvent() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        byte[] largeBaseEvent = new byte[128];
        for (int i = 0; i < 128; i++) {
            largeBaseEvent[i] = (byte) i;
        }
        // when
        header.setBaseEvent(largeBaseEvent);
        // then
        assertArrayEquals(largeBaseEvent, header.getBaseEvent());
    }

    @Test
    void testSetBaseEventWithVeryLargeValueThrowsException() {
        // given
        int numberOfBytes = 1024;
        BlockHeaderV2 header = createHeaderV2();
        byte[] veryLargeBaseEvent = new byte[numberOfBytes];
        for (int i = 0; i < numberOfBytes; i++) {
            veryLargeBaseEvent[i] = (byte) (i % 256);
        }
        // when / then
        assertThrows(FieldMaxSizeBlockHeaderException.class, () -> {
            header.setBaseEvent(veryLargeBaseEvent);
        });
    }

    @Test
    void testSetBaseEventWithFirstLargeValueForbiddenThrowsException() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        int numberOfBytes = 129;
        byte[] firstLargeValueForbidden = new byte[numberOfBytes];
        for (int i = 0; i < numberOfBytes; i++) {
            firstLargeValueForbidden[i] = (byte) (i % 256);
        }
        // when / then
        assertThrows(FieldMaxSizeBlockHeaderException.class, () -> {
            header.setBaseEvent(firstLargeValueForbidden);
        });
    }

    @Test
    void testSetBaseEventWithSpecialBytes() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        // Test with special byte values
        byte[] specialBytes = new byte[]{0x00, (byte) 0xFF, (byte) 0x80, (byte) 0x7F, (byte) 0x01, (byte) 0xFE};
        // when
        header.setBaseEvent(specialBytes);
        // then
        assertArrayEquals(specialBytes, header.getBaseEvent());
    }

    @Test
    void testSetBaseEventWithZeroBytes() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        byte[] zeroBytes = new byte[16];
        // when
        header.setBaseEvent(zeroBytes);
        // then
        assertArrayEquals(zeroBytes, header.getBaseEvent());
    }

    @Test
    void testSetBaseEventWithAllOnesBytes() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        // Test with all ones bytes
        byte[] onesBytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            onesBytes[i] = (byte) 0xFF;
        }
        // when
        header.setBaseEvent(onesBytes);
        // then
        assertArrayEquals(onesBytes, header.getBaseEvent());
    }

    @Test
    void testSetBaseEventMultipleTimes() {
        // given
        BlockHeaderV2 header = createHeaderV2();

        // when
        // Set baseEvent multiple times
        byte[] firstBaseEvent = new byte[]{1, 2, 3};
        header.setBaseEvent(firstBaseEvent);
        // then
        assertArrayEquals(firstBaseEvent, header.getBaseEvent());

        // when
        byte[] secondBaseEvent = new byte[]{4, 5, 6, 7};
        header.setBaseEvent(secondBaseEvent);
        // then
        assertArrayEquals(secondBaseEvent, header.getBaseEvent());

        // when
        byte[] thirdBaseEvent = new byte[]{8, 9};
        header.setBaseEvent(thirdBaseEvent);
        // then
        assertArrayEquals(thirdBaseEvent, header.getBaseEvent());
    }

    @Test
    void testSetBaseEventAfterSealing() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        header.seal();

        // when / then
        assertThrows(SealedBlockHeaderException.class, () -> {
            header.setBaseEvent(new byte[]{1, 2, 3});
        });
    }

    @Test
    void testGetBaseEventReturnsCorrectValue() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        byte[] expectedBaseEvent = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};

        // when
        header.getExtension().setBaseEvent(expectedBaseEvent);
        byte[] result = header.getBaseEvent();

        // then
        assertArrayEquals(expectedBaseEvent, result);
    }

    @Test
    void testBaseEventPersistenceAfterExtensionUpdate() {
        // given
        BlockHeaderV2 header = createHeaderV2();
        byte[] originalBaseEvent = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC};
        header.setBaseEvent(originalBaseEvent);

        // and a new extension with different baseEvent
        BlockHeaderExtensionV2 newExtension = new BlockHeaderExtensionV2(
                new byte[256], // logsBloom
                new short[]{1, 2, 3}, // txExecutionSublistsEdges
                new byte[]{(byte) 0xDD, (byte) 0xEE, (byte) 0xFF} // different baseEvent
        );

        // when
        header.setExtension(newExtension);
        byte[] result = header.getBaseEvent();

        // then
        assertArrayEquals(new byte[]{(byte) 0xDD, (byte) 0xEE, (byte) 0xFF}, result);
    }

}
