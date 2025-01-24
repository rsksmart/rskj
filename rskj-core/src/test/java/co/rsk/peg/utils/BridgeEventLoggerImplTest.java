/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.peg.utils;

import static co.rsk.peg.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.flatKeysAsByteArray;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.bitcoin.*;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.pegin.RejectedPeginReason;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.core.*;
import org.ethereum.core.CallTransaction.Function;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/**
 * Test class for BridgeEventLoggerImpl.
 *
 * @author martin.medina
 */
class BridgeEventLoggerImplTest {
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final BtcTransaction BTC_TRANSACTION = new BtcTransaction(NETWORK_PARAMETERS);
    private static final RskAddress RSK_ADDRESS = new RskAddress("0x0000000000000000000000000000000000000101");
    private static final Keccak256 RSK_TX_HASH = RskTestUtils.createHash(1);

    private List<LogInfo> eventLogs;
    private BridgeEventLogger eventLogger;
    private SignatureCache signatureCache;

    @BeforeEach
    void setup() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        eventLogs = new LinkedList<>();
        eventLogger = new BridgeEventLoggerImpl(
            activations,
            eventLogs,
            signatureCache
        );
    }

    @Test
    void logLockBtc() {
        Address senderAddress = Address.fromBase58(NETWORK_PARAMETERS, "1MvSMVpP6PY6XZtxenGXoFcH575JUWkzoP");
        Coin amount = Coin.SATOSHI;

        // Act
        eventLogger.logLockBtc(RSK_ADDRESS, BTC_TRANSACTION, senderAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.LOCK_BTC.getEvent(),
            new Object[]{RSK_ADDRESS.toString()},
            new Object[]{BTC_TRANSACTION.getHash().getBytes(), senderAddress.toString(), amount.getValue()}
        );
    }

    @Test
    void logLockBtc_with_segwit_address() {
        Address senderAddress = new Address(
            NETWORK_PARAMETERS,
            NETWORK_PARAMETERS.getP2SHHeader(),
            Hex.decode("c99a8f22127007255b4a9d8d57b0892ae2103f2d")
        );
        Coin amount = Coin.SATOSHI;

        // Act
        eventLogger.logLockBtc(RSK_ADDRESS, BTC_TRANSACTION, senderAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.LOCK_BTC.getEvent(),
            new Object[]{RSK_ADDRESS.toString()},
            new Object[]{BTC_TRANSACTION.getHash().getBytes(), "3L4zu4GWVVSfAWCPbP3RqkJUvxpiiQebPX", amount.getValue()}
        );
    }

    @Test
    void logPeginBtc() {
        Coin amount = Coin.SATOSHI;
        int protocolVersion = 1;

        // Act
        eventLogger.logPeginBtc(RSK_ADDRESS, BTC_TRANSACTION, amount, protocolVersion);

        // Assert log size
        assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);
        CallTransaction.Function event = BridgeEvents.PEGIN_BTC.getEvent();

        // Assert address that made the log
        assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        assertEquals(3, logResult.getTopics().size());
        byte[][] topics = event.encodeEventTopics(RSK_ADDRESS.toString(), BTC_TRANSACTION.getHash().getBytes());
        for (int i=0; i<topics.length; i++) {
            assertArrayEquals(topics[i], logResult.getTopics().get(i).getData());
        }

        // Assert log data
        byte[] encodedData = event.encodeEventData(amount.getValue(), protocolVersion);
        assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    void logUpdateCollections() {
        // Setup Rsk transaction
        Transaction tx = mock(Transaction.class);
        when(tx.getSender(any(SignatureCache.class))).thenReturn(RSK_ADDRESS);

        // Act
        eventLogger.logUpdateCollections(tx);

        commonAssertLogs(eventLogs);
        assertTopics(1, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.UPDATE_COLLECTIONS.getEvent(),
            new Object[]{},
            new Object[]{tx.getSender(signatureCache).toString()}
        );
    }

    private static Stream<Arguments> logAddSignatureArgProvider() {
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        BtcECKey btcKey = BtcECKey.fromPrivate(Hex.decode("000000000000000000000000000000000000000000000000000000000000015e")); // 350
        ECKey rskKey = ECKey.fromPrivate(Hex.decode("000000000000000000000000000000000000000000000000000000000000015f")); // 351
        ECKey mstKey = ECKey.fromPrivate(Hex.decode("0000000000000000000000000000000000000000000000000000000000000160")); // 352

        RskAddress addressDerivedFromBtcKey = new RskAddress(ECKey.fromPrivate(btcKey.getPrivKeyBytes()).getAddress());
        RskAddress addressDerivedFromRskKey = new RskAddress(rskKey.getAddress());

        FederationMember singleKeyFedMember = new FederationMember(
            btcKey,
            ECKey.fromPublicOnly(btcKey.getPubKey()),
            ECKey.fromPublicOnly(btcKey.getPubKey())
        );

        FederationMember multiKeyFedMember = new FederationMember(
            btcKey,
            rskKey,
            mstKey
        );

        return Stream.of(
            Arguments.of(fingerrootActivations, singleKeyFedMember, addressDerivedFromBtcKey),
            Arguments.of(fingerrootActivations, multiKeyFedMember, addressDerivedFromBtcKey),
            Arguments.of(arrowheadActivations, singleKeyFedMember, addressDerivedFromBtcKey), // Given this is a single key fed member, the rsk address is equal to the one obtained from btc key
            Arguments.of(arrowheadActivations, multiKeyFedMember, addressDerivedFromRskKey)
        );
    }

    @ParameterizedTest()
    @MethodSource("logAddSignatureArgProvider")
    void logAddSignature(ActivationConfig.ForBlock activations, FederationMember federationMember, RskAddress expectedRskAddress) {
        // Arrange
        BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
            activations,
            eventLogs,
            signatureCache
        );
        BtcECKey federatorBtcPubKey = federationMember.getBtcPublicKey();

        // Act
        bridgeEventLogger.logAddSignature(federationMember, BTC_TRANSACTION, RSK_TX_HASH.getBytes());

        // Assert
        commonAssertLogs(eventLogs);
        assertTopics(3, eventLogs);

        CallTransaction.Function bridgeEvent = BridgeEvents.ADD_SIGNATURE.getEvent();
        Object[] eventTopics = new Object[]{RSK_TX_HASH.getBytes(), expectedRskAddress.toString()};
        Object[] eventParams = new Object[]{federatorBtcPubKey.getPubKey()};

        assertEvent(eventLogs, 0, bridgeEvent, eventTopics, eventParams);
    }

    @Test
    void logReleaseBtc() {
        // Act
        eventLogger.logReleaseBtc(BTC_TRANSACTION, RSK_TX_HASH.getBytes());

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.RELEASE_BTC.getEvent(),
            new Object[]{RSK_TX_HASH.getBytes()},
            new Object[]{BTC_TRANSACTION.bitcoinSerialize()}
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logCommitFederation(boolean isRSKIP383Active) {
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0L);
        if (!isRSKIP383Active) {
            eventLogger = new BridgeEventLoggerImpl(hopActivations, eventLogs, signatureCache);
        }

        long federationActivationAgePreRskip383 = FEDERATION_CONSTANTS.getFederationActivationAge(hopActivations);
        long federationActivationAgePostRskip383 = FEDERATION_CONSTANTS.getFederationActivationAge(allActivations);

        // Setup parameters for test method call
        Instant creationTime = Instant.ofEpochMilli(15005L);
        Block executionBlock = mock(Block.class);
        when(executionBlock.getTimestamp()).thenReturn(15005L);
        when(executionBlock.getNumber()).thenReturn(15L);

        List<BtcECKey> oldFederationKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
            BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
            BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49"))
        );

        List<FederationMember> oldFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(oldFederationKeys);
        NetworkParameters btcParams = BRIDGE_CONSTANTS.getBtcParams();
        FederationArgs oldFedArgs = new FederationArgs(
            oldFederationMembers,
            creationTime,
            15L,
            btcParams
        );

        Federation oldFederation = FederationFactory.buildStandardMultiSigFederation(oldFedArgs);

        List<BtcECKey> newFederationKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
            BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
            BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338"))
        );
        List<FederationMember> newFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(newFederationKeys);
        FederationArgs newFedArgs = new FederationArgs(
            newFederationMembers,
            creationTime,
            15L,
            btcParams
        );

        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFedArgs);

        long newFedActivationBlockNumber = isRSKIP383Active ?
            executionBlock.getNumber() + federationActivationAgePostRskip383 :
            executionBlock.getNumber() + federationActivationAgePreRskip383;

        // Act
        eventLogger.logCommitFederation(newFedActivationBlockNumber, oldFederation, newFederation);
        commonAssertLogs(eventLogs);
        assertTopics(1, eventLogs);

        // Assert log data
        byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();

        Object[] data = new Object[]{
            oldFederationFlatPubKeys,
            oldFederationBtcAddress,
            newFederationFlatPubKeys,
            newFederationBtcAddress,
            newFedActivationBlockNumber
        };

        CallTransaction.Function event = BridgeEvents.COMMIT_FEDERATION.getEvent();
        Object[] topics = {};
        assertEvent(eventLogs, 0, event, topics, data);

        final LogInfo log = eventLogs.get(0);
        Object[] decodeEventData = event.decodeEventData(log.getData());
        long loggedFedActivationBlockNumber = ((BigInteger) decodeEventData[4]).longValue();

        assertEquals(loggedFedActivationBlockNumber, newFedActivationBlockNumber);

        // assert fed activation has different values before and after RSKIP383 respectively
        if (isRSKIP383Active){
            long legacyFedActivationBlockNumber = executionBlock.getNumber() + federationActivationAgePreRskip383;
            assertNotEquals(loggedFedActivationBlockNumber, legacyFedActivationBlockNumber);
        } else {
            long defaultFedActivationBlockNumber = executionBlock.getNumber() + federationActivationAgePostRskip383;
            assertNotEquals(loggedFedActivationBlockNumber, defaultFedActivationBlockNumber);
        }
    }

    @Test
    void logReleaseBtcRequested() {
        Coin amount = Coin.SATOSHI;

        eventLogger.logReleaseBtcRequested(RSK_TX_HASH.getBytes(), BTC_TRANSACTION, amount);

        commonAssertLogs(eventLogs);
        assertTopics(3, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.RELEASE_REQUESTED.getEvent(),
            new Object[]{RSK_TX_HASH.getBytes(), BTC_TRANSACTION.getHash().getBytes()},
            new Object[]{amount.getValue()}
        );
    }

    @Test
    void logRejectedPegin() {
        eventLogger.logRejectedPegin(BTC_TRANSACTION, RejectedPeginReason.PEGIN_CAP_SURPASSED);

        assertEquals(1, eventLogs.size());
        LogInfo entry = eventLogs.get(0);

        assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(entry.getAddress()));

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        assertEquals(2, result.getTopics().size());
        CallTransaction.Function event = BridgeEvents.REJECTED_PEGIN.getEvent();

        byte[][] topics = event.encodeEventTopics(BTC_TRANSACTION.getHash().getBytes());

        for (int i=0; i<topics.length; i++) {
            assertArrayEquals(topics[i], result.getTopics().get(i).getData());
        }

        // Assert log data
        assertArrayEquals(event.encodeEventData(RejectedPeginReason.PEGIN_CAP_SURPASSED.getValue()), result.getData());
    }

    @Test
    void logUnrefundablePegin() {
        // Setup event logger
        eventLogger.logUnrefundablePegin(BTC_TRANSACTION, UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);

        assertEquals(1, eventLogs.size());
        LogInfo entry = eventLogs.get(0);

        assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(entry.getAddress()));

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        assertEquals(2, result.getTopics().size());
        CallTransaction.Function event = BridgeEvents.UNREFUNDABLE_PEGIN.getEvent();

        byte[][] topics = event.encodeEventTopics(BTC_TRANSACTION.getHash().getBytes());

        for (int i=0; i<topics.length; i++) {
            assertArrayEquals(topics[i], result.getTopics().get(i).getData());
        }

        // Assert log data
        assertArrayEquals(
            event.encodeEventData(UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER.getValue()),
            result.getData()
        );
    }

    @Test
    void logReleaseBtcRequestReceived_preRSKIP326_destinationAddressAsHash160() {
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0L);
        eventLogger = new BridgeEventLoggerImpl(
            hopActivations,
            eventLogs,
            signatureCache
        );

        Address btcRecipientAddress = new Address(
            NETWORK_PARAMETERS,
            NETWORK_PARAMETERS.getP2SHHeader(),
            Hex.decode("6bf06473af5f595cf97702229b007e50d6cfba83")
        );
        co.rsk.core.Coin amount = co.rsk.core.Coin.fromBitcoin(Coin.COIN);

        eventLogger.logReleaseBtcRequestReceived(RSK_ADDRESS, btcRecipientAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent(),
            new Object[]{RSK_ADDRESS.toHexString()},
            new Object[]{btcRecipientAddress.getHash160(), amount.toBitcoin().getValue()}
        );
    }

    @Test
    void logReleaseBtcRequestReceived_postRSKIP326_destinationAddressAsBase58() {
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
        eventLogger = new BridgeEventLoggerImpl(
            arrowheadActivations,
            eventLogs,
            signatureCache
        );

        Address btcRecipientAddress = new Address(
            NETWORK_PARAMETERS,
            NETWORK_PARAMETERS.getP2SHHeader(),
            Hex.decode("6bf06473af5f595cf97702229b007e50d6cfba83")
        );
        co.rsk.core.Coin amount = co.rsk.core.Coin.fromBitcoin(Coin.COIN);

        eventLogger.logReleaseBtcRequestReceived(RSK_ADDRESS, btcRecipientAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent(),
            new Object[]{RSK_ADDRESS.toHexString()},
            new Object[]{btcRecipientAddress.toString(), amount.toBitcoin().getValue()}
        );
    }

    @Test
    void logReleaseBtcRequestReceived_postRSKIP427_amountAsWeis() {
        Address btcRecipientAddress = new Address(
            NETWORK_PARAMETERS,
            NETWORK_PARAMETERS.getP2SHHeader(),
            Hex.decode("6bf06473af5f595cf97702229b007e50d6cfba83")
        );
        co.rsk.core.Coin amount = co.rsk.core.Coin.fromBitcoin(Coin.COIN);

        eventLogger.logReleaseBtcRequestReceived(RSK_ADDRESS, btcRecipientAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent(),
            new Object[]{RSK_ADDRESS.toHexString()},
            new Object[]{btcRecipientAddress.toString(), amount.asBigInteger()}
        );
    }

    @Test
    void logReleaseBtcRequestRejected_preRSKIP427_logAmountAsSatoshis() {
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0);
        eventLogger = new BridgeEventLoggerImpl(arrowheadActivations, eventLogs, signatureCache);

        co.rsk.core.Coin amount = co.rsk.core.Coin.valueOf(100_000_000_000_000_000L);
        RejectedPegoutReason reason = RejectedPegoutReason.LOW_AMOUNT;

        eventLogger.logReleaseBtcRequestRejected(RSK_ADDRESS, amount, reason);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent(),
            new Object[]{RSK_ADDRESS.toHexString()},
            new Object[]{amount.toBitcoin().getValue(), reason.getValue()}
        );
    }

    @Test
    void logReleaseBtcRequestRejected_postRSKIP427_logAmountAsWeis() {
        co.rsk.core.Coin amount = co.rsk.core.Coin.valueOf(100_000_000_000_000_000L);
        RejectedPegoutReason reason = RejectedPegoutReason.LOW_AMOUNT;

        eventLogger.logReleaseBtcRequestRejected(RSK_ADDRESS, amount, reason);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent(),
            new Object[]{RSK_ADDRESS.toHexString()},
            new Object[]{amount.asBigInteger(), reason.getValue()}
        );
    }

    @Test
    void logBatchPegoutCreated() {
        List<Keccak256> rskTxHashes = Arrays.asList(
            RskTestUtils.createHash(0),
            RskTestUtils.createHash(1),
            RskTestUtils.createHash(2)
        );

        eventLogger.logBatchPegoutCreated(BTC_TRANSACTION.getHash(), rskTxHashes);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.BATCH_PEGOUT_CREATED.getEvent(),
            new Object[]{BTC_TRANSACTION.getHash().getBytes()},
            new Object[]{serializeRskTxHashes(rskTxHashes)}
        );
    }

    @Test
    void logBatchPegoutCreatedWithWitness() {
        List<Keccak256> rskTxHashes = Arrays.asList(
            RskTestUtils.createHash(0),
            RskTestUtils.createHash(1),
            RskTestUtils.createHash(2)
        );

        TransactionWitness txWitness = new TransactionWitness(1);

        BTC_TRANSACTION.addInput(
            Sha256Hash.ZERO_HASH,
            0,
            ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        txWitness.setPush(0, new byte[]{ 0x1 });
        BTC_TRANSACTION.setWitness(0, txWitness);
        eventLogger.logBatchPegoutCreated(BTC_TRANSACTION.getHash(true), rskTxHashes);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.BATCH_PEGOUT_CREATED.getEvent(),
            new Object[]{BTC_TRANSACTION.getHash(true).getBytes()},
            new Object[]{serializeRskTxHashes(rskTxHashes)}
        );
    }

    @Test
    void logPegoutConfirmed() {
        long pegoutCreationRskBlockNumber = 50;
        eventLogger.logPegoutConfirmed(BTC_TRANSACTION.getHash(), pegoutCreationRskBlockNumber);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(
            eventLogs,
            0,
            BridgeEvents.PEGOUT_CONFIRMED.getEvent(),
            new Object[]{BTC_TRANSACTION.getHash().getBytes()},
            new Object[]{pegoutCreationRskBlockNumber}
        );
    }

    private static Stream<Arguments> logPegoutTransactionCreatedValidArgProvider() {
        List<Arguments> args = new ArrayList<>();

        args.add(Arguments.of(Sha256Hash.ZERO_HASH, Collections.singletonList(Coin.SATOSHI)));
        args.add(Arguments.of(BitcoinTestUtils.createHash(5), coinListOf(500_000, 400_000, 1_000)));
        args.add(Arguments.of(BitcoinTestUtils.createHash(10), Collections.singletonList(Coin.ZERO)));
        args.add(Arguments.of(BitcoinTestUtils.createHash(10), Collections.EMPTY_LIST));
        args.add(Arguments.of(BitcoinTestUtils.createHash(15), null));

        return args.stream();
    }

    @ParameterizedTest()
    @MethodSource("logPegoutTransactionCreatedValidArgProvider")
    void logPegoutTransactionCreated_ok(Sha256Hash btcTxHash, List<Coin> outpointValues) {
        eventLogger.logPegoutTransactionCreated(btcTxHash, outpointValues);
        commonAssertLogs(eventLogs);

        assertTopics(2, eventLogs);

        int index = 0;
        Function expectedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();
        Object[] topics = {btcTxHash.getBytes()};
        Object[] params = {UtxoUtils.encodeOutpointValues(outpointValues)};
        assertEvent(eventLogs, index, expectedEvent, topics, params);
    }

    private static Stream<Arguments> logPegoutTransactionCreatedInvalidArgProvider() {
        List<Arguments> args = new ArrayList<>();

        args.add(Arguments.of(null, Collections.singletonList(null), IllegalArgumentException.class));
        args.add(Arguments.of(null, Collections.singletonList(Coin.SATOSHI), IllegalArgumentException.class));
        args.add(Arguments.of(Sha256Hash.ZERO_HASH, Collections.singletonList(null), InvalidOutpointValueException.class));
        args.add(Arguments.of(BitcoinTestUtils.createHash(1), coinListOf(-100), InvalidOutpointValueException.class));
        args.add(Arguments.of(BitcoinTestUtils.createHash(2), coinListOf(100, -200, 300), InvalidOutpointValueException.class));

        return args.stream();
    }

    @ParameterizedTest()
    @MethodSource("logPegoutTransactionCreatedInvalidArgProvider")
    void logPegoutTransactionCreated_invalidBtcTxHashOrOutpointValues_shouldFail(Sha256Hash btcTxHash, List<Coin> outpointValues, Class<? extends  Exception> expectedException) {
        assertThrows(expectedException, () -> eventLogger.logPegoutTransactionCreated(btcTxHash, outpointValues));
    }

    /**********************************
     *  -------     UTILS     ------- *
     *********************************/
    private static void assertEvent(List<LogInfo> logs, int index, CallTransaction.Function event, Object[] topics, Object[] params) {
        final LogInfo log = logs.get(index);
        assertEquals(LogInfo.byteArrayToList(event.encodeEventTopics(topics)), log.getTopics());
        assertArrayEquals(event.encodeEventData(params), log.getData());
    }

    private void assertTopics(int topics, List<LogInfo> logs) {
        assertEquals(topics, logs.get(0).getTopics().size());
    }

    private void commonAssertLogs(List<LogInfo> logs) {
        assertEquals(1, logs.size());
        LogInfo entry = logs.get(0);

        // Assert address that made the log
        assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(entry.getAddress()));
        assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), entry.getAddress());
    }

    private byte[] serializeRskTxHashes(List<Keccak256> rskTxHashes) {
        List<byte[]> rskTxHashesList = rskTxHashes.stream()
            .map(Keccak256::getBytes)
            .collect(Collectors.toList());
        int rskTxHashesLength = rskTxHashesList.stream().mapToInt(key -> key.length).sum();

        byte[] serializedRskTxHashes = new byte[rskTxHashesLength];
        int copyPos = 0;
        for (byte[] serializedRskTxHash : rskTxHashesList) {
            System.arraycopy(
                serializedRskTxHash,
                0,
                serializedRskTxHashes,
                copyPos,
                serializedRskTxHash.length
            );
            copyPos += serializedRskTxHash.length;
        }

        return serializedRskTxHashes;
    }
}
