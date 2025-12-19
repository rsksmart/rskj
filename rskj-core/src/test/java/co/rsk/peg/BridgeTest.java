package co.rsk.peg;

import static co.rsk.core.RskAddress.ZERO_ADDRESS;
import static co.rsk.peg.PegTestUtils.createHash3;
import static org.ethereum.vm.PrecompiledContracts.BRIDGE_ADDR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.peg.BridgeMethods.AuthorizerProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.FederationMember.KeyType;
import co.rsk.peg.flyover.FlyoverTxResponseCodes;
import co.rsk.peg.union.UnionBridgeSupport;
import co.rsk.peg.union.UnionResponseCode;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.test.builders.BridgeBuilder;
import co.rsk.test.builders.BridgeSupportBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.MessageCall;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class BridgeTest {
    private NetworkParameters networkParameters;
    private BridgeBuilder bridgeBuilder;
    private final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();

    @BeforeEach
    void resetConfigToMainnet() {
        networkParameters = BridgeMainNetConstants.getInstance().getBtcParams();
        bridgeBuilder = new BridgeBuilder();
    }

    @Test
    void getActivePowpegRedeemScript_before_RSKIP293_activation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        byte[] data = BridgeMethods.GET_ACTIVE_POWPEG_REDEEM_SCRIPT.getFunction().encode();

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void getActivePowpegRedeemScript_after_RSKIP293_activation() throws VMException {
        ActivationConfig activationConfig = ActivationConfigsForTest.hop400();
        CallTransaction.Function getActivePowpegRedeemScriptFunction = BridgeMethods.GET_ACTIVE_POWPEG_REDEEM_SCRIPT.getFunction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Script activePowpegRedeemScript = FederationTestUtils.getGenesisFederation(
            bridgeMainNetConstants.getFederationConstants()).getRedeemScript();
        when(bridgeSupportMock.getActiveFederationRedeemScript()).thenReturn(
            Optional.of(activePowpegRedeemScript)
        );

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        byte[] data = getActivePowpegRedeemScriptFunction.encode();
        byte[] result = bridge.execute(data);
        byte[] decodedResult = (byte[]) getActivePowpegRedeemScriptFunction.decodeResult(result)[0];
        Script obtainedRedeemScript = new Script(decodedResult);

        assertEquals(activePowpegRedeemScript, obtainedRedeemScript);
    }

    @Test
    void getLockingCap_before_RSKIP134_activation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.wasabi100();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        byte[] data = BridgeMethods.GET_LOCKING_CAP.getFunction().encode();

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void getLockingCap_after_RSKIP134_activation() throws VMException {
        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();
        CallTransaction.Function getLockingCapFunction = Bridge.GET_LOCKING_CAP;

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Coin lockingCap = Coin.COIN;
        when(bridgeSupportMock.getLockingCap()).thenReturn(lockingCap);

        Transaction tx = mock(Transaction.class);
        when(tx.isLocalCallTransaction()).thenReturn(true);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .transaction(tx)
            .bridgeSupport(bridgeSupportMock)
            .build();

        byte[] data = getLockingCapFunction.encode();
        byte[] result = bridge.execute(data);
        BigInteger decodedResult = (BigInteger) getLockingCapFunction.decodeResult(result)[0];
        Coin obtainedLockingCap = Coin.valueOf(decodedResult.longValue());

        assertEquals(lockingCap, obtainedLockingCap);

        // Also test the method itself
        long lockingCapFromTheBridge = bridge.getLockingCap(new Object[]{});
        assertEquals(lockingCap.getValue(), lockingCapFromTheBridge);
    }

    @Test
    void increaseLockingCap_before_RSKIP134_activation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.wasabi100();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        byte[] data = BridgeMethods.INCREASE_LOCKING_CAP.getFunction().encode();
        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @ParameterizedTest()
    @MethodSource("lockingCapValues")
    void increaseLockingCap_after_RSKIP134_activation(long newLockingCapValue) throws VMException {
        ActivationConfig activationConfig = ActivationConfigsForTest.all();
        CallTransaction.Function increaseLockingCapFunction = Bridge.INCREASE_LOCKING_CAP;

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.increaseLockingCap(any(), any())).thenReturn(true);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        byte[] data = increaseLockingCapFunction.encode(newLockingCapValue);
        byte[] result = bridge.execute(data);
        boolean decodedResult = (boolean) increaseLockingCapFunction.decodeResult(result)[0];

        assertTrue(decodedResult);

        // Also test the method itself
        boolean resultFromTheBridge = bridge.increaseLockingCap(new Object[]{BigInteger.valueOf(newLockingCapValue)});
        assertTrue(resultFromTheBridge);
    }

    private static Stream<Arguments> lockingCapValues() {
        return Stream.of(
            Arguments.of(1),
            Arguments.of(21_000_0000)
        );
    }

    @Test
    void increaseLockingCap_whenNewLockingCapIsInvalidParameter_shouldThrowVMException() {
        ActivationConfig activationConfig = ActivationConfigsForTest.all();
        CallTransaction.Function increaseLockingCapFunction = Bridge.INCREASE_LOCKING_CAP;
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        // Uses the proper signature but appends an invalid data type
        // This will be rejected by the solidity decoder in the Bridge directly
        final byte[] invalidTypeData = ByteUtil.merge(increaseLockingCapFunction.encodeSignature(), Hex.decode("ab"));
        assertThrows(VMException.class, () -> bridge.execute(invalidTypeData));

        // Uses the proper signature and data type, but with a value that exceeds the long max value
        final byte[] aboveMaxLengthData = ByteUtil.merge(
            increaseLockingCapFunction.encodeSignature(),
            Hex.decode("0000000000000000000000000000000000000000000000080000000000000000")
        );
        assertThrows(VMException.class, () -> bridge.execute(aboveMaxLengthData));
    }

    @Test
    void increaseLockingCap_whenNoArgumentsInTheMethodSignature_shouldThrowVMException() {
        // Arrange
        ActivationConfig activationConfig = ActivationConfigsForTest.all();
        CallTransaction.Function increaseLockingCapFunction = Bridge.INCREASE_LOCKING_CAP;
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        // No arguments signature
        final byte[] noArgumentData = increaseLockingCapFunction.encodeArguments();

        // Act / Assert
        assertThrows(VMException.class, () -> bridge.execute(noArgumentData));
    }

    @Test
    void increaseLockingCap_whenNewLockingCapIsNegativeValue_shouldThrowVMException() {
        // Arrange
        ActivationConfig activationConfig = ActivationConfigsForTest.all();
        CallTransaction.Function increaseLockingCapFunction = Bridge.INCREASE_LOCKING_CAP;
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        // When new LockingCap is a negative value
        final byte[] negativeValueData = increaseLockingCapFunction.encodeArguments(Coin.NEGATIVE_SATOSHI.getValue());

        // Act / Assert
        assertThrows(VMException.class, () -> bridge.execute(negativeValueData));
    }

    @Test
    void increaseLockingCap_whenNewLockingCapIsZeroValue_shouldThrowVMException() {
        // Arrange
        ActivationConfig activationConfig = ActivationConfigsForTest.all();
        CallTransaction.Function increaseLockingCapFunction = Bridge.INCREASE_LOCKING_CAP;
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        // When new LockingCap is a zero value
        final byte[] negativeValueData = increaseLockingCapFunction.encodeArguments(Coin.ZERO.getValue());

        // Act / Assert
        assertThrows(VMException.class, () -> bridge.execute(negativeValueData));
    }

    @Test
    void registerBtcCoinbaseTransaction_before_RSKIP143_activation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.wasabi100();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        Integer zero = 0;
        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encode(value, zero, value, zero, zero);

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void registerBtcCoinbaseTransaction_after_RSKIP143_activation() throws VMException {
        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        Integer zero = 0;

        byte[] data = Bridge.REGISTER_BTC_COINBASE_TRANSACTION.encode(value, zero, value, zero, zero);

        bridge.execute(data);
        verify(bridgeSupportMock, times(1)).registerBtcCoinbaseTransaction(
            value,
            Sha256Hash.wrap(value),
            value,
            Sha256Hash.wrap(value),
            value
        );
    }

    @Test
    void registerBtcCoinbaseTransaction_after_RSKIP143_activation_null_data() {
        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();
        CallTransaction.Function registerBtcCoinbaseTransactionFunction = Bridge.REGISTER_BTC_COINBASE_TRANSACTION;

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        final byte[] emptyData = registerBtcCoinbaseTransactionFunction.encodeSignature();
        assertThrows(VMException.class, () -> bridge.execute(emptyData));

        final byte[] invalidStringData = ByteUtil.merge(registerBtcCoinbaseTransactionFunction.encodeSignature(), Hex.decode("ab"));
        assertThrows(VMException.class, () -> bridge.execute(invalidStringData));

        final byte[] invalidHexData = ByteUtil.merge(registerBtcCoinbaseTransactionFunction.encodeSignature(), Hex.decode("0000000000000000000000000000000000000000000000080000000000000000"));
        assertThrows(VMException.class, () -> bridge.execute(invalidHexData));
    }

    @Test
    void registerBtcTransaction_beforeRskip199_rejectsExternalCalls() throws VMException, IOException, BlockStoreException {
        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();
        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        FederationArgs activeFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            btcParams
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(activeFederationArgs);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(activeFederation);

        Transaction rskTx = mock(Transaction.class);
        RskAddress senderAddress = new RskAddress("0000000000000000000000000000000000000001");
        when(rskTx.getSender(any(SignatureCache.class))).thenReturn(senderAddress);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .transaction(rskTx)
            .build();

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        int zero = 0;
        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(value, zero, value);

        try {
            bridge.execute(data);
            fail();
        } catch (VMException e) {
            assertEquals("Exception executing bridge: The sender is not a member of the active or retiring federations and is therefore not authorized to invoke the function: 'registerBtcTransaction'", e.getMessage());
        }

        verify(bridgeSupportMock, never()).registerBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class)
        );
    }

    @Test
    void registerBtcTransaction_beforeRskip199_acceptsCallFromFederationMember() throws VMException, IOException, BlockStoreException {
        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();
        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        BtcECKey fed1Key = new BtcECKey();
        RskAddress fed1Address = new RskAddress(ECKey.fromPublicOnly(fed1Key.getPubKey()).getAddress());
        List<BtcECKey> federationKeys = Arrays.asList(fed1Key, new BtcECKey(), new BtcECKey());
        federationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        FederationArgs activeFederationArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithKeys(federationKeys),
            Instant.ofEpochMilli(1000),
            0L,
            btcParams
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(activeFederationArgs);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(activeFederation);

        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getSender(any(SignatureCache.class))).thenReturn(fed1Address);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .transaction(rskTx)
            .build();

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        int zero = 0;
        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(value, zero, value);

        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).registerBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class)
        );
    }

    @Test
    void registerBtcTransaction_afterRskip199_acceptsExternalCalls() throws VMException, IOException, BlockStoreException {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        int zero = 0;
        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(value, zero, value);

        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).registerBtcTransaction(
            any(Transaction.class),
            any(byte[].class),
            anyInt(),
            any(byte[].class)
        );
    }

    @Test
    void getActiveFederationCreationBlockHeight_before_RSKIP186_activation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        byte[] data = BridgeMethods.GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT.getFunction().encode();

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void getActiveFederationCreationBlockHeight_after_RSKIP186_activation() throws VMException {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        long activeFederationCreationBlockHeight = 1L;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederationCreationBlockHeight()).thenReturn(activeFederationCreationBlockHeight);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT.getFunction();
        byte[] data = function.encode();
        byte[] result = bridge.execute(data);
        BigInteger decodedResult = (BigInteger)function.decodeResult(result)[0];

        assertEquals(activeFederationCreationBlockHeight, decodedResult.longValue());

        // Also test the method itself
        long resultFromTheBridge = bridge.getActiveFederationCreationBlockHeight(new Object[]{});
        assertEquals(activeFederationCreationBlockHeight, resultFromTheBridge);
    }

    @Test
    void registerFlyoverBtcTransaction_before_RSKIP176_activation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

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
        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void registerFlyoverBtcTransaction_after_RSKIP176_activation_testnet_p2sh_refund_address_before_RSKIP284_activation_fails()
        throws VMException, IOException, BlockStoreException {
        NetworkParameters testnetNetworkParameters = BridgeTestNetConstants.getInstance().getBtcParams();
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
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

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .constants(Constants.regtest())
            .build();

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();

        Address refundBtcAddress = Address.fromBase58(
            testnetNetworkParameters,
            "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP"
        );
        byte[] refundBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(
            activationConfig.forBlock(0),
            refundBtcAddress
        );

        BtcECKey btcECKeyLp = new BtcECKey();
        Address lpBtcAddress = btcECKeyLp.toAddress(testnetNetworkParameters);
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(
            activationConfig.forBlock(0),
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
        BigInteger decodedResult = (BigInteger) Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.decodeResult(result)[0];

        //Assert
        assertEquals(FlyoverTxResponseCodes.GENERIC_ERROR.value(), decodedResult.longValue());
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
    void registerFlyoverBtcTransaction_after_RSKIP176_activation_testnet_p2sh_refund_address_after_RSKIP284_activation_ok()
        throws VMException, IOException, BlockStoreException {
        NetworkParameters testnetNetworkParameters = BridgeTestNetConstants.getInstance().getBtcParams();
        ActivationConfig activationConfig = ActivationConfigsForTest.hop400();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
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

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .constants(Constants.regtest())
            .build();

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();

        Address refundBtcAddress = Address.fromBase58(
            testnetNetworkParameters,
            "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP"
        );
        byte[] refundBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(
            activationConfig.forBlock(0),
            refundBtcAddress
        );

        BtcECKey btcECKeyLp = new BtcECKey();
        Address lpBtcAddress = btcECKeyLp.toAddress(testnetNetworkParameters);
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(
            activationConfig.forBlock(0),
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
    void registerFlyoverBtcTransaction_after_RSKIP176_activation_generic_error() throws VMException, IOException, BlockStoreException {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
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

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

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
        BigInteger decodedResult = (BigInteger)Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.decodeResult(result)[0];

        assertEquals(FlyoverTxResponseCodes.GENERIC_ERROR.value(), decodedResult.longValue());
    }

    @Test
    void registerFlyoverBtcTransaction_after_RSKIP176_null_parameter() {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        byte[] noArgumentData = Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.encodeSignature();
        assertThrows(VMException.class, () -> bridge.execute(noArgumentData));

        byte[] value = Sha256Hash.ZERO_HASH.getBytes();
        byte[] zeroValueData = ByteUtil.merge(Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.encodeSignature(), value);
        assertThrows(VMException.class, () -> bridge.execute(zeroValueData));
    }

    @Test
    void receiveHeader_before_RSKIP200() {
        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
            networkParameters,
            1,
            BitcoinTestUtils.createHash(1),
            BitcoinTestUtils.createHash(1),
            1,
            Utils.encodeCompactBits(networkParameters.getMaxTarget()),
            1,
            new ArrayList<>()
        ).cloneAsHeader();

        Object[] parameters = new Object[]{block.bitcoinSerialize()};
        byte[] data = Bridge.RECEIVE_HEADER.encode(parameters);

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void receiveHeader_empty_parameter() {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        byte[] data = Bridge.RECEIVE_HEADER.encode();

        assertThrows(VMException.class, () -> bridge.execute(data));
        verifyNoInteractions(bridgeSupportMock);
    }

    @Test
    void receiveHeader_after_RSKIP200_Ok() throws VMException {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
            networkParameters,
            1,
            BitcoinTestUtils.createHash(1),
            BitcoinTestUtils.createHash(2),
            1L,
            100L,
            1L,
            new ArrayList<>()
        ).cloneAsHeader();

        CallTransaction.Function function = BridgeMethods.RECEIVE_HEADER.getFunction();

        byte[] data = function.encode(block.bitcoinSerialize());
        byte[] result = bridge.execute(data);
        BigInteger decodedResult = (BigInteger) function.decodeResult(result)[0];

        assertEquals(BigInteger.valueOf(0), decodedResult);
    }

    @Test
    void receiveHeader_bridgeSupport_Exception() throws IOException, BlockStoreException {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doThrow(new IOException()).when(bridgeSupportMock).receiveHeader(any())
        ;
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
            networkParameters,
            1,
            BitcoinTestUtils.createHash(1),
            BitcoinTestUtils.createHash(1),
            1,
            Utils.encodeCompactBits(networkParameters.getMaxTarget()),
            1,
            new ArrayList<>()
        ).cloneAsHeader();

        Object[] parameters = new Object[]{block.bitcoinSerialize()};
        byte[] data = Bridge.RECEIVE_HEADER.encode(parameters);

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void receiveHeaders_after_RSKIP200_notFederation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeMainNetConstants.getFederationConstants());

        when(bridgeSupportMock.getRetiringFederation()).thenReturn(null);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(genesisFederation);

        Transaction txMock = mock(Transaction.class);
        RskAddress txSender = new RskAddress(new ECKey().getAddress());
        when(txMock.getSender(any(SignatureCache.class))).thenReturn(txSender);  //access for anyone

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .transaction(txMock)
            .bridgeSupport(bridgeSupportMock)
            .build();

        byte[] data = Bridge.RECEIVE_HEADERS.encode();

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void receiveHeaders_after_RSKIP200_header_wrong_size() throws VMException {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        Object[] parameters = new Object[]{Sha256Hash.ZERO_HASH.getBytes()};
        byte[] data = Bridge.RECEIVE_HEADER.encode(parameters);

        byte[] result = bridge.execute(data);
        BigInteger decodedResult = (BigInteger) Bridge.RECEIVE_HEADER.decodeResult(result)[0];
        assertEquals(BigInteger.valueOf(-20), decodedResult);
    }

    @Test
    void getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls_afterRskip220() {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        assertFalse(bridge.getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls(new Object[0]));
    }

    @Test
    void getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls_beforeRskip220() {
        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        assertTrue(bridge.getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls(new Object[0]));
    }

    @Test
    void activeAndRetiringFederationOnly_activeFederationIsNotFromFederateMember_retiringFederationIsNull_throwsVMException() {
        // Given
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
            null,
            null
        );

        int senderPK = 999; // Sender PK does not belong to Member PKs
        Integer[] memberPKs = new Integer[]{ 100, 200, 300, 400, 500, 600 };
        Federation activeFederation = FederationTestUtils.getFederation(memberPKs);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(Optional.empty()).when(bridgeSupportMock).getRetiringFederation();

        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
        RskAddress txSender = new RskAddress(key.getAddress());
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(txSender).when(rskTxMock).getSender(any(SignatureCache.class));

        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        assertThrows(VMException.class, () -> executor.execute(bridge, null));
    }

    @Test
    void activeAndRetiringFederationOnly_activeFederationIsNotFromFederateMember_retiringFederationIsNotNull_retiringFederationIsNotFromFederateMember_throwsVMException() {
        // Given
        BridgeMethods.BridgeMethodExecutor executor = Bridge.activeAndRetiringFederationOnly(
            null,
            null
        );

        int senderPK = 999; // Sender PK does not belong to Member PKs of active nor retiring fed
        Integer[] activeMemberPKs = new Integer[]{ 100, 200, 300, 400, 500, 600 };
        Integer[] retiringMemberPKs = new Integer[]{ 101, 202, 303, 404, 505, 606 };

        Federation activeFederation = FederationTestUtils.getFederation(activeMemberPKs);
        Federation retiringFederation = FederationTestUtils.getFederation(retiringMemberPKs);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(Optional.of(retiringFederation)).when(bridgeSupportMock).getRetiringFederation();

        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
        RskAddress txSender = new RskAddress(key.getAddress());
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(txSender).when(rskTxMock).getSender(any(SignatureCache.class));

        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        assertThrows(VMException.class, () -> executor.execute(bridge, null));
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
        Integer[] memberPKs = new Integer[]{ 100, 200, 300, 400, 500, 600 };
        Federation activeFederation = FederationTestUtils.getFederation(memberPKs);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(Optional.empty()).when(bridgeSupportMock).getRetiringFederation();

        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
        RskAddress txSender = new RskAddress(key.getAddress());
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(txSender).when(rskTxMock).getSender(any(SignatureCache.class));

        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

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
        Integer[] activeMemberPKs = new Integer[]{ 100, 200, 300, 400, 500, 600 };
        Integer[] retiringMemberPKs = new Integer[]{ 101, 202, 303, 404, 505, 606 };

        Federation activeFederation = FederationTestUtils.getFederation(activeMemberPKs);
        Federation retiringFederation = FederationTestUtils.getFederation(retiringMemberPKs);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        doReturn(Optional.of(retiringFederation)).when(bridgeSupportMock).getRetiringFederation();

        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
        RskAddress txSender = new RskAddress(key.getAddress());
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(txSender).when(rskTxMock).getSender(any(SignatureCache.class));

        ActivationConfig activationConfig = ActivationConfigsForTest.papyrus200();

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        // When
        executor.execute(bridge, null);

        // Then
        verify(bridgeSupportMock, times(1)).getActiveFederation();
        verify(bridgeSupportMock, times(1)).getRetiringFederation();
        verify(decorate, times(1)).execute(any(), any());
    }

    @Test
    void getNextPegoutCreationBlockNumber_before_RSKIP271_activation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        byte[] data = BridgeMethods.GET_NEXT_PEGOUT_CREATION_BLOCK_NUMBER.getFunction().encode();

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void getNextPegoutCreationBlockNumber_after_RSKIP271_activation() throws VMException {
        ActivationConfig activationConfig = ActivationConfigsForTest.hop400();

        long nextPegoutCreationHeight = 1L;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getNextPegoutCreationBlockNumber()).thenReturn(nextPegoutCreationHeight);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_NEXT_PEGOUT_CREATION_BLOCK_NUMBER.getFunction();
        byte[] data = function.encode();
        byte[] result = bridge.execute(data);
        BigInteger decodedResult = (BigInteger) function.decodeResult(result)[0];

        assertEquals(nextPegoutCreationHeight, decodedResult.longValue());

        // Also test the method itself
        long resultFromTheBridge = bridge.getNextPegoutCreationBlockNumber(new Object[]{});
        assertEquals(nextPegoutCreationHeight, resultFromTheBridge);
    }

    @Test
    void getQueuedPegoutsCount_before_RSKIP271_activation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        byte[] data = BridgeMethods.GET_QUEUED_PEGOUTS_COUNT.getFunction().encode();

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void getQueuedPegoutsCount_after_RSKIP271_activation() throws VMException, IOException {
        ActivationConfig activationConfig = ActivationConfigsForTest.hop400();

        int queuedPegoutsCount = 1;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getQueuedPegoutsCount()).thenReturn(queuedPegoutsCount);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_QUEUED_PEGOUTS_COUNT.getFunction();
        byte[] data = function.encode();
        byte[] result = bridge.execute(data);
        BigInteger decodedResult = (BigInteger) function.decodeResult(result)[0];

        assertEquals(queuedPegoutsCount, decodedResult.intValue());

        // Also test the method itself
        int resultFromTheBridge = bridge.getQueuedPegoutsCount(new Object[]{});
        assertEquals(queuedPegoutsCount, resultFromTheBridge);
    }

    @Test
    void getEstimatedFeesForNextPegOutEvent_before_RSKIP271_activation() {
        ActivationConfig activationConfig = ActivationConfigsForTest.iris300();

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .build();

        byte[] data = BridgeMethods.GET_ESTIMATED_FEES_FOR_NEXT_PEGOUT_EVENT.getFunction().encode();

        assertThrows(VMException.class, () -> bridge.execute(data));
    }

    @Test
    void getEstimatedFeesForNextPegOutEvent_after_RSKIP271_activation() throws VMException, IOException {
        ActivationConfig activationConfig = ActivationConfigsForTest.hop400();

        Coin estimatedFeesForNextPegout = Coin.SATOSHI;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getEstimatedFeesForNextPegOutEvent()).thenReturn(estimatedFeesForNextPegout);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_ESTIMATED_FEES_FOR_NEXT_PEGOUT_EVENT.getFunction();
        byte[] data = function.encode();
        byte[] result = bridge.execute(data);
        BigInteger decodedResult = (BigInteger) function.decodeResult(result)[0];

        assertEquals(estimatedFeesForNextPegout.getValue(), decodedResult.longValue());

        // Also test the method itself
        long resultFromTheBridge = bridge.getEstimatedFeesForNextPegOutEvent(new Object[]{});
        assertEquals(estimatedFeesForNextPegout.getValue(), resultFromTheBridge);
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void addFederatorPublicKey(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        String publicKey = "039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9";
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY.getFunction();
        byte[] data = function.encode(Hex.decode(publicKey));

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            // Post RSKIP123 this method is no longer enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).voteFederationChange(any(), any());
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void addFederatorPublicKeyMultikey(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        String publicKey = "039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9";
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY.getFunction();
        byte[] data = function.encode(Hex.decode(publicKey), Hex.decode(publicKey), Hex.decode(publicKey));

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
                // Post arrowhead should fail for any msg type != CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).voteFederationChange(any(), any());
            }
        } else {
            // Pre RSKIP123 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void addLockWhitelistAddress(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        String addressBase58 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
        long maxTransferValue = 100_000L;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.ADD_LOCK_WHITELIST_ADDRESS.getFunction();
        byte[] data = function.encode(addressBase58, maxTransferValue);

        if (activationConfig.isActive(ConsensusRule.RSKIP87, 0)) {
            // Post RSKIP87 this method is no longer enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).addOneOffLockWhitelistAddress(
                any(),
                any(),
                any()
            );
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void addOneOffLockWhitelistAddress(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        String addressBase58 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
        long maxTransferValue = 100_000L;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.getFunction();
        byte[] data = function.encode(addressBase58, maxTransferValue);

        if (activationConfig.isActive(ConsensusRule.RSKIP87, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
                // Post arrowhead should fail for any msg type != CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).addOneOffLockWhitelistAddress(
                    any(),
                    any(),
                    any()
                );
            }
        } else {
            // Pre RSKIP87 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void addUnlimitedLockWhitelistAddress(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        String addressBase58 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.getFunction();
        byte[] data = function.encode(addressBase58);

        if (activationConfig.isActive(ConsensusRule.RSKIP87, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
                // Post arrowhead should fail for any msg type != CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).addUnlimitedLockWhitelistAddress(
                    any(),
                    any()
                );
            }
        } else {
            // Pre RSKIP87 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void addSignatures(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws Exception {
        String pegnatoryPublicKey = "039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9";
        String signature = "3045022100a0963cea7551eb3174a3470c6ed25cda901c4b1093818d4d54792b87508820220220325f93b5aecc98385a664328e68d1cec7a2a2fe81810a7692358bd870aeecb74";
        List<byte[]> derEncodedSigs = Collections.singletonList(Hex.decode(signature));
        Keccak256 rskTxHash = createHash3(1);

        int senderPK = 101; // Sender PK belongs to active federation member PKs
        Integer[] activeMemberPKs = new Integer[]{ 100, 200, 300, 400, 500, 600 };
        Federation activeFederation = FederationTestUtils.getFederation(activeMemberPKs);

        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
        RskAddress txSender = new RskAddress(key.getAddress());
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(txSender).when(rskTxMock).getSender(any(SignatureCache.class));

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.ADD_SIGNATURE.getFunction();
        byte[] data = function.encode(Hex.decode(pegnatoryPublicKey), derEncodedSigs, rskTxHash.getBytes());

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            byte[] result = bridge.execute(data);
            assertVoidMethodResult(activationConfig, result);
            verify(bridgeSupportMock, times(1)).addSignature(
                any(),
                any(),
                any()
            );
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void commitFederation(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Keccak256 commitTransactionHash = createHash3(2);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.COMMIT_FEDERATION.getFunction();
        byte[] data = function.encode(commitTransactionHash.getBytes());

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).voteFederationChange(
                any(),
                any()
            );
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void createFederation(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.CREATE_FEDERATION.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).voteFederationChange(
                any(),
                any()
            );
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcBlockchainBestChainHeight(MessageCall.MsgType msgType, ActivationConfig activationConfig)
        throws VMException, BlockStoreException, IOException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getBtcBlockchainBestChainHeight();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcBlockchainInitialBlockHeight(MessageCall.MsgType msgType, ActivationConfig activationConfig)
        throws VMException, IOException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getBtcBlockchainInitialBlockHeight();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcBlockchainBlockLocator(MessageCall.MsgType msgType, ActivationConfig activationConfig)
        throws VMException, IOException, BlockStoreException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP89, 0)) {
            // Post RSKIP89 this method is no longer enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getBtcBlockchainBlockLocator();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcBlockchainBlockHashAtDepth(MessageCall.MsgType msgType, ActivationConfig activationConfig)
        throws VMException, IOException, BlockStoreException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        int depth = 1000;
        Sha256Hash blockHashAtDepth = BitcoinTestUtils.createHash(1);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getBtcBlockchainBlockHashAtDepth(depth)).thenReturn(blockHashAtDepth);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH.getFunction();
        byte[] data = function.encode(depth);

        if (activationConfig.isActive(ConsensusRule.RSKIP89, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getBtcBlockchainBlockHashAtDepth(depth);
            }
        } else {
            // Pre RSKIP89 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcTransactionConfirmations(MessageCall.MsgType msgType, ActivationConfig activationConfig)
        throws VMException, IOException, BlockStoreException {

        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(1);
        Sha256Hash btcBlockHash = BitcoinTestUtils.createHash(2);
        int merkleBranchPath = 1;
        List<Sha256Hash> merkleBranchHashes = Arrays.asList(
            BitcoinTestUtils.createHash(10),
            BitcoinTestUtils.createHash(11),
            BitcoinTestUtils.createHash(12)
        );
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_TRANSACTION_CONFIRMATIONS.getFunction();
        byte[] data = function.encode(
            btcTxHash.getBytes(),
            btcBlockHash.getBytes(),
            merkleBranchPath,
            merkleBranchHashes.stream().map(Sha256Hash::getBytes).toArray()
        );

        if (activationConfig.isActive(ConsensusRule.RSKIP122, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getBtcTransactionConfirmations(
                    any(),
                    any(),
                    any()
                );
            }
        } else {
            // Pre RSKIP122 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcTxHashProcessedHeight(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(1);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_TX_HASH_PROCESSED_HEIGHT.getFunction();
        byte[] data = function.encode(btcTxHash.toString());

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getBtcTxHashProcessedHeight(btcTxHash);
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFederationAddress(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        Address federationAddress = Address.fromBase58(networkParameters, "32Bhwee9FzQbuaG29RcXpdrvYnvZeMk11M");
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederationAddress()).thenReturn(federationAddress);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getActiveFederationAddress();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFederationCreationBlockNumber(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_FEDERATION_CREATION_BLOCK_NUMBER.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getActiveFederationCreationBlockNumber();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFederationCreationTime(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederationCreationTime()).thenReturn(Instant.ofEpochSecond(100_000L));
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_FEDERATION_CREATION_TIME.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getActiveFederationCreationTime();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFederationSize(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_FEDERATION_SIZE.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getActiveFederationSize();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFederationThreshold(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_FEDERATION_THRESHOLD.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getActiveFederationThreshold();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFederatorPublicKey(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        int federatorIndex = 1;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction();
        byte[] data = function.encode(federatorIndex);

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            // Post RSKIP123 this method is no longer enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getActiveFederatorBtcPublicKey(federatorIndex);
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFederatorPublicKeyOfType(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        int federatorIndex = 1;
        KeyType keyType = KeyType.BTC;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction();
        byte[] data = function.encode(federatorIndex, keyType.getValue());

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getActiveFederatorPublicKeyOfType(
                    federatorIndex,
                    keyType
                );
            }
        } else {
            // Pre RSKIP123 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFeePerKb(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        Coin feePerKb = Coin.COIN;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getFeePerKb()).thenReturn(feePerKb);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_FEE_PER_KB.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getFeePerKb();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getLockWhitelistAddress(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        int index = 1;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_LOCK_WHITELIST_ADDRESS.getFunction();
        byte[] data = function.encode(index);

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getLockWhitelistEntryByIndex(index);
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getLockWhitelistEntryByAddress(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        String addressBase58 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction();
        byte[] data = function.encode(addressBase58);

        if (activationConfig.isActive(ConsensusRule.RSKIP87, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getLockWhitelistEntryByAddress(addressBase58);
            }
        } else {
            // Pre RSKIP87 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getLockWhitelistSize(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_LOCK_WHITELIST_SIZE.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getLockWhitelistSize();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getMinimumLockTxValue(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        Coin minimumPeginTxValue = Coin.COIN;
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        when(bridgeConstants.getMinimumPeginTxValue(any())).thenReturn(minimumPeginTxValue);
        Constants constants = mock(Constants.class);
        when(constants.getBridgeConstants()).thenReturn(bridgeConstants);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .constants(constants)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_MINIMUM_LOCK_TX_VALUE.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeConstants, times(1)).getMinimumPeginTxValue(any());
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getPendingFederationHash(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_PENDING_FEDERATION_HASH.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getPendingFederationHash();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getPendingFederationSize(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException
    {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_PENDING_FEDERATION_SIZE.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getPendingFederationSize();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getPendingFederatorPublicKey(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction();

        int federatorIndex = 1;
        byte[] data = function.encode(federatorIndex);

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            // Post RSKIP123 this method is no longer enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getPendingFederatorBtcPublicKey(federatorIndex);
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getPendingFederatorPublicKeyOfType(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction();

        int federatorIndex = 1;
        FederationMember.KeyType keyType = FederationMember.KeyType.BTC;
        byte[] data = function.encode(federatorIndex, keyType.getValue());

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getPendingFederatorPublicKeyOfType(
                    federatorIndex,
                    keyType
                );
            }
        } else {
            // Pre RSKIP123 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getRetiringFederationAddress(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Address retiringFederationAddress = Address.fromBase58(networkParameters, "32Bhwee9FzQbuaG29RcXpdrvYnvZeMk11M");
        when(bridgeSupportMock.getRetiringFederationAddress()).thenReturn(retiringFederationAddress);

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_RETIRING_FEDERATION_ADDRESS.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getRetiringFederationAddress();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getRetiringFederationCreationBlockNumber(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getRetiringFederationCreationBlockNumber();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getRetiringFederationCreationTime(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getActiveFederationCreationTime()).thenReturn(Instant.ofEpochSecond(100_000L));
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_RETIRING_FEDERATION_CREATION_TIME.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getRetiringFederationCreationTime();
        }
    }

    @ParameterizedTest
    @MethodSource("activationsAndExpectedFederationCreationTimeArgs")
    void getActiveFederationCreationTime_returnsCreationTimeInExpectedTimeUnit(ActivationConfig activationConfig, long expectedActiveFederationCreationTime) {
        // arrange
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Instant creationTime = Instant.ofEpochMilli(5000);
        when(bridgeSupportMock.getActiveFederationCreationTime()).thenReturn(creationTime);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        // act
        long actualActiveFederationCreationTime = bridge.getFederationCreationTime(new Object[]{});

        assertEquals(expectedActiveFederationCreationTime, actualActiveFederationCreationTime);
    }

    @ParameterizedTest
    @MethodSource("activationsAndExpectedFederationCreationTimeArgs")
    void getRetiringFederationCreationTime_returnsCreationTimeInExpectedTimeUnit(ActivationConfig activationConfig, long expectedRetiringFederationCreationTime) {
        // arrange
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Instant creationTime = Instant.ofEpochMilli(5000);
        when(bridgeSupportMock.getRetiringFederationCreationTime()).thenReturn(Optional.of(creationTime));

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        // act
        long actualRetiringFederationCreationTime = bridge.getRetiringFederationCreationTime(new Object[]{});

        // assert
        assertEquals(expectedRetiringFederationCreationTime, actualRetiringFederationCreationTime);
    }

    private static Stream<Arguments> activationsAndExpectedFederationCreationTimeArgs() {
        long creationTimeInMillis = 5000;
        long creationTimeInSeconds = 5;

        return Stream.of(
            Arguments.of(ActivationConfigsForTest.arrowhead631(), creationTimeInMillis),
            Arguments.of(ActivationConfigsForTest.all(), creationTimeInSeconds)
        );
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getRetiringFederationSize(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_RETIRING_FEDERATION_SIZE.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getRetiringFederationSize();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getRetiringFederationThreshold(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_RETIRING_FEDERATION_THRESHOLD.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getRetiringFederationThreshold();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getRetiringFederatorPublicKey(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        int federatorIndex = 1;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction();
        byte[] data = function.encode(federatorIndex);

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            // Post RSKIP123 this method is no longer enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getRetiringFederatorBtcPublicKey(federatorIndex);
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getRetiringFederatorPublicKeyOfType(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction();

        int federatorIndex = 1;
        FederationMember.KeyType keyType = FederationMember.KeyType.BTC;
        byte[] data = function.encode(federatorIndex, keyType.getValue());

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getRetiringFederatorPublicKeyOfType(
                    federatorIndex,
                    keyType
                );
            }
        } else {
            // Pre RSKIP123 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getProposedFederationAddress(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Address expectedAddress = Address.fromBase58(networkParameters, "32Bhwee9FzQbuaG29RcXpdrvYnvZeMk11M");
        when(bridgeSupportMock.getProposedFederationAddress()).thenReturn(Optional.of(expectedAddress));

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_PROPOSED_FEDERATION_ADDRESS.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP419, 0)) {
            if (!(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock).getProposedFederationAddress();
            }
        } else {
            // Pre RSKIP419 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getProposedFederationSize(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Integer expectedSize = 9;
        when(bridgeSupportMock.getProposedFederationSize()).thenReturn(Optional.of(expectedSize));

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_PROPOSED_FEDERATION_SIZE.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP419, 0)) {
            if (!(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock).getProposedFederationSize();
            }
        } else {
            // Pre RSKIP419 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getProposedFederationCreationTime(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Instant expectedCreationTime = Instant.EPOCH;
        when(bridgeSupportMock.getProposedFederationCreationTime()).thenReturn(Optional.of(expectedCreationTime));

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_PROPOSED_FEDERATION_CREATION_TIME.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP419, 0)) {
            if (!(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock).getProposedFederationCreationTime();
            }
        } else {
            // Pre RSKIP419 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @Test
    void getProposedFederationCreationTime_shouldReturnValueFromSeconds() {
        // arrange
        ActivationConfig activationConfig = ActivationConfigsForTest.all();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        long creationTimeInSeconds = 1000;
        Instant creationTime = Instant.ofEpochSecond(creationTimeInSeconds);
        when(bridgeSupportMock.getProposedFederationCreationTime()).thenReturn(Optional.of(creationTime));

        // act & assert
        assertEquals(creationTimeInSeconds, bridge.getProposedFederationCreationTime(new Object[]{}));
    }

    @Test
    void getProposedFederationCreationTime_whenBridgeSupportReturnsEmpty_shouldReturnMinusOne() {
        // arrange
        ActivationConfig activationConfig = ActivationConfigsForTest.all();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .build();

        when(bridgeSupportMock.getProposedFederationCreationTime()).thenReturn(Optional.empty());

        // act & assert
        long expectedCreationTime = -1L;
        assertEquals(expectedCreationTime, bridge.getProposedFederationCreationTime(new Object[]{}));
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getProposedFederationCreationBlockNumber(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        long expectedCreationBlockNumber = 123456L;
        when(bridgeSupportMock.getProposedFederationCreationBlockNumber()).thenReturn(Optional.of(expectedCreationBlockNumber));

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_PROPOSED_FEDERATION_CREATION_BLOCK_NUMBER.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP419, 0)) {
            if (!(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock).getProposedFederationCreationBlockNumber();
            }
        } else {
            // Pre RSKIP419 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getProposedFederatorPublicKeyOfType(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        int federatorIndex = 1;
        KeyType keyType = KeyType.BTC;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_PROPOSED_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction();
        byte[] data = function.encode(federatorIndex, keyType.getValue());

        if (activationConfig.isActive(ConsensusRule.RSKIP419, 0)) {
            if (!(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock).getProposedFederatorPublicKeyOfType(federatorIndex, keyType);
            }
        } else {
            // Pre RSKIP419 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @Test
    void getProposedFederatorPublicKeyOfType_whenBridgeSupportCallThrowsIOOBE_throwsVMException() {
        // arrange
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();
        ActivationConfig activationConfig = ActivationConfigsForTest.all();
        MessageCall.MsgType msgType = MessageCall.MsgType.CALL;

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        // this value is not being actually tested since we are mocking the response
        int outOfBoundsIndex = 1000;
        KeyType keyType = KeyType.BTC;
        when(bridgeSupportMock.getProposedFederatorPublicKeyOfType(outOfBoundsIndex, keyType)).thenThrow(IndexOutOfBoundsException.class);

        // act & assert
        Object[] args = new Object[]{ BigInteger.valueOf(outOfBoundsIndex), keyType.getValue() };
        assertThrows(VMException.class, () -> bridge.getProposedFederatorPublicKeyOfType(args));
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getStateForBtcReleaseClient(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_STATE_FOR_BTC_RELEASE_CLIENT.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getStateForBtcReleaseClient();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getStateForSvpClient(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_STATE_FOR_SVP_CLIENT.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP419, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock).getStateForSvpClient();
            }
        } else {
            // Pre RSKIP419 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getStateForDebugging(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_STATE_FOR_DEBUGGING.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getStateForDebugging();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getLockingCap(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Coin lockingCap = Coin.COIN;
        when(bridgeSupportMock.getLockingCap()).thenReturn(lockingCap);

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_LOCKING_CAP.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP134, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getLockingCap();
            }
        } else {
            // Pre RSKIP134 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getActivePowpegRedeemScript(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Integer[] activeMemberPKs = new Integer[]{ 100, 200, 300, 400, 500, 600 };
        Federation activeFederation = FederationTestUtils.getFederation(activeMemberPKs);
        Script activePowpegRedeemScript = activeFederation.getRedeemScript();
        when(bridgeSupportMock.getActiveFederationRedeemScript()).thenReturn(Optional.of(activePowpegRedeemScript));

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_ACTIVE_POWPEG_REDEEM_SCRIPT.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP293, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getActiveFederationRedeemScript();
            }
        } else {
            // Pre RSKIP293 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getActiveFederationCreationBlockHeight(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP186, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getActiveFederationCreationBlockHeight();
            }
        }
        else {
            // Pre RSKIP186 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void increaseLockingCap(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.increaseLockingCap(any(), any())).thenReturn(true);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.INCREASE_LOCKING_CAP.getFunction();

        long newLockingCap = 1;
        byte[] data = function.encode(newLockingCap);

        if (activationConfig.isActive(ConsensusRule.RSKIP134, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
                // Post arrowhead should fail for any msg type != CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).increaseLockingCap(any(), any());
            }
        }
        else {
            // Pre RSKIP134 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void isBtcTxHashAlreadyProcessed(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Sha256Hash btcTxHash = Sha256Hash.of("btcTxHash".getBytes());
        when(bridgeSupportMock.isBtcTxHashAlreadyProcessed(btcTxHash)).thenReturn(true);

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.IS_BTC_TX_HASH_ALREADY_PROCESSED.getFunction();
        byte[] data = function.encode(btcTxHash.toString());

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).isBtcTxHashAlreadyProcessed(btcTxHash);
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void receiveHeaders(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        Transaction rskTxMock = mock(Transaction.class);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        boolean receiveHeadersIsPublic =
            activationConfig.isActive(ConsensusRule.RSKIP124, 0)
                && !activationConfig.isActive(ConsensusRule.RSKIP200, 0);

        // Pre RSKIP124 and post RSKIP200 receiveHeaders is callable only from active or retiring federation fed members
        if (!receiveHeadersIsPublic) {
            int senderPK = 101; // Sender PK belongs to active federation member PKs
            Integer[] activeMemberPKs = new Integer[]{ 100, 200, 300, 400, 500, 600 };
            Federation activeFederation = FederationTestUtils.getFederation(activeMemberPKs);

            ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
            RskAddress txSender = new RskAddress(key.getAddress());
            doReturn(txSender).when(rskTxMock).getSender(any(SignatureCache.class));
            doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        }

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.RECEIVE_HEADERS.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            byte[] result = bridge.execute(data);
            assertVoidMethodResult(activationConfig, result);
            verify(bridgeSupportMock, times(1)).receiveHeaders(new BtcBlock[]{});
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void receiveHeader(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.RECEIVE_HEADER.getFunction();

        BtcBlock btcBlock = new BtcBlock(
            networkParameters,
            1,
            BitcoinTestUtils.createHash(1),
            BitcoinTestUtils.createHash(2),
            1,
            100L,
            1,
            new ArrayList<>()
        ).cloneAsHeader();

        byte[] serializedBlockHeader = btcBlock.bitcoinSerialize();
        byte[] data = function.encode(serializedBlockHeader);

        if (activationConfig.isActive(ConsensusRule.RSKIP200, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
                // Post arrowhead should fail for any msg type != CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).receiveHeader(any());
            }
        } else {
            // Pre RSKIP200 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void registerBtcTransaction(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        Transaction rskTxMock = mock(Transaction.class);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        boolean registerBtcTransactionIsPublic = activationConfig.isActive(ConsensusRule.RSKIP199, 0);
        // Before RSKIP199 registerBtcTransaction was callable only from active or retiring federation fed members
        if (!registerBtcTransactionIsPublic) {
            int senderPK = 101; // Sender PK belongs to active federation member PKs
            Integer[] activeMemberPKs = new Integer[]{ 100, 200, 300, 400, 500, 600 };
            Federation activeFederation = FederationTestUtils.getFederation(activeMemberPKs);

            ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
            RskAddress txSender = new RskAddress(key.getAddress());
            doReturn(txSender).when(rskTxMock).getSender(any(SignatureCache.class));
            doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();
        }

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.REGISTER_BTC_TRANSACTION.getFunction();

        byte[] btcTxSerialized = new byte[]{1};
        int height = 0;
        byte[] pmtSerialized = new byte[]{2};
        byte[] data = function.encode(btcTxSerialized, height, pmtSerialized);

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            byte[] result = bridge.execute(data);
            assertVoidMethodResult(activationConfig, result);
            verify(bridgeSupportMock, times(1)).registerBtcTransaction(
                any(Transaction.class),
                any(byte[].class),
                anyInt(),
                any(byte[].class)
            );
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void releaseBtc(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.RELEASE_BTC.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            byte[] result = bridge.execute(data);
            assertVoidMethodResult(activationConfig, result);
            verify(bridgeSupportMock, times(1)).releaseBtc(any());
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void removeLockWhitelistAddress(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.REMOVE_LOCK_WHITELIST_ADDRESS.getFunction();

        String addressBase58 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
        byte[] data = function.encode(addressBase58);

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).removeLockWhitelistAddress(any(), any());
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void rollbackFederation(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.ROLLBACK_FEDERATION.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).voteFederationChange(any(), any());
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void setLockWhiteListDisableBlockDelay(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY.getFunction();

        BigInteger disableBlockDelay = BigInteger.valueOf(100);
        byte[] data = function.encode(disableBlockDelay);

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).setLockWhitelistDisableBlockDelay(any(), any());
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void updateCollections(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException {
        Transaction rskTxMock = mock(Transaction.class);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        // updateCollections is only callable from active or retiring federation
        int senderPK = 101; // Sender PK belongs to active federation member PKs
        Integer[] activeMemberPKs = new Integer[]{ 100, 200, 300, 400, 500, 600 };
        Federation activeFederation = FederationTestUtils.getFederation(activeMemberPKs);

        ECKey key = ECKey.fromPrivate(BigInteger.valueOf(senderPK));
        RskAddress txSender = new RskAddress(key.getAddress());
        doReturn(txSender).when(rskTxMock).getSender(any(SignatureCache.class));
        doReturn(activeFederation).when(bridgeSupportMock).getActiveFederation();

        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.UPDATE_COLLECTIONS.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            byte[] result = bridge.execute(data);
            assertVoidMethodResult(activationConfig, result);
            verify(bridgeSupportMock, times(1)).updateCollections(rskTxMock);
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void voteFeePerKb(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.VOTE_FEE_PER_KB.getFunction();

        long feePerKB = 10_000;
        byte[] data = function.encode(feePerKB);

        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).voteFeePerKbChange(any(), any());
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void registerBtcCoinbaseTransaction(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.REGISTER_BTC_COINBASE_TRANSACTION.getFunction();

        byte[] btcTxSerialized = ByteUtil.EMPTY_BYTE_ARRAY;
        Sha256Hash blockHash = BitcoinTestUtils.createHash(1);
        byte[] pmtSerialized = ByteUtil.EMPTY_BYTE_ARRAY;
        Sha256Hash witnessMerkleRoot = BitcoinTestUtils.createHash(2);
        byte[] witnessReservedValue = new byte[32];
        byte[] data = function.encode(
            btcTxSerialized,
            blockHash.getBytes(),
            pmtSerialized,
            witnessMerkleRoot.getBytes(),
            witnessReservedValue
        );

        if (activationConfig.isActive(ConsensusRule.RSKIP143, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
                // Post arrowhead should fail for any msg type != CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                byte[] result = bridge.execute(data);
                assertVoidMethodResult(activationConfig, result);
                verify(bridgeSupportMock, times(1)).registerBtcCoinbaseTransaction(
                    btcTxSerialized,
                    blockHash,
                    pmtSerialized,
                    witnessMerkleRoot,
                    witnessReservedValue
                );
            }
        } else {
            // Pre RSKIP143 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void hasBtcBlockCoinbaseTransactionInformation(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.getFunction();

        Sha256Hash blockHash = BitcoinTestUtils.createHash(2);
        byte[] data = function.encode(blockHash.getBytes());

        if (activationConfig.isActive(ConsensusRule.RSKIP143, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATICCALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).hasBtcBlockCoinbaseTransactionInformation(any());
            }
        } else {
            // Pre RSKIP143 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void registerFastBridgeBtcTransaction(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.getFunction();

        byte[] btcTxSerialized = new byte[]{1};
        int height = 1;
        byte[] pmtSerialized = new byte[]{2};
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(2);
        byte[] refundAddressSerialized = new byte[21];
        RskAddress lbcAddress = new RskAddress("0xFcE93641243D1EFB6131277cCD1c0a60460d5610");
        byte[] lpAddressSerialized = new byte[21];
        boolean shouldTransferToContract = true;
        byte[] data = function.encode(
            btcTxSerialized,
            height,
            pmtSerialized,
            derivationArgumentsHash.getBytes(),
            refundAddressSerialized,
            lbcAddress.toHexString(),
            lpAddressSerialized,
            shouldTransferToContract
        );

        if (activationConfig.isActive(ConsensusRule.RSKIP176, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
                // Post arrowhead should fail for any msg type != CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).registerFlyoverBtcTransaction(
                    any(Transaction.class),
                    any(byte[].class),
                    anyInt(),
                    any(byte[].class),
                    any(Keccak256.class),
                    any(Address.class),
                    any(RskAddress.class),
                    any(Address.class),
                    any(boolean.class)
                );
            }
        } else {
            // Pre RSKIP176 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcBlockchainBestBlockHeader(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_BLOCKCHAIN_BEST_BLOCK_HEADER.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP220, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getBtcBlockchainBestBlockHeader();
            }
        } else {
            // Pre RSKIP220 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcBlockchainBlockHeaderByHash(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_HEADER_BY_HASH.getFunction();

        byte[] hashBytes = new byte[32];
        byte[] data = function.encode(hashBytes);

        if (activationConfig.isActive(ConsensusRule.RSKIP220, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getBtcBlockchainBlockHeaderByHash(Sha256Hash.wrap(hashBytes));
            }
        } else {
            // Pre RSKIP220 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcBlockchainBlockHeaderByHeight(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_HEADER_BY_HEIGHT.getFunction();

        int height = 20;
        byte[] data = function.encode(height);

        if (activationConfig.isActive(ConsensusRule.RSKIP220, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getBtcBlockchainBlockHeaderByHeight(height);
            }
        } else {
            // Pre RSKIP220 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getBtcBlockchainParentBlockHeaderByHash(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_BTC_BLOCKCHAIN_PARENT_BLOCK_HEADER_BY_HASH.getFunction();

        byte[] hashBytes = new byte[32];
        byte[] data = function.encode(hashBytes);

        if (activationConfig.isActive(ConsensusRule.RSKIP220, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getBtcBlockchainParentBlockHeaderByHash(any());
            }
        } else {
            // Pre RSKIP220 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getNextPegoutCreationBlockNumber(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_NEXT_PEGOUT_CREATION_BLOCK_NUMBER.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP271, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getNextPegoutCreationBlockNumber();
            }
        } else {
            // Pre RSKIP271 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getQueuedPegoutsCount(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_QUEUED_PEGOUTS_COUNT.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP271, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getQueuedPegoutsCount();
            }
        } else {
            // Pre RSKIP271 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getEstimatedFeesForNextPegoutEvent(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Coin estimatedFeesForNextPegout = Coin.SATOSHI;
        when(bridgeSupportMock.getEstimatedFeesForNextPegOutEvent()).thenReturn(estimatedFeesForNextPegout);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_ESTIMATED_FEES_FOR_NEXT_PEGOUT_EVENT.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP271, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP417, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getEstimatedFeesForNextPegOutEvent();
            }
        } else {
            // Pre RSKIP271 this method is not enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    @Nested
    @Tag("unionBridge")
    class UnionBridgeTest {
        private static final ActivationConfig allActivations = ActivationConfigsForTest.all();

        private static final Constants testnetConstants = Constants.testnet2(allActivations);
        private static final Constants mainNetConstants = Constants.mainnet();

        private static final UnionBridgeConstants unionBridgeMainNetConstants = mainNetConstants.getBridgeConstants()
            .getUnionBridgeConstants();

        private static final AddressBasedAuthorizer testnetChangeUnionBridgeAuthorizer = testnetConstants.getBridgeConstants()
            .getUnionBridgeConstants().getChangeUnionBridgeContractAddressAuthorizer();

        private static final co.rsk.core.Coin initialLockingCap = unionBridgeMainNetConstants.getInitialLockingCap();
        private static final co.rsk.core.Coin newLockingCap = initialLockingCap.multiply(
            BigInteger.valueOf(unionBridgeMainNetConstants.getLockingCapIncrementsMultiplier()));

        private static final RskAddress unionBridgeAddress = unionBridgeMainNetConstants.getAddress();

        private static final RskAddress changeTestnetUnionAddressAuthorizer = new RskAddress("54fdb399cf235c9b0d464ab4055af9251883bbfe");

        private static final RskAddress increaseLockingCapAuthorizerMainnet = ZERO_ADDRESS;
        private static final RskAddress setTransferPermissionsAuthorizerMainnet = ZERO_ADDRESS;

        private static final RskAddress increaseLockingCapAuthorizerTestnet = new RskAddress("1a8109af0f019ED3045Fbcdf45E5e90d6b6AAfaF");
        private static final RskAddress setTransferPermissionsAuthorizerTestnet = new RskAddress("8db1F83E8119E4Dce5bC708ec2f4390FFd910B19");

        private static final RskAddress unauthorizedCaller = new RskAddress("0000000000000000000000000000000000000001");
        private static final byte[] superEvent = new byte []{(byte) 0x123456};
        private static final byte[] baseEvent = new byte []{(byte) 0x123456};

        private UnionBridgeSupport unionBridgeSupport;
        private Repository repository;
        private BridgeEventLogger eventLogger;
        private BridgeSupport bridgeSupport;
        private Bridge bridge;
        private Transaction rskTx;

        private BridgeMethods.BridgeMethodExecutor decorate;
        private AuthorizerProvider authorizerProvider;
        private Bridge bridgeForTestnet;

        @BeforeEach
        void setUp() {
            unionBridgeSupport = mock(UnionBridgeSupport.class);

            eventLogger = mock(BridgeEventLogger.class);
            repository = mock(Repository.class);
            bridgeSupport = BridgeSupportBuilder.builder()
                .withEventLogger(eventLogger)
                .withRepository(repository)
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            rskTx = mock(Transaction.class);

            bridge = bridgeBuilder
                .activationConfig(allActivations)
                .constants(mainNetConstants)
                .bridgeSupport(bridgeSupport)
                .transaction(rskTx)
                .build();

            when(rskTx.getSender(any())).thenReturn(unionBridgeAddress);
            authorizerProvider = mock(AuthorizerProvider.class);
            decorate = mock(BridgeMethods.BridgeMethodExecutor.class);

            bridgeForTestnet = bridgeBuilder
                .transaction(rskTx)
                .constants(testnetConstants)
                .activationConfig(allActivations)
                .bridgeSupport(bridgeSupport)
                .build();
        }

        @Test
        void executeIfAuthorized_whenAuthorized_shouldExecute() throws Exception {
            // Arrange
            when(rskTx.getSender(any())).thenReturn(changeTestnetUnionAddressAuthorizer);
            when(authorizerProvider.provide(any())).thenReturn(testnetChangeUnionBridgeAuthorizer);

            // Act
            BridgeMethods.BridgeMethodExecutor executor = Bridge.executeIfAuthorized(
                authorizerProvider,
                decorate,
                "setUnionBridgeContractAddressForTestnet"
            );
            executor.execute(bridge, null);

            // Assert
            verify(decorate, times(1)).execute(any(), any());
        }

        @Test
        void executeIfAuthorized_whenUnauthorized_shouldThrowVMException() throws Exception {
            // Arrange
            when(rskTx.getSender(any())).thenReturn(unauthorizedCaller);
            when(authorizerProvider.provide(any())).thenReturn(unionBridgeMainNetConstants.getChangeUnionBridgeContractAddressAuthorizer());

            // Act
            BridgeMethods.BridgeMethodExecutor executor = Bridge.executeIfAuthorized(
                authorizerProvider,
                decorate,
                "setUnionBridgeContractAddressForTestnet"
            );
            VMException actualException = assertThrows(
                VMException.class,
                () -> executor.execute(bridge, null)
            );

            // Assert
            assertTrue(actualException.getMessage().contains("The sender is not authorized to call setUnionBridgeContractAddressForTestnet"));
            verify(decorate, never()).execute(any(), any());
        }

        @Test
        void executeIfTestnetAndAuthorized_whenTestnetAndAuthorized_shouldExecute() throws Exception {
            // Arrange
            when(rskTx.getSender(any())).thenReturn(changeTestnetUnionAddressAuthorizer);
            when(authorizerProvider.provide(any())).thenReturn(testnetChangeUnionBridgeAuthorizer);

            // Act
            BridgeMethods.BridgeMethodExecutor executor = Bridge.executeIfTestnetAndAuthorized(
                authorizerProvider,
                decorate,
                "setUnionBridgeContractAddressForTestnet"
            );
            executor.execute(bridgeForTestnet, null);

            // Assert
            verify(decorate, times(1)).execute(any(), any());
        }

        @Test
        void executeIfTestnet_shouldThrowVMException() throws Exception {
            // Arrange
            when(rskTx.getSender(any())).thenReturn(RskAddress.ZERO_ADDRESS);
            when(authorizerProvider.provide(any())).thenReturn(unionBridgeMainNetConstants.getChangeLockingCapAuthorizer());

            // Act
            BridgeMethods.BridgeMethodExecutor executor = Bridge.executeIfTestnetAndAuthorized(
                authorizerProvider,
                decorate,
                "setUnionBridgeContractAddressForTestnet"
            );
            VMException actualException = assertThrows(
                VMException.class,
                () -> executor.execute(bridge, null)
            );

            // Assert
            assertTrue(actualException.getMessage().contains("The setUnionBridgeContractAddressForTestnet function is disabled in Mainnet."));
            verify(decorate, never()).execute(any(), any());
        }

        @Test
        void executeIfTestnetAndAuthorized_whenTestnetButUnauthorized_shouldThrowVMException() throws Exception {
            // Arrange
            when(rskTx.getSender(any())).thenReturn(unauthorizedCaller);

            when(authorizerProvider.provide(any())).thenReturn(testnetChangeUnionBridgeAuthorizer);

            // Act
            BridgeMethods.BridgeMethodExecutor executor = Bridge.executeIfTestnetAndAuthorized(
                authorizerProvider,
                decorate,
                "setUnionBridgeContractAddressForTestnet"
            );
            VMException actualException = assertThrows(VMException.class,
                () -> executor.execute(bridgeForTestnet, null));

            // Assert
            assertTrue(actualException.getMessage().contains("The sender is not authorized to call setUnionBridgeContractAddressForTestnet"));
            verify(decorate, never()).execute(any(), any());
        }

        @Test
        void executeIfTestnetAndUnauthorized_shouldThrowVMException() throws Exception {
            // Arrange
            when(rskTx.getSender(any())).thenReturn(unauthorizedCaller);
            when(authorizerProvider.provide(any())).thenReturn(unionBridgeMainNetConstants.getChangeLockingCapAuthorizer());

            // Act
            BridgeMethods.BridgeMethodExecutor executor = Bridge.executeIfTestnetAndAuthorized(
                authorizerProvider,
                decorate,
                "setUnionBridgeContractAddressForTestnet"
            );
            VMException actualException = assertThrows(
                VMException.class,
                () -> executor.execute(bridge, null)
            );

            // Assert
            assertTrue(actualException.getMessage().contains("The setUnionBridgeContractAddressForTestnet function is disabled in Mainnet."));
            verify(decorate, never()).execute(any(), any());
        }

        @Test
        void setUnionBridgeContractAddressForTestnet_beforeRSKIP502_shouldFail() {
            ActivationConfig activationConfig = ActivationConfigsForTest.lovell700();
            bridge = bridgeBuilder
                .constants(Constants.testnet2(activationConfig))
                .activationConfig(activationConfig)
                .build();
            when(rskTx.getSender(any())).thenReturn(changeTestnetUnionAddressAuthorizer);

            CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.getFunction();
            byte[] data = function.encode(TestUtils.generateAddress("unionBridgeContractAddress").toHexString());

            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void setUnionBridgeContractAddressForTestnet_afterRSKIP502_shouldSetNewAddress() throws VMException {
            RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress("newUnionBridgeContractAddress");
            when(unionBridgeSupport.setUnionBridgeContractAddressForTestnet(newUnionBridgeContractAddress)).thenReturn(UnionResponseCode.SUCCESS);
            bridge = bridgeBuilder
                .activationConfig(allActivations)
                .bridgeSupport(bridgeSupport)
                .constants(testnetConstants)
                .build();
            when(rskTx.getSender(any())).thenReturn(changeTestnetUnionAddressAuthorizer);

            CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.getFunction();
            byte[] data = function.encode(newUnionBridgeContractAddress.toHexString());

            byte[] result = bridge.execute(data);
            BigInteger decodedResult = (BigInteger) Bridge.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.decodeResult(result)[0];

            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);
        }

        @Test
        void setUnionBridgeContractAddressForTestnet_whenUnauthorized_shouldThrowVMException() {
            RskAddress newUnionBridgeContractAddress = TestUtils.generateAddress("newUnionBridgeContractAddress");
            when(rskTx.getSender(any())).thenReturn(unauthorizedCaller);

            CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.getFunction();
            byte[] data = function.encode(newUnionBridgeContractAddress.toHexString());

            VMException actualException = assertThrows(VMException.class, () -> bridgeForTestnet.execute(data));
            assertTrue(actualException.getMessage().contains("The sender is not authorized to call setUnionBridgeContractAddressForTestnet"));
            verify(unionBridgeSupport, never()).setUnionBridgeContractAddressForTestnet(any());
        }

        @Test
        void setUnionBridgeContractAddressForTestnet_whenMainnet_shouldThrowVMException() {
            bridge = bridgeBuilder
                .activationConfig(allActivations)
                .bridgeSupport(bridgeSupport)
                .constants(Constants.mainnet())
                .build();

            CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.getFunction();
            byte[] data = function.encode(TestUtils.generateAddress("unionBridgeContractAddress").toHexString());
            VMException actualException = assertThrows(VMException.class, () -> bridge.execute(data));

            assertTrue(actualException.getMessage().contains("The setUnionBridgeContractAddressForTestnet function is disabled in Mainnet"));
            verify(unionBridgeSupport, never()).setUnionBridgeContractAddressForTestnet(any());
        }

        @Test
        void setUnionBridgeContractAddressForTestnet_afterRSKIP502_emptyArgument_shouldSuccess() throws VMException {
            UnionResponseCode expectedUnionResponseCode = UnionResponseCode.SUCCESS;
            when(unionBridgeSupport.setUnionBridgeContractAddressForTestnet(any())).thenReturn(expectedUnionResponseCode);

            when(rskTx.getSender(any())).thenReturn(changeTestnetUnionAddressAuthorizer);

            CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.getFunction();
            byte[] data = function.encode();

            byte[] result = bridgeForTestnet.execute(data);
            BigInteger decodedResult = (BigInteger) Bridge.SET_UNION_BRIDGE_CONTRACT_ADDRESS_FOR_TESTNET.decodeResult(result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(expectedUnionResponseCode.getCode(), actualUnionResponseCode);
        }

        @Test
        void getUnionBridgeContractAddress_beforeRSKIP502_shouldFail() {
            bridge = bridgeBuilder
                .activationConfig(ActivationConfigsForTest.lovell700())
                .build();

            CallTransaction.Function getUnionBridgeContractAddressFunction = Bridge.GET_UNION_BRIDGE_CONTRACT_ADDRESS;
            byte[] data = getUnionBridgeContractAddressFunction.encode();

            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        private static Stream<Arguments> constantsProvider() {
            return Stream.of(
                Arguments.of(Constants.regtest()),
                Arguments.of(Constants.testnet2(ActivationConfigsForTest.all())),
                Arguments.of(Constants.mainnet())
            );
        }

        private static Stream<Arguments> testnetAndMainnetConstantsProvider() {
            return Stream.of(
                Arguments.of(Constants.testnet2(ActivationConfigsForTest.all())),
                Arguments.of(Constants.mainnet())
            );
        }

        @ParameterizedTest()
        @MethodSource("constantsProvider")
        void getUnionBridgeContractAddress_afterRSKIP502_shouldReturnAddress(Constants constants) throws VMException {
            // Arrange
            RskAddress expectedAddress = constants.getBridgeConstants().getUnionBridgeConstants().getAddress();
            when(unionBridgeSupport.getUnionBridgeContractAddress()).thenReturn(expectedAddress);

            byte[] data = Bridge.GET_UNION_BRIDGE_CONTRACT_ADDRESS.encode();

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            RskAddress actualAddress = new RskAddress((DataWord) Bridge.GET_UNION_BRIDGE_CONTRACT_ADDRESS.decodeResult(result)[0]);
            assertEquals(expectedAddress, actualAddress);
        }

        @Test
        void getUnionBridgeLockingCap_beforeRSKIP502_shouldFail() {
            bridge = bridgeBuilder
                .activationConfig(ActivationConfigsForTest.lovell700())
                .build();

            CallTransaction.Function getUnionBridgeLockingCapFunction = Bridge.GET_UNION_BRIDGE_LOCKING_CAP;
            byte[] data = getUnionBridgeLockingCapFunction.encode();

            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @ParameterizedTest()
        @MethodSource("constantsProvider")
        void getUnionBridgeLockingCap_afterRSKIP502_shouldReturnLockingCap(Constants constants)
            throws VMException {
            // Arrange
            co.rsk.core.Coin expectedLockingCap = constants.getBridgeConstants().getUnionBridgeConstants()
                .getInitialLockingCap();
            when(unionBridgeSupport.getLockingCap()).thenReturn(expectedLockingCap);

            byte[] data = Bridge.GET_UNION_BRIDGE_LOCKING_CAP.encode();

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger)Bridge.GET_UNION_BRIDGE_LOCKING_CAP.decodeResult(result)[0];
            co.rsk.core.Coin actualLockingCap = new co.rsk.core.Coin(decodedResult);
            assertEquals(expectedLockingCap, actualLockingCap);
        }

        @ParameterizedTest()
        @MethodSource("testnetAndMainnetConstantsProvider")
        void increaseUnionBridgeLockingCap_beforeRSKIP502_shouldFail(Constants constants) {
            bridge = bridgeBuilder
                .activationConfig(ActivationConfigsForTest.lovell700())
                .constants(constants)
                .build();
            setupIncreaseLockingCapAuthorizer(constants);

            byte[] data = Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.encode(newLockingCap.asBigInteger());

            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @ParameterizedTest()
        @MethodSource("testnetAndMainnetConstantsProvider")
        void increaseUnionBridgeLockingCap_afterRSKIP502_whenMeetRequirements_shouldReturnSuccess(Constants constants)
            throws VMException {
            // Arrange
            bridge = bridgeBuilder
                .constants(constants)
                .build();
            setupIncreaseLockingCapAuthorizer(constants);

            UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;
            when(bridgeSupport.increaseUnionBridgeLockingCap(any(), any())).thenReturn(expectedResponseCode);

            byte[] data = Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.encode(newLockingCap.asBigInteger());

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.decodeResult(
                result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(UnionResponseCode.SUCCESS.getCode(), actualUnionResponseCode);
        }

        private void setupIncreaseLockingCapAuthorizer(Constants constants) {
            boolean isMainnet = constants.getChainId() == Constants.MAINNET_CHAIN_ID;
            RskAddress increaseLockingCapAuthorizer = isMainnet ? increaseLockingCapAuthorizerMainnet : increaseLockingCapAuthorizerTestnet;
            when(rskTx.getSender(any())).thenReturn(increaseLockingCapAuthorizer);
        }

        @ParameterizedTest()
        @MethodSource("testnetAndMainnetConstantsProvider")
        void increaseUnionBridgeLockingCap_afterRSKIP502_whenNewLockingCapSurpassingMaxIncrement_shouldReturnInvalidLockingCapCode(Constants constants)
            throws VMException {
            // Arrange
            bridge = bridgeBuilder
                .constants(constants)
                .build();
            setupIncreaseLockingCapAuthorizer(constants);

            UnionResponseCode expectedResponseCode = UnionResponseCode.INVALID_VALUE;
            when(bridgeSupport.increaseUnionBridgeLockingCap(any(), any())).thenReturn(
                expectedResponseCode);

            co.rsk.core.Coin lockingCapAboveMaxIncrementAllowed = newLockingCap.add(co.rsk.core.Coin.valueOf(1));
            byte[] data = Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.encode(lockingCapAboveMaxIncrementAllowed.asBigInteger());

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.decodeResult(
                result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);
        }

        @ParameterizedTest()
        @MethodSource("testnetAndMainnetConstantsProvider")
        void increaseUnionBridgeLockingCap_afterRSKIP502_whenSendingZero_shouldReturnInvalidLockingCapCode(Constants constants)
            throws VMException {
            // Arrange
            bridge = bridgeBuilder
                .constants(constants)
                .build();
            setupIncreaseLockingCapAuthorizer(constants);

            UnionResponseCode expectedResponseCode = UnionResponseCode.INVALID_VALUE;
            when(bridgeSupport.increaseUnionBridgeLockingCap(any(), any())).thenReturn(
                expectedResponseCode);

            // New locking cap is zero
            byte[] data = Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.encode(0);

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.decodeResult(
                result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);
        }

        @Test
        void increaseUnionBridgeLockingCap_afterRSKIP502_whenUnauthorized_shouldThrowVmException() {
            // Arrange
            when(rskTx.getSender(any())).thenReturn(unauthorizedCaller);

            byte[] data = Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.encode(newLockingCap.asBigInteger());

            // Act
            VMException vmException = assertThrows(VMException.class, () -> bridge.execute(data));
            assertInstanceOf(VMException.class, vmException.getCause());
            assertTrue(vmException.getMessage().contains("The sender is not authorized to call increaseUnionBridgeLockingCap"));

            verify(unionBridgeSupport, never()).increaseLockingCap(any(), any());
        }

        @ParameterizedTest()
        @MethodSource("testnetAndMainnetConstantsProvider")
        void increaseUnionBridgeLockingCap_afterRSKIP502_emptyArgument_shouldFail(Constants constants) throws VMException {
            // Arrange
            bridge = bridgeBuilder
                .constants(constants)
                .build();
            setupIncreaseLockingCapAuthorizer(constants);

            UnionResponseCode expectedResponseCode = UnionResponseCode.INVALID_VALUE;

            // when no argument is passed, the default value assigned to the arg is a big integer of zero
            when(bridgeSupport.increaseUnionBridgeLockingCap(any(), eq(co.rsk.core.Coin.ZERO))).thenReturn(expectedResponseCode);

            CallTransaction.Function function = BridgeMethods.INCREASE_UNION_BRIDGE_LOCKING_CAP.getFunction();
            byte[] data = function.encode();

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.INCREASE_UNION_BRIDGE_LOCKING_CAP.decodeResult(result)[0];
            int actualUnionResponseCode = decodedResult.intValue();

            assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);
        }

        @Test
        void requestUnionBridgeRbtc_beforeRSKIP502_shouldFail() {
            bridge = bridgeBuilder.activationConfig(ActivationConfigsForTest.lovell700()).build();

            CallTransaction.Function function = BridgeMethods.REQUEST_UNION_BRIDGE_RBTC.getFunction();
            byte[] data = function.encode();

            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void requestUnionBridgeRbtc_shouldTransferredRbtc() throws VMException {
            // Arrange
            UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;
            RskAddress unionBridgeContractAddress = mainNetConstants.getBridgeConstants()
                .getUnionBridgeConstants().getAddress();
            when(unionBridgeSupport.getUnionBridgeContractAddress()).thenReturn(
                unionBridgeContractAddress);
            when(unionBridgeSupport.requestUnionRbtc(any(), any())).thenReturn(
                expectedResponseCode);

            CallTransaction.Function function = BridgeMethods.REQUEST_UNION_BRIDGE_RBTC.getFunction();

            BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
            co.rsk.core.Coin amountToRequest = new co.rsk.core.Coin(oneEth);
            byte[] data = function.encode(amountToRequest.asBigInteger());

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.REQUEST_UNION_BRIDGE_RBTC.decodeResult(
                result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);
            verify(unionBridgeSupport).requestUnionRbtc(rskTx, amountToRequest);
            verify(eventLogger).logUnionRbtcRequested(unionBridgeContractAddress, amountToRequest);
            verify(repository).transfer(BRIDGE_ADDR, unionBridgeContractAddress, amountToRequest);
        }

        @ParameterizedTest
        @EnumSource(value = UnionResponseCode.class, names = {"INVALID_VALUE", "REQUEST_DISABLED", "UNAUTHORIZED_CALLER"})
        void requestUnionBridgeRbtc_whenFail_shouldReturnErrorResponseCode(UnionResponseCode expectedResponseCode)
            throws VMException {
            // Arrange
            when(unionBridgeSupport.requestUnionRbtc(any(), any())).thenReturn(
                expectedResponseCode);

            CallTransaction.Function function = BridgeMethods.REQUEST_UNION_BRIDGE_RBTC.getFunction();
            BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
            co.rsk.core.Coin amountToRequest = new co.rsk.core.Coin(oneEth);
            byte[] data = function.encode(amountToRequest.asBigInteger());

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.REQUEST_UNION_BRIDGE_RBTC.decodeResult(
                result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);
            verify(unionBridgeSupport, times(1)).requestUnionRbtc(rskTx, amountToRequest);
            verify(repository, never()).transfer(eq(BRIDGE_ADDR), any(RskAddress.class),
                any(co.rsk.core.Coin.class));
            verify(eventLogger, never()).logUnionRbtcRequested(any(RskAddress.class), any(co.rsk.core.Coin.class));
        }

        @Test
        void requestUnionBridgeRbtc_emptyArgument_shouldReturnInvalidValue() throws VMException {
            // Arrange
            UnionResponseCode expectedResponseCode = UnionResponseCode.INVALID_VALUE;
            // when no argument is passed, the default value assigned to the arg is zero
            when(unionBridgeSupport.requestUnionRbtc(any(), eq(co.rsk.core.Coin.ZERO))).thenReturn(
                expectedResponseCode);

            CallTransaction.Function function = BridgeMethods.REQUEST_UNION_BRIDGE_RBTC.getFunction();
            byte[] data = function.encode();

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.REQUEST_UNION_BRIDGE_RBTC.decodeResult(result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);
        }

        @Test
        void releaseUnionBridgeRbtc_beforeRSKIP502_shouldFail() {
            bridge = bridgeBuilder
                .activationConfig(ActivationConfigsForTest.lovell700())
                .build();

            CallTransaction.Function function = BridgeMethods.RELEASE_UNION_BRIDGE_RBTC.getFunction();
            byte[] data = function.encode();

            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void releaseUnionBridgeRbtc_afterRSKIP502_shouldReleaseRbtc() throws VMException {
            // Arrange
            UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;
            when(unionBridgeSupport.releaseUnionRbtc(any())).thenReturn(expectedResponseCode);

            when(rskTx.getSender(any())).thenReturn(unionBridgeMainNetConstants.getAddress());

            BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1_000_000_000_000_000_000 wei
            co.rsk.core.Coin amountToRelease = new co.rsk.core.Coin(oneEth.multiply(BigInteger.TEN));
            when(rskTx.getValue()).thenReturn(amountToRelease);

            CallTransaction.Function function = BridgeMethods.RELEASE_UNION_BRIDGE_RBTC.getFunction();
            byte[] data = function.encode();

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.RELEASE_UNION_BRIDGE_RBTC.decodeResult(
                result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);
            verify(unionBridgeSupport, times(1)).releaseUnionRbtc(rskTx);
            verify(repository, never()).transfer(any(), any(), any());
        }

        @ParameterizedTest
        @EnumSource(value = UnionResponseCode.class, names = {"INVALID_VALUE", "UNAUTHORIZED_CALLER", "RELEASE_DISABLED"})
        void releaseUnionBridgeRbtc_afterRSKIP502_whenFail_shouldReturnErrorResponseCode(UnionResponseCode expectedResponseCode)
            throws VMException {
            // Arrange
            when(unionBridgeSupport.releaseUnionRbtc(any())).thenReturn(expectedResponseCode);

            RskAddress unionBridgeMainNetConstantsAddress = unionBridgeMainNetConstants.getAddress();
            when(rskTx.getSender(any())).thenReturn(unionBridgeMainNetConstantsAddress);

            BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1_000_000_000_000_000_000 wei
            co.rsk.core.Coin amountToRelease = new co.rsk.core.Coin(oneEth.multiply(BigInteger.TEN));
            when(rskTx.getValue()).thenReturn(amountToRelease);

            CallTransaction.Function function = BridgeMethods.RELEASE_UNION_BRIDGE_RBTC.getFunction();
            byte[] data = function.encode();

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.RELEASE_UNION_BRIDGE_RBTC.decodeResult(
                result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);

            verify(unionBridgeSupport, times(1)).releaseUnionRbtc(rskTx);
            verify(repository, times(1)).transfer(BRIDGE_ADDR, unionBridgeMainNetConstantsAddress, amountToRelease);
        }

        @ParameterizedTest()
        @MethodSource("testnetAndMainnetConstantsProvider")
        void setUnionBridgeTransferPermissions_beforeRSKIP502_shouldFail(Constants constants) {
            bridge = bridgeBuilder
                .activationConfig(ActivationConfigsForTest.lovell700())
                .constants(constants)
                .build();
            setupTransferPermissionsAuthorizer(constants);

            CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
            byte[] data = function.encode(true, true);

            VMException actualException = assertThrows(VMException.class, () -> bridge.execute(data));
            assertTrue(actualException.getMessage().contains(String.format("Invalid data given: %s",
                Bytes.of(data))));
        }

        @ParameterizedTest()
        @MethodSource("testnetAndMainnetConstantsProvider")
        void setUnionBridgeTransferPermissions_whenValidArgs_shouldSetTransferPermissions(Constants constants) throws VMException {
            // Arrange
            bridge = bridgeBuilder
                .constants(constants)
                .build();
            setupTransferPermissionsAuthorizer(constants);

            UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;

            when(unionBridgeSupport.setTransferPermissions(any(), anyBoolean(),
                anyBoolean())).thenReturn(expectedResponseCode);
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

        private void setupTransferPermissionsAuthorizer(Constants constants) {
            boolean isMainnet = constants.getChainId() == Constants.MAINNET_CHAIN_ID;
            RskAddress setTransferPermissionsAuthorizer = isMainnet ? setTransferPermissionsAuthorizerMainnet : setTransferPermissionsAuthorizerTestnet;
            when(rskTx.getSender(any())).thenReturn(setTransferPermissionsAuthorizer);
        }

        @ParameterizedTest()
        @MethodSource("testnetAndMainnetConstantsProvider")
        void setUnionBridgeTransferPermissions_whenEmptyArguments_shouldAssumeArgAsFalseAndProcessed(Constants constants) throws VMException {
            // Arrange
            bridge = bridgeBuilder
                .constants(constants)
                .build();
            setupTransferPermissionsAuthorizer(constants);

            UnionResponseCode expectedResponseCode = UnionResponseCode.SUCCESS;
            // when no argument is passed, the default value assigned to the arg is false
            when(unionBridgeSupport.setTransferPermissions(any(), eq(false), eq(false))).thenReturn(expectedResponseCode);

            CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
            byte[] data = function.encode();

            // Act
            byte[] result = bridge.execute(data);

            // Assert
            BigInteger decodedResult = (BigInteger) Bridge.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.decodeResult(result)[0];
            int actualUnionResponseCode = decodedResult.intValue();
            assertEquals(expectedResponseCode.getCode(), actualUnionResponseCode);

            verify(unionBridgeSupport, times(1)).setTransferPermissions(any(Transaction.class), eq(false), eq(false));
        }

        @ParameterizedTest()
        @MethodSource("testnetAndMainnetConstantsProvider")
        void setUnionBridgeTransferPermissions_whenNotAuthorized_shouldReturnUnauthorizedCode(Constants constants) {
            // Arrange
            bridge = bridgeBuilder
                .constants(constants)
                .build();

            when(rskTx.getSender(any())).thenReturn(unauthorizedCaller);

            CallTransaction.Function function = BridgeMethods.SET_UNION_BRIDGE_TRANSFER_PERMISSIONS.getFunction();
            byte[] data = function.encode(true, true);

            // Act
            VMException actualException = Assertions.assertThrows(VMException.class, () -> bridge.execute(data));

            // Assert
            assertInstanceOf(VMException.class, actualException.getCause());
            verify(unionBridgeSupport, never()).setTransferPermissions(any(Transaction.class), anyBoolean(), anyBoolean());
        }

        @Test
        void getSuperEvent_preRSKIP529_shouldThrowVMException() {
            // Arrange
            ActivationConfig activationConfig = ActivationConfigsForTest.reed800();
            bridge = bridgeBuilder
                .activationConfig(activationConfig)
                .build();

            CallTransaction.Function function = BridgeMethods.GET_SUPER_EVENT.getFunction();
            byte[] data = function.encode();

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void getSuperEvent_shouldExecute() throws VMException {
            // Arrange
            CallTransaction.Function function = BridgeMethods.GET_SUPER_EVENT.getFunction();
            byte[] data = function.encode();

            // Act
            bridge.execute(data);

            // Assert
            verify(unionBridgeSupport).getSuperEvent();
        }

        @Test
        void setSuperEvent_preRSKIP529_shouldThrowVMException() {
            // Arrange
            ActivationConfig activationConfig = ActivationConfigsForTest.reed800();
            bridge = bridgeBuilder
                .activationConfig(activationConfig)
                .build();

            CallTransaction.Function function = BridgeMethods.SET_SUPER_EVENT.getFunction();
            byte[] data = function.encode(superEvent);

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void setSuperEvent_shouldExecuteData() throws VMException {
            // Arrange
            CallTransaction.Function function = BridgeMethods.SET_SUPER_EVENT.getFunction();
            byte[] data = function.encode(superEvent);

            // Act
            bridge.execute(data);

            // Assert
            verify(unionBridgeSupport).setSuperEvent(rskTx, superEvent);
        }

        @Test
        void setSuperEvent_unionBridgeSupportThrowsIAE_shouldThrowVMException() {
            // Arrange
            CallTransaction.Function function = BridgeMethods.SET_SUPER_EVENT.getFunction();
            byte[] data = function.encode(superEvent);

            doThrow(new IllegalArgumentException())
                .when(unionBridgeSupport)
                .setSuperEvent(rskTx, superEvent);

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void clearSuperEvent_preRSKIP529_shouldThrowVMException() {
            // Arrange
            ActivationConfig activationConfig = ActivationConfigsForTest.reed800();
            bridge = bridgeBuilder
                .activationConfig(activationConfig)
                .build();

            CallTransaction.Function function = BridgeMethods.CLEAR_SUPER_EVENT.getFunction();
            byte[] data = function.encode();

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void clearSuperEvent_shouldExecute() throws VMException {
            // Arrange
            CallTransaction.Function function = BridgeMethods.CLEAR_SUPER_EVENT.getFunction();
            byte[] data = function.encode();

            // Act
            bridge.execute(data);

            // Assert
            verify(unionBridgeSupport).clearSuperEvent(rskTx);
        }

        @Test
        void clearSuperEvent_unionBridgeSupportThrowsIAE_shouldThrowVMException() {
            // Arrange
            CallTransaction.Function function = BridgeMethods.CLEAR_SUPER_EVENT.getFunction();
            byte[] data = function.encode();

            doThrow(new IllegalArgumentException())
                .when(unionBridgeSupport)
                .clearSuperEvent(rskTx);

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void getBaseEvent_preRSKIP529_shouldThrowVMException() {
            // Arrange
            ActivationConfig activationConfig = ActivationConfigsForTest.reed800();
            bridge = bridgeBuilder
                .activationConfig(activationConfig)
                .build();

            CallTransaction.Function function = BridgeMethods.GET_BASE_EVENT.getFunction();
            byte[] data = function.encode();

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void getBaseEvent_shouldExecute() throws VMException {
            // Arrange
            CallTransaction.Function function = BridgeMethods.GET_BASE_EVENT.getFunction();
            byte[] data = function.encode();

            // Act
            bridge.execute(data);

            // Assert
            verify(unionBridgeSupport).getBaseEvent();
        }

        @Test
        void setBaseEvent_preRSKIP529_shouldThrowVMException() {
            // Arrange
            ActivationConfig activationConfig = ActivationConfigsForTest.reed800();
            bridge = bridgeBuilder
                .activationConfig(activationConfig)
                .build();

            CallTransaction.Function function = BridgeMethods.SET_BASE_EVENT.getFunction();
            byte[] data = function.encode(baseEvent);

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void setBaseEvent_shouldExecuteData() throws VMException {
            // Arrange
            CallTransaction.Function function = BridgeMethods.SET_BASE_EVENT.getFunction();
            byte[] data = function.encode(baseEvent);

            // Act
            bridge.execute(data);

            // Assert
            verify(unionBridgeSupport).setBaseEvent(rskTx, baseEvent);
        }

        @Test
        void setBaseEvent_unionBridgeSupportThrowsIAE_shouldThrowVMException() {
            // Arrange
            CallTransaction.Function function = BridgeMethods.SET_BASE_EVENT.getFunction();
            byte[] data = function.encode(baseEvent);

            doThrow(new IllegalArgumentException())
                .when(unionBridgeSupport)
                .setBaseEvent(rskTx, baseEvent);

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void clearBaseEvent_preRSKIP529_shouldThrowVMException() {
            // Arrange
            ActivationConfig activationConfig = ActivationConfigsForTest.reed800();
            bridge = bridgeBuilder
                .activationConfig(activationConfig)
                .build();

            CallTransaction.Function function = BridgeMethods.CLEAR_BASE_EVENT.getFunction();
            byte[] data = function.encode();

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }

        @Test
        void clearBaseEvent_shouldExecute() throws VMException {
            // Arrange
            CallTransaction.Function function = BridgeMethods.CLEAR_BASE_EVENT.getFunction();
            byte[] data = function.encode();

            // Act
            bridge.execute(data);

            // Assert
            verify(unionBridgeSupport).clearBaseEvent(rskTx);
        }

        @Test
        void clearBaseEvent_unionBridgeSupportThrowsIAE_shouldThrowVMException() {
            // Arrange
            CallTransaction.Function function = BridgeMethods.CLEAR_BASE_EVENT.getFunction();
            byte[] data = function.encode();

            doThrow(new IllegalArgumentException())
                .when(unionBridgeSupport)
                .clearBaseEvent(rskTx);

            // Act & Assert
            assertThrows(VMException.class, () -> bridge.execute(data));
        }
    }

    private static Stream<Arguments> msgTypesAndActivations() {
        List<Arguments> argumentsList = new ArrayList<>();
        List<ActivationConfig> activationConfigs = Arrays.asList(
            ActivationConfigsForTest.orchid(),
            ActivationConfigsForTest.wasabi100(),
            ActivationConfigsForTest.papyrus200(),
            ActivationConfigsForTest.iris300(),
            ActivationConfigsForTest.hop400(),
            ActivationConfigsForTest.fingerroot500(),
            ActivationConfigsForTest.arrowhead600(),
            ActivationConfigsForTest.lovell700()
        );

        for (MessageCall.MsgType msgType : MessageCall.MsgType.values()) {
            for (ActivationConfig activationConfig : activationConfigs) {
                argumentsList.add(Arguments.of(msgType, activationConfig));
            }
        }

        return argumentsList.stream();
    }

    private void assertVoidMethodResult(ActivationConfig activationConfig, byte[] result) {
        if (activationConfig.isActive(ConsensusRule.RSKIP417, 0)) {
            assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, result);
        } else {
            assertNull(result);
        }
    }
}
