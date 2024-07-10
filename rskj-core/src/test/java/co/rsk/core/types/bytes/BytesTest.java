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

import org.ethereum.TestUtils;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BytesTest {

    @Test
    void testBytesOf() {
        assertNull(Bytes.of(null));
        assertNotNull(Bytes.of(new byte[]{}));
        assertNotNull(Bytes.of(new byte[]{1}));
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
        byte[] bArray1 = TestUtils.generateBytes("hash1",15);
        byte[] bArray2 = TestUtils.generateBytes("hash2",15);
        byte[] finalArray = ByteUtil.merge(bArray1, new byte[]{1, 2, 3}, bArray2);

        assertEquals(33, finalArray.length);

        String actualMessage = String.format("Some '%s' hex", Bytes.of(finalArray));
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
}