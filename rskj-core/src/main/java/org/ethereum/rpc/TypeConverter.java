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

package org.ethereum.rpc;

import org.ethereum.util.ByteUtil;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Created by Ruben on 19/11/2015.
 */
public class TypeConverter {

    private TypeConverter() {
        throw new IllegalAccessError("Utility class");
    }

    public static BigInteger stringNumberAsBigInt(String input) {
        if (input.startsWith("0x")) {
            return TypeConverter.stringHexToBigInteger(input);
        } else {
            return TypeConverter.stringDecimalToBigInteger(input);
        }
    }

    public static BigInteger stringHexToBigInteger(String input) {
        String hexa = input.substring(2);
        return new BigInteger(hexa, 16);
    }

    private static BigInteger stringDecimalToBigInteger(String input) {
        return new BigInteger(input);
    }

    public static byte[] stringHexToByteArray(String x) {
        String result = x;
        if (x.startsWith("0x")) {
            result = x.substring(2);
        }
        if (result.length() % 2 != 0) {
            result = "0" + result;
        }
        return Hex.decode(result);
    }

    public static byte[] stringToByteArray(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    public static String toJsonHex(byte[] x) {
        String result = "0x" + Hex.toHexString(x == null ? ByteUtil.EMPTY_BYTE_ARRAY : x);

        if ("0x".equals(result)) {
            return "0x00";
        }

        return result;
    }

    public static String toJsonHex(String x) {
        return "0x"+x;
    }

    public static String toJsonHex(long n) {
        return "0x" + Long.toHexString(n);
    }

    public static String toJsonHex(BigInteger n) {
        return "0x"+ n.toString(16);
    }

    /**
     * Converts "0x876AF" to "876AF"
     */
    public static String removeZeroX(String str) {
        return str.substring(2);
    }

    public static long JSonHexToLong(String x) {
        if (!x.startsWith("0x")) {
            throw new NumberFormatException("Incorrect hex syntax");
        }
        x = x.substring(2);
        return Long.parseLong(x, 16);
    }
}
