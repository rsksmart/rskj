/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

class StringUtilsTest {

    private static final String STR_66_CHARS = "0xee5c851e70650111887bb6c04e18ef4353391abe37846234c17895a9ca2b33d5";
    private static final String STR_67_CHARS = STR_66_CHARS + "a";

    @Test
    void testTrim() {
        assertNull(StringUtils.trim(null));
        assertEquals("", StringUtils.trim(""));
        assertEquals("a", StringUtils.trim("a"));

        assertEquals(66, STR_66_CHARS.length());
        assertEquals(67, STR_67_CHARS.length());

        assertEquals(STR_66_CHARS, StringUtils.trim(STR_66_CHARS));
        assertEquals(STR_66_CHARS + "...", StringUtils.trim(STR_67_CHARS));
    }

    @Test
    void testTrimWithMaxLength() {
        assertEquals("...", StringUtils.trim("abc", 0));
        assertEquals("abc", StringUtils.trim("abc", 3));
        assertEquals("abc", StringUtils.trim("abc", 4));
        assertEquals("abc...", StringUtils.trim("abcd", 3));
    }

    @Test
    void testTrimWithInvalidMaxLength() {
        assertThrows(IllegalArgumentException.class, () -> StringUtils.trim("abc", -1));
    }
}
