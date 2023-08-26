package org.ethereum.rpc.parameters;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HexAddressParamTest {

    @Test
    public void testValidHexAddressParam() {
        String validHexAddress = "0x407d73d8a49eeb85d32cf465507dd71d507100c1";

        HexAddressParam hexAddressParam = new HexAddressParam(validHexAddress);

        assertEquals(validHexAddress, hexAddressParam.getAddress().toJsonString());
    }

    @Test
    public void testInvalidHexAddressParam() {
        String invalidHexAddress = "0x407d73d8a4sseb85d32cf465507dd71d507100c1";
        String shorterHexAddress = "0x407d73";

        assertThrows(RskJsonRpcRequestException.class, () -> new HexAddressParam(null));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexAddressParam(invalidHexAddress));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexAddressParam(shorterHexAddress));
    }
}
