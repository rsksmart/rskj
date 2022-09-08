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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 15/07/2019.
 */
public class GasCostTest {

    @Test
    public void toGas() {
        Assertions.assertEquals(0, GasCost.toGas(new byte[0]));
        Assertions.assertEquals(1, GasCost.toGas(new byte[] { 0x01 }));
        Assertions.assertEquals(255, GasCost.toGas(new byte[] { (byte)0xff }));
        Assertions.assertEquals(
                Long.MAX_VALUE, GasCost.toGas(BigInteger.valueOf(Long.MAX_VALUE).toByteArray())
        );
    }

    @Test
    public void toGasOverflowsSlightly() {
        byte[] bytes = new byte[32];

        for (int k = 0; k < 17; k++) {
            bytes[k] = (byte)0x00;
        }
        bytes[17] = (byte)0x80;
        for (int k = 18; k < 32; k++) {
            bytes[k] = (byte)0x00;
        }
        Assertions.assertEquals(GasCost.toGas(bytes), Long.MAX_VALUE);
    }


    @Test
    public void toGasGivesNegativeValue() throws GasCost.InvalidGasException {
        byte[] negativeBytes = new byte[]{
                (byte)255, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)255, (byte)255, (byte)255,
        };
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.toGas(negativeBytes));
    }

    @Test
    public void toGasArrayTooBig() throws GasCost.InvalidGasException {
        byte[] bigArray = new byte[]{
                (byte)255, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)255, (byte)255, (byte)255,
        };
        Assertions.assertEquals(GasCost.toGas(bigArray), Long.MAX_VALUE);
    }

    @Test
    public void toGasFromLongWithNegativeLong() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.toGas(-1L));
    }

    @Test
    public void toGasFromOverflowedLong() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.toGas(Long.MAX_VALUE + 1));
    }

    @Test
    public void toGasFromLong() {
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.toGas(Long.MAX_VALUE));
        Assertions.assertEquals(123L, GasCost.toGas(123L));
    }

    @Test
    public void toGasWithBigInteger() {
        BigInteger bi = BigInteger.valueOf(Long.MAX_VALUE - 10);
        Assertions.assertEquals(Long.MAX_VALUE - 10, GasCost.toGas(bi));
    }

    @Test
    public void toGasWithBigIntegerOverflowing() {
        BigInteger bi = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(100));
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.toGas(bi));
    }

    @Test
    public void toGasWithNegativeBigInteger() {
        BigInteger bi = BigInteger.valueOf(-1);
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.toGas(bi));
    }

    @Test
    public void moreNegativeBiToGas() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.toGas(BigInteger.valueOf(-3512)));
    }

    @Test
    public void mostNegativeBiToGas() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.toGas(BigInteger.valueOf(-99999999999999L)));
    }

    @Test
    public void calculateAddGas() {
        Assertions.assertEquals(1, GasCost.add(1, 0));
        Assertions.assertEquals(2, GasCost.add(1, 1));
        Assertions.assertEquals(1000000, GasCost.add(500000, 500000));
    }

    @Test
    public void calculateAddGasWithOverflow() {
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.add(Long.MAX_VALUE, 1));
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.add(1, Long.MAX_VALUE));
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.add(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    public void calculateAddGasCostWithSecondNegativeInputAndResult() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.add(0, -1));
    }

    @Test
    public void calculateAddGasCostWithFirstNegativeInputAndResult() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.add(-1, 1));
    }

    @Test
    public void calculateSubtractGasCost() {
        Assertions.assertEquals(1, GasCost.subtract(1, 0));
        Assertions.assertEquals(0, GasCost.subtract(1, 1));
        Assertions.assertEquals(1000000, GasCost.subtract(1500000, 500000));
        Assertions.assertEquals(0, GasCost.subtract(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    public void calculateSubtractWithNegativeInput() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.subtract(1, -1));
    }

    @Test
    public void calculateSubtractWithExtremelyNegativeResult() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.subtract(0, Long.MAX_VALUE));
    }

    @Test
    public void calculateSubtractGasToInvalidSubtle() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.subtract(1, 2));
    }

    @Test
    public void calculateSubtractGasToInvalidObvious()  {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.subtract(1, 159));
    }

    @Test
    public void multiplyWithNegativeValues() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.multiply(-1, -2));
    }

    @Test
    public void multiplyWithXNegative() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.multiply(-1, 123));
    }

    @Test
    public void multiplyWithYNegative() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.multiply(1, -9123));
    }

    @Test
    public void multiply() {
        long x = (long) Math.pow(2, 62);
        long y = (long) Math.pow(2, 12);
        long overflowed = GasCost.multiply(x, y);
        Assertions.assertEquals(Long.MAX_VALUE, overflowed, "overflowed is coverted to max gas");
    }

    @Test
    public void multiplyOverflowing() throws GasCost.InvalidGasException {
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.multiply(4611686018427387903L, 4096L));
    }

    @Test
    public void multiplyWithNegativeInput() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.multiply(1, -9123));
    }

    @Test
    public void calculateGasCost() throws GasCost.InvalidGasException {
        Assertions.assertEquals(1, GasCost.calculateTotal(1, 0, 0));
        Assertions.assertEquals(2, GasCost.calculateTotal(0, 2, 1));
        Assertions.assertEquals(7, GasCost.calculateTotal(1, 2, 3));
        Assertions.assertEquals(10, GasCost.calculateTotal(4, 3, 2));
        Assertions.assertEquals(GasCost.CREATE + 100 * GasCost.CREATE_DATA, GasCost.calculateTotal(GasCost.CREATE, GasCost.CREATE_DATA, 100));
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.calculateTotal(1, Long.MAX_VALUE, 1));
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.calculateTotal(Long.MAX_VALUE, Long.MAX_VALUE, 1));
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.calculateTotal(0, Long.MAX_VALUE, 2));
    }

    @Test
    public void calculateGasCostWithNegativeSecondInputs() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.calculateTotal(1, -1, 1));
    }

    @Test
    public void calculateGasCostWithNegativeFirstInput() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.calculateTotal(-1, 1, 1));
    }

    @Test
    public void calculateGasCostWithNegativeThirdInput() {
        Assertions.assertThrows(GasCost.InvalidGasException.class, () -> GasCost.calculateTotal(1, 1, -1));
    }

    @Test
    public void calculateGasCostBeyondMaxGas() throws GasCost.InvalidGasException {
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.calculateTotal(1, Long.MAX_VALUE, 1));
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.calculateTotal(1, Long.MAX_VALUE, 1));
        Assertions.assertEquals(Long.MAX_VALUE, GasCost.calculateTotal(Long.MAX_VALUE, 1, 1));
    }
}
