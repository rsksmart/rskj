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
        assertThrows(RskJsonRpcRequestException.class, () -> new HexKeyParam(null));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexKeyParam(""));
    }
}
