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

package org.ethereum.vm;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class DataWordTest {

    @Test
    public void testAdd() {
        byte[] three = new byte[32];

        for (int i = 0; i < three.length; i++) {
            three[i] = (byte) 0xff;
        }

        DataWord x = DataWord.valueOf(three);
        byte[] xdata = x.getData();

        DataWord result = x.add(DataWord.valueOf(three));

        assertArrayEquals(xdata, x.getData());
        assertEquals(32, result.getData().length);
    }

    @Test
    public void testMod() {
        String expected = "000000000000000000000000000000000000000000000000000000000000001a";

        byte[] one = new byte[32];
        one[31] = 0x1e; // 0x000000000000000000000000000000000000000000000000000000000000001e

        byte[] two = new byte[32];
        for (int i = 0; i < two.length; i++) {
            two[i] = (byte) 0xff;
        }
        two[31] = 0x56; // 0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff56

        DataWord x = DataWord.valueOf(one);
        byte[] xdata = x.getData();
        DataWord y = DataWord.valueOf(two);
        byte[] ydata = y.getData();

        DataWord result = y.mod(x);

        assertArrayEquals(xdata, x.getData());
        assertArrayEquals(ydata, y.getData());
        assertEquals(32, result.getData().length);
        assertEquals(expected, ByteUtil.toHexString(result.getData()));
    }

    @Test
    public void testMul() {
        byte[] one = new byte[32];
        one[31] = 0x1; // 0x0000000000000000000000000000000000000000000000000000000000000001

        byte[] two = new byte[32];
        two[11] = 0x1; // 0x0000000000000000000000010000000000000000000000000000000000000000

        DataWord x = DataWord.valueOf(one);
        DataWord y = DataWord.valueOf(two);
        byte[] xdata = x.getData();
        byte[] ydata = y.getData();

        DataWord result = x.mul(y);

        assertArrayEquals(xdata, x.getData());
        assertArrayEquals(ydata, y.getData());
        assertEquals(32, y.getData().length);
        assertEquals("0000000000000000000000010000000000000000000000000000000000000000", ByteUtil.toHexString(y.getData()));
        assertEquals(32, result.getData().length);
        assertEquals("0000000000000000000000010000000000000000000000000000000000000000", ByteUtil.toHexString(result.getData()));
    }

    @Test
    public void testMulOverflow() {
        byte[] one = new byte[32];
        one[30] = 0x1; // 0x0000000000000000000000000000000000000000000000000000000000000100

        byte[] two = new byte[32];
        two[0] = 0x1; //  0x1000000000000000000000000000000000000000000000000000000000000000

        DataWord x = DataWord.valueOf(one);
        DataWord y = DataWord.valueOf(two);
        byte[] xdata = x.getData();
        byte[] ydata = y.getData();

        DataWord result = x.mul(y);

        assertArrayEquals(xdata, x.getData());
        assertArrayEquals(ydata, y.getData());
        assertEquals(32, y.getData().length);
        assertEquals("0100000000000000000000000000000000000000000000000000000000000000", ByteUtil.toHexString(y.getData()));
        assertEquals(32, result.getData().length);
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", ByteUtil.toHexString(result.getData()));
    }

    @Test
    public void testDiv() {
        byte[] one = new byte[32];
        one[30] = 0x01;
        one[31] = 0x2c; // 0x000000000000000000000000000000000000000000000000000000000000012c

        byte[] two = new byte[32];
        two[31] = 0x0f; // 0x000000000000000000000000000000000000000000000000000000000000000f

        DataWord x = DataWord.valueOf(one);
        DataWord y = DataWord.valueOf(two);
        byte[] xdata = x.getData();
        byte[] ydata = y.getData();

        DataWord result =x.div(y);

        assertArrayEquals(xdata, x.getData());
        assertArrayEquals(ydata, y.getData());
        assertEquals(32, result.getData().length);
        assertEquals("0000000000000000000000000000000000000000000000000000000000000014", ByteUtil.toHexString(result.getData()));
    }

    @Test
    public void testDivZero() {
        byte[] one = new byte[32];
        one[30] = 0x05; // 0x0000000000000000000000000000000000000000000000000000000000000500

        byte[] two = new byte[32];

        DataWord x = DataWord.valueOf(one);
        DataWord y = DataWord.valueOf(two);
        byte[] xdata = x.getData();
        byte[] ydata = y.getData();

        DataWord result = x.div(y);

        assertArrayEquals(xdata, x.getData());
        assertArrayEquals(ydata, y.getData());
        assertEquals(32, result.getData().length);
        assertTrue(result.isZero());
    }

    @Test
    public void testSDivNegative() {
        // one is -300 as 256-bit signed integer:
        byte[] one = Hex.decode("fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed4");

        byte[] two = new byte[32];
        two[31] = 0x0f;

        DataWord x = DataWord.valueOf(one);
        DataWord y = DataWord.valueOf(two);
        byte[] xdata = x.getData();
        byte[] ydata = y.getData();

        DataWord result = x.sDiv(y);

        assertArrayEquals(xdata, x.getData());
        assertArrayEquals(ydata, y.getData());
        assertEquals(32, result.getData().length);
        assertEquals("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec", result.toString());
    }

    @Test
    public void testPow() {
        BigInteger x = BigInteger.valueOf(Integer.MAX_VALUE);
        BigInteger y = BigInteger.valueOf(1000);

        BigInteger result1 = x.modPow(x, y);
        BigInteger result2 = pow(x, y);
    }

    @Test
    public void testSignExtend1() {
        DataWord x = DataWord.valueOf(Hex.decode("f2"));
        byte[] xdata = x.getData();

        byte k = 0;
        String expected = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff2";

        DataWord result = x.signExtend(k);

        assertArrayEquals(xdata, x.getData());
        assertEquals(expected, result.toString());
    }

    @Test
    public void testSignExtend2() {
        DataWord x = DataWord.valueOf(Hex.decode("f2"));
        byte[] xdata = x.getData();

        byte k = 1;
        String expected = "00000000000000000000000000000000000000000000000000000000000000f2";

        DataWord result = x.signExtend(k);

        assertArrayEquals(xdata, x.getData());
        assertEquals(expected, result.toString());
    }

    @Test
    public void testSignExtend3() {
        byte k = 1;

        DataWord x = DataWord.valueOf(Hex.decode("0f00ab"));
        byte[] xdata = x.getData();

        String expected = "00000000000000000000000000000000000000000000000000000000000000ab";

        DataWord result = x.signExtend(k);

        assertArrayEquals(xdata, x.getData());
        assertEquals(expected, result.toString());
    }

    @Test
    public void testSignExtend4() {
        byte k = 1;

        DataWord x = DataWord.valueOf(Hex.decode("ffff"));
        byte[] xdata = x.getData();

        String expected = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

        DataWord result = x.signExtend(k);

        assertArrayEquals(xdata, x.getData());
        assertEquals(expected, result.toString());
    }

    @Test
    public void testSignExtend5() {
        byte k = 3;

        DataWord x = DataWord.valueOf(Hex.decode("ffffffff"));
        byte[] xdata = x.getData();

        String expected = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

        DataWord result = x.signExtend(k);

        assertArrayEquals(xdata, x.getData());
        assertEquals(expected, result.toString());
    }

    @Test
    public void testSignExtend6() {
        byte k = 3;

        DataWord x = DataWord.valueOf(Hex.decode("ab02345678"));
        byte[] xdata = x.getData();

        String expected = "0000000000000000000000000000000000000000000000000000000002345678";

        DataWord result = x.signExtend(k);

        assertArrayEquals(xdata, x.getData());
        assertEquals(expected, result.toString());
    }

    @Test
    public void testSignExtend7() {
        byte k = 3;

        DataWord x = DataWord.valueOf(Hex.decode("ab82345678"));
        byte[] xdata = x.getData();

        String expected = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffff82345678";

        DataWord result = x.signExtend(k);

        assertArrayEquals(xdata, x.getData());
        assertEquals(expected, result.toString());
    }

    @Test
    public void testSignExtend8() {
        byte k = 30;

        DataWord x = DataWord.valueOf(Hex.decode("ff34567882345678823456788234567882345678823456788234567882345678"));
        byte[] xdata = x.getData();

        String expected = "0034567882345678823456788234567882345678823456788234567882345678";

        DataWord result = x.signExtend(k);

        assertArrayEquals(xdata, x.getData());
        assertEquals(expected, result.toString());
    }

    @Test
    public void testSignExtendException1() {
        byte k = -1;

        DataWord x = DataWord.ZERO;

        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            x.signExtend(k); // should throw an exception
        });

    }

    @Test
    public void testLongValue1() {
        byte[] negative = new byte[]{-1, -1, -1, -1,-1,-1,-1,-1};

        DataWord x = DataWord.valueOf(negative);

        long l = x.longValue();
        assertEquals(l, -1);
    }

    @Test
    public void testSignExtendException2() {
        byte k = 32;

        DataWord x = DataWord.ZERO;

        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            x.signExtend(k); // should throw an exception
        });
    }

    @Test
    public void testFromString() {
        // I'm using a 32 bytes string to avoid the preceding blanks
        DataWord parsed = DataWord.fromString("01234567890123456789012345678901");

        assertEquals(new String(parsed.getData()),"01234567890123456789012345678901");
    }

    @Test
    public void testFromLongString() {
        String value = "012345678901234567890123456789012345678901234567890123456789";
        byte[] hashedValue = HashUtil.keccak256(value.getBytes(StandardCharsets.UTF_8));
        DataWord parsed = DataWord.fromLongString(value);

        assertArrayEquals(parsed.getData(),hashedValue);
    }

    @Test
    public void testAddModOverflow() {
        testAddMod("9999999999999999999999999999999999999999999999999999999999999999",
                "8888888888888888888888888888888888888888888888888888888888888888",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        testAddMod("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    }

    void testAddMod(String v1, String v2, String v3) {
        DataWord dv1 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode(v1));
        DataWord dv2 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode(v2));
        DataWord dv3 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode(v3));

        byte[] datadv1 = dv1.getData();
        byte[] datadv2 = dv2.getData();
        byte[] datadv3 = dv3.getData();

        BigInteger bv1 = new BigInteger(v1, 16);
        BigInteger bv2 = new BigInteger(v2, 16);
        BigInteger bv3 = new BigInteger(v3, 16);

        DataWord result = dv1.addmod(dv2, dv3);

        BigInteger br = bv1.add(bv2).mod(bv3);

        assertArrayEquals(datadv1, dv1.getData());
        assertArrayEquals(datadv2, dv2.getData());
        assertArrayEquals(datadv3, dv3.getData());

        assertEquals(result.value(), br);
    }

    @Test
    public void testMulMod1() {
        DataWord wr = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("9999999999999999999999999999999999999999999999999999999999999999"));
        DataWord w1 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("01"));
        DataWord w2 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("9999999999999999999999999999999999999999999999999999999999999998"));

        byte[] datawr = wr.getData();
        byte[] dataw1 = w1.getData();
        byte[] dataw2 = w2.getData();

        DataWord result = wr.mulmod(w1, w2);

        assertArrayEquals(datawr, wr.getData());
        assertArrayEquals(dataw1, w1.getData());
        assertArrayEquals(dataw2, w2.getData());
        assertEquals(32, result.getData().length);
        assertEquals("0000000000000000000000000000000000000000000000000000000000000001", ByteUtil.toHexString(result.getData()));
    }

    @Test
    public void testMulMod2() {
        DataWord wr = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("9999999999999999999999999999999999999999999999999999999999999999"));
        DataWord w1 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("01"));
        DataWord w2 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("9999999999999999999999999999999999999999999999999999999999999999"));

        byte[] datawr = wr.getData();
        byte[] dataw1 = w1.getData();
        byte[] dataw2 = w2.getData();

        DataWord result = wr.mulmod(w1, w2);

        assertArrayEquals(datawr, wr.getData());
        assertArrayEquals(dataw1, w1.getData());
        assertArrayEquals(dataw2, w2.getData());
        assertEquals(32, result.getData().length);
        assertTrue(result.isZero());
    }

    @Test
    public void testMulModZero() {
        DataWord wr = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("00"));
        DataWord w1 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("9999999999999999999999999999999999999999999999999999999999999999"));
        DataWord w2 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));

        byte[] datawr = wr.getData();
        byte[] dataw1 = w1.getData();
        byte[] dataw2 = w2.getData();

        DataWord result = wr.mulmod(w1, w2);

        assertArrayEquals(datawr, wr.getData());
        assertArrayEquals(dataw1, w1.getData());
        assertArrayEquals(dataw2, w2.getData());
        assertEquals(32, result.getData().length);
        assertTrue(result.isZero());
    }

    @Test
    public void testMulModZeroWord1() {
        DataWord wr = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("9999999999999999999999999999999999999999999999999999999999999999"));
        DataWord w1 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("00"));
        DataWord w2 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));

        byte[] datawr = wr.getData();
        byte[] dataw1 = w1.getData();
        byte[] dataw2 = w2.getData();

        DataWord result = wr.mulmod(w1, w2);

        assertArrayEquals(datawr, wr.getData());
        assertArrayEquals(dataw1, w1.getData());
        assertArrayEquals(dataw2, w2.getData());
        assertEquals(32, wr.getData().length);
        assertTrue(result.isZero());
    }

    @Test
    public void testMulModZeroWord2() {
        DataWord wr = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("9999999999999999999999999999999999999999999999999999999999999999"));
        DataWord w1 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
        DataWord w2 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("00"));

        byte[] datawr = wr.getData();
        byte[] dataw1 = w1.getData();
        byte[] dataw2 = w2.getData();

        DataWord result = wr.mulmod(w1, w2);

        assertArrayEquals(datawr, wr.getData());
        assertArrayEquals(dataw1, w1.getData());
        assertArrayEquals(dataw2, w2.getData());
        assertEquals(32, wr.getData().length);
        assertTrue(result.isZero());
    }

    @Test
    public void testMulModOverflow() {
        DataWord wr = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
        DataWord w1 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
        DataWord w2 = DataWord.valueOf(org.spongycastle.util.encoders.Hex.decode("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));

        byte[] datawr = wr.getData();
        byte[] dataw1 = w1.getData();
        byte[] dataw2 = w2.getData();

        DataWord result = wr.mulmod(w1, w2);

        assertArrayEquals(datawr, wr.getData());
        assertArrayEquals(dataw1, w1.getData());
        assertArrayEquals(dataw2, w2.getData());
        assertEquals(32, wr.getData().length);
        assertTrue(result.isZero());
    }

    public static BigInteger pow(BigInteger x, BigInteger y) {
        if (y.compareTo(BigInteger.ZERO) < 0)
            throw new IllegalArgumentException();
        BigInteger z = x; // z will successively become x^2, x^4, x^8, x^16,
        // x^32...
        BigInteger result = BigInteger.ONE;
        byte[] bytes = y.toByteArray();
        for (int i = bytes.length - 1; i >= 0; i--) {
            byte bits = bytes[i];
            for (int j = 0; j < 8; j++) {
                if ((bits & 1) != 0)
                    result = result.multiply(z);
                // short cut out if there are no more bits to handle:
                if ((bits >>= 1) == 0 && i == 0)
                    return result;
                z = z.multiply(z);
            }
        }

        return result;
    }

}
