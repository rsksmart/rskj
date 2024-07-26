package co.rsk.peg.lockingcap.constants;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LockingCapConstantsTest {

    @ParameterizedTest
    @MethodSource("getIncreaseAuthorizerProvider")
    void getIncreaseAuthorizer(LockingCapConstants lockingCapConstants, AddressBasedAuthorizer expectedIncreaseAuthorizer) {
        AddressBasedAuthorizer actualIncreaseAuthorizer = lockingCapConstants.getIncreaseAuthorizer();
        assertThat(actualIncreaseAuthorizer, samePropertyValuesAs(expectedIncreaseAuthorizer));
    }

    private static Stream<Arguments> getIncreaseAuthorizerProvider() {
        // MainNet
        List<ECKey> mainNetIncreaseAuthorizedKeys = Arrays.stream(new String[]{
            "0448f51638348b034995b1fd934fe14c92afde783e69f120a46ee16eb6bdc2e4f6b5e37772094c68c0dea2b1be3d96ea9651a9eebda7304914c8047f4e3e251378",
            "0484c66f75548baf93e322574adac4e4579b6a53f8d11fab640e14c90118e6983ef24b0de349a3e88f72e81e771ae1c897cef446fd7f4da71778c532aee3b6c41b",
            "04bb6435dc1ea12da843ebe213893d136c1624acd681fff82551498ae00bf28e9323164b00daf925fa75177463b8254a2aae8a1713e4d851a84ea369c193e9ce51"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        AddressBasedAuthorizer mainNetIncreaseAuthorizer = new AddressBasedAuthorizer(
            mainNetIncreaseAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        // TestNet
        List<ECKey> testNetIncreaseAuthorizedKeys = Arrays.stream(new String[]{
            "04701d1d27f8c2ae97912d96fb1f82f10c2395fd320e7a869049268c6b53d2060dfb2e22e3248955332d88cd2ae29a398f8f3858e48dd6d8ffbc37dfd6d1aa4934",
            "045ef89e4a5645dc68895dbc33b4c966c3a0a52bb837ecdd2ba448604c4f47266456d1191420e1d32bbe8741f8315fde4d1440908d400e5998dbed6549d499559b",
            "0455db9b3867c14e84a6f58bd2165f13bfdba0703cb84ea85788373a6a109f3717e40483aa1f8ef947f435ccdf10e530dd8b3025aa2d4a7014f12180ee3a301d27"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        AddressBasedAuthorizer testNetIncreaseAuthorizer = new AddressBasedAuthorizer(
            testNetIncreaseAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        // RegTest
        ECKey authorizerPublicKey = ECKey.fromPublicOnly(Hex.decode(
            "04450bbaab83ec48b3cb8fbb077c950ee079733041c039a8c4f1539e5181ca1a27589eeaf0fbf430e49d2909f14c767bf6909ad6845831f683416ee12b832e36ed"
        ));

        List<ECKey> regTestIncreaseAuthorizedKeys = Collections.singletonList(authorizerPublicKey);

        AddressBasedAuthorizer regTestIncreaseAuthorizer = new AddressBasedAuthorizer(
            regTestIncreaseAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        return Stream.of(
            Arguments.of(LockingCapMainNetConstants.getInstance(), mainNetIncreaseAuthorizer),
            Arguments.of(LockingCapTestNetConstants.getInstance(), testNetIncreaseAuthorizer),
            Arguments.of(LockingCapRegTestConstants.getInstance(), regTestIncreaseAuthorizer)
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
            Arguments.of(LockingCapMainNetConstants.getInstance(), mainNetInitialValue),
            Arguments.of(LockingCapTestNetConstants.getInstance(), testNetInitialValue),
            Arguments.of(LockingCapRegTestConstants.getInstance(), regTestInitialValue)
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
            Arguments.of(LockingCapMainNetConstants.getInstance(), mainNetIncrementsMultiplier),
            Arguments.of(LockingCapTestNetConstants.getInstance(), testNetIncrementsMultiplier),
            Arguments.of(LockingCapRegTestConstants.getInstance(), regTestIncrementsMultiplier)
        );
    }
}
