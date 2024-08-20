package co.rsk.peg.whitelist.constants;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.jupiter.api.Assertions.assertEquals;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
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
        //MainNet
        ECKey mainNetAuthorizerPublicKey = ECKey.fromPublicOnly(Hex.decode(
            "041a2449e9d63409c5a8ea3a21c4109b1a6634ee88fd57176d45ea46a59713d5e0b688313cf252128a3e49a0b2effb4b413e5a2525a6fa5894d059f815c9d9efa6"
        ));
        List<ECKey> mainNetLockWhitelistAuthorizedKeys = Collections.singletonList(mainNetAuthorizerPublicKey);

        AddressBasedAuthorizer mainNetLockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            mainNetLockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        //TestNet
        ECKey testNetAuthorizerPublicKey = ECKey.fromPublicOnly(Hex.decode(
            "04bf7e3bca7f7c58326382ed9c2516a8773c21f1b806984bb1c5c33bd18046502d97b28c0ea5b16433fbb2b23f14e95b36209f304841e814017f1ede1ecbdcfce3"
        ));
        List<ECKey> testNetLockWhitelistAuthorizedKeys = Collections.singletonList(testNetAuthorizerPublicKey);

        AddressBasedAuthorizer testNetLockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            testNetLockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        //RegTest
        ECKey regTestAuthorizerPublicKey = ECKey.fromPublicOnly(Hex.decode(
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        ));
        List<ECKey> regTestLockWhitelistAuthorizedKeys = Collections.singletonList(regTestAuthorizerPublicKey);

        AddressBasedAuthorizer regTestLockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            regTestLockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        return Stream.of(
            Arguments.of(WhitelistMainNetConstants.getInstance(), mainNetLockWhitelistChangeAuthorizer),
            Arguments.of(WhitelistTestNetConstants.getInstance(), testNetLockWhitelistChangeAuthorizer),
            Arguments.of(WhitelistRegTestConstants.getInstance(), regTestLockWhitelistChangeAuthorizer)
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
}
