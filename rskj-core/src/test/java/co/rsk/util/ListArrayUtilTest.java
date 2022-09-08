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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ListArrayUtilTest {

    @Test
    public void testAsByteList() {
        byte[] array = new byte[]{'a','b','c','d'};

        List<Byte> result = ListArrayUtil.asByteList(array);

        for(int i = 0; i < array.length; i++) {
            Assertions.assertEquals(array[i], result.get(i).byteValue());
        }
    }

    @Test
    public void testNullIsEmpty() {
        Assertions.assertTrue(ListArrayUtil.isEmpty(null));
    }

    @Test
    public void testEmptyIsEmpty() {
        Assertions.assertTrue(ListArrayUtil.isEmpty(new byte[]{}));
    }

    @Test
    public void testNotEmptyIsEmpty() {
        Assertions.assertFalse(ListArrayUtil.isEmpty(new byte[]{'a'}));
    }

    @Test
    public void testNullToEmpty() {
        Assertions.assertNotNull(ListArrayUtil.nullToEmpty(null));
    }

    @Test
    public void testNonNullToEmpty() {
        byte[] array = new byte[1];
        Assertions.assertSame(array, ListArrayUtil.nullToEmpty(array));
    }

    @Test
    public void testNullGetLength() {
        Assertions.assertEquals(0, ListArrayUtil.getLength(null));
    }

    @Test
    public void testNonNullGetLength() {
        byte[] array = new byte[1];
        Assertions.assertEquals(1, ListArrayUtil.getLength(array));
    }

    @Test
    public void testLastIndexOfSublistEmptyArrays() {
        byte[] source = new byte[] {};
        byte[] target = new byte[] {};

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals( 0, res);
    }

    @Test
    public void testLastIndexOfSublistSearchEmpty() {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        byte[] target = new byte[] { };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(5, res);
    }

    @Test
    public void testLastIndexOfSublistFindsMatch1() {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        byte[] target = new byte[] { 3, 4 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(2, res);
    }

    @Test
    public void testLastIndexOfSublistFindsMatch2() {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        byte[] target = new byte[] { 4, 5 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(3, res);
    }

    @Test
    public void testLastIndexOfSublistSameArray() {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        byte[] target = new byte[] { 1, 2, 3, 4, 5 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(0, res);
    }

    @Test
    public void testLastIndexOfSublistTargetLongerThanSource() {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        byte[] target = new byte[] { 1, 2, 3, 4, 5, 6 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(-1, res);
    }

    @Test
    public void testLastIndexOfSublistPartialOverlapOnBeginning() {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        byte[] target = new byte[] { 0, 1, 2 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(-1, res);
    }

    @Test
    public void testLastIndexOfSublistPartialOverlapOnEnd() {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        byte[] target = new byte[] { 4, 5, 6 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(-1, res);
    }

    @Test
    public void testLastIndexOfSublist6ArraysWithNoSharedElements() {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        byte[] target = new byte[] { 6, 7, 8, 9, 10 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(-1, res);
    }

    @Test
    public void testLastIndexOfSublistNullSource() {
        byte[] source = null;
        byte[] target = new byte[] { 6, 7, 8, 9, 10 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(-1, res);
    }

    @Test
    public void testLastIndexOfSublistNullTarget() {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        byte[] target = null;

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(-1, res);
    }

    @Test
    public void testLastIndexOfSublistMatchesSecondOcurrence() {
        byte[] source = new byte[] { 3, 4, 5, 3, 4, 5 };
        byte[] target = new byte[] { 3, 4, 5 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(3, res);
    }

    @Test
    public void testLastIndexOfSublistMatchesThirdOcurrence() {
        byte[] source = new byte[] { 1, 2, 3, 1, 2, 3, 1, 2, 3 };
        byte[] target = new byte[] { 1, 2, 3 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(6, res);
    }

    @Test
    public void testLastIndexOfSublistMatchesLastOcurrence() {
        byte[] source = new byte[] { 1, 2, 3, 3, 4, 5, 1, 2, 3 };
        byte[] target = new byte[] { 1, 2, 3 };

        int res = ListArrayUtil.lastIndexOfSubList(source, target);

        Assertions.assertEquals(6, res);
    }
}
