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

import org.ethereum.core.transaction.TransactionType;

import java.util.Objects;

/** Standard typed transaction: {@code type || rlpPayload}. */
public record StandardTypedPrefix(TransactionType type) implements TransactionTypePrefix {
    public StandardTypedPrefix {
        Objects.requireNonNull(type, "type must not be null");
        if (type == TransactionType.LEGACY) {
            throw new IllegalArgumentException(
                    "Use TransactionTypePrefix.legacy() for legacy transactions");
        }
    }

    @Override public boolean isLegacy()       { return false; }
    @Override public boolean isTyped()        { return true;  }
    @Override public boolean isRskNamespace() { return false; }
    @Override public byte[] toBytes()         { return new byte[]{ type.getByteCode() }; }
    @Override public int length()             { return 1; }

    @Override
    public String toFullString() {
        return String.format("0x%02x", type.getByteCode());
    }

    @Override
    public String toRpcString() {
        return "0x" + Long.toHexString(type.getByteCode() & 0xFF);
    }

    @Override
    public String toString() {
        return "StandardTyped[" + type.getTypeName() + "]";
    }
}
