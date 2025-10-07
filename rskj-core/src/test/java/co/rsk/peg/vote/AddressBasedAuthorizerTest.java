
/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.peg.vote;

import co.rsk.RskTestUtils;
import co.rsk.core.RskAddress;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static co.rsk.core.RskAddress.ZERO_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AddressBasedAuthorizerTest {

    private Transaction rskTx;
    private SignatureCache signatureCache;

    private static final RskAddress authorizedAddress = RskTestUtils.generateAddress("authorized");
    private static final RskAddress unauthorizedAddress = RskTestUtils.generateAddress("non-authorized");

    // Addresses for majority test cases
    private static final RskAddress majorityAuthorizedAddress1 = RskTestUtils.generateAddress("authorized1");
    private static final RskAddress majorityAuthorizedAddress2 = RskTestUtils.generateAddress("authorized2");
    private static final RskAddress majorityAuthorizedAddress3 = RskTestUtils.generateAddress("authorized3");

    // For testing legacy ALL constructor using keys
    private static final ECKey legacyKey1 = RskTestUtils.getEcKeyFromSeed("legacy1");
    private static final ECKey legacyKey2 = RskTestUtils.getEcKeyFromSeed("legacy2");
    private static final ECKey legacyKey3 = RskTestUtils.getEcKeyFromSeed("legacy3");

    @BeforeEach
    void setup() {
        rskTx = mock(Transaction.class);
        signatureCache = mock(BlockTxSignatureCache.class);
    }

    private AddressBasedAuthorizer createSingleAuthorizer(RskAddress address) {
        return AddressBasedAuthorizerFactory.buildSingleAuthorizer(address);
    }

    private AddressBasedAuthorizer createMajorityAuthorizer(Set<RskAddress> addresses) {
        return AddressBasedAuthorizerFactory.buildMajorityAuthorizer(addresses);
    }

    private AddressBasedAuthorizer createLegacyAllAuthorizer(List<ECKey> keys) {
        return new AddressBasedAuthorizer(keys, AddressBasedAuthorizer.MinimumRequiredCalculation.ALL);
    }

    private static Stream<Arguments> authorizedAddressProvider() {
        return Stream.of(
            Arguments.of(authorizedAddress),
            Arguments.of(ZERO_ADDRESS)
        );
    }

    // Single authorizer tests
    @ParameterizedTest
    @MethodSource("authorizedAddressProvider")
    void getNumberOfAuthorizedAddresses_whenSingleAuthorizer_shouldReturnOne(RskAddress authorizedAddress) {
        AddressBasedAuthorizer singleAuthorizer = createSingleAuthorizer(authorizedAddress);
        Assertions.assertEquals(1, singleAuthorizer.getNumberOfAuthorizedAddresses());
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressProvider")
    void getRequiredAuthorizedAddresses_whenSingleAuthorizer_shouldReturnOne(RskAddress authorizedAddress) {
        AddressBasedAuthorizer singleAuthorizer = createSingleAuthorizer(authorizedAddress);
        Assertions.assertEquals(1, singleAuthorizer.getRequiredAuthorizedAddresses());
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressProvider")
    void isAuthorized_whenAuthorizedAddress_shouldBeTrue(RskAddress authorizedAddress) {
        AddressBasedAuthorizer singleAuthorizer = createSingleAuthorizer(authorizedAddress);
        assertTrue(singleAuthorizer.isAuthorized(authorizedAddress));
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressProvider")
    void isAuthorized_whenAuthorizedTx_shouldBeTrue(RskAddress authorizedAddress) {
        AddressBasedAuthorizer singleAuthorizer = createSingleAuthorizer(authorizedAddress);
        when(rskTx.getSender(signatureCache)).thenReturn(authorizedAddress);

        assertTrue(singleAuthorizer.isAuthorized(rskTx, signatureCache));
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressProvider")
    void isAuthorized_whenUnauthorizedAddress_shouldBeFalse(RskAddress authorizedAddress) {
        AddressBasedAuthorizer singleAuthorizer = createSingleAuthorizer(authorizedAddress);
        assertFalse(singleAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressProvider")
    void isAuthorized_whenUnauthorizedTx_shouldBeFalse() {
        AddressBasedAuthorizer singleAuthorizer = createSingleAuthorizer(authorizedAddress);
        when(rskTx.getSender(signatureCache)).thenReturn(unauthorizedAddress);
        assertFalse(singleAuthorizer.isAuthorized(rskTx, signatureCache));
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressProvider")
    void isAuthorized_whenNullTx_shouldBeFalse() {
        AddressBasedAuthorizer singleAuthorizer = createSingleAuthorizer(authorizedAddress);
        Assertions.assertThrows(NullPointerException.class, () -> singleAuthorizer.isAuthorized(null, signatureCache));
    }

    private static Stream<Arguments> authorizedAddressesProvider() {
        return Stream.of(
            Arguments.of(Set.of(majorityAuthorizedAddress1, majorityAuthorizedAddress2, majorityAuthorizedAddress3)),
            Arguments.of(Set.of(majorityAuthorizedAddress1, majorityAuthorizedAddress2, majorityAuthorizedAddress3, ZERO_ADDRESS))
        );
    }

    // Majority authorizer tests
    @ParameterizedTest
    @MethodSource("authorizedAddressesProvider")
    void getNumberOfAuthorizedAddresses_whenMajorityAuthorizer_shouldReturnSizeOfAuthorizedAddresses(Set<RskAddress> authorizedAddresses) {
        AddressBasedAuthorizer majorityAuthorizer = createMajorityAuthorizer(authorizedAddresses);
        Assertions.assertEquals(authorizedAddresses.size(), majorityAuthorizer.getNumberOfAuthorizedAddresses());
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressesProvider")
    void getRequiredAuthorizedAddresses_whenMajorityAuthorizer_shouldReturnNumberOfRequiredAddresses(Set<RskAddress> authorizedAddresses) {
        AddressBasedAuthorizer majorityAuthorizer = createMajorityAuthorizer(authorizedAddresses);
        int expectedRequiredAuthorizedAddresses = authorizedAddresses.size() / 2 + 1;
        Assertions.assertEquals(expectedRequiredAuthorizedAddresses, majorityAuthorizer.getRequiredAuthorizedAddresses());
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressesProvider")
    void isAuthorized_whenMajorityAndAuthorizedAddress_shouldBeTrue(Set<RskAddress> authorizedAddresses) {
        AddressBasedAuthorizer majorityAuthorizer = createMajorityAuthorizer(authorizedAddresses);
        // Assert that all authorized addresses are authorized
        authorizedAddresses.forEach(address -> assertTrue(majorityAuthorizer.isAuthorized(address)));
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressesProvider")
    void isAuthorized_whenMajorityAndUnauthorizedAddress_shouldBeFalse(Set<RskAddress> authorizedAddresses) {
        AddressBasedAuthorizer majorityAuthorizer = createMajorityAuthorizer(authorizedAddresses);
        assertFalse(majorityAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressesProvider")
    void isAuthorized_whenMajorityAuthorizedTx_shouldBeTrue(Set<RskAddress> authorizedAddresses) {
        AddressBasedAuthorizer majorityAuthorizer = createMajorityAuthorizer(authorizedAddresses);
        // Assert that all authorized addresses are authorized
        authorizedAddresses.forEach(address -> {
            when(rskTx.getSender(signatureCache)).thenReturn(address);
            assertTrue(majorityAuthorizer.isAuthorized(rskTx, signatureCache));
        });
    }

    @ParameterizedTest
    @MethodSource("authorizedAddressesProvider")
    void isAuthorized_whenMajorityAndUnauthorizedTx_shouldBeFalse(Set<RskAddress> authorizedAddresses) {
        AddressBasedAuthorizer majorityAuthorizer = createMajorityAuthorizer(authorizedAddresses);
        when(rskTx.getSender(signatureCache)).thenReturn(unauthorizedAddress);
        assertFalse(majorityAuthorizer.isAuthorized(rskTx, signatureCache));
    }

    private static Stream<Arguments> authorizedKeysProvider() {
        return Stream.of(
            Arguments.of(List.of(legacyKey1)),
            Arguments.of(List.of(legacyKey1, legacyKey2)),
            Arguments.of(List.of(legacyKey1, legacyKey2, legacyKey3))
        );
    }

    // Legacy ALL authorizer tests (using deprecated constructor)
    @ParameterizedTest
    @MethodSource("authorizedKeysProvider")
    void getNumberOfAuthorizedAddresses_whenLegacyAllAuthorizer_shouldReturnSizeOfAuthorizedAddresses(List<ECKey> keys) {
        AddressBasedAuthorizer allAuthorizer = createLegacyAllAuthorizer(keys);
        Assertions.assertEquals(keys.size(), allAuthorizer.getNumberOfAuthorizedAddresses());
    }

    @ParameterizedTest
    @MethodSource("authorizedKeysProvider")
    void getRequiredAuthorizedAddresses_whenLegacyAllAuthorizer_shouldReturnRequiredNumberOfAddresses(List<ECKey> keys) {
        AddressBasedAuthorizer allAuthorizer = createLegacyAllAuthorizer(keys);
        Assertions.assertEquals(keys.size(), allAuthorizer.getRequiredAuthorizedAddresses());
    }

    @ParameterizedTest
    @MethodSource("authorizedKeysProvider")
    void isAuthorized_whenLegacyAllAuthorizedAddress_shouldBeTrue(List<ECKey> authorizedKeys) {
        AddressBasedAuthorizer allAuthorizer = createLegacyAllAuthorizer(authorizedKeys);

        // Assert that all authorized keys are authorized
        authorizedKeys.stream()
            .map(ECKey::getAddress)
            .map(RskAddress::new)
            .forEach(authorizedAddress -> assertTrue(allAuthorizer.isAuthorized(authorizedAddress)));
    }

    @ParameterizedTest
    @MethodSource("authorizedKeysProvider")
    void isAuthorized_whenLegacyAllUnauthorizedAddress_shouldBeFalse(List<ECKey> keys) {
        AddressBasedAuthorizer allAuthorizer = createLegacyAllAuthorizer(keys);
        assertFalse(allAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @ParameterizedTest
    @MethodSource("authorizedKeysProvider")
    void isAuthorized_whenLegacyAllAuthorizedTx_shouldBeTrue(List<ECKey> authorizedKeys) {
        AddressBasedAuthorizer allAuthorizer = createLegacyAllAuthorizer(authorizedKeys);

        // Assert that all authorized keys are authorized
        authorizedKeys.stream()
            .map(ECKey::getAddress)
            .map(RskAddress::new)
            .forEach(authorizedAddress -> {
                when(rskTx.getSender(signatureCache)).thenReturn(authorizedAddress);
                assertTrue(allAuthorizer.isAuthorized(rskTx, signatureCache));
            });
    }

    @ParameterizedTest
    @MethodSource("authorizedKeysProvider")
    void isAuthorized_whenLegacyAllUnauthorizedTx_shouldBeFalse(List<ECKey> keys) {
        AddressBasedAuthorizer allAuthorizer = createLegacyAllAuthorizer(keys);
        when(rskTx.getSender(signatureCache)).thenReturn(unauthorizedAddress);
        assertFalse(allAuthorizer.isAuthorized(rskTx, signatureCache));
    }

    @ParameterizedTest
    @MethodSource("authorizedKeysProvider")
    void isAuthorized_whenNullTx_shouldBeFalse(List<ECKey> keys) {
        AddressBasedAuthorizer allAuthorizer = createLegacyAllAuthorizer(keys);
        Assertions.assertThrows(NullPointerException.class, () -> allAuthorizer.isAuthorized(null, signatureCache));
    }
}
