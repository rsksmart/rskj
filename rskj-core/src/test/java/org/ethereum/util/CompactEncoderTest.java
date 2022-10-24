/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CompactEncoderTest {

    private final static byte T = 16; // terminator

    @Test
    void testCompactEncodeOddCompact() {
        byte[] test = new byte[]{1, 2, 3, 4, 5};
        byte[] expectedData = new byte[]{0x11, 0x23, 0x45};
        assertArrayEquals(expectedData, CompactEncoder.packNibbles(test), "odd compact encode fail");
    }

    @Test
    void testCompactEncodeEvenCompact() {
        byte[] test = new byte[]{0, 1, 2, 3, 4, 5};
        byte[] expectedData = new byte[]{0x00, 0x01, 0x23, 0x45};
        assertArrayEquals(expectedData, CompactEncoder.packNibbles(test), "even compact encode fail");
    }

    @Test
    void testCompactEncodeEvenTerminated() {
        byte[] test = new byte[]{0, 15, 1, 12, 11, 8, T};
        byte[] expectedData = new byte[]{0x20, 0x0f, 0x1c, (byte) 0xb8};
        assertArrayEquals(expectedData, CompactEncoder.packNibbles(test), "even terminated compact encode fail");
    }

    @Test
    void testCompactEncodeOddTerminated() {
        byte[] test = new byte[]{15, 1, 12, 11, 8, T};
        byte[] expectedData = new byte[]{0x3f, 0x1c, (byte) 0xb8};
        assertArrayEquals(expectedData, CompactEncoder.packNibbles(test), "odd terminated compact encode fail");
    }

    @Test
    void testCompactDecodeOddCompact() {
        byte[] test = new byte[]{0x11, 0x23, 0x45};
        byte[] expected = new byte[]{1, 2, 3, 4, 5};
        assertArrayEquals(expected, CompactEncoder.unpackToNibbles(test), "odd compact decode fail");
    }

    @Test
    void testCompactDecodeEvenCompact() {
        byte[] test = new byte[]{0x00, 0x01, 0x23, 0x45};
        byte[] expected = new byte[]{0, 1, 2, 3, 4, 5};
        assertArrayEquals(expected, CompactEncoder.unpackToNibbles(test), "even compact decode fail");
    }

    @Test
    void testCompactDecodeEvenTerminated() {
        byte[] test = new byte[]{0x20, 0x0f, 0x1c, (byte) 0xb8};
        byte[] expected = new byte[]{0, 15, 1, 12, 11, 8, T};
        assertArrayEquals(expected, CompactEncoder.unpackToNibbles(test), "even terminated compact decode fail");
    }

    @Test
    void testCompactDecodeOddTerminated() {
        byte[] test = new byte[]{0x3f, 0x1c, (byte) 0xb8};
        byte[] expected = new byte[]{15, 1, 12, 11, 8, T};
        assertArrayEquals(expected, CompactEncoder.unpackToNibbles(test), "odd terminated compact decode fail");
    }

    @Test
    void testCompactHexEncode_1() {
        byte[] test = "stallion".getBytes();
        byte[] result = new byte[]{7, 3, 7, 4, 6, 1, 6, 12, 6, 12, 6, 9, 6, 15, 6, 14, T};
        assertArrayEquals(result, CompactEncoder.binToNibbles(test));
    }

    @Test
    void testCompactHexEncode_2() {
        byte[] test = "verb".getBytes();
        byte[] result = new byte[]{7, 6, 6, 5, 7, 2, 6, 2, T};
        assertArrayEquals(result, CompactEncoder.binToNibbles(test));
    }

    @Test
    void testCompactHexEncode_3() {
        byte[] test = "puppy".getBytes();
        byte[] result = new byte[]{7, 0, 7, 5, 7, 0, 7, 0, 7, 9, T};
        assertArrayEquals(result, CompactEncoder.binToNibbles(test));
    }
}
