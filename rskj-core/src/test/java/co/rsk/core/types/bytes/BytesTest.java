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
import org.ethereum.TestUtils;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BytesTest {

    @Test
    void testBytesOf() {
        assertNull(Bytes.of(null));
        assertNotNull(Bytes.of(new byte[]{}));
        assertNotNull(Bytes.of(new byte[]{1}));
    }

    @Test
    void testBytesLength() {
        assertEquals(0, Bytes.of(new byte[]{}).length());
        assertEquals(1, Bytes.of(new byte[]{1}).length());
    }

    @Test
    void testBytesAt() {
        assertThrows(IndexOutOfBoundsException.class, () -> Bytes.of(new byte[]{}).byteAt(0));
        assertThrows(IndexOutOfBoundsException.class, () -> Bytes.of(new byte[]{1}).byteAt(1));
        assertThrows(IndexOutOfBoundsException.class, () -> Bytes.of(new byte[]{1}).byteAt(-1));
        assertEquals(1, Bytes.of(new byte[]{1}).byteAt(0));
        assertEquals(2, Bytes.of(new byte[]{1,2}).byteAt(1));
    }

    @Test
    void testBytesArraycopy() {
        checkArraycopy((src, srcPos, dest, destPos, length) -> Bytes.of((byte[]) src).arraycopy(srcPos, (byte[]) dest, destPos, length));
    }

    @Test
    void testBytesArraycopyMimicsSystemOne() {
        checkArraycopy(System::arraycopy);
    }

    @Test
    void testCopyArrayOfRange() {
        checkCopyOfRange((original, from, to) -> Bytes.of(original).copyArrayOfRange(from, to));
    }

    @Test
    void testCopyArrayOfRangeMimicsSystemOne() {
        checkCopyOfRange(Arrays::copyOfRange);
    }

    @Test
    void testToPrintableString() {
        assertEquals("0a", Bytes.toPrintableString(new byte[]{10}));
    }

    @Test
    void testToPrintableStringWithNull() {
        assertEquals("<null>", Bytes.toPrintableString((byte[]) null));
    }

    @Test
    void testToPrintableStringWithDefaultValue() {
        assertEquals("xyz", Bytes.toPrintableString(null, "xyz"));
    }

    @Test
    void testAsUnsafeByteArray() {
        byte[] bArray = {1, 2, 3};
        assertTrue(bArray == Bytes.of(bArray).asUnsafeByteArray());
    }

    @Test
    void testEqualBytes() {
        byte[] b1Array = {1, 2, 3};
        byte[] b2Array = {1, 2, 3};
        assertTrue(Bytes.equalBytes(Bytes.of(b1Array), Bytes.of(b2Array)));
    }

    @Test
    void testBytesToString() {
        byte[] bArray = new byte[]{1, 2, 3, 5, 8, 13};
        String actualMessage = String.format("Some '%s' hex", Bytes.of(bArray));
        String expectedMessage = "Some '" + ByteUtil.toHexString(bArray) + "' hex";

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void testShortEnoughBytesToString() {
        byte[] bArray = TestUtils.generateBytes("hash",32);

        String actualMessage = String.format("Some '%s' hex", Bytes.of(bArray));
        String expectedMessage = "Some '" + ByteUtil.toHexString(bArray) + "' hex";

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void testLongBytesToString() {
        byte[] bArray1 = TestUtils.generateBytes("hash1",16);
        byte[] bArray2 = TestUtils.generateBytes("hash2",15);
        byte[] finalArray = ByteUtil.merge(bArray1, new byte[]{1, 2, 3}, bArray2);

        assertEquals(34, finalArray.length);

        Bytes bytes = Bytes.of(finalArray);

        assertEquals(64, String.format("%s", bytes).length());

        String actualMessage = String.format("Some '%s' hex", bytes);
        String expectedMessage = "Some '"
                + ByteUtil.toHexString(bArray1)
                + ".."
                + ByteUtil.toHexString(bArray2)
                + "' hex";

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void testNullBytesToString() {
        byte[] bArray = null;
        String actualMessage = String.format("Some '%s' hex", Bytes.of(bArray));
        String expectedMessage = "Some '" + null + "' hex";

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void testEmptyBytesToString() {
        byte[] bArray = new byte[]{};
        String actualMessage = String.format("Some '%s' hex", Bytes.of(bArray));
        String expectedMessage = "Some '' hex";

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void testEqualByteSlices() {
        byte[] b1Array = {1, 2, 3, 4, 5};
        byte[] b2Array = {1, 2, 3, 4, 5};
        byte[] b3Array = {1, 2, 3, 2, 1};
        byte[] emptyArray = {};

        // Both null
        assertTrue(Bytes.equalByteSlices(null, null));

        // One null, one non-null
        assertFalse(Bytes.equalByteSlices(Bytes.of(b1Array), null));
        assertFalse(Bytes.equalByteSlices(null, Bytes.of(b1Array)));

        // Both empty
        assertTrue(Bytes.equalByteSlices(Bytes.of(emptyArray), Bytes.of(emptyArray)));

        // Different lengths
        assertFalse(Bytes.equalByteSlices(Bytes.of(b1Array), Bytes.of(new byte[]{1, 2, 3, 4})));

        // Same length, different content
        assertFalse(Bytes.equalByteSlices(Bytes.of(b1Array), Bytes.of(b3Array)));

        // Slices of Bytes
        assertTrue(Bytes.equalByteSlices(Bytes.of(b1Array).slice(0, b1Array.length), Bytes.of(b2Array)));
        assertFalse(Bytes.equalByteSlices(Bytes.of(b1Array).slice(0, b1Array.length), Bytes.of(b3Array).slice(0, b3Array.length)));
    }

    private static void checkArraycopy(Functions.Action5<Object, Integer, Object, Integer, Integer> fun) {
        /*
            'fun' signature:
            @src – the source array.
            @srcPos – starting position in the source array.
            @dest – the destination array.
            @destPos – starting position in the destination data.
            @length – the number of array elements to be copied.
        */

        byte[] dest = new byte[5];
        byte[] origin = new byte[]{1,2,3,4,5};

        assertThrows(NullPointerException.class, () -> fun.apply(origin, 0, null, 0, 5));

        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, -1, dest, 0, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 0, dest, -1, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 0, dest, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 0, dest, 0, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 1, dest, 0, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(origin, 0, dest, 1, 5));

        assertArrayEquals(new byte[5], dest); // yet unmodified

        fun.apply(origin, 0, dest, 0, 5);
        assertArrayEquals(new byte[]{1,2,3,4,5}, dest);

        byte[] dest2 = new byte[5];
        fun.apply(origin, 1, dest2, 1, 3);
        assertArrayEquals(new byte[]{0,2,3,4,0}, dest2);
    }

    private static void checkCopyOfRange(Functions.Function3<byte[], Integer, Integer, byte[]> fun) {
        /*
            'fun' signature:
            @original – the array from which a range is to be copied
            @from – the initial index of the range to be copied, inclusive
            @to – the final index of the range to be copied, exclusive. (This index may lie outside the array.)

            @return a new array containing the specified range from the original array, truncated or padded with zeros
            to obtain the required length
        */

        byte[] bArray =  new byte[]{1, 2, 3, 4, 5};

        assertEquals(bArray.length, fun.apply(bArray, 0, 5).length);
        assertNotSame(bArray, fun.apply(bArray, 0, 5));

        assertArrayEquals(new byte[]{2, 3, 4}, fun.apply(bArray, 1, 4));
        assertArrayEquals(new byte[]{2}, fun.apply(bArray, 1, 2));
        assertArrayEquals(new byte[]{2, 3, 4, 5, 0, 0, 0}, fun.apply(bArray, 1, 8));
        assertArrayEquals(new byte[]{}, fun.apply(bArray, 3, 3));

        assertThrows(IllegalArgumentException.class, () -> fun.apply(bArray, 1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(bArray, -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> fun.apply(bArray, 6, 6));
    }
}
