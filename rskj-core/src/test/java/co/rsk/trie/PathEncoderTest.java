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

import org.junit.Test;
import org.junit.Assert;

/**
 * Created by ajlopez on 07/02/2017.
 */
public class PathEncoderTest {
    @Test
    public void encodeNullBinaryPath() {
        try {
            PathEncoder.encode(null, 2);
            Assert.fail();
        }
        catch (Exception ex) {
            Assert.assertTrue(ex instanceof IllegalArgumentException);
        }
    }

    @Test
    public void encodeNullHexadecimalPath() {
        try {
            PathEncoder.encode(null, 16);
            Assert.fail();
        }
        catch (Exception ex) {
            Assert.assertTrue(ex instanceof IllegalArgumentException);
        }
    }

    @Test
    public void encodeBinaryPath() {
        byte[] path = new byte[] { 0x00, 0x01, 0x01 };

        byte[] encoded = PathEncoder.encode(path, 2);

        Assert.assertNotNull(encoded);
        Assert.assertArrayEquals(new byte[] { 0x60 }, encoded);
    }

    @Test
    public void encodeBinaryPathOneByte() {
        byte[] path = new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01 };

        byte[] encoded = PathEncoder.encode(path, 2);

        Assert.assertNotNull(encoded);
        Assert.assertArrayEquals(new byte[] { 0x6d }, encoded);
    }

    @Test
    public void encodeBinaryPathNineBits() {
        byte[] path = new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01, 0x01 };

        byte[] encoded = PathEncoder.encode(path, 2);

        Assert.assertNotNull(encoded);
        Assert.assertArrayEquals(new byte[] { 0x6d, (byte)0x80 }, encoded);
    }

    @Test
    public void encodeBinaryPathOneAndHalfByte() {
        byte[] path = new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x01 };

        byte[] encoded = PathEncoder.encode(path, 2);

        Assert.assertNotNull(encoded);
        Assert.assertArrayEquals(new byte[] { 0x6d, 0x50 }, encoded);
    }

    @Test
    public void encodeHexadecimalPath() {
        byte[] path = new byte[] { 0x06 };

        byte[] encoded = PathEncoder.encode(path, 16);

        Assert.assertNotNull(encoded);
        Assert.assertArrayEquals(new byte[] { 0x60 }, encoded);
    }

    @Test
    public void encodeHexadecimalPathOneByte() {
        byte[] path = new byte[] { 0x06, 0x0d };

        byte[] encoded = PathEncoder.encode(path, 16);

        Assert.assertNotNull(encoded);
        Assert.assertArrayEquals(new byte[] { 0x6d }, encoded);
    }

    @Test
    public void encodeHexadecimalPathThreeNibbles() {
        byte[] path = new byte[] { 0x06, 0x0d, 0x08 };

        byte[] encoded = PathEncoder.encode(path, 16);

        Assert.assertNotNull(encoded);
        Assert.assertArrayEquals(new byte[] { 0x6d, (byte)0x80 }, encoded);
    }

    @Test
    public void encodeHexadecimalPathFiveNibbles() {
        byte[] path = new byte[] { 0x06, 0x0d, 0x08, 0x02, 0x05 };

        byte[] encoded = PathEncoder.encode(path, 16);

        Assert.assertNotNull(encoded);
        Assert.assertArrayEquals(new byte[] { 0x6d, (byte)0x82, 0x50 }, encoded);
    }

    @Test
    public void decodeNullBinaryPath() {
        try {
            PathEncoder.decode(null, 2, 0);
            Assert.fail();
        }
        catch (Exception ex) {
            Assert.assertTrue(ex instanceof IllegalArgumentException);
        }
    }

    @Test
    public void decodeNullHexadecimalPath() {
        try {
            PathEncoder.decode(null, 16, 0);
            Assert.fail();
        }
        catch (Exception ex) {
            Assert.assertTrue(ex instanceof IllegalArgumentException);   
        }
    }

    @Test
    public void decodeBinaryPath() {
        byte[] encoded = new byte[] { 0x60 };

        byte[] path = PathEncoder.decode(encoded, 2, 3);

        Assert.assertNotNull(path);
        Assert.assertArrayEquals(new byte[] { 0x00, 0x01, 0x01 }, path);
    }

    @Test
    public void decodeBinaryPathOneByte() {
        byte[] encoded = new byte[] { 0x6d };

        byte[] path = PathEncoder.decode(encoded, 2, 8);

        Assert.assertNotNull(path);
        Assert.assertArrayEquals(new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01 }, path);
    }

    @Test
    public void decodeBinaryPathNineBits() {
        byte[] encoded = new byte[] { 0x6d, (byte)0x80 };

        byte[] path = PathEncoder.decode(encoded, 2, 9);

        Assert.assertNotNull(path);
        Assert.assertArrayEquals(new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01, 0x01 }, path);
    }

    @Test
    public void decodeBinaryPathOneAndHalfByte() {
        byte[] encoded = new byte[] { 0x6d, 0x50 };

        byte[] path = PathEncoder.decode(encoded, 2, 12);

        Assert.assertNotNull(path);
        Assert.assertArrayEquals(new byte[] { 0x00, 0x01, 0x01, 0x00, 0x01, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x01 }, path);
    }

    @Test
    public void decodeHexadecimalPath() {
        byte[] encoded = new byte[] { 0x60 };

        byte[] path = PathEncoder.decode(encoded, 16, 1);

        Assert.assertNotNull(path);
        Assert.assertArrayEquals(new byte[] { 0x06 }, path);
    }

    @Test
    public void decodeHexadecimalPathOneByte() {
        byte[] encoded = new byte[] { 0x6d };

        byte[] path = PathEncoder.decode(encoded, 16, 2);

        Assert.assertNotNull(path);
        Assert.assertArrayEquals(new byte[] { 0x06, 0x0d }, path);
    }

    @Test
    public void decodeHexadecimalPathThreeNibbles() {
        byte[] encoded = new byte[] { 0x6d, (byte)0x80 };

        byte[] path = PathEncoder.decode(encoded, 16, 3);

        Assert.assertNotNull(path);
        Assert.assertArrayEquals(new byte[] { 0x06, 0x0d, 0x08 }, path);
    }

    @Test
    public void decodeHexadecimalPathFiveNibbles() {
        byte[] encoded = new byte[] { 0x6d, (byte)0x82, 0x50 };

        byte[] path = PathEncoder.decode(encoded, 16, 5);

        Assert.assertNotNull(path);
        Assert.assertArrayEquals(new byte[] { 0x06, 0x0d, 0x08, 0x02, 0x05 }, path);
    }
}
