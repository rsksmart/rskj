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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.Bridge;
import co.rsk.peg.DeprecatedMethodCallException;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.util.*;
import org.ethereum.vm.*;
import org.junit.jupiter.api.*;

/**
 * Test class for BridgeEventLoggerLegacyImpl.
 *
 * @author kelvin.isievwore
 */
class BridgeEventLoggerLegacyImplTest {
    private ActivationConfig.ForBlock activations;
    private List<LogInfo> eventLogs;
    private BridgeEventLogger eventLogger;
    private BridgeConstants constantsMock;
    private BtcTransaction btcTxMock;
    private Keccak256 rskTxHash;

    @BeforeEach
    void setup() {
        activations = mock(ActivationConfig.ForBlock.class);
        eventLogs = new LinkedList<>();
        constantsMock = mock(BridgeConstants.class);
        eventLogger = new BrigeEventLoggerLegacyImpl(constantsMock, activations, eventLogs);
        btcTxMock = mock(BtcTransaction.class);
        rskTxHash = RskTestUtils.createHash(1);
    }

    @Test
    void testLogUpdateCollectionsBeforeRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        // Setup Rsk transaction
        Transaction tx = mock(Transaction.class);
        RskAddress sender = new RskAddress("0x0000000000000000000000000000000000000001");
        when(tx.getSender(any(SignatureCache.class))).thenReturn(sender);

        // Act
        eventLogger.logUpdateCollections(sender);

        commonAssertLogs(eventLogs);
        assertTopics(1, eventLogs);

        LogInfo logResult = eventLogs.get(0);
        List<DataWord> topics = Collections.singletonList(Bridge.UPDATE_COLLECTIONS_TOPIC);
        for (int i = 0; i < topics.size(); i++) {
            assertEquals(topics.get(i), logResult.getTopics().get(i));
        }

        // Assert log data
        byte[] encodedData = RLP.encodeElement(tx.getSender(new BlockTxSignatureCache(new ReceivedTxSignatureCache())).getBytes());
        assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    void testLogUpdateCollectionsAfterRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        assertThrows(DeprecatedMethodCallException.class, () -> eventLogger.logUpdateCollections(any(RskAddress.class)));
    }

    @Test
    void testLogAddSignatureBeforeRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        // Setup logAddSignature params
        BtcECKey federatorPubKey = BtcECKey.fromPrivate(BigInteger.valueOf(2L));
        FederationMember federationMember = FederationTestUtils.getFederationMemberWithKey(federatorPubKey);
        when(btcTxMock.getHashAsString()).thenReturn("3e72fdbae7bbd103f08e876c765e3d5ba35db30ea46cb45ab52803f987ead9fb");

        // Act
        eventLogger.logAddSignature(federationMember, btcTxMock, rskTxHash.getBytes());

        // Assert log size
        Assertions.assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);

        // Assert address that made the log
        assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        assertEquals(1, logResult.getTopics().size());
        assertEquals(Bridge.ADD_SIGNATURE_TOPIC, logResult.getTopics().get(0));

        // Assert log data
        assertNotNull(logResult.getData());
        List<RLPElement> rlpData = RLP.decode2(logResult.getData());
        Assertions.assertEquals(1, rlpData.size());
        RLPList dataList = (RLPList) rlpData.get(0);
        assertEquals(3, dataList.size());
        assertArrayEquals(btcTxMock.getHashAsString().getBytes(), dataList.get(0).getRLPData());
        assertArrayEquals(federatorPubKey.getPubKeyHash(), dataList.get(1).getRLPData());
        assertArrayEquals(rskTxHash.getBytes(), dataList.get(2).getRLPData());
    }

    @Test
    void testLogAddSignatureAfterRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        BtcECKey federatorPublicKey = new BtcECKey();
        FederationMember federationMember = FederationTestUtils.getFederationMemberWithKey(federatorPublicKey);
        byte[] bytes = rskTxHash.getBytes();
        assertThrows(DeprecatedMethodCallException.class, () -> eventLogger.logAddSignature(federationMember, btcTxMock, bytes));
    }

    @Test
    void testLogReleaseBtcBeforeRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        // Act
        NetworkParameters regtestParameters = (new BridgeRegTestConstants()).getBtcParams();
        BtcTransaction btcTx = new BtcTransaction(regtestParameters);

        eventLogger.logReleaseBtc(btcTx, rskTxHash.getBytes());

        commonAssertLogs(eventLogs);
        assertTopics(1, eventLogs);

        LogInfo logResult = eventLogs.get(0);

        // Assert address that made the log
        assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        assertEquals(1, logResult.getTopics().size());
        List<DataWord> topics = Collections.singletonList(Bridge.RELEASE_BTC_TOPIC);
        for (int i = 0; i < topics.size(); i++) {
            assertEquals(topics.get(i), logResult.getTopics().get(i));
        }

        // Assert log data
        byte[] encodedData = RLP.encodeList(RLP.encodeString(btcTx.getHashAsString()), RLP.encodeElement(btcTx.bitcoinSerialize()));
        assertArrayEquals(encodedData, logResult.getData());
    }

    @Test
    void testLogReleaseBtcAfterRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        // Act
        byte[] bytes = rskTxHash.getBytes();
        assertThrows(DeprecatedMethodCallException.class, () -> eventLogger.logReleaseBtc(btcTxMock, bytes));
    }

    @Test
    void testLogCommitFederationBeforeRskip146() {
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        // Setup parameters for test method call
        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        when(constantsMock.getFederationConstants()).thenReturn((new BridgeRegTestConstants()).getFederationConstants());

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
        FederationArgs oldFedArgs = new FederationArgs(oldFederationMembers, Instant.ofEpochMilli(15005L), 15L, btcParams);

        Federation oldFederation = FederationFactory.buildStandardMultiSigFederation(oldFedArgs);

        List<BtcECKey> newFederationKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
            BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
            BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338"))
        );
        List<FederationMember> newFederationMembers = FederationTestUtils.getFederationMembersWithBtcKeys(newFederationKeys);
        FederationArgs newFedArgs  = new FederationArgs(newFederationMembers, Instant.ofEpochMilli(15005L), 15L, btcParams);

        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFedArgs);

        // Act
        eventLogger.logCommitFederation(executionBlock, oldFederation, newFederation);

        // Assert log size
        assertEquals(1, eventLogs.size());

        LogInfo logResult = eventLogs.get(0);

        // Assert address that made the log
        assertEquals(PrecompiledContracts.BRIDGE_ADDR, new RskAddress(logResult.getAddress()));

        // Assert log topics
        assertEquals(1, logResult.getTopics().size());
        assertEquals(Bridge.COMMIT_FEDERATION_TOPIC, logResult.getTopics().get(0));

        // Assert log data
        assertNotNull(logResult.getData());
        List<RLPElement> rlpData = RLP.decode2(logResult.getData());
        assertEquals(1, rlpData.size());
        RLPList dataList = (RLPList) rlpData.get(0);
        assertEquals(3, dataList.size());

        // Assert old federation data
        RLPList oldFedData = (RLPList) dataList.get(0);
        assertEquals(2, oldFedData.size());
        assertArrayEquals(oldFederation.getAddress().getHash160(), oldFedData.get(0).getRLPData());

        RLPList oldFedPubKeys = (RLPList) oldFedData.get(1);
        assertEquals(4, oldFedPubKeys.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(oldFederation.getBtcPublicKeys().get(i), BtcECKey.fromPublicOnly(oldFedPubKeys.get(i).getRLPData()));
        }

        // Assert new federation data
        RLPList newFedData = (RLPList) dataList.get(1);
        assertEquals(2, newFedData.size());
        assertArrayEquals(newFederation.getAddress().getHash160(), newFedData.get(0).getRLPData());

        RLPList newFedPubKeys = (RLPList) newFedData.get(1);
        assertEquals(3, newFedPubKeys.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(
                newFederation.getBtcPublicKeys().get(i),
                BtcECKey.fromPublicOnly(newFedPubKeys.get(i).getRLPData())
            );
        }

        // Assert new federation activation block number
        FederationConstants federationConstants = constantsMock.getFederationConstants();
        assertEquals(
            15L + federationConstants.getFederationActivationAge(activations),
            Long.valueOf(new String(dataList.get(2).getRLPData(), StandardCharsets.UTF_8)).longValue()
        );
    }

    @Test
    void testLogCommitFederationAfterRskip146() {
        // Setup event logger
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        // Act
        assertThrows(
            DeprecatedMethodCallException.class,
            () -> eventLogger.logCommitFederation(mock(Block.class), mock(Federation.class), mock(Federation.class))
        );
    }

    @Test
    void testLogCommitFederationFailure_throwsUnsupportedOperationException() {
        // Setup event logger
        activations = ActivationConfigsForTest.all().forBlock(0);

        // Act
        assertThrows(UnsupportedOperationException.class, () -> eventLogger.logCommitFederationFailure(mock(Block.class), mock(Federation.class)));
    }

    /**********************************
     *  -------     UTILS     ------- *
     *********************************/

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
}
