package co.rsk.peg.feeperkb.constants;

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

class FeePerKbConstantsTest {

    private static final FeePerKbConstants MAINNET = FeePerKbMainNetConstants.getInstance();
    private static final FeePerKbConstants TESTNET = FeePerKbTestNetConstants.getInstance();
    private static final FeePerKbConstants REGTEST = FeePerKbRegTestConstants.getInstance();

    @ParameterizedTest
    @MethodSource("getGenesisFeePerKbProvider")
    void getGenesisFeePerKb(FeePerKbConstants feePerKbConstants, Coin expectedGenesisFeePerKb) {
        Coin actualGenesisFeePerKb = feePerKbConstants.getGenesisFeePerKb();

        assertEquals(expectedGenesisFeePerKb, actualGenesisFeePerKb);
    }

    private static Stream<Arguments> getGenesisFeePerKbProvider() {
        return Stream.of(
            Arguments.of(MAINNET, Coin.MILLICOIN.multiply(5)),
            Arguments.of(TESTNET, Coin.MILLICOIN),
            Arguments.of(REGTEST, Coin.MILLICOIN)
        );
    }

    @ParameterizedTest
    @MethodSource("getMaxFeePerKbProvider")
    void getMaxFeePerKb(FeePerKbConstants feePerKbConstants, Coin expectedMaxFeePerKb) {
        Coin actualMaxFeePerKb = feePerKbConstants.getMaxFeePerKb();

        assertEquals(expectedMaxFeePerKb, actualMaxFeePerKb);
    }

    private static Stream<Arguments> getMaxFeePerKbProvider() {
        return Stream.of(
            Arguments.of(MAINNET, Coin.valueOf(5_000_000L)),
            Arguments.of(TESTNET, Coin.valueOf(5_000_000L)),
            Arguments.of(REGTEST, Coin.valueOf(5_000_000L))
        );
    }

    @ParameterizedTest
    @MethodSource("getFeePerKbChangeAuthorizerProvider")
    void getFeePerKbChangeAuthorizer(
        FeePerKbConstants feePerKbConstants,
        AddressBasedAuthorizer expectedFeePerKbChangeAuthorizer
    ) {
        AddressBasedAuthorizer actualFeePerKbChangeAuthorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();

        assertThat(actualFeePerKbChangeAuthorizer, samePropertyValuesAs(expectedFeePerKbChangeAuthorizer));
    }

    private static Stream<Arguments> getFeePerKbChangeAuthorizerProvider() {
        Set<RskAddress> feePerKbAuthorizedAddressesMainnet = Stream.of(
            "8418edc8fea47183116b4c8cd6a12e51a7e169c1",
            "a02db0ed94a5894bc6f9079bb9a2d93ada1917f3",
            "180a7edda4e640ea5a3e495e17a1efad260c39e9"
        ).map(RskAddress::new).collect(Collectors.toSet());
        AddressBasedAuthorizer feePerKbChangeAuthorizerMainnet = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(
            feePerKbAuthorizedAddressesMainnet
        );

        Set<RskAddress> feePerKbAuthorizedAddressesTestnet = Stream.of(
            "9e472a8b3f3c95736b0b255db2b7ba38c687c751",
            "f7d8befef29878e5c35fee23e6889ed88afb56b8",
            "4502d34c8e889b2be7e1bfe1a645df8fa3f9204b"
        ).map(RskAddress::new).collect(Collectors.toSet());
        AddressBasedAuthorizer feePerKbChangeAuthorizerTestnet = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(
            feePerKbAuthorizedAddressesTestnet
        );

        RskAddress feePerKbAuthorizedAddressRegtest = new RskAddress("53f8f6dabd612b6137215ddd7758bb5cdd638922");
        AddressBasedAuthorizer feePerKbChangeAuthorizerRegtest = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            feePerKbAuthorizedAddressRegtest
        );

        return Stream.of(
            Arguments.of(MAINNET, feePerKbChangeAuthorizerMainnet),
            Arguments.of(TESTNET, feePerKbChangeAuthorizerTestnet),
            Arguments.of(REGTEST, feePerKbChangeAuthorizerRegtest)
        );
    }
}
