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
import org.ethereum.core.Rskip546TestSupport;
import org.ethereum.core.TransactionTypePrefix;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsedType1TransactionTest {

    private static final RskAddress RECEIVER =
            new RskAddress("0x1234567890123456789012345678901234567890");

    @Test
    void equals_hashCode_toString_andAccept() throws Exception {
        ParsedType1Transaction left = sample();
        ParsedType1Transaction right = sample();

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertFalse(left.equals("other"));
        assertNotEquals(left, withNonce(right, new byte[]{0x02}));
        assertTrue(left.toString().contains("ParsedType1Transaction"));
        assertEquals("type1", left.accept(visitorReturningLabels()));

        Method toHex = ParsedType1Transaction.class.getDeclaredMethod("toHex", byte[].class);
        toHex.setAccessible(true);
        assertEquals("null", toHex.invoke(null, (Object) null));
    }

    @Test
    void byteAccessors_returnDefensiveCopies() {
        ParsedType1Transaction parsed = sample();

        parsed.nonce()[0] ^= 0x01;
        parsed.gasLimit()[0] ^= 0x01;
        parsed.data()[0] ^= 0x01;

        assertNotSame(parsed.accessListBytes(), parsed.accessListBytes());
        assertEquals(sample(), parsed);
    }

    private static ParsedType1Transaction sample() {
        return new ParsedType1Transaction(
                TransactionTypePrefix.typed(org.ethereum.core.transaction.TransactionType.TYPE_1),
                BigInteger.ONE.toByteArray(),
                Coin.valueOf(10),
                BigInteger.valueOf(21_000).toByteArray(),
                RECEIVER,
                Coin.ZERO,
                new byte[]{0x01},
                new UnsignedSignature((byte) 33),
                Rskip546TestSupport.EMPTY_ACCESS_LIST);
    }

    private static ParsedType1Transaction withNonce(ParsedType1Transaction base, byte[] nonce) {
        return new ParsedType1Transaction(
                base.typePrefix(),
                nonce,
                base.gasPrice(),
                base.gasLimit(),
                base.receiveAddress(),
                base.value(),
                base.data(),
                base.signatureState(),
                base.accessListBytes());
    }

    private static ParsedRawTransactionVisitor<String> visitorReturningLabels() {
        return new ParsedRawTransactionVisitor<>() {
            @Override public String visitType0(ParsedType0Transaction transaction) { return "type0"; }
            @Override public String visitType1(ParsedType1Transaction transaction) { return "type1"; }
            @Override public String visitType2(ParsedType2Transaction transaction) { return "type2"; }
            @Override public String visitType2Rsk(ParsedType2RSKTransaction transaction) { return "type2rsk"; }
            @Override public String visitType4(ParsedType4Transaction transaction) { return "type4"; }
        };
    }
}
