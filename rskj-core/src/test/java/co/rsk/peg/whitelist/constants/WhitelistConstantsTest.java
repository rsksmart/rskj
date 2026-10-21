package co.rsk.peg.whitelist.constants;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.jupiter.api.Assertions.assertEquals;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WhitelistConstantsTest {

    private static final WhitelistConstants MAINNET = WhitelistMainNetConstants.getInstance();
    private static final WhitelistConstants TESTNET = WhitelistTestNetConstants.getInstance();
    private static final WhitelistConstants REGTEST = WhitelistRegTestConstants.getInstance();

    @ParameterizedTest
    @MethodSource("getLockWhitelistChangeAuthorizerProvider")
    void getLockWhitelistChangeAuthorizer(WhitelistConstants whitelistConstants, Set<RskAddress> changeAuthorizerAddresses) {
        AddressBasedAuthorizer expectedLockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildOneOfManyAuthorizer(changeAuthorizerAddresses);
        AddressBasedAuthorizer actualLockWhitelistChangeAuthorizer = whitelistConstants.getLockWhitelistChangeAuthorizer();

        assertThat(actualLockWhitelistChangeAuthorizer, samePropertyValuesAs(expectedLockWhitelistChangeAuthorizer));
    }

    private static Stream<Arguments> getLockWhitelistChangeAuthorizerProvider() {
        Set<RskAddress> authorizerAddressesMainnet = Set.of(new RskAddress("6ba9d41b07da470fe340cbd439a42538795eb75b"));
        Set<RskAddress> authorizerAddressesTestnet = Set.of(new RskAddress("54fdb399cf235c9b0d464ab4055af9251883bbfe"));
        Set<RskAddress> authorizerAddressesRegtest = Set.of(new RskAddress("87d2a0f33744929da08b65fd62b627ea52b25f8e"));

        return Stream.of(
            Arguments.of(MAINNET, authorizerAddressesMainnet),
            Arguments.of(TESTNET, authorizerAddressesTestnet),
            Arguments.of(REGTEST, authorizerAddressesRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("btcParamsProvider")
    void getBtcParams(WhitelistConstants whitelistConstants, NetworkParameters expectedNetworkParameters) {
        NetworkParameters actualNetworkParameters = whitelistConstants.getBtcParams();

        assertEquals(expectedNetworkParameters, actualNetworkParameters);
    }

    private static Stream<Arguments> btcParamsProvider() {
        return Stream.of(
            Arguments.of(MAINNET, NetworkParameters.fromID(NetworkParameters.ID_MAINNET)),
            Arguments.of(TESTNET, NetworkParameters.fromID(NetworkParameters.ID_TESTNET)),
            Arguments.of(REGTEST, NetworkParameters.fromID(NetworkParameters.ID_REGTEST))
        );
    }
}
