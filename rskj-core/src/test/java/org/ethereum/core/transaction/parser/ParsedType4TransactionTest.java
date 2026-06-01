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
package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ParsedType4Transaction} compact-constructor invariants.
 */
class ParsedType4TransactionTest {

    private static final byte CHAIN_ID = 33;
    private static final RskAddress RECEIVER =
            new RskAddress("0x0000000000000000000000000000000000000002");

    @Test
    void constructor_emptyAuthorizationList_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newParsedType4(List.of()));

        assertTrue(ex.getMessage().contains("authorization_list must not be empty"),
                "Expected empty authorization_list error, got: " + ex.getMessage());
    }

    @Test
    void constructor_nullAuthorizationList_throws() {
        assertThrows(NullPointerException.class, () -> newParsedType4(null));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ParsedType4Transaction newParsedType4(List<SetCodeAuthorization> authorizationList) {
        return new ParsedType4Transaction(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                new byte[]{0x01},
                BigInteger.valueOf(21_000).toByteArray(),
                RECEIVER,
                Coin.ZERO,
                new byte[0],
                new UnsignedSignature(CHAIN_ID),
                RLP.encodeList(),
                Coin.valueOf(10),
                Coin.valueOf(100),
                authorizationList);
    }
}
