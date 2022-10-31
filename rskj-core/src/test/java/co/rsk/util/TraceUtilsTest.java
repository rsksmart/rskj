/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraceUtilsTest {

    @Test
    void testIdWillHaveMaxChars() {
        String test = "asdfñalsdkfjasñdlfkjañsldfjañslfjkasf";
        assertTrue(test.length() > TraceUtils.MAX_ID_LENGTH);
        String resultId = TraceUtils.toId(test);
        String expected = test.substring(0, TraceUtils.MAX_ID_LENGTH);
        assertEquals(TraceUtils.MAX_ID_LENGTH, resultId.length());
        assertEquals(expected, resultId);
    }

    @Test
    void idSmallerThanMaXSizeIsAccepted() {
        String test = "asdfasdf";
        assertTrue(test.length() < TraceUtils.MAX_ID_LENGTH);
        String resultId = TraceUtils.toId(test);
        assertEquals(test, resultId);
    }

    @Test
    void nullIsReturnedIfIdIsNull() {
        String id = TraceUtils.toId(null);
        assertNull(id);
    }

    @Test
    void randomIdHasMaxLength() {
        String randomId = TraceUtils.getRandomId();
        assertEquals(TraceUtils.MAX_ID_LENGTH, randomId.length());
    }

}