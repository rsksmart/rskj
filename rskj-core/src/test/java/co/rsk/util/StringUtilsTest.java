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

    private static final String STR_64_CHARS = "ee5c851e70650111887bb6c04e18ef4353391abe37846234c17895a9ca2b33d5";
    private static final String STR_65_CHARS = STR_64_CHARS + "a";

    @Test
    void testTrim() {
        assertNull(StringUtils.trim(null));
        assertEquals("", StringUtils.trim(""));
        assertEquals("a", StringUtils.trim("a"));

        assertEquals(64, STR_64_CHARS.length());
        assertEquals(65, STR_65_CHARS.length());

        assertEquals(STR_64_CHARS, StringUtils.trim(STR_64_CHARS));
        assertEquals(STR_64_CHARS + "...", StringUtils.trim(STR_65_CHARS));
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
