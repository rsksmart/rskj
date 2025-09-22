package co.rsk.peg.vote;

import co.rsk.core.RskAddress;
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
    private static final RskAddress authorized = new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(1L)).getAddress());
    private static final RskAddress unauthorized = new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(999L)).getAddress());
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
    }

    @Test
    void buildSingleAuthorizer_whenAuthorizedAddress_shouldBuildAuthorizer() {
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(authorized);
        assertSingleAuthorizer(addressBasedAuthorizer, authorized);
    }

    @Test
    void buildSingleAuthorizer_whenNullAddress_shouldThrowNullPointerException() {
        Assertions.assertThrows(NullPointerException.class, () -> AddressBasedAuthorizerFactory.buildSingleAuthorizer(null));
    }

    @Test
    void buildMajorityAuthorizer_whenSetOfAddresses_shouldBuildAuthorizer() {
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizedAddresses);
        assertMajorityAuthorizer(addressBasedAuthorizer, authorizedAddresses);
    }

    @Test
    void buildMajorityAuthorizer_whenEmptySet_shouldThrowIllegalArgumentException() {
        final Set<RskAddress> emptyAuthorizerSet = Set.of();
        Assertions.assertThrows(IllegalArgumentException.class, () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(emptyAuthorizerSet));
    }

    @Test
    void buildMajorityAuthorizer_whenNullSet_shouldThrowNullPointerException() {
        Assertions.assertThrows(NullPointerException.class, () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(null));
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsNull_shouldThrowNullPointerException() {
        Set<RskAddress> authorizersWithNull = Set.of((RskAddress) null);
        Assertions.assertThrows(NullPointerException.class, () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizersWithNull));
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsZeroAddress_shouldBuildAuthorizer() {
        Set<RskAddress> addresses = Set.of(zeroAddress, authorized);
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(addresses);
        assertMajorityAuthorizer(addressBasedAuthorizer, addresses);
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsSingleAddress_shouldThrowIllegalArgumentException() {
        Set<RskAddress> authorizersWithSingleAddress = Set.of(authorized);
        Assertions.assertThrows(IllegalArgumentException.class, () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizersWithSingleAddress));
    }

    private void assertSingleAuthorizer(AddressBasedAuthorizer addressBasedAuthorizer, RskAddress authorizedAddress) {
        Assertions.assertEquals(1, addressBasedAuthorizer.getNumberOfAuthorizedKeys());
        Assertions.assertEquals(1, addressBasedAuthorizer.getRequiredAuthorizedKeys());

        // should be authorized
        Assertions.assertTrue(addressBasedAuthorizer.isAuthorized(authorizedAddress));
        // negative authorization for a different address
        Assertions.assertFalse(addressBasedAuthorizer.isAuthorized(unauthorized));
        // assert authorization by transaction sender
        when(rskTx.getSender(any(SignatureCache.class))).thenReturn(authorizedAddress);
        Assertions.assertTrue(addressBasedAuthorizer.isAuthorized(rskTx, signatureCache));
    }

    private void assertMajorityAuthorizer(AddressBasedAuthorizer auth, Set<RskAddress> authorizedAddresses) {
        Assertions.assertEquals(authorizedAddresses.size(), auth.getNumberOfAuthorizedKeys());
        Assertions.assertEquals(authorizedAddresses.size() / 2 + 1, auth.getRequiredAuthorizedKeys());

        authorizedAddresses.stream().map(auth::isAuthorized).forEach(Assertions::assertTrue);
        Assertions.assertFalse(auth.isAuthorized(unauthorized));

        authorizedAddresses.forEach(address -> {
            when(rskTx.getSender(any(SignatureCache.class)))
                .thenReturn(address);
            Assertions.assertTrue(auth.isAuthorized(rskTx, signatureCache));
        });
    }
}
