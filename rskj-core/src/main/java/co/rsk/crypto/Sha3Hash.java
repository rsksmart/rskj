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

package co.rsk.crypto;

import com.google.common.primitives.Ints;
import org.spongycastle.util.encoders.Hex;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * A Sha3Hash just wraps a byte[] so that equals and hashcode work correctly, allowing it to be used as keys in a
 * map. It also checks that the length is correct and provides a bit more type safety.
 */
public class Sha3Hash implements Serializable, Comparable<Sha3Hash> {
    private final byte[] bytes;
    public static final Sha3Hash ZERO_HASH = new Sha3Hash(new byte[32]);

    public Sha3Hash(byte[] rawHashBytes) {
        // If i check arguments validate transaction fails
//        checkArgument(rawHashBytes.length == 32);
        this.bytes = rawHashBytes;
    }

    public Sha3Hash(String hexString) {
        if (hexString == null) {
            this.bytes = EMPTY_BYTE_ARRAY;
            return;
        }
        if (hexString.startsWith("0x")) {
            hexString = hexString.substring(2);
        }
        this.bytes = Hex.decode(hexString);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && getClass() == o.getClass() && Arrays.equals(bytes, ((Sha3Hash) o).bytes);
    }

    /**
     * Returns the last four bytes of the wrapped hash. This should be unique enough to be a suitable hash code even for
     * blocks, where the goal is to try and get the first bytes to be zeros (i.e. the value as a big integer lower
     * than the target value).
     */
    @Override
    public int hashCode() {
       return Arrays.hashCode(this.bytes);
    }

    @Override
    public String toString() {
        return bytes == null ? "" : Hex.toHexString(bytes);
    }

    /**
     * Returns the bytes interpreted as a positive integer.
     */
    public BigInteger toBigInteger() {
        return new BigInteger(1, bytes);
    }

    /**
     * Returns the internal byte array, without defensively copying. Therefore do NOT modify the returned array.
     */
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public int compareTo(Sha3Hash o) {
        for (int i = 32 - 1; i >= 0; i--) {
            final int thisByte = this.bytes[i] & 0xff;
            final int otherByte = o.bytes[i] & 0xff;
            if (thisByte > otherByte) {
                return 1;
            }

            if (thisByte < otherByte) {
                return -1;
            }
        }
        return 0;
    }
}
