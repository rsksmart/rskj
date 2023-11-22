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

import org.ethereum.util.ByteUtil;

import java.math.BigInteger;

/**
 * The fundamental network cost unit. Paid for exclusively by SBTC, which is converted
 * freely to and from Gas as required. Gas does not exist outside of the internal RSK
 * computation engine; its price is set by the Transaction and miners are free to
 * ignore Transactions whose Gas price is too low.
 *
 * GasCost includes static methods to operate on longs which represent Gas. These methods
 * should always be used when calculating gas.
 */
public class GasCost {
    /* backwards compatibility, remove eventually */
    public static final long STEP = 1;
    public static final long SSTORE = 300;
    /* backwards compatibility, remove eventually */
    public static final long ZEROSTEP = 0;
    public static final long QUICKSTEP = 2;
    public static final long FASTESTSTEP = 3;
    public static final long FASTSTEP = 5;
    public static final long MIDSTEP = 8;
    public static final long SLOWSTEP = 10;
    public static final long EXTSTEP = 20;

    public static final long GENESISGASLIMIT = 1000000;
    public static final long MINGASLIMIT = 125000;

    public static final long BALANCE = 400;
    public static final long SHA3 = 30;
    public static final long SHA3_WORD = 6;
    public static final long SLOAD = 200;
    public static final long STOP = 0;
    public static final long SUICIDE = 5000;
    public static final long CLEAR_SSTORE = 5000;
    public static final long SET_SSTORE = 20000;
    public static final long RESET_SSTORE = 5000;
    public static final long REFUND_SSTORE = 15000;
    public static final long CREATE = 32000;

    public static final long JUMPDEST = 1;
    public static final long CREATE_DATA_BYTE = 5;
    public static final long CALL = 700;
    public static final long STIPEND_CALL = 2300; // For transferring coins in CALL, this is always passed to child
    public static final long VT_CALL = 9000;  //value transfer call
    public static final long NEW_ACCT_CALL = 25000;  //new account call
    public static final long MEMORY = 3; // TODO: Memory in V0 is more expensive than V1: This MUST be modified before release
    public static final long MEMORY_V1 =3;
    public static final long SUICIDE_REFUND = 24000;
    public static final long QUAD_COEFF_DIV = 512;
    public static final long CREATE_DATA = 200; // paid for each new byte of code
    public static final long REPLACE_DATA = 50; // paid for each byte of code replaced
    public static final long TX_NO_ZERO_DATA = 68;
    public static final long TX_NO_ZERO_DATA_EIP2028 = 16; // https://eips.ethereum.org/EIPS/eip-2028
    public static final long TX_ZERO_DATA = 4;
    public static final long TRANSACTION = 21000;
    public static final long TRANSACTION_DEFAULT = 90000; //compatibility with ethereum (mmarquez)
    public static final long TRANSACTION_CREATE_CONTRACT = 53000;
    public static final long LOG_GAS = 375;
    public static final long LOG_DATA_GAS = 8;
    public static final long LOG_TOPIC_GAS = 375;
    public static final long COPY_GAS = 3;
    public static final long EXP_GAS = 10;
    public static final long EXP_BYTE_GAS = 50;
    public static final long IDENTITY = 15;
    public static final long IDENTITY_WORD = 3;
    public static final long RIPEMD160 = 600;
    public static final long RIPEMD160_WORD = 120;
    public static final long SHA256 = 60;
    public static final long SHA256_WORD = 12;
    public static final long EC_RECOVER = 3000;
    public static final long EXT_CODE_SIZE = 700;
    public static final long EXT_CODE_COPY = 700;
    public static final long EXT_CODE_HASH = 400;
    public static final long CODEREPLACE = 15000;
    public static final long NEW_ACCT_SUICIDE = 25000;
    public static final long RETURN = 0;

    public static final long MAX_GAS = Long.MAX_VALUE;

    /**
     * An exception which is thrown be methods in GasCost when
     * an operation overflows, has invalid inputs or wants to return
     * an invalid gas value.
     */
    public static class InvalidGasException extends IllegalArgumentException {

        private InvalidGasException(long invalidValue) {
            super(String.format("Got invalid gas value: %d", invalidValue));
        }

        private InvalidGasException(byte[] bytes) {
            super(String.format("Got invalid gas value as bytes array: %s", ByteUtil.toHexStringOrEmpty(bytes)));
        }

        private InvalidGasException(String str) {
            super(String.format("Got invalid gas value, tried operation: %s", str));
        }

    }

    // Everything in this class should be static, do not initialize.
    private GasCost() { }

    /**
     * Converts a byte array to gas. Byte arrays are signed two bit compliments.
     * The byte array must have at most 8 values in it so as to fit in a long.
     * Be careful so as not to send a negative byte array.
     * @param bytes represents the number which will be converted to gas.
     * @return the gas equivalent of the byte array.
     * @throws InvalidGasException if the array has more than 8 values or
     *                             is negative.
     */
    public static long toGas(byte[] bytes) throws InvalidGasException {
        if (bytes.length > 8 || (bytes.length == 8 && (bytes[0] & 0xff) >= 0x80)) {
            return Long.MAX_VALUE;
        }

        return ByteUtil.byteArrayToLong(bytes);
    }

    /**
     * Convert a BigInteger to gas.
     * @throws InvalidGasException if the big integer is negative or is bigger than Long.MAX_VALUE.
     */
    public static long toGas(BigInteger big) throws InvalidGasException {
        if (big.compareTo(BigInteger.ZERO) < 0) {
            throw new InvalidGasException(big.toByteArray());
        }
        return toGas(big.toByteArray());
    }

    /**
     * Make sure the number is a valid gas value.
     * @return: the number, if is positive or zero.
     * @throws InvalidGasException if the number is negative.
     */
    public static long toGas(long number) throws InvalidGasException {
        if (number < 0) {
            throw new InvalidGasException(number);
        }
        return number;
    }

    /**
     * Adds two longs numbers representing gas, capping at Long.MAX_VALUE.
     * @param x some gas.
     * @param y another gas.
     * @return the sum of the two numbers, capped.
     * @throws InvalidGasException if any of the inputs is negative
     */
    public static long add(long x, long y) throws InvalidGasException {
        if (x < 0 || y < 0) {
            throw new InvalidGasException(String.format("%d + %d", x, y));
        }
        long result = x + y;
        if (result < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    /**
     * Multply two longs representing gas, capping at Long.MAX_VALUE.
     * @param x some gas.
     * @param y another gas.
     * @return the multiplication of the two numbers, capped.
     * @throws InvalidGasException if any of the inputs is negative
     */
    public static long multiply(long x, long y) throws InvalidGasException {
        if (x < 0 || y < 0) {
            throw new InvalidGasException(String.format("%d * %d", x, y));
        }
        long result = x * y;
        if (multiplicationOverflowed(x, y, result)) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    /**
     * Subtracts two longs representing gas.
     * @throws InvalidGasException if any of the inputs are negative or the
     * result of the subtraction is negative.
     */
    public static long subtract(long x, long y) throws InvalidGasException {
        if (y < 0 || y > x) {
            throw new InvalidGasException(String.format("%d - %d", x, y));
        }
        return x - y;
    }

    /**
     * Calculate the total gas cost given a baseline cost, the cost of an unit and how many units
     * are passed. The operation is capped at Long.MAX_VALUE.
     * @param baseCost a baseline cost.
     * @param unitCost the cost of a single unit.
     * @param units how many units.
     * @return baseCost + unitCost * units, capped at Long.MAX_VALUE
     * @throws InvalidGasException if any of the inputs are negative.
     */
    public static long calculateTotal(long baseCost, long unitCost, long units) throws InvalidGasException {
        if (baseCost < 0 || unitCost < 0 || units < 0) {
            throw new InvalidGasException(String.format("%d + %d * %d", baseCost, unitCost, units));
        }
        long mult = unitCost * units;
        if (multiplicationOverflowed(unitCost, units, mult)) {
            return Long.MAX_VALUE;
        }
        long result = baseCost + mult;
        if (result < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    /**
     * Returns whether r is overflowed in `x * y = r`
     * Both x and y must be positive.
     */
    private static boolean multiplicationOverflowed(long x, long y, long result) {
        // Heavily inspired on Math.multiplyExact
        // https://github.com/frohoff/jdk8u-jdk/blob/master/src/share/classes/java/lang/Math.java#L882/
        // changed because a precondition states that both x and y must be positive.
        if (((x | y) >>> 31 != 0)) {
            // Some bits greater than 2^31 that might cause overflow
            // Check the result using the divide operator
            // and check for the special case of Long.MIN_VALUE * -1
            return (y != 0) && (result/ y != x);
        }
        return false;
    }
}
