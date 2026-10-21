package co.rsk.peg.lockingcap.constants;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.jupiter.api.Assertions.assertEquals;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LockingCapConstantsTest {

    private static final LockingCapConstants MAINNET = LockingCapMainNetConstants.getInstance();
    private static final LockingCapConstants TESTNET = LockingCapTestNetConstants.getInstance();
    private static final LockingCapConstants REGTEST = LockingCapRegTestConstants.getInstance();

    @ParameterizedTest
    @MethodSource("getIncreaseAuthorizerProvider")
    void getIncreaseAuthorizer(LockingCapConstants lockingCapConstants, Set<RskAddress> expectedAuthorizedAddresses) {
        AddressBasedAuthorizer expectedIncreaseAuthorizer = AddressBasedAuthorizerFactory.buildOneOfManyAuthorizer(expectedAuthorizedAddresses);
        AddressBasedAuthorizer actualIncreaseAuthorizer = lockingCapConstants.getIncreaseAuthorizer();

        assertThat(actualIncreaseAuthorizer, samePropertyValuesAs(expectedIncreaseAuthorizer));
    }

    private static Stream<Arguments> getIncreaseAuthorizerProvider() {
        Set<RskAddress> authorizedAddressesMainnet = Stream.of(
            "8418edc8fea47183116b4c8cd6a12e51a7e169c1",
            "a02db0ed94a5894bc6f9079bb9a2d93ada1917f3",
            "180a7edda4e640ea5a3e495e17a1efad260c39e9"
        ).map(RskAddress::new).collect(Collectors.toSet());

        Set<RskAddress> authorizedAddressesTestnet = Stream.of(
            "9e472a8b3f3c95736b0b255db2b7ba38c687c751",
            "f7d8befef29878e5c35fee23e6889ed88afb56b8",
            "4502d34c8e889b2be7e1bfe1a645df8fa3f9204b"
        ).map(RskAddress::new).collect(Collectors.toSet());

        Set<RskAddress> authorizedAddressesRegtest = Set.of(
            new RskAddress("6913bd4715eff3b25501f3212f15df38296d1f21")
        );

        return Stream.of(
            Arguments.of(MAINNET, authorizedAddressesMainnet),
            Arguments.of(TESTNET, authorizedAddressesTestnet),
            Arguments.of(REGTEST, authorizedAddressesRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("getInitialValueProvider")
    void getInitialValue(LockingCapConstants lockingCapConstants, Coin expectedInitialValue) {
        Coin actualInitialValue = lockingCapConstants.getInitialValue();
        assertEquals(expectedInitialValue, actualInitialValue);
    }

    private static Stream<Arguments> getInitialValueProvider() {
        // MainNet
        Coin mainNetInitialValue = Coin.COIN.multiply(300L);

        // TestNet
        Coin testNetInitialValue = Coin.COIN.multiply(200L);

        // RegTest
        Coin regTestInitialValue = Coin.COIN.multiply(1_000L);

        return Stream.of(
            Arguments.of(MAINNET, mainNetInitialValue),
            Arguments.of(TESTNET, testNetInitialValue),
            Arguments.of(REGTEST, regTestInitialValue)
        );
    }

    @ParameterizedTest
    @MethodSource("getIncrementsMultiplierProvider")
    void getIncrementsMultiplier(LockingCapConstants lockingCapConstants, int expectedIncrementsMultiplier) {
        int actualIncrementsMultiplier = lockingCapConstants.getIncrementsMultiplier();
        assertEquals(expectedIncrementsMultiplier, actualIncrementsMultiplier);
    }

    private static Stream<Arguments> getIncrementsMultiplierProvider() {
        // MainNet
        int mainNetIncrementsMultiplier = 2;

        // TestNet
        int testNetIncrementsMultiplier = 2;

        // RegTest
        int regTestIncrementsMultiplier = 2;

        return Stream.of(
            Arguments.of(MAINNET, mainNetIncrementsMultiplier),
            Arguments.of(TESTNET, testNetIncrementsMultiplier),
            Arguments.of(REGTEST, regTestIncrementsMultiplier)
        );
    }
}
