package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.flyover.FlyoverTxResponseCodes;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.*;

class BridgeTest {

    private TestSystemProperties config = new TestSystemProperties();
    private Constants constants;
    private ActivationConfig activationConfig;
    private SignatureCache signatureCache;

    @BeforeEach
    void resetConfigToRegTest() {
        config = spy(new TestSystemProperties());
        constants = Constants.regtest();
        when(config.getNetworkConstants()).thenReturn(constants);
        activationConfig = spy(ActivationConfigsForTest.genesis());
        when(config.getActivationConfig()).thenReturn(activationConfig);
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    }

    @Test
    void getActivePowpegRedeemScript_before_RSKIP293_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP293), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activationConfig);

        byte[] data = BridgeMethods.GET_ACTIVE_POWPEG_REDEEM_SCRIPT.getFunction().encode(new Object[]{});

        assertNull(bridge.execute(data));
    }

    @Test
    void getActivePowpegRedeemScript_after_RSKIP293_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP293), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActivePowpegRedeemScript()).thenReturn(
                Optional.of(BridgeRegTestConstants.getInstance().getGenesisFederation().getRedeemScript())
        );

        Bridge bridge = getBridgeInstance(bridgeSupportMock, activationConfig);

        byte[] data = BridgeMethods.GET_ACTIVE_POWPEG_REDEEM_SCRIPT.getFunction().encode(new Object[]{});
        byte[] result = (byte[]) BridgeMethods.GET_ACTIVE_POWPEG_REDEEM_SCRIPT.getFunction().decodeResult(bridge.execute(data))[0];

        assertArrayEquals(constants.bridgeConstants.getGenesisFederation().getRedeemScript().getProgram(), result);
    }

    @Test
    void getLockingCap_before_RSKIP134_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.GET_LOCKING_CAP.getFunction().encode(new Object[]{});

        assertNull(bridge.execute(data));
    }

    @Test
    void getLockingCap_after_RSKIP134_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.getLockingCap()).thenReturn(Coin.COIN);

        byte[] data = Bridge.GET_LOCKING_CAP.encode(new Object[]{});
        byte[] result = bridge.execute(data);
        assertEquals(Coin.COIN.getValue(), ((BigInteger) Bridge.GET_LOCKING_CAP.decodeResult(result)[0]).longValue());
        // Also test the method itself
        assertEquals(Coin.COIN.getValue(), bridge.getLockingCap(new Object[]{}));
    }

    @Test
    void increaseLockingCap_before_RSKIP134_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.INCREASE_LOCKING_CAP.getFunction().encode(new Object[]{});

        assertNull(bridge.execute(data));
    }

    @Test
    void increaseLockingCap_after_RSKIP134_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.increaseLockingCap(any(), any())).thenReturn(true);

        byte[] data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{1});
        byte[] result = bridge.execute(data);
        assertTrue((boolean) Bridge.INCREASE_LOCKING_CAP.decodeResult(result)[0]);
        // Also test the method itself
        assertEquals(true, bridge.increaseLockingCap(new Object[]{BigInteger.valueOf(1)}));

        data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{21_000_000});
        result = bridge.execute(data);
        assertTrue((boolean) Bridge.INCREASE_LOCKING_CAP.decodeResult(result)[0]);
        // Also test the method itself
        assertEquals(true, bridge.increaseLockingCap(new Object[]{BigInteger.valueOf(21_000_000)}));
    }

    @Test
    void increaseLockingCap_invalidParameter() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP134), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Uses the proper signature but with no argument
        // The solidity decoder in the Bridge will convert the undefined argument as 0, but the initial validation in the method will reject said value
        byte[] data = Bridge.INCREASE_LOCKING_CAP.encodeSignature();
        byte[] result = bridge.execute(data);
        assertNull(result);

        // Uses the proper signature but appends invalid data type
        // This will be rejected by the solidity decoder in the Bridge directly
        data = ByteUtil.merge(Bridge.INCREASE_LOCKING_CAP.encodeSignature(), Hex.decode("ab"));
        result = bridge.execute(data);
        assertNull(result);

        // Uses the proper signature and data type, but with an invalid value
        // This will be rejected by the initial validation in the method
        data = Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{-1});
        result = bridge.execute(data);
        assertNull(result);

        // Uses the proper signature and data type, but with a value that exceeds the long max value
        data = ByteUtil.merge(Bridge.INCREASE_LOCKING_CAP.encodeSignature(), Hex.decode("0000000000000000000000000000000000000000000000080000000000000000"));
        result = bridge.execute(data);
        assertNull(result);
    }

    @Test
    void registerBtcCoinbaseTransaction_before_RSKIP143_activation() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        Integer zero = new Integer(0);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encode(new Object[]{value, zero, value, zero, zero});

        assertNull(bridge.execute(data));
    }

    @Test
    void registerBtcCoinbaseTransaction_after_RSKIP143_activation() throws VMException {
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
    void registerBtcCoinbaseTransaction_after_RSKIP143_activation_null_data() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock, activations);

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encodeSignature();
        byte[] result = bridge.execute(data);
        assertNull(result);

        data = ByteUtil.merge(Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encodeSignature(), Hex.decode("ab"));
        result = bridge.execute(data);
        assertNull(result);

        data = ByteUtil.merge(Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encodeSignature(), Hex.decode("0000000000000000000000000000000000000000000000080000000000000000"));
        result = bridge.execute(data);
        assertNull(result);
    }

    @Test
    void registerBtcTransaction_beforeRskip199_rejectsExternalCalls()
        throws VMException, IOException, BlockStoreException {

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP199), anyLong());

        Federation activeFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(activeFederation);

        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getSender(any(SignatureCache.class))).thenReturn(new RskAddress("0000000000000000000000000000000000000001"));

        Bridge bridge = getBridgeInstance(rskTx, bridgeSupportMock, activations, signatureCache);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        int zero = 0;
        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new Object[]{ value, zero, value });

        try {
            bridge.execute(data);
            fail();
        } catch (VMException e) {
            assertEquals("Exception executing bridge: Sender is not part of the active or retiring federations, so he is not enabled to call the function 'registerBtcTransaction'", e.getMessage());
        }

        verify(bridgeSupportMock, never()).registerBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class)
        );
    }

    @Test
    void registerBtcTransaction_beforeRskip199_acceptsCallFromFederationMember()
        throws VMException, IOException, BlockStoreException {

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP199), anyLong());

        BtcECKey fed1Key = new BtcECKey();
        RskAddress fed1Address = new RskAddress(ECKey.fromPublicOnly(fed1Key.getPubKey()).getAddress());
        List<BtcECKey> federationKeys = Arrays.asList(fed1Key, new BtcECKey(), new BtcECKey());
        federationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation activeFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembersWithKeys(federationKeys),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(activeFederation);

        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getSender(any(SignatureCache.class))).thenReturn(fed1Address);

        Bridge bridge = getBridgeInstance(rskTx, bridgeSupportMock, activations, signatureCache);

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
    void registerBtcTransaction_afterRskip199_acceptsExternalCalls()
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
    void getActiveFederationCreationBlockHeight_before_RSKIP186_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP186), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT.getFunction().encode(new Object[]{});

        assertNull(bridge.execute(data));
    }

    @Test
    void getActiveFederationCreationBlockHeight_after_RSKIP186_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP186), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        // Don't really care about the internal logic, just checking if the method is active
        when(bridgeSupportMock.getActiveFederationCreationBlockHeight()).thenReturn(1L);

        CallTransaction.Function function = BridgeMethods.GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT.getFunction();
        byte[] data = function.encode(new Object[]{ });
        byte[] result = bridge.execute(data);
        assertEquals(1L, ((BigInteger)function.decodeResult(result)[0]).longValue());
        // Also test the method itself
        assertEquals(1L, bridge.getActiveFederationCreationBlockHeight(new Object[]{ }));
    }

    @Test
    void registerFlyoverBtcTransaction_before_RSKIP176_activation() throws VMException {
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
        assertNull(bridge.execute(data));
    }

    @Test
    void registerFlyoverBtcTransaction_after_RSKIP176_activation_p2sh_refund_address_before_RSKIP284_activation_fails()
        throws VMException, IOException, BlockStoreException {
        NetworkParameters networkParameters = constants.getBridgeConstants().getBtcParams();
        doReturn(true).when(activationConfig).isActive(eq(RSKIP176), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP284), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        when(bridgeSupportMock.registerFlyoverBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class),
            any(Keccak256.class),
            any(Address.class),
            any(RskAddress.class),
            any(Address.class),
            anyBoolean()
        )).thenReturn(BigInteger.valueOf(2));

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();

        Address refundBtcAddress = Address.fromBase58(networkParameters, "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP");
        byte[] refundBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(
            activationConfig.forBlock(anyLong()),
            refundBtcAddress
        );

        BtcECKey btcECKeyLp = new BtcECKey();
        Address lpBtcAddress = btcECKeyLp.toAddress(networkParameters);
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(
            activationConfig.forBlock(anyLong()),
            lpBtcAddress
        );

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
        assertEquals(BigInteger.valueOf(-900), Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.decodeResult(result)[0]);
        verify(bridgeSupportMock, times(0)).registerFlyoverBtcTransaction(
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
    void registerFlyoverBtcTransaction_after_RSKIP176_activation_p2sh_refund_address_after_RSKIP284_activation_ok()
        throws VMException, IOException, BlockStoreException {
        NetworkParameters networkParameters = constants.getBridgeConstants().getBtcParams();
        doReturn(true).when(activationConfig).isActive(eq(RSKIP176), anyLong());
        doReturn(true).when(activationConfig).isActive(eq(RSKIP284), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        when(bridgeSupportMock.registerFlyoverBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class),
            any(Keccak256.class),
            any(Address.class),
            any(RskAddress.class),
            any(Address.class),
            anyBoolean()
        )).thenReturn(BigInteger.valueOf(2));

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();

        Address refundBtcAddress = Address.fromBase58(networkParameters, "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP");
        byte[] refundBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(
            activationConfig.forBlock(anyLong()),
            refundBtcAddress
        );

        BtcECKey btcECKeyLp = new BtcECKey();
        Address lpBtcAddress = btcECKeyLp.toAddress(networkParameters);
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(
            activationConfig.forBlock(anyLong()),
            lpBtcAddress
        );

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
        assertEquals(BigInteger.valueOf(2), Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.decodeResult(result)[0]);
        verify(bridgeSupportMock, times(1)).registerFlyoverBtcTransaction(
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
    void registerFlyoverBtcTransaction_after_RSKIP176_activation_generic_error()
        throws VMException, IOException, BlockStoreException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP176), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        when(bridgeSupportMock.registerFlyoverBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class),
            any(Keccak256.class),
            any(Address.class),
            any(RskAddress.class),
            any(Address.class),
            anyBoolean()
        )).thenReturn(BigInteger.valueOf(FlyoverTxResponseCodes.GENERIC_ERROR.value()));

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

        assertEquals(FlyoverTxResponseCodes.GENERIC_ERROR.value(),
            ((BigInteger)Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.decodeResult(result)[0]).longValue());
    }

    @Test
    void registerFlyoverBtcTransaction_after_RSKIP176_null_parameter() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP176), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();

        byte[] data = Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.encodeSignature();
        byte[] result = bridge.execute(data);
        assertNull(result);

        data = ByteUtil.merge(Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.encodeSignature(), value);
        result = bridge.execute(data);
        assertNull(result);
    }

    @Test
    void receiveHeader_before_RSKIP200() throws VMException {
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
        assertNull(bridge.execute(data));
        verify(bridge, never()).receiveHeader(any(Object[].class));
    }

    @Test
    void receiveHeader_empty_parameter() throws VMException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP200), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = spy(getBridgeInstance(bridgeSupportMock, activations));

        byte[] data = Bridge.RECEIVE_HEADER.encode(new Object[]{});

        assertNull(bridge.execute(data));
        verify(bridge, never()).receiveHeader(any(Object[].class));
        verifyNoInteractions(bridgeSupportMock);
    }

    @Test
    void receiveHeader_after_RSKIP200_Ok() throws VMException {
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
        verify(bridge, times(1)).receiveHeader(eq(parameters)); // NOSONAR: eq is needed
        assertEquals(BigInteger.valueOf(0), Bridge.RECEIVE_HEADER.decodeResult(result)[0]);
    }

    @Test
    void receiveHeader_bridgeSupport_Exception() throws IOException, BlockStoreException {
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

        Assertions.assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void receiveHeaders_after_RSKIP200_notFederation() {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP200), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getRetiringFederation()).thenReturn(null);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(BridgeRegTestConstants.getInstance().getGenesisFederation());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(new ECKey().getAddress()));  //acces for anyone

        Bridge bridge = getBridgeInstance(txMock, bridgeSupportMock, activations, signatureCache);

        try {
            bridge.execute(Bridge.RECEIVE_HEADERS.encode());
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("Sender is not part of the active or retiring federation"));
        }
    }

    @Test
    void receiveHeaders_after_RSKIP200_header_wrong_size() throws VMException, IOException {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP200), anyLong());
        // It is used to check the size of the header
        doReturn(true).when(activations).isActive(eq(RSKIP124), anyLong());

        Bridge bridge = getBridgeInstance(mock(BridgeSupport.class), activations);

        Object[] parameters = new Object[]{Sha256Hash.ZERO_HASH.getBytes()};
        byte[] data = Bridge.RECEIVE_HEADER.encode(parameters);

        byte[] result = bridge.execute(data);
        assertEquals(BigInteger.valueOf(-20), Bridge.RECEIVE_HEADER.decodeResult(result)[0]);
    }

    @Test
    void getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls_afterRskip220() {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP220), anyLong());

        Bridge bridge = getBridgeInstance(mock(BridgeSupport.class), activations);

        assertFalse(bridge.getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls(new Object[0]));
    }

    @Test
    void getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls_beforeRskip220() {
        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(false).when(activations).isActive(eq(RSKIP220), anyLong());

        Bridge bridge = getBridgeInstance(mock(BridgeSupport.class), activations);

        assertTrue(bridge.getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls(new Object[0]));
    }

    @Test
    void activeAndRetiringFederationOnly_activeFederationIsNotFromFederateMember_retiringFederationIsNull_throwsVMException() throws Exception {
        // Given
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
                null,
                null
        );

        int senderPK = 999; // Sender PK does not belong to Member PKs
        Integer[] memberPKs = new Integer[]{100, 200, 300, 400, 500, 600};
        Federation activeFederation = FederationTestUtils.getFederation(memberPKs);

        Bridge bridge = getBridgeInstance(activeFederation, null, senderPK);

        // Then
        try {
            executor.execute(bridge, null);
            fail("VMException should be thrown!");
        } catch (VMException vme) {
            assertEquals(
                    "Sender is not part of the active or retiring federations, so he is not enabled to call the function 'null'",
                    vme.getMessage()
            );
        }
    }

    @Test
    void activeAndRetiringFederationOnly_activeFederationIsNotFromFederateMember_retiringFederationIsNotNull_retiringFederationIsNotFromFederateMember_throwsVMException() throws Exception {
        // Given
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
                null,
                null
        );

        int senderPK = 999; // Sender PK does not belong to Member PKs of active nor retiring fed
        Integer[] activeMemberPKs = new Integer[]{100, 200, 300, 400, 500, 600};
        Integer[] retiringMemberPKs = new Integer[]{101, 202, 303, 404, 505, 606};

        Federation activeFederation = FederationTestUtils.getFederation(activeMemberPKs);
        Federation retiringFederation = FederationTestUtils.getFederation(retiringMemberPKs);

        Bridge bridge = getBridgeInstance(activeFederation, retiringFederation, senderPK);

        // Then
        try {
            executor.execute(bridge, null);
            fail("VMException should be thrown!");
        } catch (VMException vme) {
            assertEquals(
                    "Sender is not part of the active or retiring federations, so he is not enabled to call the function 'null'",
                    vme.getMessage()
            );
        }
    }

    @Test
    void activeAndRetiringFederationOnly_activeFederationIsFromFederateMember_OK() throws Exception {
        // Given
        BridgeMethods.BridgeMethodExecutor decorate = mock(
                BridgeMethods.BridgeMethodExecutor.class
        );
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
                decorate,
                null
        );

        int senderPK = 101; // Sender PK belongs to active federation member PKs
        Integer[] memberPKs = new Integer[]{100, 200, 300, 400, 500, 600};

        Federation activeFederation = FederationTestUtils.getFederation(memberPKs);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(activeFederation, null, senderPK, bridgeSupportMock);

        // When
        executor.execute(bridge, null);

        // Then
        verify(bridgeSupportMock, times(1)).getActiveFederation();
        verify(bridgeSupportMock, times(1)).getRetiringFederation();
        verify(decorate, times(1)).execute(any(), any());
    }

    @Test
    void activeAndRetiringFederationOnly_activeFederationIsNotFromFederateMember_retiringFederationIsNotNull_retiringFederationIsFromFederateMember_OK() throws Exception {
        // Given
        BridgeMethods.BridgeMethodExecutor decorate = mock(
                BridgeMethods.BridgeMethodExecutor.class
        );
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
                decorate,
                null
        );

        int senderPK = 405; // Sender PK belongs to retiring federation member PKs
        Integer[] activeMemberPKs = new Integer[]{100, 200, 300, 400, 500, 600};
        Integer[] retiringMemberPKs = new Integer[]{101, 202, 303, 404, 505, 606};

        Federation activeFederation = FederationTestUtils.getFederation(activeMemberPKs);
        Federation retiringFederation = FederationTestUtils.getFederation(retiringMemberPKs);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(activeFederation, retiringFederation, senderPK, bridgeSupportMock);

        // When
        executor.execute(bridge, null);

        // Then
        verify(bridgeSupportMock, times(1)).getActiveFederation();
        verify(bridgeSupportMock, times(1)).getRetiringFederation();
        verify(decorate, times(1)).execute(any(), any());
    }

    @Test
    void getNextPegoutCreationBlockNumber_before_RSKIP271_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP271), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.GET_NEXT_PEGOUT_CREATION_BLOCK_NUMBER.getFunction().encode(new Object[]{});

        assertNull(bridge.execute(data));
    }

    @Test
    void getNextPegoutCreationBlockNumber_after_RSKIP271_activation() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP271), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        when(bridgeSupportMock.getNextPegoutCreationBlockNumber()).thenReturn(1L);

        CallTransaction.Function function = BridgeMethods.GET_NEXT_PEGOUT_CREATION_BLOCK_NUMBER.getFunction();
        byte[] data = function.encode(new Object[]{ });
        byte[] result = bridge.execute(data);

        assertEquals(1L, ((BigInteger)function.decodeResult(result)[0]).longValue());
        // Also test the method itself
        assertEquals(1L, bridge.getNextPegoutCreationBlockNumber(new Object[]{ }));
    }

    @Test
    void getQueuedPegoutsCount_before_RSKIP271_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP271), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.GET_QUEUED_PEGOUTS_COUNT.getFunction().encode(new Object[]{});

        assertNull(bridge.execute(data));
    }

    @Test
    void getQueuedPegoutsCount_after_RSKIP271_activation() throws VMException, IOException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP271), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        when(bridgeSupportMock.getQueuedPegoutsCount()).thenReturn(1);

        CallTransaction.Function function = BridgeMethods.GET_QUEUED_PEGOUTS_COUNT.getFunction();
        byte[] data = function.encode(new Object[]{ });
        byte[] result = bridge.execute(data);

        assertEquals(1, ((BigInteger)function.decodeResult(result)[0]).intValue());
        // Also test the method itself
        assertEquals(1, bridge.getQueuedPegoutsCount(new Object[]{ }));
    }

    @Test
    void getEstimatedFeesForNextPegOutEvent_before_RSKIP271_activation() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP271), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        byte[] data = BridgeMethods.GET_ESTIMATED_FEES_FOR_NEXT_PEGOUT_EVENT.getFunction().encode(new Object[]{});

        assertNull(bridge.execute(data));
    }

    @Test
    void getEstimatedFeesForNextPegOutEvent_after_RSKIP271_activation() throws VMException, IOException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP271), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = getBridgeInstance(bridgeSupportMock);

        when(bridgeSupportMock.getEstimatedFeesForNextPegOutEvent()).thenReturn(Coin.SATOSHI);

        CallTransaction.Function function = BridgeMethods.GET_ESTIMATED_FEES_FOR_NEXT_PEGOUT_EVENT.getFunction();
        byte[] data = function.encode(new Object[]{ });
        byte[] result = bridge.execute(data);

        assertEquals(Coin.SATOSHI.value, ((BigInteger)function.decodeResult(result)[0]).intValue());
        // Also test the method itself
        assertEquals(Coin.SATOSHI.value, bridge.getEstimatedFeesForNextPegOutEvent(new Object[]{ }));
    }

    private Bridge getBridgeInstance(Federation activeFederation, Federation retiringFederation, int senderPK) {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(retiringFederation).when(bridgeSupportMock).getRetiringFederation();

        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(new RskAddress(key.getAddress())).when(rskTxMock).getSender(any(SignatureCache.class));

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        return getBridgeInstance(rskTxMock, bridgeSupportMock, activations, signatureCache);
    }

    private Bridge getBridgeInstance(Federation activeFederation, Federation retiringFederation, int senderPK, BridgeSupport bridgeSupportMock) {
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(retiringFederation).when(bridgeSupportMock).getRetiringFederation();

        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(new RskAddress(key.getAddress())).when(rskTxMock).getSender(any(SignatureCache.class));

        ActivationConfig activations = spy(ActivationConfigsForTest.genesis());
        doReturn(true).when(activations).isActive(eq(RSKIP143), anyLong());

        return getBridgeInstance(rskTxMock, bridgeSupportMock, activations, signatureCache);
    }

    /**
     * Gets a bridge instance mocking the transaction and BridgeSupportFactory
     *
     * @param txMock                Provide the transaction to be used
     * @param bridgeSupportInstance Provide the bridgeSupport to be used
     * @param activationConfig      Provide the activationConfig to be used
     * @return Bridge instance
     */
    private Bridge getBridgeInstance(Transaction txMock, BridgeSupport bridgeSupportInstance, ActivationConfig activationConfig, SignatureCache signatureCache) {
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportInstance);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactoryMock, signatureCache);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);
        return bridge;
    }

    private Bridge getBridgeInstance(BridgeSupport bridgeSupportInstance, ActivationConfig activationConfig) {
        return getBridgeInstance(mock(Transaction.class), bridgeSupportInstance, activationConfig, signatureCache);
    }

    @Deprecated
    private Bridge getBridgeInstance(BridgeSupport bridgeSupportInstance) {
        return getBridgeInstance(bridgeSupportInstance, activationConfig);
    }

    private Block getGenesisBlock() {
        return new BlockGenerator().getGenesisBlock();
    }
}
