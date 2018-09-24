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

import org.ethereum.db.ByteArrayWrapper;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ByteUtil {

    private ByteUtil() {
    }

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    /**
     * Creates a copy of bytes and appends b to the end of it
     */
    public static byte[] appendByte(byte[] bytes, byte b) {
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[result.length - 1] = b;
        return result;
    }

    public static byte[] cloneBytes(byte[] bytes) {
        if (bytes == null) {
            return EMPTY_BYTE_ARRAY;
        }

        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * The regular {@link java.math.BigInteger#toByteArray()} method isn't quite what we often need:
     * it appends a leading zero to indicate that the number is positive and may need padding.
     *
     * @param b the integer to format into a byte array
     * @param numBytes the desired size of the resulting byte array
     * @return numBytes byte long array.
     */
    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        if (b == null) {
            return EMPTY_BYTE_ARRAY;
        }
        byte[] bytes = new byte[numBytes];
        byte[] biBytes = b.toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    /**
     * Omitting sign indication byte.
     * <br><br>
     * Instead of {@link org.bouncycastle.util.BigIntegers#asUnsignedByteArray(BigInteger)}
     * <br>we use this custom method to avoid an empty array in case of BigInteger.ZERO
     *
     * @param value - any big integer number. A <code>null</code>-value will return <code>null</code>
     * @return A byte array without a leading zero byte if present in the signed encoding.
     *      BigInteger.ZERO will return an array with length 1 and byte-value 0.
     */
    public static byte[] bigIntegerToBytes(BigInteger value) {
        if (value == null) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] data = value.toByteArray();

        if (data.length != 1 && data[0] == 0) {
            byte[] tmp = new byte[data.length - 1];
            System.arraycopy(data, 1, tmp, 0, tmp.length);
            data = tmp;
        }
        return data;
    }

    /**
     * Parses fixed number of bytes starting from {@code offset} in {@code input} array.
     * If {@code input} has not enough bytes return array will be right padded with zero bytes.
     * I.e. if {@code offset} is higher than {@code input.length} then zero byte array of length {@code len} will be returned
     */
    public static byte[] parseBytes(byte[] input, int offset, int len) {

        if (offset >= input.length || len == 0) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] bytes = new byte[len];
        System.arraycopy(input, offset, bytes, 0, Math.min(input.length - offset, len));
        return bytes;
    }

    /**
     * Returns the amount of nibbles that match each other from 0 ...
     * amount will never be larger than smallest input
     *
     * @param a - first input
     * @param b - second input
     * @return Number of bytes that match
     */
    public static int matchingNibbleLength(byte[] a, byte[] b) {
        int i = 0;
        int length = a.length < b.length ? a.length : b.length;
        while (i < length) {
            if (a[i] != b[i]) {
                return i;
            }
            i++;
        }
        return i;
    }

    /**
     * Converts a long value into a byte array.
     *
     * @param val - long value to convert
     * @return <code>byte[]</code> of length 8, representing the long value
     */
    public static byte[] longToBytes(long val) {
        return ByteBuffer.allocate(8).putLong(val).array();
    }

    /**
     * Converts a long value into a byte array.
     *
     * @param val - long value to convert
     * @return decimal value with leading byte that are zeroes striped
     */
    public static byte[] longToBytesNoLeadZeroes(long val) {

        // todo: improve performance by while strip numbers until (long >> 8 == 0)
        byte[] data = ByteBuffer.allocate(8).putLong(val).array();

        return stripLeadingZeroes(data);
    }

    /**
     * Converts int value into a byte array.
     *
     * @param val - int value to convert
     * @return <code>byte[]</code> of length 4, representing the int value
     */
    public static byte[] intToBytes(int val){
        return ByteBuffer.allocate(4).putInt(val).array();
    }

    /**
     * Converts a int value into a byte array.
     *
     * @param val - int value to convert
     * @return value with leading byte that are zeroes striped
     */
    public static byte[] intToBytesNoLeadZeroes(int val){

        if (val == 0) {
            return EMPTY_BYTE_ARRAY;
        }

        int lenght = 0;

        int tmpVal = val;
        while (tmpVal != 0){
            tmpVal = tmpVal >>> 8;
            ++lenght;
        }

        byte[] result = new byte[lenght];

        int index = result.length - 1;
        while(val != 0){

            result[index] = (byte)(val & 0xFF);
            val = val >>> 8;
            index -= 1;
        }

        return result;
    }


    /**
     * Convert a byte-array into a hex String.<br>
     * Works similar to {@link Hex#toHexString}
     * but allows for <code>null</code>
     *
     * @param data - byte-array to convert to a hex-string
     * @return hex representation of the data.<br>
     *      Returns an empty String if the input is <code>null</code>
     *
     * @see Hex#toHexString
     */
    public static String toHexString(byte[] data) {
        return data == null ? "" : Hex.toHexString(data);
    }

    /**
     * Calculate packet length
     *
     * @param msg byte[]
     * @return byte-array with 4 elements
     */
    public static byte[] calcPacketLength(byte[] msg) {
        int msgLen = msg.length;
        return new byte[]{
                (byte) ((msgLen >> 24) & 0xFF),
                (byte) ((msgLen >> 16) & 0xFF),
                (byte) ((msgLen >> 8) & 0xFF),
                (byte) ((msgLen) & 0xFF)};
    }

    /**
     * Cast hex encoded value from byte[] to int
     *
     * Limited to Integer.MAX_VALUE: 2^32-1 (4 bytes)
     *
     * @param b array contains the values
     * @return unsigned positive int value.
     */
    public static int byteArrayToInt(byte[] b) {
        if (b == null || b.length == 0) {
            return 0;
        }
        return new BigInteger(1, b).intValue();
    }

    /**
     * Cast hex encoded value from byte[] to int
     *
     * Limited to Integer.MAX_VALUE: 2^32-1 (4 bytes)
     *
     * @param b array contains the values
     * @return unsigned positive long value.
     */
    public static long byteArrayToLong(byte[] b) {
        if (b == null || b.length == 0) {
            return 0;
        }
        return new BigInteger(1, b).longValue();
    }


    /**
     * Turn nibbles to a pretty looking output string
     *
     * Example. [ 1, 2, 3, 4, 5 ] becomes '\x11\x23\x45'
     *
     * @param nibbles - getting byte of data [ 04 ] and turning
     *                  it to a '\x04' representation
     * @return pretty string of nibbles
     */
    public static String nibblesToPrettyString(byte[] nibbles) {
        StringBuilder builder = new StringBuilder();
        for (byte nibble : nibbles) {
            final String nibbleString = oneByteToHexString(nibble);
            builder.append("\\x").append(nibbleString);
        }
        return builder.toString();
    }

    public static String oneByteToHexString(byte value) {
        String retVal = Integer.toString(value & 0xFF, 16);
        if (retVal.length() == 1) {
            retVal = "0" + retVal;
        }
        return retVal;
    }

    /**
     * Calculate the number of bytes need
     * to encode the number
     *
     * @param val - number
     * @return number of min bytes used to encode the number
     */
    public static int numBytes(String val) {

        BigInteger bInt = new BigInteger(val);
        int bytes = 0;

        while (!bInt.equals(BigInteger.ZERO)) {
            bInt = bInt.shiftRight(8);
            ++bytes;
        }
        if (bytes == 0) {
            ++bytes;
        }
        return bytes;
    }

    public static int firstNonZeroByte(byte[] data) {
        for (int i = 0; i < data.length; ++i) {
            if (data[i] != 0) {
                return i;
            }
        }
        return -1;
    }

    public static byte[] stripLeadingZeroes(byte[] data) {
        return stripLeadingZeroes(data,ZERO_BYTE_ARRAY);
    }

    public static byte[] stripLeadingZeroes(byte[] data,byte[] valueForZero) {

        if (data == null) {
            return null;
        }

        final int firstNonZero = firstNonZeroByte(data);

        switch (firstNonZero) {
            case -1:
                return valueForZero;

            case 0:
                return data;

            default:
                byte[] result = new byte[data.length - firstNonZero];
                System.arraycopy(data, firstNonZero, result, 0, data.length - firstNonZero);

                return result;
        }
    }

    /**
     * increment byte array as a number until max is reached
     *
     * @param bytes byte[]
     * @return boolean
     */
    public static boolean increment(byte[] bytes) {
        final int startIndex = 0;
        int i;
        for (i = bytes.length - 1; i >= startIndex; i--) {
            bytes[i]++;
            if (bytes[i] != 0) {
                break;
            }
        }
        // we return false when all bytes are 0 again
        return (i >= startIndex || bytes[startIndex] != 0);
    }

    /**
     * Utility function to copy a byte array into a new byte array with given size.
     * If the src length is smaller than the given size, the result will be left-padded
     * with zeros.
     *
     * @param value - a BigInteger with a maximum value of 2^256-1
     * @return Byte array of given size with a copy of the <code>src</code>
     */
    public static byte[] copyToArray(BigInteger value) {
        byte[] src = ByteUtil.bigIntegerToBytes(value);

        if (Arrays.equals(src, EMPTY_BYTE_ARRAY)) {
            throw new NullPointerException();
        }

        byte[] dest = ByteBuffer.allocate(32).array();
        System.arraycopy(src, 0, dest, dest.length - src.length, src.length);
        return dest;
    }

    public static ByteArrayWrapper wrap(byte[] data) {
        return new ByteArrayWrapper(data);
    }

    public static byte[] setBit(byte[] data, int pos, int val) {

        if ((data.length * 8) - 1 < pos) {
            throw new Error("outside byte array limit, pos: " + pos);
        }

        int posByte = data.length - 1 - (pos) / 8;
        int posBit = (pos) % 8;
        byte setter = (byte) (1 << (posBit));
        byte toBeSet = data[posByte];
        byte result;
        if (val == 1) {
            result = (byte) (toBeSet | setter);
        } else {
            result = (byte) (toBeSet & ~setter);
        }

        data[posByte] = result;
        return data;
    }

    public static int getBit(byte[] data, int pos) {

        if ((data.length * 8) - 1 < pos) {
            throw new Error("outside byte array limit, pos: " + pos);
        }

        int posByte = data.length - 1 - pos / 8;
        int posBit = pos % 8;
        byte dataByte = data[posByte];
        return Math.min(1, (dataByte & 0xff & (1 << (posBit))));
    }

    public static byte[] and(byte[] b1, byte[] b2) {
        if (b1.length != b2.length) {
            throw new RuntimeException("Array sizes differ");
        }
        byte[] ret = new byte[b1.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (byte) (b1[i] & b2[i]);
        }
        return ret;
    }

    public static byte[] or(byte[] b1, byte[] b2) {
        if (b1.length != b2.length) {
            throw new RuntimeException("Array sizes differ");
        }
        byte[] ret = new byte[b1.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (byte) (b1[i] | b2[i]);
        }
        return ret;
    }

    /**
     * @param arrays - arrays to merge
     * @return - merged array
     */
    public static byte[] merge(byte[]... arrays) {
        int count = 0;
        for (byte[] array: arrays) {
            count += array.length;
        }

        // Create new array and copy all array contents
        byte[] mergedArray = new byte[count];
        int start = 0;
        for (byte[] array: arrays) {
            System.arraycopy(array, 0, mergedArray, start, array.length);
            start += array.length;
        }
        return mergedArray;
    }

    public static boolean isSingleZero(byte[] array){
        return (array.length == 1 && array[0] == 0);
    }

    public static boolean isAllZeroes(byte[] array) {
        for (byte b : array) {
            if (b != 0) {
                return false;
            }
        }

        return true;
    }

    public static <T> Set<T> difference(Set<T> setA, Set<T> setB){

        Set<T> temp = new HashSet<>(setA);
        temp.removeAll(setB);
        return temp;
    }

    public static int length(byte[]... bytes) {
        int result = 0;
        for (byte[] array : bytes) {
            result += (array == null) ? 0 : array.length;
        }
        return result;
    }

    public static short bigEndianToShort(byte[] bs) {
        return bigEndianToShort(bs, 0);
    }

    public static short bigEndianToShort(byte[] bs, int off) {
        int n = bs[off] << 8;
        ++off;
        n |= bs[off] & 0xFF;
        return (short) n;
    }

    public static byte[] shortToBytes(short n) {
        return ByteBuffer.allocate(2).putShort(n).array();
    }

    /**
     * Returns a number of zero bits preceding the highest-order ("leftmost") one-bit
     * interpreting input array as a big-endian integer value
     */
    public static int numberOfLeadingZeros(byte[] bytes) {

        int i = firstNonZeroByte(bytes);

        if (i == -1) {
            return bytes.length * 8;
        } else {
            int byteLeadingZeros = Integer.numberOfLeadingZeros((int)bytes[i] & 0xff) - 24;
            return i * 8 + byteLeadingZeros;
        }
    }

    /**
     * Returns a copy of original padded to the left with zeroes,
     * or original if {@code original.length >= newLength}
     */
    public static byte[] leftPadBytes(@Nonnull byte[] original, int newLength) {
        // the result array is larger than the modulus length,
        // but this should never happen.
        if (original.length >= newLength) {
            return original;
        }

        // otherwise adjust result to the same length as the modulus has
        byte[] copy = new byte[newLength];
        System.arraycopy(original, 0, copy, newLength - original.length, original.length);
        return copy;
    }

    public static boolean fastEquals(byte[] left, byte[] right) {
        return FastByteComparisons.compareTo(
                left, 0, left.length,
                right, 0, right.length) == 0;
    }

    public static boolean fastPrefix(byte[] left, byte[] right) {
        // Short circuit equalBytes case
        if (left == right) {
            return true;
        }
        if (right.length > left.length)
            return false;

        int end1 = left.length;
        int end2 = right.length;
        for (int i = 0, j = 0; i < end1 && j < end2; i++, j++) {
            int a = (left[i] & 0xff);
            int b = (right[j] & 0xff);
            if (a != b) {
                return false;
            }
        }
        return true;
    }
}