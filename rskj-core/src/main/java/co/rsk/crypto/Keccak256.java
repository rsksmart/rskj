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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.util.Arrays;

import org.ethereum.util.ByteUtil;

import com.google.common.primitives.Ints;

import co.rsk.bitcoinj.core.Utils;
import co.rsk.util.HexUtils;

/**
 * A Keccak256 just wraps a byte[] so that equals and hashcode work correctly, allowing it to be used as keys in a
 * map. It also checks that the length is correct and provides a bit more type safety.
 */
public class Keccak256 implements Serializable, Comparable<Keccak256> {
    public static final int HASH_LEN = 32;
    public static final Keccak256 ZERO_HASH = new Keccak256(new byte[HASH_LEN]);
    private static final long serialVersionUID = -3076062998897837430L;

    private final byte[] bytes;

    public Keccak256(byte[] rawHashBytes) {
        checkArgument(rawHashBytes.length == HASH_LEN);
        this.bytes = rawHashBytes;
    }

    public Keccak256(String hexString) {
        checkArgument(hexString.length() == 2*HASH_LEN);
        this.bytes = Utils.HEX.decode(hexString);
    }

    public String toJsonString() {
        return HexUtils.toUnformattedJsonHex(this.bytes);
    }

    public String toHexString(boolean addPrefix) {
        String hexString = this.toHexString();
        return addPrefix ? String.format("%s%s", HexUtils.HEX_PREFIX, hexString) : hexString;
    }

    public String toHexString() {
        return ByteUtil.toHexString(bytes);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && getClass() == o.getClass() && Arrays.equals(bytes, ((Keccak256) o).bytes);
    }

    /**
     * Returns the last four bytes of the wrapped hash. This should be unique enough to be a suitable hash code even for
     * blocks, where the goal is to try and get the first bytes to be zeros (i.e. the value as a big integer lower
     * than the target value).
     */
    @Override
    public int hashCode() {
        // Use the last 4 bytes, not the first 4 which are often zeros in Bitcoin.
        return Ints.fromBytes(bytes[28], bytes[29], bytes[30], bytes[31]);
    }

    /**
     * @return a DEBUG representation of the hash, mainly used for logging.
     */
    @Override
    public String toString() {
        return toHexString();
    }

    /**
     * Returns the internal byte array, without defensively copying. Therefore do NOT modify the returned array.
     */
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * Returns an identical Sha3Hash with a copy of the the internal byte array.
     */
    public Keccak256 copy() {
        return new Keccak256(ByteUtil.cloneBytes(bytes));
    }

    @Override
    public int compareTo(Keccak256 o) {
        for (int i = HASH_LEN - 1; i >= 0; i--) {
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
