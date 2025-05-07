package co.rsk.peg.union;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.union.constants.UnionBridgeMainNetConstants;
import co.rsk.peg.union.constants.UnionBridgeRegTestConstants;
import co.rsk.peg.union.constants.UnionBridgeTestNetConstants;
import co.rsk.test.builders.UnionBridgeSupportBuilder;
import java.util.Optional;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnionBridgeSupportImplTest {

    private static final ActivationConfig.ForBlock lovell700 = ActivationConfigsForTest.lovell700()
        .forBlock(0);
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all()
        .forBlock(0);

    private static final RskAddress authorizerRskAddress = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
            .getAddress());

    private static final RskAddress unionBridgeContractAddress = TestUtils.generateAddress(
        "newUnionBridgeContractAddress");

    private static final RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress(
        "secondNewUnionBridgeContractAddress");

    private UnionBridgeSupport unionBridgeSupport;
    private UnionBridgeSupportBuilder unionBridgeSupportBuilder;
    private StorageAccessor storageAccessor;
    private UnionBridgeStorageProvider unionBridgeStorageProvider;
    private SignatureCache signatureCache;
    private Transaction rskTx;

    @BeforeEach
    void setUp() {
        storageAccessor = new InMemoryStorage();
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
        signatureCache = mock(SignatureCache.class);
        unionBridgeSupportBuilder = UnionBridgeSupportBuilder.builder()
            .withActivations(allActivations)
            .withStorageProvider(unionBridgeStorageProvider)
            .withSignatureCache(signatureCache);
        unionBridgeSupport = unionBridgeSupportBuilder.build();

        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(authorizerRskAddress);
    }

    private static Stream<Arguments> unionBridgeConstantsForSetUnionBridgeContractAddress() {
        return Stream.of(
            Arguments.of(UnionBridgeRegTestConstants.getInstance()),
            Arguments.of(UnionBridgeTestNetConstants.getInstance())
        );
    }

    private static Stream<Arguments> unionBridgeConstantsProvider() {
        return Stream.of(
            Arguments.of(UnionBridgeRegTestConstants.getInstance()),
            Arguments.of(UnionBridgeTestNetConstants.getInstance()),
            Arguments.of(UnionBridgeMainNetConstants.getInstance())
        );
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void setUnionBridgeContractAddressForTestnet_preRSKIP502_whenMeetRequirementsToUpdate_shouldReturnSuccessCode(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(lovell700)
            .withConstants(unionBridgeConstants).build();

        // act
        int result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS.getCode(),
            result
        );
        assertAddressWasSet(unionBridgeContractAddress);
        assertNoAddressIsStored();
    }

    private void assertNoAddressIsStored() {
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertNull(actualRskAddress);
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void setUnionBridgeContractAddressForTestnet_postRSKIP502_whenMeetRequirementsToUpdate_shouldReturnSuccess(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        // act
        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS.getCode(),
            actualResponseCode
        );
        assertAddressWasSet(unionBridgeContractAddress);
        assertNoAddressIsStored();
    }

    private void assertAddressWasSet(RskAddress expectedAddress) {
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
        Assertions.assertTrue(actualAddress.isPresent());
        Assertions.assertEquals(expectedAddress, actualAddress.get());
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void setUnionBridgeContractAddressForTestnet_preRSKIP502_whenAddressIsTheSameAddressInConstants_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        ActivationConfig.ForBlock activations = lovell700;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();

        // act
        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeConstants.getAddress());

        // assert
        Assertions.assertEquals(
            UnionResponseCode.INVALID_VALUE.getCode(),
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    private void assertAddressWasNotSet(ActivationConfig.ForBlock activations) {
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(activations);
        Assertions.assertTrue(actualAddress.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void setUnionBridgeContractAddressForTestnet_postRSKIP502_whenAddressIsTheSameAddressInConstants_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        ActivationConfig.ForBlock activations = allActivations;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();

        // act
        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeConstants.getAddress());

        // assert
        Assertions.assertEquals(
            UnionResponseCode.INVALID_VALUE.getCode(),
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void setUnionBridgeContractAddressForTestnet_whenStoredAddress_shouldReturnSuccessCodeButDoNotStoreAnyAddress(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange

        // Save the address in storage
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(allActivations)
            .withConstants(unionBridgeConstants).build();

        // act
        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            newUnionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS.getCode(),
            actualResponseCode
        );
        assertAddressWasSet(newUnionBridgeContractAddress);

        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertEquals(unionBridgeContractAddress, actualRskAddress);
        Assertions.assertNotEquals(newUnionBridgeContractAddress, actualRskAddress);
    }

    @Test
    void setUnionBridgeContractAddressForTestnet_whenMainnet_shouldReturnEnvironmentDisabledCode() {
        // arrange
        UnionBridgeConstants unionBridgeConstants = UnionBridgeMainNetConstants.getInstance();
        ActivationConfig.ForBlock activations = allActivations;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();

        // act
        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);

        // assert
        int expectedResponseCode = UnionResponseCode.ENVIRONMENT_DISABLED.getCode();
        Assertions.assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void setUnionBridgeContractAddressForTestnet_whenCallerIsNotAuthorized_shouldReturnUnauthorizedCode(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        ActivationConfig.ForBlock activations = allActivations;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notAuthorizedAddress"));

        // act
        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);

        // assert
        int expectedResponseCode = UnionResponseCode.UNAUTHORIZED_CALLER.getCode();
        Assertions.assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsNull_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        ActivationConfig.ForBlock activations = allActivations;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();

        // act
        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            null);

        // assert
        int expectedResponseCode = UnionResponseCode.INVALID_VALUE.getCode();
        Assertions.assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsEmpty_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        ActivationConfig.ForBlock activations = allActivations;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();

        // act
        RskAddress emptyAddress = new RskAddress(new byte[20]);
        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            emptyAddress);

        // assert
        int expectedResponseCode = UnionResponseCode.INVALID_VALUE.getCode();
        Assertions.assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    @Test
    void getUnionBridgeLockingCap_beforeRSKIP502_shouldReturnEmpty() {
        // arrange
        UnionBridgeConstants unionBridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants)
            .withActivations(lovell700)
            .build();

        // act
        Optional<Coin> actualLockingCap = unionBridgeSupport.getLockingCap();

        // assert
        Assertions.assertTrue(actualLockingCap.isEmpty());
        assertNoLockingCapIsStored();
    }

    private void assertNoLockingCapIsStored() {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeCoin
        );
        Assertions.assertNull(storedLockingCap);
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void getUnionBridgeLockingCap_whenNoStoredLockingCap_shouldReturnInitialLockingCap(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        // act
        Optional<Coin> actualLockingCap = unionBridgeSupport.getLockingCap();

        // assert
        Coin expectedInitialLockingCap = unionBridgeConstants.getInitialLockingCap();
        Assertions.assertTrue(actualLockingCap.isPresent());
        Assertions.assertEquals(expectedInitialLockingCap, actualLockingCap.get());
        assertNoLockingCapIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void getUnionBridgeLockingCap_whenStoredLockingCap_shouldReturnStoredLockingCap(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        Coin expectedLockingCap = Coin.FIFTY_COINS;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            expectedLockingCap,
            BridgeSerializationUtils::serializeCoin
        );

        // act
        Optional<Coin> actualLockingCap = unionBridgeSupport.getLockingCap();

        // assert
        Assertions.assertTrue(actualLockingCap.isPresent());
        Assertions.assertEquals(expectedLockingCap, actualLockingCap.get());
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void increaseLockingCap_preRSKIP502_whenMeetRequirementsToIncreaseLockingCap_shouldSetLockingButNotStore(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(lovell700)
            .withConstants(unionBridgeConstants)
            .build();

        Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(unionBridgeConstants.getLockingCapIncrementsMultiplier()).minus(Coin.CENT);

        // act
        int actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(actualResponseCode, UnionResponseCode.SUCCESS.getCode());
        assertNoLockingCapIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void increaseLockingCap_postRSKIP502_whenMeetRequirementsToIncreaseLockingCap_shouldSetButNotStoreLockingCap(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        // Stored a locking cap in the storage to demonstrate that the new locking cap is set but not stored
        Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            initialLockingCap,
            BridgeSerializationUtils::serializeCoin
        );
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants)
            .build();


        Coin newLockingCap = initialLockingCap.multiply(unionBridgeConstants.getLockingCapIncrementsMultiplier()).minus(Coin.CENT);

        // act
        int actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(actualResponseCode, UnionResponseCode.SUCCESS.getCode());
        assertLockingCapWasSet(newLockingCap);
        assertLNewLockingCapWasNotStored(newLockingCap);
    }

    @ParameterizedTest
    @MethodSource("invalidLockingCapProvider")
    void increaseLockingCap_whenInvalidLockingCap_shouldReturnInvalidValue(Coin newLockingCap) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(UnionBridgeMainNetConstants.getInstance())
            .build();

        // act
        int actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(actualResponseCode, UnionResponseCode.INVALID_VALUE.getCode());
        assertNoLockingCapIsStored();
    }

    public static Stream<Arguments> invalidLockingCapProvider() {
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();

        Coin maxRbtc = BridgeMainNetConstants.getInstance().getMaxRbtc();
        Coin moreThanMaxRbtc = maxRbtc.add(Coin.CENT);

        Coin initialLockingCap = bridgeConstants.getInitialLockingCap();
        Coin lessThanInitialLockingCap = initialLockingCap.minus(Coin.CENT);
        Coin maxLockingCap = initialLockingCap.multiply(bridgeConstants.getLockingCapIncrementsMultiplier());
        Coin moreThanMaxLockingCap = maxLockingCap.add(Coin.CENT);

        return Stream.of(
            Arguments.of(Coin.NEGATIVE_SATOSHI),
            Arguments.of(Coin.ZERO),
            Arguments.of(lessThanInitialLockingCap), // less than the initial locking cap
            Arguments.of(initialLockingCap),
            Arguments.of(moreThanMaxLockingCap),
            Arguments.of(moreThanMaxRbtc)
        );
    }

    private void assertLockingCapWasSet(Coin newLockingCap) {
        Optional<Coin> cacheLockingCap = unionBridgeStorageProvider.getLockingCap(allActivations);
        Assertions.assertTrue(cacheLockingCap.isPresent());
        Assertions.assertEquals(newLockingCap, cacheLockingCap.get());
    }

    private void assertLNewLockingCapWasNotStored(Coin newLockingCap) {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeCoin
        );
        Assertions.assertNotEquals(newLockingCap, storedLockingCap);
    }

    @Test
    void increaseLockingCap_whenCallerIsNotAuthorized_shouldReturnUnauthorizedCode() {
        // arrange
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(allActivations)
            .withConstants(bridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notAuthorizedAddress"));
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants)
            .build();
        Coin initialLockingCap = bridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(bridgeConstants.getLockingCapIncrementsMultiplier()).minus(Coin.CENT);

        // act
        int actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(actualResponseCode, UnionResponseCode.UNAUTHORIZED_CALLER.getCode());
        assertNoLockingCapIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void save_preRSKIP502_shouldSetButDoNotStoreGivenAddress(UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(lovell700)
            .withConstants(unionBridgeConstants).build();

        // act
        unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);
        unionBridgeSupport.save();

        // assert
        assertAddressWasSet(unionBridgeContractAddress);
        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsForSetUnionBridgeContractAddress")
    void save_postRSKIP502_shouldSave(UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        int actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);
        Assertions.assertEquals(actualResponseCode, UnionResponseCode.SUCCESS.getCode());

        Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(unionBridgeConstants.getLockingCapIncrementsMultiplier()).minus(Coin.CENT);
        int actualLockingCapResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);
        Assertions.assertEquals(actualLockingCapResponseCode, UnionResponseCode.SUCCESS.getCode());

        // act
        unionBridgeSupport.save();

        // assert
        assertAddressWasSet(unionBridgeContractAddress);
        assertAddressWasStored(unionBridgeContractAddress);
        assertLockingCapWasStored(newLockingCap);
    }

    private void assertAddressWasStored(RskAddress expectedAddress) {
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertEquals(expectedAddress, actualRskAddress);
    }

    private void assertLockingCapWasStored(Coin newLockingCap) {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeCoin
        );
        Assertions.assertEquals(newLockingCap, storedLockingCap);
    }
}
