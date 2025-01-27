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

package co.rsk.core.types.bytes;

public class BoundaryUtils {

    private BoundaryUtils() { /* hidden */ }

    public static void checkArraycopyParams(int srcLength, int srcPos, byte[] dest, int destPos, int length) {
        if (length < 0) {
            throw new IndexOutOfBoundsException("invalid 'length': " + length);
        }
        if (srcPos < 0 || Long.sum(srcPos, length) > srcLength) {
            throw new IndexOutOfBoundsException("invalid 'srcPos' and/or 'length': [" + srcPos + ";" + length + ")");
        }
        if (destPos < 0 || Long.sum(destPos, length) > dest.length) {
            throw new IndexOutOfBoundsException("invalid 'destPos' and/or 'length': [" + destPos + ";" + length + ")");
        }
    }

    public static void checkArrayIndexParam(int srcLength, int index) {
        if (index < 0 || index >= srcLength) {
            throw new IndexOutOfBoundsException("invalid index: " + index);
        }
    }
}
