package org.ethereum.rpc.validation;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HexValueValidatorTest {

    @Test
    void testValidHexadecimals() {
        assertTrue(HexValueValidator.isValid("0x0"));
        assertTrue(HexValueValidator.isValid("0x123"));
        assertTrue(HexValueValidator.isValid("0xabcdef"));
        assertTrue(HexValueValidator.isValid("0x0000000000000000000000000000000001000008"));
        assertTrue(HexValueValidator.isValid("0xABCDEF")); // Uppercase are not allowed in Ethereum
        assertTrue(HexValueValidator.isValid("0x0123456789")); //Numbers staring by 0x0 is not allowed in Ethereum
    }

    @Test
    void testInvalidHexadecimals() {
        assertThrows(RskJsonRpcRequestException.class, () -> HexValueValidator.isValid("0x"));
        assertThrows(RskJsonRpcRequestException.class, () -> HexValueValidator.isValid("0xGHIJKLMNOPQRSTUVWXYZ"));
        assertThrows(RskJsonRpcRequestException.class, () -> HexValueValidator.isValid("123456"));
        assertThrows(RskJsonRpcRequestException.class, () -> HexValueValidator.isValid("abcdef"));
        assertThrows(RskJsonRpcRequestException.class, () -> HexValueValidator.isValid("invalid"));
        assertThrows(RskJsonRpcRequestException.class, () -> HexValueValidator.isValid("0x1234g6"));
    }

}