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

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.InvalidOutpointValueException;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.union.constants.UnionBridgeMainNetConstants;
import co.rsk.peg.union.constants.UnionBridgeRegTestConstants;
import co.rsk.peg.union.constants.UnionBridgeTestNetConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.CallTransaction.Function;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.flatKeysAsByteArray;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeEventLoggerImplTest {
    private static final RskAddress BRIDGE_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;
    private static final byte[] BRIDGE_ADDRESS_SERIALIZED = BRIDGE_ADDRESS.getBytes();
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final BtcTransaction BTC_TRANSACTION = new BtcTransaction(NETWORK_PARAMETERS);
    private static final RskAddress RSK_ADDRESS = new RskAddress("0x0000000000000000000000000000000000000101");
    private static final Keccak256 RSK_TX_HASH = RskTestUtils.createHash(1);

    private List<LogInfo> eventLogs;
    private BridgeEventLogger eventLogger;

    @BeforeEach
    void setup() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

        eventLogs = new LinkedList<>();
        eventLogger = new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, activations, eventLogs);
    }

    @Test
    void logLockBtc() {
        Address senderAddress = Address.fromBase58(NETWORK_PARAMETERS, "1MvSMVpP6PY6XZtxenGXoFcH575JUWkzoP");
        Coin amount = Coin.SATOSHI;

        // Act
        eventLogger.logLockBtc(RSK_ADDRESS, BTC_TRANSACTION, senderAddress, amount);

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
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

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
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
        assertEquals(BRIDGE_ADDRESS, new RskAddress(logResult.getAddress()));

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
        // Act
        eventLogger.logUpdateCollections(RSK_ADDRESS);

        commonAssertLogs();
        assertTopics(1);
        assertEvent(
            BridgeEvents.UPDATE_COLLECTIONS.getEvent(),
            new Object[]{},
            new Object[]{RSK_ADDRESS.toHexString()}
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
            BRIDGE_CONSTANTS,
            activations,
            eventLogs
        );
        BtcECKey federatorBtcPubKey = federationMember.getBtcPublicKey();

        // Act
        bridgeEventLogger.logAddSignature(federationMember, BTC_TRANSACTION, RSK_TX_HASH.getBytes());

        // Assert
        commonAssertLogs();
        assertTopics(3);

        CallTransaction.Function bridgeEvent = BridgeEvents.ADD_SIGNATURE.getEvent();
        Object[] eventTopics = new Object[]{RSK_TX_HASH.getBytes(), expectedRskAddress.toString()};
        Object[] eventParams = new Object[]{federatorBtcPubKey.getPubKey()};

        assertEvent(bridgeEvent, eventTopics, eventParams);
    }

    @Test
    void logReleaseBtc() {
        // Act
        eventLogger.logReleaseBtc(BTC_TRANSACTION, RSK_TX_HASH.getBytes());

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
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
            eventLogger = new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, hopActivations, eventLogs);
        }

        long federationActivationAgePreRskip383 = FEDERATION_CONSTANTS.getFederationActivationAge(hopActivations);
        long federationActivationAgePostRskip383 = FEDERATION_CONSTANTS.getFederationActivationAge(allActivations);

        // Setup parameters for test method call
        Block rskExecutionBlock = arrangeRskExecutionBlock();
        long rskExecutionBlockNumber = rskExecutionBlock.getNumber();

        Instant creationTime = Instant.ofEpochMilli(rskExecutionBlockNumber);
        Federation oldFederation = getOldFederation(creationTime);
        Federation newFederation = getNewFederation(creationTime);

        // Act
        eventLogger.logCommitFederation(rskExecutionBlock, oldFederation, newFederation);
        commonAssertLogs();
        assertTopics(1);

        // Assert log data
        byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();
        long newFedActivationBlockNumber = isRSKIP383Active ?
            rskExecutionBlockNumber + federationActivationAgePostRskip383 :
            rskExecutionBlockNumber + federationActivationAgePreRskip383;

        Object[] data = new Object[]{
            oldFederationFlatPubKeys,
            oldFederationBtcAddress,
            newFederationFlatPubKeys,
            newFederationBtcAddress,
            newFedActivationBlockNumber
        };

        CallTransaction.Function event = BridgeEvents.COMMIT_FEDERATION.getEvent();
        Object[] topics = {};
        assertEvent(event, topics, data);

        final LogInfo log = eventLogs.get(0);
        Object[] decodeEventData = event.decodeEventData(log.getData());
        long loggedFedActivationBlockNumber = ((BigInteger) decodeEventData[4]).longValue();

        assertEquals(loggedFedActivationBlockNumber, newFedActivationBlockNumber);

        // assert fed activation has different values before and after RSKIP383 respectively
        if (isRSKIP383Active){
            long legacyFedActivationBlockNumber = rskExecutionBlockNumber + federationActivationAgePreRskip383;
            assertNotEquals(loggedFedActivationBlockNumber, legacyFedActivationBlockNumber);
        } else {
            long defaultFedActivationBlockNumber = rskExecutionBlockNumber + federationActivationAgePostRskip383;
            assertNotEquals(loggedFedActivationBlockNumber, defaultFedActivationBlockNumber);
        }
    }

    private Federation getOldFederation(Instant creationTime) {
        List<BtcECKey> oldFederationKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
            BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
            BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49"))
        );

        return getFederationFromBtcKeys(oldFederationKeys, creationTime);
    }

    private Federation getNewFederation(Instant creationTime) {
        List<BtcECKey> newFederationKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
            BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
            BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338"))
        );

        return getFederationFromBtcKeys(newFederationKeys, creationTime);
    }

    private Federation getFederationFromBtcKeys(List<BtcECKey> federationKeys, Instant creationTime) {
        NetworkParameters btcParams = BRIDGE_CONSTANTS.getBtcParams();

        List<FederationMember> federationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(federationKeys);
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            creationTime,
            15L,
            btcParams
        );

        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    @Test
    void logCommitFederationFailed() {
        // arrange
        Block rskExecutionBlock = arrangeRskExecutionBlock();
        long executionBlockNumber = rskExecutionBlock.getNumber();

        Federation proposedFederation = P2shErpFederationBuilder.builder().build();
        byte[] proposedFederationRedeemScriptSerialized = proposedFederation.getRedeemScript().getProgram();

        CallTransaction.Function event = BridgeEvents.COMMIT_FEDERATION_FAILED.getEvent();
        var topics = new Object[]{};
        var data = new Object[]{
            proposedFederationRedeemScriptSerialized,
            executionBlockNumber
        };

        // act
        eventLogger.logCommitFederationFailure(rskExecutionBlock, proposedFederation);

        // assert
        commonAssertLogs();
        assertTopics(1);
        assertEvent(event, topics, data);
    }

    private Block arrangeRskExecutionBlock() {
        long rskExecutionBlockNumber = 15005L;
        long rskExecutionBlockTimestamp = 15L;
        return createRskBlock(rskExecutionBlockNumber, rskExecutionBlockTimestamp);
    }

    @Test
    void logReleaseBtcRequested() {
        Coin amount = Coin.SATOSHI;

        eventLogger.logReleaseBtcRequested(RSK_TX_HASH.getBytes(), BTC_TRANSACTION, amount);

        commonAssertLogs();
        assertTopics(3);
        assertEvent(
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

        assertEquals(BRIDGE_ADDRESS, new RskAddress(entry.getAddress()));

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        assertArrayEquals(BRIDGE_ADDRESS_SERIALIZED, result.getAddress());

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
    void logNonRefundablePegin() {
        // Setup event logger
        eventLogger.logNonRefundablePegin(BTC_TRANSACTION, NonRefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);

        assertEquals(1, eventLogs.size());
        LogInfo entry = eventLogs.get(0);

        assertEquals(BRIDGE_ADDRESS, new RskAddress(entry.getAddress()));

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        assertArrayEquals(BRIDGE_ADDRESS_SERIALIZED, result.getAddress());

        // Assert log topics
        assertEquals(2, result.getTopics().size());
        CallTransaction.Function event = BridgeEvents.UNREFUNDABLE_PEGIN.getEvent();

        byte[][] topics = event.encodeEventTopics(BTC_TRANSACTION.getHash().getBytes());

        for (int i=0; i<topics.length; i++) {
            assertArrayEquals(topics[i], result.getTopics().get(i).getData());
        }

        // Assert log data
        assertArrayEquals(
            event.encodeEventData(NonRefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER.getValue()),
            result.getData()
        );
    }

    @Test
    void logReleaseBtcRequestReceived_preRSKIP326_destinationAddressAsHash160() {
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0L);
        eventLogger = new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            hopActivations,
            eventLogs
        );

        Address btcRecipientAddress = new Address(
            NETWORK_PARAMETERS,
            NETWORK_PARAMETERS.getP2SHHeader(),
            Hex.decode("6bf06473af5f595cf97702229b007e50d6cfba83")
        );
        co.rsk.core.Coin amount = co.rsk.core.Coin.fromBitcoin(Coin.COIN);

        eventLogger.logReleaseBtcRequestReceived(RSK_ADDRESS, btcRecipientAddress, amount);

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
            BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent(),
            new Object[]{RSK_ADDRESS.toHexString()},
            new Object[]{btcRecipientAddress.getHash160(), amount.toBitcoin().getValue()}
        );
    }

    @Test
    void logReleaseBtcRequestReceived_postRSKIP326_destinationAddressAsBase58() {
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
        eventLogger = new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            arrowheadActivations,
            eventLogs
        );

        Address btcRecipientAddress = new Address(
            NETWORK_PARAMETERS,
            NETWORK_PARAMETERS.getP2SHHeader(),
            Hex.decode("6bf06473af5f595cf97702229b007e50d6cfba83")
        );
        co.rsk.core.Coin amount = co.rsk.core.Coin.fromBitcoin(Coin.COIN);

        eventLogger.logReleaseBtcRequestReceived(RSK_ADDRESS, btcRecipientAddress, amount);

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
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

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
            BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent(),
            new Object[]{RSK_ADDRESS.toHexString()},
            new Object[]{btcRecipientAddress.toString(), amount.asBigInteger()}
        );
    }

    @Test
    void logReleaseBtcRequestRejected_preRSKIP427_logAmountAsSatoshis() {
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0);
        eventLogger = new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, arrowheadActivations, eventLogs);

        co.rsk.core.Coin amount = co.rsk.core.Coin.valueOf(100_000_000_000_000_000L);
        RejectedPegoutReason reason = RejectedPegoutReason.LOW_AMOUNT;

        eventLogger.logReleaseBtcRequestRejected(RSK_ADDRESS, amount, reason);

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
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

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
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

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
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

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
            BridgeEvents.BATCH_PEGOUT_CREATED.getEvent(),
            new Object[]{BTC_TRANSACTION.getHash(true).getBytes()},
            new Object[]{serializeRskTxHashes(rskTxHashes)}
        );
    }

    @Test
    void logPegoutConfirmed() {
        long pegoutCreationRskBlockNumber = 50;
        eventLogger.logPegoutConfirmed(BTC_TRANSACTION.getHash(), pegoutCreationRskBlockNumber);

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
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
        commonAssertLogs();

        assertTopics(2);

        Function expectedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();
        Object[] topics = {btcTxHash.getBytes()};
        Object[] params = {UtxoUtils.encodeOutpointValues(outpointValues)};
        assertEvent(expectedEvent, topics, params);
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

    @ParameterizedTest
    @MethodSource("logUnionRbtcRequestedArgProvider")
    void logUnionRbtcRequested_whenOk_shouldEmitEvent(RskAddress requester, co.rsk.core.Coin amount) {
        // Act
        eventLogger.logUnionRbtcRequested(requester, amount);

        // Assert
        commonAssertLogs();
        assertTopics(2);
        assertEvent(
            BridgeEvents.UNION_RBTC_REQUESTED.getEvent(),
            new Object[]{requester.toHexString()},
            new Object[]{amount.asBigInteger()}
        );
    }

    private static Stream<Arguments> logUnionRbtcRequestedArgProvider() {
        BigInteger oneRbtcInWeis = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        co.rsk.core.Coin oneRbtc = new co.rsk.core.Coin(oneRbtcInWeis);
        co.rsk.core.Coin twoRbtc = new co.rsk.core.Coin(oneRbtcInWeis.multiply(BigInteger.valueOf(2L)));
        co.rsk.core.Coin threeRbtc = new co.rsk.core.Coin(oneRbtcInWeis.multiply(BigInteger.valueOf(3L)));

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance().getAddress(), oneRbtc),
            Arguments.of(UnionBridgeTestNetConstants.getInstance().getAddress(), twoRbtc),
            Arguments.of(UnionBridgeRegTestConstants.getInstance().getAddress(), threeRbtc)
        );
    }

    @ParameterizedTest
    @MethodSource("logUnionRbtcRequestedInvalidArgProvider")
    void logUnionRbtcRequested_whenInvalidArg_shouldFail(RskAddress requester,
        co.rsk.core.Coin amount) {
        assertThrows(NullPointerException.class,
            () -> eventLogger.logUnionRbtcRequested(requester, amount),
            "Requester or amount cannot be null");
    }

    private static Stream<Arguments> logUnionRbtcRequestedInvalidArgProvider() {
        RskAddress unionBridgeContractAddress = UnionBridgeMainNetConstants.getInstance().getAddress();
        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei

        return Stream.of(
            Arguments.of(unionBridgeContractAddress, null),
            Arguments.of(null, new co.rsk.core.Coin(oneEth)),
            Arguments.of(null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("logUnionRbtcReleasedInvalidArgProvider")
    void logUnionRbtcReleased_whenInvalidArg_shouldFail(RskAddress receiver, co.rsk.core.Coin amount) {
        assertThrows(NullPointerException.class,
            () -> eventLogger.logUnionRbtcReleased(receiver, amount),
            "Receiver or amount cannot be null");
    }

    private static Stream<Arguments> logUnionRbtcReleasedInvalidArgProvider() {
        RskAddress unionBridgeContractAddress = UnionBridgeMainNetConstants.getInstance().getAddress();
        BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei

        return Stream.of(
            Arguments.of(unionBridgeContractAddress, null),
            Arguments.of(null, new co.rsk.core.Coin(oneEth)),
            Arguments.of(null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("logUnionRbtcReleasedArgProvider")
    void logUnionRbtcReleased_whenOk_shouldEmitEvent(RskAddress receiver, co.rsk.core.Coin amount) {
        // Act
        eventLogger.logUnionRbtcReleased(receiver, amount);

        // Assert
        commonAssertLogs();
        assertTopics(2);
        assertEvent(
            BridgeEvents.UNION_RBTC_RELEASED.getEvent(),
            new Object[]{receiver.toHexString()},
            new Object[]{amount.asBigInteger()}
        );
    }

    private static Stream<Arguments> logUnionRbtcReleasedArgProvider() {
        BigInteger oneRbtcInWeis = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
        co.rsk.core.Coin oneRbtc = new co.rsk.core.Coin(oneRbtcInWeis);
        co.rsk.core.Coin twoRbtc = new co.rsk.core.Coin(oneRbtcInWeis.multiply(BigInteger.valueOf(2L)));
        co.rsk.core.Coin threeRbtc = new co.rsk.core.Coin(oneRbtcInWeis.multiply(BigInteger.valueOf(3L)));

        return Stream.of(
            Arguments.of(UnionBridgeMainNetConstants.getInstance().getAddress(), oneRbtc),
            Arguments.of(UnionBridgeTestNetConstants.getInstance().getAddress(), twoRbtc),
            Arguments.of(UnionBridgeRegTestConstants.getInstance().getAddress(), threeRbtc)
        );
    }

    @Test
    void logUnionLockingCapIncreased_whenOk_shouldEmitEvent() {
        RskAddress caller = new RskAddress(ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
            .getAddress());

        UnionBridgeConstants unionBridgeConstants = UnionBridgeMainNetConstants.getInstance();
        int lockingCapIncrementsMultiplier = unionBridgeConstants.getLockingCapIncrementsMultiplier();
        co.rsk.core.Coin previousLockingCap = unionBridgeConstants.getInitialLockingCap().multiply(BigInteger.valueOf(lockingCapIncrementsMultiplier));
        co.rsk.core.Coin newLockingCap = previousLockingCap.multiply(BigInteger.valueOf(lockingCapIncrementsMultiplier));

        eventLogger.logUnionLockingCapIncreased(caller, previousLockingCap, newLockingCap);

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
            BridgeEvents.UNION_LOCKING_CAP_INCREASED.getEvent(),
            new Object[]{caller.toHexString()},
            new Object[]{previousLockingCap.asBigInteger(), newLockingCap.asBigInteger()}
        );
    }

    @ParameterizedTest
    @MethodSource("logUnionLockingCapIncreasedNullArgsProvider")
    void logUnionLockingCapIncreased_whenNullArgs_shouldFail(RskAddress caller, co.rsk.core.Coin previousLockingCap, co.rsk.core.Coin newLockingCap) {
        assertThrows(NullPointerException.class, () -> eventLogger.logUnionLockingCapIncreased(caller, previousLockingCap, newLockingCap));
    }

    private static Stream<Arguments> logUnionLockingCapIncreasedNullArgsProvider() {
        RskAddress caller = new RskAddress(ECKey.fromPublicOnly(Hex.decode(
            "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
            .getAddress());

        UnionBridgeConstants unionBridgeConstants = UnionBridgeMainNetConstants.getInstance();
        co.rsk.core.Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
        int lockingCapIncrementsMultiplier = unionBridgeConstants.getLockingCapIncrementsMultiplier();
        co.rsk.core.Coin previousLockingCap = initialLockingCap.multiply(BigInteger.valueOf(lockingCapIncrementsMultiplier));
        co.rsk.core.Coin newLockingCap = previousLockingCap.multiply(BigInteger.valueOf(lockingCapIncrementsMultiplier));

        return Stream.of(
            Arguments.of(null, previousLockingCap, newLockingCap),
            Arguments.of(caller, null, newLockingCap),
            Arguments.of(caller, previousLockingCap, null)
        );
    }

    @ParameterizedTest
    @MethodSource("logUnionBridgeTransferPermissionsUpdatedArgProvider")
    void logUnionBridgeTransferPermissionsUpdated_whenOk_shouldEmitEvent(RskAddress caller, boolean enablePowPegToUnionBridge, boolean enableUnionBridgeToPowPeg) {
        eventLogger.logUnionBridgeTransferPermissionsUpdated(caller, enablePowPegToUnionBridge, enableUnionBridgeToPowPeg);

        commonAssertLogs();
        assertTopics(2);
        assertEvent(
            BridgeEvents.UNION_BRIDGE_TRANSFER_PERMISSIONS_UPDATED.getEvent(),
            new Object[]{caller.toHexString()},
            new Object[]{enablePowPegToUnionBridge, enableUnionBridgeToPowPeg}
        );
    }

    private static Stream<Arguments> logUnionBridgeTransferPermissionsUpdatedArgProvider() {
        RskAddress caller = new RskAddress(ECKey.fromPublicOnly(Hex.decode(
                "04ea24f3943dff3b9b8abc59dbdf1bd2c80ec5b61f5c2c6dfcdc189299115d6d567df34c52b7e678cc9934f4d3d5491b6e53fa41a32f58a71200396f1e11917e8f"))
            .getAddress());

        return Stream.of(
            Arguments.of(caller, true, true),
            Arguments.of(caller, false, true),
            Arguments.of(caller, true, false),
            Arguments.of(caller, false, false)
        );
    }

    @ParameterizedTest
    @MethodSource("logUnionBridgeTransferPermissionsUpdatedInvalidArgProvider")
    void logUnionBridgeTransferPermissionsUpdated_whenInvalidArg_shouldFail(RskAddress caller, boolean enablePowPegToUnionBridge, boolean enableUnionBridgeToPowPeg) {
        assertThrows(NullPointerException.class,
            () -> eventLogger.logUnionBridgeTransferPermissionsUpdated(caller, enablePowPegToUnionBridge, enableUnionBridgeToPowPeg),
            "Caller cannot be null");
    }

    private static Stream<Arguments> logUnionBridgeTransferPermissionsUpdatedInvalidArgProvider() {
        return Stream.of(
            Arguments.of(null, true, true),
            Arguments.of(null, false, false)
        );
    }

    /**********************************
     *  -------     UTILS     ------- *
     *********************************/
    private void assertEvent(CallTransaction.Function event, Object[] topics, Object[] data) {
        LogInfo log = eventLogs.get(0);

        byte[][] expectedEventTopicsSerialized = event.encodeEventTopics(topics);
        List<DataWord> expectedEventTopics = LogInfo.byteArrayToList(expectedEventTopicsSerialized);
        assertEquals(expectedEventTopics, log.getTopics());
        
        byte[] expectedEventData = event.encodeEventData(data);
        assertArrayEquals(expectedEventData, log.getData());
    }

    private void assertTopics(int expectedTopicsSize) {
        int topicsSize = eventLogs.get(0).getTopics().size();

        assertEquals(expectedTopicsSize, topicsSize);
    }

    private void commonAssertLogs() {
        assertEquals(1, eventLogs.size());

        // Assert address that made the log
        LogInfo entry = eventLogs.get(0);
        byte[] entryAddressSerialized = entry.getAddress();
        assertArrayEquals(BRIDGE_ADDRESS_SERIALIZED, entryAddressSerialized);
        assertEquals(BRIDGE_ADDRESS, new RskAddress(entryAddressSerialized));
    }

    private byte[] serializeRskTxHashes(List<Keccak256> rskTxHashes) {
        List<byte[]> rskTxHashesList = rskTxHashes.stream()
            .map(Keccak256::getBytes)
            .toList();
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
