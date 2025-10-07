package co.rsk.peg.vote;

import static co.rsk.core.RskAddress.ZERO_ADDRESS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.core.RskAddress;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressBasedAuthorizerFactoryTest {

    private static final RskAddress authorizedAddress = RskTestUtils.generateAddress("authorized");
    private static final RskAddress unauthorizedAddress = RskTestUtils.generateAddress("not-authorized");;
    private static final Set<RskAddress> authorizedAddresses = Set.of(
        RskTestUtils.generateAddress("authorized1"),
        RskTestUtils.generateAddress("authorized2"),
        RskTestUtils.generateAddress("authorized3")
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
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            ZERO_ADDRESS
        );

        // Assert
        assertSingleAuthorizer(addressBasedAuthorizer, ZERO_ADDRESS);
        // Assert unauthorized for any other addresses
        assertFalse(addressBasedAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @Test
    void buildSingleAuthorizer_whenAuthorizedAddress_shouldBuildAuthorizer() {
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            authorizedAddress
        );

        // Assert
        assertSingleAuthorizer(addressBasedAuthorizer, authorizedAddress);
        // Assert unauthorized for any other addresses
        assertFalse(addressBasedAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsDuplicateAddresses_shouldBuildAuthorizer() {
        // Arrange
        RskAddress duplicatedAddress = RskTestUtils.generateAddress("authorizedDuplicated");
        Set<RskAddress> addressesWithDuplicates = Stream.of(authorizedAddress, unauthorizedAddress,
            duplicatedAddress, duplicatedAddress).collect(Collectors.toSet());
        // Act
        AddressBasedAuthorizer authorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(addressesWithDuplicates);
        // Assert
        assertTrue(authorizer.isAuthorized(duplicatedAddress));
    }

    @Test
    void buildMajorityAuthorizer_whenLargeSet_shouldBuildAuthorizer() {
        Set<RskAddress> largeSet = IntStream.range(0, 100)
            .mapToObj(i -> new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(i)).getAddress()))
            .collect(Collectors.toSet());
        AddressBasedAuthorizer authorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(largeSet);
        assertEquals(100, authorizer.getNumberOfAuthorizedAddresses());
        assertEquals(51, authorizer.getRequiredAuthorizedAddresses()); // majority of 100
    }

    @Test
    void buildSingleAuthorizer_whenNullAddress_shouldThrowNPE() {
        assertThrows(
            NullPointerException.class,
            () -> AddressBasedAuthorizerFactory.buildSingleAuthorizer(null)
        );
    }

    @Test
    void buildMajorityAuthorizer_whenSetOfAddresses_shouldBuildAuthorizer() {
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizedAddresses);

        // Assert
        assertMajorityAuthorizer(addressBasedAuthorizer, authorizedAddresses);

        // Assert unauthorized for any other addresses
        assertFalse(addressBasedAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @Test
    void buildMajorityAuthorizer_whenEmptySet_shouldThrowIllegalArgumentException() {
        final Set<RskAddress> emptyAuthorizerSet = Set.of();
        assertThrows(
            IllegalArgumentException.class,
            () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(emptyAuthorizerSet)
        );
    }

    @Test
    void buildMajorityAuthorizer_whenNullSet_shouldThrowNPE() {
        assertThrows(
            NullPointerException.class,
            () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(null)
        );
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsNull_shouldThrowIAE() {
        Set<RskAddress> authorizersWithNull = new HashSet<>(authorizedAddresses);
        authorizersWithNull.add(null);
        assertThrows(
            IllegalArgumentException.class,
            () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizersWithNull)
        );
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsZeroAddress_shouldBuildAuthorizer() {
        Set<RskAddress> authorizersWithZeroAddress = new HashSet<>(authorizedAddresses);
        authorizersWithZeroAddress.add(ZERO_ADDRESS);
        AddressBasedAuthorizer addressBasedAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizersWithZeroAddress);

        assertMajorityAuthorizer(addressBasedAuthorizer, authorizersWithZeroAddress);
        // Assert unauthorized for any other addresses
        assertFalse(addressBasedAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsSingleAddress_shouldThrowIllegalArgumentException() {
        Set<RskAddress> authorizersWithSingleAddress = Set.of(authorizedAddress);
        assertThrows(
            IllegalArgumentException.class,
            () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizersWithSingleAddress)
        );
    }

    @Test
    void buildMajorityAuthorizer_whenSetContainsTwoAddresses_shouldThrowIllegalArgumentException() {
        Set<RskAddress> authorizersWithTwoAddresses = new HashSet<>();
        authorizersWithTwoAddresses.add(authorizedAddress);
        authorizersWithTwoAddresses.add(RskTestUtils.generateAddress("authorized2"));

        assertThrows(
            IllegalArgumentException.class,
            () -> AddressBasedAuthorizerFactory.buildMajorityAuthorizer(authorizersWithTwoAddresses)
        );
    }

    private void assertSingleAuthorizer(AddressBasedAuthorizer addressBasedAuthorizer, RskAddress authorizedAddress) {
        assertEquals(1, addressBasedAuthorizer.getNumberOfAuthorizedAddresses());
        assertEquals(1, addressBasedAuthorizer.getNumberOfAuthorizedAddresses());

        // should be authorized
        assertTrue(addressBasedAuthorizer.isAuthorized(authorizedAddress));
        // should be authorized when coming from the transaction
        when(rskTx.getSender(any(SignatureCache.class))).thenReturn(authorizedAddress);
        assertTrue(addressBasedAuthorizer.isAuthorized(rskTx, signatureCache));
    }

    private void assertMajorityAuthorizer(AddressBasedAuthorizer majorityAuthorizer, Set<RskAddress> authorizedAddresses) {
        assertEquals(authorizedAddresses.size(), majorityAuthorizer.getNumberOfAuthorizedAddresses());
        assertEquals(authorizedAddresses.size() / 2 + 1, majorityAuthorizer.getRequiredAuthorizedAddresses());

        authorizedAddresses.stream().map(majorityAuthorizer::isAuthorized).forEach(Assertions::assertTrue);
        authorizedAddresses.forEach(address -> {
            when(rskTx.getSender(any(SignatureCache.class)))
                .thenReturn(address);
            assertTrue(majorityAuthorizer.isAuthorized(rskTx, signatureCache));
        });
    }
}
