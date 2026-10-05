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

public record LegacyPrefix() implements TransactionTypePrefix {
    @Override public TransactionType type()     { return TransactionType.LEGACY; }
    @Override public boolean isLegacy()         { return true;  }
    @Override public boolean isTyped()          { return false; }
    @Override public boolean isRskNamespace()   { return false; }
    @Override public byte[] toBytes()           { return new byte[0]; }
    @Override public int length()               { return 0; }
    @Override public String toFullString()      { return "0x00"; }
    @Override public String toRpcString()       { return "0x0"; }

    @Override public String toString() {
        return "Legacy";
    }
}
