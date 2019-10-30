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
    // Everything in this class should be static, do not initialize.
    private GasCost() { }

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
    public static final long NEW_ACCT_SUICIDE = 25000;
    public static final long RETURN = 0;

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
            super(String.format("Got invalid gas value as bytes array: %s", ByteUtil.toHexString(bytes)));
        }

    }

    /**
     * Converts a byte array to gas. Byte arrays are signed two bit compliments.
     * @param bytes represents the number which will be converted to gas.
     * @return the gas equivalent of the byte array.
     * @throws InvalidGasException if the number if bigger than Long.MAX_GAS.
     */
    public static long toGas(byte[] bytes) throws InvalidGasException {
        if (bytes.length > 8) {
            throw new InvalidGasException(bytes);
        }
        long result = ByteUtil.byteArrayToLong(bytes);
        if (result < 0) {
            throw new InvalidGasException(bytes);
        }
        return result;
    }

    public static long toGas(BigInteger big) throws InvalidGasException {
        if (big.compareTo(BigInteger.ZERO) < 0) {
            throw new InvalidGasException(big.longValue());
        }
        return toGas(big.toByteArray());
    }

    public static long toGas(long number) {
        if (number < 0) {
            throw new InvalidGasException(number);
        }
        return number;
    }

    /**
     * Adds two longs numbers representing gas.
     * @param x some gas.
     * @param y another gas.
     * @return the sum of the two numbers.
     * @throws InvalidGasException if any of the gas inputs is negative
     *         or if the sum is bigger than Long.MAX_VALUE.
     */
    public static long add(long x, long y) throws InvalidGasException {
        if (x < 0 || y < 0) {
            long offender = x < 0 ? x : y;
            throw new InvalidGasException(offender);
        }
        long result = x + y;
        if (additionOverflowed(x, y, result)) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    public static long multiply(long x, long y) throws InvalidGasException {
        if (x < 0 || y < 0) {
            long offender = x < 0 ? x : y;
            throw new InvalidGasException(offender);
        }
        long result = x * y;
        if (multiplicationOverflowed(x, y, result)) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    /**
     * Substracts two longs representing gas.
     * @throws InvalidGasException if any of the inputs are invalid
     *         or the operation overflows or underflows.
     */
    public static long subtract(long x, long y) throws InvalidGasException {
        if (x < 0 || y < 0) {
            long offender = x < 0 ? x : y;
            throw new InvalidGasException(offender);
        }
        long result = x - y;
        // no need to check for overflow. as both inputs must be positive,
        // the min value here is when x = 0 and y = Long.MAX_VALUE and
        // thus result == Long.MAX_VALUE * -1, which does not overflow.
        if (result < 0) {
            return 0;
        }
        return result;
    }

    /**
     * Calculate the total gas cost given a baseline cost, the cost of an unit and how many units
     * are passed.
     * @param baseCost a baseline cost.
     * @param unitCost the cost of a single unit.
     * @param units how many units.
     * @return baseCost + unitCost * units
     * @throws InvalidGasException if any of the inputs are negative, or if the operation overflows.
     */
    public static long calculate(long baseCost, long unitCost, long units) throws InvalidGasException {
        if (baseCost < 0 || unitCost < 0 || units < 0) {
            long offender = baseCost < 0 ? baseCost : unitCost < 0 ? unitCost : units;
            throw new InvalidGasException(offender);
        }
        long mult = unitCost * units;
        if (multiplicationOverflowed(unitCost, units, mult)) {
            return Long.MAX_VALUE;
        }
        long result = baseCost + mult;
        if (additionOverflowed(baseCost, mult, result)) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    /**
     * Returns whether r is overflowed in `x + y = r`.
     */
    private static boolean additionOverflowed(long x, long y, long result) {
        // This is taken exactly from the implementation of
        // java.Math.addExact.
        // https://github.com/frohoff/jdk8u-jdk/blob/master/src/share/classes/java/lang/Math.java#L805
        // Copied here to avoid exceptions, which are slow.
        return ((x ^ result) & (y ^ result)) < 0;
    }

    /**
     * Returns whether r is overflowed in `x * y = r`
     */
    private static boolean multiplicationOverflowed(long x, long y, long result) {
        // Again, taken exactly from java.Math.multiplyExact to avoid
        // using exceptions.
        // https://github.com/frohoff/jdk8u-jdk/blob/master/src/share/classes/java/lang/Math.java#L882/
        long ax = Math.abs(x);
        long ay = Math.abs(y);
        if (((ax | ay) >>> 31 != 0)) {
            // Some bits greater than 2^31 that might cause overflow
            // Check the result using the divide operator
            // and check for the special case of Long.MIN_VALUE * -1
            return (((y != 0) && (result/ y != x)) || (x == Long.MIN_VALUE && y == -1));
        }
        return false;
    }
}
