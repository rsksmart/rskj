/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.signing;

import java.util.Arrays;

/**
 * Immutable plain byte array message
 *
 * @author Ariel Mendelzon
 */
public class PlainMessage extends Message {
    private final byte[] message;

    public PlainMessage(byte[] message) {
        // Save a copy
        this.message = copy(message);
    }

    @Override
    public byte[] getBytes() {
        return copy(message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        return Arrays.equals(this.message, ((PlainMessage) o).message);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(message);
    }

    private byte[] copy(byte[] a) {
        return Arrays.copyOf(a, a.length);
    }
}
