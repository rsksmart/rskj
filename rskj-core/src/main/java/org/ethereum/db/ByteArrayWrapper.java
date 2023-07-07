/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.db;

import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;

import java.io.Serializable;
import java.util.Arrays;

/**
 * @author Roman Mandeleil
 * @since 11.06.2014
 */
public class ByteArrayWrapper implements Comparable<ByteArrayWrapper>, Serializable {

    private static final long serialVersionUID = -2510179337207357675L;
    private final byte[] data;
    private int hashCode = 0;

    public ByteArrayWrapper(byte[] data) {
        if (data == null) {
            throw new NullPointerException("Data must not be null");
        }
        this.data = data;
        this.hashCode = Arrays.hashCode(data);
    }

    public boolean equals(Object other) {
        if (!(other instanceof ByteArrayWrapper)) {
            return false;
        }
        byte[] otherData = ((ByteArrayWrapper) other).data;
        return ByteUtil.fastEquals(data, otherData);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public int compareTo(ByteArrayWrapper o) {
        return FastByteComparisons.compareTo(
                data, 0, data.length,
                o.data, 0, o.data.length);
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return ByteUtil.toHexString(data);
    }

    public boolean equalsToByteArray(byte[] otherData) {
        return otherData != null && ByteUtil.fastEquals(data, otherData);
    }
}
