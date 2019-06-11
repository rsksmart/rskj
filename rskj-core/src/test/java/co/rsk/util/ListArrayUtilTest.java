/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ListArrayUtilTest {

    @Test
    public void testAsByteList() {
        byte[] array = new byte[]{'a','b','c','d'};

        List<Byte> result = ListArrayUtil.asByteList(array);

        for(int i = 0; i < array.length; i++) {
            Assert.assertEquals(array[i], result.get(i).byteValue());
        }
    }

    @Test
    public void testNullIsEmpty() {
        Assert.assertTrue(ListArrayUtil.isEmpty(null));
    }

    @Test
    public void testEmptyIsEmpty() {
        Assert.assertTrue(ListArrayUtil.isEmpty(new byte[]{}));
    }

    @Test
    public void testNotEmptyIsEmpty() {
        Assert.assertFalse(ListArrayUtil.isEmpty(new byte[]{'a'}));
    }

    @Test
    public void testNullToEmpty() {
        Assert.assertNotNull(ListArrayUtil.nullToEmpty(null));
    }

    @Test
    public void testNonNullToEmpty() {
        byte[] array = new byte[1];
        Assert.assertSame(array, ListArrayUtil.nullToEmpty(array));
    }

    @Test
    public void testNullGetLength() {
        Assert.assertEquals(0, ListArrayUtil.getLength(null));
    }

    @Test
    public void testNonNullGetLength() {
        byte[] array = new byte[1];
        Assert.assertEquals(1, ListArrayUtil.getLength(array));
    }
}
