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

import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ByteUtilTest {

    @Test
    public void testAppendByte() {
        byte[] bytes = "tes".getBytes();
        byte b = 0x74;
        Assertions.assertArrayEquals("test".getBytes(), ByteUtil.appendByte(bytes, b));
    }

    @Test
    public void testBigIntegerToBytes() {
        byte[] expecteds = new byte[]{(byte) 0xff, (byte) 0xec, 0x78};
        BigInteger b = BigInteger.valueOf(16772216);
        byte[] actuals = ByteUtil.bigIntegerToBytes(b);
        Assertions.assertArrayEquals(expecteds, actuals);
    }

    @Test
    public void testBigIntegerToBytesNegative() {
        byte[] expecteds = new byte[]{(byte) 0xff, 0x0, 0x13, (byte) 0x88};
        BigInteger b = BigInteger.valueOf(-16772216);
        byte[] actuals = ByteUtil.bigIntegerToBytes(b);
        Assertions.assertArrayEquals(expecteds, actuals);
    }

    @Test
    public void testBigIntegerToBytesZero() {
        byte[] expecteds = new byte[]{0x00};
        BigInteger b = BigInteger.ZERO;
        byte[] actuals = ByteUtil.bigIntegerToBytes(b);
        Assertions.assertArrayEquals(expecteds, actuals);
    }

    @Test
    public void testToHexString_ProducedHex() {
        byte[] data = new byte[] {(byte) 0xff, 0x0, 0x13, (byte) 0x88};
        Assertions.assertEquals(Hex.toHexString(data), ByteUtil.toHexString(data));
    }

    @Test
    public void testToHexString_NullPointerExceptionForNull() {
        Assertions.assertThrows(NullPointerException.class, () -> ByteUtil.toHexString(null));
    }

    @Test
    public void testToHexStringOrEmpty_EmptyStringForNull() {
        Assertions.assertEquals("", ByteUtil.toHexStringOrEmpty(null));
    }

    @Test
    public void testCalcPacketLength() {
        byte[] test = new byte[]{0x0f, 0x10, 0x43};
        byte[] expected = new byte[]{0x00, 0x00, 0x00, 0x03};
        Assertions.assertArrayEquals(expected, ByteUtil.calcPacketLength(test));
    }

    @Test
    public void testByteArrayToLong() {
        Assertions.assertEquals(Long.MAX_VALUE, ByteUtil.byteArrayToLong(new byte[]{
                (byte)127, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)255, (byte)255, (byte)255,
                }
        ));
        Assertions.assertEquals(0, ByteUtil.byteArrayToLong(new byte[]{ } ));
    }

    @Test
    public void testByteArrayToLongThrowsWhenOverflow() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ByteUtil.byteArrayToLong(new byte[]{
                (byte)255, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)255, (byte)255, (byte)255,
                (byte)123, }
        ));
    }

    @Test
    public void testByteArrayToInt() {
        Assertions.assertEquals(0, ByteUtil.byteArrayToInt(null));
        Assertions.assertEquals(0, ByteUtil.byteArrayToInt(new byte[0]));

//      byte[] x = new byte[] { 5,1,7,0,8 };
//      long start = System.currentTimeMillis();
//      for (int i = 0; i < 100000000; i++) {
//           ByteArray.read32bit(x, 0);
//      }
//      long end = System.currentTimeMillis();
//      System.out.println(end - start + "ms");
//
//      long start1 = System.currentTimeMillis();
//      for (int i = 0; i < 100000000; i++) {
//          new BigInteger(1, x).intValue();
//      }
//      long end1 = System.currentTimeMillis();
//      System.out.println(end1 - start1 + "ms");

    }

    @Test
    public void testNumBytes() {
        String test1 = "0";
        String test2 = "1";
        String test3 = "1000000000"; //3B9ACA00
        int expected1 = 1;
        int expected2 = 1;
        int expected3 = 4;
        Assertions.assertEquals(expected1, ByteUtil.numBytes(test1));
        Assertions.assertEquals(expected2, ByteUtil.numBytes(test2));
        Assertions.assertEquals(expected3, ByteUtil.numBytes(test3));
    }

    @Test
    public void testStripLeadingZeroes() {
        byte[] test1 = null;
        byte[] test2 = new byte[]{};
        byte[] test3 = new byte[]{0x00};
        byte[] test4 = new byte[]{0x00, 0x01};
        byte[] test5 = new byte[]{0x00, 0x00, 0x01};
        byte[] expected1 = null;
        byte[] expected2 = new byte[]{0};
        byte[] expected3 = new byte[]{0};
        byte[] expected4 = new byte[]{0x01};
        byte[] expected5 = new byte[]{0x01};
        Assertions.assertArrayEquals(expected1, ByteUtil.stripLeadingZeroes(test1));
        Assertions.assertArrayEquals(expected2, ByteUtil.stripLeadingZeroes(test2));
        Assertions.assertArrayEquals(expected3, ByteUtil.stripLeadingZeroes(test3));
        Assertions.assertArrayEquals(expected4, ByteUtil.stripLeadingZeroes(test4));
        Assertions.assertArrayEquals(expected5, ByteUtil.stripLeadingZeroes(test5));
    }

    @Test
    public void testMatchingNibbleLength1() {
        // a larger than b
        byte[] a = new byte[]{0x00, 0x01};
        byte[] b = new byte[]{0x00};
        int result = ByteUtil.matchingNibbleLength(a, b);
        Assertions.assertEquals(1, result);
    }

    @Test
    public void testMatchingNibbleLength2() {
        // b larger than a
        byte[] a = new byte[]{0x00};
        byte[] b = new byte[]{0x00, 0x01};
        int result = ByteUtil.matchingNibbleLength(a, b);
        Assertions.assertEquals(1, result);
    }

    @Test
    public void testMatchingNibbleLength3() {
        // a and b the same length equalBytes
        byte[] a = new byte[]{0x00};
        byte[] b = new byte[]{0x00};
        int result = ByteUtil.matchingNibbleLength(a, b);
        Assertions.assertEquals(1, result);
    }

    @Test
    public void testMatchingNibbleLength4() {
        // a and b the same length not equalBytes
        byte[] a = new byte[]{0x01};
        byte[] b = new byte[]{0x00};
        int result = ByteUtil.matchingNibbleLength(a, b);
        Assertions.assertEquals(0, result);
    }

    @Test
    public void testNiceNiblesOutput_1() {
        byte[] test = {7, 0, 7, 5, 7, 0, 7, 0, 7, 9};
        String result = "\\x07\\x00\\x07\\x05\\x07\\x00\\x07\\x00\\x07\\x09";
        Assertions.assertEquals(result, ByteUtil.nibblesToPrettyString(test));
    }

    @Test
    public void testNiceNiblesOutput_2() {
        byte[] test = {7, 0, 7, 0xf, 7, 0, 0xa, 0, 7, 9};
        String result = "\\x07\\x00\\x07\\x0f\\x07\\x00\\x0a\\x00\\x07\\x09";
        Assertions.assertEquals(result, ByteUtil.nibblesToPrettyString(test));
    }

    @Test
    public void testMatchingNibbleLength5() {
        // a == null
        byte[] a = null;
        byte[] b = new byte[]{0x00};
        Assertions.assertThrows(NullPointerException.class, () -> ByteUtil.matchingNibbleLength(a, b));
    }

    @Test
    public void testMatchingNibbleLength6() {
        // b == null
        byte[] a = new byte[]{0x00};
        byte[] b = null;
        Assertions.assertThrows(NullPointerException.class, () -> ByteUtil.matchingNibbleLength(a, b));
    }

    @Test
    public void testMatchingNibbleLength7() {
        // a or b is empty
        byte[] a = new byte[0];
        byte[] b = new byte[]{0x00};
        int result = ByteUtil.matchingNibbleLength(a, b);
        Assertions.assertEquals(0, result);
    }

    /**
     * This test shows the difference between iterating over,
     * and comparing byte[] vs BigInteger value.
     *
     * Results indicate that the former has ~15x better performance.
     * Therefore this is used in the Miner.mine() method.
     */
    @Test
    public void testIncrementPerformance() {
        boolean testEnabled = false;

        if (testEnabled) {
            byte[] counter1 = new byte[4];
            byte[] max = ByteBuffer.allocate(4).putInt(Integer.MAX_VALUE).array();
            long start1 = System.currentTimeMillis();
            while (ByteUtil.increment(counter1)) {
                if (FastByteComparisons.compareTo(counter1, 0, 4, max, 0, 4) == 0) {
                    break;
                }
            }
            System.out.println(System.currentTimeMillis() - start1 + "ms to reach: " + ByteUtil.toHexString(counter1));

            BigInteger counter2 = BigInteger.ZERO;
            long start2 = System.currentTimeMillis();
            while (true) {
                if (counter2.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) == 0) {
                    break;
                }
                counter2 = counter2.add(BigInteger.ONE);
            }
            System.out.println(System.currentTimeMillis() - start2 + "ms to reach: " + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(4, counter2)));
        }
    }


    @Test
    public void firstNonZeroByte_1() {

        byte[] data = Hex.decode("0000000000000000000000000000000000000000000000000000000000000000");
        int result = ByteUtil.firstNonZeroByte(data);

        Assertions.assertEquals(-1, result);
    }

    @Test
    public void firstNonZeroByte_2() {

        byte[] data = Hex.decode("0000000000000000000000000000000000000000000000000000000000332211");
        int result = ByteUtil.firstNonZeroByte(data);

        Assertions.assertEquals(29, result);
    }

    @Test
    public void firstNonZeroByte_3() {

        byte[] data = Hex.decode("2211009988776655443322110099887766554433221100998877665544332211");
        int result = ByteUtil.firstNonZeroByte(data);

        Assertions.assertEquals(0, result);
    }

    @Test
    public void setBitTest() {
        /*
            Set on
         */
        byte[] data = ByteBuffer.allocate(4).putInt(0).array();
        int posBit = 24;
        int expected = 16777216;
        int result = -1;
        byte[] ret = ByteUtil.setBit(data, posBit, 1);
        result = ByteUtil.byteArrayToInt(ret);
        Assertions.assertTrue(expected == result);

        posBit = 25;
        expected = 50331648;
        ret = ByteUtil.setBit(data, posBit, 1);
        result = ByteUtil.byteArrayToInt(ret);
        Assertions.assertTrue(expected == result);

        posBit = 2;
        expected = 50331652;
        ret = ByteUtil.setBit(data, posBit, 1);
        result = ByteUtil.byteArrayToInt(ret);
        Assertions.assertTrue(expected == result);

        /*
            Set off
         */
        posBit = 24;
        expected = 33554436;
        ret = ByteUtil.setBit(data, posBit, 0);
        result = ByteUtil.byteArrayToInt(ret);
        Assertions.assertTrue(expected == result);

        posBit = 25;
        expected = 4;
        ret = ByteUtil.setBit(data, posBit, 0);
        result = ByteUtil.byteArrayToInt(ret);
        Assertions.assertTrue(expected == result);

        posBit = 2;
        expected = 0;
        ret = ByteUtil.setBit(data, posBit, 0);
        result = ByteUtil.byteArrayToInt(ret);
        Assertions.assertTrue(expected == result);
    }

    @Test
    public void getBitTest() {
        byte[] data = ByteBuffer.allocate(4).putInt(0).array();
        ByteUtil.setBit(data, 24, 1);
        ByteUtil.setBit(data, 25, 1);
        ByteUtil.setBit(data, 2, 1);

        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < (data.length * 8); i++) {
            int res = ByteUtil.getBit(data, i);
            if (res == 1)
                if (i != 24 && i != 25 && i != 2)
                    Assertions.assertTrue(false);
                else
                    found.add(i);
            else {
                if (i == 24 || i == 25 || i == 2)
                    Assertions.assertTrue(false);
            }
        }

        if (found.size() != 3)
            Assertions.assertTrue(false);
        Assertions.assertTrue(found.get(0) == 2);
        Assertions.assertTrue(found.get(1) == 24);
        Assertions.assertTrue(found.get(2) == 25);
    }

    @Test
    public void numToBytesTest() {
        byte[] bytes = ByteUtil.intToBytesNoLeadZeroes(-1);
        Assertions.assertArrayEquals(bytes, Hex.decode("ffffffff"));
        bytes = ByteUtil.intToBytesNoLeadZeroes(1);
        Assertions.assertArrayEquals(bytes, Hex.decode("01"));
        bytes = ByteUtil.intToBytesNoLeadZeroes(255);
        Assertions.assertArrayEquals(bytes, Hex.decode("ff"));
        bytes = ByteUtil.intToBytesNoLeadZeroes(256);
        Assertions.assertArrayEquals(bytes, Hex.decode("0100"));

        bytes = ByteUtil.intToBytes(-1);
        Assertions.assertArrayEquals(bytes, Hex.decode("ffffffff"));
        bytes = ByteUtil.intToBytes(1);
        Assertions.assertArrayEquals(bytes, Hex.decode("00000001"));
        bytes = ByteUtil.intToBytes(255);
        Assertions.assertArrayEquals(bytes, Hex.decode("000000ff"));
        bytes = ByteUtil.intToBytes(256);
        Assertions.assertArrayEquals(bytes, Hex.decode("00000100"));

        bytes = ByteUtil.longToBytesNoLeadZeroes(-1);
        Assertions.assertArrayEquals(bytes, Hex.decode("ffffffffffffffff"));
        bytes = ByteUtil.longToBytesNoLeadZeroes(1);
        Assertions.assertArrayEquals(bytes, Hex.decode("01"));
        bytes = ByteUtil.longToBytesNoLeadZeroes(255);
        Assertions.assertArrayEquals(bytes, Hex.decode("ff"));
        bytes = ByteUtil.longToBytesNoLeadZeroes(1L << 32);
        Assertions.assertArrayEquals(bytes, Hex.decode("0100000000"));

        bytes = ByteUtil.longToBytes(-1);
        Assertions.assertArrayEquals(bytes, Hex.decode("ffffffffffffffff"));
        bytes = ByteUtil.longToBytes(1);
        Assertions.assertArrayEquals(bytes, Hex.decode("0000000000000001"));
        bytes = ByteUtil.longToBytes(255);
        Assertions.assertArrayEquals(bytes, Hex.decode("00000000000000ff"));
        bytes = ByteUtil.longToBytes(256);
        Assertions.assertArrayEquals(bytes, Hex.decode("0000000000000100"));
    }

    @Test
    public void testNumberOfLeadingZeros() {

        int n0 = ByteUtil.numberOfLeadingZeros(new byte[0]);
        Assertions.assertEquals(0, n0);

        int n1 = ByteUtil.numberOfLeadingZeros(Hex.decode("05"));
        Assertions.assertEquals(5, n1);

        int n2 = ByteUtil.numberOfLeadingZeros(Hex.decode("01"));
        Assertions.assertEquals(7, n2);

        int n3 = ByteUtil.numberOfLeadingZeros(Hex.decode("00"));
        Assertions.assertEquals(8, n3);

        int n4 = ByteUtil.numberOfLeadingZeros(Hex.decode("ff"));
        Assertions.assertEquals(0, n4);


        byte[] v1 = Hex.decode("1040");

        int n5 = ByteUtil.numberOfLeadingZeros(v1);
        Assertions.assertEquals(3, n5);

        // add leading zero bytes
        byte[] v2 = new byte[4];
        System.arraycopy(v1, 0, v2, 2, v1.length);

        int n6 = ByteUtil.numberOfLeadingZeros(v2);
        Assertions.assertEquals(19, n6);

        byte[] v3 = new byte[8];

        int n7 = ByteUtil.numberOfLeadingZeros(v3);
        Assertions.assertEquals(64, n7);

    }

    @Test
    public void testParseBytes() {
        byte[] shortByteArray = new byte[]{0,1};
        byte[] normalByteArray = new byte[]{0,1,2,3,4,5};
        byte[] normalByteArrayOffset3LeadingZeroes = new byte[]{3,4,5,0,0,0};
        //If input.length < offset then EMPTY_BYTE_ARRAY
        byte[] b1 = ByteUtil.parseBytes(shortByteArray, shortByteArray.length+1, 10);
        Assertions.assertEquals(b1, ByteUtil.EMPTY_BYTE_ARRAY);
        //If len == 0 then EMPTY_BYTE_ARRAY
        byte[] b2 = ByteUtil.parseBytes(shortByteArray, shortByteArray.length-1, 0);
        Assertions.assertEquals(b1, ByteUtil.EMPTY_BYTE_ARRAY);

        byte[] b3 = ByteUtil.parseBytes(normalByteArray, normalByteArray.length -3,
                normalByteArrayOffset3LeadingZeroes.length);
        for (int i=0; i < b3.length; i++) {
            Assertions.assertEquals(b3[i], normalByteArrayOffset3LeadingZeroes[i]);
        }
    }

    @Test
    public void testToBytesWithLeadingZeros_InvalidLen() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ByteUtil.toBytesWithLeadingZeros(new byte[0], -1));
    }

    @Test
    public void testToBytesWithLeadingZeros_NullSource() {
        byte[] actualResult = ByteUtil.toBytesWithLeadingZeros(null, 1);

        Assertions.assertNull(actualResult);
    }

    @Test
    public void testToBytesWithLeadingZeros_EmptySource() {
        byte[] src = new byte[0];

        byte[] actualResult = ByteUtil.toBytesWithLeadingZeros(src, 0);

        Assertions.assertEquals(src, actualResult);
    }

    @Test
    public void testToBytesWithLeadingZeros_SameSource() {
        byte[] src = new byte[]{0};

        byte[] actualResult = ByteUtil.toBytesWithLeadingZeros(src, 0);

        Assertions.assertEquals(src, actualResult);
    }

    @Test
    public void testToBytesWithLeadingZeros_WithLeadingZeros() {
        byte[] src = new byte[]{1, 2};

        byte[] actualResult = ByteUtil.toBytesWithLeadingZeros(src, 10);

        Assertions.assertEquals(10, actualResult.length);

        int srcStart = actualResult.length - src.length;
        for (int i = 0; i < srcStart; i++) {
            Assertions.assertEquals(0, actualResult[i]);
        }
        for (int i = srcStart; i < actualResult.length; i++) {
            Assertions.assertEquals(src[i - srcStart], actualResult[i]);
        }
    }
}
