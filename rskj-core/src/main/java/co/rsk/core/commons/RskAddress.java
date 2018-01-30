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

package co.rsk.core.commons;

import com.google.common.primitives.UnsignedBytes;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Immutable representation of an RSK address.
 * It is a simple wrapper on the raw byte[].
 *
 * @author Ariel Mendelzon
 */
public class RskAddress {

    /**
     * This is the size of an RSK address in bytes.
     */
    private static final int LENGTH_IN_BYTES = 20;

    private static final RskAddress NULL_ADDRESS = new RskAddress(new byte[LENGTH_IN_BYTES]);

    /**
     * This compares using the lexicographical order of the sender unsigned bytes.
     */
    public static final Comparator<RskAddress> LEXICOGRAPHICAL_COMPARATOR = Comparator.comparing(
            RskAddress::getBytes,
            UnsignedBytes.lexicographicalComparator());

    private final byte[] bytes;

    /**
     * @param address a data word containing an address in the last 20 bytes.
     */
    public RskAddress(DataWord address) {
        this(address.getLast20Bytes());
    }

    /**
     * @param address the hex-encoded 20 bytes long address, with or without 0x prefix.
     */
    public RskAddress(String address) {
        this(TypeConverter.stringHexToByteArray(address));
    }

    /**
     * @param bytes the 20 bytes long raw address bytes.
     */
    public RskAddress(byte[] bytes) {
        if (bytes.length != LENGTH_IN_BYTES) {
            throw new RuntimeException(String.format("An RSK address must be %d bytes long", LENGTH_IN_BYTES));
        }

        this.bytes = bytes;
    }

    /**
     * @return the null address, which is the receiver of contract creation transactions.
     */
    public static RskAddress nullAddress() {
        return NULL_ADDRESS;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        RskAddress otherSender = (RskAddress) other;
        return Arrays.equals(bytes, otherSender.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return Hex.toHexString(bytes);
    }
}
