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

package org.ethereum.rpc.converters;

import co.rsk.core.Coin;
import org.ethereum.rpc.TypeConverter;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by martin.medina on 3/7/17.
 */
public class TypeConverterTest {

    @Test
    public void stringToByteArray() {
        Assert.assertArrayEquals(new byte[] { 116, 105, 110, 99, 104, 111 }, TypeConverter.stringToByteArray("tincho"));
    }

    @Test
    public void stringHexToByteArrayStartsWithZeroX() {
        Assert.assertArrayEquals(new byte[] { 32 }, TypeConverter.stringHexToByteArray("0x20"));
    }

    @Test
    public void stringHexToByteArrayLengthNotModTwo() {
        Assert.assertArrayEquals(new byte[] { 2 }, TypeConverter.stringHexToByteArray("0x2"));
    }

    @Test
    public void stringHexToByteArray() {
        Assert.assertArrayEquals(new byte[] { 32 }, TypeConverter.stringHexToByteArray("20"));
    }

    @Test
    public void toJsonHex() {
        Assert.assertEquals("0x20", TypeConverter.toJsonHex(new byte[] { 32 }));
    }

    @Test
    public void toJsonHexNullInput() {
        Assert.assertEquals("0x00", TypeConverter.toJsonHex((byte[])null));
    }

    @Test
    public void toUnformattedJsonHex() {
        Assert.assertEquals("0x20", TypeConverter.toUnformattedJsonHex(new byte[] { 0x20 }));
    }

    @Test
    public void toUnformattedJsonHex_nullArray() {
        Assert.assertEquals("0x", TypeConverter.toUnformattedJsonHex(null));
    }

    @Test
    public void toUnformattedJsonHex_empty() {
        Assert.assertEquals("0x", TypeConverter.toUnformattedJsonHex(new byte[0]));
    }

    @Test
    public void toUnformattedJsonHex_twoHex() {
        Assert.assertEquals("0x02", TypeConverter.toUnformattedJsonHex(new byte[] {0x2}));
    }

    @Test
    public void toQuantityJsonHex() {
        byte[] toEncode = new byte[]{0x0A};
        Assert.assertEquals("0xa", TypeConverter.toQuantityJsonHex(toEncode));
    }

    @Test
    public void toQuantityJsonHex_Zero() {
        byte[] toEncode = new byte[]{0x00, 0x00};
        Assert.assertEquals("0x0", TypeConverter.toQuantityJsonHex(toEncode));
    }

    @Test
    public void toQuantityJsonHex_EmptyByteArray() {
        byte[] toEncode = new byte[0];
        Assert.assertEquals("0x0", TypeConverter.toQuantityJsonHex(toEncode));
    }

    @Test
    public void toJsonHexCoin() {
        Assert.assertEquals("1234", TypeConverter.toJsonHex(new Coin(new BigInteger("1234"))));
    }

    @Test
    public void toJsonHexNullCoin() {
        Assert.assertEquals("", TypeConverter.toJsonHex((Coin) null));
    }


    @Test
    public void stringHexToBigIntegerDefaultCase() {
        Assert.assertEquals(new BigInteger("1"), TypeConverter.stringHexToBigInteger("0x1"));
    }

    @Test
    public void stringHexToBigIntegerDefaultCase2() {
        Assert.assertEquals(new BigInteger("255"), TypeConverter.stringHexToBigInteger("0xff"));
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenThereIsNoNumber() {
        TypeConverter.stringHexToBigInteger("0x");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItIsNotHexa() {
        TypeConverter.stringHexToBigInteger("0xg");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItHasLessThanTwoCharacters() {
        TypeConverter.stringHexToBigInteger("0");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItIsEmpty() {
        TypeConverter.stringHexToBigInteger("");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItDoesNotStartWith0x() {
        TypeConverter.stringHexToBigInteger("0d99");
    }
}
