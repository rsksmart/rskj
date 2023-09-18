package org.ethereum.rpc.parameters;

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
}
