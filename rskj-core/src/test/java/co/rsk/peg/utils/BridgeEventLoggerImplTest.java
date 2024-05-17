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

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.*;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.InvalidOutpointValueException;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.federation.*;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.pegin.RejectedPeginReason;
import java.util.ArrayList;
import java.util.Collections;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.rsk.peg.bitcoin.UtxoTestUtils.coinListOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class for BridgeEventLoggerImpl.
 *
 * @author martin.medina
 */

class BridgeEventLoggerImplTest {

    private static final BridgeRegTestConstants CONSTANTS = BridgeRegTestConstants.getInstance();
    private final ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

    private List<LogInfo> eventLogs;
    private BridgeEventLogger eventLogger;
    private BtcTransaction btcTxMock;
    private BtcTransaction btcTx;
    private SignatureCache signatureCache;

    @BeforeEach
    void setup() {
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        eventLogs = new LinkedList<>();
        eventLogger = new BridgeEventLoggerImpl(CONSTANTS, activations, eventLogs, signatureCache);
        btcTxMock = mock(BtcTransaction.class);
        btcTx = new BtcTransaction(CONSTANTS.getBtcParams());
    }

    @Test
    void logLockBtc() {
        Address senderAddress = mock(Address.class);
        when(senderAddress.toString()).thenReturn("mixzLp4xx5bUsHuYUEyPpL42BzEDp8kSTv");

        RskAddress rskAddress = mock(RskAddress.class);
        when(rskAddress.toString()).thenReturn("0x00000000000000000000000000000000000000");

        // Mock btc transaction
        when(btcTxMock.getHash()).thenReturn(PegTestUtils.createHash(0));

        Coin amount = Coin.SATOSHI;

        // Act
        eventLogger.logLockBtc(rskAddress, btcTxMock, senderAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.LOCK_BTC.getEvent(), new Object[]{rskAddress.toString()}, new Object[]{btcTxMock.getHash().getBytes(), senderAddress.toString(), amount.getValue()});
    }

    @Test
    void logLockBtc_with_segwit_address() {
        Address senderAddress = new Address(CONSTANTS.getBtcParams(), CONSTANTS.getBtcParams().getP2SHHeader(), Hex.decode("c99a8f22127007255b4a9d8d57b0892ae2103f2d"));

        RskAddress rskAddress = mock(RskAddress.class);
        when(rskAddress.toString()).thenReturn("0x00000000000000000000000000000000000000");

        // Mock btc transaction
        when(btcTxMock.getHash()).thenReturn(PegTestUtils.createHash(0));

        Coin amount = Coin.SATOSHI;

        // Act
        eventLogger.logLockBtc(rskAddress, btcTxMock, senderAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.LOCK_BTC.getEvent(), new Object[]{rskAddress.toString()}, new Object[]{btcTxMock.getHash().getBytes(), "2NBdCxoCY6wx1NHpwGWfJThHk9K2tVdNx1A", amount.getValue()});
    }

    @Test
    void logPeginBtc() {
        // Setup event logger
        List<LogInfo> eventLogs = new LinkedList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs, signatureCache);

        RskAddress rskAddress = mock(RskAddress.class);
        when(rskAddress.toString()).thenReturn("0x00000000000000000000000000000000000000");

        // Mock btc transaction
        BtcTransaction mockedTx = mock(BtcTransaction.class);
        when(mockedTx.getHash()).thenReturn(PegTestUtils.createHash(0));

        Coin amount = Coin.SATOSHI;
        int protocolVersion = 1;

        // Act
        eventLogger.logPeginBtc(rskAddress, mockedTx, amount, protocolVersion);

        // Assert log size
        Assertions.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);
        CallTransaction.Function event = BridgeEvents.PEGIN_BTC.getEvent();

        // Assert address that made the log
        Assertions.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assertions.assertEquals(3, logResult.getTopics().size());
        byte[][] topics = event.encodeEventTopics(rskAddress.toString(), mockedTx.getHash().getBytes());
        for (int i=0; i<topics.length; i++) {
            Assertions.assertArrayEquals(topics[i], logResult.getTopics().get(i).getData());
        }

        // Assert log data
        byte[] encodedData = event.encodeEventData(amount.getValue(), protocolVersion);
        Assertions.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    void logUpdateCollections() {
        // Setup Rsk transaction
        Transaction tx = mock(Transaction.class);
        RskAddress sender = mock(RskAddress.class);
        when(sender.toString()).thenReturn("0x0000000000000000000000000000000000000001");
        when(tx.getSender(any(SignatureCache.class))).thenReturn(sender);

        // Act
        eventLogger.logUpdateCollections(tx);

        commonAssertLogs(eventLogs);
        assertTopics(1, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.UPDATE_COLLECTIONS.getEvent(), new Object[]{}, new Object[]{tx.getSender(signatureCache).toString()});
    }

    private static Stream<Arguments> logAddSignatureArgProvider() {
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        BtcECKey btcKey = BtcECKey.fromPrivate(Hex.decode("000000000000000000000000000000000000000000000000000000000000015e")); // 350
        ECKey rskKey = ECKey.fromPrivate(Hex.decode("000000000000000000000000000000000000000000000000000000000000015f")); // 351
        ECKey mstKey = ECKey.fromPrivate(Hex.decode("0000000000000000000000000000000000000000000000000000000000000160")); // 352

        String addressDerivedFromBtcKey = "dbc29273d4de3d5645e308c7e629d28d4499b3d3";
        String addressDerivedFromRskKey = "74891a05ad4d7ec87c1cffe9bd00bb4e1382b586";

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
    void logAddSignature(ActivationConfig.ForBlock activations, FederationMember federationMember, String expectedRskAddress) {
        // Arrange
        BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        LinkedList<LogInfo> eventLogs = new LinkedList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(bridgeMainNetConstants, activations, eventLogs, signatureCache);

        BtcECKey federatorBtcPubKey = federationMember.getBtcPublicKey();

        Keccak256 rskTxHash = PegTestUtils.createHash3(1);

        BtcTransaction btcTxMock = mock(BtcTransaction.class);
        when(btcTxMock.getHash()).thenReturn(BitcoinTestUtils.createHash(1));
        when(btcTxMock.getHashAsString()).thenReturn(rskTxHash.toHexString());

        // Act
        eventLogger.logAddSignature(federationMember, btcTxMock, rskTxHash.getBytes());

        // Assert
        commonAssertLogs(eventLogs);
        assertTopics(3, eventLogs);

        CallTransaction.Function bridgeEvent = BridgeEvents.ADD_SIGNATURE.getEvent();
        Object[] eventTopics = new Object[]{rskTxHash.getBytes(), expectedRskAddress};
        Object[] eventParams = new Object[]{federatorBtcPubKey.getPubKey()};

        assertEvent(eventLogs, 0, bridgeEvent, eventTopics, eventParams);
    }

    @Test
    void logReleaseBtc() {
        // Setup Btc transaction
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);

        // Act
        eventLogger.logReleaseBtc(btcTx, rskTxHash.getBytes());

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.RELEASE_BTC.getEvent(), new Object[]{rskTxHash.getBytes()}, new Object[]{btcTx.bitcoinSerialize()});
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logCommitFederation(boolean isRSKIP383Active) {
        when(activations.isActive(ConsensusRule.RSKIP383)).thenReturn(isRSKIP383Active);
        // Setup parameters for test method call
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
        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        FederationArgs oldFedArgs = new FederationArgs(oldFederationMembers, Instant.ofEpochMilli(15005L), 15L, btcParams);

        Federation oldFederation = FederationFactory.buildStandardMultiSigFederation(oldFedArgs);

        List<BtcECKey> newFederationKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
            BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
            BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338"))
        );
        List<FederationMember> newFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(newFederationKeys);
        FederationArgs newFedArgs = new FederationArgs(newFederationMembers, Instant.ofEpochMilli(15005L), 15L, btcParams);

        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFedArgs);

        // Act
        eventLogger.logCommitFederation(executionBlock, oldFederation, newFederation);

        commonAssertLogs(eventLogs);

        assertTopics(1, eventLogs);
        // Assert log data
        byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();
        long newFedActivationBlockNumber = executionBlock.getNumber() + CONSTANTS.getFederationActivationAge(activations);

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
            ActivationConfig.ForBlock activationsForLegacyFedActivationAge = mock(ActivationConfig.ForBlock.class);
            when(activationsForLegacyFedActivationAge.isActive(ConsensusRule.RSKIP383)).thenReturn(false);

            long legacyFedActivationBlockNumber = executionBlock.getNumber() + CONSTANTS.getFederationActivationAge(activationsForLegacyFedActivationAge);

            assertNotEquals(loggedFedActivationBlockNumber, legacyFedActivationBlockNumber);
        } else {
            ActivationConfig.ForBlock activationsForDefaultFedActivationAge = mock(ActivationConfig.ForBlock.class);
            when(activationsForDefaultFedActivationAge.isActive(ConsensusRule.RSKIP383)).thenReturn(true);

            long defaultFedActivationBlockNumber = executionBlock.getNumber() + CONSTANTS.getFederationActivationAge(activationsForDefaultFedActivationAge);
            assertNotEquals(loggedFedActivationBlockNumber, defaultFedActivationBlockNumber);
        }
    }

    @Test
    void logReleaseBtcRequested() {
        Keccak256 rskTxHash = PegTestUtils.createHash3(0);
        Coin amount = Coin.SATOSHI;

        eventLogger.logReleaseBtcRequested(rskTxHash.getBytes(), btcTx, amount);

        commonAssertLogs(eventLogs);

        assertTopics(3, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.RELEASE_REQUESTED.getEvent(), new Object[]{rskTxHash.getBytes(), btcTx.getHash().getBytes()}, new Object[]{amount.getValue()});
    }

    @Test
    void logRejectedPegin() {
        // Setup event logger
        List<LogInfo> eventLogs = new LinkedList<>();

        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs, signatureCache);

        BtcTransaction btcTx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());

        eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.PEGIN_CAP_SURPASSED);

        Assertions.assertEquals(1, eventLogs.size());
        LogInfo entry = eventLogs.get(0);

        Assertions.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(entry.getAddress()));

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        Assertions.assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assertions.assertEquals(2, result.getTopics().size());
        CallTransaction.Function event = BridgeEvents.REJECTED_PEGIN.getEvent();

        byte[][] topics = event.encodeEventTopics(btcTx.getHash().getBytes());

        for (int i=0; i<topics.length; i++) {
            Assertions.assertArrayEquals(topics[i], result.getTopics().get(i).getData());
        }

        // Assert log data
        Assertions.assertArrayEquals(event.encodeEventData(RejectedPeginReason.PEGIN_CAP_SURPASSED.getValue()), result.getData());
    }

    @Test
    void logUnrefundablePegin() {
        // Setup event logger
        List<LogInfo> eventLogs = new LinkedList<>();

        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs, signatureCache);

        BtcTransaction btcTx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());

        eventLogger.logUnrefundablePegin(btcTx, UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);

        Assertions.assertEquals(1, eventLogs.size());
        LogInfo entry = eventLogs.get(0);

        Assertions.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(entry.getAddress()));

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        Assertions.assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assertions.assertEquals(2, result.getTopics().size());
        CallTransaction.Function event = BridgeEvents.UNREFUNDABLE_PEGIN.getEvent();

        byte[][] topics = event.encodeEventTopics(btcTx.getHash().getBytes());

        for (int i=0; i<topics.length; i++) {
            Assertions.assertArrayEquals(topics[i], result.getTopics().get(i).getData());
        }

        // Assert log data
        Assertions.assertArrayEquals(event.encodeEventData(UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER.getValue()), result.getData());
    }


    @Test
    void testLogReleaseBtcRequestReceivedBeforeRSKIP326HardFork() {
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(false);
        String sender = "0x00000000000000000000000000000000000000";
        Address btcRecipientAddress = new Address(CONSTANTS.getBtcParams(), CONSTANTS.getBtcParams().getP2SHHeader(), Hex.decode("6bf06473af5f595cf97702229b007e50d6cfba83"));
        Coin amount = Coin.COIN;

        eventLogger.logReleaseBtcRequestReceived(sender, btcRecipientAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent(), new Object[]{sender}, new Object[]{btcRecipientAddress.getHash160(), amount.value});
    }

    @Test
    void testLogReleaseBtcRequestReceivedAfterRSKIP326HardFork() {
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(true);
        String sender = "0x00000000000000000000000000000000000000";
        Address btcRecipientAddress = new Address(CONSTANTS.getBtcParams(), CONSTANTS.getBtcParams().getP2SHHeader(), Hex.decode("6bf06473af5f595cf97702229b007e50d6cfba83"));
        Coin amount = Coin.COIN;

        eventLogger.logReleaseBtcRequestReceived(sender, btcRecipientAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent(), new Object[]{sender}, new Object[]{btcRecipientAddress.toString(), amount.value});
    }


    @Test
    void testLogReleaseBtcRequestRejected() {
        String sender = "0x00000000000000000000000000000000000000";
        Coin amount = Coin.COIN;
        RejectedPegoutReason reason = RejectedPegoutReason.LOW_AMOUNT;

        eventLogger.logReleaseBtcRequestRejected(sender, amount, reason);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent(), new Object[]{sender}, new Object[]{amount.value, reason.getValue()});
    }

    @Test
    void logBatchPegoutCreated() {
        List<Keccak256> rskTxHashes = Arrays.asList(PegTestUtils.createHash3(0), PegTestUtils.createHash3(1), PegTestUtils.createHash3(2));

        eventLogger.logBatchPegoutCreated(btcTx.getHash(), rskTxHashes);

        commonAssertLogs(eventLogs);

        assertTopics(2, eventLogs);

        assertEvent(eventLogs, 0, BridgeEvents.BATCH_PEGOUT_CREATED.getEvent(), new Object[]{btcTx.getHash().getBytes()}, new Object[]{serializeRskTxHashes(rskTxHashes)});
    }

    @Test
    void logBatchPegoutCreatedWithWitness() {
        List<Keccak256> rskTxHashes = Arrays.asList(PegTestUtils.createHash3(0), PegTestUtils.createHash3(1), PegTestUtils.createHash3(2));

        TransactionWitness txWitness = new TransactionWitness(1);

        btcTx.addInput(
                Sha256Hash.ZERO_HASH,
                0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        txWitness.setPush(0, new byte[]{ 0x1 });
        btcTx.setWitness(0, txWitness);
        eventLogger.logBatchPegoutCreated(btcTx.getHash(true), rskTxHashes);

        commonAssertLogs(eventLogs);

        assertTopics(2, eventLogs);

        assertEvent(eventLogs, 0, BridgeEvents.BATCH_PEGOUT_CREATED.getEvent(), new Object[]{btcTx.getHash(true).getBytes()}, new Object[]{serializeRskTxHashes(rskTxHashes)});

    }

    @Test
    void logPegoutConfirmed() {
        long pegoutCreationRskBlockNumber = 50;
        eventLogger.logPegoutConfirmed(btcTx.getHash(), pegoutCreationRskBlockNumber);

        commonAssertLogs(eventLogs);

        assertTopics(2, eventLogs);

        assertEvent(eventLogs, 0, BridgeEvents.PEGOUT_CONFIRMED.getEvent(), new Object[]{btcTx.getHash().getBytes()}, new Object[]{pegoutCreationRskBlockNumber});
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
        assertEvent(eventLogs, 0, BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent(), new Object[]{btcTxHash.getBytes()}, new Object[]{
            UtxoUtils.encodeOutpointValues(outpointValues)}
        );
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

    private byte[] flatKeysAsByteArray(List<BtcECKey> keys) {
        List<byte[]> pubKeys = keys.stream()
            .map(BtcECKey::getPubKey)
            .collect(Collectors.toList());
        int pubKeysLength = pubKeys.stream().mapToInt(key -> key.length).sum();

        byte[] flatPubKeys = new byte[pubKeysLength];
        int copyPos = 0;
        for (byte[] key : pubKeys) {
            System.arraycopy(key, 0, flatPubKeys, copyPos, key.length);
            copyPos += key.length;
        }

        return flatPubKeys;
    }

    private byte[] serializeRskTxHashes(List<Keccak256> rskTxHashes) {
        List<byte[]> rskTxHashesList = rskTxHashes.stream()
            .map(Keccak256::getBytes)
            .collect(Collectors.toList());
        int rskTxHashesLength = rskTxHashesList.stream().mapToInt(key -> key.length).sum();

        byte[] serializedRskTxHashes = new byte[rskTxHashesLength];
        int copyPos = 0;
        for (byte[] rskTxHash : rskTxHashesList) {
            System.arraycopy(rskTxHash, 0, serializedRskTxHashes, copyPos, rskTxHash.length);
            copyPos += rskTxHash.length;
        }

        return serializedRskTxHashes;
    }
}
