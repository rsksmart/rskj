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
import java.math.BigInteger;
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

    private static final UnionBridgeConstants unionBridgeConstants = UnionBridgeMainNetConstants.getInstance();
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

    private static Stream<Arguments> testnetAndRegtestConstantsProvider() {
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

    @Test
    void getUnionBridgeContractAddress_beforeRSKIP502_shouldReturnConstantAddress() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants)
            .withActivations(lovell700)
            .build();

        // act
        RskAddress actualUnionBridgeContractAddress = unionBridgeSupport.getUnionBridgeContractAddress();

        // assert
        RskAddress expectedUnionBridgeContractAddress = unionBridgeConstants.getAddress();
        Assertions.assertEquals(expectedUnionBridgeContractAddress, actualUnionBridgeContractAddress);
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void getUnionBridgeContractAddress_whenNoStoredAddress_shouldReturnConstantAddress(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        // act
        RskAddress actualUnionBridgeContractAddress = unionBridgeSupport.getUnionBridgeContractAddress();

        // assert
        RskAddress expectedUnionBridgeContractAddress = unionBridgeConstants.getAddress();
        Assertions.assertEquals(expectedUnionBridgeContractAddress, actualUnionBridgeContractAddress);
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void getUnionBridgeContractAddress_whenStoredAddress_shouldReturnStoredAddress(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();
        // to simulate the case where there is a previous address stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // act
        RskAddress actualUnionBridgeContractAddress = unionBridgeSupport.getUnionBridgeContractAddress();

        // assert
        Assertions.assertEquals(unionBridgeContractAddress, actualUnionBridgeContractAddress);
    }

    @Test
    void getUnionBridgeContractAddress_whenMainnet_shouldReturnConstant() {
        // arrange
        // to simulate the case where there is a previous address stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(allActivations)
            .withConstants(unionBridgeConstants).build();

        // act
        RskAddress actualUnionBridgeContractAddress = unionBridgeSupport.getUnionBridgeContractAddress();

        // assert
        RskAddress expectedUnionBridgeContractAddress = unionBridgeConstants.getAddress();
        Assertions.assertEquals(expectedUnionBridgeContractAddress, actualUnionBridgeContractAddress);
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_preRSKIP502_whenMeetRequirementsToUpdate_shouldReturnSuccessCode(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(lovell700)
            .withConstants(unionBridgeConstants).build();

        // act
        UnionResponseCode result = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS,
            result
        );
        assertAddressWasSet(unionBridgeContractAddress);
        assertNoAddressIsStored();

        // call save and assert that the address is not stored
        unionBridgeSupport.save();
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
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_postRSKIP502_whenMeetRequirementsToUpdate_shouldReturnSuccess(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS,
            actualResponseCode
        );
        assertAddressWasSet(unionBridgeContractAddress);
        assertNoAddressIsStored();

        // call save and assert that the address is stored
        unionBridgeSupport.save();
        assertAddressWasStored(unionBridgeContractAddress);
    }

    private void assertAddressWasSet(RskAddress expectedAddress) {
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress(allActivations);
        Assertions.assertTrue(actualAddress.isPresent());
        Assertions.assertEquals(expectedAddress, actualAddress.get());
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_preRSKIP502_whenAddressIsTheSameAddressInConstants_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        ActivationConfig.ForBlock activations = lovell700;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeConstants.getAddress());

        // assert
        Assertions.assertEquals(
            UnionResponseCode.INVALID_VALUE,
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
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_postRSKIP502_whenAddressIsTheSameAddressInConstants_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        ActivationConfig.ForBlock activations = allActivations;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeConstants.getAddress());

        // assert
        Assertions.assertEquals(
            UnionResponseCode.INVALID_VALUE,
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_whenStoredAddress_shouldUpdateToNewUnionBridgeContractAddress(
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
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            newUnionBridgeContractAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS,
            actualResponseCode
        );
        assertAddressWasSet(newUnionBridgeContractAddress);

        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertEquals(unionBridgeContractAddress, actualRskAddress);
        Assertions.assertNotEquals(newUnionBridgeContractAddress, actualRskAddress);

        // call save and assert that the new address is stored
        unionBridgeSupport.save();
        assertAddressWasStored(newUnionBridgeContractAddress);
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
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);

        // assert
        UnionResponseCode expectedResponseCode = UnionResponseCode.ENVIRONMENT_DISABLED;
        Assertions.assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
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
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);

        // assert
        UnionResponseCode expectedResponseCode = UnionResponseCode.UNAUTHORIZED_CALLER;
        Assertions.assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsNull_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        ActivationConfig.ForBlock activations = allActivations;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            null);

        // assert
        UnionResponseCode expectedResponseCode = UnionResponseCode.INVALID_VALUE;
        Assertions.assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasNotSet(activations);
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsEmpty_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        ActivationConfig.ForBlock activations = allActivations;
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(activations)
            .withConstants(unionBridgeConstants).build();

        // act
        RskAddress emptyAddress = new RskAddress(new byte[20]);
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            emptyAddress);

        // assert
        UnionResponseCode expectedResponseCode = UnionResponseCode.INVALID_VALUE;
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
    void increaseLockingCap_preRSKIP502_whenMeetRequirementsToIncreaseLockingCap_shouldIncreaseLockingButNotStore(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(lovell700)
            .withConstants(unionBridgeConstants)
            .build();

        Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(unionBridgeConstants.getLockingCapIncrementsMultiplier());

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertNoLockingCapIsStored();

        // call save and assert that the new locking cap is not stored
        unionBridgeSupport.save();
        assertNoLockingCapIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void increaseLockingCap_postRSKIP502_whenMeetRequirementsToIncreaseLockingCap_shouldIncreaseLockingCap(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants)
            .build();
        Coin newLockingCap = initialLockingCap.multiply(unionBridgeConstants.getLockingCapIncrementsMultiplier());

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLockingCapWasSet(newLockingCap);
        assertNewLockingCapWasNotStored(newLockingCap);

        // call save and assert that the new locking cap is stored
        unionBridgeSupport.save();
        assertLockingCapWasStored(newLockingCap);
    }

    @Test
    void increaseLockingCap_postRSKIP502_whenMoreThanMaxRbtc_shouldIncreaseLockingCap() {
        // arrange
        Coin moreThanMaxRbtc = BridgeMainNetConstants.getInstance().getMaxRbtc().add(Coin.COIN);

        // Stored a locking cap in the storage to demonstrate that the new locking cap is valid when is possible
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            moreThanMaxRbtc.divide(2),
            BridgeSerializationUtils::serializeCoin
        );

        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(UnionBridgeMainNetConstants.getInstance())
            .build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, moreThanMaxRbtc);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLockingCapWasSet(moreThanMaxRbtc);
        assertNewLockingCapWasNotStored(moreThanMaxRbtc);

        // call save and assert that the new locking cap is stored
        unionBridgeSupport.save();
        assertLockingCapWasStored(moreThanMaxRbtc);
    }

    @ParameterizedTest
    @MethodSource("invalidLockingCapProvider")
    void increaseLockingCap_whenInvalidLockingCap_shouldReturnInvalidValue(Coin newLockingCap) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(UnionBridgeMainNetConstants.getInstance())
            .build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);
        assertNoLockingCapIsStored();

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoLockingCapIsStored();
    }

    private static Stream<Arguments> invalidLockingCapProvider() {
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();

        Coin initialLockingCap = bridgeConstants.getInitialLockingCap();
        Coin lessThanInitialLockingCap = initialLockingCap.minus(Coin.CENT);
        Coin lockingCapAboveMaxIncreaseAmount = initialLockingCap
            .multiply(bridgeConstants.getLockingCapIncrementsMultiplier())
            .add(Coin.CENT);

        return Stream.of(
            Arguments.of(Coin.NEGATIVE_SATOSHI),
            Arguments.of(Coin.ZERO),
            Arguments.of(lessThanInitialLockingCap), // less than the initial locking cap
            Arguments.of(initialLockingCap),
            Arguments.of(lockingCapAboveMaxIncreaseAmount)
        );
    }

    private void assertLockingCapWasSet(Coin newLockingCap) {
        Optional<Coin> cacheLockingCap = unionBridgeStorageProvider.getLockingCap(allActivations);
        Assertions.assertTrue(cacheLockingCap.isPresent());
        Assertions.assertEquals(newLockingCap, cacheLockingCap.get());
    }

    private void assertNewLockingCapWasNotStored(Coin newLockingCap) {
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
        Coin newLockingCap = initialLockingCap.multiply(bridgeConstants.getLockingCapIncrementsMultiplier());

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);
        assertNoLockingCapIsStored();

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoLockingCapIsStored();
    }

    @Test
    void requestUnionRbtc_whenCallerIsNotUnionBridgeContractAddress_shouldReturnUnauthorizedCaller() {
        // arrange
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(allActivations)
            .withConstants(bridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notUnionBridgeContractAddress"));
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants)
            .build();

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        co.rsk.core.Coin amountRequested = new co.rsk.core.Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        Assertions.assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoUnionRbtcIsStored();
    }

    @Test
    void requestUnionRbtc_whenGivenAmountNull_shouldReturnInvalidValue() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(allActivations)
            .withConstants(unionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeConstants.getAddress());

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, null);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoUnionRbtcIsStored();
    }
    
    private static Stream<Arguments> invalidAmountArgProvider() {
        Coin surpassingLockingCap = unionBridgeConstants.getInitialLockingCap()
            .multiply(unionBridgeConstants.getLockingCapIncrementsMultiplier()).add(Coin.COIN);
        return Stream.of(
            Arguments.of(new co.rsk.core.Coin(BigInteger.valueOf(-1))),
            Arguments.of(new co.rsk.core.Coin(BigInteger.valueOf(-10))),
            Arguments.of(new co.rsk.core.Coin(BigInteger.valueOf(-100))),
            Arguments.of(co.rsk.core.Coin.ZERO),
            Arguments.of(co.rsk.core.Coin.fromBitcoin(surpassingLockingCap))
        );
    }

    @ParameterizedTest()
    @MethodSource("invalidAmountArgProvider")
    void requestUnionRbtc_whenInvalidValue_shouldReturnInvalidValue(co.rsk.core.Coin amountRequested) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(allActivations)
            .withConstants(unionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeConstants.getAddress());
        
        // To simulate the case where a locking cap is store
        Coin newLockingCap = unionBridgeConstants.getInitialLockingCap()
            .multiply(unionBridgeConstants.getLockingCapIncrementsMultiplier());
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            newLockingCap,
            BridgeSerializationUtils::serializeCoin
        );
        
        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoUnionRbtcIsStored();
    }

    private static Stream<Arguments> validAmountArgProvider() {
        Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        co.rsk.core.Coin amountRequestEqualToLockingCap = co.rsk.core.Coin.fromBitcoin(initialLockingCap);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei

        return Stream.of(
            Arguments.of(new co.rsk.core.Coin(BigInteger.valueOf(1))),
            Arguments.of(new co.rsk.core.Coin(BigInteger.valueOf(100))),
            Arguments.of(new co.rsk.core.Coin(BigInteger.valueOf(1000))),
            Arguments.of(new co.rsk.core.Coin(oneEth)),
            Arguments.of(amountRequestEqualToLockingCap)
        );
    }

    @ParameterizedTest
    @MethodSource("validAmountArgProvider")
    void requestUnionRbtc_whenValidValue_shouldReturnSuccessCode(co.rsk.core.Coin amountRequested) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(allActivations)
            .withConstants(unionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeConstants.getAddress());

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        assertUnionRbtcWasStored(amountRequested);
    }

    @Test
    void requestUnionRbtc_whenAmountToRequestEqualToLockingCap_shouldReturnSuccessCode() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withActivations(allActivations)
            .withConstants(unionBridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeConstants.getAddress());

        Coin lockingCap = Coin.FIFTY_COINS;
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            lockingCap,
            BridgeSerializationUtils::serializeCoin
        );

        // To simulate the case where a weis transferred is store
        Coin twentyFiveRbtc = lockingCap.div(2);
        co.rsk.core.Coin weisTransferred = co.rsk.core.Coin.fromBitcoin(twentyFiveRbtc);
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            weisTransferred,
            BridgeSerializationUtils::serializeRskCoin
        );

        co.rsk.core.Coin amountToRequest = co.rsk.core.Coin.fromBitcoin(twentyFiveRbtc);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountToRequest);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        assertUnionRbtcWasStored(amountToRequest);
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
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
        assertNoUnionRbtcIsStored();
    }

    private void assertNoUnionRbtcIsStored() {
        co.rsk.core.Coin actualAmountTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        Assertions.assertNull(actualAmountTransferred);
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void save_postRSKIP502_shouldSave(UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(unionBridgeConstants.getLockingCapIncrementsMultiplier());
        UnionResponseCode actualLockingCapResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualLockingCapResponseCode);

        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeContractAddress);
        co.rsk.core.Coin amountRequested = new co.rsk.core.Coin(BigInteger.valueOf(100));
        UnionResponseCode actualRequestUnionRbtcResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);
        Assertions.assertEquals(UnionResponseCode.SUCCESS,  actualRequestUnionRbtcResponseCode);

        // act
        unionBridgeSupport.save();

        // assert
        assertAddressWasSet(unionBridgeContractAddress);
        assertAddressWasStored(unionBridgeContractAddress);
        assertLockingCapWasStored(newLockingCap);
        assertUnionRbtcWasStored(amountRequested);
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void save_whenUnionBridgeContractAddressIsUpdated_shouldSave(UnionBridgeConstants unionBridgeConstants){
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        // to simulate the case where the address is already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            newUnionBridgeContractAddress);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // act
        unionBridgeSupport.save();

        assertAddressWasSet(newUnionBridgeContractAddress);
        assertAddressWasStored(newUnionBridgeContractAddress);
        assertNoLockingCapIsStored();
        assertNoUnionRbtcIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void save_whenLockingCapIsIncreased_shouldSave(UnionBridgeConstants unionBridgeConstants){
        Coin newLockingCap = BridgeMainNetConstants.getInstance().getMaxRbtc();

        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        // to simulate the case where the address is already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            newLockingCap.divide(2),
            BridgeSerializationUtils::serializeCoin
        );

        UnionResponseCode actualLockingCapResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualLockingCapResponseCode);

        // act
        unionBridgeSupport.save();

        assertLockingCapWasSet(newLockingCap);
        assertLockingCapWasStored(newLockingCap);
        assertNoAddressIsStored();
        assertNoUnionRbtcIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void save_whenWeisTransferredBalanceIsIncreased_shouldSave(UnionBridgeConstants unionBridgeConstants){
        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        co.rsk.core.Coin storedWeisTransferredAmount = new co.rsk.core.Coin(oneEth);
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeConstants.getAddress());

        // to simulate the case where weis transferred balance is already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            storedWeisTransferredAmount,
            BridgeSerializationUtils::serializeRskCoin
        );

        co.rsk.core.Coin amountRequested = new co.rsk.core.Coin(oneEth).multiply(BigInteger.TWO);
        UnionResponseCode actualRequestUnionRbtcResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);
        Assertions.assertEquals(UnionResponseCode.SUCCESS,  actualRequestUnionRbtcResponseCode);

        // act
        unionBridgeSupport.save();

        // assert
        assertUnionRbtcWasStored(amountRequested);
        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
    }

    private void assertAddressWasStored(RskAddress newUnionBridgeContractAddress) {
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertEquals(newUnionBridgeContractAddress, actualRskAddress);
    }

    private void assertLockingCapWasStored(Coin newLockingCap) {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeCoin
        );
        Assertions.assertEquals(newLockingCap, storedLockingCap);
    }

    private void assertUnionRbtcWasStored(co.rsk.core.Coin amountRequested) {
        co.rsk.core.Coin actualAmountTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        Assertions.assertEquals(amountRequested, actualAmountTransferred);
    }
}
