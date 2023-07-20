package org.ethereum.rpc.validation;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HexTopicValidatorTest {
    @Test
    void testValidHexTopicWithPrefix() {
        String validHexTopic = "0x1ABF1234567890ABCdEF01234167890ABCDeF01234567890ABCDEF0123456789";
        Assertions.assertTrue(HexTopicValidator.isValid(validHexTopic));
    }

    @Test
    void testValidHexTopicWithoutPrefix() {
        String validHexTopic = "1ABF1234567890ABCDEF01234567890ABCDEF01234567890ABCDEF0123456789";
        boolean result = HexTopicValidator.isValid(validHexTopic);
        Assertions.assertTrue(result);
    }

    @Test
    void testInvalidHexTopicTooShort() {
        String invalidHexTopic = "0x12345";
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> {
            HexTopicValidator.isValid(invalidHexTopic);
        });
    }

    @Test
    void testInvalidHexTopicTooLong() {
        String invalidHexTopic = "0x1ABF1234567890ABCDEF01234567890ABCDEF01234567890ABCDEF0123456789FF";
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> {
            HexTopicValidator.isValid(invalidHexTopic);
        });
    }

    @Test
    void testInvalidHexTopicWithInvalidCharacters() {
        String invalidHexTopic = "0x1ABF1234567890ABCDEF01234567890ABGDEF01234567890ABCDEF0123456789";
        Assertions.assertThrows(RskJsonRpcRequestException.class, () -> {
            HexTopicValidator.isValid(invalidHexTopic);
        });
    }
}
