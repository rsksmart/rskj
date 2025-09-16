package co.rsk.peg.vote;

import co.rsk.core.RskAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressBasedAuthorizerFactoryTest {

    @Test
    void fromAddress_zeroAddress_isAuthorizedAndDefaultsToOne() {
        // Arrange
        RskAddress zero = new RskAddress("0000000000000000000000000000000000000000");
        AddressBasedAuthorizer authorizer = AddressBasedAuthorizerFactory.fromAddress(zero);

        // Act & Assert
        assertTrue(authorizer.isAuthorized(zero), "Zero address should be authorized");

        RskAddress other = new RskAddress("0000000000000000000000000000000000000001");
        assertFalse(authorizer.isAuthorized(other), "Different address should not be authorized");

        assertEquals(1, authorizer.getNumberOfAuthorizedAddresses(), "Should have exactly one authorized address");
        assertEquals(1, authorizer.getRequiredAuthorizedAddresses(), "Default minimum (ONE) should require one");
    }
}
