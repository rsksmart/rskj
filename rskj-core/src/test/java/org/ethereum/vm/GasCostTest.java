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
    public void toGas() {
        Assert.assertEquals(0, GasCost.toGas(new byte[0]));
        Assert.assertEquals(1, GasCost.toGas(new byte[] { 0x01 }));
        Assert.assertEquals(255, GasCost.toGas(new byte[] { (byte)0xff }));
        Assert.assertEquals(
                Long.MAX_VALUE, GasCost.toGas(BigInteger.valueOf(Long.MAX_VALUE).toByteArray())
        );
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void toGasOverflowsSlightly() {
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


    @Test(expected = GasCost.InvalidGasException.class)
    public void toGasGivesNegativeValue() throws GasCost.InvalidGasException {
        byte[] negativeBytes = new byte[]{
                (byte)255, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)255, (byte)255, (byte)255,
        };
        GasCost.toGas(negativeBytes);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void toGasArrayToBig() throws GasCost.InvalidGasException {
        byte[] bigArray = new byte[]{
                (byte)255, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)255, (byte)255, (byte)255,
        };
        GasCost.toGas(bigArray);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void toGasBoundedGivesNegativeValue() {
        byte[] negativeBytes = new byte[]{
                (byte)255, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)255, (byte)255, (byte)255,
        };
        GasCost.toGas(negativeBytes);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void toGasFromLongWithNegativeLong() {
        GasCost.toGas(-1L);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void toGasFromOverflowedLong() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.toGas(Long.MAX_VALUE + 1));
    }

    @Test
    public void toGasFromLong() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.toGas(Long.MAX_VALUE));
        Assert.assertEquals(123L, GasCost.toGas(123L));
    }

    @Test
    public void toGasWithBigInteger() {
        BigInteger bi = BigInteger.valueOf(Long.MAX_VALUE - 10);
        Assert.assertEquals(Long.MAX_VALUE - 10, GasCost.toGas(bi));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void toGasWithBigIntegerOverflowing() {
        BigInteger bi = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(100));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.toGas(bi));
    }

    @Test
    public void calculateAddGas() {
        Assert.assertEquals(1, GasCost.add(1, 0));
        Assert.assertEquals(2, GasCost.add(1, 1));
        Assert.assertEquals(1000000, GasCost.add(500000, 500000));
    }

    @Test
    public void calculateAddGasBoundedWithOverflow() throws GasCost.InvalidGasException {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.add(Long.MAX_VALUE, 1));
    }


    @Test
    public void calculateAddGasWithOverflow() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.add(Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.add(1, Long.MAX_VALUE));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateAddGasCostWithSecondNegativeInput() throws GasCost.InvalidGasException {
        GasCost.add(-1, 0);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateAddGasCostWithFirstNegativeInput() throws GasCost.InvalidGasException {
        GasCost.add(0, -1);
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateAddBoundedGasCostWithNegativeInput() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.add(-1, 0));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.add(0, -1));
    }

    @Test
    public void calculateSubtractGasCost() {
        Assert.assertEquals(1, GasCost.subtract(1, 0));
        Assert.assertEquals(0, GasCost.subtract(1, 1));
        Assert.assertEquals(1000000, GasCost.subtract(1500000, 500000));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void slightlyNegativeBItoGas() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.toGas(BigInteger.valueOf(-1)));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void moreNegativeBiToGas() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.toGas(BigInteger.valueOf(-3512)));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void mostNegativeBiToGas() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.toGas(BigInteger.valueOf(-99999999999999L)));

    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void calculateSubstractWithNegativeInput() throws GasCost.InvalidGasException {
        GasCost.subtract(1, -1);
    }

    @Test
    public void calculateSubtractWithExtremelyNegativeResult() throws GasCost.InvalidGasException {
        Assert.assertEquals(0, GasCost.subtract(0, Long.MAX_VALUE));
    }

    @Test
    public void calculateSubtractGasToInvalidSubtle() throws GasCost.InvalidGasException {
        Assert.assertEquals(0, GasCost.subtract(1, 2));
    }

    @Test
    public void calculateSubtractGasToInvalidObvious() throws GasCost.InvalidGasException  {
        Assert.assertEquals(0, GasCost.subtract(1, 159));
    }

    @Test()
    public void calculateSubtractBoundedGasCostToMaxGasIfNegative() {
        Assert.assertEquals(0, GasCost.subtract(1, 2));
        Assert.assertEquals(0, GasCost.subtract(1, 10));
    }

    @Test
    public void calculateAddGasCostBeyondMaxGas() throws GasCost.InvalidGasException {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.add(Long.MAX_VALUE, 1));
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

    @Test
    public void multiplyOverflowing() throws GasCost.InvalidGasException {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.multiply(4611686018427387903L, 4096L));
    }

    @Test(expected = GasCost.InvalidGasException.class)
    public void multiplyWithNegativeInput() throws GasCost.InvalidGasException {
        GasCost.multiply(1, -9123);
    }

    @Test
    public void multiply() {
        long x = (long) Math.pow(2, 62);
        long y = (long) Math.pow(2, 12);
        long overflowed = GasCost.multiply(x, y);
        Assert.assertEquals("overflowed is coverted to max gas", Long.MAX_VALUE, overflowed);
    }

    @Test
    public void calculateAddUnsafeGasCostBeyondMaxGas() {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.add(Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.add(1, Long.MAX_VALUE));
    }

    @Test
    public void calculateGasCost() throws GasCost.InvalidGasException {
        Assert.assertEquals(1, GasCost.calculate(1, 0, 0));
        Assert.assertEquals(2, GasCost.calculate(0, 2, 1));
        Assert.assertEquals(7, GasCost.calculate(1, 2, 3));
        Assert.assertEquals(10, GasCost.calculate(4, 3, 2));
        Assert.assertEquals(GasCost.CREATE + 100 * GasCost.CREATE_DATA, GasCost.calculate(GasCost.CREATE, GasCost.CREATE_DATA, 100));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculate(1, Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculate(Long.MAX_VALUE, Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculate(0, Long.MAX_VALUE, 2));
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
    public void calculateGasCostBeyondMaxGas() throws GasCost.InvalidGasException {
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculate(1, Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculate(1, Long.MAX_VALUE, 1));
        Assert.assertEquals(Long.MAX_VALUE, GasCost.calculate(Long.MAX_VALUE, 1, 1));
    }
}
