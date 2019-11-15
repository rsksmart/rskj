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
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Test class for BridgeEventLoggerImpl.
 *
 * @author martin.medina
 */

public class BridgeEventLoggerImplTest {

    @Test
    public void logLockBtc() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        Address senderAddress = mock(Address.class);
        when(senderAddress.toString()).thenReturn("mixzLp4xx5bUsHuYUEyPpL42BzEDp8kSTv");

        RskAddress rskAddress = mock(RskAddress.class);
        when(rskAddress.toString()).thenReturn("0x00000000000000000000000000000000000000");

        // Mock btc transaction
        BtcTransaction mockedTx = mock(BtcTransaction.class);
        when(mockedTx.getHash()).thenReturn(PegTestUtils.createHash(0));

        Coin amount = Coin.SATOSHI;

        // Act
        eventLogger.logLockBtc(rskAddress, mockedTx, senderAddress, amount);

        // Assert log size
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);
        CallTransaction.Function event = BridgeEvents.LOCK_BTC.getEvent();

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(2, logResult.getTopics().size());
        byte[][] topics = event.encodeEventTopics(rskAddress.toString());
        for (int i=0; i<topics.length; i++) {
            Assert.assertArrayEquals(topics[i], logResult.getTopics().get(i).getData());
        }

        // Assert log data
        byte[] encodedData = event.encodeEventData(mockedTx.getHash().getBytes(), senderAddress.toString(), amount.getValue());
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    public void logUpdateCollectionsBeforeRskip146HardFork() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        // Setup Rsk transaction
        Transaction tx = mock(Transaction.class);
        RskAddress sender = mock(RskAddress.class);
        when(sender.toString()).thenReturn("0x0000000000000000000000000000000000000001");
        when(tx.getSender()).thenReturn(sender);

        // Act
        eventLogger.logUpdateCollections(tx);

        // Assert log size
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(1, logResult.getTopics().size());
        List<DataWord> topics = Collections.singletonList(Bridge.UPDATE_COLLECTIONS_TOPIC);
        for (int i=0; i<topics.size(); i++) {
            Assert.assertEquals(topics.get(i), logResult.getTopics().get(i));
        }

        // Assert log data
        byte[] encodedData = RLP.encodeElement(tx.getSender().getBytes());
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    public void logUpdateCollectionsAfterRskip146HardFork() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        // Setup Rsk transaction
        Transaction tx = mock(Transaction.class);
        RskAddress sender = mock(RskAddress.class);
        when(sender.toString()).thenReturn("0x0000000000000000000000000000000000000001");
        when(tx.getSender()).thenReturn(sender);

        // Act
        eventLogger.logUpdateCollections(tx);

        // Assert log size
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);
        CallTransaction.Function event = BridgeEvents.UPDATE_COLLECTIONS.getEvent();

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(1, logResult.getTopics().size());
        byte[][] topics = event.encodeEventTopics();
        for (int i=0; i<topics.length; i++) {
            Assert.assertArrayEquals(topics[i], logResult.getTopics().get(i).getData());
        }

        // Assert log data
        byte[] encodedData = event.encodeEventData(tx.getSender().toString());
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    public void logAddSignatureBeforeRskip146HardFork() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        // Setup logAddSignature params
        BtcECKey federatorPubKey = BtcECKey.fromPrivate(BigInteger.valueOf(2L));
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);
        when(btcTx.getHashAsString()).thenReturn("3e72fdbae7bbd103f08e876c765e3d5ba35db30ea46cb45ab52803f987ead9fb");

        // Act
        eventLogger.logAddSignature(federatorPubKey, btcTx, rskTxHash.getBytes());

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
        Assert.assertArrayEquals(btcTx.getHashAsString().getBytes(), dataList.get(0).getRLPData());
        Assert.assertArrayEquals(federatorPubKey.getPubKeyHash(), dataList.get(1).getRLPData());
        Assert.assertArrayEquals(rskTxHash.getBytes(), dataList.get(2).getRLPData());
    }

    @Test
    public void logAddSignatureAfterRskip146HardFork() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        // Setup logAddSignature params
        BtcECKey federatorPubKey = BtcECKey.fromPrivate(BigInteger.valueOf(2L));
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);
        when(btcTx.getHashAsString()).thenReturn("3e72fdbae7bbd103f08e876c765e3d5ba35db30ea46cb45ab52803f987ead9fb");

        // Act
        eventLogger.logAddSignature(federatorPubKey, btcTx, rskTxHash.getBytes());

        // Assert log size
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);
        CallTransaction.Function event = BridgeEvents.ADD_SIGNATURE.getEvent();

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(3, logResult.getTopics().size());
        ECKey key = ECKey.fromPublicOnly(federatorPubKey.getPubKey());
        String federatorRskAddress = Hex.toHexString(key.getAddress());
        byte[][] topics = event.encodeEventTopics(rskTxHash.getBytes(), federatorRskAddress);
        for (int i=0; i<topics.length; i++) {
            Assert.assertArrayEquals(topics[i], logResult.getTopics().get(i).getData());
        }

        // Assert log data
        byte[] encodedData = event.encodeEventData(federatorPubKey.getPubKey());
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    public void logReleaseBtcBeforeRskip146() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        // Setup Btc transaction
        BtcTransaction btcTx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);

        // Act
        eventLogger.logReleaseBtc(btcTx, rskTxHash.getBytes());

        // Assert log size
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(1, logResult.getTopics().size());
        List<DataWord> topics = Collections.singletonList(Bridge.RELEASE_BTC_TOPIC);
        for (int i=0; i<topics.size(); i++) {
            Assert.assertEquals(topics.get(i), logResult.getTopics().get(i));
        }

        // Assert log data
        byte[] encodedData = RLP.encodeList(RLP.encodeString(btcTx.getHashAsString()), RLP.encodeElement(btcTx.bitcoinSerialize()));
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    public void logReleaseBtcAfterRskip146() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        // Setup Btc transaction
        BtcTransaction btcTx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);

        // Act
        eventLogger.logReleaseBtc(btcTx, rskTxHash.getBytes());

        // Assert log size
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_BTC.getEvent();

        // Assert address that made the log
        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        Assert.assertEquals(2, logResult.getTopics().size());
        byte[][] topics = event.encodeEventTopics(rskTxHash.getBytes());
        for (int i=0; i<topics.length; i++) {
            Assert.assertArrayEquals(topics[i], logResult.getTopics().get(i).getData());
        }

        // Assert log data
        byte[] encodedData = event.encodeEventData(btcTx.bitcoinSerialize());
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    public void logCommitFederationBeforeRskip146() {
        // Setup event logger
        BridgeConstants constantsMock = mock(BridgeConstants.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);
        when(constantsMock.getFederationActivationAge()).thenReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge());
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(constantsMock, activations, eventLogs);

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
        Assert.assertEquals(1 , rlpData.size());
        RLPList dataList = (RLPList)rlpData.get(0);
        Assert.assertEquals(3, dataList.size());

        // Assert old federation data
        RLPList oldFedData = (RLPList) dataList.get(0);
        Assert.assertEquals(2, oldFedData.size());
        Assert.assertArrayEquals(oldFederation.getAddress().getHash160(), oldFedData.get(0).getRLPData());

        RLPList oldFedPubKeys = (RLPList) oldFedData.get(1);
        Assert.assertEquals(4, oldFedPubKeys.size());
        for(int i = 0; i < 4; i++) {
            Assert.assertEquals(oldFederation.getBtcPublicKeys().get(i), BtcECKey.fromPublicOnly(oldFedPubKeys.get(i).getRLPData()));
        }

        // Assert new federation data
        RLPList newFedData = (RLPList) dataList.get(1);
        Assert.assertEquals(2, newFedData.size());
        Assert.assertArrayEquals(newFederation.getAddress().getHash160(), newFedData.get(0).getRLPData());

        RLPList newFedPubKeys = (RLPList) newFedData.get(1);
        Assert.assertEquals(3, newFedPubKeys.size());
        for(int i = 0; i < 3; i++) {
            Assert.assertEquals(newFederation.getBtcPublicKeys().get(i), BtcECKey.fromPublicOnly(newFedPubKeys.get(i).getRLPData()));
        }

        // Assert new federation activation block number
        Assert.assertEquals(15L + BridgeRegTestConstants.getInstance().getFederationActivationAge(), Long.valueOf(new String(dataList.get(2).getRLPData(), StandardCharsets.UTF_8)).longValue());
    }

    @Test
    public void logCommitFederationAfterRskip146() {
        // Setup event logger
        BridgeConstants constantsMock = mock(BridgeConstants.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(constantsMock.getFederationActivationAge()).thenReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge());
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(constantsMock, activations, eventLogs);

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

        // Assert log size
        Assert.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);
        CallTransaction.Function event = BridgeEvents.COMMIT_FEDERATION.getEvent();

        // Assert log topics
        Assert.assertEquals(1, logResult.getTopics().size());
        byte[][] topics = event.encodeEventTopics();
        for (int i=0; i<topics.length; i++) {
            Assert.assertArrayEquals(topics[i], logResult.getTopics().get(i).getData());
        }

        // Assert log data
        byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();
        long newFedActivationBlockNumber = executionBlock.getNumber() + constantsMock.getFederationActivationAge();

        byte[] encodedData = event.encodeEventData(
                oldFederationFlatPubKeys,
                oldFederationBtcAddress,
                newFederationFlatPubKeys,
                newFederationBtcAddress,
                newFedActivationBlockNumber
        );
        Assert.assertArrayEquals(encodedData, logResult.getData());
    }

    private byte[] flatKeysAsByteArray(List<BtcECKey> keys) {
        List<byte[]> pubKeys = keys.stream()
                .map(BtcECKey::getPubKey)
                .collect(Collectors.toList());
        int pubKeysLength = pubKeys.stream().mapToInt(key -> key.length).sum();

        byte[] flatPubKeys = new byte[pubKeysLength];
        int copyPos = 0;
        for(byte[] key : pubKeys) {
            System.arraycopy(key, 0, flatPubKeys, copyPos, key.length);
            copyPos += key.length;
        }

        return flatPubKeys;
    }

    @Test
    public void logReleaseBtcRequested() {
        // Setup event logger
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> eventLogs = new LinkedList<>();

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(null, activations, eventLogs);

        Keccak256 rskTxHash = PegTestUtils.createHash3(0);
        BtcTransaction btcTx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());
        Coin amount = Coin.SATOSHI;

        eventLogger.logReleaseBtcRequested(rskTxHash.getBytes(), btcTx, amount);

        Assert.assertEquals(1, eventLogs.size());
        LogInfo entry = eventLogs.get(0);

        Assert.assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(entry.getAddress()));

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        Assert.assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assert.assertEquals(3, result.getTopics().size());
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUESTED.getEvent();

        byte[][] topics = event.encodeEventTopics(rskTxHash.getBytes(), btcTx.getHash().getBytes());

        for (int i=0; i<topics.length; i++) {
            Assert.assertArrayEquals(topics[i], result.getTopics().get(i).getData());
        }

        // Assert log data
        Assert.assertArrayEquals(event.encodeEventData(amount.getValue()), result.getData());
    }
}
