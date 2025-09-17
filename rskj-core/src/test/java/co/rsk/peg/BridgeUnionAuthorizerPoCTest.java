package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.union.UnionBridgeSupport;
import co.rsk.peg.union.UnionResponseCode;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeBuilder;
import co.rsk.test.builders.BridgeSupportBuilder;
import java.math.BigInteger;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PoC tests for Bridge union authorizer wrappers on BridgeMethods.
 */
class BridgeUnionAuthorizerPoCTest {

    private static final ActivationConfig allActivations = ActivationConfigsForTest.all();
    private static final Constants mainNetConstants = Constants.mainnet();

    private static final UnionBridgeConstants unionBridgeMainNetConstants = mainNetConstants.getBridgeConstants()
        .getUnionBridgeConstants();
    private static final co.rsk.core.Coin initialLockingCap = unionBridgeMainNetConstants.getInitialLockingCap();

    private static final co.rsk.core.Coin newLockingCap = initialLockingCap.multiply(
        BigInteger.valueOf(unionBridgeMainNetConstants.getLockingCapIncrementsMultiplier()));

    private UnionBridgeSupport unionBridgeSupport;
    private Repository repository;
    private BridgeEventLogger eventLogger;
    private BridgeSupport bridgeSupport;
    private Bridge bridge;
    private Transaction rskTx;
    private BridgeBuilder bridgeBuilder;
    private NetworkParameters networkParameters;

    @BeforeEach
    void setup() {
        networkParameters = BridgeMainNetConstants.getInstance().getBtcParams();
        bridgeBuilder = new BridgeBuilder();
        unionBridgeSupport = mock(UnionBridgeSupport.class);

        eventLogger = mock(BridgeEventLogger.class);
        repository = mock(Repository.class);
        bridgeSupport = BridgeSupportBuilder.builder()
            .withEventLogger(eventLogger)
            .withRepository(repository)
            .withUnionBridgeSupport(unionBridgeSupport).build();

        rskTx = mock(Transaction.class);

        bridge = bridgeBuilder
            .activationConfig(allActivations)
            .constants(mainNetConstants)
            .bridgeSupport(bridgeSupport)
            .transaction(rskTx)
            .build();
    }

    @Test
    void test () throws VMException {
        // Arrange
        UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;
        when(bridgeSupport.increaseUnionBridgeLockingCap(any(), any())).thenReturn(expectedResponseCode);
        when(bridgeSupport.isAuthorizedToChangeUnionLockingCap(any())).thenReturn(true);

        byte[] data = Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.encode(newLockingCap.asBigInteger());

        // Act
        byte[] result = bridge.execute(data);

        // Assert
        BigInteger decodedResult = (BigInteger) Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.decodeResult(
            result)[0];
        int actualUnionResponseCode = decodedResult.intValue();
        assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);
    }

    @Test
    void increaseUnionBridgeLockingCap_unauthorized_throws() {
        // Arrange
        when(bridgeSupport.increaseUnionBridgeLockingCap(any(), any())).thenReturn(co.rsk.peg.union.UnionResponseCode.UNAUTHORIZED_CALLER);
        when(bridgeSupport.isAuthorizedToChangeUnionLockingCap(any())).thenReturn(false);

        byte[] data = Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.encode(newLockingCap.asBigInteger());

        // Act
        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void increaseUnionBridgeLockingCap_authorized_callsExecutor() throws VMException {
        // Arrange
        UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;
        when(bridgeSupport.increaseUnionBridgeLockingCap(any(), any())).thenReturn(expectedResponseCode);
        when(bridgeSupport.isAuthorizedToChangeUnionLockingCap(any())).thenReturn(true);

        byte[] data = Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.encode(newLockingCap.asBigInteger());

        // Act
        byte[] result = bridge.execute(data);

        // Assert
        BigInteger decodedResult = (BigInteger) Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.decodeResult(
            result)[0];
        int actualUnionResponseCode = decodedResult.intValue();
        assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);
    }

    @Test
    void setUnionBridgeTransferPermissions_unauthorized_throws() {
        // Arrange
        when(bridgeSupport.setUnionBridgeTransferPermissions(any(), anyBoolean(), anyBoolean())).thenReturn(co.rsk.peg.union.UnionResponseCode.UNAUTHORIZED_CALLER);
        when(bridgeSupport.isAuthorizedToChangeUnionLockingCap(any())).thenReturn(false);

        CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
        byte[] data = function.encode(true, false);

        // Act
        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void setUnionBridgeTransferPermissions_authorized_callsExecutor() throws VMException {
        // Arrange
        UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;
        when(bridgeSupport.setUnionBridgeTransferPermissions(any(), anyBoolean(), anyBoolean())).thenReturn(
            expectedResponseCode);
        when(bridgeSupport.isAuthorizedToChangeUnionTransferPermissions(any())).thenReturn(true);

        CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
        byte[] data = function.encode(true, false);

        // Act
        byte[] result = bridge.execute(data);

        // Assert
        BigInteger decodedResult = (BigInteger) Bridge.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.decodeResult(
            result)[0];
        int actualUnionResponseCode = decodedResult.intValue();
        assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);
        verify(unionBridgeSupport, times(1)).setTransferPermissions(any(Transaction.class), eq(true), eq(false));
    }
}
