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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Roman Mandeleil
 * @since 17.05.14
 */
public class UtilsTest {

    @Test
    public void testGetValueShortString1() {

        String expected = "123·(10^24)";
        String result = Utils.getValueShortString(new BigInteger("123456789123445654363653463"));

        assertEquals(expected, result);
    }

    @Test
    public void testGetValueShortString2() {

        String expected = "123·(10^3)";
        String result = Utils.getValueShortString(new BigInteger("123456"));

        assertEquals(expected, result);
    }

    @Test
    public void testGetValueShortString3() {

        String expected = "1·(10^3)";
        String result = Utils.getValueShortString(new BigInteger("1234"));

        assertEquals(expected, result);
    }

    @Test
    public void testGetValueShortString4() {

        String expected = "123·(10^0)";
        String result = Utils.getValueShortString(new BigInteger("123"));

        assertEquals(expected, result);
    }

    @Test
    public void testGetValueShortString5() {

        byte[] decimal = Hex.decode("3913517ebd3c0c65000000");
        String expected = "69·(10^24)";
        String result = Utils.getValueShortString(new BigInteger(decimal));

        assertEquals(expected, result);
    }

    @Test
    public void testAddressStringToBytes() {
        // valid address
        String HexStr = "6c386a4b26f73c802f34673f7248bb118f97424a";
        byte[] expected = Hex.decode(HexStr);
        byte[] result = Utils.addressStringToBytes(HexStr);
        assertEquals(Arrays.areEqual(expected, result), true);

        // invalid address, we removed the last char so it cannot decode
        HexStr = "6c386a4b26f73c802f34673f7248bb118f97424";
        expected = null;
        result = Utils.addressStringToBytes(HexStr);
        assertEquals(expected, result);

        // invalid address, longer than 20 bytes
        HexStr = new String(Hex.encode("I am longer than 20 bytes, i promise".getBytes()));
        expected = null;
        result = Utils.addressStringToBytes(HexStr);
        assertEquals(expected, result);

        // invalid address, shorter than 20 bytes
        HexStr = new String(Hex.encode("I am short".getBytes()));
        expected = null;
        result = Utils.addressStringToBytes(HexStr);
        assertEquals(expected, result);
    }

    @Test
    public void TestValidateArrayWithOffset() {
        byte[] data = new byte[10];
        // Valid indices
        Utils.validateArrayAllegedSize(data, 1, 0);
        Utils.validateArrayAllegedSize(data, 8, 1);
        Utils.validateArrayAllegedSize(data, 0, 10);
        Utils.validateArrayAllegedSize(data, 8, 2);
        Utils.validateArrayAllegedSize(
                data, 11, -1); // This makes no sense but we don't care about negative indices
        Utils.validateArrayAllegedSize(
                data, -2, 12); // This makes no sense but we don't care about negative indices

        // Invalid indices
        try {
            Utils.validateArrayAllegedSize(data, 0, 11);
            fail("should have failed");
        } catch (IllegalArgumentException e) {
            // Only type of exception expected
        }
        try {
            Utils.validateArrayAllegedSize(data, 2, 9);
            fail("should have failed");
        } catch (IllegalArgumentException e) {
            // Only type of exception expected
        }
        try {
            Utils.validateArrayAllegedSize(new byte[0], 1, 0);
            fail("should have failed");
        } catch (IllegalArgumentException e) {
            // Only type of exception expected
        }
        byte[] noData = null;
        try {
            Utils.validateArrayAllegedSize(noData, 1, 1);
            fail("should have failed");
        } catch (NullPointerException e) {
            // Only type of exception expected
        }
    }

    @Test
    public void TestSafeCopyOfRangeWithValidArrays() {
        Utils.safeCopyOfRange(new byte[2], 0, 1);
        Utils.safeCopyOfRange(new byte[100], 97, 3);
        Utils.safeCopyOfRange(new byte[0], 0, 0);
    }

    @Test
    public void TestSafeCopyOfRangeWithInvalidArrays() {
        try {
            Utils.safeCopyOfRange(new byte[2], 1, 2);
            fail("should have failed");
        } catch (IllegalArgumentException e) {
        }
        try {
            Utils.safeCopyOfRange(new byte[100], 98, 3);
            fail("should have failed");
        } catch (IllegalArgumentException e) {
        }
        try {
            Utils.safeCopyOfRange(new byte[0], 0, 1);
            fail("should have failed");
        } catch (IllegalArgumentException e) {
        }
        try {
            Utils.safeCopyOfRange(new byte[0], 1, 0);
            fail("should have failed");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void isHexadecimalString() {
        String[] hexStrings = new String[] {"0x1", "0xaaa", "0xAAA", "0xAFe1", "0x10", "0xA12"};
        java.util.Arrays.stream(hexStrings)
                .forEach(s -> Assert.assertTrue(s, Utils.isHexadecimalString(s)));

        String[] nonHexStrings =
                new String[] {"hellothisisnotahex", "123", "AAA", "AFe1", "0xab123z", "0xnothing"};
        java.util.Arrays.stream(nonHexStrings)
                .forEach(s -> Assert.assertFalse(s, Utils.isHexadecimalString(s)));
    }

    @Test
    public void isDecimalString() {
        String[] decStrings = new String[] {"1", "123", "045670", "220", "0", "01"};
        java.util.Arrays.stream(decStrings)
                .forEach(s -> Assert.assertTrue(s, Utils.isDecimalString(s)));

        String[] nonDecStrings =
                new String[] {"hellothisisnotadec", "123a", "0b", "b1", "AAA", "0xabcd", "0x123"};
        java.util.Arrays.stream(nonDecStrings)
                .forEach(s -> Assert.assertFalse(s, Utils.isDecimalString(s)));
    }

    @Test
    public void decimalStringToLong() {
        Object[] cases =
                new Object[] {
                    "1", 1L,
                    "123", 123L,
                    "045670", 45670L,
                    "220", 220L,
                    "0", 0L,
                    "01", 1L
                };
        for (int i = 0; i < cases.length / 2; i++) {
            String s = (String) cases[i * 2];
            long expected = (long) cases[i * 2 + 1];
            Assert.assertEquals(expected, Utils.decimalStringToLong(s));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void decimalStringToLongFail() {
        Utils.decimalStringToLong("zzz");
    }

    @Test
    public void hexadecimalStringToLong() {
        Object[] cases =
                new Object[] {
                    "0x1", 1L,
                    "0xaaa", 2730L,
                    "0xAAA", 2730L,
                    "0xAFe1", 45025L,
                    "0x10", 16L,
                    "0xA12", 2578L
                };
        for (int i = 0; i < cases.length / 2; i++) {
            String s = (String) cases[i * 2];
            long expected = (long) cases[i * 2 + 1];
            Assert.assertEquals(expected, Utils.hexadecimalStringToLong(s));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void hexadecimalStringToLongFail() {
        Utils.hexadecimalStringToLong("abcd");
    }

    @Test(expected = IllegalArgumentException.class)
    public void hexadecimalStringToLongFailBis() {
        Utils.hexadecimalStringToLong("zzz");
    }

    @Test
    public void significantBitCount() {
        Assert.assertEquals(0, Utils.significantBitCount(0b0));
        Assert.assertEquals(1, Utils.significantBitCount(0b1));
        Assert.assertEquals(2, Utils.significantBitCount(0b10));
        Assert.assertEquals(2, Utils.significantBitCount(0b11));
        Assert.assertEquals(3, Utils.significantBitCount(0b111));
        Assert.assertEquals(3, Utils.significantBitCount(0b100));
        Assert.assertEquals(3, Utils.significantBitCount(0b101));
        Assert.assertEquals(13, Utils.significantBitCount(0b1000111000101));
        Assert.assertEquals(9, Utils.significantBitCount(0b000111000101));
    }
}
