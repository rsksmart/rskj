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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeIllegalArgumentException;
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
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.CallTransaction.Function;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.vm.exception.VMException;
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

    private static final BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private static final UnionBridgeConstants unionBridgeMainNetConstants = bridgeMainNetConstants.getUnionBridgeConstants();

    private static final UnionBridgeSupportBuilder unionBridgeSupportBuilder = UnionBridgeSupportBuilder.builder();

    private static final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();

    private static final BridgeBuilder bridgeBuilder = new BridgeBuilder();
    private Bridge bridge;

    private static final RskAddress CURRENT_UNION_BRIDGE_ADDRESS = new RskAddress(
        "5988645d30cd01e4b3bc2c02cb3909dec991ae31");
    private static final RskAddress NEW_UNION_BRIDGE_CONTRACT_ADDRESS = TestUtils.generateAddress(
        "newUnionBridgeContractAddress");

    private static final int LOCKING_CAP_INCREMENTS_MULTIPLIER = unionBridgeMainNetConstants.getLockingCapIncrementsMultiplier();
    private static final Coin NEW_LOCKING_CAP = unionBridgeMainNetConstants.getInitialLockingCap()
        .multiply(BigInteger.valueOf(
            LOCKING_CAP_INCREMENTS_MULTIPLIER));

    private static final BigInteger ONE_ETH = BigInteger.TEN.pow(
        18); // 1 ETH = 1000000000000000000 wei
    private static final Coin AMOUNT_TO_REQUEST = new co.rsk.core.Coin(ONE_ETH);
    private static final Coin AMOUNT_TO_RELEASE = new co.rsk.core.Coin(ONE_ETH);

    private Transaction rskTx;

    @BeforeAll
    void setup() {
        Repository repository = BridgeSupportTestUtil.createRepository();
        repository.addBalance(BRIDGE_ADDR,
            co.rsk.core.Coin.fromBitcoin(bridgeMainNetConstants.getMaxRbtc()));
        StorageAccessor storageAccessor = new BridgeStorageAccessorImpl(repository);

        SignatureCache signatureCache = mock(SignatureCache.class);
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
        BridgeSupport bridgeSupport = bridgeSupportBuilder
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
}
