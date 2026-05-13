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
package org.ethereum.core.transaction.parser.util;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CommonParsingUtils}.
 */
class CommonParsingUtilsTest {

    // -------------------------------------------------------------------------
    // nullToEmpty
    // -------------------------------------------------------------------------

    @Test
    void nullToEmpty_null_returnsEmptyArray() {
        assertArrayEquals(new byte[0], CommonParsingUtils.nullToEmpty(null));
    }

    @Test
    void nullToEmpty_nonNull_returnsSameArray() {
        byte[] input = {0x01, 0x02};
        assertArrayEquals(input, CommonParsingUtils.nullToEmpty(input));
    }

    // -------------------------------------------------------------------------
    // parseHexData
    // -------------------------------------------------------------------------

    @Test
    void parseHexData_null_returnsEmptyArray() {
        assertArrayEquals(new byte[0], CommonParsingUtils.parseHexData(null));
    }

    @Test
    void parseHexData_hexWithPrefix_parsesCorrectly() {
        assertArrayEquals(new byte[]{0x12, 0x34}, CommonParsingUtils.parseHexData("0x1234"));
    }

    @Test
    void parseHexData_hexWithoutPrefix_parsesCorrectly() {
        assertArrayEquals(new byte[]{(byte) 0xab, (byte) 0xcd}, CommonParsingUtils.parseHexData("abcd"));
    }

    // -------------------------------------------------------------------------
    // parseAddress
    // -------------------------------------------------------------------------

    @Test
    void parseAddress_null_returnsNullAddress() {
        assertEquals(RskAddress.nullAddress(), CommonParsingUtils.parseAddress(null));
    }

    @Test
    void parseAddress_validHex_returnsCorrectAddress() {
        String hex = "0x" + "00".repeat(19) + "01";
        RskAddress result = CommonParsingUtils.parseAddress(hex);
        assertNotNull(result);
        assertNotEquals(RskAddress.nullAddress(), result);
    }

    // -------------------------------------------------------------------------
    // defaultAddress
    // -------------------------------------------------------------------------

    @Test
    void defaultAddress_null_returnsNullAddress() {
        assertEquals(RskAddress.nullAddress(), CommonParsingUtils.defaultAddress(null));
    }

    @Test
    void defaultAddress_nonNull_returnsSame() {
        RskAddress addr = new RskAddress("0x" + "aa".repeat(20));
        assertEquals(addr, CommonParsingUtils.defaultAddress(addr));
    }

    // -------------------------------------------------------------------------
    // parseBigInteger
    // -------------------------------------------------------------------------

    @Test
    void parseBigInteger_null_invokesDefaultSupplier() {
        BigInteger result = CommonParsingUtils.parseBigInteger(null, () -> BigInteger.TEN);
        assertEquals(BigInteger.TEN, result);
    }

    @Test
    void parseBigInteger_hexValue_parsesCorrectly() {
        BigInteger result = CommonParsingUtils.parseBigInteger("0x64", () -> BigInteger.ZERO);
        assertEquals(BigInteger.valueOf(100), result);
    }

    @Test
    void parseBigInteger_decimalValue_parsesCorrectly() {
        BigInteger result = CommonParsingUtils.parseBigInteger("42", () -> BigInteger.ZERO);
        assertEquals(BigInteger.valueOf(42), result);
    }

    // -------------------------------------------------------------------------
    // parseCoin
    // -------------------------------------------------------------------------

    @Test
    void parseCoin_null_returnsZero() {
        assertEquals(Coin.ZERO, CommonParsingUtils.parseCoin(null));
    }

    @Test
    void parseCoin_emptyString_returnsZero() {
        assertEquals(Coin.ZERO, CommonParsingUtils.parseCoin(""));
    }

    @Test
    void parseCoin_hexValue_parsesCorrectly() {
        Coin result = CommonParsingUtils.parseCoin("0xa");
        assertEquals(Coin.valueOf(10), result);
    }

    // -------------------------------------------------------------------------
    // defaultValue (Coin)
    // -------------------------------------------------------------------------

    @Test
    void defaultValue_null_returnsZero() {
        assertEquals(Coin.ZERO, CommonParsingUtils.defaultValue((Coin) null));
    }

    @Test
    void defaultValue_nonNull_returnsSame() {
        Coin coin = Coin.valueOf(99);
        assertEquals(coin, CommonParsingUtils.defaultValue(coin));
    }

    // -------------------------------------------------------------------------
    // requireFieldCount
    // -------------------------------------------------------------------------

    @Test
    void requireFieldCount_matchingCount_doesNotThrow() {
        // Build an RLP list with exactly 3 elements
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{1}),
                RLP.encodeElement(new byte[]{2}),
                RLP.encodeElement(new byte[]{3})
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        assertDoesNotThrow(() -> CommonParsingUtils.requireFieldCount(list, 3, "Test"));
    }

    @Test
    void requireFieldCount_wrongCount_throws() {
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(new byte[]{1}),
                RLP.encodeElement(new byte[]{2})
        );
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireFieldCount(list, 9, "TestType"));
        assertTrue(ex.getMessage().contains("TestType"));
        assertTrue(ex.getMessage().contains("9"));
    }
}
