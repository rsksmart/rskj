/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.vm;

/**
 * Created by ajlopez on 29/04/2017.
 */
public class BitSet {
    private byte[] bytes;
    private int size;

    public BitSet(int size) {
        if (size < 0) {
            throw new IllegalArgumentException(String.format("Negative size: %s", size));
        }

        this.size = size;
        int bsize = (size + 7) / 8;
        this.bytes = new byte[bsize];
    }

    public void set(int position) {
        if (position < 0 || position >= this.size) {
            throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", position, this.size));
        }
        
        int offset = position / 8;
        int bitoffset = position % 8;

        this.bytes[offset] |= 1 << bitoffset;
    }

    public boolean get(int position) {
        if (position < 0 || position >= this.size) {
            throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", position, this.size));
        }

        int offset = position / 8;
        int bitoffset = position % 8;

        return (this.bytes[offset] & 0xff & (1 << bitoffset)) != 0;
    }

    public int size() {
        return this.size;
    }
}
