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

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

    @Test
    void equals_hashCode_andToString_coverValueSemantics() throws Exception {
        SetCodeAuthorization auth = Rskip545TestSupport.minimalAuthorization(CHAIN_ID);
        ParsedType4Transaction left = newParsedType4(List.of(auth));
        ParsedType4Transaction right = newParsedType4(List.of(auth));

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertFalse(left.equals("other"));
        assertNotEquals(left, withNonce(right, new byte[]{0x02}));
        assertEquals(Coin.valueOf(10), left.maxPriorityFeePerGas());
        assertEquals(Coin.valueOf(100), left.maxFeePerGas());
        assertTrue(left.toString().contains("ParsedType4Transaction"));

        Method toHex = ParsedType4Transaction.class.getDeclaredMethod("toHex", byte[].class);
        toHex.setAccessible(true);
        assertEquals("null", toHex.invoke(null, (Object) null));
    }

    @Test
    void byteAccessors_returnDefensiveCopies() {
        SetCodeAuthorization auth = Rskip545TestSupport.minimalAuthorization(CHAIN_ID);
        ParsedType4Transaction parsed = newParsedType4(List.of(auth));

        parsed.nonce()[0] ^= 0x01;
        parsed.gasLimit()[0] ^= 0x01;
        parsed.data()[0] ^= 0x01;

        assertNotSame(parsed.nonce(), parsed.nonce());
        assertEquals(newParsedType4(List.of(auth)), parsed);
    }

    @Test
    void accept_dispatchesToVisitor() {
        ParsedType4Transaction parsed = newParsedType4(List.of(Rskip545TestSupport.minimalAuthorization(CHAIN_ID)));

        assertEquals("type4", parsed.accept(new ParsedRawTransactionVisitor<>() {
            @Override
            public String visitType0(ParsedType0Transaction transaction) {
                return "type0";
            }

            @Override
            public String visitType1(ParsedType1Transaction transaction) {
                return "type1";
            }

            @Override
            public String visitType2(ParsedType2Transaction transaction) {
                return "type2";
            }

            @Override
            public String visitType2Rsk(ParsedType2RSKTransaction transaction) {
                return "type2rsk";
            }

            @Override
            public String visitType4(ParsedType4Transaction transaction) {
                return "type4";
            }
        }));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ParsedType4Transaction withNonce(ParsedType4Transaction base, byte[] nonce) {
        return new ParsedType4Transaction(
                base.typePrefix(),
                nonce,
                base.gasLimit(),
                base.receiveAddress(),
                base.value(),
                base.data(),
                base.signatureState(),
                base.accessListBytes(),
                base.maxPriorityFeePerGas(),
                base.maxFeePerGas(),
                base.authorizationList());
    }

    private static ParsedType4Transaction newParsedType4(List<SetCodeAuthorization> authorizationList) {
        return new ParsedType4Transaction(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                new byte[]{0x01},
                BigInteger.valueOf(21_000).toByteArray(),
                RECEIVER,
                Coin.ZERO,
                new byte[]{0x01},
                new UnsignedSignature(CHAIN_ID),
                RLP.encodeList(),
                Coin.valueOf(10),
                Coin.valueOf(100),
                authorizationList);
    }
}
