/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.trie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * Created by ajlopez on 07/02/2017.
 */
class PathEncoderTest {
    @Test
    void encodeNullBinaryPath() {
        try {
            PathEncoder.encode(null);
            Assertions.fail();
        }
        catch (Exception ex) {
            Assertions.assertTrue(ex instanceof IllegalArgumentException);
        }
    }

    @Test
    void encodeBinaryPath() {
        byte[] path = new byte[] { 0x00, 0x01, 0x01 };

        byte[] encoded = PathEncoder.encode(path);

        Assertions.assertNotNull(encoded);
        Assertions.assertArrayEquals(new byte[] { 0x60 }, encoded);
    }

    @Test
    void encodeBinaryPathOneByte() {
        byte[] path = new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01 };

        byte[] encoded = PathEncoder.encode(path);

        Assertions.assertNotNull(encoded);
        Assertions.assertArrayEquals(new byte[] { 0x6d }, encoded);
    }

    @Test
    void encodeBinaryPathNineBits() {
        byte[] path = new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01, 0x01 };

        byte[] encoded = PathEncoder.encode(path);

        Assertions.assertNotNull(encoded);
        Assertions.assertArrayEquals(new byte[] { 0x6d, (byte)0x80 }, encoded);
    }

    @Test
    void encodeBinaryPathOneAndHalfByte() {
        byte[] path = new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x01 };

        byte[] encoded = PathEncoder.encode(path);

        Assertions.assertNotNull(encoded);
        Assertions.assertArrayEquals(new byte[] { 0x6d, 0x50 }, encoded);
    }

    @Test
    void decodeNullBinaryPath() {
        try {
            PathEncoder.decode(null, 0);
            Assertions.fail();
        }
        catch (Exception ex) {
            Assertions.assertTrue(ex instanceof IllegalArgumentException);
        }
    }

    @Test
    void decodeBinaryPath() {
        byte[] encoded = new byte[] { 0x60 };

        byte[] path = PathEncoder.decode(encoded, 3);

        Assertions.assertNotNull(path);
        Assertions.assertArrayEquals(new byte[] { 0x00, 0x01, 0x01 }, path);
    }

    @Test
    void decodeBinaryPathOneByte() {
        byte[] encoded = new byte[] { 0x6d };

        byte[] path = PathEncoder.decode(encoded, 8);

        Assertions.assertNotNull(path);
        Assertions.assertArrayEquals(new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01 }, path);
    }

    @Test
    void decodeBinaryPathNineBits() {
        byte[] encoded = new byte[] { 0x6d, (byte)0x80 };

        byte[] path = PathEncoder.decode(encoded, 9);

        Assertions.assertNotNull(path);
        Assertions.assertArrayEquals(new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01, 0x01 }, path);
    }

    @Test
    void decodeBinaryPathOneAndHalfByte() {
        byte[] encoded = new byte[] { 0x6d, 0x50 };

        byte[] path = PathEncoder.decode(encoded, 12);

        Assertions.assertNotNull(path);
        Assertions.assertArrayEquals(new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x01 }, path);
    }
}
