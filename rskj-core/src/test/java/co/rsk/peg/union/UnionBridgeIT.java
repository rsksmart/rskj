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

    private static final RskAddress CHANGE_UNION_ADDRESS_AUTHORIZER = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
            .getAddress());

    private static final RskAddress CHANGE_LOCKING_CAP_AUTHORIZER = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "049929eb3c107a65108830f4c221068f42301bd8b054f91bd594944e7fb488fd1c93a8921fb28d3494769598eb271cd2834a31c5bd08fa075170b3da804db00a5b"))
            .getAddress());

    private static final RskAddress CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER = new RskAddress(
        ECKey.fromPublicOnly(Hex.decode(
                "04ea24f3943dff3b9b8abc59dbdf1bd2c80ec5b61f5c2c6dfcdc189299115d6d567df34c52b7e678cc9934f4d3d5491b6e53fa41a32f58a71200396f1e11917e8f"))
            .getAddress());

    private static final RskAddress CURRENT_UNION_BRIDGE_ADDRESS = new RskAddress(
        "5988645d30cd01e4b3bc2c02cb3909dec991ae31");
    private static final RskAddress NEW_UNION_BRIDGE_CONTRACT_ADDRESS = TestUtils.generateAddress(
        "newUnionBridgeContractAddress");

    private static final int LOCKING_CAP_INCREMENTS_MULTIPLIER = unionBridgeMainNetConstants.getLockingCapIncrementsMultiplier();
    private static final Coin INITIAL_LOCKING_CAP = unionBridgeMainNetConstants.getInitialLockingCap();
    private static Coin NEW_LOCKING_CAP = unionBridgeMainNetConstants.getInitialLockingCap()
        .multiply(BigInteger.valueOf(
            LOCKING_CAP_INCREMENTS_MULTIPLIER));

    private static final BigInteger ONE_ETH = BigInteger.TEN.pow(
        18); // 1 ETH = 1000000000000000000 wei
    private static final Coin AMOUNT_TO_REQUEST = new co.rsk.core.Coin(ONE_ETH);
    private static final Coin AMOUNT_TO_RELEASE = new co.rsk.core.Coin(ONE_ETH);

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
    void increaseUnionBridgeLockingCap_whenAllActivations_shouldIncreaseLockingCap() throws VMException {
        // Assert that the locking cap is the initial value constant
        Coin actualUnionLockingCap = getUnionBridgeLockingCap();
        assertEquals(INITIAL_LOCKING_CAP, actualUnionLockingCap);

        int actualUnionResponseCode = increaseUnionBridgeLockingCap(NEW_LOCKING_CAP);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);
        assertStoredUnionLockingCap(NEW_LOCKING_CAP);
    }

    @Test
    @Order(9)
    void increaseUnionBridgeLockingCap_whenSmallerLockingCap_shouldReturnInvalidValue() throws VMException {
        // Attempt to increase the locking cap with a smaller value than the current one
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(INITIAL_LOCKING_CAP);
        // Assert that the response code is INVALID_VALUE
        assertEquals(UnionResponseCode.INVALID_VALUE.getCode(), actualUnionResponseCode);

        // Assert that the locking cap remains unchanged
        assertStoredUnionLockingCap(NEW_LOCKING_CAP);
    }

    @Test
    @Order(10)
    void requestUnionBridgeRbtc_whenAllActivations_shouldRequestUnionBridgeRbtc() throws VMException {
        // Assert that no weisTransferred to union bridge is stored before any request/release
        assertNoWeisTransferredToUnionBridgeIsStored();

        // Assert that the union bridge rBTC can be requested
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), requestUnionResponseCode);
        assertWeisTransferredToUnionBridge(AMOUNT_TO_REQUEST);

        // Assert that the union bridge address has the expected balance
        assertUnionBridgeBalance(AMOUNT_TO_REQUEST);
    }

    @Test
    @Order(11)
    void releaseUnionBridgeRbtc_whenAllActivations_shouldReleaseUnionBridgeRbtc()
        throws VMException {
        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), releaseUnionResponseCode);
        // After release, the transferred amount should be zero
        assertWeisTransferredToUnionBridge(
            Coin.ZERO);

        // Since the transaction is not being executed, the transfer to the bridge address is not performed.
        // Then the union bridge balance still with the weis transferred. It means the release didn't affect the union bridge balance.
        // Let's simulate the transfer to the bridge address
        simulateTransferBackToBridgeAddress(AMOUNT_TO_RELEASE);

        // Assert that the union address balance is equal to zero
        assertUnionBridgeBalance(Coin.ZERO);
    }

    @Test
    @Order(12)
    void setTransferPermissions_whenAllActivations_shouldDisableRequestAndRelease() throws VMException {
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(false, false);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(false, false);
    }

    @Test
    @Order(13)
    void requestUnionBridgeRbtc_whenRequestIsDisabled_shouldReturnDisabledCode()
        throws VMException {
        int requestUnionResponseCodeAfterUpdate = requestUnionRbtc(AMOUNT_TO_REQUEST);
        assertEquals(UnionResponseCode.REQUEST_DISABLED.getCode(),
            requestUnionResponseCodeAfterUpdate,
            "Requesting union rBTC should fail when request permission is disabled");
        // Assert no funds were refunded and the union bridge balance remains the same
        assertUnionBridgeBalance(Coin.ZERO);
    }

    @Test
    @Order(14)
    void releaseUnionBridgeRbtc_whenReleaseIsDisabled_shouldReturnDisabledCode()
        throws VMException {
        int releaseUnionResponseCodeAfterUpdate = releaseUnionRbtc(AMOUNT_TO_RELEASE);
        assertEquals(UnionResponseCode.RELEASE_DISABLED.getCode(),
            releaseUnionResponseCodeAfterUpdate,
            "Releasing union rBTC should fail when release permission is disabled");
        // Union Balance should be equal to the amount release refunded
        assertUnionBridgeBalance(AMOUNT_TO_RELEASE);
    }

    @Test
    @Order(15)
    void increaseUnionBridgeLockingCap_whenTransferIsDisabled_shouldIncrease() throws VMException {
        Coin newLockingCap = NEW_LOCKING_CAP.multiply(
            BigInteger.valueOf(LOCKING_CAP_INCREMENTS_MULTIPLIER));
        int actualUnionResponseCode = increaseUnionBridgeLockingCap(newLockingCap);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);
        // Assert that the locking cap is updated
        assertStoredUnionLockingCap(newLockingCap);
    }

    @Test
    @Order(16)
    void setUnionBridgeContractAddressForTestnet_whenMainnetAndTransferIsDisabled_shouldFail()
        throws VMException {
        RskAddress actualUnionAddress = getUnionBridgeContractAddress();
        assertEquals(CURRENT_UNION_BRIDGE_ADDRESS, actualUnionAddress);

        int actualUnionResponseCode = updateUnionAddress(NEW_UNION_BRIDGE_CONTRACT_ADDRESS);
        assertEquals(UnionResponseCode.ENVIRONMENT_DISABLED.getCode(), actualUnionResponseCode);

        // Assert that the union address remains unchanged
        assertNoAddressIsStored();
    }

    @Test
    @Order(17)
    void setTransferPermissions_whenEnableOnlyRequest_shouldEnableRequestPermission()
        throws VMException {
        int unionTransferPermissionsResponseCode = setUnionTransferPermissions(true, false);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), unionTransferPermissionsResponseCode);
        assertUnionTransferredPermissions(true, false);
    }

    @Test
    @Order(18)
    void requestUnionBridgeRbtc_whenRequestIsEnabled_shouldRequestUnionBridgeRbtc()
        throws VMException {
        Coin currentUnionAddressBalance = repository.getBalance(CURRENT_UNION_BRIDGE_ADDRESS);
        int requestUnionResponseCode = requestUnionRbtc(AMOUNT_TO_REQUEST);
        assertEquals(UnionResponseCode.SUCCESS.getCode(), requestUnionResponseCode);
        assertWeisTransferredToUnionBridge(AMOUNT_TO_REQUEST);

        // Assert that the union bridge address has the expected balance
        Coin expectedUnionAddressBalance = currentUnionAddressBalance.add(AMOUNT_TO_REQUEST);
        assertUnionBridgeBalance(expectedUnionAddressBalance);
    }

    @Test
    @Order(19)
    void releaseUnionBridgeRbtc_whenReleaseContinueDisabled_shouldReturnDisabledCode()
        throws VMException {
        Coin currentUnionAddressBalance = repository.getBalance(CURRENT_UNION_BRIDGE_ADDRESS);

        int releaseUnionResponseCode = releaseUnionRbtc(AMOUNT_TO_RELEASE);

        assertEquals(UnionResponseCode.RELEASE_DISABLED.getCode(), releaseUnionResponseCode);

        // The bridge should refund the amount released
        Coin expectedUnionAddressBalance = currentUnionAddressBalance.add(AMOUNT_TO_RELEASE);
        assertUnionBridgeBalance(expectedUnionAddressBalance);
    }

    private void setupChangeUnionAddressAuthorizer() {
        when(rskTx.getSender(signatureCache)).thenReturn(CHANGE_UNION_ADDRESS_AUTHORIZER);
    }

    private void setupUnionAddressAsCaller() {
        when(rskTx.getSender(signatureCache)).thenReturn(CURRENT_UNION_BRIDGE_ADDRESS);
    }

    private void setupChangeTransferPermissionsAuthorizer() {
        when(rskTx.getSender(signatureCache)).thenReturn(CHANGE_TRANSFER_PERMISSIONS_AUTHORIZER);
    }

    private int updateUnionAddress(RskAddress newUnionAddress) throws VMException {
        setupChangeUnionAddressAuthorizer();
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
        setupChangeLockingCapAuthorizer();
        CallTransaction.Function function = INCREASE_UNION_BRIDGE_LOCKING_CAP.getFunction();
        byte[] increaseUnionBridgeLockingCapData = function.encode(newLockingCap.asBigInteger());
        byte[] result = bridge.execute(increaseUnionBridgeLockingCapData);
        BigInteger decodedResult = (BigInteger) Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.decodeResult(
            result)[0];
        return decodedResult.intValue();
    }

    private void setupChangeLockingCapAuthorizer() {
        when(rskTx.getSender(signatureCache)).thenReturn(CHANGE_LOCKING_CAP_AUTHORIZER);
    }

    private void assertStoredUnionLockingCap(Coin expectedLockingCap) throws VMException {
        Coin actualLockingCap = getUnionBridgeLockingCap();
        assertEquals(expectedLockingCap, actualLockingCap);
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
        setupUnionAddressAsCaller();
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
        setupUnionAddressAsCaller();
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
        setupChangeTransferPermissionsAuthorizer();
        CallTransaction.Function function = SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
        byte[] setUnionTransferPermissionsData = function.encode(requestEnabled, releaseEnabled);
        byte[] result = bridge.execute(setUnionTransferPermissionsData);
        BigInteger decodedResult = (BigInteger) Bridge.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.decodeResult(
            result)[0];
        return decodedResult.intValue();
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
