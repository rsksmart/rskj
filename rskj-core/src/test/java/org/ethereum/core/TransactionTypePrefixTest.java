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

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @ParameterizedTest
    @ValueSource(strings = {"128", "0x80", "0xff", "-1", "abc", ""})
    void hexToRskSubtype_valuesOutOfRange_throw(String hex) {
        assertThrows(RskJsonRpcRequestException.class,
                () -> TransactionTypePrefix.hexToRskSubtype(hex));
    }
}
