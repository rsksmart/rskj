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
package co.rsk.util;


import co.rsk.core.Coin;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

/**
 * Hex utils
 */
public class HexUtils {
	
    private static final Pattern LEADING_ZEROS_PATTERN = Pattern.compile("0x(0)+");

    private static final String HEX_PREFIX = "0x";

    private static final String ZERO_STR = "0";

    private static final byte[] HEX_PREFIX_BYTE_ARRAY = HEX_PREFIX.getBytes();

    private static final byte[] ZERO_BYTE_ARRAY = "0".getBytes();

    private static final String INCORRECT_HEX_SYNTAX = "Incorrect hex syntax";
    private static final String INPUT_CANT_BE_NULL = "input cant be null";
    private static final String PREFIX_EXPECTED = "Invalid hex number, expected 0x prefix";

    private static final String NUMBER_VALUE_ERROR = "Number values should not contain letters or hex values should start with 0x.";

    private HexUtils() {
        throw new IllegalAccessError("Utility class");
    }

    /**
     * If 0x prefix is present Convert hex encoded string to decoded BigInteger
     * Else converts number in String representation to BigInteger
     */
    public static BigInteger stringNumberAsBigInt(final String input) {
        if (hasHexPrefix(input)) {
            return HexUtils.stringHexToBigInteger(input);
        } else {
            return new BigInteger(input);
        }
    }

    /**
     * Convert hex encoded string to decoded BigInteger
     */
    public static BigInteger stringHexToBigInteger(final String input) {
        if(!hasHexPrefix(input)) {
            throw new NumberFormatException(PREFIX_EXPECTED);
        }
        String hexa = input.substring(2);
        return new BigInteger(hexa, 16);
    }

    /**
     * Convert hex encoded string to decoded byte array
     */
    public static byte[] stringHexToByteArray(final String param) {

        String result = removeHexPrefix(param);

        if (result.length() % 2 != 0) { //NOSONAR
            result = ZERO_STR + result;
        }
        return Hex.decode(result);
    }
        
    /**
     * Convert hex encoded string or integer in string format to decoded byte array
     */
    public static byte[] strHexOrStrNumberToByteArray(String strHexOrStrNumber) {

        if (strHexOrStrNumber == null) {
            throw invalidParamError(INPUT_CANT_BE_NULL);
        }
    	
        if (hasHexPrefix(strHexOrStrNumber)) {
            return stringHexToByteArray(strHexOrStrNumber);
        }

        try {
            BigInteger number = new BigInteger(strHexOrStrNumber);
            return ByteUtil.bigIntegerToBytes(number);
        } catch (Exception e) {
            throw invalidParamError(NUMBER_VALUE_ERROR);
        }
    }

    /**
     * Convert hex encoded string or integer in BigInteger
     */
    public static BigInteger strHexOrStrNumberToBigInteger(String strHexOrStrNumber) {

        if (strHexOrStrNumber == null) {
            throw invalidParamError(INPUT_CANT_BE_NULL);
        }

        if (hasHexPrefix(strHexOrStrNumber)) {
            return stringHexToBigInteger(strHexOrStrNumber);
        }

        try {
            return new BigInteger(strHexOrStrNumber);
        } catch (Exception e) {
            throw invalidParamError(NUMBER_VALUE_ERROR);
        }
    }
    
    /**
     * String to byte array 
     */
    public static byte[] stringToByteArray(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Converts a byte array to a string according to ethereum json-rpc specifications
     */
    public static String toJsonHex(final byte[] param) {
        String result = toUnformattedJsonHex(param);

        if (HEX_PREFIX.equals(result)) {
            return "0x00";
        }

        return result;
    }

    /**
     * Convert from Coin to its BigInteger value in String representation or empty if the parameter is null
     */
    public static String toJsonHex(final Coin coin) {
        return coin != null ? coin.asBigInteger().toString() : "" ;
    }

    /**
     * add 0x prefix
     */
    public static String toJsonHex(final String param) {
        return HEX_PREFIX + param;
    }

    /**
     * @return A Hex representation of n WITHOUT leading zeroes
     */
    public static String toQuantityJsonHex(long n) {
        return HEX_PREFIX + Long.toHexString(n);
    }

    /**
     * @return A Hex representation of n WITHOUT leading zeroes
     */
    public static String toQuantityJsonHex(BigInteger n) {
        return HEX_PREFIX + n.toString(16);
    }

    /**
     * Converts a byte array to a string according to ethereum json-rpc specifications, null and empty
     * convert to 0x.
     *
     * @param param An unformatted byte array
     * @return A hex representation of the input with two hex digits per byte
     */
    public static String toUnformattedJsonHex(byte[] param) {
        return HEX_PREFIX + (param == null ? "" : ByteUtil.toHexString(param));
    }

    /**
     * Converts a byte array representing a quantity according to ethereum json-rpc specifications.
     *
     * <p>
     * 0x000AEF -> 0x2AEF
     * <p>
     * 0x00 -> 0x0
     * @param x A hex string with or without leading zeroes ("0x00AEF"). If null, it is considered as zero.
     * @return A hex string without leading zeroes ("0xAEF")
     */
    public static String toQuantityJsonHex(byte[] x) {
        String withoutLeading = LEADING_ZEROS_PATTERN.matcher(toJsonHex(x)).replaceFirst("0x");
        if (HEX_PREFIX.equals(withoutLeading)) {
            return "0x0";
        }

        return withoutLeading;
    }

    /**
     * decodes a hexadecimal encoded with the 0x prefix into a long
     */
    public static long jsonHexToLong(String x) {
        if (!hasHexPrefix(x)) {
            throw new NumberFormatException(INCORRECT_HEX_SYNTAX);
        }
        x = removeHexPrefix(x);
        return Long.parseLong(x, 16);
    }

    /**
     * if the parameter has the hex prefix 
     */
    public static boolean hasHexPrefix(final String data) {
        return data != null && data.startsWith(HEX_PREFIX);
    }

    /**
     * if the parameter has the hex prefix 
     */
    public static boolean hasHexPrefix(final byte[] data) {

        if (data == null || data.length < HEX_PREFIX_BYTE_ARRAY.length) {
            return false;
        }
        
        for (int i = 0; i < HEX_PREFIX_BYTE_ARRAY.length; i++) {
            if (HEX_PREFIX_BYTE_ARRAY[i] != data[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * if string is hexadecimal with 0x prefix
     */
    public static boolean isHexWithPrefix(final String data) {

        if (data == null) {
            return false;
        }

        String value = data.toLowerCase();

        if (value.length() <= 2 || !hasHexPrefix(value)) {
            return false;
        }

        // start testing after 0x prefix
        return isHex(value, 2);
    }

    /**
     * if a string is composed solely of hexa chars
     */
    public static boolean isHex(final String data) {
        return isHex(data, 0);
    }

    /**
     * if a string is composed solely of hexa chars Starting at the given index
     */
    public static boolean isHex(final String data, int startAt) {

        if (data == null) {
            return false;
        }

        for (int i = startAt; i < data.length(); i++) {
            char c = data.charAt(i);

            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
                return false;
            }
        }

        return true;
    }

    /**
     * Receives a plain byte array -> converts it to hexa and prepend the 0x  
     */
    public static byte[] encodeToHexByteArray(final byte[] input) {

        if (input == null) {
            throw invalidParamError(INPUT_CANT_BE_NULL);
        }

        byte[] encoded = Hex.encode(input);

        byte[] result = ByteUtil.merge(HEX_PREFIX_BYTE_ARRAY, encoded);

        return result;
    }

    /**
     * remove Hex Prefix from string
     */
    public static String removeHexPrefix(final String data) {
    	String result = data;
        if (hasHexPrefix(result)) {
            result = data.substring(2);
        }
        return result;
	}

    /**
     * remove Hex Prefix from byte array
     */
    public static byte[] removeHexPrefix(final byte[] data) {
        byte[] result = data;
        if (result != null && hasHexPrefix(result)) {
            result = new byte[data.length - HEX_PREFIX_BYTE_ARRAY.length];
            System.arraycopy(data, HEX_PREFIX_BYTE_ARRAY.length, result, 0, result.length);
        }

        return result;
    }

    /**
     * left pad with zero if the byte array has odd elements 
     */
    public static byte[] leftPad(final byte[] data) {
        byte[] result = data;
        if (result != null && result.length % 2 != 0) {
            result = ByteUtil.merge(ZERO_BYTE_ARRAY, result);
        }

        return result;
    }

    /**
     * decodes a hexadecimal encoded byte array
     */
    public static byte[] decode(byte[] dataBytes) {
        return Hex.decode(HexUtils.leftPad(HexUtils.removeHexPrefix(dataBytes)));
    }

    /**
     * decodes a hexadecimal encoded with the 0x prefix into a integer
     */
    public static int jsonHexToInt(final String param) {
        if (!hasHexPrefix(param)) {
            throw invalidParamError(INCORRECT_HEX_SYNTAX);
        }

        String preResult = removeHexPrefix(param);

        return Integer.parseInt(preResult, 16);
    }

    public static int jsonHexToIntOptionalPrefix(final String param) {
        if (!hasHexPrefix(param) && !HexUtils.isHex(param)) {
            throw invalidParamError(INCORRECT_HEX_SYNTAX);
        }

        String preResult = removeHexPrefix(param);

        return Integer.parseInt(preResult, 16);
    }

}
