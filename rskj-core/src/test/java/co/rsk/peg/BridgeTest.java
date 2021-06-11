package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import co.rsk.crypto.Keccak256;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;
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
    public void getLockingCap_before_RSKIP134_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.GET_LOCKING_CAP.getFunction().encode(new Object[]{});

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void getLockingCap_after_RSKIP134_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.getLockingCap()).thenReturn(Coin.COIN);

        byte[] data = Bridge.GET_LOCKING_CAP.encode(new Object[]{});
        byte[] result = bridge.execute(data);
        Assert.assertEquals(Coin.COIN.getValue(), ((BigInteger) Bridge.GET_LOCKING_CAP.decodeResult(result)[0]).longValue());
        // Also test the method itself
        Assert.assertEquals(Coin.COIN.getValue(), bridge.getLockingCap(new Object[]{}));
    }

    @Test
    public void increaseLockingCap_before_RSKIP134_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.INCREASE_LOCKING_CAP.getFunction().encode(new Object[]{});

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void increaseLockingCap_after_RSKIP134_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.increaseLockingCap(any(), any())).thenReturn(true);

        byte[] data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{1});
        byte[] result = bridge.execute(data);
        Assert.assertTrue((boolean) Bridge.INCREASE_LOCKING_CAP.decodeResult(result)[0]);
        // Also test the method itself
        Assert.assertEquals(true, bridge.increaseLockingCap(new Object[]{BigInteger.valueOf(1)}));

        data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{21_000_000});
        result = bridge.execute(data);
        Assert.assertTrue((boolean) Bridge.INCREASE_LOCKING_CAP.decodeResult(result)[0]);
        // Also test the method itself
        Assert.assertEquals(true, bridge.increaseLockingCap(new Object[]{BigInteger.valueOf(21_000_000)}));
    }

    @Test
    public void increaseLockingCap_invalidParameter() throws VMException {
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
        data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{-1});
        result = bridge.execute(data);
        Assert.assertNull(result);

        // Uses the proper signature and data type, but with a value that exceeds the long max value
        data = ByteUtil.merge(Bridge.INCREASE_LOCKING_CAP.encodeSignature(), Hex.decode("0000000000000000000000000000000000000000000000080000000000000000"));
        result = bridge.execute(data);
        Assert.assertNull(result);
    }

    @Test
    public void registerBtcCoinbaseTransaction_before_RSKIP143_activation() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        Integer zero = new Integer(0);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encode(new Object[]{value, zero, value, zero, zero});

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcCoinbaseTransaction_after_RSKIP143_activation() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        Integer zero = new Integer(0);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encode(new Object[]{value, zero, value, zero, zero});

        bridge.execute(data);
        verify(bridgeSupportMock, times(1)).registerBtcCoinbaseTransaction(value, Sha256Hash.wrap(value), value, Sha256Hash.wrap(value), value);
    }

    @Test
    public void registerBtcCoinbaseTransaction_after_RSKIP143_activation_null_data() throws VMException {
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

    @Test
    public void registerBtcTransaction_beforeRskip199_rejectsExternalCalls()
        throws VMException, IOException, BlockStoreException {

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP199), anyLong());

        Federation activeFederation = new Federation(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(activeFederation);

        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getSender()).thenReturn(new RskAddress("0000000000000000000000000000000000000001"));

        Bridge bridge = getBridgeInstance(rskTx, bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        int zero = 0;
        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new Object[]{ value, zero, value });

        try {
            bridge.execute(data);
            Assert.fail();
        } catch (VMException e) {
            Assert.assertEquals("Exception executing bridge: Sender is not part of the active or retiring federations, so he is not enabled to call the function 'registerBtcTransaction'", e.getMessage());
        }

        verify(bridgeSupportMock, never()).registerBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class)
        );
    }

    @Test
    public void registerBtcTransaction_beforeRskip199_acceptsCallFromFederationMember()
        throws VMException, IOException, BlockStoreException {

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP199), anyLong());

        BtcECKey fed1Key = new BtcECKey();
        RskAddress fed1Address = new RskAddress(ECKey.fromPublicOnly(fed1Key.getPubKey()).getAddress());
        List<BtcECKey> federationKeys = Arrays.asList(fed1Key, new BtcECKey(), new BtcECKey());
        federationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation activeFederation = new Federation(
            FederationTestUtils.getFederationMembersWithKeys(federationKeys),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(activeFederation);

        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getSender()).thenReturn(fed1Address);

        Bridge bridge = getBridgeInstance(rskTx, bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        int zero = 0;
        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new Object[]{ value, zero, value });

        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).registerBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class)
        );
    }

    @Test
    public void registerBtcTransaction_afterRskip199_acceptsExternalCalls()
        throws VMException, IOException, BlockStoreException {

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP199), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        int zero = 0;
        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new Object[]{ value, zero, value });

        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).registerBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class)
        );
    }

    @Test
    public void getActiveFederationCreationBlockHeight_before_RSKIP186_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP186), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT.getFunction().encode(new Object[]{});

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void getActiveFederationCreationBlockHeight_after_RSKIP186_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP186), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.getActiveFederationCreationBlockHeight()).thenReturn(1L);

        CallTransaction.Function function = BridgeMethods.GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT.getFunction();
        byte[] data = function.encode(new Object[]{ });
        byte[] result = bridge.execute(data);
        Assert.assertEquals(1L, ((BigInteger)function.decodeResult(result)[0]).longValue());
        // Also test the method itself
        Assert.assertEquals(1L, bridge.getActiveFederationCreationBlockHeight(new Object[]{ }));
    }

    @Test
    public void registerFastBridgeBtcTransaction_before_RSKIP176_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP176), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        byte[] pubKeyHash = new BtcECKey().getPubKeyHash();

        byte[] data = BridgeMethods.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.getFunction().encode(
            value,
            1,
            value,
            value,
            pubKeyHash,
            "2e12a7e43926ccd228a2587896e53c3d1a51dacb",
            pubKeyHash,
            true
        );

        //Assert
        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerFastBridgeBtcTransaction_after_RSKIP176_activation()
        throws VMException, IOException, BlockStoreException {
        NetworkParameters networkParameters = constants.getBridgeConstants().getBtcParams();
        doReturn(true).when(activationConfig).isActive(eq(RSKIP176), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        when(bridgeSupportMock.registerFastBridgeBtcTransaction(
                any(Transaction.class),
                any(byte[].class),
                anyInt(),
                any(byte[].class),
                any(Keccak256.class),
                any(Address.class),
                any(RskAddress.class),
                any(Address.class),
                anyBoolean()
        )).thenReturn(Long.valueOf(2));

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();

        BtcECKey btcECKeyRefund = new BtcECKey();
        Address refundBtcAddress = btcECKeyRefund.toAddress(networkParameters);
        byte[] refundBtcAddressBytes = addressToByteArray(refundBtcAddress);

        BtcECKey btcECKeyLp = new BtcECKey();
        Address lpBtcAddress = btcECKeyLp.toAddress(networkParameters);
        byte[] lpBtcAddressBytes = addressToByteArray(lpBtcAddress);

        ECKey ecKey = new ECKey();
        RskAddress rskAddress = new RskAddress(ecKey.getAddress());

        byte[] data = Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.encode(
            value,
            1,
            value,
            value,
            refundBtcAddressBytes,
            rskAddress.toHexString(),
            lpBtcAddressBytes,
            true
        );

        byte[] result = bridge.execute(data);

        //Assert
        Assert.assertEquals(BigInteger.valueOf(2), Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.decodeResult(result)[0]);
        verify(bridgeSupportMock, times(1)).registerFastBridgeBtcTransaction(
                any(Transaction.class),
                eq(value),
                eq(1),
                eq(value),
                eq(new Keccak256(value)),
                eq(refundBtcAddress),
                eq(rskAddress),
                eq(lpBtcAddress),
                eq(true)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_after_RSKIP176_activation_generic_error()
        throws VMException, IOException, BlockStoreException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP176), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        when(bridgeSupportMock.registerFastBridgeBtcTransaction(
                any(Transaction.class),
                any(byte[].class),
                anyInt(),
                any(byte[].class),
                any(Keccak256.class),
                any(Address.class),
                any(RskAddress.class),
                any(Address.class),
                anyBoolean()
        )).thenReturn(BridgeSupport.FAST_BRIDGE_GENERIC_ERROR);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        BtcECKey btcECKeyRefund = new BtcECKey();
        byte[] pubKeyHashRefund = btcECKeyRefund.getPubKeyHash();
        BtcECKey btcECKeyLp = new BtcECKey();
        byte[] pubKeyHashLp = btcECKeyLp.getPubKeyHash();

        ECKey ecKey = new ECKey();
        RskAddress rskAddress = new RskAddress(ecKey.getAddress());
        byte[] data = Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.encode(
            value,
            1,
            value,
            value,
            pubKeyHashRefund,
            rskAddress.toHexString(),
            pubKeyHashLp,
            true
        );
        byte[] result = bridge.execute(data);
        
        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_GENERIC_ERROR, 
            ((BigInteger)Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.decodeResult(result)[0]).longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_after_RSKIP176_null_parameter() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP176), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();

        byte[] data = Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.encodeSignature();
        byte[] result = bridge.execute(data);
        Assert.assertNull(result);

        data = ByteUtil.merge(Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.encodeSignature(), value);
        result = bridge.execute(data);
        Assert.assertNull(result);
    }

    public void receiveHeader_before_RSKIP200() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP200), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = spy(getBridgeInstance(bridgeSupportMock, activations));

        NetworkParameters networkParameters = constants.bridgeConstants.getBtcParams();
        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
                networkParameters,
                1,
                PegTestUtils.createHash(1),
                PegTestUtils.createHash(1),
                1,
                Utils.encodeCompactBits(networkParameters.getMaxTarget()
                ), 1, new ArrayList<>()).cloneAsHeader();

        Object[] parameters = new Object[]{block.bitcoinSerialize()};
        byte[] data = Bridge.RECEIVE_HEADER.encode(parameters);

        bridge.execute(data);
        Assert.assertNull(bridge.execute(data));
        verify(bridge, never()).receiveHeader(any(Object[].class));
    }

    @Test
    public void receiveHeader_empty_parameter() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP200), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = spy(getBridgeInstance(bridgeSupportMock, activations));

        byte[] data = Bridge.RECEIVE_HEADER.encode(new Object[]{});

        Assert.assertNull(bridge.execute(data));
        verify(bridge, never()).receiveHeader(any(Object[].class));
        verifyZeroInteractions(bridgeSupportMock);
    }

    @Test
    public void receiveHeader_after_RSKIP200_Ok() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP200), anyLong());

        Bridge bridge = spy(getBridgeInstance(mock(BridgeSupport.class), activations));

        NetworkParameters networkParameters = constants.bridgeConstants.getBtcParams();
        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
                networkParameters,
                1,
                PegTestUtils.createHash(1),
                PegTestUtils.createHash(1),
                1,
                Utils.encodeCompactBits(networkParameters.getMaxTarget()
                ), 1, new ArrayList<>()).cloneAsHeader();

        Object[] parameters = new Object[]{block.bitcoinSerialize()};
        byte[] data = Bridge.RECEIVE_HEADER.encode(parameters);

        byte[] result = bridge.execute(data);
        verify(bridge, times(1)).receiveHeader(eq(parameters));
        Assert.assertEquals(BigInteger.valueOf(0), Bridge.RECEIVE_HEADER.decodeResult(result)[0]);
    }

    @Test(expected = VMException.class)
    public void receiveHeader_bridgeSupport_Exception() throws VMException, IOException, BlockStoreException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP200), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doThrow(new IOException()).when(bridgeSupportMock).receiveHeader(any());
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        NetworkParameters networkParameters = constants.bridgeConstants.getBtcParams();
        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
                networkParameters,
                1,
                PegTestUtils.createHash(1),
                PegTestUtils.createHash(1),
                1,
                Utils.encodeCompactBits(networkParameters.getMaxTarget()
                ), 1, new ArrayList<>()).cloneAsHeader();

        Object[] parameters = new Object[]{block.bitcoinSerialize()};
        byte[] data = Bridge.RECEIVE_HEADER.encode(parameters);

        bridge.execute(data);
    }

    @Test
    public void receiveHeaders_after_RSKIP200_notFederation() {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP200), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getRetiringFederation()).thenReturn(null);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(BridgeRegTestConstants.getInstance().getGenesisFederation());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getSender()).thenReturn(new RskAddress(new ECKey().getAddress()));  //acces for anyone

        Bridge bridge = getBridgeInstance(txMock, bridgeSupportMock, activations);

        try {
            bridge.execute(Bridge.RECEIVE_HEADERS.encode());
            Assert.fail();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getMessage().contains("Sender is not part of the active or retiring federation"));
        }
    }

    @Test
    public void receiveHeaders_after_RSKIP200_header_wrong_size() throws VMException, IOException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP200), anyLong());
        // It is used to check the size of the header
        doReturn(true).when(activations).isActive(eq(RSKIP124), anyLong());

        Bridge bridge = getBridgeInstance(mock(BridgeSupport.class), activations);

        Object[] parameters = new Object[]{Sha256Hash.ZERO_HASH.getBytes()};
        byte[] data = Bridge.RECEIVE_HEADER.encode(parameters);

        byte[] result = bridge.execute(data);
        Assert.assertEquals(BigInteger.valueOf(-20), Bridge.RECEIVE_HEADER.decodeResult(result)[0]);
    }

    @Test
    public void getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls_afterRskip220() {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP220), anyLong());

        Bridge bridge = getBridgeInstance(mock(BridgeSupport.class), activations);

        Assert.assertFalse(bridge.getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls(new Object[0]));
    }

    @Test
    public void getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls_beforeRskip220() {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP220), anyLong());

        Bridge bridge = getBridgeInstance(mock(BridgeSupport.class), activations);

        Assert.assertTrue(bridge.getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls(new Object[0]));
    }

    /**
     * Gets a bride instance mocking the transaction and BridgeSupportFactory
     * @param bridgeSupportInstance Provide the bridgeSupport to be used
     * @return
     */
    private Bridge getBridgeInstance(Transaction txMock, BridgeSupport bridgeSupportInstance, ActivationConfig activationConfig) {
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportInstance);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactoryMock);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);
        return bridge;
    }

    private Bridge getBridgeInstance(BridgeSupport bridgeSupportInstance, ActivationConfig activationConfig) {
        return getBridgeInstance(mock(Transaction.class), bridgeSupportInstance, activationConfig);
    }

    @Deprecated
    private Bridge getBridgeInstance(BridgeSupport bridgeSupportInstance) {
        return getBridgeInstance(bridgeSupportInstance, activationConfig);
    }

    private Block getGenesisBlock() {
        return new BlockGenerator().getGenesisBlock();
    }

    private byte[] addressToByteArray(Address address) {
        byte[] version = BigInteger.valueOf(address.getVersion()).toByteArray();

        byte[] hash = address.getHash160();
        byte[] addressBytes = new byte[version.length + hash.length];
        System.arraycopy(version, 0, addressBytes, 0, version.length);
        System.arraycopy(hash, 0, addressBytes, version.length, hash.length);

        return addressBytes;
    }
}
