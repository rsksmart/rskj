package co.rsk.peg.union;

import static co.rsk.peg.BridgeSupportTestUtil.assertEventWasEmittedWithExpectedData;
import static co.rsk.peg.BridgeSupportTestUtil.assertEventWasEmittedWithExpectedTopics;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.vm.PrecompiledContracts.BRIDGE_ADDR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.constants.*;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.UnionBridgeSupportBuilder;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class UnionBridgeSupportImplTest {
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);

    private static final BridgeConstants mainnetConstants = BridgeMainNetConstants.getInstance();
    private static final UnionBridgeConstants mainnetUnionBridgeConstants = UnionBridgeMainNetConstants.getInstance();
    private static final RskAddress mainnetUnionBridgeContractAddress = mainnetUnionBridgeConstants.getAddress();

    private static final UnionBridgeConstants testnetUnionBridgeConstants = UnionBridgeTestNetConstants.getInstance();

    private static final RskAddress changeLockingCapAuthorizer = RskAddress.ZERO_ADDRESS;
    private static final RskAddress changeTransferPermissionsAuthorizer = RskAddress.ZERO_ADDRESS;

    private static final RskAddress unionBridgeContractAddress = RskTestUtils.generateAddress("newUnionBridgeContractAddress");
    private static final RskAddress newUnionBridgeContractAddress = RskTestUtils.generateAddress("secondNewUnionBridgeContractAddress");
    private static final byte[] superEvent = new byte[]{(byte) 0x123456};
    private static final byte[] baseEvent = new byte[]{(byte) 0x123456};

    private final UnionBridgeSupportBuilder unionBridgeSupportBuilder = UnionBridgeSupportBuilder.builder();

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
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeConstants.getAddress());
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
        assertEquals(expectedUnionBridgeContractAddress, actualUnionBridgeContractAddress);
    }

    @Test
    void getUnionBridgeContractAddress_whenStoredAddress_shouldReturnStoredAddress() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants).build();
        // to simulate the case where there is a previous address stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // act
        RskAddress actualUnionBridgeContractAddress = unionBridgeSupport.getUnionBridgeContractAddress();

        // assert
        assertEquals(unionBridgeContractAddress, actualUnionBridgeContractAddress);
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
        assertEquals(mainnetUnionBridgeContractAddress, actualUnionBridgeContractAddress);
    }

    @Test
    void setUnionBridgeContractAddressForTestnet_whenMeetRequirementsToUpdate_shouldReturnSuccess() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(unionBridgeContractAddress);

        // assert
        assertEquals(
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
        assertNull(actualRskAddress);
    }

    private void assertAddressWasSet(RskAddress expectedAddress) {
        Optional<RskAddress> actualAddress = unionBridgeStorageProvider.getAddress();
        assertTrue(actualAddress.isPresent());
        assertEquals(expectedAddress, actualAddress.get());
    }

    @Test
    void setUnionBridgeContractAddressForTestnet_whenAddressIsTheSameAddressInConstants_shouldReturnSuccess() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();
        RskAddress newUnionBridgeAddress = testnetUnionBridgeConstants.getAddress();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(newUnionBridgeAddress);

        // assert
        assertEquals(
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
        assertTrue(actualAddress.isEmpty());
    }

    @Test
    void setUnionBridgeContractAddressForTestnet_whenStoredAddress_shouldUpdateToNewUnionBridgeContractAddress() {
        // arrange

        // Save the address in storage
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(newUnionBridgeContractAddress);

        // assert
        assertEquals(
            UnionResponseCode.SUCCESS,
            actualResponseCode
        );
        assertAddressWasSet(newUnionBridgeContractAddress);

        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertEquals(unionBridgeContractAddress, actualRskAddress);
        assertNotEquals(newUnionBridgeContractAddress, actualRskAddress);

        // call save and assert that the new address is stored
        unionBridgeSupport.save();
        assertAddressWasStored(newUnionBridgeContractAddress);
    }

    @Test
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsNull_shouldReturnSuccess() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(null);

        // assert
        UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;
        assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasNotSet();
        assertNoAddressIsStored();
    }

    @Test
    void setUnionBridgeContractAddressForTestnet_whenGivenAddressIsEmpty_shouldReturnSuccess() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();

        // act
        RskAddress emptyAddress = new RskAddress(new byte[20]);
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(emptyAddress);

        // assert
        UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;
        assertEquals(
            expectedResponseCode,
            actualResponseCode
        );
        assertAddressWasSet(emptyAddress);

        // call save and assert that the empty address is stored
        unionBridgeSupport.save();
        assertAddressWasStored(emptyAddress);
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
        assertEquals(expectedInitialLockingCap, actualLockingCap);
        assertNoLockingCapIsStored();
    }

    private void assertNoLockingCapIsStored() {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        assertNull(storedLockingCap);
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
        assertEquals(expectedLockingCap, actualLockingCap);
    }

    @Test
    void increaseLockingCap_whenMeetRequirementsToIncreaseLockingCap_shouldIncreaseLockingCap() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants)
            .build();

        Coin initialLockingCap = mainnetUnionBridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(BigInteger.valueOf(mainnetUnionBridgeConstants.getLockingCapIncrementsMultiplier()));

        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);

        // assert
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
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

        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(UnionBridgeMainNetConstants.getInstance())
            .build();

        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, moreThanMaxRbtc);

        // Assert that new_locking_cap is allowed.
        // The new locking cap surpassing the maxRbtc is allowed because it meets the condition:
        // current_locking_cap <= new_locking_cap <= current_locking_cap * increment_multiplier
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
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
        assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);
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
        assertTrue(cacheLockingCap.isPresent());
        assertEquals(newLockingCap, cacheLockingCap.get());
    }

    private void assertNewLockingCapWasNotStored(Coin newLockingCap) {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        assertNotEquals(newLockingCap, storedLockingCap);
    }

    @ParameterizedTest
    @CsvSource({
        "false, false",
        "false, true"
    })
    void requestUnionRbtc_whenRequestIsDisabled_shouldReturnRequestDisabled(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        setupTransferPermissions(requestEnabled, releaseEnabled);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountRequested = new Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        assertEquals(UnionResponseCode.REQUEST_DISABLED, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoWeisTransferredIsStored();
    }

    private void setupTransferPermissions(boolean requestEnabled, boolean releaseEnabled) {
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            requestEnabled,
            BridgeSerializationUtils::serializeBoolean
        );

        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            releaseEnabled,
            BridgeSerializationUtils::serializeBoolean
        );
    }

    @Test
    void requestUnionRbtc_whenRequestIsEnabledByDefault_shouldReturnSuccessCode() {
        // arrange
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountRequested = new Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

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
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        setupTransferPermissions(requestEnabled, releaseEnabled);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountRequested = new Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(amountRequested);
    }

    @Test
    void requestUnionRbtc_whenCallerIsNotUnionBridgeContractAddress_shouldReturnUnauthorizedCaller() {
        // arrange
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notUnionBridgeContractAddress"));

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountRequested = new Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);

        // call save and assert that nothing is stored
        unionBridgeSupport.save();
        assertNoWeisTransferredIsStored();
    }

    @Test
    void requestUnionRbtc_whenIsDisabledAndCallerIsNotUnionBridgeContractAddress_shouldReturnUnauthorizedCaller() {
        // arrange
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notUnionBridgeContractAddress"));

        setupTransferPermissions(false, true);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountRequested = new Coin(oneEth);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);

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
        assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

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
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);

        // assert
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(amountRequested);
    }

    @Test
    void requestUnionRbtc_whenNewTotalWeisTransferredAmountEqualToLockingCap_shouldReturnSuccessCode() {
        // arrange
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
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();

        // The total weis transferred amount should equal the locking cap. (500 RBTC + 500 RBTC = 1000 RBTC)
        assertWeisTransferredStoredAmount(initialLockingCap);
    }

    @Test
    void requestUnionRbtc_whenNewTotalWeisTransferredAmountSurpassLockingCap_shouldReturnInvalidValue() {
        // arrange
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
        assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

        Coin storedWeisTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        // The total weis transferred amount should not change, it should still be 500 RBTC
        assertEquals(currentWeisTransferredAmount, storedWeisTransferred);
    }

    private void assertNoWeisTransferredIsStored() {
        Coin actualAmountTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        assertNull(actualAmountTransferred);
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
        assertEquals(UnionResponseCode.RELEASE_DISABLED, actualResponseCode);

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
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
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
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
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
        assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);

        // call save and assert that weisTransferredToUnionBridge still equals the original amount
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(weisTransferredToUnionBridge);
    }

    @Test
    void releaseUnionRbtc_whenIsDisabledAndCallerIsNotUnionBridgeContractAddress_shouldReturnUnauthorizedCaller() {
        // arrange
        when(rskTx.getSender(signatureCache)).thenReturn(
            TestUtils.generateAddress("notUnionBridgeContractAddress"));

        setupTransferPermissions(true, false);

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
        assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER, actualResponseCode);

        // call save and assert that weisTransferredToUnionBridge still equals the original amount
        unionBridgeSupport.save();
        assertWeisTransferredStoredAmount(weisTransferredToUnionBridge);
    }

    @ParameterizedTest
    @MethodSource("invalidAmountArgProvider")
    void releaseUnionRbtc_whenInvalidValue_shouldReturnInvalidValue(Coin amountToRelease) {
        // arrange
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
        assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

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
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionRbtcReleased(amountToRelease);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();
        Coin expectedWeisTransferred = lockingCap.subtract(amountToRelease);
        assertWeisTransferredStoredAmount(expectedWeisTransferred);
    }

    @Test
    void releaseUnionRbtc_whenNewTotalWeisTransferredAmountEqualToZero_shouldReturnSuccessCode() {
        // arrange
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
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionRbtcReleased(minimumPeginTxValue);

        // call save and assert that the amount is stored
        unionBridgeSupport.save();

        // The total weis transferred amount should equal zero.
        assertWeisTransferredStoredAmount(Coin.ZERO);
    }

    @Test
    void releaseUnionRbtc_whenNewTotalWeisTransferredAmountLessThanZero_shouldReturnInvalidValue() {
        // arrange
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
        assertEquals(UnionResponseCode.INVALID_VALUE, actualResponseCode);

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
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);

        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        Coin amountToRequest = new Coin(oneEth.multiply(BigInteger.TEN)); // 10 RBTC
        UnionResponseCode actualResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountToRequest);
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        Coin amountToRelease = new Coin(oneEth.multiply(BigInteger.TWO)); // 2 RBTC
        when(rskTx.getValue()).thenReturn(amountToRelease);

        // act
        actualResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);

        // assert
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
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
        "true, false",
        "false, true",
        "false, false"
    })
    void setTransferPermissions_whenMeetsRequirements_shouldReturnSuccessCode(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants).build();

        when(rskTx.getSender(signatureCache)).thenReturn(changeTransferPermissionsAuthorizer);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, requestEnabled, releaseEnabled);

        // assert
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionTransferPermissionsSet(requestEnabled, releaseEnabled);

        // call save and assert that the permissions are stored
        unionBridgeSupport.save();
        assertTransferPermissionsWereStored(requestEnabled, releaseEnabled);
    }

    @ParameterizedTest
    @CsvSource({
        "true, false",
        "false, true",
        "false, false"
    })
    void setTransferPermissions_whenSomeVotesDiffersAndFinalOneMatchesOneVoteAndWins_shouldReturnSuccessCode(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(changeTransferPermissionsAuthorizer);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, requestEnabled, releaseEnabled);

        // assert
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertLogUnionTransferPermissionsSet(requestEnabled, releaseEnabled);

        // call save and assert that the permissions are stored
        unionBridgeSupport.save();
        assertTransferPermissionsWereStored(requestEnabled, releaseEnabled);
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

        assertTrue(retrievedUnionBridgeRequestEnabled.isEmpty());
        assertTrue(retrievedUnionBridgeReleaseEnabled.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "true, false",
        "false, true",
        "false, false"
    })
    void setTransferPermissions_whenNoChangingCurrentState_shouldReturnSuccessCodeAndEmitNoEvent(boolean requestEnabled, boolean releaseEnabled) {
        // arrange
        setupTransferPermissions(requestEnabled, releaseEnabled);
        UnionBridgeConstants bridgeConstants = UnionBridgeMainNetConstants.getInstance();
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(bridgeConstants).build();
        when(rskTx.getSender(signatureCache)).thenReturn(changeTransferPermissionsAuthorizer);

        // act
        UnionResponseCode actualResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, requestEnabled, releaseEnabled);

        // assert
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        assertNoEventWasEmitted();

        // call save and assert that the permissions are stored
        unionBridgeSupport.save();

        assertTransferPermissionsWereStored(requestEnabled, releaseEnabled);
    }

    private void assertTransferPermissionsWereStored(boolean requestEnabled,
        boolean releaseEnabled) {
        Boolean retrievedUnionBridgeRequestEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        Boolean retrievedUnionBridgeReleaseEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        assertEquals(requestEnabled, retrievedUnionBridgeRequestEnabled);
        assertEquals(releaseEnabled, retrievedUnionBridgeReleaseEnabled);
    }


    private void assertLogUnionTransferPermissionsSet(boolean requestEnabled, boolean releaseEnabled) {
        CallTransaction.Function transferPermissionsEvent = BridgeEvents.UNION_BRIDGE_TRANSFER_PERMISSIONS_UPDATED.getEvent();
        byte[][] encodedTopicsSerialized = transferPermissionsEvent.encodeEventTopics(
            changeTransferPermissionsAuthorizer.toHexString());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsSerialized);
        byte[] encodedData = transferPermissionsEvent.encodeEventData(requestEnabled, releaseEnabled);
        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    private void assertUnionBridgeEntersPauseModeWhenMalFunctioning() {
        assertTransferPermissionsWereStored(false, false);

        CallTransaction.Function transferPermissionsEvent = BridgeEvents.UNION_BRIDGE_TRANSFER_PERMISSIONS_UPDATED.getEvent();
        byte[][] encodedTopicsSerialized = transferPermissionsEvent.encodeEventTopics(BRIDGE_ADDR.toHexString());
        List<DataWord> encodedTopics = LogInfo.byteArrayToList(encodedTopicsSerialized);
        byte[] encodedData = transferPermissionsEvent.encodeEventData(false, false);
        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    @Test
    void save_whenMainnetAndAllValuesWereUpdated_shouldSave() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();

        // increase locking cap
        Coin initialLockingCap = mainnetUnionBridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(BigInteger.valueOf(mainnetUnionBridgeConstants.getLockingCapIncrementsMultiplier()));
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);
        UnionResponseCode actualLockingCapResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);
        assertEquals(UnionResponseCode.SUCCESS, actualLockingCapResponseCode);

        // request union rbtc
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(mainnetUnionBridgeContractAddress);
        Coin amountRequested = new Coin(BigInteger.valueOf(100));
        UnionResponseCode actualRequestUnionRbtcResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);
        assertEquals(UnionResponseCode.SUCCESS,  actualRequestUnionRbtcResponseCode);

        // release union rbtc
        Coin amountToRelease = new Coin(BigInteger.valueOf(50));
        when(rskTx.getValue()).thenReturn(amountToRelease);
        UnionResponseCode actualReleaseUnionRbtcResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);
        assertEquals(UnionResponseCode.SUCCESS, actualReleaseUnionRbtcResponseCode);

        // set transfer permissions
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(changeTransferPermissionsAuthorizer);
        UnionResponseCode actualSetTransferPermissionsResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, true, false);
        assertEquals(UnionResponseCode.SUCCESS, actualSetTransferPermissionsResponseCode);

        // act
        unionBridgeSupport.save();

        // assert
        assertAddressWasNotSet();
        assertNoAddressIsStored();

        assertLockingCapWasStored(newLockingCap);

        Coin expectedWeisTransferred = amountRequested.subtract(amountToRelease);
        assertWeisTransferredStoredAmount(expectedWeisTransferred);
        assertTransferPermissionsWereStored(true, false);
    }

    @Test
    void save_whenTestnetAndAllValuesWereUpdated_shouldSave() {
        // arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();

        // set union bridge contract address
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(unionBridgeContractAddress);
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // increase locking cap
        Coin initialLockingCap = testnetUnionBridgeConstants.getInitialLockingCap();
        Coin newLockingCap = initialLockingCap.multiply(BigInteger.valueOf(testnetUnionBridgeConstants.getLockingCapIncrementsMultiplier()));
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);
        UnionResponseCode actualLockingCapResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);
        assertEquals(UnionResponseCode.SUCCESS, actualLockingCapResponseCode);

        // request union rbtc
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeContractAddress);
        Coin amountRequested = new Coin(BigInteger.valueOf(100));
        UnionResponseCode actualRequestUnionRbtcResponseCode = unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested);
        assertEquals(UnionResponseCode.SUCCESS,  actualRequestUnionRbtcResponseCode);

        // release union rbtc
        Coin amountToRelease = new Coin(BigInteger.valueOf(50));
        when(rskTx.getValue()).thenReturn(amountToRelease);
        UnionResponseCode actualReleaseUnionRbtcResponseCode = unionBridgeSupport.releaseUnionRbtc(rskTx);
        assertEquals(UnionResponseCode.SUCCESS, actualReleaseUnionRbtcResponseCode);

        // set transfer permissions
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(changeTransferPermissionsAuthorizer);
        UnionResponseCode actualSetTransferPermissionsResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, true, false);
        assertEquals(UnionResponseCode.SUCCESS, actualSetTransferPermissionsResponseCode);

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

    @Test
    void save_whenTestnetAndUnionBridgeContractAddressIsUpdated_shouldSave(){
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants).build();

        // to simulate the case where the address is already stored
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );

        // arrange
        UnionResponseCode actualResponseCode = unionBridgeSupport.setUnionBridgeContractAddressForTestnet(newUnionBridgeContractAddress);
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // act
        unionBridgeSupport.save();

        assertAddressWasSet(newUnionBridgeContractAddress);
        assertAddressWasStored(newUnionBridgeContractAddress);
        assertNoLockingCapIsStored();
        assertNoWeisTransferredIsStored();
        assertNoTransferPermissionsWereStored();
    }

    @Test
    void save_whenLockingCapIsIncreased_shouldSave(){
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(mainnetUnionBridgeConstants).build();

        Coin newLockingCap = Coin.fromBitcoin(BridgeMainNetConstants.getInstance().getMaxRbtc());
        // to simulate the case where the address is already stored

        Coin storedLockingCap = newLockingCap.divide(BigInteger.TWO);
        storageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            storedLockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );
        when(rskTx.getSender(signatureCache)).thenReturn(changeLockingCapAuthorizer);

        UnionResponseCode actualLockingCapResponseCode = unionBridgeSupport.increaseLockingCap(rskTx, newLockingCap);
        assertEquals(UnionResponseCode.SUCCESS, actualLockingCapResponseCode);

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
        assertEquals(UnionResponseCode.SUCCESS,  actualRequestUnionRbtcResponseCode);

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
        assertEquals(UnionResponseCode.SUCCESS,  actualReleaseUnionRbtcResponseCode);

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

        // act
        when(rskTx.getSender(signatureCache)).thenReturn(changeTransferPermissionsAuthorizer);
        UnionResponseCode actualResponseCode = unionBridgeSupport.setTransferPermissions(rskTx, true, false);
        assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);

        // call save and assert that the permissions are stored
        unionBridgeSupport.save();
        assertTransferPermissionsWereStored(true, false);
        assertNoAddressIsStored();
        assertNoLockingCapIsStored();
        assertNoWeisTransferredIsStored();
    }

    @Test
    void getSuperEvent_whenNotSavedData_shouldReturnEmptyArray() {
        // Act & Assert
        assertArrayEquals(EMPTY_BYTE_ARRAY, unionBridgeSupport.getSuperEvent());
    }

    @Test
    void getSuperEvent_whenEmptyDataSet_shouldReturnEmptyArray() {
        // Arrange
        unionBridgeSupport.setSuperEvent(rskTx, EMPTY_BYTE_ARRAY);

        // Act & Assert
        assertArrayEquals(EMPTY_BYTE_ARRAY, unionBridgeSupport.getSuperEvent());
    }

    @Test
    void getSuperEvent_shouldSetSuperEvent() {
        // Arrange
        unionBridgeSupport.setSuperEvent(rskTx, superEvent);

        // Act & Assert
        assertArrayEquals(superEvent, unionBridgeSupport.getSuperEvent());
    }

    @Test
    void getSuperEvent_whenDataSavedAndNewDataSet_shouldReturnNewData() {
        // Arrange
        unionBridgeSupport.setSuperEvent(rskTx, superEvent);
        unionBridgeSupport.save();

        // Act
        byte[] newSuperEvent = new byte[]{(byte) 0x12345678};
        unionBridgeSupport.setSuperEvent(rskTx, newSuperEvent);

        // Assert
        assertArrayEquals(newSuperEvent, unionBridgeSupport.getSuperEvent());
    }


    @Test
    void setSuperEvent_afterChangingUnionBridgeAddress_newAddressShouldSet_oldAddressShouldNot() {
        // Arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();
        when(rskTx.getSender(any())).thenReturn(testnetUnionBridgeConstants.getAddress());
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newAddress");
        unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx, newUnionBridgeAddress);
        unionBridgeSupport.save();

        // Act & Assert
        when(rskTx.getSender(any())).thenReturn(unionBridgeContractAddress);
        assertThrows(IllegalArgumentException.class, () -> unionBridgeSupport.setSuperEvent(rskTx, superEvent));

        when(rskTx.getSender(any())).thenReturn(newUnionBridgeAddress);
        unionBridgeSupport.setSuperEvent(rskTx, superEvent);
        assertArrayEquals(superEvent, unionBridgeSupport.getSuperEvent());
    }

    @Test
    void setSuperEvent_dataLengthExactlyMaximum_shouldSetSuperEvent() {
        // Arrange
        byte[] superEventMaxLength = new byte[128];

        // Act
        unionBridgeSupport.setSuperEvent(rskTx, superEventMaxLength);

        // Assert
        assertArrayEquals(superEventMaxLength, unionBridgeSupport.getSuperEvent());
    }

    @Test
    void setSuperEvent_dataLengthAboveMaximum_shouldThrowIAE() {
        // Arrange
        byte[] superEventAboveMaxLength = new byte[129];

        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> unionBridgeSupport.setSuperEvent(rskTx, superEventAboveMaxLength)
        );
    }

    @Test
    void setSuperEvent_whenNullDataSet_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> unionBridgeSupport.setSuperEvent(rskTx, null));
    }

    @Test
    void clearSuperEvent_whenDataSaved_shouldClearData() {
        // Arrange
        unionBridgeSupport.setSuperEvent(rskTx, superEvent);
        unionBridgeSupport.save();

        // Act
        unionBridgeSupport.clearSuperEvent(rskTx);

        // Assert
        byte[] emptyData = new byte[]{};
        assertArrayEquals(emptyData, unionBridgeSupport.getSuperEvent());
    }

    @Test
    void clearSuperEvent_afterChangingUnionBridgeAddress_newAddressShouldClear_oldAddressShouldNot() {
        // Arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();
        when(rskTx.getSender(any())).thenReturn(testnetUnionBridgeConstants.getAddress());
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newAddress");
        unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx, newUnionBridgeAddress);
        unionBridgeSupport.save();

        // Act & Assert
        when(rskTx.getSender(any())).thenReturn(unionBridgeContractAddress);
        assertThrows(IllegalArgumentException.class, () -> unionBridgeSupport.clearSuperEvent(rskTx));

        when(rskTx.getSender(any())).thenReturn(newUnionBridgeAddress);
        unionBridgeSupport.clearSuperEvent(rskTx);
        assertArrayEquals(EMPTY_BYTE_ARRAY, unionBridgeSupport.getSuperEvent());
    }
    @Test
    void getBaseEvent_whenNotSavedData_shouldReturnEmptyArray() {
        // Act & Assert
        assertArrayEquals(EMPTY_BYTE_ARRAY, unionBridgeSupport.getBaseEvent());
    }

    @Test
    void getBaseEvent_whenEmptyDataSet_shouldReturnEmptyArray() {
        // Arrange
        unionBridgeSupport.setBaseEvent(rskTx, EMPTY_BYTE_ARRAY);

        // Act & Assert
        assertArrayEquals(EMPTY_BYTE_ARRAY, unionBridgeSupport.getBaseEvent());
    }

    @Test
    void getBaseEvent_whenNullDataSet_shouldReturnEmptyArray() {
        // Arrange
        unionBridgeSupport.setBaseEvent(rskTx, null);

        // Act & Assert
        assertArrayEquals(EMPTY_BYTE_ARRAY, unionBridgeSupport.getBaseEvent());
    }

    @Test
    void getBaseEvent_shouldSetBaseEvent() {
        // Arrange
        unionBridgeSupport.setBaseEvent(rskTx, baseEvent);

        // Act & Assert
        assertArrayEquals(baseEvent, unionBridgeSupport.getBaseEvent());
    }

    @Test
    void setBaseEvent_afterChangingUnionBridgeAddress_newAddressShouldSet_oldAddressShouldNot() {
        // Arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();
        when(rskTx.getSender(any())).thenReturn(testnetUnionBridgeConstants.getAddress());
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newAddress");
        unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx, newUnionBridgeAddress);
        unionBridgeSupport.save();

        // Act & Assert
        when(rskTx.getSender(any())).thenReturn(unionBridgeContractAddress);
        assertThrows(IllegalArgumentException.class, () -> unionBridgeSupport.setBaseEvent(rskTx, baseEvent));

        when(rskTx.getSender(any())).thenReturn(newUnionBridgeAddress);
        unionBridgeSupport.setBaseEvent(rskTx, baseEvent);
        assertArrayEquals(baseEvent, unionBridgeSupport.getBaseEvent());
    }

    @Test
    void setBaseEvent_dataLengthExactlyMaximum_shouldSetBaseEvent() {
        // Arrange
        byte[] baseEvent = new byte[128];

        // Act
        unionBridgeSupport.setBaseEvent(rskTx, baseEvent);

        // Assert
        assertArrayEquals(baseEvent, unionBridgeSupport.getBaseEvent());
    }

    @Test
    void setBaseEvent_dataLengthAboveMaximum_shouldThrowIAE() {
        // Arrange
        byte[] baseEvent = new byte[129];

        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> unionBridgeSupport.setBaseEvent(rskTx, baseEvent)
        );
    }

    @Test
    void getBaseEvent_whenDataSavedAndNewDataSet_shouldReturnNewData() {
        // Arrange
        unionBridgeSupport.setBaseEvent(rskTx, baseEvent);
        unionBridgeSupport.save();

        // Act
        byte[] newBaseEvent = new byte[]{(byte) 0x12345678};
        unionBridgeSupport.setBaseEvent(rskTx, newBaseEvent);

        // Assert
        assertArrayEquals(newBaseEvent, unionBridgeSupport.getBaseEvent());
    }

    @Test
    void clearBaseEvent_whenDataSaved_shouldClearData() {
        // Arrange
        unionBridgeSupport.setBaseEvent(rskTx, baseEvent);
        unionBridgeSupport.save();

        // Act
        unionBridgeSupport.clearBaseEvent(rskTx);

        // Assert
        assertArrayEquals(EMPTY_BYTE_ARRAY, unionBridgeSupport.getBaseEvent());
    }

    @Test
    void clearBaseEvent_afterChangingUnionBridgeAddress_newAddressShouldClear_oldAddressShouldNot() {
        // Arrange
        unionBridgeSupport = unionBridgeSupportBuilder
            .withConstants(testnetUnionBridgeConstants)
            .build();
        when(rskTx.getSender(any())).thenReturn(testnetUnionBridgeConstants.getAddress());
        RskAddress newUnionBridgeAddress = TestUtils.generateAddress("newAddress");
        unionBridgeSupport.setUnionBridgeContractAddressForTestnet(rskTx, newUnionBridgeAddress);
        unionBridgeSupport.save();

        // Act & Assert
        when(rskTx.getSender(any())).thenReturn(unionBridgeContractAddress);
        assertThrows(IllegalArgumentException.class, () -> unionBridgeSupport.clearBaseEvent(rskTx));

        when(rskTx.getSender(any())).thenReturn(newUnionBridgeAddress);
        unionBridgeSupport.clearBaseEvent(rskTx);
        assertArrayEquals(EMPTY_BYTE_ARRAY, unionBridgeSupport.getBaseEvent());
    }


    private void assertAddressWasStored(RskAddress newUnionBridgeContractAddress) {
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertEquals(newUnionBridgeContractAddress, actualRskAddress);
    }

    private void assertLockingCapWasStored(Coin newLockingCap) {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        assertEquals(newLockingCap, storedLockingCap);
    }

    private void assertWeisTransferredStoredAmount(Coin expectedWeisTransferred) {
        Coin actualAmountTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        assertEquals(expectedWeisTransferred, actualAmountTransferred);
    }

    private void assertNoEventWasEmitted() {
        assertTrue(logs.isEmpty(), "No events should have been emitted");
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
