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

import javax.annotation.Nullable;

public class StringUtils {

    private static final int DEFAULT_MAX_LEN = 64;

    public static String trim(@Nullable String src) {
        return trim(src, DEFAULT_MAX_LEN);
    }

    public static String trim(@Nullable String src, int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength: " + maxLength);
        }
        if (src == null || src.length() <= maxLength) {
            return src;
        }
        return src.substring(0, maxLength) + "...";
    }
}
