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

import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.junit.Assert;
import org.junit.Test;

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
}
