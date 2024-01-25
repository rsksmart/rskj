package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.test.builders.BridgeBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.MessageCall;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static co.rsk.peg.PegTestUtils.createHash3;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class BridgeMethodsTest {

    private NetworkParameters networkParameters;
    private BridgeBuilder bridgeBuilder;

    @BeforeEach
    void resetConfigToMainnet() {
        networkParameters = BridgeMainNetConstants.getInstance().getBtcParams();
        bridgeBuilder = new BridgeBuilder();
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

        CallTransaction.Function function = Bridge.ADD_FEDERATOR_PUBLIC_KEY;
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

        CallTransaction.Function function = Bridge.ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY;
        byte[] data = function.encode(Hex.decode(publicKey), Hex.decode(publicKey), Hex.decode(publicKey));

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
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

        CallTransaction.Function function = Bridge.ADD_LOCK_WHITELIST_ADDRESS;
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

        CallTransaction.Function function = Bridge.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS;
        byte[] data = function.encode(addressBase58, maxTransferValue);

        if (activationConfig.isActive(ConsensusRule.RSKIP87, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
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

        CallTransaction.Function function = Bridge.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS;
        byte[] data = function.encode(addressBase58);

        if (activationConfig.isActive(ConsensusRule.RSKIP87, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
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

        CallTransaction.Function function = Bridge.ADD_SIGNATURE;
        byte[] data = function.encode(Hex.decode(pegnatoryPublicKey), derEncodedSigs, rskTxHash.getBytes());

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
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

        CallTransaction.Function function = Bridge.COMMIT_FEDERATION;
        byte[] data = function.encode(commitTransactionHash.getBytes());

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
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

        CallTransaction.Function function = Bridge.CREATE_FEDERATION;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
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

        CallTransaction.Function function = Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR;
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

        CallTransaction.Function function = Bridge.GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH;
        byte[] data = function.encode(depth);

        if (activationConfig.isActive(ConsensusRule.RSKIP89, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_BTC_TRANSACTION_CONFIRMATIONS;
        byte[] data = function.encode(
            btcTxHash.getBytes(),
            btcBlockHash.getBytes(),
            merkleBranchPath,
            merkleBranchHashes.stream().map(Sha256Hash::getBytes).toArray()
        );

        if (activationConfig.isActive(ConsensusRule.RSKIP122, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_BTC_TX_HASH_PROCESSED_HEIGHT;
        byte[] data = function.encode(btcTxHash.toString());

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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
        when(bridgeSupportMock.getFederationAddress()).thenReturn(federationAddress);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = Bridge.GET_FEDERATION_ADDRESS;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getFederationAddress();
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

        CallTransaction.Function function = Bridge.GET_FEDERATION_CREATION_BLOCK_NUMBER;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getFederationCreationBlockNumber();
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFederationCreationTime(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochSecond(100_000L));
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = Bridge.GET_FEDERATION_CREATION_TIME;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getFederationCreationTime();
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

        CallTransaction.Function function = Bridge.GET_FEDERATION_SIZE;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getFederationSize();
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

        CallTransaction.Function function = Bridge.GET_FEDERATION_THRESHOLD;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getFederationThreshold();
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

        CallTransaction.Function function = Bridge.GET_FEDERATOR_PUBLIC_KEY;
        byte[] data = function.encode(federatorIndex);

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            // Post RSKIP123 this method is no longer enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getFederatorPublicKey(federatorIndex);
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void getFederatorPublicKeyOfType(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException {
        Transaction rskTxMock = mock(Transaction.class);
        doReturn(true).when(rskTxMock).isLocalCallTransaction();

        int federatorIndex = 1;
        FederationMember.KeyType keyType = FederationMember.KeyType.BTC;
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = Bridge.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE;
        byte[] data = function.encode(federatorIndex, keyType.getValue());

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getFederatorPublicKeyOfType(
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

        CallTransaction.Function function = Bridge.GET_FEE_PER_KB;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_LOCK_WHITELIST_ADDRESS;
        byte[] data = function.encode(index);

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS;
        byte[] data = function.encode(addressBase58);

        if (activationConfig.isActive(ConsensusRule.RSKIP87, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_LOCK_WHITELIST_SIZE;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_MINIMUM_LOCK_TX_VALUE;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_PENDING_FEDERATION_HASH;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_PENDING_FEDERATION_SIZE;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_PENDING_FEDERATOR_PUBLIC_KEY;

        int federatorIndex = 1;
        byte[] data = function.encode(federatorIndex);

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            // Post RSKIP123 this method is no longer enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getPendingFederatorPublicKey(federatorIndex);
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

        CallTransaction.Function function = Bridge.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE;

        int federatorIndex = 1;
        FederationMember.KeyType keyType = FederationMember.KeyType.BTC;
        byte[] data = function.encode(federatorIndex, keyType.getValue());

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_RETIRING_FEDERATION_ADDRESS;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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
        when(bridgeSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochSecond(100_000L));
        Bridge bridge = bridgeBuilder
            .transaction(rskTxMock)
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = Bridge.GET_RETIRING_FEDERATION_CREATION_TIME;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
            // Post arrowhead should fail for any msg type != CALL or STATIC CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getRetiringFederationCreationTime();
        }
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

        CallTransaction.Function function = Bridge.GET_RETIRING_FEDERATION_SIZE;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_RETIRING_FEDERATION_THRESHOLD;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_RETIRING_FEDERATOR_PUBLIC_KEY;
        byte[] data = function.encode(federatorIndex);

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            // Post RSKIP123 this method is no longer enabled, should fail for all message types
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).getRetiringFederatorPublicKey(federatorIndex);
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

        CallTransaction.Function function = Bridge.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE;

        int federatorIndex = 1;
        FederationMember.KeyType keyType = FederationMember.KeyType.BTC;
        byte[] data = function.encode(federatorIndex, keyType.getValue());

        if (activationConfig.isActive(ConsensusRule.RSKIP123, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_STATE_FOR_BTC_RELEASE_CLIENT;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_STATE_FOR_DEBUGGING;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_LOCKING_CAP;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP134, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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
        when(bridgeSupportMock.getActivePowpegRedeemScript()).thenReturn(Optional.of(activePowpegRedeemScript));

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = Bridge.GET_ACTIVE_POWPEG_REDEEM_SCRIPT;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP293, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
                !(msgType.equals(MessageCall.MsgType.CALL) || msgType.equals(MessageCall.MsgType.STATICCALL))) {
                // Post arrowhead should fail for any msg type != CALL or STATIC CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).getActivePowpegRedeemScript();
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
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.INCREASE_LOCKING_CAP;

        long newLockingCap = 1;
        byte[] data = function.encode(newLockingCap);

        if (activationConfig.isActive(ConsensusRule.RSKIP134, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
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

        CallTransaction.Function function = Bridge.IS_BTC_TX_HASH_ALREADY_PROCESSED;
        byte[] data = function.encode(btcTxHash.toString());

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.RECEIVE_HEADERS;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
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

        CallTransaction.Function function = Bridge.RECEIVE_HEADER;

        BtcBlock btcBlock = new BtcBlock(
            networkParameters,
            1,
            BitcoinTestUtils.createHash(1),
            BitcoinTestUtils.createHash(1),
            1,
            100L,
            1,
            new ArrayList<>()
        ).cloneAsHeader();

        byte[] serializedBlockHeader = btcBlock.bitcoinSerialize();
        byte[] data = function.encode(serializedBlockHeader);

        if (activationConfig.isActive(ConsensusRule.RSKIP200, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
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

        CallTransaction.Function function = Bridge.REGISTER_BTC_TRANSACTION;

        byte[] btcTxSerialized = new byte[]{1};
        int height = 0;
        byte[] pmtSerialized = new byte[]{2};
        byte[] data = function.encode(btcTxSerialized, height, pmtSerialized);

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
            verify(bridgeSupportMock, times(1)).registerBtcTransaction(
                any(Transaction.class), any(byte[].class), anyInt(), any(byte[].class)
            );
        }
    }

    @ParameterizedTest()
    @MethodSource("msgTypesAndActivations")
    void releaseBtc(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = Bridge.RELEASE_BTC;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
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

        CallTransaction.Function function = Bridge.REMOVE_LOCK_WHITELIST_ADDRESS;

        String addressBase58 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
        byte[] data = function.encode(addressBase58);

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.ROLLBACK_FEDERATION;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
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

        CallTransaction.Function function = Bridge.SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY;

        BigInteger disableBlockDelay = BigInteger.valueOf(100);
        byte[] data = function.encode(disableBlockDelay);

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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
    void updateCollections(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
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

        CallTransaction.Function function = Bridge.UPDATE_COLLECTIONS;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
            !msgType.equals(MessageCall.MsgType.CALL)) {
            // Post arrowhead should fail for any msg type != CALL
            assertThrows(VMException.class, () -> bridge.execute(data));
        } else {
            bridge.execute(data);
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

        CallTransaction.Function function = Bridge.VOTE_FEE_PER_KB;

        long feePerKB = 10_000;
        byte[] data = function.encode(feePerKB);

        if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.REGISTER_BTC_COINBASE_TRANSACTION;

        byte[] value = new byte[32];
        byte[] data = function.encode(value, value, value, value, value);

        if (activationConfig.isActive(ConsensusRule.RSKIP143, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
                // Post arrowhead should fail for any msg type != CALL
                assertThrows(VMException.class, () -> bridge.execute(data));
            } else {
                bridge.execute(data);
                verify(bridgeSupportMock, times(1)).registerBtcCoinbaseTransaction(
                    value, Sha256Hash.wrap(value), value, Sha256Hash.wrap(value), value);
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

        CallTransaction.Function function = Bridge.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION;

        Sha256Hash blockHash = BitcoinTestUtils.createHash(2);
        byte[] data = function.encode(blockHash.getBytes());

        if (activationConfig.isActive(ConsensusRule.RSKIP143, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.REGISTER_FAST_BRIDGE_BTC_TRANSACTION;

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
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) && !msgType.equals(MessageCall.MsgType.CALL)) {
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

        CallTransaction.Function function = Bridge.GET_BTC_BLOCKCHAIN_BEST_BLOCK_HEADER;
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP220, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_BTC_BLOCKCHAIN_BLOCK_HEADER_BY_HASH;

        byte[] hashBytes = new byte[32];
        byte[] data = function.encode(hashBytes);

        if (activationConfig.isActive(ConsensusRule.RSKIP220, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_BTC_BLOCKCHAIN_BLOCK_HEADER_BY_HEIGHT;

        int height = 20;
        byte[] data = function.encode(height);

        if (activationConfig.isActive(ConsensusRule.RSKIP220, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

        CallTransaction.Function function = Bridge.GET_BTC_BLOCKCHAIN_PARENT_BLOCK_HEADER_BY_HASH;

        byte[] hashBytes = new byte[32];
        byte[] data = function.encode(hashBytes);

        if (activationConfig.isActive(ConsensusRule.RSKIP220, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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
    void getNextPegoutCreationBlockNumber(MessageCall.MsgType msgType, ActivationConfig activationConfig) throws VMException, IOException, BlockStoreException {
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Bridge bridge = bridgeBuilder
            .activationConfig(activationConfig)
            .bridgeSupport(bridgeSupportMock)
            .msgType(msgType)
            .build();

        CallTransaction.Function function = BridgeMethods.GET_NEXT_PEGOUT_CREATION_BLOCK_NUMBER.getFunction();
        byte[] data = function.encode();

        if (activationConfig.isActive(ConsensusRule.RSKIP271, 0)) {
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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
            if (activationConfig.isActive(ConsensusRule.RSKIP_ARROWHEAD, 0) &&
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

    private static Stream<Arguments> msgTypesAndActivations() {
        List<Arguments> argumentsList = new ArrayList<>();
        List<ActivationConfig> activationConfigs = Arrays.asList(
            ActivationConfigsForTest.orchid(),
            ActivationConfigsForTest.wasabi100(),
            ActivationConfigsForTest.papyrus200(),
            ActivationConfigsForTest.iris300(),
            ActivationConfigsForTest.hop400(),
            ActivationConfigsForTest.fingerroot500(),
            ActivationConfigsForTest.arrowhead600()
        );

        for (MessageCall.MsgType msgType : MessageCall.MsgType.values()) {
            for(ActivationConfig activationConfig : activationConfigs) {
                argumentsList.add(Arguments.of(msgType, activationConfig));
            }
        }

        return argumentsList.stream();
    }
}
