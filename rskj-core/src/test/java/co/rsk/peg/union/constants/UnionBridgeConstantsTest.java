package co.rsk.peg.union.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.math.BigInteger;
import java.util.Collections;
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
        List<RskAddress> mainnetAuthorizers = mapToRskAddress(Collections.singletonList(
            ECKey.fromPublicOnly(Hex.decode(
                "04bd1d5747ca6564ed860df015c1a8779a35ef2a9f184b6f5390bccb51a3dcace02f88a401778be6c8fd8ed61e4d4f1f508075b3394eb6ac0251d4ed6d06ce644d"))));

        List<RskAddress> testnetAuthorizers = mapToRskAddress(Collections.singletonList(
            ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))));

        List<RskAddress> regtestAuthorizers = mapToRskAddress(Collections.singletonList(
            ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))));

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), mainnetAuthorizers),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), testnetAuthorizers),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), regtestAuthorizers)
        );
    }

    @ParameterizedTest
    @MethodSource("changeUnionAddressAuthorizerProvider")
    void getChangeUnionBridgeContractAddressAuthorizer_ok(UnionBridgeConstants unionBridgeConstants,
                                            List<RskAddress> expectedSenderAddresses) {
        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeAddressAuthorizer = unionBridgeConstants.getChangeUnionBridgeContractAddressAuthorizer();

        // Assert
        for (RskAddress expectedSenderAddress : expectedSenderAddresses) {
            Assertions.assertTrue(actualUnionBridgeChangeAddressAuthorizer.isAuthorized(expectedSenderAddress));
        }
    }

    @ParameterizedTest
    @MethodSource("unionBridgeChangeLockingCapAuthorizerProvider")
    void getChangeLockingCapAuthorizer_ok(UnionBridgeConstants unionBridgeConstants, List<RskAddress> expectedSenderAddresses) {
        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeLockingCapAuthorizer = unionBridgeConstants.getChangeLockingCapAuthorizer();

        // Assert
        for (RskAddress expectedSenderAddress : expectedSenderAddresses) {
            Assertions.assertTrue(actualUnionBridgeChangeLockingCapAuthorizer.isAuthorized(expectedSenderAddress));
        }
    }

    private static Stream<Arguments> unionBridgeChangeLockingCapAuthorizerProvider() {
        List<RskAddress> mainnetAuthorizers = mapToRskAddress(Collections.singletonList(
            ECKey.fromPublicOnly(Hex.decode(
                "040162aff21e78665eabe736746ed86ca613f9e628289438697cf820ed8ac800e5fe8cbca350f8cf0b3ee4ec3d8c3edec93820d889565d4ae9b4f6e6d012acec09"))));

        List<RskAddress> testnetAuthorizers = mapToRskAddress(Collections.singletonList(
            ECKey.fromPublicOnly(Hex.decode(
                "049929eb3c107a65108830f4c221068f42301bd8b054f91bd594944e7fb488fd1c93a8921fb28d3494769598eb271cd2834a31c5bd08fa075170b3da804db00a5b"))));

        List<RskAddress> regtestAuthorizers = mapToRskAddress(Collections.singletonList(
            ECKey.fromPublicOnly(Hex.decode(
                "049929eb3c107a65108830f4c221068f42301bd8b054f91bd594944e7fb488fd1c93a8921fb28d3494769598eb271cd2834a31c5bd08fa075170b3da804db00a5b"))));

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), mainnetAuthorizers),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), testnetAuthorizers),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), regtestAuthorizers)
        );
    }

    private static List<RskAddress> mapToRskAddress(List<ECKey> keys) {
        return keys.stream()
            .map(ECKey::getAddress)
            .map(RskAddress::new)
            .toList();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeChangeTransferPermissionsAuthorizerProvider")
    void getChangeTransferPermissionsAuthorizer_ok(UnionBridgeConstants unionBridgeConstants, List<RskAddress> expectedSenderAddresses) {
        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeTransferPermissionsAuthorizer = unionBridgeConstants.getChangeTransferPermissionsAuthorizer();

        // Assert
        for (RskAddress expectedSenderAddress : expectedSenderAddresses) {
            Assertions.assertTrue(actualUnionBridgeChangeTransferPermissionsAuthorizer.isAuthorized(expectedSenderAddress));
        }
    }

    private static Stream<Arguments> unionBridgeChangeTransferPermissionsAuthorizerProvider() {
        List<RskAddress> mainnetAuthorizers = mapToRskAddress(Collections.singletonList(
            ECKey.fromPublicOnly(Hex.decode(
                "0458fdbe66a1eda5b94eaf3b3ef1bc8439a05a0b13d2bb9d5a1c6ea1d98ed5b0405fd002c884eed4aa1102d812c7347acc6dd172ad4828de542e156bd47cd90282"))));

        List<RskAddress> testnetAuthorizers = mapToRskAddress(Collections.singletonList(
            ECKey.fromPublicOnly(Hex.decode(
                "04ea24f3943dff3b9b8abc59dbdf1bd2c80ec5b61f5c2c6dfcdc189299115d6d567df34c52b7e678cc9934f4d3d5491b6e53fa41a32f58a71200396f1e11917e8f"))));

        List<RskAddress> regtestAuthorizers = mapToRskAddress(Collections.singletonList(
            ECKey.fromPublicOnly(Hex.decode(
                "04ea24f3943dff3b9b8abc59dbdf1bd2c80ec5b61f5c2c6dfcdc189299115d6d567df34c52b7e678cc9934f4d3d5491b6e53fa41a32f58a71200396f1e11917e8f"))));

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), mainnetAuthorizers),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), testnetAuthorizers),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), regtestAuthorizers)
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
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), "0000000000000000000000000000000000000000"),
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
