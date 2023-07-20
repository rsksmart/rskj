package org.ethereum.rpc.validation;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EthAddressValidatorTest {
    @Test
    void testValidAddress() {
        assertTrue(EthAddressValidator.isValid("0x023827714750bf8c232ed8856049dc6dd42a693c"));

    }

    @Test
    void testInvalidAddress() {
        assertThrows(RskJsonRpcRequestException.class, () -> {
            EthAddressValidator.isValid("0x12345");
        });

        assertThrows(RskJsonRpcRequestException.class, () -> {
            EthAddressValidator.isValid("0x023827714750bf8c232ed8856049dc6dd42a693");
        });

        assertThrows(RskJsonRpcRequestException.class, () -> {
            EthAddressValidator.isValid("0x02382771475f8c232ed8856049dc6dd42a693c");
        });

        assertThrows(RskJsonRpcRequestException.class, () -> {
            EthAddressValidator.isValid("0x0y3827714750bf8c232ed8856049dc6dd42a693c");
        });

        assertThrows(RskJsonRpcRequestException.class, () -> {
            EthAddressValidator.isValid("013827714750bf8c232ed8856049dc6dd42a693c");
        });
    }

}