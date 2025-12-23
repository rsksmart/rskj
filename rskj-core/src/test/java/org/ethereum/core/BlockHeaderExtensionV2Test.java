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

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockHeaderExtensionV2Test {

    @Test
    void constructorAndGetters() {
        byte[] logsBloom = new byte[256];
        Arrays.fill(logsBloom, (byte) 0xAB);
        short[] edges = new short[]{1, 2, 3};
        byte[] baseEvent = new byte[]{0x01, 0x02};

        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, edges, baseEvent);

        assertArrayEquals(logsBloom, ext.getLogsBloom());
        assertArrayEquals(edges, ext.getTxExecutionSublistsEdges());
        assertArrayEquals(baseEvent, ext.getBaseEvent());
        assertEquals(0x2, ext.getVersion());
    }

    @Test
    void setBaseEventCopiesArray() {
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(null, null, null);
        byte[] hash = new byte[]{0x0A, 0x0B};
        ext.setBaseEvent(hash);

        assertArrayEquals(hash, ext.getBaseEvent());
        // Mutate original array to check for defensive copy
        hash[0] = 0x00;
        assertNotEquals(hash[0], ext.getBaseEvent()[0]);
    }

    @Test
    void getBaseEventReturnsCopy() {
        byte[] hash = new byte[]{0x0A, 0x0B};
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(null, null, hash);
        byte[] returned = ext.getBaseEvent();
        returned[0] = 0x00;
        assertNotEquals(returned[0], ext.getBaseEvent()[0]);
    }

    @Test
    void encodingAndDecoding() {
        byte[] logsBloom = new byte[256];
        Arrays.fill(logsBloom, (byte) 0x01);
        short[] edges = new short[]{10, 20};
        byte[] superChainDataHash = new byte[]{0x55, 0x66};

        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, edges, superChainDataHash);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertArrayEquals(edges, decoded.getTxExecutionSublistsEdges());
        assertArrayEquals(superChainDataHash, decoded.getBaseEvent());
    }

    @Test
    void encodingWithNullFields() {
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(null, null, null);
        byte[] encoded = ext.getEncoded();
        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertNull(decoded.getLogsBloom());
        assertNull(decoded.getTxExecutionSublistsEdges());
        assertNull(decoded.getBaseEvent());
    }

    @Test
    void decodeNullInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> BlockHeaderExtensionV2.fromEncoded(null));
    }

    @Test
    void decodeOnlyLogsBloom() {
        byte[] logsBloom = new byte[256];
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, null, null);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertNull(decoded.getTxExecutionSublistsEdges());
        assertNull(decoded.getBaseEvent());
    }

    @Test
    void decodeLogsBloomAndEdges() {
        byte[] logsBloom = new byte[256];
        short[] edges = new short[]{1, 2};
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, edges, null);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertArrayEquals(edges, decoded.getTxExecutionSublistsEdges());
        assertNull(decoded.getBaseEvent());
    }

    @Test
    void decodeWithEmptyEdges() {
        byte[] logsBloom = new byte[256];
        byte[] baseEvent = new byte[128];
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, null, baseEvent);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertArrayEquals(baseEvent, decoded.getBaseEvent());
        assertNull(decoded.getTxExecutionSublistsEdges());
    }

    @Test
    void decodeWithEmptyBaseEvent() {
        byte[] logsBloom = new byte[256];
        short[] edges = new short[]{1};
        byte[] baseEvent = new byte[0];
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, edges, baseEvent);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertArrayEquals(edges, decoded.getTxExecutionSublistsEdges());
        assertNull(decoded.getBaseEvent());
    }

    @Test
    void decodeMalformedRLPThrows() {
        byte[] malformed = new byte[]{0x01, 0x02, 0x03};
        assertThrows(Exception.class, () -> BlockHeaderExtensionV2.fromEncoded(malformed));
    }

    @Test
    void testSetBaseEventWithNull() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);
        extension.setBaseEvent(null);
        assertNull(extension.getBaseEvent());
    }

    @Test
    void testSetBaseEventWithEmptyArray() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);
        byte[] emptyArray = new byte[0];
        extension.setBaseEvent(emptyArray);
        assertArrayEquals(emptyArray, extension.getBaseEvent());
    }

    @Test
    void testSetBaseEventWithLargeValue() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);
        byte[] largeValue = new byte[1024];
        for (int i = 0; i < 1024; i++) {
            largeValue[i] = (byte) (i % 256);
        }
        extension.setBaseEvent(largeValue);
        assertArrayEquals(largeValue, extension.getBaseEvent());
    }

    @Test
    void testSetBaseEventWithSpecialBytes() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);
        byte[] specialBytes = new byte[]{0x00, (byte) 0xFF, (byte) 0x80, (byte) 0x7F};
        extension.setBaseEvent(specialBytes);
        assertArrayEquals(specialBytes, extension.getBaseEvent());
    }

    @Test
    void testSetBaseEventMultipleTimes() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);

        byte[] firstValue = new byte[]{1, 2, 3};
        extension.setBaseEvent(firstValue);
        assertArrayEquals(firstValue, extension.getBaseEvent());

        byte[] secondValue = new byte[]{4, 5, 6, 7, 8};
        extension.setBaseEvent(secondValue);
        assertArrayEquals(secondValue, extension.getBaseEvent());
    }

    @Test
    void testGetBaseEventReturnsCorrectValue() {
        byte[] expectedBaseEvent = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], expectedBaseEvent);
        assertArrayEquals(expectedBaseEvent, extension.getBaseEvent());
    }

    @Test
    void testBaseEventPersistenceAfterEncoding() {
        byte[] originalBaseEvent = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55};
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], originalBaseEvent);

        // Encode and decode
        byte[] encoded = extension.getEncoded();
        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        // Verify baseEvent is preserved
        assertArrayEquals(originalBaseEvent, decoded.getBaseEvent());
    }

    @Test
    void testBaseEventWithZeroBytes() {
        byte[] zeroBytes = new byte[32];
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], zeroBytes);
        assertArrayEquals(zeroBytes, extension.getBaseEvent());
    }

    @Test
    void testBaseEventWithAllOnesBytes() {
        byte[] onesBytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            onesBytes[i] = (byte) 0xFF;
        }
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], onesBytes);
        assertArrayEquals(onesBytes, extension.getBaseEvent());
    }

    @Test
    void testBaseEventWithMaxSize() {
        // Test with maximum practical size (64KB)
        byte[] maxSizeValue = new byte[65536];
        for (int i = 0; i < 65536; i++) {
            maxSizeValue[i] = (byte) (i % 256);
        }
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], maxSizeValue);
        assertArrayEquals(maxSizeValue, extension.getBaseEvent());
    }
}
