package co.rsk.peg.union;

import static co.rsk.peg.BridgeEvents.*;
import static co.rsk.peg.BridgeEventsTestUtils.getEncodedData;
import static co.rsk.peg.BridgeEventsTestUtils.getEncodedTopics;
import static co.rsk.peg.BridgeMethods.*;
import static co.rsk.peg.BridgeSupportTestUtil.assertEventWasEmittedWithExpectedData;
import static co.rsk.peg.BridgeSupportTestUtil.assertEventWasEmittedWithExpectedTopics;
import static co.rsk.peg.union.UnionBridgeStorageIndexKey.*;
import static co.rsk.peg.union.UnionResponseCode.*;
import static org.ethereum.vm.PrecompiledContracts.BRIDGE_ADDR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.*;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.core.CallTransaction.Function;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class UnionBridgeIT {

    // Boolean constants for transfer permissions
    private static final boolean REQUEST_PERMISSION_ENABLED = true;
    private static final boolean REQUEST_PERMISSION_DISABLED = false;
    private static final boolean RELEASE_PERMISSION_ENABLED = true;
    private static final boolean RELEASE_PERMISSION_DISABLED = false;

    private static final ActivationConfig lovellActivations = ActivationConfigsForTest.lovell700();
    private static final ActivationConfig allActivations = ActivationConfigsForTest.all();

    private static final BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private static final UnionBridgeConstants unionBridgeMainNetConstants = bridgeMainNetConstants.getUnionBridgeConstants();

    private static final UnionBridgeSupportBuilder unionBridgeSupportBuilder = UnionBridgeSupportBuilder.builder();

    private static final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
    private BridgeSupport bridgeSupport;

    private static final BridgeBuilder bridgeBuilder = new BridgeBuilder();
    private Bridge bridge;

    private static final RskAddress UNAUTHORIZED_CALLER_ADDRESS = TestUtils.generateAddress("UNAUTHORIZED");

    private static final RskAddress CHANGE_LOCKING_CAP_AUTHORIZER_1 = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "040162aff21e78665eabe736746ed86ca613f9e628289438697cf820ed8ac800e5fe8cbca350f8cf0b3ee4ec3d8c3edec93820d889565d4ae9b4f6e6d012acec09"))
            .getAddress());

    private static final RskAddress CHANGE_LOCKING_CAP_AUTHORIZER_2 = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "04ee99364235a33edbd177c0293bd3e13f1c85b2ee6197e66aa7e975fb91183b08b30bf1227468980180e10092decaaeed0ae1c4bcf29d17993569bb3c1b274f83"))
            .getAddress());

    private static final RskAddress CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1 = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "0458fdbe66a1eda5b94eaf3b3ef1bc8439a05a0b13d2bb9d5a1c6ea1d98ed5b0405fd002c884eed4aa1102d812c7347acc6dd172ad4828de542e156bd47cd90282"))
            .getAddress());

    private static final RskAddress CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2 = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "0486559d73a991df9e5eef1782c41959ecc7e334ef57ddcb6e4ebc500771a50f0c3b889afb9917165db383a9bf9a8e9b4f73abd542109ba06387f016f62df41b0f"))
            .getAddress());

    private static final RskAddress CURRENT_UNION_BRIDGE_ADDRESS = new RskAddress(
        "0000000000000000000000000000000000000000");
    private static final RskAddress NEW_UNION_BRIDGE_CONTRACT_ADDRESS = TestUtils.generateAddress(
        "newUnionBridgeContractAddress");

    private static final int LOCKING_CAP_INCREMENTS_MULTIPLIER = unionBridgeMainNetConstants.getLockingCapIncrementsMultiplier();
    private static final Coin INITIAL_LOCKING_CAP = unionBridgeMainNetConstants.getInitialLockingCap();
    private static final Coin INITIAL_MAX_LOCKING_CAP_INCREMENT = INITIAL_LOCKING_CAP
        .multiply(BigInteger.valueOf(
            LOCKING_CAP_INCREMENTS_MULTIPLIER));

    private static final Coin NEW_LOCKING_CAP_1 = INITIAL_MAX_LOCKING_CAP_INCREMENT.subtract(Coin.valueOf(20));
    private static final Coin NEW_LOCKING_CAP_2 = NEW_LOCKING_CAP_1.add(Coin.valueOf(10));

    private static final BigInteger ONE_ETH = BigInteger.TEN.pow(
        18); // 1 ETH = 1000000000000000000 wei
    private static final Coin AMOUNT_TO_REQUEST = new co.rsk.core.Coin(ONE_ETH.multiply(BigInteger.TWO));
    private static final Coin AMOUNT_TO_RELEASE = new co.rsk.core.Coin(ONE_ETH);

    private Repository repository;
    private StorageAccessor storageAccessor;

    private SignatureCache signatureCache;
    private Transaction rskTx;

    private List<LogInfo> logs;

    @BeforeEach
    void beforeEach() {
        // To mimic a fresh block start for each transaction, we clear the logs before each test.
        logs.clear();
    }

    @BeforeAll
    void lovellSetup() {
        ForBlock lovellActivationsForBlock = lovellActivations.forBlock(0);
        repository = BridgeSupportTestUtil.createRepository();
        repository.addBalance(BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(bridgeMainNetConstants.getMaxRbtc()));
        storageAccessor = new BridgeStorageAccessorImpl(repository);

        signatureCache = mock(SignatureCache.class);
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(CURRENT_UNION_BRIDGE_ADDRESS);

        logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(
            bridgeMainNetConstants,
            lovellActivationsForBlock,
            logs
        );

        UnionBridgeStorageProvider unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(
            storageAccessor);
        UnionBridgeSupport unionBridgeSupport = unionBridgeSupportBuilder
            .withStorageProvider(unionBridgeStorageProvider)
            .withConstants(unionBridgeMainNetConstants)
            .withSignatureCache(signatureCache)
            .withEventLogger(eventLogger)
            .build();

        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            BRIDGE_ADDR,
            bridgeMainNetConstants.getBtcParams(),
            lovellActivationsForBlock
        );

        bridgeSupport = bridgeSupportBuilder
            .withSignatureCache(signatureCache)
            .withUnionBridgeSupport(unionBridgeSupport)
            .withRepository(repository)
            .withProvider(bridgeStorageProvider)
            .withEventLogger(eventLogger)
            .withBridgeConstants(bridgeMainNetConstants)
            .withActivations(lovellActivationsForBlock)
            .build();

        bridge = bridgeBuilder
            .signatureCache(signatureCache)
            .activationConfig(lovellActivations)
            .bridgeSupport(bridgeSupport)
            .transaction(rskTx)
            .build();
    }

    private void setupForAllActivations() {
        ForBlock allActivationsForBlock = allActivations.forBlock(0);
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            BRIDGE_ADDR,
            bridgeMainNetConstants.getBtcParams(),
            allActivationsForBlock
        );

        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(
            bridgeMainNetConstants,
            allActivationsForBlock,
            logs
        );

        UnionBridgeSupport unionBridgeSupport = unionBridgeSupportBuilder
            .withEventLogger(eventLogger)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withProvider(bridgeStorageProvider)
            .withActivations(allActivationsForBlock)
            .withEventLogger(eventLogger)
            .withUnionBridgeSupport(unionBridgeSupport)
            .build();
        bridge = bridgeBuilder
            .activationConfig(allActivations)
            .bridgeSupport(bridgeSupport)
            .build();
    }

    private void executeBridgeMethodAndAssertFail(byte[] bridgeMethod) {
        VMException actualException = assertThrows(VMException.class,
            () -> bridge.execute(bridgeMethod));
        assertEquals(BridgeIllegalArgumentException.class, actualException.getCause().getClass());
    }

    @Test
    @Order(0)
    void setUnionBridgeContractAddressForTestnet_whenLovell_shouldFail() {
        // Arrange
        setupCaller(CURRENT_UNION_BRIDGE_ADDRESS);
        Function function = SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.getFunction();
        byte[] functionEncoded = function.encode(
            NEW_UNION_BRIDGE_CONTRACT_ADDRESS.toHexString());
        // Act & Assert
        executeBridgeMethodAndAssertFail(functionEncoded);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(1)
    void getUnionBridgeContractAddress_whenLovell_shouldFail() {
        // Arrange
        Function function = GET_UNION_BRIDGE_CONTRACT_ADDRESS.getFunction();
        byte[] functionEncoded = function.encode();
        // Act & Assert
        executeBridgeMethodAndAssertFail(functionEncoded);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(2)
    void getUnionBridgeLockingCap_whenLovell_shouldFail() {
        // Arrange
        Function function = GET_UNION_BRIDGE_LOCKING_CAP.getFunction();
        byte[] functionEncoded = function.encode();
        // Act & Assert
        executeBridgeMethodAndAssertFail(functionEncoded);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(3)
    void increaseUnionBridgeLockingCap_whenLovell_shouldFail() {
        // Arrange
        setupUnauthorizedCaller();
        Function function = INCREASE_UNION_BRIDGE_LOCKING_CAP.getFunction();
        byte[] functionEncoded = function.encode(NEW_LOCKING_CAP_1.asBigInteger());
        // Act & Assert
        executeBridgeMethodAndAssertFail(functionEncoded);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(4)
    void requestUnionBridgeRbtc_whenLovell_shouldFail() {
        // Arrange
        setupCaller(CURRENT_UNION_BRIDGE_ADDRESS);
        Function function = REQUEST_UNION_BRIDGE_RBTC.getFunction();
        byte[] functionEncoded = function.encode(AMOUNT_TO_REQUEST.asBigInteger());
        // Act & Assert
        executeBridgeMethodAndAssertFail(functionEncoded);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(5)
    void releaseUnionBridgeRbtc_whenLovell_shouldFail() {
        // Arrange
        setupUnauthorizedCaller();
        Function function = RELEASE_UNION_BRIDGE_RBTC.getFunction();
        when(rskTx.getValue()).thenReturn(AMOUNT_TO_RELEASE);
        byte[] functionEncoded = function.encode();
        // Act & Assert
        executeBridgeMethodAndAssertFail(functionEncoded);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(6)
    void setUnionBridgeTransferPermissions_whenLovell_shouldFail() {
        // Arrange
        setupUnauthorizedCaller();
        Function function = SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
        byte[] functionEncoded = function.encode(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_ENABLED);
        // Act & Assert
        executeBridgeMethodAndAssertFail(functionEncoded);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(7)
    void setUnionBridgeContractAddressForTestnet_whenAllActivations_shouldReturnEnvironmentDisabled()
        throws VMException {
        // Setup for all activations
        setupForAllActivations();
        // Setup authorizer for changing union address
        setupCaller(CURRENT_UNION_BRIDGE_ADDRESS);

        // Assert that the union address is equal to the constant address
        RskAddress unionAddressBeforeAttemptToUpdate = getUnionBridgeContractAddress();
        assertEquals(CURRENT_UNION_BRIDGE_ADDRESS, unionAddressBeforeAttemptToUpdate);
        assertNoAddressIsStored();

        // Attempt to update union address when mainnet network. It should fail.
        int actualUnionResponseCode = updateUnionAddress();

        // Assert mainnet network does not allow updating union address
        assertEquals(ENVIRONMENT_DISABLED.getCode(), actualUnionResponseCode);
        assertNoAddressIsStored();
        assertNoEventWasEmitted();

        // Assert that the union address continues to be the constant address
        RskAddress actualUnionAddress = getUnionBridgeContractAddress();
        assertEquals(unionAddressBeforeAttemptToUpdate, actualUnionAddress);
    }

    @Test
    @Order(8)
    void increaseUnionBridgeLockingCap_whenUnauthorized_shouldFailAndReturnUnauthorizedCaller() throws VMException {
        // Arrange
        setupCaller(UNAUTHORIZED_CALLER_ADDRESS);
        // Assert that the initial locking cap is equal to the mainnet constant INITIAL_LOCKING_CAP
        Coin actualUnionLockingCap = getUnionBridgeLockingCap();
        assertEquals(INITIAL_LOCKING_CAP, actualUnionLockingCap);
        // Assert that no union locking cap is stored
        assertNoStoredUnionLockingCap();

        // Act
        VMException actualException = assertThrows(VMException.class, () -> increaseUnionBridgeLockingCap(NEW_LOCKING_CAP_1));

        // Assert
        assertTrue(actualException.getMessage().contains("The sender is not authorized to call increaseUnionBridgeLockingCap"));
        // Assert that the locking cap remains unchanged
        assertEquals(INITIAL_LOCKING_CAP, getUnionBridgeLockingCap());
        // Assert that the stored union locking cap is still null
        assertNoStoredUnionLockingCap();
        assertNoEventWasEmitted();
    }

    @Test
    @Order(9)
    void increaseUnionBridgeLockingCap_whenFirstVote_shouldVoteSuccessfully() throws VMException {
        // Arrange
        setupCaller(CHANGE_LOCKING_CAP_AUTHORIZER_1);

        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(NEW_LOCKING_CAP_1);

        // Assert
        assertSuccessfulResponseCode(actualUnionResponseCode);
        // Assert that the locking cap remains unchanged after the first vote
        Coin actualUnionLockingCap = getUnionBridgeLockingCap();
        assertEquals(INITIAL_LOCKING_CAP, actualUnionLockingCap);
        assertNoStoredUnionLockingCap();
        assertNoEventWasEmitted();
    }

    /**
     * This test simulates a scenario where a second vote is for a different value than the first vote.
     * The expected result is that the second vote should be successful, but the locking cap should not change.
     */
    @Test
    @Order(10)
    void increaseUnionBridgeLockingCap_whenSecondVoteForDifferentValue_shouldVoteSuccessfully() throws VMException {
        // Arrange
        setupCaller(CHANGE_LOCKING_CAP_AUTHORIZER_2);
        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(NEW_LOCKING_CAP_2);
        // Assert
        assertSuccessfulResponseCode(actualUnionResponseCode);
        // Assert that the locking cap remains unchanged after the second vote for another value
        Coin actualUnionLockingCap = getUnionBridgeLockingCap();
        assertEquals(INITIAL_LOCKING_CAP, actualUnionLockingCap);
        assertNoStoredUnionLockingCap();
        assertNoEventWasEmitted();
    }

    /**
     * This test simulates a scenario where a third vote is for the first value that was voted in the first vote.
     * The expected result is that the third vote should be successful, and now since the required votes are 2 out of 3,
     * the locking cap should be updated to the first value.
     */
    @Test
    @Order(11)
    void increaseUnionBridgeLockingCap_whenThirdVoteForFirstValue_shouldIncrementLockingCap() throws VMException {
        // Arrange
        setupCaller(CHANGE_LOCKING_CAP_AUTHORIZER_2);
        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(NEW_LOCKING_CAP_1);
        // Assert
        assertSuccessfulResponseCode(actualUnionResponseCode);
        assertLockingCap(NEW_LOCKING_CAP_1);
        assertLogUnionLockingCapIncreased(INITIAL_LOCKING_CAP, NEW_LOCKING_CAP_1);
    }

    @Test
    @Order(12)
    void increaseUnionBridgeLockingCap_whenVoteAgainForPreviousDifferentValueAfterElectionClear_shouldVoteSuccessfulButNoChange() throws VMException {
        // Arrange
        setupCaller(CHANGE_LOCKING_CAP_AUTHORIZER_2);
        Coin lockingCapBeforeUpdate = getUnionBridgeLockingCap();
        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(NEW_LOCKING_CAP_2);
        // Assert
        assertSuccessfulResponseCode(actualUnionResponseCode);
        // Assert that the locking cap remains unchanged
        assertLockingCap(lockingCapBeforeUpdate);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(13)
    void increaseUnionBridgeLockingCap_whenSmallerLockingCap_shouldReturnInvalidValue() throws VMException {
        // Arrange
        setupCaller(CHANGE_LOCKING_CAP_AUTHORIZER_2);
        Coin lockingCapBeforeUpdate = getUnionBridgeLockingCap();
        Coin smallerLockingCap = lockingCapBeforeUpdate.subtract(Coin.valueOf(1));
        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(smallerLockingCap);
        // Assert
        assertEquals(INVALID_VALUE.getCode(), actualUnionResponseCode);
        // Assert that the locking cap remains unchanged
        assertLockingCap(lockingCapBeforeUpdate);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(14)
    void requestUnionBridgeRbtc_whenUnauthorized_shouldReturnUnauthorized() throws VMException {
        setupUnauthorizedCaller();
        // Assert that no weisTransferred to union bridge is stored before any request/release
        assertNoWeisTransferredToUnionBridgeIsStored();
        assertUnionBridgeBalance(Coin.ZERO);

        // Act
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);
        assertUnauthorizedCaller(requestUnionResponseCode);

        // Assert WEIS_TRANSFERRED_TO_UNION_BRIDGE is null in the storage since no request was made
        assertNoWeisTransferredToUnionBridgeIsStored();

        // Assert that the union bridge address has the expected balance
        assertUnionBridgeBalance(Coin.ZERO);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(15)
    void requestUnionBridgeRbtc_whenAuthorized_shouldTransferFunds() throws VMException {
        setupUnionAddressCaller();
        Coin weisTransferredBalanceBeforeRequest = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRequest = getUnionBridgeBalance();

        // Act
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);

        // Assert
        assertSuccessfulResponseCode(requestUnionResponseCode);
        assertLogUnionRbtcRequested();

        Coin expectedWeisTransferredBalance = weisTransferredBalanceBeforeRequest.add(AMOUNT_TO_REQUEST);
        Coin expectedUnionBridgeBalance = unionBridgeBalanceBeforeRequest.add(AMOUNT_TO_REQUEST);
        assertWeisTransferredToUnionBridge(expectedWeisTransferredBalance);
        assertUnionBridgeBalance(expectedUnionBridgeBalance);
    }

    @Test
    @Order(16)
    void releaseUnionBridgeRbtc_whenUnauthorized_shouldReturnUnauthorized()
        throws VMException {
        setupUnauthorizedCaller();
        Coin weisTransferredBalanceBeforeRelease = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRelease = getUnionBridgeBalance();
        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);
        // Assert
        assertUnauthorizedCaller(releaseUnionResponseCode);
        assertUnionBridgeBalance(unionBridgeBalanceBeforeRelease);
        assertWeisTransferredToUnionBridge(weisTransferredBalanceBeforeRelease);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(17)
    void releaseUnionBridgeRbtc_whenAllActivations_shouldReceiveFundsBack()
        throws VMException {
        // Arrange
        setupUnionAddressCaller();
        Coin weisTransferredBalanceBeforeRelease = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRelease = getUnionBridgeBalance();

        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);

        // Assert
        assertSuccessfulResponseCode(releaseUnionResponseCode);
        assertLogUnionRbtcReleased();

        Coin expectedWeisTransferredBalance = weisTransferredBalanceBeforeRelease.subtract(AMOUNT_TO_RELEASE);
        Coin expectedUnionBridgeBalance = unionBridgeBalanceBeforeRelease.subtract(AMOUNT_TO_RELEASE);
        assertWeisTransferredToUnionBridge(expectedWeisTransferredBalance);
        assertUnionBridgeBalance(expectedUnionBridgeBalance);
    }

    @Test
    @Order(18)
    void setTransferPermissions_whenUnauthorized_shouldReturnUnauthorized() {
        setupUnauthorizedCaller();
        // Assert that no union transferred permissions are stored initially
        assertNoUnionTransferredPermissionsIsStored();

        // Act
        VMException actualException = assertThrows(VMException.class, () -> setUnionTransferPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_DISABLED));

        // Assert
        assertTrue(actualException.getMessage().contains("The sender is not authorized to call setUnionBridgeTransferPermissions"));
        assertNoUnionTransferredPermissionsIsStored();
        assertNoEventWasEmitted();
    }

    @Test
    @Order(19)
    void setTransferPermissions_whenFirstVote_shouldVoteSuccessfully() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_DISABLED);
        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        // Assert transferred permissions remain unchanged
        assertNoUnionTransferredPermissionsIsStored();
        assertNoEventWasEmitted();
    }

    @Test
    @Order(20)
    void setTransferPermissions_whenSecondVoteForDifferentValue_shouldVoteSuccessfully() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_ENABLED);
        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        assertNoUnionTransferredPermissionsIsStored();
        assertNoEventWasEmitted();
    }

    @Test
    @Order(21)
    void setTransferPermissions_whenThirdVoteForSameValue_shouldUpdateTransferPermissions() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_DISABLED);
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);

        // Assert transferred permissions are updated
        assertUnionTransferredPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_DISABLED);
        assertLogUnionTransferPermissionsSet(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_DISABLED, CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);
    }

    @Test
    @Order(22)
    void requestUnionBridgeRbtc_whenRequestIsDisabled_shouldReturnDisabledCode() throws VMException {
        // Arrange
        setupUnionAddressCaller();
        Coin weisTransferredBalanceBeforeRequest = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRequest = getUnionBridgeBalance();

        // Act
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);

        // Assert
        assertEquals(
            REQUEST_DISABLED.getCode(),
            requestUnionResponseCode,
            "Requesting union rBTC should fail when request permission is disabled"
        );
        assertNoEventWasEmitted();

        // Assert that the balance remains the same
        assertWeisTransferredToUnionBridge(weisTransferredBalanceBeforeRequest);
        assertUnionBridgeBalance(unionBridgeBalanceBeforeRequest);
    }

    @Test
    @Order(23)
    void increaseUnionBridgeLockingCap_whenFirstVoteWhileTransferIsDisabled_shouldVoteSuccessfully() throws VMException {
        // Arrange
        setupCaller(CHANGE_LOCKING_CAP_AUTHORIZER_1);
        Coin lockingCapBeforeIncrement = getUnionBridgeLockingCap();
        Coin newLockingCap = lockingCapBeforeIncrement.multiply(
            BigInteger.valueOf(LOCKING_CAP_INCREMENTS_MULTIPLIER));

        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(newLockingCap);

        // Assert
        assertSuccessfulResponseCode(actualUnionResponseCode);

        // Assert that the locking cap remains unchanged
        assertLockingCap(lockingCapBeforeIncrement);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(24)
    void increaseUnionBridgeLockingCap_whenSecondVoteWhileTransferIsDisabled_shouldIncrementLockingCap() throws VMException {
        // Arrange
        setupCaller(CHANGE_LOCKING_CAP_AUTHORIZER_2);
        Coin lockingCapBeforeIncrement = getUnionBridgeLockingCap();
        Coin newLockingCap = lockingCapBeforeIncrement.multiply(
            BigInteger.valueOf(LOCKING_CAP_INCREMENTS_MULTIPLIER));
        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(newLockingCap);
        // Assert
        assertSuccessfulResponseCode(actualUnionResponseCode);
        assertLockingCap(newLockingCap);
        assertLogUnionLockingCapIncreased(lockingCapBeforeIncrement, newLockingCap);
    }

    @Test
    @Order(25)
    void setUnionBridgeContractAddressForTestnet_whenMainnetAndTransferIsDisabled_shouldReturnEnvironmentDisabled()
        throws VMException {
        setupCaller(CURRENT_UNION_BRIDGE_ADDRESS);
        RskAddress actualUnionAddress = getUnionBridgeContractAddress();
        assertEquals(CURRENT_UNION_BRIDGE_ADDRESS, actualUnionAddress);

        int actualUnionResponseCode = updateUnionAddress();
        assertEquals(ENVIRONMENT_DISABLED.getCode(), actualUnionResponseCode);

        // Assert that the union address remains unchanged
        assertNoAddressIsStored();
        assertNoEventWasEmitted();
    }

    @Test
    @Order(26)
    void setTransferPermissions_whenFirstVoteToEnableOnlyRequest_shouldVoteSuccessfully()
        throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_DISABLED);
        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        // Assert that the transfer permissions remain the same
        assertUnionTransferredPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_DISABLED);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(27)
    void setTransferPermissions_whenSecondVoteToEnableOnlyRequest_shouldUpdatePermissions() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_DISABLED);

        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_DISABLED);
        assertLogUnionTransferPermissionsSet(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_DISABLED, CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);
    }

    @Test
    @Order(28)
    void requestUnionBridgeRbtc_whenRequestIsEnabled_shouldTransferFundsToUnionBridge() throws VMException {
        // Arrange
        setupUnionAddressCaller();
        Coin weisTransferredBalanceBeforeRequest = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRequest = getUnionBridgeBalance();

        // Act
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);

        // Assert
        assertSuccessfulResponseCode(requestUnionResponseCode);
        assertLogUnionRbtcRequested();

        Coin expectedWeisTransferredBalance = weisTransferredBalanceBeforeRequest.add(AMOUNT_TO_REQUEST);
        Coin expectedUnionBridgeBalance = unionBridgeBalanceBeforeRequest.add(AMOUNT_TO_REQUEST);
        assertWeisTransferredToUnionBridge(expectedWeisTransferredBalance);
        assertUnionBridgeBalance(expectedUnionBridgeBalance);
    }

    @Test
    @Order(29)
    void releaseUnionBridgeRbtc_whenReleaseIsDisabled_shouldReturnReleaseDisabled() throws VMException {
        // Arrange
        Coin weisTransferredBalanceBeforeRelease = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRelease = getUnionBridgeBalance();
        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);
        // Assert
        assertEquals(RELEASE_DISABLED.getCode(), releaseUnionResponseCode);
        assertNoEventWasEmitted();
        assertUnionBridgeBalance(unionBridgeBalanceBeforeRelease);
        assertWeisTransferredToUnionBridge(weisTransferredBalanceBeforeRelease);
    }

    @Test
    @Order(30)
    void setTransferPermissions_whenFirstVoteToEnableOnlyRelease_shouldVoteSuccessfully() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_ENABLED);
        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        // Assert that the transfer permissions remain the same
        assertUnionTransferredPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_DISABLED);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(31)
    void setTransferPermissions_whenSecondVoteToEnableOnlyRelease_shouldUpdatePermissions() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_ENABLED);

        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_ENABLED);
        assertLogUnionTransferPermissionsSet(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_ENABLED, CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);
    }

    @Test
    @Order(32)
    void requestUnionBridgeRbtc_whenRequestNowIsDisabled_shouldReturnRequestDisabled() throws VMException {
        // Arrange
        setupUnionAddressCaller();
        Coin weisTransferredBalanceBeforeRequest = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRequest = getUnionBridgeBalance();

        // Act
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);
        // Assert
        assertEquals(REQUEST_DISABLED.getCode(), requestUnionResponseCode);
        assertNoEventWasEmitted();

        // Assert union bridge balance remains the same
        assertUnionBridgeBalance(weisTransferredBalanceBeforeRequest);
        assertWeisTransferredToUnionBridge(unionBridgeBalanceBeforeRequest);
    }

    @Test
    @Order(33)
    void releaseUnionBridgeRbtc_whenReleaseIsEnabled_shouldReceiveFundsBack() throws VMException {
        // Arrange
        setupUnionAddressCaller();
        Coin weisTransferredBalanceBeforeRelease = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRelease = getUnionBridgeBalance();

        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);

        // Assert
        assertSuccessfulResponseCode(releaseUnionResponseCode);
        assertLogUnionRbtcReleased();

        Coin expectedWeisTransferredBalance = weisTransferredBalanceBeforeRelease.subtract(AMOUNT_TO_RELEASE);
        Coin expectedUnionBridgeBalance = unionBridgeBalanceBeforeRelease.subtract(AMOUNT_TO_RELEASE);
        assertWeisTransferredToUnionBridge(expectedWeisTransferredBalance);
        assertUnionBridgeBalance(expectedUnionBridgeBalance);
    }

    @Test
    @Order(34)
    void setTransferPermissions_whenFirstVoteToEnableBothPermissions_shouldVoteSuccessfully() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_ENABLED);
        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_ENABLED);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(35)
    void setTransferPermissions_whenSecondVoteToEnableBothPermissions_shouldEnablePermissions() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_ENABLED);
        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_ENABLED);
        assertLogUnionTransferPermissionsSet(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_ENABLED, CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);
    }

    @Test
    @Order(36)
    void requestUnionBridgeRbtc_whenSurpassLockingCap_shouldFail() throws VMException {
        // Arrange
        setupUnionAddressCaller();
        Coin weisTransferredBalanceBeforeRelease = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRelease = getUnionBridgeBalance();
        Coin currentLockingCap = getUnionBridgeLockingCap();
        Coin amountSurpassingUnionLockingCap = currentLockingCap.add(Coin.valueOf(1));

        // Act
        int requestUnionRbtcResponseCode = requestUnionRbtc(amountSurpassingUnionLockingCap);

        // Assert
        assertEquals(INVALID_VALUE.getCode(), requestUnionRbtcResponseCode);
        assertNoEventWasEmitted();
        assertWeisTransferredToUnionBridge(weisTransferredBalanceBeforeRelease);
        assertUnionBridgeBalance(unionBridgeBalanceBeforeRelease);
    }

    @Test
    @Order(37)
    void releaseUnionBridgeRbtc_whenSurpassWeisTransferredBalance_shouldFailAndDisableTransfer() throws VMException {
        // Arrange
        Coin weisTransferredBalanceBeforeRelease = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRelease = getUnionBridgeBalance();
        // Add extra funds to simulate the union bridge sending more than the transferred amount
        addFundsToUnionAddress(weisTransferredBalanceBeforeRelease);
        Coin amountSurpassingWeisTransferredBalance = unionBridgeBalanceBeforeRelease.add(weisTransferredBalanceBeforeRelease);

        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(amountSurpassingWeisTransferredBalance);

        // Assert
        assertEquals(INVALID_VALUE.getCode(), releaseUnionResponseCode);
        assertWeisTransferredToUnionBridge(weisTransferredBalanceBeforeRelease);
        assertUnionBridgeBalance(amountSurpassingWeisTransferredBalance);
        assertUnionTransferredPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_DISABLED);
        assertLogUnionTransferPermissionsSet(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_DISABLED, BRIDGE_ADDR);
    }

    @Test
    @Order(38)
    void setTransferPermissions_whenFirstVoteToEnableBackBothPermissions_shouldVoteSuccessfully() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_ENABLED);
        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(REQUEST_PERMISSION_DISABLED, RELEASE_PERMISSION_DISABLED);
        assertNoEventWasEmitted();
    }

    @Test
    @Order(39)
    void setTransferPermissions_whenSecondVoteToEnableBackBothPermissions_shouldEnablePermissions() throws VMException {
        // Arrange
        setupCaller(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_ENABLED);
        // Assert
        assertSuccessfulResponseCode(unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_ENABLED);
        assertLogUnionTransferPermissionsSet(REQUEST_PERMISSION_ENABLED, RELEASE_PERMISSION_ENABLED, CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);
    }

    @Test
    @Order(40)
    void requestUnionBridgeRbtc_whenPermissionsEnabledAfterForcePause_shouldTransferFundsToUnionBridge()
        throws VMException {
        // Arrange
        setupUnionAddressCaller();
        Coin weisTransferredBalanceBeforeRequest = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRequest = getUnionBridgeBalance();

        // Act
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);

        // Assert
        assertSuccessfulResponseCode(requestUnionResponseCode);
        assertLogUnionRbtcRequested();

        Coin expectedWeisTransferredBalance = weisTransferredBalanceBeforeRequest.add(AMOUNT_TO_REQUEST);
        Coin expectedUnionBridgeBalance = unionBridgeBalanceBeforeRequest.add(AMOUNT_TO_REQUEST);
        assertWeisTransferredToUnionBridge(expectedWeisTransferredBalance);
        assertUnionBridgeBalance(expectedUnionBridgeBalance);
    }

    @Test
    @Order(41)
    void releaseUnionBridgeRbtc_whenPermissionsEnabledAfterForcePause_shouldReceiveFundsBack() throws VMException {
        // Arrange
        setupUnionAddressCaller();
        Coin weisTransferredBalanceBeforeRelease = getWeisTransferredToUnionBridge();
        Coin unionBridgeBalanceBeforeRelease = getUnionBridgeBalance();

        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);

        // Assert
        assertSuccessfulResponseCode(releaseUnionResponseCode);
        assertLogUnionRbtcReleased();

        Coin expectedWeisTransferredBalance = weisTransferredBalanceBeforeRelease.subtract(AMOUNT_TO_RELEASE);
        Coin expectedUnionBridgeBalance = unionBridgeBalanceBeforeRelease.subtract(AMOUNT_TO_RELEASE);
        assertWeisTransferredToUnionBridge(expectedWeisTransferredBalance);
        assertUnionBridgeBalance(expectedUnionBridgeBalance);
    }

    private void addFundsToUnionAddress(Coin amount) {
        repository.addBalance(CURRENT_UNION_BRIDGE_ADDRESS, amount);
    }

    private static void assertSuccessfulResponseCode(int releaseUnionResponseCode) {
        assertEquals(SUCCESS.getCode(), releaseUnionResponseCode);
    }

    private void assertUnauthorizedCaller(int actualResponseCode) {
        assertEquals(
            UNAUTHORIZED_CALLER.getCode(),
            actualResponseCode,
            "Expected response code to be UNAUTHORIZED_CALLER"
        );
        assertNoEventWasEmitted();
    }

    private void assertNoEventWasEmitted() {
        assertTrue(logs.isEmpty(), "No events should have been emitted");
    }

    private void assertLogUnionTransferPermissionsSet(boolean requestEnabled, boolean releaseEnabled, RskAddress callerAddress) {
        CallTransaction.Function transferPermissionsEvent = UNION_BRIDGE_TRANSFER_PERMISSIONS_UPDATED.getEvent();
        List<DataWord> encodedTopics = getEncodedTopics(transferPermissionsEvent, callerAddress.toHexString());
        byte[] encodedData = getEncodedData(transferPermissionsEvent, requestEnabled, releaseEnabled);
        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    private void assertLogUnionLockingCapIncreased(Coin previousLockingCap, Coin newLockingCap) {
        CallTransaction.Function unionLockingCapIncreasedEvent = UNION_LOCKING_CAP_INCREASED.getEvent();
        List<DataWord> encodedTopics = getEncodedTopics(unionLockingCapIncreasedEvent, CHANGE_LOCKING_CAP_AUTHORIZER_2.toHexString());
        byte[] encodedData = getEncodedData(unionLockingCapIncreasedEvent, previousLockingCap.asBigInteger(), newLockingCap.asBigInteger());
        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    private void assertLogUnionRbtcReleased() {
        CallTransaction.Function releaseUnionRbtcEvent = UNION_RBTC_RELEASED.getEvent();
        List<DataWord> encodedTopics = getEncodedTopics(releaseUnionRbtcEvent, CURRENT_UNION_BRIDGE_ADDRESS.toHexString());
        byte[] encodedData = getEncodedData(releaseUnionRbtcEvent, AMOUNT_TO_RELEASE.asBigInteger());

        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    private void assertLogUnionRbtcRequested() {
        CallTransaction.Function releaseUnionRbtcEvent = UNION_RBTC_REQUESTED.getEvent();
        List<DataWord> encodedTopics = getEncodedTopics(releaseUnionRbtcEvent, CURRENT_UNION_BRIDGE_ADDRESS.toHexString());
        byte[] encodedData = getEncodedData(releaseUnionRbtcEvent, AMOUNT_TO_REQUEST.asBigInteger());

        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    private void setupUnauthorizedCaller() {
        setupCaller(UNAUTHORIZED_CALLER_ADDRESS);
    }

    private void setupUnionAddressCaller() {
        setupCaller(CURRENT_UNION_BRIDGE_ADDRESS);
    }

    private int updateUnionAddress() throws VMException {
        CallTransaction.Function function = SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.getFunction();
        byte[] setUnionBridgeContractAddressData = function.encode(NEW_UNION_BRIDGE_CONTRACT_ADDRESS.toHexString());
        byte[] result = bridge.execute(setUnionBridgeContractAddressData);
        BigInteger decodedResult = (BigInteger) Bridge.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.decodeResult(
            result)[0];
        return decodedResult.intValue();
    }

    private void assertNoAddressIsStored() {
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        assertNull(actualRskAddress);
    }

    private RskAddress getUnionBridgeContractAddress() throws VMException {
        CallTransaction.Function function = GET_UNION_BRIDGE_CONTRACT_ADDRESS.getFunction();
        byte[] getUnionBridgeContractAddressData = function.encode();
        byte[] result = bridge.execute(getUnionBridgeContractAddressData);
        return new RskAddress(
            (DataWord) Bridge.GET_UNION_BRIDGE_CONTRACT_ADDRESS.decodeResult(result)[0]);
    }

    private Coin getUnionBridgeLockingCap() throws VMException {
        CallTransaction.Function function = GET_UNION_BRIDGE_LOCKING_CAP.getFunction();
        byte[] getUnionBridgeLockingCapData = function.encode();
        byte[] result = bridge.execute(getUnionBridgeLockingCapData);
        BigInteger decodedResult = (BigInteger) Bridge.GET_UNION_BRIDGE_LOCKING_CAP.decodeResult(
            result)[0];
        return new Coin(decodedResult);
    }

    private int increaseUnionBridgeLockingCap(Coin newLockingCap) throws VMException {
        CallTransaction.Function function = INCREASE_UNION_BRIDGE_LOCKING_CAP.getFunction();
        byte[] increaseUnionBridgeLockingCapData = function.encode(newLockingCap.asBigInteger());
        byte[] result = bridge.execute(increaseUnionBridgeLockingCapData);
        BigInteger decodedResult = (BigInteger) Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.decodeResult(
            result)[0];
        return decodedResult.intValue();
    }

    private void setupCaller(RskAddress caller) {
        when(rskTx.getSender(signatureCache)).thenReturn(caller);
    }

    private void assertLockingCap(Coin expectedLockingCap) throws VMException {
        Coin actualLockingCap = getUnionBridgeLockingCap();
        assertEquals(expectedLockingCap, actualLockingCap);
        assertStoredUnionLockingCap(expectedLockingCap);
    }

    private void assertNoStoredUnionLockingCap() {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        assertNull(storedLockingCap, "Union bridge locking cap should not be stored when no increase has been made");
    }

    private void assertStoredUnionLockingCap(Coin expectedLockingCap) {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        assertEquals(expectedLockingCap, storedLockingCap);
    }

    private void assertNoWeisTransferredToUnionBridgeIsStored() {
        Coin actualUnionWeisTransferred = storageAccessor.getFromRepository(
            WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        assertNull(actualUnionWeisTransferred, "Weis transferred to union bridge should not be stored");
    }

    private int requestUnionRbtc(Coin amountToRequest) throws VMException {
        CallTransaction.Function function = REQUEST_UNION_BRIDGE_RBTC.getFunction();
        byte[] requestUnionBridgeRbtcData = function.encode(amountToRequest.asBigInteger());
        byte[] result = bridge.execute(requestUnionBridgeRbtcData);
        BigInteger decodedResult = (BigInteger) Bridge.REQUEST_UNION_BRIDGE_RBTC.decodeResult(
            result)[0];
        bridgeSupport.save();
        repository.commit();
        return decodedResult.intValue();
    }

    private Coin getWeisTransferredToUnionBridge() {
        return Optional.ofNullable(storageAccessor.getFromRepository(
            WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        )).orElse(Coin.ZERO);
    }

    private void assertWeisTransferredToUnionBridge(Coin expectedUnionWeisTransferred) {
        Coin actualUnionWeisTransferred = getWeisTransferredToUnionBridge();
        assertEquals(expectedUnionWeisTransferred, actualUnionWeisTransferred);
    }

    private Coin getUnionBridgeBalance() {
        return repository.getBalance(CURRENT_UNION_BRIDGE_ADDRESS);
    }

    private void assertUnionBridgeBalance(Coin expectedBalance) {
        Coin currentBalance = getUnionBridgeBalance();
        assertEquals(expectedBalance, currentBalance);
    }

    private int releaseUnionRbtc(Coin amountToRelease) throws VMException {
        when(rskTx.getValue()).thenReturn(amountToRelease);
        CallTransaction.Function function = RELEASE_UNION_BRIDGE_RBTC.getFunction();
        byte[] releaseUnionBridgeRbtcData = function.encode();
        byte[] result = bridge.execute(releaseUnionBridgeRbtcData);
        BigInteger decodedResult = (BigInteger) Bridge.RELEASE_UNION_BRIDGE_RBTC.decodeResult(
            result)[0];
        // Since transaction execution is not being done, union address' balance is not updated.
        // Therefore, we simulate the transfer to update bridge address balance.
        RskAddress from = rskTx.getSender(signatureCache);
        simulateTransferToBridgeAddress(from, amountToRelease);
        bridgeSupport.save();
        return decodedResult.intValue();
    }

    private void simulateTransferToBridgeAddress(RskAddress from, Coin amount) {
        repository.transfer(
            from,
            PrecompiledContracts.BRIDGE_ADDR,
            amount
        );
    }

    private int setUnionTransferPermissions(boolean requestEnabled, boolean releaseEnabled) throws VMException {
        CallTransaction.Function function = SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
        byte[] setUnionTransferPermissionsData = function.encode(requestEnabled, releaseEnabled);
        byte[] result = bridge.execute(setUnionTransferPermissionsData);
        BigInteger decodedResult = (BigInteger) Bridge.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.decodeResult(
            result)[0];
        return decodedResult.intValue();
    }

    private void assertNoUnionTransferredPermissionsIsStored() {
        Boolean actualRequestEnabled = storageAccessor.getFromRepository(
            UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        assertNull(actualRequestEnabled, "Union bridge request enabled should not be stored");

        Boolean actualReleaseEnabled = storageAccessor.getFromRepository(
            UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        assertNull(actualReleaseEnabled, "Union bridge release enabled should not be stored");
    }

    private void assertUnionTransferredPermissions(boolean expectedRequestEnabled, boolean expectedReleaseEnabled) {
        Boolean actualRequestEnabled = storageAccessor.getFromRepository(
            UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        assertEquals(
            expectedRequestEnabled,
            actualRequestEnabled,
            "Union bridge request enabled should match expected value"
        );

        Boolean actualReleaseEnabled = storageAccessor.getFromRepository(
            UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        assertEquals(
            expectedReleaseEnabled,
            actualReleaseEnabled,
            "Union bridge release enabled should match expected value"
        );
    }
}
