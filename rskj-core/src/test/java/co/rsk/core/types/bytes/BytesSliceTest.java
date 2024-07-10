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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BytesSliceTest {

    @Test
    void testCopyArrayOfRange() {
        byte[] bArray =  new byte[]{1, 2, 3, 4, 5, 6};
        byte[] expectedResult =  new byte[]{3, 4, 5};
        assertArrayEquals(expectedResult, Bytes.of(bArray).slice(0, bArray.length).copyArrayOfRange(2, 5));
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
}
