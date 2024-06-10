package co.rsk.peg.feeperkb.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.jupiter.api.Assertions.*;

class FeePerKbConstantsTest {

    @ParameterizedTest
    @MethodSource("getGenesisFeePerKbProvider")
    void getGenesisFeePerKb(FeePerKbConstants feePerKbConstants, Coin expectedGenesisFeePerKb) {
        Coin actualGenesisFeePerKb = feePerKbConstants.getGenesisFeePerKb();

        assertEquals(expectedGenesisFeePerKb, actualGenesisFeePerKb);
    }

    private static Stream<Arguments> getGenesisFeePerKbProvider() {
        return Stream.of(
            Arguments.of(FeePerKbMainNetConstants.getInstance(), Coin.MILLICOIN.multiply(5)),
            Arguments.of(FeePerKbTestNetConstants.getInstance(), Coin.MILLICOIN),
            Arguments.of(FeePerKbRegTestConstants.getInstance(), Coin.MILLICOIN)
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
            Arguments.of(FeePerKbMainNetConstants.getInstance(), Coin.valueOf(5_000_000L)),
            Arguments.of(FeePerKbTestNetConstants.getInstance(), Coin.valueOf(5_000_000L)),
            Arguments.of(FeePerKbRegTestConstants.getInstance(), Coin.valueOf(5_000_000L))
        );
    }

    @ParameterizedTest
    @MethodSource("getFeePerKbChangeAuthorizerProvider")
    void getFeePerKbChangeAuthorizer(FeePerKbConstants feePerKbConstants,
        AddressBasedAuthorizer expectedFeePerKbChangeAuthorizer) {
        AddressBasedAuthorizer actualFeePerKbChangeAuthorizer = feePerKbConstants.getFeePerKbChangeAuthorizer();

        assertThat(actualFeePerKbChangeAuthorizer, samePropertyValuesAs(expectedFeePerKbChangeAuthorizer));
    }

    private static Stream<Arguments> getFeePerKbChangeAuthorizerProvider() {
        //MainNet
        List<ECKey> mainNetFeePerKbAuthorizedKeys = Arrays.stream(new String[]{
            "0448f51638348b034995b1fd934fe14c92afde783e69f120a46ee16eb6bdc2e4f6b5e37772094c68c0dea2b1be3d96ea9651a9eebda7304914c8047f4e3e251378",
            "0484c66f75548baf93e322574adac4e4579b6a53f8d11fab640e14c90118e6983ef24b0de349a3e88f72e81e771ae1c897cef446fd7f4da71778c532aee3b6c41b",
            "04bb6435dc1ea12da843ebe213893d136c1624acd681fff82551498ae00bf28e9323164b00daf925fa75177463b8254a2aae8a1713e4d851a84ea369c193e9ce51"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        AddressBasedAuthorizer mainNetFeePerKbChangeAuthorizer = new AddressBasedAuthorizer(
            mainNetFeePerKbAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        //TestNet
        List<ECKey> testNetFeePerKbAuthorizedKeys = Arrays.stream(new String[]{
            "04701d1d27f8c2ae97912d96fb1f82f10c2395fd320e7a869049268c6b53d2060dfb2e22e3248955332d88cd2ae29a398f8f3858e48dd6d8ffbc37dfd6d1aa4934",
            "045ef89e4a5645dc68895dbc33b4c966c3a0a52bb837ecdd2ba448604c4f47266456d1191420e1d32bbe8741f8315fde4d1440908d400e5998dbed6549d499559b",
            "0455db9b3867c14e84a6f58bd2165f13bfdba0703cb84ea85788373a6a109f3717e40483aa1f8ef947f435ccdf10e530dd8b3025aa2d4a7014f12180ee3a301d27"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        AddressBasedAuthorizer testNetFeePerKbChangeAuthorizer = new AddressBasedAuthorizer(
            testNetFeePerKbAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        //RegTest
        List<ECKey> regTestFeePerKbAuthorizedKeys = Arrays.stream(new String[]{
            "0430c7d0146029db553d60cf11e8d39df1c63979ee2e4cd1e4d4289a5d88cfcbf3a09b06b5cbc88b5bfeb4b87a94cefab81c8d44655e7e813fc3e18f51cfe7e8a0"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        AddressBasedAuthorizer regTestFeePerKbChangeAuthorizer = new AddressBasedAuthorizer(
            regTestFeePerKbAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        return Stream.of(
            Arguments.of(FeePerKbMainNetConstants.getInstance(), mainNetFeePerKbChangeAuthorizer),
            Arguments.of(FeePerKbTestNetConstants.getInstance(), testNetFeePerKbChangeAuthorizer),
            Arguments.of(FeePerKbRegTestConstants.getInstance(), regTestFeePerKbChangeAuthorizer)
        );
    }
}
