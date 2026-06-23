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
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsedType0TransactionTest {

    private static final RskAddress RECEIVER =
            new RskAddress("0x1234567890123456789012345678901234567890");

    @Test
    void equals_hashCode_andToString_coverValueSemantics() {
        ParsedType0Transaction parsed = sample();

        assertEquals(parsed, parsed);
        assertEquals(parsed.hashCode(), parsed.hashCode());
        assertFalse(parsed.equals("other"));
        assertTrue(parsed.toString().contains("ParsedType0Transaction"));
    }

    @Test
    void accept_dispatchesToVisitor() {
        ParsedType0Transaction parsed = sample();

        assertEquals("type0", parsed.accept(new ParsedRawTransactionVisitor<>() {
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

    private static ParsedType0Transaction sample() {
        ECKey.ECDSASignature sig = new ECKey().sign(new byte[32]);
        ECDSASignature signature = ECDSASignature.fromComponents(
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(sig.r),
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(sig.s),
                sig.v);

        return new ParsedType0Transaction(
                TransactionTypePrefix.legacy(),
                BigInteger.ONE.toByteArray(),
                Coin.valueOf(10),
                BigInteger.valueOf(21_000).toByteArray(),
                RECEIVER,
                Coin.ZERO,
                new byte[0],
                new SignedSignature((byte) 33, signature));
    }
}
