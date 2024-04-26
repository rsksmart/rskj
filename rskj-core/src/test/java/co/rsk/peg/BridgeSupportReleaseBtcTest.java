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
package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.utils.RejectedPegoutReason;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.ethereum.vm.program.Program;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BridgeSupportReleaseBtcTest {

    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger DUST_AMOUNT = new BigInteger("1");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final ECKey SENDER = new ECKey();

    private BridgeConstants bridgeConstants;
    private ActivationConfig.ForBlock activationsBeforeForks;
    private ActivationConfig.ForBlock activationMock = mock(ActivationConfig.ForBlock.class);
    private Federation activeFederation;
    private Repository repository;
    private BridgeEventLogger eventLogger;
    private UTXO utxo;
    private BridgeStorageProvider provider;
    private BridgeSupport bridgeSupport;
    private Transaction releaseTx;
    private BridgeSupportBuilder bridgeSupportBuilder;
    private SignatureCache signatureCache;

    @BeforeEach
    void setUpOnEachTest() throws IOException {
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        bridgeConstants = BridgeRegTestConstants.getInstance();
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
        activeFederation = getFederation();
        repository = spy(createRepository());
        eventLogger = mock(BridgeEventLogger.class);
        utxo = buildUTXO();
        provider = initProvider(repository, activationMock);
        bridgeSupportBuilder = new BridgeSupportBuilder();
        bridgeSupport = spy(initBridgeSupport(eventLogger, activationMock));
        releaseTx = buildReleaseRskTx();
    }

    @Test
    void noLogEvents_before_rskip_146_185() throws IOException {
        provider = initProvider(repository, activationsBeforeForks);
        bridgeSupport = initBridgeSupport(eventLogger, activationsBeforeForks);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void eventLogger_logReleaseBtcRequested_after_rskip_146() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void eventLogger_logReleaseBtcRequested_after_rskip_146_185() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(0)).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void eventLogger_logReleaseBtcRequested_after_rskip_146_185_326() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP326)).thenReturn(true);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(0)).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void eventLogger_logReleaseBtcRequested_release_before_activation_and_updateCollections_after_activation() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(false);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);

        bridgeSupport.releaseBtc(releaseTx);

        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);

        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void handmade_release_before_rskip_146_185() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(false);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(new ECKey().getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        verify(eventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void handmade_release_after_rskip_146() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(new ECKey().getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        verify(eventLogger, times(1)).logReleaseBtcRequested(
            any(byte[].class),
            any(BtcTransaction.class),
            any(Coin.class)
        );
    }

    @Test
    void handmade_release_after_rskip_146_185() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP326)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());

        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(3, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequested(
            any(byte[].class),
            any(BtcTransaction.class),
            any(Coin.class)
        );
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logUpdateCollections(any());

        LogInfo logInfo1 = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent();
        Object btcDestinationAddress = event.decodeEventData(logInfo1.getData())[0];
        Assertions.assertTrue(btcDestinationAddress instanceof byte[]);
    }

    @Test
    void handmade_release_after_rskip_146_185_326() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP326)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();

        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());

        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(3, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequested(
                any(byte[].class),
                any(BtcTransaction.class),
                any(Coin.class)
        );

        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logUpdateCollections(any());

        LogInfo logInfo1 = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();
        Object btcDestinationAddress = event.decodeEventData(logInfo1.getData())[0];
        Assertions.assertTrue(btcDestinationAddress instanceof  String);
    }

    @Test
    void handmade_release_after_rskip_146_rejected_lowAmount() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);


        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        releaseTx = buildReleaseRskTx(Coin.ZERO);
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());

        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());

        Assertions.assertEquals(1, logInfo.size());

        verify(eventLogger, times(1)).logUpdateCollections(any());
    }

    @Test
    void handmade_release_after_rskip_146_185_rejected_lowAmount() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);


        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        releaseTx = buildReleaseRskTx(Coin.ZERO);
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, times(1)).transfer(
            argThat((a) -> a.equals(PrecompiledContracts.BRIDGE_ADDR)),
            argThat((a) -> a.equals(new RskAddress(SENDER.getAddress()))),
            argThat((a) -> a.equals(co.rsk.core.Coin.fromBitcoin(Coin.ZERO)))
        );

        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(2, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
        verify(eventLogger, times(1)).logUpdateCollections(any());
    }


    @Test
    void handmade_release_after_rskip_146_185_rejected_contractCaller() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);


        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        releaseTx = buildReleaseRskTx_fromContract(Coin.COIN);
        bridgeSupport.releaseBtc(releaseTx);

        // Create Contract transaction
        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(
            any(), any(), any()
        );

        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        Assertions.assertEquals(2, logInfo.size());

        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
        verify(eventLogger, times(1)).logUpdateCollections(any());
    }

    @Test
    void handmade_release_after_rskip_146_rejected_contractCaller() throws IOException {

        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache);
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        releaseTx = buildReleaseRskTx_fromContract(Coin.COIN);
        try {
            bridgeSupport.releaseBtc(releaseTx);
            Assertions.fail();
        } catch (Program.OutOfGasException e) {
            Assertions.assertTrue(e.getMessage().contains("Contract calling releaseBTC"));
        }
    }

    @Test
    void release_after_rskip_219() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value between old and new minimum pegout values
        Coin middle = bridgeConstants.getLegacyMinimumPegoutTxValue().subtract(bridgeConstants.getMinimumPegoutTxValue()).div(2);
        Coin value = bridgeConstants.getMinimumPegoutTxValue().add(middle);
        Assertions.assertTrue(value.isLessThan(bridgeConstants.getLegacyMinimumPegoutTxValue()));
        Assertions.assertTrue(value.isGreaterThan(bridgeConstants.getMinimumPegoutTxValue()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        Assertions.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(1, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());

        LogInfo logInfo1 = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent();
        Object btcDestinationAddress = event.decodeEventData(logInfo1.getData())[0];
        Assertions.assertTrue(btcDestinationAddress instanceof byte[]);

    }

    @Test
    void release_after_rskip_219_326() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP326)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value between old and new minimum pegout values
        Coin middle = bridgeConstants.getLegacyMinimumPegoutTxValue().subtract(bridgeConstants.getMinimumPegoutTxValue()).div(2);
        Coin value = bridgeConstants.getMinimumPegoutTxValue().add(middle);
        Assertions.assertTrue(value.isLessThan(bridgeConstants.getLegacyMinimumPegoutTxValue()));
        Assertions.assertTrue(value.isGreaterThan(bridgeConstants.getMinimumPegoutTxValue()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        Assertions.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(1, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());

        LogInfo logInfo1 = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();
        Object btcDestinationAddress = event.decodeEventData(logInfo1.getData())[0];
        Assertions.assertTrue(btcDestinationAddress instanceof  String);
    }

    @Test
    void release_before_rskip_219() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value between old and new minimum pegout values
        Coin middle = bridgeConstants.getLegacyMinimumPegoutTxValue().subtract(bridgeConstants.getMinimumPegoutTxValue()).div(2);
        Coin value = bridgeConstants.getMinimumPegoutTxValue().add(middle);
        Assertions.assertTrue(value.isLessThan(bridgeConstants.getLegacyMinimumPegoutTxValue()));
        Assertions.assertTrue(value.isGreaterThan(bridgeConstants.getMinimumPegoutTxValue()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void release_before_rskip_219_minimum_exclusive() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value exactly to legacy minimum
        Coin value = bridgeConstants.getLegacyMinimumPegoutTxValue();
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void release_after_rskip_219_minimum_inclusive() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value exactly to current minimum
        Coin value = bridgeConstants.getMinimumPegoutTxValue();
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        Assertions.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(1, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());

        LogInfo logInfo1 = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent();
        Object btcDestinationAddress = event.decodeEventData(logInfo1.getData())[0];
        Assertions.assertTrue(btcDestinationAddress instanceof byte[]);
    }

    @Test
    void release_after_rskip_219_326_minimum_inclusive() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP326)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value exactly to current minimum
        Coin value = bridgeConstants.getMinimumPegoutTxValue();
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        Assertions.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(1, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());

        LogInfo logInfo1 = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();
        Object btcDestinationAddress = event.decodeEventData(logInfo1.getData())[0];
        Assertions.assertTrue(btcDestinationAddress instanceof  String);

    }

    @Test
    void release_verify_fee_below_fee_is_rejected() throws IOException {
        Coin value = bridgeConstants.getMinimumPegoutTxValue().add(Coin.SATOSHI);

        testPegoutMinimumWithFeeVerification(Coin.COIN, value, false);
    }

    @Test
    void release_verify_fee_above_fee_but_below_gap_is_rejected_before_rskip_271() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);
        Coin feePerKB = Coin.COIN;

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(activationMock, provider.getNewFederation());
        Coin value = feePerKB.div(1000).times(pegoutSize);

        testPegoutMinimumWithFeeVerification(feePerKB, value, false);
    }

    @Test
    void release_verify_fee_above_fee_but_below_gap_is_rejected_after_rskip_271() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        Coin feePerKB = Coin.COIN;

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(activationMock, provider.getNewFederation());
        Coin value = feePerKB.div(1000).times(pegoutSize);

        testPegoutMinimumWithFeeVerification(feePerKB, value, false);
    }

    @Test
    void release_verify_fee_above_fee_but_below_minimum_is_rejected() throws IOException {
        testPegoutMinimumWithFeeVerification(
            Coin.MILLICOIN,
            bridgeConstants.getMinimumPegoutTxValue().minus(Coin.SATOSHI),
            false
        );
    }

    @Test
    void release_verify_fee_above_fee_and_minimum_is_accepted() throws IOException {
        testPegoutMinimumWithFeeVerification(Coin.COIN, Coin.FIFTY_COINS, true);
    }

    @Test
    void test_processPegoutsIndividually_before_RSKIP271_activation() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(3), genesisFederation.getAddress()));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN))));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // assert pegouts were not batched
        Assertions.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        verify(provider, never()).getNextPegoutHeight();
        verify(provider, never()).setNextPegoutHeight(any(Long.class));
    }

    @Test
    void test_processPegoutsInBatch_after_RSKIP271() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(4), genesisFederation.getAddress()));

        ReleaseRequestQueue pegoutRequests = new ReleaseRequestQueue(
            Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN)));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(pegoutRequests);
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        Coin totalValue = pegoutRequests.getEntries()
            .stream()
            .map(ReleaseRequestQueue.Entry::getAmount)
            .reduce(Coin.ZERO, Coin::add);

        List<Keccak256> rskHashesList = pegoutRequests.getEntries()
            .stream()
            .map(ReleaseRequestQueue.Entry::getRskTxHash)
            .collect(Collectors.toList());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withEventLogger(eventLogger)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        BtcTransaction generatedTransaction = provider.getPegoutsWaitingForConfirmations().getEntries().iterator().next().getBtcTransaction();

        verify(provider, times(1)).getNextPegoutHeight();
        verify(provider, times(1)).setNextPegoutHeight(any(Long.class));

        verify(eventLogger, times(1)).logBatchPegoutCreated(generatedTransaction.getHash(), rskHashesList);
        verify(eventLogger, times(1)).logReleaseBtcRequested(rskTx.getHash().getBytes(), generatedTransaction, totalValue);
    }

    @Test
    void test_processPegoutsInBatch_after_RSKIP271_activation_next_pegout_height_not_reached() throws IOException {

        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(100L);

        long executionBlockNumber = executionBlock.getNumber();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNextPegoutHeight()).thenReturn(Optional.of(executionBlockNumber + bridgeConstants.getNumberOfBlocksBetweenPegouts() - 1));
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(
                Arrays.asList(
                    new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN),
                    new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN))));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activationMock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(provider, times(1)).getNextPegoutHeight();
        verify(provider, never()).setNextPegoutHeight(any(Long.class));

        Assertions.assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void test_processPegoutsInBatch_after_RSKIP271_activation_no_requests_in_queue_updates_next_pegout_height() throws IOException {

        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNextPegoutHeight()).thenReturn(Optional.of(100L));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(100L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        long nextPegoutHeight = executionBlock.getNumber() + bridgeConstants.getNumberOfBlocksBetweenPegouts();

        verify(provider, times(1)).getNextPegoutHeight();
        verify(provider, times(1)).setNextPegoutHeight(nextPegoutHeight);
    }

    @Test
    void test_processPegoutsInBatch_after_rskip_271_Insufficient_Money() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(2, 0, Coin.COIN.multiply(4), genesisFederation.getAddress()));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(
                Arrays.asList(
                    new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN),
                    new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN),
                    new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN),
                    new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN),
                    new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN))));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // Insufficient_Money i.e 4 BTC UTXO Available For 5 BTC Transaction.
        // Pegout requests can't be processed and remains in the queue
        Assertions.assertEquals(5, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void test_processPegoutsInBatch_after_rskip_271_divide_transaction_when_max_size_exceeded() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = PegTestUtils.createUTXOs(610, genesisFederation.getAddress());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(PegTestUtils.createReleaseRequestQueueEntries(600)));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // First Half of the PegoutRequests 600 / 2 = 300 Is Batched For The First Time
        Assertions.assertEquals(300, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // The Rest PegoutRequests 600 / 2 = 300 Is Batched The 2nd Time updateCollections Is Called
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(2, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void test_processPegoutsInBatch_after_rskip_271_when_max_size_exceeded_for_one_pegout() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = PegTestUtils.createUTXOs(700, genesisFederation.getAddress());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(
            new ReleaseRequestQueue(Collections.singletonList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(700)))));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        Assertions.assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void test_processPegoutsInBatch_after_rskip_271_when_max_size_exceeded_for_two_pegout() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = PegTestUtils.createUTXOs(1400, genesisFederation.getAddress());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(700)),
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(700)))));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        Assertions.assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void test_processPegoutsIndividually_before_rskip_271_no_funds_to_process_any_requests() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN, genesisFederation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(2, 1, Coin.COIN, genesisFederation.getAddress()));

        List<ReleaseRequestQueue.Entry> entries = Arrays.asList(
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(5)),
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(3))
        );

        ReleaseRequestQueue originalPegoutRequests = new ReleaseRequestQueue(entries);
        ReleaseRequestQueue pegoutRequests = new ReleaseRequestQueue(entries);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(pegoutRequests);
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        Assertions.assertEquals(originalPegoutRequests, provider.getReleaseRequestQueue());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void test_processPegoutsIndividually_before_rskip_271_no_funds_to_process_any_requests_order_changes_in_queue() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN, genesisFederation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(2, 1, Coin.COIN, genesisFederation.getAddress()));

        List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();
        int entriesSizeAboveMaxIterations = BridgeSupport.MAX_RELEASE_ITERATIONS + 10;
        for (int i = 0; i < entriesSizeAboveMaxIterations; i++) {
            entries.add(
                new ReleaseRequestQueue.Entry(
                    BitcoinTestUtils.createP2PKHAddress(bridgeConstants.getBtcParams(), String.valueOf(i)),
                    Coin.COIN.multiply(5)
                )
            );
        }

        List<ReleaseRequestQueue.Entry> expectedEntries = new ArrayList<>();
        expectedEntries.addAll(entries.subList(BridgeSupport.MAX_RELEASE_ITERATIONS, entries.size()));
        expectedEntries.addAll(entries.subList(0, BridgeSupport.MAX_RELEASE_ITERATIONS));

        ReleaseRequestQueue expectedPegoutRequests = new ReleaseRequestQueue(expectedEntries);
        ReleaseRequestQueue originalPegoutRequests = new ReleaseRequestQueue(entries);
        ReleaseRequestQueue pegoutRequests = new ReleaseRequestQueue(entries);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(pegoutRequests);
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        Assertions.assertNotEquals(originalPegoutRequests, provider.getReleaseRequestQueue());

        Assertions.assertEquals(expectedPegoutRequests, provider.getReleaseRequestQueue());

        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void test_check_wallet_balance_before_rskip_271_process_at_least_one_request() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(2), genesisFederation.getAddress()));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(4)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(3)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(2)))));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // 2 remains in queue, 1 is processed to the transaction set
        Assertions.assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void test_check_wallet_balance_after_rskip_271_process_no_requests() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(4), genesisFederation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(2, 1, Coin.COIN.multiply(4), genesisFederation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(3, 2, Coin.COIN.multiply(3), genesisFederation.getAddress()));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(5)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(4)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(3)))));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .withEventLogger(eventLogger)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        Assertions.assertEquals(3, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        verify(eventLogger, never()).logBatchPegoutCreated(any(), any());
        verify(provider, never()).setNextPegoutHeight(any(Long.class));
    }

    @Test
    void test_check_wallet_balance_after_rskip_271_process_all_requests_when_utxos_available() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstants);
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(4), genesisFederation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(2, 1, Coin.COIN.multiply(4), genesisFederation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(3, 2, Coin.COIN.multiply(3), genesisFederation.getAddress()));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(5)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(4)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(3)))));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .withEventLogger(eventLogger)
            .build();

        // First Call To updateCollections
        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        Assertions.assertEquals(3, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        verify(eventLogger, never()).logBatchPegoutCreated(any(), any());
        verify(provider, never()).setNextPegoutHeight(any(Long.class));

        utxos.add(PegTestUtils.createUTXO(4, 3, Coin.COIN.multiply(1), genesisFederation.getAddress()));
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .withEventLogger(eventLogger)
            .build();

        // Second Call To updateCollections
        Transaction rskTx2 = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx2);

        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        verify(eventLogger, times(1)).logBatchPegoutCreated(any(), any());
        verify(provider, times(1)).setNextPegoutHeight(any(Long.class));
    }

    private void testPegoutMinimumWithFeeVerification(Coin feePerKB, Coin value, boolean shouldPegout)
        throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo, signatureCache));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        provider.setFeePerKb(feePerKB);

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(activationMock, provider.getNewFederation());
        Coin minValueAccordingToFee = provider.getFeePerKb().div(1000).times(pegoutSize);
        Coin minValueWithGapAboveFee = minValueAccordingToFee.add(minValueAccordingToFee.times(bridgeConstants.getMinimumPegoutValuePercentageToReceiveAfterFee()).div(100));
        // if shouldPegout true then value should be greater or equals than both required fee plus gap and min pegout value
        // if shouldPegout false then value should be smaller than any of those minimums
        Assertions.assertEquals(!shouldPegout, value.isLessThan(minValueWithGapAboveFee) ||
            value.isLessThan(bridgeConstants.getMinimumPegoutTxValue()));

        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, shouldPegout ? never() : times(1)).transfer(any(), any(), any());

        Assertions.assertEquals(shouldPegout ? 1 : 0, provider.getReleaseRequestQueue().getEntries().size());

        Assertions.assertEquals(1, logInfo.size());
        verify(eventLogger, shouldPegout ? times(1) : never()).logReleaseBtcRequestReceived(any(), any(), any());
        ArgumentCaptor<RejectedPegoutReason> argumentCaptor = ArgumentCaptor.forClass(RejectedPegoutReason.class);
        verify(eventLogger, shouldPegout ? never() : times(1)).logReleaseBtcRequestRejected(any(), any(), argumentCaptor.capture());
        if (!shouldPegout) {
            // Verify rejected pegout reason using value in comparison with fee and pegout minimum
            Assertions.assertEquals(
                value.isLessThan(minValueWithGapAboveFee) ?
                    RejectedPegoutReason.FEE_ABOVE_VALUE :
                    RejectedPegoutReason.LOW_AMOUNT,
                argumentCaptor.getValue()
            );
        }
    }

    /**********************************
     *  -------     UTILS     ------- *
     *********************************/

    private UTXO buildUTXO() {
        return new UTXO(Sha256Hash.wrap(TestUtils.generateBytes("utxo",32)), 0, Coin.COIN.multiply(2), 1, false, activeFederation.getP2SHScript());
    }

    private Transaction buildReleaseRskTx() {
        return buildReleaseRskTx(Coin.COIN);
    }

    private Transaction buildReleaseRskTx(Coin coin) {
        Transaction releaseTx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(PrecompiledContracts.BRIDGE_ADDR.toHexString())
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(co.rsk.core.Coin.fromBitcoin(coin).asBigInteger())
            .build();
        releaseTx.sign(SENDER.getPrivKeyBytes());
        return releaseTx;
    }

    private Transaction buildReleaseRskTx_fromContract(Coin coin) {
        Transaction releaseTx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(PrecompiledContracts.BRIDGE_ADDR.toHexString())
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(co.rsk.core.Coin.fromBitcoin(coin).asBigInteger())
            .build();
        releaseTx.sign(SENDER.getPrivKeyBytes());
        return new InternalTransaction(releaseTx.getHash().getBytes(), 400, 0, NONCE.toByteArray(),
            DataWord.valueOf(GAS_PRICE.intValue()), DataWord.valueOf(GAS_LIMIT.intValue()), SENDER.getAddress(),
            PrecompiledContracts.BRIDGE_ADDR.getBytes(), co.rsk.core.Coin.fromBitcoin(Coin.COIN).getBytes(),
            Hex.decode(DATA), "", new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
    }

    private Transaction buildUpdateTx() {
        return Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
    }

    private BridgeSupport initBridgeSupport(BridgeEventLogger eventLogger, ActivationConfig.ForBlock activationMock) {
        return bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withRepository(repository)
            .withEventLogger(eventLogger)
            .withActivations(activationMock)
            .withSignatureCache(signatureCache)
            .build();
    }

    private BridgeStorageProvider initProvider(Repository repository, ActivationConfig.ForBlock activationMock) throws IOException {
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationMock);
        provider.getNewFederationBtcUTXOs().add(utxo);
        provider.setNewFederation(activeFederation);
        return provider;
    }

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    private static Federation getFederation() {
        FederationArgs federationArgs = new FederationArgs(FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

}
