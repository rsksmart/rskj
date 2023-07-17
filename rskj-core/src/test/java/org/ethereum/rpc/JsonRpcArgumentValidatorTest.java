/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package org.ethereum.rpc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRpcArgumentValidatorTest {


    @Test
    public void testValidHexBlockNumberOrId() {
        assertTrue(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("0x123"));
        assertTrue(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("earliest"));
        assertTrue(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("finalized"));
        assertTrue(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("safe"));
        assertTrue(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("latest"));
        assertTrue(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("pending"));
    }

    @Test
    public void testInvalidParameters() {
        assertFalse(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("0x"));
        assertFalse(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("0x12j"));
        assertFalse(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("0xGHI"));
        assertFalse(JsonRpcArgumentValidator.isValidHexBlockNumberOrId("invalid"));
    }
}