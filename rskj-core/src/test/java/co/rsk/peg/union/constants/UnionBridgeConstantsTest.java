package co.rsk.peg.union.constants;

import static co.rsk.core.RskAddress.ZERO_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnionBridgeConstantsTest {

    private static Stream<Arguments> changeUnionAddressAuthorizerProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), ZERO_ADDRESS),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), new RskAddress("54fdb399cf235c9b0d464ab4055af9251883bbfe")),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), new RskAddress("6c9dfd950bf748bb26f893f7e5f693c7f60a8f85"))
        );
    }

    @ParameterizedTest
    @MethodSource("changeUnionAddressAuthorizerProvider")
    void getChangeUnionBridgeContractAddressAuthorizer_ok(
        UnionBridgeConstants unionBridgeConstants,
        RskAddress expectedSenderAddress
    ) {
        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeAddressAuthorizer = unionBridgeConstants.getChangeUnionBridgeContractAddressAuthorizer();

        // Assert
        assertTrue(actualUnionBridgeChangeAddressAuthorizer.isAuthorized(expectedSenderAddress));

        RskAddress unauthorizedAddress = RskTestUtils.generateAddress("unauthorized");
        assertFalse(actualUnionBridgeChangeAddressAuthorizer.isAuthorized(unauthorizedAddress));
    }

    @ParameterizedTest
    @MethodSource("unionBridgeChangeLockingCapAuthorizerProvider")
    void getChangeLockingCapAuthorizer_ok(UnionBridgeConstants unionBridgeConstants, RskAddress expectedSenderAddress) {
        RskAddress unauthorizedAddress = RskTestUtils.generateAddress("unauthorized");

        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeLockingCapAuthorizer = unionBridgeConstants.getChangeLockingCapAuthorizer();

        // Assert
        assertTrue(actualUnionBridgeChangeLockingCapAuthorizer.isAuthorized(expectedSenderAddress));
        assertFalse(actualUnionBridgeChangeLockingCapAuthorizer.isAuthorized(unauthorizedAddress));
    }

    private static Stream<Arguments> unionBridgeChangeLockingCapAuthorizerProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), ZERO_ADDRESS),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), new RskAddress("1a8109af0f019ED3045Fbcdf45E5e90d6b6AAfaF")),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), new RskAddress("377e67e16c13994A4d44791Daf0a4d4Cac445783"))
        );
    }

    @ParameterizedTest
    @MethodSource("unionBridgeChangeTransferPermissionsAuthorizerProvider")
    void getChangeTransferPermissionsAuthorizer_ok(UnionBridgeConstants unionBridgeConstants, RskAddress expectedSenderAddress) {
        RskAddress unauthorizedAddress = RskTestUtils.generateAddress("unauthorized");

        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeTransferPermissionsAuthorizer = unionBridgeConstants.getChangeTransferPermissionsAuthorizer();

        // Assert
        assertTrue(actualUnionBridgeChangeTransferPermissionsAuthorizer.isAuthorized(expectedSenderAddress));
        assertFalse(actualUnionBridgeChangeTransferPermissionsAuthorizer.isAuthorized(unauthorizedAddress));
    }

    private static Stream<Arguments> unionBridgeChangeTransferPermissionsAuthorizerProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), ZERO_ADDRESS),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), new RskAddress("8db1F83E8119E4Dce5bC708ec2f4390FFd910B19")),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), new RskAddress("377e67e16c13994A4d44791Daf0a4d4Cac445783"))
        );
    }

    private static Stream<Arguments> btcParamsProvider() {
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

    private static Stream<Arguments> unionBridgeAddressProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), ZERO_ADDRESS),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), ZERO_ADDRESS),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), ZERO_ADDRESS)
        );
    }

    @ParameterizedTest
    @MethodSource("unionBridgeAddressProvider")
    void getAddress_ok(UnionBridgeConstants unionBridgeConstants,
        RskAddress expectedUnionBridgeAddress) {
        // Act
        RskAddress actualUnionBridgeAddress = unionBridgeConstants.getAddress();

        // Assert
        assertEquals(expectedUnionBridgeAddress, actualUnionBridgeAddress);
    }

    private static Stream<Arguments> unionLockingCapInitialValueProvider() {
        Coin oneEth = new Coin(BigInteger.TEN.pow(18)); // 1 ETH = 1000000000000000000 wei
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), oneEth),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), oneEth),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), oneEth)
        );
    }

    @ParameterizedTest
    @MethodSource("unionLockingCapInitialValueProvider")
    void getInitialLockingCap_ok(
        UnionBridgeConstants unionBridgeConstants,
        Coin expectedInitialLockingCap
    ) {
        // Act
        Coin actualInitialLockingCap = unionBridgeConstants.getInitialLockingCap();

        // Assert
        assertEquals(expectedInitialLockingCap, actualInitialLockingCap);
    }

    private static Stream<Arguments> unionLockingCapIncrementsMultiplierProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), 2),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), 2),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), 2)
        );
    }

    @ParameterizedTest
    @MethodSource("unionLockingCapIncrementsMultiplierProvider")
    void getLockingCapIncrementsMultiplier_ok(UnionBridgeConstants unionBridgeConstants,
        int expectedLockingCapIncrementsMultiplier) {
        // Act
        int actualLockingCapIncrementsMultiplier = unionBridgeConstants.getLockingCapIncrementsMultiplier();

        // Assert
        assertEquals(expectedLockingCapIncrementsMultiplier, actualLockingCapIncrementsMultiplier);
    }
}
