/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core;

/** RSK namespace transaction: {@code 0x02 || subtype || rlpPayload}. */
public record RskNamespacePrefix(byte subtype) implements TransactionTypePrefix {
    public RskNamespacePrefix {
        if ((subtype & 0xFF) > TransactionType.MAX_TYPE_VALUE) {
            throw new IllegalArgumentException(
                    "RSK subtype must be in [0x00, 0x7f], got: 0x"
                            + String.format("%02x", subtype & 0xFF));
        }
    }

    @Override public TransactionType type()   { return TransactionType.TYPE_2; }
    @Override public boolean isLegacy()       { return false; }
    @Override public boolean isTyped()        { return true;  }
    @Override public boolean isRskNamespace() { return true;  }

    @Override
    public byte[] toBytes() {
        return new byte[]{ TransactionType.RSK_NAMESPACE_PREFIX, subtype };
    }

    @Override public int length() { return 2; }

    @Override
    public String toFullString() {
        return String.format("0x%02x%02x", TransactionType.RSK_NAMESPACE_PREFIX, subtype);
    }

    @Override
    public String toRpcString() {
        return toFullString();
    }

    @Override
    public String toString() {
        return String.format("RskNamespace[subtype=0x%02x]", subtype);
    }
}
