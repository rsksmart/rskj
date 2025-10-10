package co.rsk.peg.union;

import static co.rsk.peg.BridgeSupportTestUtil.assertEventWasEmittedWithExpectedData;
import static co.rsk.peg.BridgeSupportTestUtil.assertEventWasEmittedWithExpectedTopics;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.union.constants.UnionBridgeMainNetConstants;
import co.rsk.peg.union.constants.UnionBridgeRegTestConstants;
import co.rsk.peg.union.constants.UnionBridgeTestNetConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.UnionBridgeSupportBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class UnionBridgeSupportImplTest {

    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
    private static final BridgeConstants mainnetConstants = BridgeMainNetConstants.getInstance();
    private static final UnionBridgeConstants mainnetUnionBridgeConstants = UnionBridgeMainNetConstants.getInstance();
    private static final RskAddress mainnetUnionBridgeContractAddress = mainnetUnionBridgeConstants.getAddress();

    private static final RskAddress changeUnionAddressAuthorizer = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
            .getAddress());

    private static final RskAddress changeLockingCapAuthorizer = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "049929eb3c107a65108830f4c221068f42301bd8b054f91bd594944e7fb488fd1c93a8921fb28d3494769598eb271cd2834a31c5bd08fa075170b3da804db00a5b"))
            .getAddress());

    private static final RskAddress changeTransferPermissionsAuthorizer = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "04ea24f3943dff3b9b8abc59dbdf1bd2c80ec5b61f5c2c6dfcdc189299115d6d567df34c52b7e678cc9934f4d3d5491b6e53fa41a32f58a71200396f1e11917e8f"))
            .getAddress());

    private static final RskAddress unionBridgeContractAddress = TestUtils.generateAddress(
        "newUnionBridgeContractAddress");

    private static final RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress(
        "secondNewUnionBridgeContractAddress");

    private final UnionBridgeSupportBuilder unionBridgeSupportBuilder = UnionBridgeSupportBuilder.builder();;

    private UnionBridgeSupport unionBridgeSupport;
    private StorageAccessor storageAccessor;
    private UnionBridgeStorageProvider unionBridgeStorageProvider;
    private SignatureCache signatureCache;
    private Transaction rskTx;
    private List<LogInfo> logs;

    @BeforeEach
    void setUp() {
        storageAccessor = new InMemoryStorage();
        unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(storageAccessor);
        signatureCache = mock(SignatureCache.class);

        logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(
            mainnetConstants,
            allActivations,
            logs
        );
        unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants)
            .withStorageProvider(unionBridgeStorageProvider)
            .withSignatureCache(signatureCache)
            .withEventLogger(eventLogger);
        unionBridgeSupport = unionBridgeSupportBuilder.build();

        rskTx = mock(Transaction.class);
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
            .withConstants(mainnetUnionBridgeConstants).build();

        // act
        RskAddress actualUnionBridgeContractAddress = unionBridgeSupport.getUnionBridgeContractAddress();

        // assert
        Assertions.assertEquals(mainnetUnionBridgeContractAddress, actualUnionBridgeContractAddress);
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_whenMeetRequirementsToUpdate_shouldReturnSuccess(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(changeUnionAddressAuthorizer);

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

    private void assertNoAddressIsStored() {
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertNull(actualRskAddress);
    }

    private void assertAddressWasSet(RskAddress expectedAddress) {
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        Assertions.assertTrue(actualAddress.isPresent());
        Assertions.assertEquals(expectedAddress, actualAddress.get());
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_whenAddressIsTheSameAddressInConstants_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();
        RskAddress newUnionBridgeAddress = unionBridgeConstants.getAddress();
        when(rskTx.getSender(signatureCache)).thenReturn(changeUnionAddressAuthorizer);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            newUnionBridgeAddress);

        // assert
        Assertions.assertEquals(
            UnionResponseCode.SUCCESS,
            actualResponseCode
        );
        assertAddressWasSet(newUnionBridgeAddress);

        // call save and assert that the address is stored
        unionBridgeSupport.save();

        // assert that the address was stored
        assertAddressWasStored(newUnionBridgeAddress);
    }

    private void assertAddressWasNotSet() {
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        Assertions.assertTrue(actualAddress.isEmpty());
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
            .withConstants(unionBridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(changeUnionAddressAuthorizer);

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
        unionBridgeSupport = unionBridgeSupportBuilder
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
        assertAddressWasNotSet();
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_whenCallerIsNotAuthorized_shouldReturnUnauthorizedCode(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
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
        assertAddressWasNotSet();
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsNull_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(changeUnionAddressAuthorizer);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            null);

        // assert
        UnionResponseCode expectedResponseCode = UnionResponseCode.INVALID_VALUE;
        Assertions.assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasNotSet();
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsEmpty_shouldReturnInvalidValue(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(changeUnionAddressAuthorizer);

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
        assertAddressWasNotSet();
        assertNoAddressIsStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void getLockingCap_whenNoStoredLockingCap_shouldReturnInitialLockingCap(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        // act
        Coin actualLockingCap = unionBridgeSupport.getLockingCap();

        // assert
        Coin expectedInitialLockingCap = unionBridgeConstants.getInitialLockingCap();
        Assertions.assertEquals(expectedInitialLockingCap, actualLockingCap);
        assertNoLockingCapIsStored();
    }

    private void assertNoLockingCapIsStored() {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        Assertions.assertNull(storedLockingCap);
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void getLockingCap_whenStoredLockingCap_shouldReturnStoredLockingCap(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin expectedLockingCap = new Coin(oneEth).multiply(BigInteger.valueOf(50L));
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            expectedLockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );

        // act
        Coin actualLockingCap = unionBridgeSupport.getLockingCap();

        // assert
        Assertions.assertEquals(expectedLockingCap, actualLockingCap);
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void increaseLockingCap_whenMeetRequirementsToIncreaseLockingCap_shouldIncreaseLockingCap(
        UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants)
            .build();

        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);

        Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(BigInteger.valueOf(unionBridgeConstants.getLockingCapIncrementsMultiplier()));

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLockingCapWasSet(newLockingCap);
        assertNewLockingCapWasNotStored(newLockingCap);
        assertLogUnionLockingCapIncreased(initialLockingCap, newLockingCap);

        // call save and assert that the new locking cap is stored
        unionBridgeSupport.save();
        assertLockingCapWasStored(newLockingCap);
    }

    @Test
    void increaseLockingCap_whenMoreThanMaxRbtc_shouldIncreaseLockingCap() {
        // arrange
        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin moreThanMaxRbtc = Coin.fromBitcoin(
            BridgeMainNetConstants.getInstance().getMaxRbtc()).add(new Coin(oneEth));

        int lockingCapIncrementsMultiplier = UnionBridgeMainNetConstants.getInstance()
            .getLockingCapIncrementsMultiplier();
        // Stored the half of [maxRbtc + 1]. Then the max locking cap allowed to increase is: = (maxRbtc + 1) * lockingCapIncrementsMultiplier
        Coin storedLockingCap = moreThanMaxRbtc.divide(BigInteger.valueOf(lockingCapIncrementsMultiplier));
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            storedLockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );

        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);

        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(UnionBridgeMainNetConstants.getInstance())
            .build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, moreThanMaxRbtc);

        // assert that new_locking_cap is allowed.
        // new locking cap surpassing the maxRbtc is allowed because it meets the condition:
        // current_locking_cap <= new_locking_cap <= current_locking_cap * increment_multiplier
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLockingCapWasSet(moreThanMaxRbtc);
        assertNewLockingCapWasNotStored(moreThanMaxRbtc);
        assertLogUnionLockingCapIncreased(storedLockingCap, moreThanMaxRbtc);

        // call save and assert that the new locking cap is stored
        unionBridgeSupport.save();
        assertLockingCapWasStored(moreThanMaxRbtc);
    }

    @ParameterizedTest
    @MethodSource("invalidLockingCapProvider")
    void increaseLockingCap_whenInvalidLockingCap_shouldReturnInvalidValue(Coin newLockingCap) {
        // arrange
        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);

        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants)
            .build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);
        assertNoLockingCapIsStored();
        assertNoEventWasEmitted();

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoLockingCapIsStored();
    }

    private static Stream<Arguments> invalidLockingCapProvider() {
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();

        Coin initialLockingCap = bridgeConstants.getInitialLockingCap();
        Coin lessThanInitialLockingCap = initialLockingCap.subtract(Coin.valueOf(1L));
        Coin lockingCapAboveMaxIncreaseAmount = initialLockingCap
            .multiply(BigInteger.valueOf(bridgeConstants.getLockingCapIncrementsMultiplier()))
            .add(Coin.valueOf(1L));

        return Stream.of(
            Arguments.of(Coin.valueOf(-1L)),
            Arguments.of(Coin.ZERO),
            Arguments.of(lessThanInitialLockingCap), // less than the initial locking cap
            Arguments.of(initialLockingCap),
            Arguments.of(lockingCapAboveMaxIncreaseAmount)
        );
    }

    private void assertLockingCapWasSet(Coin newLockingCap) {
        Optional<Coin> cacheLockingCap = unionBridgeStorageProvider.getLockingCap();
        Assertions.assertTrue(cacheLockingCap.isPresent());
        Assertions.assertEquals(newLockingCap, cacheLockingCap.get());
    }

    private void assertNewLockingCapWasNotStored(Coin newLockingCap) {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        Assertions.assertNotEquals(newLockingCap, storedLockingCap);
    }

    @Test
    void increaseLockingCap_whenCallerIsNotAuthorized_shouldReturnUnauthorizedCode() {
        // arrange
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notAuthorizedAddress"));
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants)
            .build();
        Coin initialLockingCap = bridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(BigInteger.valueOf(bridgeConstants.getLockingCapIncrementsMultiplier()));

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        Assertions.assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);
        assertNoLockingCapIsStored();
        assertNoEventWasEmitted();

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoLockingCapIsStored();
    }

    @ParameterizedTest
    @CsvSource({
        "false, false",
        "false, true"
    })
    void requestUnionRbtc_whenRequestIsDisabled_shouldReturnRequestDisabled(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        setupTransferPermissions(requestEnabled, releaseEnabled);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountRequested = new Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        Assertions.assertEquals(UnionResponseCode.REQUEST_DISABLED, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoWeisTransferredIsStored();
    }

    private void setupTransferPermissions(boolean requestEnabled, boolean releaseEnabled) {
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            requestEnabled? 1L: 0L,
            BridgeSerializationUtils::serializeLong
        );

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            releaseEnabled? 1L: 0L,
            BridgeSerializationUtils::serializeLong
        );
    }

    @Test
    void requestUnionRbtc_whenRequestIsEnabledByDefault_shouldReturnSuccessCode() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountRequested = new Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(amountRequested);
    }

    @ParameterizedTest
    @CsvSource({
        "true, false",
        "true, true"
    })
    void requestUnionRbtc_whenRequestIsEnabled_shouldReturnSuccessCode(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        setupTransferPermissions(requestEnabled, releaseEnabled);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountRequested = new Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(amountRequested);
    }

    @Test
    void requestUnionRbtc_whenCallerIsNotUnionBridgeContractAddress_shouldReturnUnauthorizedCaller() {
        // arrange
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notUnionBridgeContractAddress"));
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants)
            .build();

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountRequested = new Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        Assertions.assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoWeisTransferredIsStored();
    }

    @Test
    void requestUnionRbtc_whenGivenAmountNull_shouldReturnInvalidValue() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, null);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoWeisTransferredIsStored();
    }
    
    private static Stream<Arguments> invalidAmountArgProvider() {
        Coin surpassingLockingCap = mainnetUnionBridgeConstants.getInitialLockingCap()
            .multiply(BigInteger.valueOf(mainnetUnionBridgeConstants.getLockingCapIncrementsMultiplier()))
            .add(Coin.valueOf(1L));
        return Stream.of(
            Arguments.of(new Coin(BigInteger.valueOf(-1))),
            Arguments.of(new Coin(BigInteger.valueOf(-10))),
            Arguments.of(new Coin(BigInteger.valueOf(-100))),
            Arguments.of(Coin.ZERO),
            Arguments.of(surpassingLockingCap)
        );
    }

    @ParameterizedTest()
    @MethodSource("invalidAmountArgProvider")
    void requestUnionRbtc_whenInvalidValue_shouldReturnInvalidValue(Coin amountRequested) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);
        
        // To simulate the case where a locking cap is store
        Coin newLockingCap = mainnetUnionBridgeConstants.getInitialLockingCap()
            .multiply(BigInteger.valueOf(mainnetUnionBridgeConstants.getLockingCapIncrementsMultiplier()));
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            newLockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );
        
        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoWeisTransferredIsStored();
    }

    private static Stream<Arguments> validAmountArgProvider() {
        Coin amountRequestEqualToLockingCap = mainnetUnionBridgeConstants.getInitialLockingCap();
        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei

        return Stream.of(
            Arguments.of(new Coin(BigInteger.valueOf(1))),
            Arguments.of(new Coin(BigInteger.valueOf(100))),
            Arguments.of(new Coin(BigInteger.valueOf(1000))),
            Arguments.of(new Coin(oneEth)),
            Arguments.of(amountRequestEqualToLockingCap)
        );
    }

    @ParameterizedTest
    @MethodSource("validAmountArgProvider")
    void requestUnionRbtc_whenValidValue_shouldReturnSuccessCode(Coin amountRequested) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(amountRequested);
    }

    @Test
    void requestUnionRbtc_whenNewTotalWeisTransferredAmountEqualToLockingCap_shouldReturnSuccessCode() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        BigInteger oneRbtc = BigInteger.TEN.pow(18);
        Coin initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(1000L));

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            initialLockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );

        Coin currentWeisTransferredAmount = initialLockingCap.divide(BigInteger.valueOf(2L)); // 500 RBTC
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            currentWeisTransferredAmount,
            BridgeSerializationUtils::serializeRskCoin
        );

        Coin amountToRequest = initialLockingCap.divide(BigInteger.valueOf(2L)); // 500 RBTC

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx,
            amountToRequest);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();

        // The total weis transferred amount should equal the locking cap. (500 RBTC + 500 RBTC = 1000 RBTC)
        assertWeisTransferredStoredAmount(initialLockingCap);
    }

    @Test
    void requestUnionRbtc_whenNewTotalWeisTransferredAmountSurpassLockingCap_shouldReturnInvalidValue() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        BigInteger oneRbtc = BigInteger.TEN.pow(18);
        Coin initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(1000L));

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            initialLockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );

        Coin currentWeisTransferredAmount = initialLockingCap.divide(BigInteger.valueOf(2L)); // 500 RBTC
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            currentWeisTransferredAmount,
            BridgeSerializationUtils::serializeRskCoin
        );

        Coin amountToRequest = initialLockingCap.divide(BigInteger.valueOf(2L)).add(Coin.valueOf(1)); // 500 RBTC + 1 wei

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx,
            amountToRequest);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

        Coin storedWeisTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        // The total weis transferred amount should not change, it should still be 500 RBTC
        Assertions.assertEquals(currentWeisTransferredAmount, storedWeisTransferred);
    }

    private void assertNoWeisTransferredIsStored() {
        Coin actualAmountTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        Assertions.assertNull(actualAmountTransferred);
    }

    @ParameterizedTest
    @CsvSource({
        "false, false",
        "true, false"
    })
    void releaseUnionRbtc_whenReleaseIsDisabled_shouldReturnReleaseDisabled(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        setupTransferPermissions(requestEnabled, releaseEnabled);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin weisTransferredToUnionBridge = new Coin(oneEth.multiply(BigInteger.TEN)); // 10 RBTC
        Coin amountToRelease = new Coin(oneEth); // 1 RBTC
        when(rskTx.getValue()).thenReturn(amountToRelease);

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            weisTransferredToUnionBridge,
            BridgeSerializationUtils::serializeRskCoin
        );

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.RELEASE_DISABLED, actualResponseCode);

        // call save and assert that weisTransferredToUnionBridge still equals the original amount
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(weisTransferredToUnionBridge);
    }

    @Test
    void releaseUnionRbtc_whenReleaseIsEnabledByDefault_shouldReturnSuccessCode() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin weisTransferredToUnionBridge = new Coin(oneEth.multiply(BigInteger.TEN)); // 10 RBTC
        Coin amountToRelease = new Coin(oneEth); // 1 RBTC
        when(rskTx.getValue()).thenReturn(amountToRelease);

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            weisTransferredToUnionBridge,
            BridgeSerializationUtils::serializeRskCoin
        );

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionRbtcReleased(amountToRelease);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        Coin expectedWeisTransferred = weisTransferredToUnionBridge.subtract(amountToRelease); // 10 RBTC - 1 RBTC = 9 RBTC
        assertWeisTransferredStoredAmount(expectedWeisTransferred);
    }

    @ParameterizedTest
    @CsvSource({
        "false, true",
        "true, true"
    })
    void releaseUnionRbtc_whenReleaseIsEnabled_shouldReturnSuccessCode(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        setupTransferPermissions(requestEnabled, releaseEnabled);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin weisTransferredToUnionBridge = new Coin(oneEth.multiply(BigInteger.TEN)); // 10 RBTC
        Coin amountToRelease = new Coin(oneEth.divide(BigInteger.TWO)); // 0.5 RBTC
        when(rskTx.getValue()).thenReturn(amountToRelease);

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            weisTransferredToUnionBridge,
            BridgeSerializationUtils::serializeRskCoin
        );

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionRbtcReleased(amountToRelease);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        Coin expectedWeisTransferred = weisTransferredToUnionBridge.subtract(amountToRelease); // 10 RBTC - 0.5 RBTC = 9.5 RBTC
        assertWeisTransferredStoredAmount(expectedWeisTransferred);
    }

    @Test
    void releaseUnionRbtc_whenCallerIsNotUnionBridgeContractAddress_shouldReturnUnauthorizedCaller() {
        // arrange
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notUnionBridgeContractAddress"));

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin weisTransferredToUnionBridge = new Coin(oneEth.multiply(BigInteger.TEN)); // 10 RBTC
        Coin amountToRelease = new Coin(oneEth); // 1 RBTC
        when(rskTx.getValue()).thenReturn(amountToRelease);

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            weisTransferredToUnionBridge,
            BridgeSerializationUtils::serializeRskCoin
        );

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);

        // call save and assert that weisTransferredToUnionBridge still equals the original amount
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(weisTransferredToUnionBridge);
    }

    @Test
    void releaseUnionRbtc_whenGivenAmountNull_shouldReturnInvalidValue() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);
        when(rskTx.getValue()).thenReturn(null);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoWeisTransferredIsStored();
    }

    @ParameterizedTest
    @MethodSource("invalidAmountArgProvider")
    void releaseUnionRbtc_whenInvalidValue_shouldReturnInvalidValue(Coin amountToRelease) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        when(rskTx.getValue()).thenReturn(amountToRelease);

        // To simulate the case where a locking cap is store
        Coin lockingCap = mainnetUnionBridgeConstants.getInitialLockingCap()
            .multiply(BigInteger.valueOf(
                mainnetUnionBridgeConstants.getLockingCapIncrementsMultiplier()));
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            lockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );

        // To simulate the case where weisTransferredToUnionBridge is stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            lockingCap, // Use the same value as the locking cap to test the invalid value surpassing the locking cap
            BridgeSerializationUtils::serializeRskCoin
        );

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

        // assert that the weisTransferredToUnionBridge is not changed
        assertWeisTransferredStoredAmount(lockingCap);

        // call save and assert that weisTransferredToUnionBridge still equals the original amount
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(lockingCap);
    }

    @ParameterizedTest
    @MethodSource("validAmountArgProvider")
    void releaseUnionRbtc_whenValidValue_shouldReturnSuccessCode(Coin amountToRelease) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        when(rskTx.getValue()).thenReturn(amountToRelease);

        // To simulate the case where a locking cap is store
        Coin lockingCap = mainnetUnionBridgeConstants.getInitialLockingCap()
            .multiply(BigInteger.valueOf(
                mainnetUnionBridgeConstants.getLockingCapIncrementsMultiplier()));
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            lockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );

        // To simulate the case where weisTransferredToUnionBridge is stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            lockingCap, // Use the same value as the locking cap to test the invalid value surpassing the locking cap
            BridgeSerializationUtils::serializeRskCoin
        );

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionRbtcReleased(amountToRelease);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        Coin expectedWeisTransferred = lockingCap.subtract(amountToRelease);
        assertWeisTransferredStoredAmount(expectedWeisTransferred);
    }

    @Test
    void releaseUnionRbtc_whenNewTotalWeisTransferredAmountEqualToZero_shouldReturnSuccessCode() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        Coin minimumPeginTxValue = Coin.fromBitcoin(mainnetConstants.getMinimumPeginTxValue(
            allActivations));
        when(rskTx.getValue()).thenReturn(minimumPeginTxValue);

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            minimumPeginTxValue,
            BridgeSerializationUtils::serializeRskCoin
        );

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionRbtcReleased(minimumPeginTxValue);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();

        // The total weis transferred amount should equal zero.
        assertWeisTransferredStoredAmount(Coin.ZERO);
    }

    @Test
    void releaseUnionRbtc_whenNewTotalWeisTransferredAmountLessThanZero_shouldReturnInvalidValue() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        Coin minimumPeginTxValue = Coin.fromBitcoin(mainnetConstants.getMinimumPeginTxValue(
            allActivations));
        when(rskTx.getValue()).thenReturn(minimumPeginTxValue.add(Coin.valueOf(1)));

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            minimumPeginTxValue,
            BridgeSerializationUtils::serializeRskCoin
        );

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

        // The total weis transferred amount should not change, it should still be the minimum pegin tx value.
        assertWeisTransferredStoredAmount(minimumPeginTxValue);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();

        // The total weis transferred amount should not change, it should still be the minimum pegin tx value.
        assertWeisTransferredStoredAmount(minimumPeginTxValue);

        // since this an illegal state and means Union Bridge is malfunctioning, we should
        // assert that union bridge enters pause mode
        assertUnionBridgeEntersPauseModeWhenMalFunctioning();
    }

    @Test
    void releaseUnionRbtc_whenRequestUnionRbtcIsCalledBeforeRelease_shouldReturnSuccess() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountToRequest = new Coin(oneEth.multiply(BigInteger.TEN)); // 10 RBTC
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountToRequest);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        Coin amountToRelease = new Coin(oneEth.multiply(BigInteger.TWO)); // 2 RBTC
        when(rskTx.getValue()).thenReturn(amountToRelease);

        // act
        actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionRbtcReleased(amountToRelease);

        // assert that weisTransferredToUnionBridge is no stored yet
        assertNoWeisTransferredIsStored();

        // call save and assert
        unionBridgeSupport.save();

        // The total weis transferred amount should equal the amount requested minus the amount released.
        Coin expectedWeisTransferred = amountToRequest.subtract(amountToRelease);
        assertWeisTransferredStoredAmount(expectedWeisTransferred);
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "true, false",
        "false, true",
        "false, false"
    })
    void setTransferPermissions_whenCallerIsNotAuthorized_shouldReturnUnauthorizedCode(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notAuthorizedAddress"));

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, requestEnabled, releaseEnabled);

        // assert
        Assertions.assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);
        assertNoEventWasEmitted();

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoTransferPermissionsWereStored();
    }

    private void assertNoTransferPermissionsWereStored() {
        Optional<Long> retrievedUnionBridgeRequestEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeOptionalLong
        );
        Optional<Long> retrievedUnionBridgeReleaseEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeOptionalLong
        );

        Assertions.assertTrue(retrievedUnionBridgeRequestEnabled.isEmpty());
        Assertions.assertTrue(retrievedUnionBridgeReleaseEnabled.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "true, false",
        "false, true",
        "false, false"
    })
    void setTransferPermissions_whenCallerIsAuthorized_shouldReturnSuccessCode(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(changeTransferPermissionsAuthorizer);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, requestEnabled, releaseEnabled);

        // assert
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionTransferPermissionsSet(requestEnabled, releaseEnabled);

        // call save and assert that the permissions are stored
        unionBridgeSupport.save();

        assertTransferPermissionsWereStored(requestEnabled, releaseEnabled);
    }

    private void assertTransferPermissionsWereStored(boolean requestEnabled,
        boolean releaseEnabled) {
        Optional<Long> retrievedUnionBridgeRequestEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeOptionalLong
        );
        Optional<Long> retrievedUnionBridgeReleaseEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeOptionalLong
        );

        Assertions.assertTrue(retrievedUnionBridgeRequestEnabled.isPresent());
        Assertions.assertEquals(requestEnabled ? 1L : 0L, retrievedUnionBridgeRequestEnabled.get());

        Assertions.assertTrue(retrievedUnionBridgeReleaseEnabled.isPresent());
        Assertions.assertEquals(releaseEnabled ? 1L : 0L, retrievedUnionBridgeReleaseEnabled.get());
    }


    private void assertLogUnionTransferPermissionsSet(boolean requestEnabled, boolean releaseEnabled) {
        CallTransaction.Function transferPermissionsEvent = BridgeEvents.UNION_BRIDGE_TRANSFER_PERMISSIONS_UPDATED.getEvent();
        byte[][] encodedTopicsSerialized = transferPermissionsEvent.encodeEventTopics(changeTransferPermissionsAuthorizer.toHexString());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsSerialized);
        byte[] encodedData = transferPermissionsEvent.encodeEventData(requestEnabled, releaseEnabled);
        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    private void assertUnionBridgeEntersPauseModeWhenMalFunctioning() {
        assertTransferPermissionsWereStored(false, false);

        CallTransaction.Function transferPermissionsEvent = BridgeEvents.UNION_BRIDGE_TRANSFER_PERMISSIONS_UPDATED.getEvent();
        byte[][] encodedTopicsSerialized = transferPermissionsEvent.encodeEventTopics(mainnetUnionBridgeContractAddress.toHexString());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsSerialized);
        byte[] encodedData = transferPermissionsEvent.encodeEventData(false, false);
        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    @ParameterizedTest
    @MethodSource("testnetAndRegtestConstantsProvider")
    void save_shouldSave(UnionBridgeConstants unionBridgeConstants) {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        // set union bridge contract address
        when(rskTx.getSender(signatureCache)).thenReturn(changeUnionAddressAuthorizer);
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            unionBridgeContractAddress);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // set bew lockig cap
        Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(BigInteger.valueOf(unionBridgeConstants.getLockingCapIncrementsMultiplier()));
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);
        UnionResponseCode actualLockingCapResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualLockingCapResponseCode);

        // request union rbtc
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeContractAddress);
        Coin amountRequested = new Coin(BigInteger.valueOf(100));
        UnionResponseCode actualRequestUnionRbtcResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);
        Assertions.assertEquals(UnionResponseCode.SUCCESS,  actualRequestUnionRbtcResponseCode);

        // release union rbtc
        Coin amountToRelease = new Coin(BigInteger.valueOf(50));
        when(rskTx.getValue()).thenReturn(amountToRelease);
        UnionResponseCode actualReleaseUnionRbtcResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualReleaseUnionRbtcResponseCode);

        // set transfer permissions
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(changeTransferPermissionsAuthorizer);
        UnionResponseCode actualSetTransferPermissionsResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, true, false);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualSetTransferPermissionsResponseCode);

        // act
        unionBridgeSupport.save();

        // assert
        assertAddressWasSet(unionBridgeContractAddress);
        assertAddressWasStored(unionBridgeContractAddress);
        assertLockingCapWasStored(newLockingCap);

        Coin expectedWeisTransferred = amountRequested.subtract(amountToRelease);
        assertWeisTransferredStoredAmount(expectedWeisTransferred);
        assertTransferPermissionsWereStored(true, false);
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

        // arrange
        when(rskTx.getSender(signatureCache)).thenReturn(changeUnionAddressAuthorizer);
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx,
            newUnionBridgeContractAddress);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // act
        unionBridgeSupport.save();

        assertAddressWasSet(newUnionBridgeContractAddress);
        assertAddressWasStored(newUnionBridgeContractAddress);
        assertNoLockingCapIsStored();
        assertNoWeisTransferredIsStored();
        assertNoTransferPermissionsWereStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void save_whenLockingCapIsIncreased_shouldSave(UnionBridgeConstants unionBridgeConstants){
        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);

        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();

        Coin newLockingCap = Coin.fromBitcoin(BridgeMainNetConstants.getInstance().getMaxRbtc());
        // to simulate the case where the address is already stored

        Coin storedLockingCap = newLockingCap.divide(BigInteger.TWO);
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            storedLockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );

        UnionResponseCode actualLockingCapResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualLockingCapResponseCode);
        assertLogUnionLockingCapIncreased(storedLockingCap, newLockingCap);

        // act
        unionBridgeSupport.save();

        assertLockingCapWasSet(newLockingCap);
        assertLockingCapWasStored(newLockingCap);
        assertNoAddressIsStored();
        assertNoWeisTransferredIsStored();
        assertNoTransferPermissionsWereStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void save_whenWeisTransferredBalanceIsIncreased_shouldSave(UnionBridgeConstants unionBridgeConstants){
        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin storedWeisTransferredAmount = new Coin(oneEth);
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeConstants.getAddress());

        // to simulate the case where weis transferred balance is already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            storedWeisTransferredAmount,
            BridgeSerializationUtils::serializeRskCoin
        );

        Coin amountRequested = new Coin(oneEth).multiply(BigInteger.TWO);
        UnionResponseCode actualRequestUnionRbtcResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);
        Assertions.assertEquals(UnionResponseCode.SUCCESS,  actualRequestUnionRbtcResponseCode);

        // act
        unionBridgeSupport.save();

        // assert
        Coin expectedAmountTransferred = storedWeisTransferredAmount.add(amountRequested);
        assertWeisTransferredStoredAmount(expectedAmountTransferred);
        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
        assertNoTransferPermissionsWereStored();
    }

    @ParameterizedTest
    @MethodSource("unionBridgeConstantsProvider")
    void save_whenWeisTransferredBalanceIsDecreased_shouldSave(UnionBridgeConstants unionBridgeConstants){
        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin storedWeisTransferredAmount = new Coin(oneEth.multiply(BigInteger.TEN));
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(unionBridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeConstants.getAddress());

        // to simulate the case where weis transferred balance is already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            storedWeisTransferredAmount,
            BridgeSerializationUtils::serializeRskCoin
        );

        Coin amountToRelease = new Coin(oneEth.divide(BigInteger.TEN)); // 0.1 RBTC
        when(rskTx.getValue()).thenReturn(amountToRelease);
        UnionResponseCode actualReleaseUnionRbtcResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);
        Assertions.assertEquals(UnionResponseCode.SUCCESS,  actualReleaseUnionRbtcResponseCode);

        // act
        unionBridgeSupport.save();

        // assert
        Coin expectedAmountTransferred = storedWeisTransferredAmount.subtract(amountToRelease);
        assertWeisTransferredStoredAmount(expectedAmountTransferred);
        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
        assertNoTransferPermissionsWereStored();
    }

    @Test
    void save_whenTransferPermissionsAreSet_shouldSave() {
        // arrange
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(changeTransferPermissionsAuthorizer);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, true, false);
        Assertions.assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the permissions are stored
        unionBridgeSupport.save();
        assertTransferPermissionsWereStored(true, false);
        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
        assertNoWeisTransferredIsStored();
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
            BridgeSerializationUtils::deserializeRskCoin
        );
        Assertions.assertEquals(newLockingCap, storedLockingCap);
    }

    private void assertWeisTransferredStoredAmount(Coin expectedWeisTransferred) {
        Coin actualAmountTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        Assertions.assertEquals(expectedWeisTransferred, actualAmountTransferred);
    }

    private void assertNoEventWasEmitted() {
        Assertions.assertTrue(logs.isEmpty(), "No events should have been emitted");
    }

    private void assertLogUnionLockingCapIncreased(Coin previousLockingCap, Coin newLockingCap) {
        CallTransaction.Function unionLockingCapIncreasedEvent = BridgeEvents.UNION_LOCKING_CAP_INCREASED.getEvent();
        byte[][] encodedTopicsSerialized = unionLockingCapIncreasedEvent.encodeEventTopics(
            UnionBridgeSupportImplTest.changeLockingCapAuthorizer.toHexString());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsSerialized);
        byte[] encodedData = unionLockingCapIncreasedEvent.encodeEventData(previousLockingCap.asBigInteger(), newLockingCap.asBigInteger());
        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    private void assertLogUnionRbtcReleased(Coin amountReleased) {
        CallTransaction.Function releaseUnionRbtcEvent = BridgeEvents.UNION_RBTC_RELEASED.getEvent();
        byte[][] encodedTopicsSerialized = releaseUnionRbtcEvent.encodeEventTopics(
            mainnetUnionBridgeContractAddress.toHexString());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsSerialized);
        byte[] encodedData = releaseUnionRbtcEvent.encodeEventData(amountReleased.asBigInteger());
        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }
}
