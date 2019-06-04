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

public final class Uint24 implements Comparable<Uint24> {
    private static final int MAX_VALUE_INT = 0x00ffffff;
    public static final int SIZE = 24;
    public static final int BYTES = SIZE / Byte.SIZE;
    public static final Uint24 ZERO = new Uint24(0);
    public static final Uint24 MAX_VALUE = new Uint24(MAX_VALUE_INT);

    private final int intValue;

    public Uint24(int intValue) {
        if (intValue < 0 || intValue > MAX_VALUE_INT) {
            throw new IllegalArgumentException("The supplied value doesn't fit in a Uint24");
        }

        this.intValue = intValue;
    }

    public byte[] encode() {
        byte[] bytes = new byte[BYTES];
        bytes[2] = (byte) (intValue);
        bytes[1] = (byte) (intValue >>>  8);
        bytes[0] = (byte) (intValue >>> 16);
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

        Uint24 uint24 = (Uint24) o;
        return intValue == uint24.intValue;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(intValue);
    }

    @Override
    public int compareTo(Uint24 o) {
        return Integer.compare(intValue, o.intValue);
    }

    @Override
    public String toString() {
        return Integer.toString(intValue);
    }

    public static Uint24 decode(byte[] bytes, int offset) {
        int intValue =  (bytes[offset + 2] & 0xFF)        +
                       ((bytes[offset + 1] & 0xFF) <<  8) +
                       ((bytes[offset    ] & 0xFF) << 16);
        return new Uint24(intValue);
    }
}
