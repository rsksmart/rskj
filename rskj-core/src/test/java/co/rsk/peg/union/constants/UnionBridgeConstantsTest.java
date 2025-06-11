package co.rsk.peg.union.constants;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.math.BigInteger;
import java.util.Collections;
import java.util.stream.Stream;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnionBridgeConstantsTest {

    private static Stream<Arguments> unionBridgeChangeAuthorizerProvider() {
        AddressBasedAuthorizer expectedUnionBridgeChangeAuthorizer = new AddressBasedAuthorizer(
            Collections.singletonList(ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"
            ))),
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), expectedUnionBridgeChangeAuthorizer),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), expectedUnionBridgeChangeAuthorizer),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), expectedUnionBridgeChangeAuthorizer)
        );
    }

    @ParameterizedTest
    @MethodSource("unionBridgeChangeAuthorizerProvider")
    void getChangeAuthorizer_ok(UnionBridgeConstants unionBridgeConstants,
                                            AddressBasedAuthorizer expectedUnionBridgeChangeAuthorizer) {
        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeAuthorizer = unionBridgeConstants.getChangeAuthorizer();

        // Assert
        assertThat(actualUnionBridgeChangeAuthorizer, samePropertyValuesAs(expectedUnionBridgeChangeAuthorizer));
    }

    @ParameterizedTest
    @MethodSource("unionBridgeChangeLockingCapAuthorizerProvider")
    void getChangeLockingCapAuthorizer_ok(UnionBridgeConstants unionBridgeConstants,
        AddressBasedAuthorizer expectedUnionBridgeChangeLockingCapAuthorizer) {
        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeLockingCapAuthorizer = unionBridgeConstants.getChangeLockingCapAuthorizer();

        // Assert
        assertThat(actualUnionBridgeChangeLockingCapAuthorizer, samePropertyValuesAs(expectedUnionBridgeChangeLockingCapAuthorizer));
    }

    private static Stream<Arguments> unionBridgeChangeLockingCapAuthorizerProvider() {
        AddressBasedAuthorizer expectedUnionBridgeChangeLockingCapAuthorizer = new AddressBasedAuthorizer(
            Collections.singletonList(ECKey.fromPublicOnly(Hex.decode(
                "049929eb3c107a65108830f4c221068f42301bd8b054f91bd594944e7fb488fd1c93a8921fb28d3494769598eb271cd2834a31c5bd08fa075170b3da804db00a5b"
            ))),
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), expectedUnionBridgeChangeLockingCapAuthorizer),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), expectedUnionBridgeChangeLockingCapAuthorizer),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), expectedUnionBridgeChangeLockingCapAuthorizer)
        );
    }

    public static Stream<Arguments> btcParamsProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), NetworkParameters.fromID(NetworkParameters.ID_MAINNET)),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), NetworkParameters.fromID(NetworkParameters.ID_TESTNET)),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), NetworkParameters.fromID(NetworkParameters.ID_REGTEST))
        );
    }

    @ParameterizedTest
    @MethodSource("btcParamsProvider")
    void getBtcParams_ok(UnionBridgeConstants unionBridgeConstants,
        NetworkParameters expectedNetworkParameters) {
        // Act
        NetworkParameters actualNetworkParameters = unionBridgeConstants.getBtcParams();

        // Assert
        Assertions.assertEquals(expectedNetworkParameters, actualNetworkParameters);
    }

    public static Stream<Arguments> unionBridgeAddressProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), "5988645d30cd01e4b3bc2c02cb3909dec991ae31"),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), "5988645d30cd01e4b3bc2c02cb3909dec991ae31"),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), "5988645d30cd01e4b3bc2c02cb3909dec991ae31")
        );
    }

    @ParameterizedTest
    @MethodSource("unionBridgeAddressProvider")
    void getAddress_ok(UnionBridgeConstants unionBridgeConstants,
        RskAddress expectedUnionBridgeAddress) {
        // Act
        RskAddress actualUnionBridgeAddress = unionBridgeConstants.getAddress();

        // Assert
        Assertions.assertEquals(expectedUnionBridgeAddress, actualUnionBridgeAddress);
    }

    public static Stream<Arguments> unionLockingCapInitialValueProvider() {
        Coin oneEth = new Coin(BigInteger.TEN.pow(18)); // 1 ETH = 1000000000000000000 wei
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), oneEth.multiply(BigInteger.valueOf(300L))),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), oneEth.multiply(BigInteger.valueOf(400L))),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), oneEth.multiply(BigInteger.valueOf(500L)))
        );
    }

    @ParameterizedTest
    @MethodSource("unionLockingCapInitialValueProvider")
    void getInitialLockingCap_ok(UnionBridgeConstants unionBridgeConstants,
        Coin expectedInitialLockingCap) {
        // Act
        Coin actualInitialLockingCap = unionBridgeConstants.getInitialLockingCap();

        // Assert
        Assertions.assertEquals(expectedInitialLockingCap, actualInitialLockingCap);
    }

    private static Stream<Arguments> unionLockingCapIncrementsMultiplierProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), 2),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), 3),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), 4)
        );
    }

    @ParameterizedTest
    @MethodSource("unionLockingCapIncrementsMultiplierProvider")
    void getLockingCapIncrementsMultiplier_ok(UnionBridgeConstants unionBridgeConstants,
        int expectedLockingCapIncrementsMultiplier) {
        // Act
        int actualLockingCapIncrementsMultiplier = unionBridgeConstants.getLockingCapIncrementsMultiplier();

        // Assert
        Assertions.assertEquals(expectedLockingCapIncrementsMultiplier, actualLockingCapIncrementsMultiplier);
    }
}
