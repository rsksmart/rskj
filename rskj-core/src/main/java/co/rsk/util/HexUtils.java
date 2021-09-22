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

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Hex utils
 */
public class HexUtils {
	
    private static final Pattern LEADING_ZEROS_PATTERN = Pattern.compile("0x(0)+");

    private static final String HEX_PREFIX = "0x";

    private static final byte[] HEX_PREFIX_BYTE_ARRAY = HEX_PREFIX.getBytes();

    private static final byte[] ZERO_BYTE_ARRAY = "0".getBytes();
    
    private HexUtils() {
        throw new IllegalAccessError("Utility class");
    }

    public static BigInteger stringNumberAsBigInt(String input) {
        if (hasHexPrefix(input)) {
            return HexUtils.stringHexToBigInteger(input);
        } else {
            return HexUtils.stringDecimalToBigInteger(input);
        }
    }

    public static BigInteger stringHexToBigInteger(String input) {
        if(!hasHexPrefix(input)) {
            throw new NumberFormatException("Invalid hex number, expected 0x prefix");
        }
        String hexa = input.substring(2);
        return new BigInteger(hexa, 16);
    }

    private static BigInteger stringDecimalToBigInteger(String input) {
        return new BigInteger(input);
    }

    public static byte[] stringHexToByteArray(final String param) {
        
    	String result = removeHexPrefix(param);
        
        if (result.length() % 2 != 0) {
            result = "0" + result;
        }
        return Hex.decode(result);
    }

    public static byte[] stringToByteArray(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    public static String toJsonHex(byte[] x) {
        String result = toUnformattedJsonHex(x);

        if (HEX_PREFIX.equals(result)) {
            return "0x00";
        }

        return result;
    }

    public static String toJsonHex(Coin x) {
        return x != null ? x.asBigInteger().toString() : "" ;
    }

    public static String toJsonHex(String x) {
        return HEX_PREFIX + x;
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
     * @param x An unformatted byte array
     * @return A hex representation of the input with two hex digits per byte
     */
    public static String toUnformattedJsonHex(byte[] x) {
        return HEX_PREFIX + (x == null ? "" : ByteUtil.toHexString(x));
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
     * Converts "0x876AF" to "876AF"
     */
    public static String removeZeroX(String str) {
        return str.substring(2);
    }

    public static long JSonHexToLong(String x) {
        if (!hasHexPrefix(x)) {
            throw new NumberFormatException("Incorrect hex syntax");
        }
        x = removeHexPrefix(x);
        return Long.parseLong(x, 16);
    }

    /**
     * if the paramenter has the hex prefix 
     */
    public static boolean hasHexPrefix(final String data) {
    	return data != null && data.startsWith(HEX_PREFIX);
    }
    
    /**
     * if the paramenter has the hex prefix 
     */
    public static boolean hasHexPrefix(final byte[] data) {
    	
    	for(int i = 0; i < HEX_PREFIX_BYTE_ARRAY.length; i++) {
    		if(HEX_PREFIX_BYTE_ARRAY[i] != data[i]) {
    			return false;
    		}
    	}
    	
    	return true;
    }
    
    /**
     * if string is hexadecimal with 0x prefix
     */
	public static boolean isHexWithPrefix(final String data) {
		
		if(data == null) {
			return false;
		} 
		
	    String value = data.toLowerCase();

	    if (value.startsWith("-")) {
	        value = value.substring(1);
	    }
	    
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
	 * if a string is composed solely of hexa chars
	 * Starting at the given index
	 */
	public static boolean isHex(final String data, int startAt) {
		
	    for (int i = startAt; i < data.length(); i++){
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
    	byte[] encoded = Hex.encode(input);
    	
    	byte[] result = new byte[HEX_PREFIX_BYTE_ARRAY.length + encoded.length];
    	
        System.arraycopy(HEX_PREFIX_BYTE_ARRAY, 0, result, 0, HEX_PREFIX_BYTE_ARRAY.length);
        System.arraycopy(encoded, 0, result, HEX_PREFIX_BYTE_ARRAY.length, encoded.length);
        
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
		if(hasHexPrefix(data)) {
		
			result = new byte[data.length - HEX_PREFIX_BYTE_ARRAY.length];
		
        	System.arraycopy(data, HEX_PREFIX_BYTE_ARRAY.length, result, 0, result.length);
		}
		
        return result;
	}
	
	public static byte[] leftPad(final byte[] data) {
		byte[] result = data;
        if (data.length % 2 != 0) {
        	result = new byte[data.length + ZERO_BYTE_ARRAY.length];
            System.arraycopy(ZERO_BYTE_ARRAY, 0, result, 0, ZERO_BYTE_ARRAY.length);
            System.arraycopy(data, 0, result, ZERO_BYTE_ARRAY.length, data.length);
        }
		
        return result;
	}

	public static byte[] decode(byte[] dataBytes) {
		return Hex.decode(HexUtils.leftPad(HexUtils.removeHexPrefix(dataBytes)));
	}

    public static int JSonHexToInt(final String param) {
        if (!hasHexPrefix(param)) {
            throw invalidParamError("Incorrect hex syntax");
        }
        
        String preResult = removeHexPrefix(param);
        
        return Integer.parseInt(preResult, 16);
    }
	
}


