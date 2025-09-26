package co.rsk.peg.vote;

import co.rsk.core.RskAddress;
import java.util.HashSet;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AddressBasedAuthorizerFactoryTest {

    private static final RskAddress zeroAddress = new RskAddress("0000000000000000000000000000000000000000");
    private static final RskAddress authorizedAddress = new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(1L)).getAddress());
    private static final RskAddress unauthorizedAddress = new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(999L)).getAddress());
    private static final Set<RskAddress> authorizedAddresses = Set.of(
        new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(1L)).getAddress()),
        new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(2L)).getAddress()),
        new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(3L)).getAddress())
    );

    private Transaction rskTx;
    private SignatureCache signatureCache;


    @BeforeEach
    void beforeEach() {
        rskTx = mock(Transaction.class);
        signatureCache = mock(SignatureCache.class);
    }


    @Test
    void buildSingleAuthorizer_whenZeroAddress_shouldBuildAuthorizer() {
        // Act
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(zeroAddress);

        // Assert
        assertSingleAuthorizer(addressBasedAuthorizer, zeroAddress);
        // Assert unauthorized for any other addresses
        Assertions.assertFalse(addressBasedAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @Test
    void buildSingleAuthorizer_whenAuthorizedAddress_shouldBuildAuthorizer() {
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            authorizedAddress);
        // Assert
        assertSingleAuthorizer(addressBasedAuthorizer, authorizedAddress);
        // Assert unauthorized for any other addresses
        Assertions.assertFalse(addressBasedAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @Test
    void buildSingleAuthorizer_whenNullAddress_shouldThrowIAE() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> AddressBasedAuthorizerFactory.buildSingleAuthorizer(null));
    }

    @Test
    void buildMajorityAuthorizer_whenSetOfAddresses_shouldBuildAuthorizer() {
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizedAddresses);
        // Assert
        assertMajorityAuthorizer(addressBasedAuthorizer, authorizedAddresses);
        // Assert unauthorized for any other addresses
        Assertions.assertFalse(addressBasedAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @Test
    void buildMajorityAuthorizer_whenEmptySet_shouldThrowIllegalArgumentException() {
        final Set<RskAddress> emptyAuthorizerSet = Set.of();
        Assertions.assertThrows(IllegalArgumentException.class, () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(emptyAuthorizerSet));
    }

    @Test
    void buildMajorityAuthorizer_whenNullSet_shouldThrowIAE() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(null));
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsNull_shouldThrowIAE() {
        Set<RskAddress> authorizersWithNull = new HashSet<>();
        authorizersWithNull.add(null);
        authorizersWithNull.add(null);
        Assertions.assertThrows(IllegalArgumentException.class, () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizersWithNull));
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsZeroAddress_shouldBuildAuthorizer() {
        Set<RskAddress> addresses = Set.of(zeroAddress, authorizedAddress);
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(addresses);
        assertMajorityAuthorizer(addressBasedAuthorizer, addresses);
        // Assert unauthorized for any other addresses
        Assertions.assertFalse(addressBasedAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsSingleAddress_shouldThrowIllegalArgumentException() {
        Set<RskAddress> authorizersWithSingleAddress = Set.of(authorizedAddress);
        Assertions.assertThrows(IllegalArgumentException.class, () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizersWithSingleAddress));
    }

    private void assertSingleAuthorizer(AddressBasedAuthorizer addressBasedAuthorizer, RskAddress authorizedAddress) {
        Assertions.assertEquals(1, addressBasedAuthorizer.getNumberOfAuthorizedKeys());
        Assertions.assertEquals(1, addressBasedAuthorizer.getRequiredAuthorizedKeys());

        // should be authorized
        Assertions.assertTrue(addressBasedAuthorizer.isAuthorized(authorizedAddress));
        // should be authorized when coming from the transaction
        when(rskTx.getSender(any(SignatureCache.class))).thenReturn(authorizedAddress);
        Assertions.assertTrue(addressBasedAuthorizer.isAuthorized(rskTx, signatureCache));
    }

    private void assertMajorityAuthorizer(AddressBasedAuthorizer majorityAuthorizer, Set<RskAddress> authorizedAddresses) {
        Assertions.assertEquals(authorizedAddresses.size(), majorityAuthorizer.getNumberOfAuthorizedKeys());
        Assertions.assertEquals(authorizedAddresses.size() / 2 + 1, majorityAuthorizer.getRequiredAuthorizedKeys());

        authorizedAddresses.stream().map(majorityAuthorizer::isAuthorized).forEach(Assertions::assertTrue);
        authorizedAddresses.forEach(address -> {
            when(rskTx.getSender(any(SignatureCache.class)))
                .thenReturn(address);
            Assertions.assertTrue(majorityAuthorizer.isAuthorized(rskTx, signatureCache));
        });
    }
}
