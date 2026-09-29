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

package co.rsk.peg.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * These numbers go into {@code release_request_rejected} and therefore into the receipts trie, so
 * they are consensus. Every other test names the constant, which would keep passing if a value were
 * renumbered while the event changed for everyone reading the chain.
 */
class RejectedPegoutReasonTest {

    @Test
    void values_shouldKeepTheirNumbers() {
        assertEquals(1, RejectedPegoutReason.LOW_AMOUNT.getValue());
        assertEquals(2, RejectedPegoutReason.CALLER_CONTRACT.getValue());
        assertEquals(3, RejectedPegoutReason.FEE_ABOVE_VALUE.getValue());
        assertEquals(4, RejectedPegoutReason.UNSUPPORTED_ADDRESS_TYPE.getValue());
    }

    /** Appended, never inserted, so an existing value never shifts under a reader. */
    @Test
    void theNewValue_shouldBeTheLastOne() {
        RejectedPegoutReason[] values = RejectedPegoutReason.values();

        assertEquals(RejectedPegoutReason.UNSUPPORTED_ADDRESS_TYPE, values[values.length - 1]);
        assertEquals(4, values.length);
    }
}
