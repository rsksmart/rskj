package org.ethereum.rpc.parameters;

import org.ethereum.rpc.BlockRef;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BlockRefParamTest {
    @Test
    public void testValidIdentifier() {
        String identifier = "latest";
        BlockRef blockRef = new BlockRef(identifier);

        BlockRefParam blockRefParam = new BlockRefParam(blockRef);

        assertEquals(identifier, blockRefParam.getIdentifier());
        assertNull(blockRefParam.getInputs());
    }

    @Test
    public void testValidInputs() {
        Map<String, String> inputs = new HashMap<String, String>() {
            {
                put("blockHash", "0x0011223344556677880011223344556677889900");
            }
        };
        BlockRef blockRef = new BlockRef(inputs);

        BlockRefParam blockRefParam = new BlockRefParam(blockRef);

        assertEquals(inputs, blockRefParam.getInputs());
        assertNull(blockRefParam.getIdentifier());
    }

    @Test
    public void testInvalidIdentifierString() {
        String invalidStringIdentifier = "first";
        BlockRef blockRef = new BlockRef(invalidStringIdentifier);

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(blockRef));
    }

    @Test
    public void testInvalidIdentifierHexString() {
        String invalidStringIdentifier = "0x1aw";
        BlockRef blockRef = new BlockRef(invalidStringIdentifier);

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(blockRef));
    }

    @Test
    public void testInvalidInputsInvalidKey() {
        Map<String, String> inputs = new HashMap<String, String>() {
            {
                put("invalidKey", "0x0011223344556677880011223344556677889900");
            }
        };
        BlockRef blockRef = new BlockRef(inputs);

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockRefParam(blockRef));
    }
}
