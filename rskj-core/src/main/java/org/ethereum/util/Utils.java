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

import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;


public class Utils {

    /**
     * @param number should be in form '0x34fabd34....'
     * @return String
     */
    public static BigInteger unifiedNumericToBigInteger(String number) {

        boolean match = Pattern.matches("0[xX][0-9a-fA-F]+", number);
        if (!match) {
            return (new BigInteger(number));
        } else{
            number = number.substring(2);
            number = number.length() % 2 != 0 ? "0".concat(number) : number;
            byte[] numberBytes = Hex.decode(number);
            return (new BigInteger(1, numberBytes));
        }
    }

    /**
     * Return formatted Date String: yyyy.MM.dd HH:mm:ss
     * Based on Unix's time() input in seconds
     *
     * @param timestamp seconds since start of Unix-time
     * @return String formatted as - yyyy.MM.dd HH:mm:ss
     */
    public static String longToDateTime(long timestamp) {
        Date date = new Date(timestamp * 1000);
        DateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        return formatter.format(date);
    }

    static BigInteger _1000_ = new BigInteger("1000");

    public static String getValueShortString(BigInteger number) {
        BigInteger result = number;
        int pow = 0;
        while (result.compareTo(_1000_) == 1 || result.compareTo(_1000_) == 0) {
            result = result.divide(_1000_);
            pow += 3;
        }
        return result.toString() + "·(" + "10^" + pow + ")";
    }

    /**
     * Decodes a hex string to address bytes and checks validity
     *
     * @param hex - a hex string of the address, e.g., 6c386a4b26f73c802f34673f7248bb118f97424a
     * @return - decode and validated address byte[]
     */
    public static byte[] addressStringToBytes(String hex) {
        final byte[] addr;
        try {
            addr = Hex.decode(hex);
        } catch (DecoderException addressIsNotValid) {
            return null;
        }

        if (isValidAddress(addr)) {
            return addr;
        }
        return null;
    }

    public static boolean isValidAddress(byte[] addr) {
        return addr != null && addr.length == 20;
    }

    /**
     * @param addr length should be 20
     * @return short string represent 1f21c...
     */
    public static String getAddressShortString(byte[] addr) {

        if (!isValidAddress(addr)) {
            throw new Error("not an address");
        }

        String addrShort = ByteUtil.toHexString(addr, 0, 3);

        StringBuffer sb = new StringBuffer();
        sb.append(addrShort);
        sb.append("...");

        return sb.toString();
    }


    public static final double JAVA_VERSION = getJavaVersion();

    static double getJavaVersion() {
        String version = System.getProperty("java.version");

        // on android this property equals to 0
        if (version.equals("0")) {
            return 0;
        }

        int pos = 0;
        int count = 0;

        for (; pos < version.length() && count < 2; pos++) {
            if (version.charAt(pos) == '.') {
                count++;
            }
        }
        return Double.parseDouble(version.substring(0, pos - 1));
    }

    public static String getHashListShort(List<byte[]> blockHashes) {
        if (blockHashes.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        String firstHash = ByteUtil.toHexString(blockHashes.get(0));
        String lastHash = ByteUtil.toHexString(blockHashes.get(blockHashes.size() - 1));
        return sb.append(" ").append(firstHash).append("...").append(lastHash).toString();
    }

    public static long toUnixTime(long javaTime) {
        return javaTime / 1000;
    }

    public static long fromUnixTime(long unixTime) {
        return unixTime * 1000;
    }

    public static <T> T[] mergeArrays(T[] ... arr) {
        int size = 0;
        for (T[] ts : arr) {
            size += ts.length;
        }
        T[] ret = (T[]) Array.newInstance(arr[0].getClass().getComponentType(), size);
        int off = 0;
        for (T[] ts : arr) {
            System.arraycopy(ts, 0, ret, off, ts.length);
            off += ts.length;
        }
        return ret;
    }

    public static String align(String s, char fillChar, int targetLen, boolean alignRight) {
        if (targetLen <= s.length()) {
            return s;
        }
        String alignString = repeat("" + fillChar, targetLen - s.length());
        return alignRight ? alignString + s : s + alignString;

    }
    public static String repeat(String s, int n) {
        if (s.length() == 1) {
            byte[] bb = new byte[n];
            Arrays.fill(bb, s.getBytes(StandardCharsets.UTF_8)[0]);
            return new String(bb);
        } else {
            StringBuilder ret = new StringBuilder();
            for (int i = 0; i < n; i++) {
                ret.append(s);
            }
            return ret.toString();
        }
    }

    public static boolean contains(List<byte[]> list, byte[] valueToFind) {
        for (byte[] b : list) {
            if (Arrays.equals(b, valueToFind)) {
                return true;
            }
        }
        
        return false;
    }

    public static void validateArrayAllegedSize(byte[] data, int offset, int allegedSize) {
        if (data.length < Math.addExact(allegedSize, offset)) {
            throw new IllegalArgumentException("The specified size exceeds the size of the payload");
        }
    }

    public static byte[] safeCopyOfRange(byte[] data, int from, int size) {
        validateArrayAllegedSize(data, from, size);
        return Arrays.copyOfRange(data, from, from + size);
    }

    public static boolean isDecimalString(String s) {
        return s.matches("^\\d+$");
    }

    public static boolean isHexadecimalString(String s) {
        return s.matches("^0x[\\da-fA-F]+$");
    }

    public static long decimalStringToLong(String s) {
        try {
            return Long.parseLong(s, 10);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Invalid decimal number: %s", s), e);
        }
    }

    public static long hexadecimalStringToLong(String s) {
        if (!s.startsWith("0x")) {
            throw new IllegalArgumentException(String.format("Invalid hexadecimal number: %s", s));
        }

        try {
            // Remove leading '0x' before parsing
            return Long.parseLong(s.substring(2), 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Invalid hexadecimal number: %s", s), e);
        }
    }

    public static int significantBitCount(int number) {
        int result = 0;
        while (number > 0) {
            result++;
            number >>= 1;
        }
        return result;
    }
}
