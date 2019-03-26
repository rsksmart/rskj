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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * DataWord is the 32-byte array representation of a 256-bit number
 * Calculations can be done on this word with other DataWords
 *
 * @author Roman Mandeleil
 * @since 01.06.2014
 */
public final class DataWord implements Comparable<DataWord> {

    /* Maximum value of the DataWord */
    public static final BigInteger _2_256 = BigInteger.valueOf(2).pow(256);
    public static final BigInteger MAX_VALUE = _2_256.subtract(BigInteger.ONE);
    public static final DataWord ZERO = new DataWord();
    public static final DataWord ONE = new DataWord(1);

    private final byte[] data;

    public DataWord() {
        this.data = new byte[32];
    }

    public DataWord(int num) {
        this(ByteBuffer.allocate(4).putInt(num));
    }

    public DataWord(long num) {
        this(ByteBuffer.allocate(8).putLong(num));
    }

    private DataWord(ByteBuffer buffer) {
        final ByteBuffer data = ByteBuffer.allocate(32);
        final byte[] array = buffer.array();
        System.arraycopy(array, 0, data.array(), 32 - array.length, array.length);
        this.data = data.array();
    }

    @JsonCreator
    public DataWord(String data) {
        this(Hex.decode(data));
    }

    public DataWord(ByteArrayWrapper wrappedData){
        this(wrappedData.getData());
    }

    public DataWord(byte[] data) {
        this(data, true);
    }

    public DataWord(byte[] data,int ofs,int len) {
        if (data == null) {
            this.data = ByteUtil.EMPTY_BYTE_ARRAY;
        } else if (len <= 32) {
            //if there is not enough data
            // trailing zeros are assumed (this is required  for PUSH opcode semantic
            this.data = new byte[32];
            int dlen =Integer.min(len,data.length-ofs);
            System.arraycopy(data, ofs, this.data, 32 - len ,dlen );
        } else {
            throw new RuntimeException("Data word can't exceed 32 bytes: " + data);
        }
    }

    private DataWord(byte[] data, boolean copy) {
        if (data == null) {
            this.data = ByteUtil.EMPTY_BYTE_ARRAY;
        } else if (data.length == 32 && !copy) {
            this.data = data;
        }
        else if (data.length <= 32) {
            this.data = new byte[32];
            System.arraycopy(data, 0, this.data, 32 - data.length, data.length);
        }else {
            throw new RuntimeException("Data word can't exceed 32 bytes: " + data);
        }
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getNoLeadZeroesData() {
        return ByteUtil.stripLeadingZeroes(data);
    }

    public byte[] getLast20Bytes() {
        return Arrays.copyOfRange(data, 12, data.length);
    }

    public BigInteger value() {
        return new BigInteger(1, data);
    }

    /**
     * Converts this DataWord to an int.
     * DOES NOT THROW EXCEPTION ON OVERFLOW
     * @return this DataWord converted to an int.
     */
    public int intValue() {
        int intVal = 0;
        // Assumes 32-byte data always
        for (int i = data.length-4; i < data.length; i++)
        {
            intVal = (intVal << 8) + (data[i] & 0xff);
        }

        return intVal;
    }
    /**
     * Converts this DataWord to an int, checking for lost information.
     * If this DataWord is out of the possible range for an int result
     * then an ArithmeticException is thrown.
     *
     * @return this DataWord converted to an int.
     * @throws ArithmeticException - if this will not fit in an int.
     */
    public int intValueCheck() {
        if (bitsOccupied()>31) {
            throw new ArithmeticException();
        }

        return intValue();
    }

    /**
     * In case of int overflow returns Integer.MAX_VALUE
     * otherwise works as #intValue()
     */
    public int intValueSafe() {
        if (occupyMoreThan(4)) {
            return Integer.MAX_VALUE;
        }

        int intValue = intValue();
        if (intValue < 0) {
            return Integer.MAX_VALUE;
        }
        return intValue;
    }

    /**
     * Converts this DataWord to a long.
     * If data overflows, it will consider only the last 8 bytes
     * @return this DataWord converted to a long.
     */
    public long longValue() {

        long longVal = 0;
        // Assumes 32-byte data always
        for (int i = data.length-8; i < data.length; i++)
        {
            longVal = (longVal << 8) + (data[i] & 0xff);
        }

        return longVal;
    }
    /**
     * Converts this DataWord to a long, checking for lost information.
     * If this DataWord is out of the possible range for a long result
     * then an ArithmeticException is thrown.
     *
     * @return this DataWord converted to a long.
     * @throws ArithmeticException - if this will not fit in a long.
     */
    public long longValueCheck() {
        if (bitsOccupied()>63) {
            throw new ArithmeticException();
        }

        return longValue();
    }
    /**
     * In case of long overflow returns Long.MAX_VALUE
     * otherwise works as #longValue()
     */
    public long longValueSafe() {
        if (occupyMoreThan(8)) {
            return Long.MAX_VALUE;
        }

        long longValue = longValue();
        if (longValue < 0) {
            return Long.MAX_VALUE;
        }
        return longValue;
    }

    public BigInteger sValue() {
        return new BigInteger(data);
    }

    public String  bigIntValue() {
        return new BigInteger(data).toString();
    }

    public boolean isZero() {
        for (int i = this.data.length-1; i>=0;i--) {
            if (data[i] != 0) {
                return false;
            }
        }
        return true;
    }

    // only in case of signed operation
    // when the number is explicit defined
    // as negative
    public boolean isNegative() {
        int result = data[0] & 0x80;
        return result == 0x80;
    }

    public DataWord and(DataWord w2) {

        for (int i = 0; i < this.data.length; ++i) {
            this.data[i] &= w2.data[i];
        }
        return this;
    }

    public DataWord or(DataWord w2) {
        byte[] newdata = new byte[32];
        System.arraycopy(this.data, 0, newdata, 32 - this.data.length, this.data.length);

        for (int i = 0; i < this.data.length; ++i) {
            newdata[i] |= w2.data[i];
        }

        return new DataWord(newdata, false);
    }

    public DataWord xor(DataWord w2) {
        byte[] newdata = new byte[32];
        System.arraycopy(this.data, 0, newdata, 0, this.data.length);

        for (int i = 0; i < this.data.length; ++i) {
            newdata[i] ^= w2.data[i];
        }

        return new DataWord(newdata, false);
    }

    public DataWord bnot() {
        byte[] newdata = new byte[32];

        for (int i = 0; i < this.data.length; ++i) {
            newdata[i] = (byte) ~this.data[i];
        }

        return new DataWord(newdata, false);
    }

    // By   : Holger
    // From : http://stackoverflow.com/a/24023466/459349
    public DataWord add(DataWord word) {
        byte[] newdata = new byte[32];

        for (int i = 31, overflow = 0; i >= 0; i--) {
            int v = (this.data[i] & 0xff) + (word.data[i] & 0xff) + overflow;
            newdata[i] = (byte) v;
            overflow = v >>> 8;
        }

        return new DataWord(newdata, false);
    }

    // TODO: mul can be done in more efficient way
    // TODO:     with shift left shift right trick
    // TODO      without BigInteger quick hack
    public DataWord mul(DataWord word) {
        BigInteger result = value().multiply(word.value());
        return new DataWord(ByteUtil.copyToArray(result.and(MAX_VALUE)), false);
    }

    // TODO: improve with no BigInteger
    public DataWord div(DataWord word) {

        if (word.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = value().divide(word.value());
        return new DataWord(ByteUtil.copyToArray(result.and(MAX_VALUE)), false);
    }

    // TODO: improve with no BigInteger
    public DataWord sDiv(DataWord word) {
        if (word.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = sValue().divide(word.sValue());
        return new DataWord(ByteUtil.copyToArray(result.and(MAX_VALUE)), false);
    }

    // TODO: improve with no BigInteger
    public DataWord sub(DataWord word) {
        BigInteger result = value().subtract(word.value());
        return new DataWord(ByteUtil.copyToArray(result.and(MAX_VALUE)), false);
    }

    // TODO: improve with no BigInteger
    public DataWord exp(DataWord word) {
        BigInteger result = value().modPow(word.value(), _2_256);
        return new DataWord(ByteUtil.copyToArray(result), false);
    }

    // TODO: improve with no BigInteger
    public DataWord mod(DataWord word) {
        if (word.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = value().mod(word.value());
        return new DataWord(ByteUtil.copyToArray(result.and(MAX_VALUE)), false);
    }

    public DataWord sMod(DataWord word) {
        if (word.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = sValue().abs().mod(word.sValue().abs());
        result = (sValue().signum() == -1) ? result.negate() : result;

        return new DataWord(ByteUtil.copyToArray(result.and(MAX_VALUE)), false);
    }

    public DataWord addmod(DataWord word1, DataWord word2) {
        if (word2.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = value().add(word1.value()).mod(word2.value());
        return new DataWord(ByteUtil.copyToArray(result.and(MAX_VALUE)), false);
    }

    public DataWord mulmod(DataWord word1, DataWord word2) {
        if (word2.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = value().multiply(word1.value()).mod(word2.value());
        return new DataWord(ByteUtil.copyToArray(result.and(MAX_VALUE)), false);
    }

    @JsonValue
    @Override
    public String toString() {
        return Hex.toHexString(data);
    }

    public String toPrefixString() {

        byte[] pref = getNoLeadZeroesData();
        if (pref.length == 0) {
            return "";
        }

        if (pref.length < 7) {
            return Hex.toHexString(pref);
        }

        return Hex.toHexString(pref).substring(0, 6);
    }

    public String shortHex() {
        String hexValue = Hex.toHexString(getNoLeadZeroesData()).toUpperCase();
        return "0x" + hexValue.replaceFirst("^0+(?!$)", "");
    }

    public DataWord clone() {
        return new DataWord(Arrays.clone(data));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return equalValue((DataWord) o);
    }

    public boolean equalValue(DataWord  o) {
        return java.util.Arrays.equals(data, o.data);

    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(data);
    }

    @Override
    public int compareTo(DataWord o) {
        if (o == null || o.getData() == null) {
            return -1;
        }
        int result = FastByteComparisons.compareTo(
                data, 0, data.length,
                o.getData(), 0, o.getData().length);

        // Convert result into -1, 0 or 1 as is the convention
        // SigNum uses floating point arithmetic. It should be faster
        // to solve it in integer arithmetic
        // return (int) Math.signum(result);
        if (result<0) {
            return -1;
        } else if (result>0) {
            return 1;
        } else {
            return 0;
        }
    }

    public DataWord signExtend(byte k) {
        byte[] newdata = new byte[32];
        System.arraycopy(this.data, 0, newdata, 32 - this.data.length, this.data.length);

        if (0 > k || k > 31) {
            throw new IndexOutOfBoundsException();
        }

        byte mask = (new BigInteger(newdata)).testBit((k * 8) + 7) ? (byte) 0xff : 0;

        for (int i = 31; i > k; i--) {
            newdata[31 - i] = mask;
        }

        return new DataWord(newdata, false);
    }

    public boolean occupyMoreThan(int n) {
        return bytesOccupied()>n;
    }

    public int bytesOccupied() {
        int firstNonZero = ByteUtil.firstNonZeroByte(data);
        if (firstNonZero == -1) {
            return 0;
        }
        return 31 - firstNonZero + 1;
    }

    public static int numberOfLeadingZeros(byte i) {
        // UNTESTED: Needs unit testing
        if (i == 0) {
            return 8;
        }
        int n = 0;
        int v = i;
        if (v >>> 4 == 0) {
            n +=  4; 
            v <<=  4;
        }
        if (v >>> 6 == 0) {
            n +=  2; 
            v <<=  2; 
        }
        if (v >>> 7 == 0) { 
            n +=  1;  
        }

        return n;
    }

    public static int numberOfTrailingNonZeros(byte i) {
        return 8 - numberOfLeadingZeros(i);
    }

    public int bitsOccupied() {
        int firstNonZero = ByteUtil.firstNonZeroByte(data);
        if (firstNonZero == -1) {
            return 0;
        }

        // TODO Replace/Update this class code with current EthereumJ version
        return numberOfTrailingNonZeros(data[firstNonZero]) + ((31 - firstNonZero)<<3);
    }

    public boolean isHex(String hex) {
        return Hex.toHexString(data).equals(hex);
    }

    /**
     * Will create a DataWord from the string value, but first will encode it using {@link org.ethereum.rpc.TypeConverter#stringToByteArray(String)}
     * @param value any string value with less than 32 bytes
     * @return a valid DataWord with the encoded string as the data, if the data has less than 32 bytes it will precede it with zeros
     */
    public static DataWord fromString(String value) {
        return new DataWord(org.ethereum.rpc.TypeConverter.stringToByteArray(value));
    }

}
