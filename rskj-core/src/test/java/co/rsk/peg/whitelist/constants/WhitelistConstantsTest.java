package co.rsk.peg.whitelist.constants;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.jupiter.api.Assertions.assertEquals;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WhitelistConstantsTest {

    @ParameterizedTest
    @MethodSource("getLockWhitelistChangeAuthorizerProvider")
    void getLockWhitelistChangeAuthorizer(WhitelistConstants whitelistConstants, AddressBasedAuthorizer expectedLockWhitelistChangeAuthorizer) {
        AddressBasedAuthorizer actualLockWhitelistChangeAuthorizer = whitelistConstants.getLockWhitelistChangeAuthorizer();

        assertThat(actualLockWhitelistChangeAuthorizer, samePropertyValuesAs(expectedLockWhitelistChangeAuthorizer));
    }

    private static Stream<Arguments> getLockWhitelistChangeAuthorizerProvider() {
        //Mainnet
        RskAddress mainnetAuthorizerAddress = new RskAddress("6ba9d41b07da470fe340cbd439a42538795eb75b");
        AddressBasedAuthorizer mainnetLockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            mainnetAuthorizerAddress
        );

        //Testnet
        RskAddress testnetAuthorizerAddress = new RskAddress("54fdb399cf235c9b0d464ab4055af9251883bbfe");
        AddressBasedAuthorizer testnetLockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            testnetAuthorizerAddress
        );

        //Regtest
        RskAddress regtestAuthorizerAddress = new RskAddress("87d2a0f33744929da08b65fd62b627ea52b25f8e");
        AddressBasedAuthorizer regtestLockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            regtestAuthorizerAddress
        );

        return Stream.of(
            Arguments.of(WhitelistMainNetConstants.getInstance(), mainnetLockWhitelistChangeAuthorizer),
            Arguments.of(WhitelistTestNetConstants.getInstance(), testnetLockWhitelistChangeAuthorizer),
            Arguments.of(WhitelistRegTestConstants.getInstance(), regtestLockWhitelistChangeAuthorizer)
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
            Arguments.of(WhitelistMainNetConstants.getInstance(), NetworkParameters.fromID(NetworkParameters.ID_MAINNET)),
            Arguments.of(WhitelistTestNetConstants.getInstance(), NetworkParameters.fromID(NetworkParameters.ID_TESTNET)),
            Arguments.of(WhitelistRegTestConstants.getInstance(), NetworkParameters.fromID(NetworkParameters.ID_REGTEST))
        );
    }

    @ParameterizedTest
    @MethodSource("genesisWhitelistEnabledProvider")
    void isGenesisWhitelistEnabled(WhitelistConstants whitelistConstants, boolean expectedGenesisWhitelistEnabled) {
        boolean actualGenesisWhitelistEnabled = whitelistConstants.isGenesisWhitelistEnabled();

        assertEquals(expectedGenesisWhitelistEnabled, actualGenesisWhitelistEnabled);
    }

    private static Stream<Arguments> genesisWhitelistEnabledProvider() {
        return Stream.of(
            Arguments.of(WhitelistMainNetConstants.getInstance(), true),
            Arguments.of(WhitelistTestNetConstants.getInstance(), true),
            Arguments.of(WhitelistRegTestConstants.getInstance(), false)
        );
    }
}
