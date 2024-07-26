/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

package co.rsk.core.types.bytes;

import co.rsk.util.Functions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BytesSliceTest {

    @Test
    void testBytesLength() {
        assertEquals(0, Bytes.of(new byte[]{}).slice(0, 0).length());
        assertEquals(0, Bytes.of(new byte[]{1}).slice(0, 0).length());
        assertEquals(1, Bytes.of(new byte[]{1}).slice(0, 1).length());
        assertEquals(0, Bytes.of(new byte[]{1,2,3}).slice(1, 1).length());
        assertEquals(1, Bytes.of(new byte[]{1,2,3}).slice(1, 2).length());
        assertEquals(2, Bytes.of(new byte[]{1,2,3}).slice(0, 2).length());
        assertEquals(3, Bytes.of(new byte[]{1,2,3}).slice(0, 3).length());
    }

    @Test
    void testBytesAt() {
        assertThrows(IndexOutOfBoundsException.class, () -> Bytes.of(new byte[]{}).slice(0, 0).byteAt(0));
        assertThrows(IndexOutOfBoundsException.class, () -> Bytes.of(new byte[]{1}).slice(0, 1).byteAt(1));
        assertThrows(IndexOutOfBoundsException.class, () -> Bytes.of(new byte[]{1}).slice(0, 1).byteAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> Bytes.of(new byte[]{1,2,3}).slice(1, 2).byteAt(1));
        assertEquals(1, Bytes.of(new byte[]{1}).slice(0, 1).byteAt(0));
        assertEquals(2, Bytes.of(new byte[]{1,2}).slice(0, 2).byteAt(1));
        assertEquals(2, Bytes.of(new byte[]{1,2,3}).slice(1, 2).byteAt(0));
        assertEquals(4, Bytes.of(new byte[]{1,2,3,4}).slice(2, 4).byteAt(1));
    }

    @Test
    void testBytesSliceArraycopy() {
        checkArraycopy((src, srcPos, dest, destPos, length) -> Bytes.of((byte[]) src).slice(1, 4).arraycopy(srcPos, (byte[]) dest, destPos, length));
    }

    @Test
    void testBytesSliceArraycopyMimicsSystemOne() {
        checkArraycopy((src, srcPos, dest, destPos, length) -> System.arraycopy(Arrays.copyOfRange((byte[]) src, 1, 4), srcPos, dest, destPos, length));
    }

    @Test
    void testCopyArrayOfRange() {
        checkCopyOfRange(BytesSlice::copyArrayOfRange, (origin, from, to) -> Bytes.of(origin).slice(from, to));
    }

    @Test
    void testCopyArrayOfRangeMimicsSystemOne() {
        checkCopyOfRange(Arrays::copyOfRange, Arrays::copyOfRange);
    }

    @Test
    void testCopyArray() {
        byte[] bArray =  new byte[]{1, 2, 3, 4, 5, 6};
        byte[] expectedResult =  new byte[]{3, 4, 5};
        byte[] actualResult = Bytes.of(bArray).slice(2, 5).copyArray();
        assertNotSame(expectedResult, actualResult); // refs are different
        assertArrayEquals(expectedResult, actualResult);
    }

    @Test
    void testCopyBytesOfRange() {
        byte[] bArray =  new byte[]{1, 2, 3, 4, 5, 6};
        Bytes expectedResult =  Bytes.of(new byte[]{3, 4, 5});
        assertTrue(Bytes.equalBytes(expectedResult, Bytes.of(bArray).slice(0, bArray.length).copyBytesOfRange(2, 5)));
    }

    @Test
    void testCopyBytes() {
        byte[] bArray =  new byte[]{1, 2, 3, 4, 5, 6};
        Bytes expectedResult = Bytes.of(new byte[]{3, 4, 5});
        Bytes actualResult = Bytes.of(bArray).slice(2, 5).copyBytes();
        assertTrue(Bytes.equalBytes(expectedResult, actualResult));
        assertArrayEquals(expectedResult.asUnsafeByteArray(), actualResult.asUnsafeByteArray());
    }

    @Test
    void testSlice() {
        byte[] bArray =  new byte[]{1, 2, 3, 4, 5, 6};
        BytesSlice actualResult = Bytes.of(bArray).slice(1, 6).slice(1, 4).slice(1, 3);
        byte[] expectedResult =  new byte[]{4, 5};
        assertEquals(2, actualResult.length());
        assertArrayEquals(expectedResult, actualResult.copyArray());
    }

    @Test
    void testEmptySlice() {
        byte[] bArray =  new byte[]{1, 2, 3, 4, 5, 6};
        BytesSlice actualResult = Bytes.of(bArray).slice(1, 6).slice(1, 4).slice(1, 3).slice(0, 0);
        byte[] expectedResult =  new byte[]{};
        assertEquals(0, actualResult.length());
        assertArrayEquals(expectedResult, actualResult.copyArray());
    }

    private static void checkArraycopy(Functions.Action5<Object, Integer, Object, Integer, Integer> fun) {
        byte[] dest = new byte[3];
        byte[] origin = new byte[]{1,2,3,4,5};

        assertThrows(NullPointerException.class, () -> fun.apply(origin, 0, null, 0, 3));

        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, -1, dest, 0, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 0, dest, -1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 0, dest, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 0, dest, 0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 1, dest, 0, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 0, dest, 1, 3));

        assertArrayEquals(new byte[3], dest); // yet unmodified

        fun.apply(origin, 0, dest, 0, 3);
        assertArrayEquals(new byte[]{2,3,4}, dest);

        byte[] dest2 = new byte[3];
        fun.apply(origin, 1, dest2, 1, 1);
        assertArrayEquals(new byte[]{0,3,0}, dest2);
    }

    private static <T> void checkCopyOfRange(Functions.Function3<T, Integer, Integer, byte[]> fun,
                                             Functions.Function3<byte[], Integer, Integer, T> slicer) {
        byte[] bArray =  new byte[]{1, 2, 3, 4, 5, 6};

        assertEquals(bArray.length, fun.apply(slicer.apply(bArray, 0, 6), 0, 6).length);
        assertNotSame(bArray, fun.apply(slicer.apply(bArray, 0, 6), 0, 6));

        assertArrayEquals(new byte[]{3, 4, 5}, fun.apply(slicer.apply(bArray, 0, 6), 2, 5));
        assertArrayEquals(new byte[]{3, 4}, fun.apply(slicer.apply(bArray, 1, 5), 1, 3));
        assertArrayEquals(new byte[]{3, 4, 5, 0, 0, 0, 0}, fun.apply(slicer.apply(bArray, 1, 5), 1, 8));
        assertArrayEquals(new byte[]{}, fun.apply(slicer.apply(bArray, 1, 5), 4, 4));

        assertThrows(IllegalArgumentException.class, () -> fun.apply(slicer.apply(bArray, 1, 5), 1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(slicer.apply(bArray, 1, 5), -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(slicer.apply(bArray, 1, 5), 5, 5));
    }
}
