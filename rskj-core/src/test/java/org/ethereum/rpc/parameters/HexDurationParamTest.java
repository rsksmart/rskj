package org.ethereum.rpc.parameters;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HexDurationParamTest {
    @Test
    void testValidHexDurationParam() {
        String validHexValue = "0x1e";
        HexDurationParam hexDurationParam = new HexDurationParam(validHexValue);

        assertNotNull(hexDurationParam);
        assertEquals(30, hexDurationParam.getDuration());
    }

    @Test
    void testInvalidHexDurationParam() {
        String invalidHexValue = "1e"; // Missing hex prefix
        String nonNumericHexValue = "0x1t"; // Non-valid hex value

        assertThrows(RskJsonRpcRequestException.class, () -> new HexDurationParam(invalidHexValue));
        assertThrows(RskJsonRpcRequestException.class, () -> new HexDurationParam(nonNumericHexValue));
    }
}
