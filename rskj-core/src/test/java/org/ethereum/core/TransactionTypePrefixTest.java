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
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TransactionTypePrefix}, focused on the RSK-namespace subtype
 * hex-parsing logic used at the JSON-RPC ingress.
 */
class TransactionTypePrefixTest {

    @Test
    void hexToRskSubtype_null_returnsNull() {
        assertNull(TransactionTypePrefix.hexToRskSubtype(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "127", "0x0", "0x1", "0x7f", "0x00", "0x01"})
    void hexToRskSubtype_validValues_returnNonNull(String hex) {
        Byte result = TransactionTypePrefix.hexToRskSubtype(hex);
        assertNotNull(result);
    }

    @ParameterizedTest(name = "hexToRskSubtype rejects {0}")
    @ValueSource(strings = {"128", "0x80", "0xff", "-1", "abc", "", "0xzz"})
    void hexToRskSubtype_invalidValues_throw(String hex) {
        assertThrows(RskJsonRpcRequestException.class,
                () -> TransactionTypePrefix.hexToRskSubtype(hex));
    }

    @Test
    void fromRawData_empty_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> TransactionTypePrefix.fromRawData(new byte[0]));
    }

    @Test
    void fromRawData_legacyMarker_returnsLegacy() {
        byte[] legacy = ByteUtil.merge(new byte[] {(byte) 0xf8, 0x00});

        assertTrue(TransactionTypePrefix.fromRawData(legacy).isLegacy());
    }

    @ParameterizedTest(name = "fromRawData type={1}, rskNamespace={2}")
    @MethodSource("typedRawPrefixCases")
    void fromRawData_decodesTypedPrefix(
            byte[] raw,
            TransactionType expectedType,
            boolean rskNamespace,
            byte expectedSubtype) {
        TransactionTypePrefix prefix = TransactionTypePrefix.fromRawData(raw);

        assertEquals(expectedType, prefix.type());
        assertEquals(rskNamespace, prefix.isRskNamespace());
        if (rskNamespace) {
            assertEquals(expectedSubtype, prefix.subtype());
        }
    }

    @Test
    void fromHex_nullTypeWithoutSubtype_returnsLegacy() {
        assertTrue(TransactionTypePrefix.fromHex(null, null).isLegacy());
    }

    @ParameterizedTest(name = "fromHex rejects type={0}, rskSubtype={1}")
    @MethodSource("invalidFromHexCases")
    void fromHex_invalidArguments_throw(String type, String rskSubtype) {
        assertThrows(RskJsonRpcRequestException.class,
                () -> TransactionTypePrefix.fromHex(type, rskSubtype));
    }

    @ParameterizedTest(name = "fromHex type={0}, rskSubtype={1}")
    @MethodSource("validRskNamespaceFromHexCases")
    void fromHex_type2WithSubtype_buildsRskNamespace(String type, String rskSubtype, byte expectedSubtype) {
        TransactionTypePrefix prefix = TransactionTypePrefix.fromHex(type, rskSubtype);

        assertTrue(prefix.isRskNamespace());
        assertEquals(expectedSubtype, prefix.subtype());
    }

    @ParameterizedTest
    @MethodSource("legacyTypedInputs")
    void typed_nullOrLegacy_returnsLegacySingleton(TransactionType type) {
        assertTrue(TransactionTypePrefix.typed(type).isLegacy());
    }

    @ParameterizedTest(name = "of rejects subtype on {0}")
    @EnumSource(value = TransactionType.class, names = {"TYPE_1", "TYPE_3", "TYPE_4"})
    void of_subtypeOnNonType2_throws(TransactionType type) {
        assertThrows(IllegalArgumentException.class,
                () -> TransactionTypePrefix.of(type, (byte) 0x03));
    }

    @Test
    void standardTypedPrefix_legacyTypeInConstructor_throws() {
        assertThrows(IllegalArgumentException.class, () -> new StandardTypedPrefix(TransactionType.LEGACY));
    }

    @ParameterizedTest(name = "rskNamespacePrefix rejects subtype={0}")
    @ValueSource(ints = {0x80, 0xff, 128, 255})
    void rskNamespacePrefix_invalidSubtype_throws(int subtype) {
        assertThrows(IllegalArgumentException.class, () -> new RskNamespacePrefix((byte) subtype));
    }

    @Test
    void stripPrefix_legacy_returnsFullPayload() {
        byte[] raw = new byte[] {(byte) 0xf8, 0x01, 0x01};

        assertEquals(3, TransactionTypePrefix.stripPrefix(raw, TransactionTypePrefix.legacy()).length());
    }

    private static Stream<Arguments> typedRawPrefixCases() {
        return Stream.of(
                Arguments.of(new byte[] {0x01, (byte) 0xc0}, TransactionType.TYPE_1, false, (byte) 0),
                Arguments.of(new byte[] {0x02, 0x03, (byte) 0xc0}, TransactionType.TYPE_2, true, (byte) 0x03)
        );
    }

    private static Stream<Arguments> invalidFromHexCases() {
        return Stream.of(
                Arguments.of(null, "0x3"),
                Arguments.of("0x0", null),
                Arguments.of("0x00", null),
                Arguments.of("not-a-number", null),
                Arguments.of("0x1", "0x3"),
                Arguments.of("0x100", null),
                Arguments.of("0x5", null)
        );
    }

    private static Stream<Arguments> validRskNamespaceFromHexCases() {
        return Stream.of(
                Arguments.of("0x2", "0x3", (byte) 0x03),
                Arguments.of("0x02", "0x0a", (byte) 0x0a)
        );
    }

    private static Stream<TransactionType> legacyTypedInputs() {
        return Stream.of(null, TransactionType.LEGACY);
    }
}
