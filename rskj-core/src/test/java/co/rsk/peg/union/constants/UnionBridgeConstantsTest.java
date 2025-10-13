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
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnionBridgeConstantsTest {

    private static Stream<Arguments> changeUnionAddressAuthorizerProvider() {
        List<RskAddress> testnetAuthorizers = mapToRskAddresses(Stream.of(
            "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

        List<RskAddress> regtestAuthorizers = mapToRskAddresses(Stream.of(
            "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), List.of(ZERO_ADDRESS)),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), testnetAuthorizers),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), regtestAuthorizers)
        );
    }

    @ParameterizedTest
    @MethodSource("changeUnionAddressAuthorizerProvider")
    void getChangeUnionBridgeContractAddressAuthorizer_ok(
        UnionBridgeConstants unionBridgeConstants,
        List<RskAddress> expectedSenderAddresses
    ) {
        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeAddressAuthorizer = unionBridgeConstants.getChangeUnionBridgeContractAddressAuthorizer();

        // Assert
        for (RskAddress expectedSenderAddress : expectedSenderAddresses) {
            assertTrue(actualUnionBridgeChangeAddressAuthorizer.isAuthorized(expectedSenderAddress));
        }

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
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), ZERO_ADDRESS),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), ZERO_ADDRESS)
        );
    }

    private static List<RskAddress> mapToRskAddresses(List<ECKey> keys) {
        return keys.stream()
            .map(ECKey::getAddress)
            .map(RskAddress::new)
            .toList();
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
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), ZERO_ADDRESS),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), ZERO_ADDRESS)
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
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), new RskAddress("5988645d30cd01e4b3bc2c02cb3909dec991ae31")),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), new RskAddress("5988645d30cd01e4b3bc2c02cb3909dec991ae31"))
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
        assertEquals(expectedInitialLockingCap, actualInitialLockingCap);
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
        assertEquals(expectedLockingCapIncrementsMultiplier, actualLockingCapIncrementsMultiplier);
    }
}
