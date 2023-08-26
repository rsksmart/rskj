package org.ethereum.rpc.parameters;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BlockIdentifierParamTest {
    @Test
    public void testValidStringBlockIdentifier() {
        String validBlockIdentifier = "latest";

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam(validBlockIdentifier);

        assertEquals(validBlockIdentifier, blockIdentifierParam.getIdentifier());
    }

    @Test
    public void testValidHexBlockIdentifier() {
        String validBlockIdentifier = "0xf892038609184e";

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam(validBlockIdentifier);

        assertEquals(validBlockIdentifier, blockIdentifierParam.getIdentifier());
    }

    @Test
    public void testValidDecimalBlockIdentifier() {
        String validBlockIdentifier = "1028";

        BlockIdentifierParam blockIdentifierParam = new BlockIdentifierParam(validBlockIdentifier);

        assertEquals(validBlockIdentifier, blockIdentifierParam.getIdentifier());
    }

    @Test
    public void testInvalidHexAddressParam() {
        String invalidStringIdentifier = "first";
        String invalidHexIdentifier = "0xf89203860918sv";

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockIdentifierParam(invalidStringIdentifier));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockIdentifierParam(invalidHexIdentifier));
    }
}
