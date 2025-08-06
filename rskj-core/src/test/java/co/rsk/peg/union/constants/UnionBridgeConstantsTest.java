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
        List<RskAddress> testnetAuthorizers = mapToRskAddresses(Stream.of(
            "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

        List<RskAddress> regtestAuthorizers = mapToRskAddresses(Stream.of(
            "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), Collections.emptyList()),
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
        List<RskAddress> mainnetAuthorizers = mapToRskAddresses(Stream.of(
            "040162aff21e78665eabe736746ed86ca613f9e628289438697cf820ed8ac800e5fe8cbca350f8cf0b3ee4ec3d8c3edec93820d889565d4ae9b4f6e6d012acec09",
            "04ee99364235a33edbd177c0293bd3e13f1c85b2ee6197e66aa7e975fb91183b08b30bf1227468980180e10092decaaeed0ae1c4bcf29d17993569bb3c1b274f83",
            "0462096357f02602ce227580aa36672a5cba4fc461918c9e1697992ee2895dbb6c1fd2be4859135b3be0e42a20b466738aa03f3871390108746d2ecbe7ffb7aad9"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

        List<RskAddress> testnetAuthorizers = mapToRskAddresses(Stream.of(
            "049929eb3c107a65108830f4c221068f42301bd8b054f91bd594944e7fb488fd1c93a8921fb28d3494769598eb271cd2834a31c5bd08fa075170b3da804db00a5b",
            "04c8a5827bfadd2bce6fa782e6c48dd61503d38c86e29381781167cd6371eb56f50bc03c9e9c265ea7e07709b964e0b4b0f3d416955225fcb9202e6763ddd5ca91",
            "0442329d63de5ec5b2f285da7e2f3eb484db3ee5e39066579244211021b81c32d7061922075e2272a8e8a633a5856071eef7e7f800b3d93c9acee91e0f0f37ac2f"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

        List<RskAddress> regtestAuthorizers = mapToRskAddresses(Stream.of(
            "049929eb3c107a65108830f4c221068f42301bd8b054f91bd594944e7fb488fd1c93a8921fb28d3494769598eb271cd2834a31c5bd08fa075170b3da804db00a5b",
            "04c8a5827bfadd2bce6fa782e6c48dd61503d38c86e29381781167cd6371eb56f50bc03c9e9c265ea7e07709b964e0b4b0f3d416955225fcb9202e6763ddd5ca91",
            "0442329d63de5ec5b2f285da7e2f3eb484db3ee5e39066579244211021b81c32d7061922075e2272a8e8a633a5856071eef7e7f800b3d93c9acee91e0f0f37ac2f"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance(), mainnetAuthorizers),
            Arguments.of(UnionBridgeTestNetConstants.getInstance(), testnetAuthorizers),
            Arguments.of(UnionBridgeRegTestConstants.getInstance(), regtestAuthorizers)
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
    void getChangeTransferPermissionsAuthorizer_ok(UnionBridgeConstants unionBridgeConstants, List<RskAddress> expectedSenderAddresses) {
        // Act
        AddressBasedAuthorizer actualUnionBridgeChangeTransferPermissionsAuthorizer = unionBridgeConstants.getChangeTransferPermissionsAuthorizer();

        // Assert
        for (RskAddress expectedSenderAddress : expectedSenderAddresses) {
            Assertions.assertTrue(actualUnionBridgeChangeTransferPermissionsAuthorizer.isAuthorized(expectedSenderAddress));
        }
    }

    private static Stream<Arguments> unionBridgeChangeTransferPermissionsAuthorizerProvider() {
        List<RskAddress> mainnetAuthorizers = mapToRskAddresses(Stream.of(
            "0458fdbe66a1eda5b94eaf3b3ef1bc8439a05a0b13d2bb9d5a1c6ea1d98ed5b0405fd002c884eed4aa1102d812c7347acc6dd172ad4828de542e156bd47cd90282",
            "0486559d73a991df9e5eef1782c41959ecc7e334ef57ddcb6e4ebc500771a50f0c3b889afb9917165db383a9bf9a8e9b4f73abd542109ba06387f016f62df41b0f",
            "048817254d54e9776964e6e1102591474043dd3dae11088919ef6cb37625a66852627b146986cbc3b188ce69ca86468fa11b275a6577e0c7d3a3c6c1b343537e3f"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

        List<RskAddress> testnetAuthorizers = mapToRskAddresses(Stream.of(
            "04ea24f3943dff3b9b8abc59dbdf1bd2c80ec5b61f5c2c6dfcdc189299115d6d567df34c52b7e678cc9934f4d3d5491b6e53fa41a32f58a71200396f1e11917e8f",
            "04cf42ec9eb287adc7196e8d3d2c288542b1db733681c22887e3a3e31eb98504002825ecbe0cd9b61aff3600ffd0ca4542094c75cb0bac5e93be0c7e00b2ead9ea",
            "043a7510e39f8c406fb682c20d0e74e6f18f6ec6cb4bc9718a3c47f9bda741f3333ed39e9854b9ad89f16fccb52453975ff1039dd913addfa6a6c56bcacbd92ff9"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

        List<RskAddress> regtestAuthorizers = mapToRskAddresses(Stream.of(
            "04397d368991eac9bbc7593ea25f99d595dc175f598afc6297dbfd4533d26577ce0b3faa9296bdecd4ef7a58738f6b23b7a71a11753ebe417c660eaa7e36f64fd3",
            "04edde251d0e8a91e118ebc4f6ea1c91a21bd1963a383d77751626a0a3a7685ec4bced2751f9c4bd914252d21799297dab159f45b870bbbccf54ed6f3eb5ad504c",
            "049a630a1bef586e5095cf2a877bc2719840f71791bf9337ef35db36c9c4234cb0a5c3809013a5c4d01cdc0d003944ae21f42caae8d0925ca75dc444990749c688"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList());

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
