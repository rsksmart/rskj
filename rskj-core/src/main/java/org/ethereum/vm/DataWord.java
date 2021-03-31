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
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * DataWord is the 32-byte array representation of a 256-bit number
 * Calculations can be done on this word with other DataWords
 *
 * @author Roman Mandeleil
 * @since 01.06.2014
 */
public final class DataWord implements Comparable<DataWord> {
    /**
     * The number of bytes used to represent a DataWord
     */
    public static final int BYTES = 32;
    private static final byte[] ZERO_DATA = new byte[BYTES];

    /* Maximum value of the DataWord */
    public static final BigInteger _2_256 = BigInteger.valueOf(2).pow(256);
    public static final BigInteger MAX_VALUE = _2_256.subtract(BigInteger.ONE);
    public static final DataWord ZERO = new DataWord(ZERO_DATA);
    public static final DataWord ONE = valueOf(1);
    public static final int MAX_POW = 256;

    private final byte[] data;

    /**
     * Use this constructor for internal operations that don't need copying
     */
    private DataWord(byte[] data) {
        if (data.length != BYTES) {
            throw new IllegalArgumentException(String.format("A DataWord must be %d bytes long", BYTES));
        }

        this.data = data;
    }

    /**
     * Returns a copy of the internal data in order to guarantee immutability
     */
    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public byte[] getNoLeadZeroesData() {
        byte[] noLeadZeroesData = ByteUtil.stripLeadingZeroes(data);
        if (noLeadZeroesData == data) {
            // in case the method returned the same object, return a copy to ensure immutability
            return getData();
        }

        return noLeadZeroesData;
    }

    // This is a special method that returns the data as the repository requires it.
    // A zero value is converted into a null (the storage cell is deleted).
    public byte[] getByteArrayForStorage() {
        return ByteUtil.stripLeadingZeroes(data, null);
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
        byte[] newdata = getData();

        for (int i = 0; i < this.data.length; ++i) {
            newdata[i] &= w2.data[i];
        }

        return new DataWord(newdata);
    }

    public DataWord or(DataWord w2) {
        byte[] newdata = getData();

        for (int i = 0; i < this.data.length; ++i) {
            newdata[i] |= w2.data[i];
        }

        return new DataWord(newdata);
    }

    public DataWord xor(DataWord w2) {
        byte[] newdata = getData();

        for (int i = 0; i < this.data.length; ++i) {
            newdata[i] ^= w2.data[i];
        }

        return new DataWord(newdata);
    }

    public DataWord bnot() {
        byte[] newdata = new byte[BYTES];

        for (int i = 0; i < this.data.length; ++i) {
            newdata[i] = (byte) ~this.data[i];
        }

        return new DataWord(newdata);
    }

    // By   : Holger
    // From : http://stackoverflow.com/a/24023466/459349
    public DataWord add(DataWord word) {
        byte[] newdata = new byte[BYTES];

        for (int i = 31, overflow = 0; i >= 0; i--) {
            int v = (this.data[i] & 0xff) + (word.data[i] & 0xff) + overflow;
            newdata[i] = (byte) v;
            overflow = v >>> 8;
        }

        return new DataWord(newdata);
    }

    // TODO: mul can be done in more efficient way
    // TODO:     with shift left shift right trick
    // TODO      without BigInteger quick hack
    public DataWord mul(DataWord word) {
        BigInteger result = value().multiply(word.value());
        return valueOf(result.and(MAX_VALUE));
    }

    // TODO: improve with no BigInteger
    public DataWord div(DataWord word) {

        if (word.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = value().divide(word.value());
        return valueOf(result.and(MAX_VALUE));
    }

    // TODO: improve with no BigInteger
    public DataWord sDiv(DataWord word) {
        if (word.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = sValue().divide(word.sValue());
        return valueOf(result.and(MAX_VALUE));
    }

    // TODO: improve with no BigInteger
    public DataWord sub(DataWord word) {
        BigInteger result = value().subtract(word.value());
        return valueOf(result.and(MAX_VALUE));
    }

    // TODO: improve with no BigInteger
    public DataWord exp(DataWord word) {
        BigInteger result = value().modPow(word.value(), _2_256);
        return valueOf(result);
    }

    // TODO: improve with no BigInteger
    public DataWord mod(DataWord word) {
        if (word.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = value().mod(word.value());
        return valueOf(result.and(MAX_VALUE));
    }

    public DataWord sMod(DataWord word) {
        if (word.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = sValue().abs().mod(word.sValue().abs());
        result = (sValue().signum() == -1) ? result.negate() : result;

        return valueOf(result.and(MAX_VALUE));
    }

    public DataWord addmod(DataWord word1, DataWord word2) {
        if (word2.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = value().add(word1.value()).mod(word2.value());
        return valueOf(result.and(MAX_VALUE));
    }

    public DataWord mulmod(DataWord word1, DataWord word2) {
        if (word2.isZero()) {
            return DataWord.ZERO;
        }

        BigInteger result = value().multiply(word1.value()).mod(word2.value());
        return valueOf(result.and(MAX_VALUE));
    }

    /**
     * Shift left, both this and input arg are treated as unsigned
     * @param arg
     * @return this << arg
     */

    public DataWord shiftLeft(DataWord arg) {
        if (arg.compareTo(DataWord.valueOf(MAX_POW)) >= 0) {
            return DataWord.ZERO;
        }

        byte[] bytes = ByteUtil.shiftLeft(this.getData(), arg.intValueSafe());

        return new DataWord(bytes);
    }

    /**
     * Shift right, both this and input arg are treated as unsigned
     * @param arg
     * @return this >> arg
     */
    public DataWord shiftRight(DataWord arg) {
        if (arg.compareTo(DataWord.valueOf(MAX_POW)) >= 0) {
            return DataWord.ZERO;
        }

        byte[] bytes = ByteUtil.shiftRight(this.getData(), arg.intValueSafe());
        return new DataWord(bytes);
    }

    /**
     * Shift right, this is signed, while input arg is treated as unsigned
     * @param arg
     * @return this >> arg
     */
    public DataWord shiftRightSigned(DataWord arg) {
        // Taken from Pantheon implementation
        // https://github.com/PegaSysEng/pantheon/blob/master/ethereum/core/src/main/java/tech/pegasys/pantheon/ethereum/vm/operations/SarOperation.java

        if (arg.compareTo(DataWord.valueOf(MAX_POW)) >= 0) {
            if (this.isNegative()) {
                return valueOf(MAX_VALUE); // This should be 0xFFFFF......
            } else {
                return DataWord.ZERO;
            }
        }

        byte[] bytes = ByteUtil.shiftRight(this.getData(), arg.intValueSafe());

        if (isNegative()){
            byte[] allBits = valueOf(MAX_VALUE).getData();
            byte[] significantBits = ByteUtil.shiftLeft(allBits, 256 - arg.intValueSafe());
            bytes = ByteUtil.or(bytes, significantBits);
        }

        return new DataWord(bytes);
    }


    @JsonValue
    @Override
    public String toString() {
        return ByteUtil.toHexString(data);
    }

    public String toPrefixString() {

        byte[] pref = getNoLeadZeroesData();
        if (pref.length == 0) {
            return "";
        }

        if (pref.length < 7) {
            return ByteUtil.toHexString(pref);
        }

        return ByteUtil.toHexString(pref).substring(0, 6);
    }

    public String shortHex() {
        String hexValue = ByteUtil.toHexString(getNoLeadZeroesData()).toUpperCase();
        return "0x" + hexValue.replaceFirst("^0+(?!$)", "");
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
        if (o == null || o.data == null) {
            return -1;
        }
        int result = FastByteComparisons.compareTo(
                data, 0, data.length,
                o.data, 0, o.data.length
        );

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
        byte[] newdata = getData();

        if (0 > k || k > 31) {
            throw new IndexOutOfBoundsException();
        }

        byte mask = (new BigInteger(newdata)).testBit((k * 8) + 7) ? (byte) 0xff : 0;

        for (int i = 31; i > k; i--) {
            newdata[31 - i] = mask;
        }

        return new DataWord(newdata);
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
        return ByteUtil.toHexString(data).equals(hex);
    }

    /**
     * Will create a DataWord from the string value.
     * @param value any string with a byte representation of 32 bytes or less
     * @return a DataWord with the encoded string as the data, padded with zeroes if necessary
     */
    public static DataWord fromString(String value) {
        return valueOf(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Will create a Dataword from the keccack256 representation of the string value.
     * @param value any streing with a byte representation of more than 32 bytes
     * @return a DataWord with the hashed string as the data
     */
    public static DataWord fromLongString(String value) { return valueOf(HashUtil.keccak256(value.getBytes(StandardCharsets.UTF_8))); }

    @JsonCreator
    public static DataWord valueFromHex(String data) {
        return valueOf(Hex.decode(data));
    }

    public static DataWord valueOf(int num) {
        byte[] data = new byte[BYTES];
        ByteBuffer.wrap(data).putInt(data.length - Integer.BYTES, num);
        return valueOf(data);
    }

    public static DataWord valueOf(long num) {
        byte[] data = new byte[BYTES];
        ByteBuffer.wrap(data).putLong(data.length - Long.BYTES, num);
        return valueOf(data);
    }

    public static DataWord valueOf(byte[] data) {
        return valueOf(data, 0, data.length);
    }

    public static DataWord valueOf(byte[] data, int offset, int length) {
        if (data == null || length == 0) {
            return ZERO;
        }

        if (length > BYTES) {
            throw new IllegalArgumentException(String.format("A DataWord must be %d bytes long", BYTES));
        }

        // if there is not enough data
        // trailing zeros are assumed (this is required for PUSH opcode semantics)
        byte[] copiedData = new byte[BYTES];
        int dlen = Integer.min(length, data.length - offset);
        System.arraycopy(data, offset, copiedData, BYTES - length, dlen);
        return new DataWord(copiedData);
    }

    private static DataWord valueOf(BigInteger data) {
        return new DataWord(ByteUtil.copyToArray(data));
    }
}
