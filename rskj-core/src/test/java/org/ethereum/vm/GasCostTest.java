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

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 15/07/2019.
 */
public class GasCostTest {

    @Test
    public void toGasUnsafe() {
        Assert.assertEquals(0, GasCost.toGasBounded(new byte[0]));
        Assert.assertEquals(1, GasCost.toGasBounded(new byte[] { 0x01 }));
        Assert.assertEquals(255, GasCost.toGasBounded(new byte[] { (byte)0xff }));

        byte[] bytes = new byte[32];

        for (int k = 0; k < bytes.length; k++) {
            bytes[k] = (byte)0xff;
        }

        Assert.assertEquals(Long.MAX_VALUE, GasCost.toGasBounded(bytes));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void toGasOverflowsSlightly() throws GasCost.InvalidGasException {
        byte[] bytes = new byte[32];

        for (int k = 0; k < 17; k++) {
            bytes[k] = (byte)0x00;
        }
        bytes[17] = (byte)0x80;
        for (int k = 18; k < 32; k++) {
            bytes[k] = (byte)0x00;
        }
        GasCost.toGas(bytes);
    }

    @Test
    public void toGasBoundedWithBigInteger() {
        BigInteger bi = BigInteger.valueOf(Long.MAX_VALUE - 10);
        Assert.assertEquals(Long.MAX_VALUE - 10, GasCost.toGasBounded(bi));
    }

    @Test
    public void toGasBoundedWithBigIntegerOverflowing() {
        BigInteger bi = BigInteger.valueOf(Long.MAX_VALUE + 10);
        Assert.assertEquals(Long.MAX_VALUE, GasCost.toGasBounded(bi));
    }

    @Test
    public void calculateAddGasCost() {
        Assert.assertEquals(1, GasCost.addBounded(1, 0));
        Assert.assertEquals(2, GasCost.addBounded(1, 1));
        Assert.assertEquals(1000000, GasCost.addBounded(500000, 500000));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateAddGasCostWithOverflow() throws GasCost.InvalidGasException {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.add(Long.MAX_VALUE, 1));
    }


    @Test
    public void calculateAddUnsafeGasCostWithOverflow() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.addBounded(Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.addBounded(1, Long.MAX_VALUE));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateAddGasCostWithSecondNegativeInput() throws GasCost.InvalidGasException {
        GasCost.add(-1, 0);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateAddGasCostWithFirstNegativeInput() throws GasCost.InvalidGasException {
        GasCost.add(0, -1);
    }

    @Test
    public void calculateAddBoundedGasCostWithNegativeInput() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.addBounded(-1, 0));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.addBounded(0, -1));
    }


    @Test
    public void calculateSubtractGasCost() throws GasCost.InvalidGasException {
        Assert.assertEquals(1, GasCost.subtract(1, 0));
        Assert.assertEquals(0, GasCost.subtract(1, 1));
        Assert.assertEquals(1000000, GasCost.subtract(1500000, 500000));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateSubstractWithNegativeInput() throws GasCost.InvalidGasException {
        GasCost.subtract(1, -1);
    }


    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateSubtractGasToInvalidSubtle() throws GasCost.InvalidGasException {
        Assert.assertEquals(0, GasCost.subtract(1, 2));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateSubtractGasToInvalidObvious() throws GasCost.InvalidGasException  {
        Assert.assertEquals(0, GasCost.subtract(1, 159));
    }

    @Test()
    public void calculateSubtractBoundedGasCostToMaxGasIfNegative() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.subtractBounded(1, 2));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.subtractBounded(1, 10));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateAddGasCostBeyondMaxGas() throws GasCost.InvalidGasException {
        GasCost.add(Long.MAX_VALUE, 1);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void multiplyWithNegativeValues() throws GasCost.InvalidGasException {
        GasCost.multiply(-1, -2);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void multiplyWithXNegative() throws GasCost.InvalidGasException {
        GasCost.multiply(-1, 123);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void multiplyWithYNegative() throws GasCost.InvalidGasException {
        GasCost.multiply(1, -9123);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void multiplyOverflowing() throws GasCost.InvalidGasException {
        GasCost.multiply(4611686018427387903L, 4096L);
    }

    @Test
    public void multiplyBounded() throws GasCost.InvalidGasException {
        long negative = GasCost.multiplyBounded(1, -9123);
        long x = (long)Math.pow(2, 62);
        long y = (long)Math.pow(2, 12);
        long overflowed = GasCost.multiplyBounded(x, y);
        Assert.assertEquals("negative is converted to max gas", Long.MAX_VALUE, negative);
        Assert.assertEquals("overflowed is coverted to max gas", Long.MAX_VALUE, overflowed);
    }

    @Test
    public void calculateAddUnsafeGasCostBeyondMaxGas() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.addBounded(Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.addBounded(1, Long.MAX_VALUE));
    }

    @Test
    public void calculateGasCost() throws GasCost.InvalidGasException {
        Assert.assertEquals(1, GasCost.calculate(1, 0, 0));
        Assert.assertEquals(2, GasCost.calculate(0, 2, 1));
        Assert.assertEquals(7, GasCost.calculate(1, 2, 3));
        Assert.assertEquals(GasCost.CREATE + 100 * GasCost.CREATE_DATA, GasCost.calculate(GasCost.CREATE, GasCost.CREATE_DATA, 100));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateGasCostWithOverflow() throws GasCost.InvalidGasException  {
        GasCost.calculate(1, Long.MAX_VALUE, 1);
    }


    @Test
    public void calculateGasCostBoundedWithOverflow() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculateBounded(1, Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculateBounded(Long.MAX_VALUE, Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculateBounded(0, Long.MAX_VALUE, 2));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateGasCostWithNegativeSecondInputs() throws GasCost.InvalidGasException {
        GasCost.calculate(1, -1, 1);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateGasCostWithNegativeFirstInput() throws GasCost.InvalidGasException {
        GasCost.calculate(-1, 1, 1);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateGasCostWithNegativeThirdInput() throws GasCost.InvalidGasException {
        GasCost.calculate(1, 1, -1);
    }

    @Test
    public void calculateGasCostBoundedWithNegativeInputs() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculateBounded(1, -1, -1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculateBounded(1, -1, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculateBounded(-2, 1, 1));
        Assert.assertEquals(10, GasCost.calculateBounded(4, 3, 2));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateGasCostBeyondMaxGas() throws GasCost.InvalidGasException {
        GasCost.calculate(1, Long.MAX_VALUE, 1);
    }

    @Test
    public void calculateGasCostBoundedBeyondMaxGas() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculateBounded(1, Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculateBounded(Long.MAX_VALUE, 1, 1));
    }
}
