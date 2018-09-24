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
    public static final int LENGHT_IN_BYTES = 32;
    private static final BigInteger _2_256 = BigInteger.valueOf(2).pow(256);
    public static final BigInteger MAX_VALUE = _2_256.subtract(BigInteger.ONE);
    public static final DataWord ZERO = new DataWord(new byte[LENGHT_IN_BYTES]);      // don't push it in to the stack
    public static final DataWord ONE = new DataWord(1);
    public static final DataWord ZERO_EMPTY_ARRAY = new DataWord(new byte[0]);      // don't push it in to the stack

    private byte[] data; // Optimization, do not initialize until needed

    public DataWord() {
        newZeroData();
    }

    public void newZeroData() {
        data=new byte[LENGHT_IN_BYTES];
    }

    public DataWord(int num) {
        this(ByteBuffer.allocate(4).putInt(num));
    }

    public DataWord(long num) {
        this(ByteBuffer.allocate(8).putLong(num));
    }

    private DataWord(ByteBuffer buffer) {
        final ByteBuffer data = ByteBuffer.allocate(LENGHT_IN_BYTES);
        final byte[] array = buffer.array();
        System.arraycopy(array, 0, data.array(), LENGHT_IN_BYTES - array.length, array.length);
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
        assign(data);
    }


    public void assign(int i) {
        // TODO: Does support negative numbers?
        zero();
        data[31] = (byte) (i & 0xff);
        data[30] = (byte) ((i>>8) & 0xff);
        data[29] = (byte) ((i>>16) & 0xff);
        data[28] = (byte) ((i>>24) & 0xff);
    }

    public void assign(long i) {
        // TODO: Does support negative numbers?
        zero();
        data[31] = (byte) (i & 0xff);
        data[30] = (byte) ((i>>8) & 0xff);
        data[29] = (byte) ((i>>16) & 0xff);
        data[28] = (byte) ((i>>24) & 0xff);
        data[27] = (byte) ((i>>32) & 0xff);
        data[26] = (byte) ((i>>40) & 0xff);
        data[25] = (byte) ((i>>48) & 0xff);
        data[24] = (byte) ((i>>56) & 0xff);
    }
    // Assign does not assume data!=null to be able to be called
    // from contructor
    public void assign(byte[] data) {
        if (data == null) {
            this.data = ByteUtil.EMPTY_BYTE_ARRAY;
        } else if (data.length == LENGHT_IN_BYTES) {
            this.data = data;
        }
        else if (data.length <= LENGHT_IN_BYTES) {
            if (this.data==null) {
                newZeroData();
            } else {
                zero();  // first clear
            }
            System.arraycopy(data, 0, this.data, LENGHT_IN_BYTES - data.length, data.length);
        }else {
            throw new RuntimeException("Data word can't exceed 32 bytes: " + data);
        }
    }

    public void assignDataRange(byte[] data,int ofs,int len) {
        if (data == null) {
            this.data = ByteUtil.EMPTY_BYTE_ARRAY;
        } else if (len <= LENGHT_IN_BYTES) {
            //if there is not enough data
            // trailing zeros are assumed (this is required  for PUSH opcode semantic
            Arrays.fill(this.data, (byte) 0); // first clear
            int dlen =Integer.min(len,data.length-ofs);
            System.arraycopy(data, ofs, this.data, LENGHT_IN_BYTES - len ,dlen );

        } else {
            throw new RuntimeException("Data word can't exceed 32 bytes: " + data);
        }
    }

    public void assignData(byte[] data) {
        if (data == null) {
            this.data = ByteUtil.EMPTY_BYTE_ARRAY;
        } else if (data.length <= LENGHT_IN_BYTES) {
            System.arraycopy(data, 0, this.data, LENGHT_IN_BYTES - data.length, data.length);
        } else {
            throw new RuntimeException("Data word can't exceed 32 bytes: " + data);
        }
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getNoLeadZeroesData() {
        return ByteUtil.stripLeadingZeroes(data);
    }

    // This is a special method that returns the data as the repository requires it.
    // A zero value is converted into a null (the storage cell is deleted).
    public byte[] getByteArrayForStorage() {
        return ByteUtil.stripLeadingZeroes(data,null);
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

        for (int i = 0; i < this.data.length; ++i) {
            this.data[i] |= w2.data[i];
        }
        return this;
    }

    public DataWord xor(DataWord w2) {

        for (int i = 0; i < this.data.length; ++i) {
            this.data[i] ^= w2.data[i];
        }
        return this;
    }

    public DataWord setTrue() {
        zero();
        data[31] = 1;
        return this;
    }

    public DataWord zero() {
        Arrays.fill(this.data, (byte) 0);
        return this;
    }

    public void negate() {

        if (this.isZero()) {
            return;
        }

        for (int i = 0; i < this.data.length; ++i) {
            this.data[i] = (byte) ~this.data[i];
        }

        for (int i = this.data.length - 1; i >= 0; --i) {
            this.data[i] = (byte) (1 + this.data[i] & 0xFF);
            if (this.data[i] != 0) {
                break;
            }
        }
    }

    public void bnot() {
        for (int i = 0; i < this.data.length; ++i) {
            this.data[i] = (byte) ~this.data[i];
        }
    }
    // this is 100 times slower than the new not.
    public void slowBnot() {
        if (this.isZero()) {
            this.data = ByteUtil.copyToArray(MAX_VALUE);
            return;
        }
        this.data = ByteUtil.copyToArray(MAX_VALUE.subtract(this.value()));
    }

    // By   : Holger
    // From : http://stackoverflow.com/a/24023466/459349
    public void add(DataWord word) {
        for (int i = 31, overflow = 0; i >= 0; i--) {
            int v = (this.data[i] & 0xff) + (word.data[i] & 0xff) + overflow;
            this.data[i] = (byte) v;
            overflow = v >>> 8;
        }
    }

    // old add-method with BigInteger quick hack
    public void add2(DataWord word) {
        BigInteger result = value().add(word.value());
        this.data = ByteUtil.copyToArray(result.and(MAX_VALUE));
    }

    // TODO: mul can be done in more efficient way
    // TODO:     with shift left shift right trick
    // TODO      without BigInteger quick hack
    public void mul(DataWord word) {
        BigInteger result = value().multiply(word.value());
        this.data = ByteUtil.copyToArray(result.and(MAX_VALUE));
    }

    // TODO: improve with no BigInteger
    public void div(DataWord word) {

        if (word.isZero()) {
            this.and(ZERO);
            return;
        }

        BigInteger result = value().divide(word.value());
        this.data = ByteUtil.copyToArray(result.and(MAX_VALUE));
    }

    // TODO: improve with no BigInteger
    public void sDiv(DataWord word) {

        if (word.isZero()) {
            this.and(ZERO);
            return;
        }

        BigInteger result = sValue().divide(word.sValue());
        this.data = ByteUtil.copyToArray(result.and(MAX_VALUE));
    }


    // TODO: improve with no BigInteger
    public void sub(DataWord word) {
        BigInteger result = value().subtract(word.value());
        this.data = ByteUtil.copyToArray(result.and(MAX_VALUE));
    }

    // TODO: improve with no BigInteger
    public void exp(DataWord word) {
        BigInteger result = value().modPow(word.value(), _2_256);
        this.data = ByteUtil.copyToArray(result);
    }

    // TODO: improve with no BigInteger
    public void mod(DataWord word) {

        if (word.isZero()) {
            this.and(ZERO);
            return;
        }

        BigInteger result = value().mod(word.value());
        this.data = ByteUtil.copyToArray(result.and(MAX_VALUE));
    }

    public void sMod(DataWord word) {

        if (word.isZero()) {
            this.and(ZERO);
            return;
        }

        BigInteger result = sValue().abs().mod(word.sValue().abs());
        result = (sValue().signum() == -1) ? result.negate() : result;

        this.data = ByteUtil.copyToArray(result.and(MAX_VALUE));
    }

    public void addmod(DataWord word1, DataWord word2) {
        if (word1.data[0] != 0 || data[0] != 0) {
            // overflow possible: slower path
            this.mod(word2);
            word1 = word1.clone();
            word1.mod(word2);
        }
        this.add(word1);
        this.mod(word2);
    }

    public void mulmod(DataWord word1, DataWord word2) {

        if (word2.isZero()) {
            this.data = new byte[LENGHT_IN_BYTES];
            return;
        }

        BigInteger result = value().multiply(word1.value()).mod(word2.value());
        this.data = ByteUtil.copyToArray(result.and(MAX_VALUE));
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

    public void signExtend(byte k) {
        if (0 > k || k > 31) {
            throw new IndexOutOfBoundsException();
        }
        byte mask = this.sValue().testBit((k * 8) + 7) ? (byte) 0xff : 0;
        for (int i = 31; i > k; i--) {
            this.data[31 - i] = mask;
        }
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
