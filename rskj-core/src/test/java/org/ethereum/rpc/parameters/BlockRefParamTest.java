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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockRefParamTest {
    @Test
    public void testValidIdentifier() {
        String identifier = "latest";

        BlockRefParam blockRefParam = new BlockRefParam(identifier);

        assertEquals(identifier, blockRefParam.getIdentifier());
        assertNull(blockRefParam.getInputs());
    }

    @Test
    public void testValidInputs() {
        Map<String, String> inputs = new HashMap<String, String>() {
            {
                put("blockHash", "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
            }
        };

        BlockRefParam blockRefParam = new BlockRefParam(inputs);

        assertEquals(inputs, blockRefParam.getInputs());
        assertNull(blockRefParam.getIdentifier());
    }

    @Test
    public void testInvalidIdentifierString() {
        String invalidStringIdentifier = "first";

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(invalidStringIdentifier));
    }

    @Test
    public void testInvalidIdentifierHexString() {
        String invalidStringIdentifier = "0x1aw";

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(invalidStringIdentifier));
    }

    @Test
    public void testInvalidInputsInvalidKey() {
        Map<String, String> inputs = new HashMap<String, String>() {
            {
                put("invalidKey", "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
            }
        };

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(inputs));
    }

    @Test
    public void testInvalidInputsInvalidBlockHash() {
        Map<String, String> inputs = new HashMap<String, String>() {
            {
                put("blockHash", "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fxk");
                put("requireCanonical", "false");
            }
        };

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(inputs));
    }

    @Test
    public void testInvalidInputsInvalidBlockNumber() {
        Map<String, String> inputs = new HashMap<String, String>() {
            {
                put("blockNumber", "0x76ty");
                put("requireCanonical", "false");
            }
        };

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(inputs));
    }

    @Test
    public void testInvalidInputsInvalidRequireCanonical() {
        Map<String, String> inputs = new HashMap<String, String>() {
            {
                put("blockNumber", "0x76c0");
                put("requireCanonical", "first");
            }
        };

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(inputs));
    }

    @Test
    public void testSafeTagIdentifier() {
        BlockRefParam param = new BlockRefParam(BlockTag.SAFE.getTag());
        assertEquals(BlockTag.SAFE.getTag(), param.getIdentifier());
        assertTrue(param.isSafeTag());
        assertFalse(param.isForkSafeTag());
        assertFalse(param.isRequireForkSafe());
    }

    @Test
    public void testForkSafeTagIdentifier() {
        BlockRefParam param = new BlockRefParam(BlockTag.FORK_SAFE.getTag());
        assertEquals(BlockTag.FORK_SAFE.getTag(), param.getIdentifier());
        assertTrue(param.isForkSafeTag());
        assertFalse(param.isSafeTag());
    }

    @Test
    public void testObjectWithRequireForkSafe() {
        Map<String, String> inputs = Map.of("blockNumber", "0x10", "requireForkSafe", "true");
        BlockRefParam param = new BlockRefParam(inputs);
        assertEquals(inputs, param.getInputs());
        assertTrue(param.isRequireForkSafe());
    }

    @Test
    public void testObjectRejectsUnknownKey() {
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(
                Map.of("blockNumber", "0x10", "requireSafe", "true")));
    }

    @Test
    public void testInvalidInputsInvalidRequireForkSafe() {
        Map<String, String> inputs = Map.of("blockNumber", "0x76c0", "requireForkSafe", "first");
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(inputs));
    }
}
