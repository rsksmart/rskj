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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void requireNormalizedSignatureComponent_signPaddedFullWord_doesNotThrow() {
        byte[] component = new byte[33];
        component[1] = (byte) 0x80;

        assertDoesNotThrow(() -> CommonParsingUtils.requireNormalizedSignatureComponent(component, "Signature R is not valid"));
    }

    @Test
    void requireNormalizedSignatureComponent_exceedsLimit_throws() {
        byte[] oversize = new byte[33];
        oversize[0] = 0x01;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireNormalizedSignatureComponent(oversize, "Signature R is not valid"));
        assertTrue(ex.getMessage().contains("Signature R is not valid"));
    }

    @Test
    void requireDataWordBytes_exceedsLimit_throws() {
        byte[] oversize = new byte[33];
        oversize[0] = 0x01;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireDataWordBytes(oversize, "Nonce is not valid"));
        assertTrue(ex.getMessage().contains("Nonce is not valid"));
    }

    @Test
    void requireDataWordBytes_withinLimit_doesNotThrow() {
        assertDoesNotThrow(() -> CommonParsingUtils.requireDataWordBytes(new byte[32], "Nonce is not valid"));
    }

    @Test
    void requireDataWordCoin_exceedsLimit_throws() {
        byte[] oversize = new byte[33];
        oversize[0] = 0x01;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireDataWordCoin(new Coin(oversize), "Gas Price is not valid"));
        assertTrue(ex.getMessage().contains("Gas Price is not valid"));
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

    // -------------------------------------------------------------------------
    // parseCanonicalYParity — shared by the typed envelope and the authorization tuple
    // -------------------------------------------------------------------------

    @Test
    void parseCanonicalYParity_emptyOrNull_isZero() {
        assertEquals(0, CommonParsingUtils.parseCanonicalYParity(null, "y"));
        assertEquals(0, CommonParsingUtils.parseCanonicalYParity(new byte[0], "y"));
    }

    @Test
    void parseCanonicalYParity_one_isReturned() {
        assertEquals(1, CommonParsingUtils.parseCanonicalYParity(new byte[]{1}, "y"));
    }

    @Test
    void parseCanonicalYParity_singleZeroByte_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.parseCanonicalYParity(new byte[]{0}, "y"));
        assertTrue(ex.getMessage().contains("must not have leading zero bytes"), ex.getMessage());
    }

    @Test
    void parseCanonicalYParity_multiByte_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.parseCanonicalYParity(new byte[]{1, 0}, "y"));
    }

    @ParameterizedTest
    @ValueSource(bytes = {2, 5, 27, (byte) 0xFF})
    void parseCanonicalYParity_outOfRange_throws(byte value) {
        assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.parseCanonicalYParity(new byte[]{value}, "y"));
    }

    // -------------------------------------------------------------------------
    // requireCanonicalSignatureComponent — the strict counterpart used on RLP paths
    // -------------------------------------------------------------------------

    @Test
    void requireCanonicalSignatureComponent_minimalWord_isAccepted() {
        byte[] word = new byte[32];
        word[0] = 0x11;
        assertDoesNotThrow(() -> CommonParsingUtils.requireCanonicalSignatureComponent(word, "Signature R"));
    }

    @Test
    void requireCanonicalSignatureComponent_nullOrEmpty_isAccepted() {
        assertDoesNotThrow(() -> CommonParsingUtils.requireCanonicalSignatureComponent(null, "Signature R"));
        assertDoesNotThrow(() -> CommonParsingUtils.requireCanonicalSignatureComponent(new byte[0], "Signature R"));
    }

    @Test
    void requireCanonicalSignatureComponent_leadingZero_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireCanonicalSignatureComponent(new byte[32], "Signature R"));
        assertTrue(ex.getMessage().contains("Signature R must not have leading zero bytes"), ex.getMessage());
    }

    @Test
    void requireCanonicalSignatureComponent_overDataWord_throws() {
        byte[] tooWide = new byte[33];
        tooWide[0] = 0x11;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireCanonicalSignatureComponent(tooWide, "Signature S"));
        assertEquals("Signature S is not valid", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // requireCanonical*ScalarFields — one call per raw typed parser
    // -------------------------------------------------------------------------

    @Test
    void requireCanonicalGasPriceScalarFields_canonicalOrAbsentFields_areAccepted() {
        assertDoesNotThrow(() -> CommonParsingUtils.requireCanonicalGasPriceScalarFields(
                new byte[]{0x01}, new byte[]{0x02}, new byte[]{0x52, 0x08}, new byte[]{0x03}));
        assertDoesNotThrow(() -> CommonParsingUtils.requireCanonicalGasPriceScalarFields(
                null, null, null, null));
        assertDoesNotThrow(() -> CommonParsingUtils.requireCanonicalGasPriceScalarFields(
                new byte[0], new byte[0], new byte[0], new byte[0]));
    }

    @ParameterizedTest
    @CsvSource({"0,Nonce", "1,Gas Price", "2,Gas Limit", "3,Value"})
    void requireCanonicalGasPriceScalarFields_leadingZero_throwsNamingTheField(int index, String label) {
        byte[][] fields = {new byte[]{0x01}, new byte[]{0x02}, new byte[]{0x03}, new byte[]{0x04}};
        fields[index] = new byte[]{0x00, 0x01};

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireCanonicalGasPriceScalarFields(
                        fields[0], fields[1], fields[2], fields[3]));
        assertTrue(ex.getMessage().startsWith(label + " must not have leading zero bytes"), ex.getMessage());
    }

    @Test
    void requireCanonicalTypedScalarFields_canonicalOrAbsentFields_areAccepted() {
        assertDoesNotThrow(() -> CommonParsingUtils.requireCanonicalTypedScalarFields(
                new byte[]{0x01}, new byte[]{0x52, 0x08}, new byte[]{0x02}, new byte[]{0x03}, new byte[]{0x04}));
        assertDoesNotThrow(() -> CommonParsingUtils.requireCanonicalTypedScalarFields(
                null, null, null, null, null));
    }

    @ParameterizedTest
    @CsvSource({
            "0,Nonce",
            "1,Gas Limit",
            "2,Value",
            "3,Max priority fee per gas",
            "4,Max fee per gas"})
    void requireCanonicalTypedScalarFields_leadingZero_throwsNamingTheField(int index, String label) {
        byte[][] fields = {
                new byte[]{0x01}, new byte[]{0x02}, new byte[]{0x03}, new byte[]{0x04}, new byte[]{0x05}};
        fields[index] = new byte[]{0x00, 0x01};

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireCanonicalTypedScalarFields(
                        fields[0], fields[1], fields[2], fields[3], fields[4]));
        assertTrue(ex.getMessage().startsWith(label + " must not have leading zero bytes"), ex.getMessage());
    }

    @Test
    void requireListFramed_listElement_doesNotThrow() {
        RLPList fields = RLP.decodeList(RLP.encodeList(RLP.encodeList(), RLP.encodeElement(new byte[]{0x01})));

        assertDoesNotThrow(() -> CommonParsingUtils.requireListFramed(fields.get(0), "Access list"));
    }

    @Test
    void requireListFramed_stringElement_throws() {
        // A string whose content happens to decode as a list is still a string.
        RLPList fields = RLP.decodeList(RLP.encodeList(RLP.encodeElement(new byte[]{(byte) 0xc0})));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireListFramed(fields.get(0), "Access list"));
        assertTrue(ex.getMessage().contains("Access list must be encoded as an RLP list"), ex.getMessage());
    }

    @Test
    void requireListFramed_emptyStringElement_throws() {
        RLPList fields = RLP.decodeList(RLP.encodeList(RLP.encodeElement(null)));

        assertThrows(IllegalArgumentException.class,
                () -> CommonParsingUtils.requireListFramed(fields.get(0), "Authorization list"));
    }
}
