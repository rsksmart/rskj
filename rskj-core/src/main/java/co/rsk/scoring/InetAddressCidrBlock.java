/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.scoring;

import com.google.common.annotations.VisibleForTesting;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;

/**
 * InetAddressBlock represents a range of InetAddress as in CIDR subnet
 */
public class InetAddressCidrBlock {
    private final String description;
    private final byte[] bytes;

    // new algorithm data
    private final int mask;
    private final int maskBytesCount;

    /**
     * Creates an InetAddressBlock given an address and a cidr
     *
     * @param address the address
     * @param cidr    the cidr
     */
    InetAddressCidrBlock(InetAddress address, int cidr) {
        if (address instanceof Inet4Address && (cidr < 0 || cidr > 32)) {
            throw new IllegalArgumentException("invalid cidr for ipV4: " + cidr);
        }

        if (address instanceof Inet6Address && (cidr < 0 || cidr > 128)) {
            throw new IllegalArgumentException("invalid cidr for ipV6: " + cidr);
        }

        this.description = address.getHostAddress() + "/" + cidr;
        this.bytes = address.getAddress();
        this.mask = (byte) (0xFF00 >> (cidr & 0x07));
        this.maskBytesCount = cidr / 8;
    }

    /**
     * Returns if a given address is included or not in the subnet defined by the address block
     *
     * @param address the address to check
     * @return <tt>true</tt> if the address belongs to the subnet defined by the address block, false otherwise
     */
    public boolean contains(InetAddress address) {
        byte[] bytesToCheck = address.getAddress();

        if (bytesToCheck.length != this.bytes.length) {
            return false;
        }

        for (int i = 0; i < this.maskBytesCount; i++) {
            if (bytesToCheck[i] != this.bytes[i]) {
                return false;
            }
        }

        if (this.mask != 0) {
            return (bytesToCheck[this.maskBytesCount] & this.mask) == (this.bytes[this.maskBytesCount] & this.mask);
        }

        return true;
    }

    /**
     * Returns the string representation of the address block
     *
     * @return the string description of this block
     * ie "192.168.51.1/16"
     */
    public String getDescription() {
        return this.description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InetAddressCidrBlock that = (InetAddressCidrBlock) o;
        return mask == that.mask && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mask);
        result = 31 * result + Arrays.hashCode(bytes);
        return result;
    }

    @VisibleForTesting
    byte[] getBytes() {
        return this.bytes.clone();
    }

    @VisibleForTesting
    int getMask() {
        return mask;
    }
}
