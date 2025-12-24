/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

package org.ethereum.core;

// RLP Header Field Indices (fixed positions in the RLP list)
public enum BlockHeaderIndex {
    PARENT_HASH(0),
    UNCLES_HASH(1),
    COINBASE(2),
    STATE_ROOT(3),
    TX_TRIE_ROOT(4),
    RECEIPT_TRIE_ROOT(5),
    EXTENSION_DATA(6),
    DIFFICULTY(7),
    NUMBER(8),
    GAS_LIMIT(9),
    GAS_USED(10),
    TIMESTAMP(11),
    EXTRA_DATA(12),
    PAID_FEES(13),
    MINIMUM_GAS_PRICE(14),
    UNCLE_COUNT(15);

    private final int index;

    BlockHeaderIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
