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

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.exception.FieldMaxSizeBlockHeaderException;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.ethereum.core.BlockHeaderV2.BASE_EVENT_MAX_SIZE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockHeaderExtensionV2Test {

    private static final short[] EDGES = new short[] { 1, 2, 3, 4 };

    // Golden fixtures: inputs and expected bytes were captured from the current
    // implementation before any refactor; the refactor must reproduce the encodings
    // and hashes byte for byte.
    private static final String GOLDEN_V2_BLOOM =
            "57afbc42df5c50a2b79c978bf534cf093f95ddba9a3bd04c11fa4ae8658500458f01ee2ba7a657487425abe67b4d2cd4e49ef79d0c1e985706b1aa6097c8d30516579bb4f52f842a217955fdb0886ceabda21729ebeb1fc47d69021eacbedffef6cce54245d508918ef0f9b4f1334535672f01f8de0866f12ac5859c3bc95b9d7faa97ad088ac8a66ee2846db08e0aa402de12c8bfea8e02e2dde8f031c952a2c4e55dba21c3de0adbfb2fb6f278cc21b49e43781dac79f620a6415c4c354c8c1cb8fdc26ab1da31d02849d8d171eab62615cde9bb6e64a582d984c9a08091fba142f83e304c37acae0cbe5ffbe5b810050515a40f8eca484a7d80d616d1490a";
    private static final String GOLDEN_V2_BASE_EVENT =
            "b6925d33ad9d6297a4907256a7e5f2683102537aa2909bb6c17b4f1adc51b38d";

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
    void withBaseEventCopiesArray() {
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(null, null, null);
        byte[] hash = new byte[]{0x0A, 0x0B};
        ext = ext.withBaseEvent(hash);

        assertArrayEquals(hash, ext.getBaseEvent());
        // Mutate original array to check for defensive copy
        hash[0] = 0x00;
        assertNotEquals(hash[0], ext.getBaseEvent()[0]);
    }

    @Test
    void getBaseEventReturnsInternalArray() {
        // Immutable value objects copy on construction and expose their internals
        // directly: callers get a read-only view and must not mutate it.
        byte[] hash = new byte[]{0x0A, 0x0B};
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(null, null, hash);
        byte[] returned = ext.getBaseEvent();
        assertArrayEquals(hash, returned);
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

        // Always-present fields (logsBloom, baseEvent) decode to byte[0] when empty; the
        // optional edges slot remains null when absent from the encoded list.
        assertArrayEquals(new byte[0], decoded.getLogsBloom());
        assertNull(decoded.getTxExecutionSublistsEdges());
        assertArrayEquals(new byte[0], decoded.getBaseEvent());
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
        assertArrayEquals(new byte[0], decoded.getBaseEvent());
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
        assertArrayEquals(new byte[0], decoded.getBaseEvent());
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
        // An empty baseEvent is encoded as the RLP byte 0x80 and fromEncoded() reads it
        // back as byte[0] (via getRLPRawData()) -- it must not be lost as null.
        assertArrayEquals(new byte[0], decoded.getBaseEvent());
    }

    @Test
    void decodeMalformedRLPThrows() {
        byte[] malformed = new byte[]{0x01, 0x02, 0x03};
        assertThrows(Exception.class, () -> BlockHeaderExtensionV2.fromEncoded(malformed));
    }

    @Test
    void testWithBaseEventNull() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);
        extension = extension.withBaseEvent(null);
        assertNull(extension.getBaseEvent());
    }

    @Test
    void testWithBaseEventEmptyArray() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);
        byte[] emptyArray = new byte[0];
        extension = extension.withBaseEvent(emptyArray);
        assertArrayEquals(emptyArray, extension.getBaseEvent());
    }

    @Test
    void testWithBaseEventLargeValue() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);
        byte[] largeValue = new byte[BASE_EVENT_MAX_SIZE];
        for (int i = 0; i < BASE_EVENT_MAX_SIZE; i++) {
            largeValue[i] = (byte) (i % 256);
        }
        extension = extension.withBaseEvent(largeValue);
        assertArrayEquals(largeValue, extension.getBaseEvent());
    }

    @Test
    void testWithBaseEventExceedingMaxSizeThrowsException() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);
        byte[] oversizedValue = new byte[BASE_EVENT_MAX_SIZE + 1];
        assertThrows(FieldMaxSizeBlockHeaderException.class, () -> extension.withBaseEvent(oversizedValue));
    }

    @Test
    void testWithBaseEventSpecialBytes() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);
        byte[] specialBytes = new byte[]{0x00, (byte) 0xFF, (byte) 0x80, (byte) 0x7F};
        extension = extension.withBaseEvent(specialBytes);
        assertArrayEquals(specialBytes, extension.getBaseEvent());
    }

    @Test
    void testWithBaseEventMultipleTimes() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], new byte[0]);

        byte[] firstValue = new byte[]{1, 2, 3};
        extension = extension.withBaseEvent(firstValue);
        assertArrayEquals(firstValue, extension.getBaseEvent());

        byte[] secondValue = new byte[]{4, 5, 6, 7, 8};
        extension = extension.withBaseEvent(secondValue);
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
        int numberOfBytes = 128;
        byte[] maxSizeValue = new byte[numberOfBytes];
        for (int i = 0; i < numberOfBytes; i++) {
            maxSizeValue[i] = (byte) (i % 256);
        }
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], new short[0], maxSizeValue);
        assertArrayEquals(maxSizeValue, extension.getBaseEvent());
    }

    @Test
    void constructorDoesNotEnforceBaseEventMaxSize() {
        // Pinned quirk: only setBaseEvent() enforces BASE_EVENT_MAX_SIZE, the constructor
        // does not, so oversized data can still reach the value object through it.
        byte[] oversizedValue = new byte[BASE_EVENT_MAX_SIZE + 1];
        for (int i = 0; i < oversizedValue.length; i++) {
            oversizedValue[i] = (byte) (i % 256);
        }

        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(new byte[256], EDGES, oversizedValue);

        assertArrayEquals(oversizedValue, extension.getBaseEvent());
    }

    @Test
    void decodeWithOneElementListThrowsIndexOutOfBounds() {
        // Pinned quirk: fromEncoded() reads the baseEvent slot (index 1) unconditionally,
        // so an inner list without it fails with IndexOutOfBoundsException.
        byte[] logsBloom = new byte[256];
        byte[] oneElementList = RLP.encodeList(RLP.encodeElement(logsBloom));

        assertThrows(IndexOutOfBoundsException.class, () -> BlockHeaderExtensionV2.fromEncoded(oneElementList));
    }

    @Test
    void hashIncludesLogsBloom() {
        byte[] logsBloom1 = new byte[256];
        Arrays.fill(logsBloom1, (byte) 0x01);
        byte[] logsBloom2 = new byte[256];
        Arrays.fill(logsBloom2, (byte) 0x02);

        BlockHeaderExtensionV2 extension1 = new BlockHeaderExtensionV2(logsBloom1, EDGES, new byte[] { 0x01 });
        BlockHeaderExtensionV2 extension2 = new BlockHeaderExtensionV2(logsBloom2, EDGES, new byte[] { 0x01 });

        assertNotEquals(
                Hex.toHexString(extension1.getHash()),
                Hex.toHexString(extension2.getHash()));
    }

    @Test
    void hashIncludesBaseEvent() {
        byte[] logsBloom = new byte[256];
        Arrays.fill(logsBloom, (byte) 0x01);

        BlockHeaderExtensionV2 extension1 = new BlockHeaderExtensionV2(logsBloom, EDGES, new byte[] { 0x01 });
        BlockHeaderExtensionV2 extension2 = new BlockHeaderExtensionV2(logsBloom, EDGES, new byte[] { 0x02 });

        assertNotEquals(
                Hex.toHexString(extension1.getHash()),
                Hex.toHexString(extension2.getHash()));
    }

    @Test
    void hashIncludesEdges() {
        byte[] logsBloom = new byte[256];
        Arrays.fill(logsBloom, (byte) 0x01);

        BlockHeaderExtensionV2 extension1 = new BlockHeaderExtensionV2(logsBloom, new short[] { 1, 2 }, new byte[] { 0x01 });
        BlockHeaderExtensionV2 extension2 = new BlockHeaderExtensionV2(logsBloom, new short[] { 3, 4 }, new byte[] { 0x01 });

        assertNotEquals(
                Hex.toHexString(extension1.getHash()),
                Hex.toHexString(extension2.getHash()));
    }

    @Test
    void goldenEncodingAndHashWithEdgesAndBaseEvent() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(
                Hex.decode(GOLDEN_V2_BLOOM), EDGES, Hex.decode(GOLDEN_V2_BASE_EVENT));

        assertArrayEquals(Hex.decode("f9012d" + "b90100" + GOLDEN_V2_BLOOM + "a0" + GOLDEN_V2_BASE_EVENT + "880100020003000400"),
                extension.getEncoded());
        assertArrayEquals(Hex.decode("83ad17bbc2bbe2dd8cff717f99c97445da62a0d1fd575192707b09f6e9858a14"),
                extension.getHash());
    }

    @Test
    void goldenEncodingAndHashWithNullBaseEvent() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(Hex.decode(GOLDEN_V2_BLOOM), EDGES, null);

        assertArrayEquals(Hex.decode("f9010d" + "b90100" + GOLDEN_V2_BLOOM + "80" + "880100020003000400"),
                extension.getEncoded());
        assertArrayEquals(Hex.decode("1814eb010dccd15a79c6e9977cd8c3d5891374312ddc081be1cb60fb6cec7d69"),
                extension.getHash());
    }

    @Test
    void goldenEncodingAndHashWithNullEdges() {
        BlockHeaderExtensionV2 extension = new BlockHeaderExtensionV2(
                Hex.decode(GOLDEN_V2_BLOOM), null, Hex.decode(GOLDEN_V2_BASE_EVENT));

        assertArrayEquals(Hex.decode("f90124" + "b90100" + GOLDEN_V2_BLOOM + "a0" + GOLDEN_V2_BASE_EVENT),
                extension.getEncoded());
        assertArrayEquals(Hex.decode("3fb85c5ee8c1bafa5de427897884c734753dad8209e2198959599efee2b22cb2"),
                extension.getHash());
    }
}
