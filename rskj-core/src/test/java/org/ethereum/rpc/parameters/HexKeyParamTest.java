/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package org.ethereum.rpc.parameters;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HexKeyParamTest {
    @Test
    void testValidHexKeyParam() {
        String validHexValue = "0xcd3376bb711cb332ee3fb2ca04c6a8b9f70c316fcdf7a1f44ef4c7999483295e";
        HexKeyParam hexKeyParam = new HexKeyParam(validHexValue);

        assertNotNull(hexKeyParam);
        assertEquals(validHexValue, hexKeyParam.getHexKey());
    }

    @Test
    void testInvalidHexKeyParam() {
        String invalidHexValue = "0xcd3376bb711cb332ee3fb2ca04c6a8b9f70c316fcdf7a1f44ef4c79994832zxt";

        assertThrows(RskJsonRpcRequestException.class, () -> new HexKeyParam(invalidHexValue));
    }
}
