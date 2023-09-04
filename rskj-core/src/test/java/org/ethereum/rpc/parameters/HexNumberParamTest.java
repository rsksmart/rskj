package org.ethereum.rpc.parameters;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HexNumberParamTest {

    @Test
    public void testValidHexNumberParam() {
        String validHexNumber = "0x76c0";
        HexNumberParam hexNumberParam = new HexNumberParam(validHexNumber);

        assertNotNull(hexNumberParam);
        assertEquals(validHexNumber, hexNumberParam.getHexNumber());
    }

    @Test
    public void testValidHexNumberParamAsStringNumber() {
        String validStringNumber = "1500";
        HexNumberParam hexNumberParam = new HexNumberParam(validStringNumber);

        assertNotNull(hexNumberParam);
        assertEquals(validStringNumber, hexNumberParam.getHexNumber());
    }

    @Test
    public void testInvalidHexNumberParam() {
        String invalidHexNumber = "0x76ty";

        assertThrows(RskJsonRpcRequestException.class, () -> new HexNumberParam(invalidHexNumber));
    }
}
