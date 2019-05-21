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

package co.rsk.core.types.ints;

public final class Uint16 {
    public static final int SIZE = 16;
    public static final int BYTES = SIZE / Byte.SIZE;

    private Uint16() {
        // until further functionality is needed, this will be a helper class
    }

    public static int decodeToInt(byte[] bytes, int offset) {
        return  (bytes[offset + 1] & 0xFF)       +
               ((bytes[offset    ] & 0xFF) << 8);
    }
}
