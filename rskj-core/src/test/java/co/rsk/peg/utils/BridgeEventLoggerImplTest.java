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
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class for BridgeEventLoggerImpl.
 *
 * @author martin.medina
 */

public class BridgeEventLoggerImplTest {

    private static final BridgeRegTestConstants CONSTANTS = BridgeRegTestConstants.getInstance();
    ActivationConfig.ForBlock activations;
    List<LogInfo> eventLogs;
    BridgeEventLogger eventLogger;
    BridgeConstants constantsMock;
    BtcTransaction btcTxMock;
    BtcTransaction btcTx;

    @Before
    public void setup() {
        activations = mock(ActivationConfig.ForBlock.class);
        eventLogs = new LinkedList<>();
        constantsMock = mock(BridgeConstants.class);
        eventLogger = new BridgeEventLoggerImpl(constantsMock, activations, eventLogs);
        btcTxMock = mock(BtcTransaction.class);
        btcTx = new BtcTransaction(CONSTANTS.getBtcParams());
    }

    @Test
    public void logLockBtc() {
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
    public void logLockBtc_with_segwit_address() {
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
    public void logPeginBtc() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

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
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);
        CallTransaction.Function event = BridgeEvents.PEGIN_BTC.getEvent();

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(3, logResult.getTopics().size());
        byte[][] topics = event.encodeEventTopics(rskAddress.toString(), mockedTx.getHash().getBytes());
        for (int i=0; i<topics.length; i++) {
            Assert.assertArrayEquals(topics[i], logResult.getTopics().get(i).getData());
        }

        // Assert log data
        byte[] encodedData = event.encodeEventData(amount.getValue(), protocolVersion);
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    public void logUpdateCollectionsBeforeRskip146HardFork() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        // Setup Rsk transaction
        Transaction tx = mock(Transaction.class);
        RskAddress sender = mock(RskAddress.class);
        when(sender.toString()).thenReturn("0x0000000000000000000000000000000000000001");
        when(tx.getSender()).thenReturn(sender);

        // Act
        eventLogger.logUpdateCollections(tx);

        commonAssertLogs(eventLogs);
        assertTopics(1, eventLogs);

        LogInfo logResult = eventLogs.get(0);
        List<DataWord> topics = Collections.singletonList(Bridge.UPDATE_COLLECTIONS_TOPIC);
        for (int i = 0; i < topics.size(); i++) {
            Assert.assertEquals(topics.get(i), logResult.getTopics().get(i));
        }

        // Assert log data
        byte[] encodedData = RLP.encodeElement(tx.getSender().getBytes());
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    public void logUpdateCollectionsAfterRskip146HardFork() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        // Setup Rsk transaction
        Transaction tx = mock(Transaction.class);
        RskAddress sender = mock(RskAddress.class);
        when(sender.toString()).thenReturn("0x0000000000000000000000000000000000000001");
        when(tx.getSender()).thenReturn(sender);

        // Act
        eventLogger.logUpdateCollections(tx);

        commonAssertLogs(eventLogs);
        assertTopics(1, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.UPDATE_COLLECTIONS.getEvent(), new Object[]{}, new Object[]{tx.getSender().toString()});
    }

    @Test
    public void logAddSignatureBeforeRskip146HardFork() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        // Setup logAddSignature params
        BtcECKey federatorPubKey = BtcECKey.fromPrivate(BigInteger.valueOf(2L));
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);
        when(btcTxMock.getHashAsString()).thenReturn("3e72fdbae7bbd103f08e876c765e3d5ba35db30ea46cb45ab52803f987ead9fb");

        // Act
        eventLogger.logAddSignature(federatorPubKey, btcTxMock, rskTxHash.getBytes());

        // Assert log size
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(1, logResult.getTopics().size());
        Assert.assertEquals(Bridge.ADD_SIGNATURE_TOPIC, logResult.getTopics().get(0));

        // Assert log data
        Assert.assertNotNull(logResult.getData());
        List<RLPElement> rlpData = RLP.decode2(logResult.getData());
        Assert.assertEquals(1, rlpData.size());
        RLPList dataList = (RLPList) rlpData.get(0);
        Assert.assertEquals(3, dataList.size());
        Assert.assertArrayEquals(btcTxMock.getHashAsString().getBytes(), dataList.get(0).getRLPData());
        Assert.assertArrayEquals(federatorPubKey.getPubKeyHash(), dataList.get(1).getRLPData());
        Assert.assertArrayEquals(rskTxHash.getBytes(), dataList.get(2).getRLPData());
    }

    @Test
    public void logAddSignatureAfterRskip146HardFork() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        // Setup logAddSignature params
        BtcECKey federatorPubKey = BtcECKey.fromPrivate(BigInteger.valueOf(2L));
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);
        when(btcTxMock.getHashAsString()).thenReturn("3e72fdbae7bbd103f08e876c765e3d5ba35db30ea46cb45ab52803f987ead9fb");

        // Act
        eventLogger.logAddSignature(federatorPubKey, btcTxMock, rskTxHash.getBytes());

        commonAssertLogs(eventLogs);
        assertTopics(3, eventLogs);
        ECKey key = ECKey.fromPublicOnly(federatorPubKey.getPubKey());
        String federatorRskAddress = ByteUtil.toHexString(key.getAddress());
        assertEvent(eventLogs, 0, BridgeEvents.ADD_SIGNATURE.getEvent(), new Object[]{rskTxHash.getBytes(), federatorRskAddress}, new Object[]{federatorPubKey.getPubKey()});
    }

    @Test
    public void logReleaseBtcBeforeRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        // Setup Btc transaction
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);

        // Act
        eventLogger.logReleaseBtc(btcTx, rskTxHash.getBytes());

        commonAssertLogs(eventLogs);
        assertTopics(1, eventLogs);

        LogInfo logResult = eventLogs.get(0);

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(1, logResult.getTopics().size());
        List<DataWord> topics = Collections.singletonList(Bridge.RELEASE_BTC_TOPIC);
        for (int i = 0; i < topics.size(); i++) {
            Assert.assertEquals(topics.get(i), logResult.getTopics().get(i));
        }

        // Assert log data
        byte[] encodedData = RLP.encodeList(RLP.encodeString(btcTx.getHashAsString()), RLP.encodeElement(btcTx.bitcoinSerialize()));
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    public void logReleaseBtcAfterRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        // Setup Btc transaction
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);

        // Act
        eventLogger.logReleaseBtc(btcTx, rskTxHash.getBytes());

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.RELEASE_BTC.getEvent(), new Object[]{rskTxHash.getBytes()}, new Object[]{btcTx.bitcoinSerialize()});
    }

    @Test
    public void logCommitFederationBeforeRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);
        when(constantsMock.getFederationActivationAge()).thenReturn(CONSTANTS.getFederationActivationAge());

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

        Federation oldFederation = new Federation(oldFederationMembers,
                Instant.ofEpochMilli(15005L), 15L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        List<BtcECKey> newFederationKeys = Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
                BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
                BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338"))
        );

        List<FederationMember> newFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(newFederationKeys);

        Federation newFederation = new Federation(
                newFederationMembers,
                Instant.ofEpochMilli(5005L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        // Act
        eventLogger.logCommitFederation(executionBlock, oldFederation, newFederation);

        // Assert log size
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(1, logResult.getTopics().size());
        Assert.assertEquals(Bridge.COMMIT_FEDERATION_TOPIC, logResult.getTopics().get(0));

        // Assert log data
        Assert.assertNotNull(logResult.getData());
        List<RLPElement> rlpData = RLP.decode2(logResult.getData());
        Assert.assertEquals(1, rlpData.size());
        RLPList dataList = (RLPList) rlpData.get(0);
        Assert.assertEquals(3, dataList.size());

        // Assert old federation data
        RLPList oldFedData = (RLPList) dataList.get(0);
        Assert.assertEquals(2, oldFedData.size());
        Assert.assertArrayEquals(oldFederation.getAddress().getHash160(), oldFedData.get(0).getRLPData());

        RLPList oldFedPubKeys = (RLPList) oldFedData.get(1);
        Assert.assertEquals(4, oldFedPubKeys.size());
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(oldFederation.getBtcPublicKeys().get(i), BtcECKey.fromPublicOnly(oldFedPubKeys.get(i).getRLPData()));
        }

        // Assert new federation data
        RLPList newFedData = (RLPList) dataList.get(1);
        Assert.assertEquals(2, newFedData.size());
        Assert.assertArrayEquals(newFederation.getAddress().getHash160(), newFedData.get(0).getRLPData());

        RLPList newFedPubKeys = (RLPList) newFedData.get(1);
        Assert.assertEquals(3, newFedPubKeys.size());
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(newFederation.getBtcPublicKeys().get(i), BtcECKey.fromPublicOnly(newFedPubKeys.get(i).getRLPData()));
        }

        // Assert new federation activation block number
        Assert.assertEquals(15L + CONSTANTS.getFederationActivationAge(), Long.valueOf(new String(dataList.get(2).getRLPData(), StandardCharsets.UTF_8)).longValue());
    }

    @Test
    public void logCommitFederationAfterRskip146() {
        // Setup event logger
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(constantsMock.getFederationActivationAge()).thenReturn(CONSTANTS.getFederationActivationAge());

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

        Federation oldFederation = new Federation(
                oldFederationMembers,
                Instant.ofEpochMilli(15005L),
                15L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        List<BtcECKey> newFederationKeys = Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
                BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
                BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338"))
        );

        List<FederationMember> newFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(newFederationKeys);

        Federation newFederation = new Federation(newFederationMembers,
                Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        // Act
        eventLogger.logCommitFederation(executionBlock, oldFederation, newFederation);

        commonAssertLogs(eventLogs);

        assertTopics(1, eventLogs);
        // Assert log data
        byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();
        long newFedActivationBlockNumber = executionBlock.getNumber() + constantsMock.getFederationActivationAge();
        Object[] data = new Object[]{
                oldFederationFlatPubKeys,
                oldFederationBtcAddress,
                newFederationFlatPubKeys,
                newFederationBtcAddress,
                newFedActivationBlockNumber
        };
        assertEvent(eventLogs, 0, BridgeEvents.COMMIT_FEDERATION.getEvent(), new Object[]{}, data);
    }

    @Test
    public void logReleaseBtcRequested() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Keccak256 rskTxHash = PegTestUtils.createHash3(0);
        Coin amount = Coin.SATOSHI;

        eventLogger.logReleaseBtcRequested(rskTxHash.getBytes(), btcTx, amount);

        commonAssertLogs(eventLogs);

        assertTopics(3, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.RELEASE_REQUESTED.getEvent(), new Object[]{rskTxHash.getBytes(), btcTx.getHash().getBytes()}, new Object[]{amount.getValue()});
    }

    @Test
    public void logRejectedPegin() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        BtcTransaction btcTx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());

        eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.PEGIN_CAP_SURPASSED);

        Assert.assertEquals(1, eventLogs.size());
        LogInfo entry = eventLogs.get(0);

        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(entry.getAddress()));

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        Assert.assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assert.assertEquals(2, result.getTopics().size());
        CallTransaction.Function event = BridgeEvents.REJECTED_PEGIN.getEvent();

        byte[][] topics = event.encodeEventTopics(btcTx.getHash().getBytes());

        for (int i=0; i<topics.length; i++) {
            Assert.assertArrayEquals(topics[i], result.getTopics().get(i).getData());
        }

        // Assert log data
        Assert.assertArrayEquals(event.encodeEventData(RejectedPeginReason.PEGIN_CAP_SURPASSED.getValue()), result.getData());
    }

    @Test
    public void logUnrefundablePegin() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        BtcTransaction btcTx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());

        eventLogger.logUnrefundablePegin(btcTx, UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);

        Assert.assertEquals(1, eventLogs.size());
        LogInfo entry = eventLogs.get(0);

        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(entry.getAddress()));

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        Assert.assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assert.assertEquals(2, result.getTopics().size());
        CallTransaction.Function event = BridgeEvents.UNREFUNDABLE_PEGIN.getEvent();

        byte[][] topics = event.encodeEventTopics(btcTx.getHash().getBytes());

        for (int i=0; i<topics.length; i++) {
            Assert.assertArrayEquals(topics[i], result.getTopics().get(i).getData());
        }

        // Assert log data
        Assert.assertArrayEquals(event.encodeEventData(UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER.getValue()), result.getData());
    }


    @Test
    public void testLogReleaseBtcRequestReceived() {
        String sender = "0x00000000000000000000000000000000000000";
        byte[] btcDestinationAddress = "1234".getBytes();
        Coin amount = Coin.COIN;

        eventLogger.logReleaseBtcRequestReceived(sender, btcDestinationAddress, amount);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent(), new Object[]{sender}, new Object[]{btcDestinationAddress, amount.value});
    }


    @Test
    public void testLogReleaseBtcRequestRejected() {
        String sender = "0x00000000000000000000000000000000000000";
        Coin amount = Coin.COIN;
        RejectedPegoutReason reason = RejectedPegoutReason.LOW_AMOUNT;

        eventLogger.logReleaseBtcRequestRejected(sender, amount, reason);

        commonAssertLogs(eventLogs);
        assertTopics(2, eventLogs);
        assertEvent(eventLogs, 0, BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent(), new Object[]{sender}, new Object[]{amount.value, reason.getValue()});
    }

    @Test
    public void logBatchPegoutCreated() {
        List<Keccak256> rskTxHashes = Arrays.asList(PegTestUtils.createHash3(0), PegTestUtils.createHash3(1), PegTestUtils.createHash3(2));

        eventLogger.logBatchPegoutCreated(btcTx, rskTxHashes);

        commonAssertLogs(eventLogs);

        assertTopics(2, eventLogs);

        assertEvent(eventLogs, 0, BridgeEvents.BATCH_PEGOUT_CREATED.getEvent(), new Object[]{btcTx.getHash().getBytes()}, new Object[]{serializeRskTxHashes(rskTxHashes)});
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
