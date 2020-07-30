package co.rsk.peg;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP134;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP143;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

public class BridgeTest {

    private TestSystemProperties config = new TestSystemProperties();
    private Constants constants;
    private ActivationConfig activationConfig;

    @Before
    public void resetConfigToRegTest() {
        config = spy(new TestSystemProperties());
        constants = Constants.regtest();
        when(config.getNetworkConstants()).thenReturn(constants);
        activationConfig = spy(ActivationConfigsForTest.genesis());
        when(config.getActivationConfig()).thenReturn(activationConfig);
    }

    @Test
    public void getLockingCap_before_RSKIP134_activation() {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.GET_LOCKING_CAP.getFunction().encode(new Object[]{});

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void getLockingCap_after_RSKIP134_activation() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.getLockingCap()).thenReturn(Coin.COIN);

        byte[] data = Bridge.GET_LOCKING_CAP.encode(new Object[]{ });
        byte[] result = bridge.execute(data);
        Assert.assertEquals(Coin.COIN.getValue(), ((BigInteger)Bridge.GET_LOCKING_CAP.decodeResult(result)[0]).longValue());
        // Also test the method itself
        Assert.assertEquals(Coin.COIN.getValue(), bridge.getLockingCap(new Object[]{ }));
    }

    @Test
    public void increaseLockingCap_before_RSKIP134_activation() {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.INCREASE_LOCKING_CAP.getFunction().encode(new Object[]{});

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void increaseLockingCap_after_RSKIP134_activation() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.increaseLockingCap(any(), any())).thenReturn(true);

        byte[] data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ 1 });
        byte[] result = bridge.execute(data);
        Assert.assertTrue((boolean)Bridge.INCREASE_LOCKING_CAP.decodeResult(result)[0]);
        // Also test the method itself
        Assert.assertEquals(true, bridge.increaseLockingCap(new Object[]{ BigInteger.valueOf(1) }));

        data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ 21_000_000 });
        result = bridge.execute(data);
        Assert.assertTrue((boolean)Bridge.INCREASE_LOCKING_CAP.decodeResult(result)[0]);
        // Also test the method itself
        Assert.assertEquals(true, bridge.increaseLockingCap(new Object[]{ BigInteger.valueOf(21_000_000) }));
    }

    @Test
    public void increaseLockingCap_invalidParameter() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Uses the proper signature but with no argument
        // The solidity decoder in the Bridge will convert the undefined argument as 0, but the initial validation in the method will reject said value
        byte[] data = Bridge.INCREASE_LOCKING_CAP.encodeSignature();
        byte[] result = bridge.execute(data);
        Assert.assertNull(result);

        // Uses the proper signature but appends invalid data type
        // This will be rejected by the solidity decoder in the Bridge directly
        data = ByteUtil.merge(Bridge.INCREASE_LOCKING_CAP.encodeSignature(), Hex.decode("ab"));
        result = bridge.execute(data);
        Assert.assertNull(result);

        // Uses the proper signature and data type, but with an invalid value
        // This will be rejected by the initial validation in the method
        data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ -1 });
        result = bridge.execute(data);
        Assert.assertNull(result);

        // Uses the proper signature and data type, but with a value that exceeds the long max value
        data = ByteUtil.merge(Bridge.INCREASE_LOCKING_CAP.encodeSignature(), Hex.decode("0000000000000000000000000000000000000000000000080000000000000000"));
        result = bridge.execute(data);
        Assert.assertNull(result);
    }

    @Test
    public void registerBtcCoinbaseTransaction_before_RSKIP143_activation() {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        Integer zero = new Integer(0);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encode(new Object[]{ value, zero, value, zero, zero });

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcCoinbaseTransaction_after_RSKIP143_activation() throws BlockStoreException, IOException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        Integer zero = new Integer(0);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encode(new Object[]{ value, zero, value, zero, zero });

        bridge.execute(data);
        verify(bridgeSupportMock, times(1)).registerBtcCoinbaseTransaction(value, Sha256Hash.wrap(value), value, Sha256Hash.wrap(value), value);
    }

    @Test
    public void registerBtcCoinbaseTransaction_after_RSKIP143_activation_null_data() {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encodeSignature();
        byte[] result = bridge.execute(data);
        Assert.assertNull(result);

        data = ByteUtil.merge(Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encodeSignature(), Hex.decode("ab"));
        result = bridge.execute(data);
        Assert.assertNull(result);

        data = ByteUtil.merge(Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encodeSignature(), Hex.decode("0000000000000000000000000000000000000000000000080000000000000000"));
        result = bridge.execute(data);
        Assert.assertNull(result);
    }

    /**
     * Gets a bride instance mocking the transaction and BridgeSupportFactory
     * @param bridgeSupportInstance Provide the bridgeSupport to be used
     * @return
     */
    private Bridge getBridgeInstance(BridgeSupport bridgeSupportInstance, ActivationConfig activationConfig) {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportInstance);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactoryMock);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        return bridge;
    }

    @Deprecated
    private Bridge getBridgeInstance(BridgeSupport bridgeSupportInstance) {
        return getBridgeInstance(bridgeSupportInstance, activationConfig);
    }

    private Block getGenesisBlock() {
        return new BlockGenerator().getGenesisBlock();
    }

}