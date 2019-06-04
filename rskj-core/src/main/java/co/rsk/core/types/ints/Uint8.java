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

public final class Uint8 {
    private static final int MAX_VALUE_INT = 0xff;
    private static final int SIZE = 8;
    public static final int BYTES = SIZE / Byte.SIZE;
    public static final Uint8 MAX_VALUE = new Uint8(MAX_VALUE_INT);

    private final int intValue;

    public Uint8(int intValue) {
        if (intValue < 0 || intValue > MAX_VALUE_INT) {
            throw new IllegalArgumentException("The supplied value doesn't fit in a Uint8");
        }

        this.intValue = intValue;
    }

    public byte asByte() {
        return (byte) intValue;
    }

    public byte[] encode() {
        byte[] bytes = new byte[BYTES];
        bytes[0] = asByte();
        return bytes;
    }

    public int intValue() {
        return intValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Uint8 uint8 = (Uint8) o;
        return intValue == uint8.intValue;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(intValue);
    }

    @Override
    public String toString() {
        return Integer.toString(intValue);
    }

    public static Uint8 decode(byte[] bytes, int offset) {
        int intValue = bytes[offset] & 0xFF;
        return new Uint8(intValue);
    }
}
