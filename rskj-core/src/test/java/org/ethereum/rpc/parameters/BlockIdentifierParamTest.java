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

import org.ethereum.core.genesis.BlockTag;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BlockIdentifierParamTest {
    @Test
    public void testValidStringBlockIdentifier() {
        String validBlockIdentifier = BlockTag.LATEST.getTag();

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam(validBlockIdentifier);

        assertEquals(validBlockIdentifier, blockIdentifierParam.getIdentifier());
    }

    @Test
    public void testValidHexBlockIdentifier() {
        String validBlockIdentifier = "0xf892038609184e";

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam(validBlockIdentifier);

        assertEquals(validBlockIdentifier, blockIdentifierParam.getIdentifier());
    }

    @Test
    public void testValidDecimalBlockIdentifier() {
        String validBlockIdentifier = "1028";

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam(validBlockIdentifier);

        assertEquals(validBlockIdentifier, blockIdentifierParam.getIdentifier());
    }

    @Test
    public void testInvalidHexAddressParam() {
        String invalidStringIdentifier = "first";
        String invalidHexIdentifier = "0xf89203860918sv";

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockIdentifierParam(invalidStringIdentifier));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockIdentifierParam(invalidHexIdentifier));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockIdentifierParam(null));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockIdentifierParam(""));
    }
}
