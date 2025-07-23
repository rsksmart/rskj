package co.rsk.peg.union;

import static co.rsk.peg.BridgeMethods.*;
import static co.rsk.peg.BridgeMethods.GET_UNION_BRIDGE_CONTRACT_ADDRESS;
import static co.rsk.peg.BridgeMethods.GET_UNION_BRIDGE_LOCKING_CAP;
import static co.rsk.peg.BridgeMethods.INCREASE_UNION_BRIDGE_LOCKING_CAP;
import static co.rsk.peg.BridgeMethods.RELEASE_UNION_BRIDGE_RBTC;
import static co.rsk.peg.BridgeMethods.REQUEST_UNION_BRIDGE_RBTC;
import static co.rsk.peg.BridgeMethods.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET;
import static org.ethereum.vm.PrecompiledContracts.BRIDGE_ADDR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeIllegalArgumentException;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BridgeSupportTestUtil;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.test.builders.BridgeBuilder;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.UnionBridgeSupportBuilder;
import java.math.BigInteger;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.CallTransaction.Function;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class UnionBridgeIT {

    private static final ActivationConfig lovellActivations = ActivationConfigsForTest.lovell700();
    private static final ActivationConfig allActivations = ActivationConfigsForTest.all();

    private static final BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private static final UnionBridgeConstants unionBridgeMainNetConstants = bridgeMainNetConstants.getUnionBridgeConstants();

    private static final UnionBridgeSupportBuilder unionBridgeSupportBuilder = UnionBridgeSupportBuilder.builder();

    private static final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
    private BridgeSupport bridgeSupport;

    private static final BridgeBuilder bridgeBuilder = new BridgeBuilder();
    private Bridge bridge;

    private static final RskAddress NO_AUTHORIZED = TestUtils.generateAddress(
        "NO_AUTHORIZED");

    private static final RskAddress CHANGE_UNION_ADDRESS_AUTHORIZER = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "04bd1d5747ca6564ed860df015c1a8779a35ef2a9f184b6f5390bccb51a3dcace02f88a401778be6c8fd8ed61e4d4f1f508075b3394eb6ac0251d4ed6d06ce644d"))
            .getAddress());

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
    private static final Coin NEW_LOCKING_CAP = unionBridgeMainNetConstants.getInitialLockingCap()
        .multiply(BigInteger.valueOf(
            LOCKING_CAP_INCREMENTS_MULTIPLIER));

    private static final BigInteger ONE_ETH = BigInteger.TEN.pow(
        18); // 1 ETH = 1000000000000000000 wei
    private static final Coin AMOUNT_TO_REQUEST = new co.rsk.core.Coin(ONE_ETH);
    private static final Coin AMOUNT_TO_RELEASE = new co.rsk.core.Coin(ONE_ETH);

    private Coin currentWeisTransferredBalance = Coin.ZERO;

    private Repository repository;
    private StorageAccessor storageAccessor;

    private SignatureCache signatureCache;
    private Transaction rskTx;

    @BeforeAll
    void setup() {
        repository = BridgeSupportTestUtil.createRepository();
        repository.addBalance(BRIDGE_ADDR,
            co.rsk.core.Coin.fromBitcoin(bridgeMainNetConstants.getMaxRbtc()));
        storageAccessor = new BridgeStorageAccessorImpl(repository);

        signatureCache = mock(SignatureCache.class);
        rskTx = mock(Transaction.class);
        when(rskTx.getSender(signatureCache)).thenReturn(CURRENT_UNION_BRIDGE_ADDRESS);

        UnionBridgeStorageProvider unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(
            storageAccessor);
        UnionBridgeSupport unionBridgeSupport = unionBridgeSupportBuilder
            .withStorageProvider(unionBridgeStorageProvider)
            .withConstants(unionBridgeMainNetConstants)
            .withSignatureCache(signatureCache)
            .build();

        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            BRIDGE_ADDR,
            bridgeMainNetConstants.getBtcParams(),
            lovellActivations.forBlock(0)
        );
        bridgeSupport = bridgeSupportBuilder
            .withSignatureCache(signatureCache)
            .withUnionBridgeSupport(unionBridgeSupport)
            .withRepository(repository)
            .withProvider(bridgeStorageProvider)
            .withBridgeConstants(bridgeMainNetConstants)
            .withActivations(lovellActivations.forBlock(0))
            .build();

        bridge = bridgeBuilder
            .signatureCache(signatureCache)
            .activationConfig(lovellActivations)
            .bridgeSupport(bridgeSupport)
            .transaction(rskTx)
            .build();
    }

    private void setupForAllActivations() {
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            BRIDGE_ADDR,
            bridgeMainNetConstants.getBtcParams(),
            allActivations.forBlock(0)
        );

        bridgeSupport = bridgeSupportBuilder
            .withProvider(bridgeStorageProvider)
            .withActivations(allActivations.forBlock(0))
            .build();
        bridge = bridgeBuilder
            .activationConfig(allActivations)
            .bridgeSupport(bridgeSupport)
            .build();
    }

    @Test
    @Order(0)
    void setUnionBridgeContractAddressForTestnet_whenLovell_shouldFail() {
        Function function = SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.getFunction();
        byte[] setUnionBridgeContractAddressData = function.encode(
            NEW_UNION_BRIDGE_CONTRACT_ADDRESS.toHexString());
        VMException actualException = assertThrows(VMException.class,
            () -> bridge.execute(setUnionBridgeContractAddressData));
        assertEquals(BridgeIllegalArgumentException.class, actualException.getCause().getClass());
    }

    @Test
    @Order(1)
    void getUnionBridgeContractAddress_whenLovell_shouldFail() {
        Function function = GET_UNION_BRIDGE_CONTRACT_ADDRESS.getFunction();
        byte[] getUnionBridgeContractAddressData = function.encode();
        VMException actualException = assertThrows(VMException.class,
            () -> bridge.execute(getUnionBridgeContractAddressData));
        assertEquals(BridgeIllegalArgumentException.class, actualException.getCause().getClass());
    }

    @Test
    @Order(2)
    void getUnionBridgeLockingCap_whenLovell_shouldFail() {
        Function function = GET_UNION_BRIDGE_LOCKING_CAP.getFunction();
        byte[] getUnionBridgeLockingCapData = function.encode();
        VMException actualException = assertThrows(VMException.class,
            () -> bridge.execute(getUnionBridgeLockingCapData));
        assertEquals(BridgeIllegalArgumentException.class, actualException.getCause().getClass());
    }

    @Test
    @Order(3)
    void increaseUnionBridgeLockingCap_whenLovell_shouldFail() {
        Function function = INCREASE_UNION_BRIDGE_LOCKING_CAP.getFunction();
        byte[] increaseUnionBridgeLockingCapData = function.encode(NEW_LOCKING_CAP.asBigInteger());
        VMException actualException = assertThrows(VMException.class,
            () -> bridge.execute(increaseUnionBridgeLockingCapData));
        assertEquals(BridgeIllegalArgumentException.class, actualException.getCause().getClass());
    }

    @Test
    @Order(4)
    void requestUnionBridgeRbtc_whenLovell_shouldFail() {
        Function function = REQUEST_UNION_BRIDGE_RBTC.getFunction();
        byte[] requestUnionBridgeRbtcData = function.encode(AMOUNT_TO_REQUEST.asBigInteger());
        VMException actualException = assertThrows(VMException.class,
            () -> bridge.execute(requestUnionBridgeRbtcData));
        assertEquals(BridgeIllegalArgumentException.class, actualException.getCause().getClass());
    }

    @Test
    @Order(5)
    void releaseUnionBridgeRbtc_whenLovell_shouldFail() {
        Function function = RELEASE_UNION_BRIDGE_RBTC.getFunction();
        when(rskTx.getValue()).thenReturn(AMOUNT_TO_RELEASE);
        byte[] releaseUnionBridgeRbtcData = function.encode();
        VMException actualException = assertThrows(VMException.class,
            () -> bridge.execute(releaseUnionBridgeRbtcData));
        assertEquals(BridgeIllegalArgumentException.class, actualException.getCause().getClass());
    }

    @Test
    @Order(6)
    void setUnionBridgeTransferPermissions_whenLovell_shouldFail() {
        Function function = SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
        byte[] setUnionTransferPermissionsData = function.encode(true, true);
        VMException actualException = assertThrows(VMException.class,
            () -> bridge.execute(setUnionTransferPermissionsData));
        assertEquals(BridgeIllegalArgumentException.class, actualException.getCause().getClass());
    }

    @Test
    @Order(7)
    void setUnionBridgeContractAddressForTestnet_whenAllActivations_shouldReturnEnvironmentDisabled()
        throws VMException {
        // Setup for all activations
        setupForAllActivations();

        // Setup authorizer for changing union address
        setupAuthorizer(CHANGE_UNION_ADDRESS_AUTHORIZER);

        // Attempt to update union address when mainnet network. It should fail.
        int actualUnionResponseCode = updateUnionAddress(NEW_UNION_BRIDGE_CONTRACT_ADDRESS);

        // Assert mainnet network does not allow updating union address
        assertEquals(UnionResponseCode.ENVIRONMENT_DISABLED.getCode(), actualUnionResponseCode);
        assertNoAddressIsStored();

        // Assert that the union address continues to be the constant address
        RskAddress actualUnionAddress = getUnionBridgeContractAddress();
        assertEquals(CURRENT_UNION_BRIDGE_ADDRESS, actualUnionAddress);
    }

    @Test
    @Order(8)
    void setUnionBridgeContractAddressForTestnet_whenMainnet_shouldReturnEnvironmentDisabled()
        throws VMException {
        // Attempt to update union address when mainnet network. It should fail.
        int actualUnionResponseCode = updateUnionAddress(NEW_UNION_BRIDGE_CONTRACT_ADDRESS);
        assertEquals(UnionResponseCode.ENVIRONMENT_DISABLED.getCode(), actualUnionResponseCode);

        // // Assert union address remains unchanged
        RskAddress updatedUnionAddress = getUnionBridgeContractAddress();
        assertEquals(CURRENT_UNION_BRIDGE_ADDRESS, updatedUnionAddress);
    }

    @Test
    @Order(9)
    void increaseUnionBridgeLockingCap_whenNoAuthorized_shouldIncreaseLockingCap() throws VMException {
        Coin actualUnionLockingCap = getUnionBridgeLockingCap();
        assertEquals(INITIAL_LOCKING_CAP, actualUnionLockingCap);

        int actualUnionResponseCode = increaseUnionBridgeLockingCap(NEW_LOCKING_CAP);
        assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER.getCode(), actualUnionResponseCode);

        // Assert that the locking cap remains unchanged
        assertEquals(INITIAL_LOCKING_CAP, getUnionBridgeLockingCap());
        // Assert that the stored union locking cap is still null since no increased has been made
        assertStoredUnionLockingCap(null);
    }

    @Test
    @Order(10)
    void increaseUnionBridgeLockingCap_whenFirstVote_shouldVoteSuccessful() throws VMException {
        setupAuthorizer(CHANGE_LOCKING_CAP_AUTHORIZER_1);

        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(NEW_LOCKING_CAP);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);

        // Assert that the locking cap remains unchanged
        Coin actualUnionLockingCap = getUnionBridgeLockingCap();
        assertEquals(INITIAL_LOCKING_CAP, actualUnionLockingCap);
        assertStoredUnionLockingCap(null);
    }

    @Test
    @Order(11)
    void increaseUnionBridgeLockingCap_whenSecondVoteForDifferentValue_shouldVoteSuccessful() throws VMException {
        Coin differentLockingCap = NEW_LOCKING_CAP.subtract(new Coin(BigInteger.TEN));
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(differentLockingCap);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);

        // Assert that the locking cap remains unchanged
        Coin actualUnionLockingCap = getUnionBridgeLockingCap();
        assertEquals(INITIAL_LOCKING_CAP, actualUnionLockingCap);
        assertStoredUnionLockingCap(null);
    }

    @Test
    @Order(12)
    void increaseUnionBridgeLockingCap_whenThirdVoteForSameValue_shouldVoteSuccessful() throws VMException {
        setupAuthorizer(CHANGE_LOCKING_CAP_AUTHORIZER_2);

        int actualUnionResponseCode = increaseUnionBridgeLockingCap(NEW_LOCKING_CAP);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);

        // Assert that the locking cap is updated
        assertLockingCap(NEW_LOCKING_CAP);
    }

    @Test
    @Order(13)
    void increaseUnionBridgeLockingCap_whenSmallerLockingCap_shouldReturnInvalidValue() throws VMException {
        // Attempt to increase the locking cap with a smaller value than the current one
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(INITIAL_LOCKING_CAP);
        // Assert that the response code is INVALID_VALUE
        assertEquals(UnionResponseCode.INVALID_VALUE.getCode(), actualUnionResponseCode);

        // Assert that the locking cap remains unchanged
        assertLockingCap(NEW_LOCKING_CAP);
    }

    @Test
    @Order(14)
    void requestUnionBridgeRbtc_whenUnauthorized_shouldReturnUnauthorized() throws VMException {
        // Assert that no weisTransferred to union bridge is stored before any request/release
        assertNoWeisTransferredToUnionBridgeIsStored();
        assertUnionBridgeBalance(Coin.ZERO);

        // Assert that the union bridge rBTC can be requested
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);
        assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER.getCode(), requestUnionResponseCode);

        // Assert WEIS_TRANSFERRED_TO_UNION_BRIDGE is null in the storage since no request was made
        assertNoWeisTransferredToUnionBridgeIsStored();

        // Assert that the union bridge address has the expected balance
        assertUnionBridgeBalance(Coin.ZERO);
    }

    @Test
    @Order(15)
    void requestUnionBridgeRbtc_whenAuthorized_shouldRequestUnionBridgeRbtc() throws VMException {
        setupUnionAddressAsCaller();

        // ACT
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), requestUnionResponseCode);
        assertWeisTransferredToUnionBridge(AMOUNT_TO_REQUEST);

        // Assert that the union bridge address has the expected balance
        assertUnionBridgeBalance(AMOUNT_TO_REQUEST);

        // Add the amount requested to the current transferred balance
        currentWeisTransferredBalance = currentWeisTransferredBalance.add(AMOUNT_TO_REQUEST);
    }

    @Test
    @Order(16)
    void releaseUnionBridgeRbtc_whenUnauthorized_shouldReturnUnauthorized()
        throws VMException {
        setupNoAuthorized();

        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);
        assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER.getCode(), releaseUnionResponseCode);

        assertWeisTransferredToUnionBridge(currentWeisTransferredBalance);

        assertUnionBridgeBalance(AMOUNT_TO_RELEASE);
    }

    @Test
    @Order(17)
    void releaseUnionBridgeRbtc_whenAllActivations_shouldReleaseUnionBridgeRbtc()
        throws VMException {
        setupUnionAddressAsCaller();

        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);
        currentWeisTransferredBalance = currentWeisTransferredBalance.subtract(AMOUNT_TO_RELEASE);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), releaseUnionResponseCode);
        // After release, the transferred amount should be zero
        assertWeisTransferredToUnionBridge(
            Coin.ZERO);

        // Since the transaction is not being executed, the transfer to the bridge address is not performed.
        // Then the union bridge balances still with the amount sent to the bridge. It means the release didn't affect the union bridge balance.
        // Let's simulate the transfer to the bridge address
        simulateTransferBackToBridgeAddress(AMOUNT_TO_RELEASE);

        // Assert that the union address balance is equal to zero
        assertUnionBridgeBalance(Coin.ZERO);
    }

    @Test
    @Order(18)
    void setTransferPermissions_whenUnauthorized_shouldReturnUnauthorized() throws VMException {
        setupNoAuthorized();

        assertNoUnionTransferredPermissionsIsStored();

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(false, false);

        // Assert
        assertEquals(UnionResponseCode.UNAUTHORIZED_CALLER.getCode(), unionTransferPermissionsResponseCode);
        assertNoUnionTransferredPermissionsIsStored();
    }

    @Test
    @Order(19)
    void setTransferPermissions_whenFirstVote_shouldVoteSuccessful() throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(false, false);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);

        // Assert transferred permissions remain unchanged
        assertNoUnionTransferredPermissionsIsStored();
    }

    @Test
    @Order(20)
    void setTransferPermissions_whenSecondVoteForDifferentValue_shouldVoteSuccessful() throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(false, true);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);

        // Assert transferred permissions remain unchanged
        assertNoUnionTransferredPermissionsIsStored();
    }

    @Test
    @Order(21)
    void setTransferPermissions_whenThirdVoteForSameValue_shouldVoteSuccessful() throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(false, false);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);

        // Assert transferred permissions are updated
        assertUnionTransferredPermissions(false, false);
    }

    @Test
    @Order(22)
    void requestUnionBridgeRbtc_whenRequestIsDisabled_shouldReturnDisabledCode()
        throws VMException {
        // Arrange
        setupUnionAddressAsCaller();
        assertWeisTransferredToUnionBridge(currentWeisTransferredBalance);

        int requestUnionResponseCodeAfterUpdate = requestUnionRbtc(AMOUNT_TO_REQUEST);
        assertEquals(UnionResponseCode.REQUEST_DISABLED.getCode(),
            requestUnionResponseCodeAfterUpdate,
            "Requesting union rBTC should fail when request permission is disabled");

        // Assert that the balance remains the same
        assertWeisTransferredToUnionBridge(currentWeisTransferredBalance);
        assertUnionBridgeBalance(currentWeisTransferredBalance);
    }

    @Test
    @Order(23)
    void increaseUnionBridgeLockingCap_whenFirstVoteWhileTransferIsDisabled_shouldVoteSuccessful() throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_LOCKING_CAP_AUTHORIZER_1);

        Coin lockingCapBeforeIncrement = getUnionBridgeLockingCap();
        Coin newLockingCap = NEW_LOCKING_CAP.multiply(
            BigInteger.valueOf(LOCKING_CAP_INCREMENTS_MULTIPLIER));

        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(newLockingCap);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);

        // Assert that the locking cap remains unchanged
        assertLockingCap(lockingCapBeforeIncrement);
    }

    @Test
    @Order(24)
    void increaseUnionBridgeLockingCap_whenSecondVoteWhileTransferIsDisabled_shouldIncrementLockingCap() throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_LOCKING_CAP_AUTHORIZER_2);

        Coin newLockingCap = NEW_LOCKING_CAP.multiply(
            BigInteger.valueOf(LOCKING_CAP_INCREMENTS_MULTIPLIER));

        // Act
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(newLockingCap);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);

        // Assert that the locking cap has been updated
        assertLockingCap(newLockingCap);
    }

    @Test
    @Order(25)
    void setUnionBridgeContractAddressForTestnet_whenMainnetAndTransferIsDisabled_shouldFail()
        throws VMException {
        setupAuthorizer(CHANGE_UNION_ADDRESS_AUTHORIZER);
        RskAddress actualUnionAddress = getUnionBridgeContractAddress();
        assertEquals(CURRENT_UNION_BRIDGE_ADDRESS, actualUnionAddress);

        int actualUnionResponseCode = updateUnionAddress(NEW_UNION_BRIDGE_CONTRACT_ADDRESS);
        assertEquals(UnionResponseCode.ENVIRONMENT_DISABLED.getCode(), actualUnionResponseCode);

        // Assert that the union address remains unchanged
        assertNoAddressIsStored();
    }

    @Test
    @Order(26)
    void setTransferPermissions_whenFirstVoteToEnableOnlyRequest_shouldVoteSuccessful()
        throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);
        assertUnionTransferredPermissions(false, false);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(true, false);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);

        // Assert that the transfer permissions remain the same
        assertUnionTransferredPermissions(false, false);
    }

    @Test
    @Order(27)
    void setTransferPermissions_whenSecondVoteToEnableOnlyRequest_shouldUpdatePermissions()
        throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(true, false);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(true, false);
    }

    @Test
    @Order(28)
    void requestUnionBridgeRbtc_whenRequestIsEnabled_shouldRequestUnionBridgeRbtc()
        throws VMException {
        // Arrange
        setupUnionAddressAsCaller();
        assertEquals(Coin.ZERO, currentWeisTransferredBalance);
        assertUnionBridgeBalance(Coin.ZERO);

        // Act
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);
        currentWeisTransferredBalance = currentWeisTransferredBalance.add(AMOUNT_TO_REQUEST);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), requestUnionResponseCode);
        assertWeisTransferredToUnionBridge(AMOUNT_TO_REQUEST);

        // Assert that the union bridge address has the expected balance
        assertUnionBridgeBalance(AMOUNT_TO_REQUEST);
    }

    @Test
    @Order(29)
    void releaseUnionBridgeRbtc_whenReleaseIsDisabled_shouldReturnDisabledCode()
        throws VMException {
        assertUnionBridgeBalance(currentWeisTransferredBalance);

        // Act
        int releaseUnionResponseCodeAfterUpdate = releaseUnionRbtc(AMOUNT_TO_RELEASE);
        simulateTransferBackToBridgeAddress(AMOUNT_TO_RELEASE);

        // Assert
        assertEquals(UnionResponseCode.RELEASE_DISABLED.getCode(), releaseUnionResponseCodeAfterUpdate);
        assertWeisTransferredToUnionBridge(currentWeisTransferredBalance);
        // Union Balance should be equal to the amount release refunded
        assertUnionBridgeBalance(currentWeisTransferredBalance);
    }

    @Test
    @Order(30)
    void setTransferPermissions_whenFirstVoteToEnableOnlyRelease_shouldVoteSuccessful()
        throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(false, true);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);
        // Assert that the transfer permissions remain the same
        assertUnionTransferredPermissions(true, false);
    }

    @Test
    @Order(31)
    void setTransferPermissions_whenSecondVoteToEnableOnlyRelease_shouldUpdatePermissions()
        throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);

        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(false, true);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(false, true);
    }

    @Test
    @Order(32)
    void requestUnionBridgeRbtc_whenRequestNowIsDisabled_shouldReturnDisabledCode()
        throws VMException {
        // Arrange
        setupUnionAddressAsCaller();
        Coin unionAddressBalanceBeforeRequestUnionRbtc = repository.getBalance(CURRENT_UNION_BRIDGE_ADDRESS);

        // Act
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);

        assertEquals(UnionResponseCode.REQUEST_DISABLED.getCode(), requestUnionResponseCode);
        // Assert union bridge balance remains the same
        assertUnionBridgeBalance(unionAddressBalanceBeforeRequestUnionRbtc);
    }

    @Test
    @Order(33)
    void releaseUnionBridgeRbtc_whenReleaseIsEnabled_shouldReleaseUnionBridgeRbtc()
        throws VMException {
        // Arrange
        Coin unionAddressBalanceBeforeRequestUnionRbtc = repository.getBalance(CURRENT_UNION_BRIDGE_ADDRESS);
        assertEquals(unionAddressBalanceBeforeRequestUnionRbtc, currentWeisTransferredBalance);

        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);
        simulateTransferBackToBridgeAddress(AMOUNT_TO_RELEASE);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), releaseUnionResponseCode);
        assertWeisTransferredToUnionBridge(Coin.ZERO);
        Coin expectedUnionBalance = unionAddressBalanceBeforeRequestUnionRbtc.subtract(AMOUNT_TO_RELEASE);
        assertUnionBridgeBalance(expectedUnionBalance);
    }

    @Test
    @Order(34)
    void setTransferPermissions_whenFirstVoteToEnableBothPermissions_shouldVoteSuccessful() throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(true, true);
        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(false, true);
    }

    @Test
    @Order(35)
    void setTransferPermissions_whenSecondVoteToEnableBothPermissions_shouldEnablePermissions() throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(true, true);
        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(true, true);
    }

    @Test
    @Order(36)
    void requestUnionBridgeRbtc_whenSurpassLockingCap_shouldFail() throws VMException {
        // Arrange
        setupUnionAddressAsCaller();
        Coin currentLockingCap = getUnionBridgeLockingCap();
        Coin amountToRequest = currentLockingCap.add(Coin.valueOf(1)); // Request more than the locking cap
        // Act
        int requestUnionRbtcResponseCode = requestUnionRbtc(amountToRequest);
        // Assert
        assertEquals(UnionResponseCode.INVALID_VALUE.getCode(), requestUnionRbtcResponseCode);
    }

    @Test
    @Order(37)
    void releaseUnionBridgeRbtc_whenSurpassWeisTransferredBalance_shouldFailAndDisable() throws VMException {
        // Arrange
        assertWeisTransferredToUnionBridge(Coin.ZERO);
        // Add some balance to the union bridge address from unknown souce
        repository.addBalance(CURRENT_UNION_BRIDGE_ADDRESS, AMOUNT_TO_RELEASE);
        Coin amountToRelease = AMOUNT_TO_RELEASE;
        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(amountToRelease);
        simulateTransferBackToBridgeAddress(amountToRelease);
        // Assert
        assertEquals(UnionResponseCode.INVALID_VALUE.getCode(), releaseUnionResponseCode);
        assertWeisTransferredToUnionBridge(Coin.ZERO);
        assertUnionBridgeBalance(amountToRelease);
        assertUnionTransferredPermissions(false, false);
    }

    @Test
    @Order(38)
    void setTransferPermissions_whenFirstVoteToEnableBackBothPermissions_shouldVoteSuccessful() throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_1);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(true, true);
        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(false, false);
    }

    @Test
    @Order(39)
    void setTransferPermissions_whenSecondVoteToEnableBackBothPermissions_shouldEnablePermissions() throws VMException {
        // Arrange
        setupAuthorizer(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER_2);
        // Act
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(true, true);
        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(true, true);
    }

    @Test
    @Order(40)
    void requestUnionBridgeRbtc_whenPermissionsEnabledAfterForcePause_shouldRequestUnionRbtc()
        throws VMException {
        // Arrange
        setupUnionAddressAsCaller();
        Coin unionAddressBalanceBeforeRequestUnionRbtc = repository.getBalance(CURRENT_UNION_BRIDGE_ADDRESS);
        assertWeisTransferredToUnionBridge(Coin.ZERO);

        // Act
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);
        currentWeisTransferredBalance = currentWeisTransferredBalance.add(AMOUNT_TO_REQUEST);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), requestUnionResponseCode);
        assertWeisTransferredToUnionBridge(AMOUNT_TO_REQUEST);

        // Assert that the union bridge address has the expected balance
        Coin updatedUnionAddressBalance = unionAddressBalanceBeforeRequestUnionRbtc.add(AMOUNT_TO_REQUEST);
        assertUnionBridgeBalance(updatedUnionAddressBalance);
    }

    @Test
    @Order(41)
    void releaseUnionBridgeRbtc_whenPermissionsEnabledAfterForcePause_shouldReleaseUnionRbtc()
        throws VMException {
        // Arrange
        Coin unionAddressBalanceBeforeRequestUnionRbtc = repository.getBalance(CURRENT_UNION_BRIDGE_ADDRESS);
        assertEquals(unionAddressBalanceBeforeRequestUnionRbtc, currentWeisTransferredBalance);

        // Act
        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);
        simulateTransferBackToBridgeAddress(AMOUNT_TO_RELEASE);

        // Assert
        assertEquals(UnionResponseCode.SUCCESS.getCode(), releaseUnionResponseCode);
        assertWeisTransferredToUnionBridge(Coin.ZERO);
        Coin expectedUnionBalance = unionAddressBalanceBeforeRequestUnionRbtc.subtract(AMOUNT_TO_RELEASE);
        assertUnionBridgeBalance(expectedUnionBalance);
    }

    private void setupNoAuthorized() {
        when(rskTx.getSender(signatureCache)).thenReturn(NO_AUTHORIZED);
    }

    private void setupUnionAddressAsCaller() {
        when(rskTx.getSender(signatureCache)).thenReturn(CURRENT_UNION_BRIDGE_ADDRESS);
    }

    private int updateUnionAddress(RskAddress newUnionAddress) throws VMException {
        CallTransaction.Function function = SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.getFunction();
        byte[] setUnionBridgeContractAddressData = function.encode(newUnionAddress.toHexString());
        byte[] result = bridge.execute(setUnionBridgeContractAddressData);
        BigInteger decodedResult = (BigInteger) Bridge.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.decodeResult(
            result)[0];
        return decodedResult.intValue();
    }

    private void assertNoAddressIsStored() {
        RskAddress actualRskAddress = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            BridgeSerializationUtils::deserializeRskAddress
        );
        Assertions.assertNull(actualRskAddress);
    }

    private RskAddress getUnionBridgeContractAddress() throws VMException {
        CallTransaction.Function function = GET_UNION_BRIDGE_CONTRACT_ADDRESS.getFunction();
        byte[] setUnionBridgeContractAddressData = function.encode();
        byte[] result = bridge.execute(setUnionBridgeContractAddressData);
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

    private void setupAuthorizer(RskAddress authorizer) {
        when(rskTx.getSender(signatureCache)).thenReturn(authorizer);
    }

    private void assertLockingCap(Coin expectedLockingCap) throws VMException {
        Coin actualLockingCap = getUnionBridgeLockingCap();
        assertEquals(expectedLockingCap, actualLockingCap);
        assertStoredUnionLockingCap(expectedLockingCap);
    }

    private void assertStoredUnionLockingCap(Coin expectedLockingCap) {
        Coin storedLockingCap = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        Assertions.assertEquals(expectedLockingCap, storedLockingCap);
    }

    private void assertNoWeisTransferredToUnionBridgeIsStored() {
        Coin actualUnionWeisTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        assertNull(actualUnionWeisTransferred,
            "Weis transferred to union bridge should not be stored");
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

    private void assertWeisTransferredToUnionBridge(Coin expectedUnionWeisTransferred) {
        Coin actualUnionWeisTransferred = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            BridgeSerializationUtils::deserializeRskCoin
        );
        Assertions.assertEquals(expectedUnionWeisTransferred, actualUnionWeisTransferred);
    }

    private void assertUnionBridgeBalance(Coin expectedRefundedAmount) {
        Coin currentBalance = repository.getBalance(CURRENT_UNION_BRIDGE_ADDRESS);
        assertEquals(expectedRefundedAmount, currentBalance);
    }

    private int releaseUnionRbtc(Coin amountToRelease) throws VMException {
        when(rskTx.getValue()).thenReturn(amountToRelease);
        CallTransaction.Function function = RELEASE_UNION_BRIDGE_RBTC.getFunction();
        byte[] releaseUnionBridgeRbtcData = function.encode();
        byte[] result = bridge.execute(releaseUnionBridgeRbtcData);
        BigInteger decodedResult = (BigInteger) Bridge.RELEASE_UNION_BRIDGE_RBTC.decodeResult(
            result)[0];
        bridgeSupport.save();
        return decodedResult.intValue();
    }

    private void simulateTransferBackToBridgeAddress(Coin amountToRequest) {
        repository.transfer(
            CURRENT_UNION_BRIDGE_ADDRESS,
            PrecompiledContracts.BRIDGE_ADDR,
            amountToRequest
        );

        repository.save();
        repository.commit();
    }

    private int setUnionTransferPermissions(boolean requestEnabled, boolean releaseEnabled)
        throws VMException {
        CallTransaction.Function function = SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
        byte[] setUnionTransferPermissionsData = function.encode(requestEnabled, releaseEnabled);
        byte[] result = bridge.execute(setUnionTransferPermissionsData);
        BigInteger decodedResult = (BigInteger) Bridge.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.decodeResult(
            result)[0];
        return decodedResult.intValue();
    }

    private void assertNoUnionTransferredPermissionsIsStored() {
        Boolean actualRequestEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        Assertions.assertNull(actualRequestEnabled,
            "Union bridge request enabled should not be stored");

        Boolean actualReleaseEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        Assertions.assertNull(actualReleaseEnabled,
            "Union bridge release enabled should not be stored");
    }

    private void assertUnionTransferredPermissions(boolean expectedRequestEnabled,
        boolean expectedReleaseEnabled) {
        Boolean actualRequestEnabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        Assertions.assertEquals(expectedRequestEnabled, actualRequestEnabled,
            "Union bridge request enabled should match expected value");

        Boolean actualRequestDisabled = storageAccessor.getFromRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
            BridgeSerializationUtils::deserializeBoolean
        );
        Assertions.assertEquals(expectedReleaseEnabled, actualRequestDisabled,
            "Union bridge release enabled should match expected value");
    }
}
