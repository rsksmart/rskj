package org.ethereum.rpc;

import org.ethereum.rpc.validation.BnTagOrNumberValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BnTagOrNumberValidatorTest {


    @Test
    void testValidHexBlockNumberOrId() {
        assertTrue(BnTagOrNumberValidator.isValid("0x123"));
        assertTrue(BnTagOrNumberValidator.isValid("0x0123"));
        assertTrue(BnTagOrNumberValidator.isValid("earliest"));
        assertTrue(BnTagOrNumberValidator.isValid("finalized"));
        assertTrue(BnTagOrNumberValidator.isValid("safe"));
        assertTrue(BnTagOrNumberValidator.isValid("latest"));
        assertTrue(BnTagOrNumberValidator.isValid("pending"));
    }

    @Test
    void testInvalidParameters() {
        assertFalse(BnTagOrNumberValidator.isValid("0x"));
        assertFalse(BnTagOrNumberValidator.isValid("0x12j"));
        assertFalse(BnTagOrNumberValidator.isValid("0xGHI"));
        assertFalse(BnTagOrNumberValidator.isValid("invalid"));
    }

}