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

import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.*;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import co.rsk.test.builders.UTXOBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.ethereum.vm.program.Program;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BridgeSupportReleaseBtcTest {
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final ECKey SENDER = RskTestUtils.getEcKeyFromSeed("sender");
    private static final RskAddress BRIDGE_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock ACTIVATIONS_ALL = ActivationConfigsForTest.all().forBlock(0L);

    private Federation activeFederation;
    private Repository repository;
    private BridgeEventLogger eventLogger;
    private BridgeStorageProvider provider;
    private FederationStorageProvider federationStorageProvider;
    private BridgeSupport bridgeSupport;
    private Transaction releaseTx;
    private BridgeSupportBuilder bridgeSupportBuilder;
    private SignatureCache signatureCache;
    private FeePerKbSupport feePerKbSupport;

    @BeforeEach
    void setUpOnEachTest() {
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        activeFederation = P2shErpFederationBuilder.builder().build();
        repository = spy(RskTestUtils.createRepository());
        eventLogger = mock(BridgeEventLogger.class);
        provider = initProvider();
        federationStorageProvider = initFederationStorageProvider();
        bridgeSupportBuilder = BridgeSupportBuilder.builder();
        feePerKbSupport = mock(FeePerKbSupportImpl.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.valueOf(5_000L));
        bridgeSupport = spy(initBridgeSupport(eventLogger, ACTIVATIONS_ALL));
        releaseTx = buildReleaseRskTx(co.rsk.core.Coin.fromBitcoin(Coin.COIN));
    }

    @Test
    void noLogEvents_before_papyrus() throws IOException {
        ActivationConfig.ForBlock wasabiActivation = ActivationConfigsForTest.wasabi100().forBlock(0L);

        bridgeSupport = initBridgeSupport(eventLogger, wasabiActivation);
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void eventLogger_logReleaseBtcRequested_after_papyrus_before_iris() throws IOException {
        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0L);

        bridgeSupport = initBridgeSupport(eventLogger, papyrusActivations);
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void eventLogger_logReleaseBtcRequested_after_iris() throws IOException {
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void eventLogger_logReleaseBtcRequested_release_before_papyrus_and_updateCollections_after_papyrus() throws IOException {
        ActivationConfig.ForBlock wasabiActivations = ActivationConfigsForTest.wasabi100().forBlock(0L);
        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0L);

        bridgeSupport = initBridgeSupport(eventLogger, wasabiActivations);
        bridgeSupport.releaseBtc(releaseTx);

        bridgeSupport = initBridgeSupport(eventLogger, papyrusActivations);
        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void release_before_papyrus_no_events_emitted() throws IOException {
        ActivationConfig.ForBlock wasabiActivations = ActivationConfigsForTest.wasabi100().forBlock(0L);

        bridgeSupport = initBridgeSupport(eventLogger, wasabiActivations);
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(new ECKey().getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        verify(eventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void release_after_papyrus_before_iris() throws IOException {
        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0L);

        bridgeSupport = initBridgeSupport(eventLogger, papyrusActivations);
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(new ECKey().getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        verify(eventLogger, times(1)).logReleaseBtcRequested(
            any(byte[].class),
            any(BtcTransaction.class),
            any(Coin.class)
        );
    }

    @Test
    void release_after_iris_before_fingerroot() throws IOException {
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0L);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, hopActivations, logInfo));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, hopActivations);

        // Get a value between old and new minimum pegout values
        Coin middle = BRIDGE_CONSTANTS.getLegacyMinimumPegoutTxValue().subtract(BRIDGE_CONSTANTS.getMinimumPegoutTxValue()).div(2);
        Coin value = BRIDGE_CONSTANTS.getMinimumPegoutTxValue().add(middle);
        assertTrue(value.isLessThan(BRIDGE_CONSTANTS.getLegacyMinimumPegoutTxValue()));
        assertTrue(value.isGreaterThan(BRIDGE_CONSTANTS.getMinimumPegoutTxValue()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(co.rsk.core.Coin.fromBitcoin(value)));

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(4, logInfo.size());
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(
            any(byte[].class),
            any(BtcTransaction.class),
            any(Coin.class)
        );
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(bridgeEventLogger, times(1)).logUpdateCollections(any());
        verify(bridgeEventLogger, times(1)).logBatchPegoutCreated(any(), any());

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        Object btcDestinationAddress = event.decodeEventData(firstLog.getData())[0];
        assertInstanceOf(byte[].class, btcDestinationAddress);
    }

    @Test
    void logReleaseBtcRequestReceived_after_fingerroot_before_lovell_shouldLogValueInSatoshis() throws IOException {
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0L);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            arrowheadActivations,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, arrowheadActivations);

        co.rsk.core.Coin pegoutRequestValue = co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMinimumPegoutTxValue());
        bridgeSupport.releaseBtc(buildReleaseRskTx(pegoutRequestValue));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(4, logInfo.size());
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(
            any(byte[].class),
            any(BtcTransaction.class),
            any(Coin.class)
        );
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(bridgeEventLogger, times(1)).logUpdateCollections(any());
        verify(bridgeEventLogger, times(1)).logBatchPegoutCreated(any(), any());

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();
        Object btcDestinationAddress = event.decodeEventData(firstLog.getData())[0];
        BigInteger amount = (BigInteger) event.decodeEventData(firstLog.getData())[1];

        assertInstanceOf(String.class, btcDestinationAddress);
        assertEquals(pegoutRequestValue.toBitcoin().longValue(), amount.longValue());
    }

    @Test
    void release_after_lovell_logPegoutTransactionCreated_use_value_in_weis() throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, ACTIVATIONS_ALL);

        co.rsk.core.Coin pegoutRequestValue = co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMinimumPegoutTxValue());
        bridgeSupport.releaseBtc(buildReleaseRskTx(pegoutRequestValue));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(5, logInfo.size());
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(
            any(byte[].class),
            any(BtcTransaction.class),
            any(Coin.class)
        );
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(bridgeEventLogger, times(1)).logUpdateCollections(any());
        verify(bridgeEventLogger, times(1)).logBatchPegoutCreated(any(), any());
        verify(bridgeEventLogger, times(1)).logPegoutTransactionCreated(any(), any());

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();
        Object btcDestinationAddress = event.decodeEventData(firstLog.getData())[0];
        BigInteger amount = (BigInteger) event.decodeEventData(firstLog.getData())[1];

        assertInstanceOf(String.class, btcDestinationAddress);
        assertEquals(pegoutRequestValue.asBigInteger(), amount);
    }

    @Test
    void release_after_papyrus_before_iris_rejected_lowAmount() throws IOException {
        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0L);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            papyrusActivations,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, papyrusActivations);

        releaseTx = buildReleaseRskTx(co.rsk.core.Coin.ZERO);
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        verify(bridgeEventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());

        assertEquals(1, logInfo.size());
        verify(bridgeEventLogger, times(1)).logUpdateCollections(any());
    }

    @Test
    void release_after_iris_rejected_lowAmount() throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, ACTIVATIONS_ALL);

        releaseTx = buildReleaseRskTx(co.rsk.core.Coin.ZERO);
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, times(1)).transfer(
            argThat(a -> a.equals(BRIDGE_ADDRESS)),
            argThat(a -> a.equals(new RskAddress(SENDER.getAddress()))),
            argThat(a -> a.equals(co.rsk.core.Coin.fromBitcoin(Coin.ZERO)))
        );

        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(2, logInfo.size());
        verify(bridgeEventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
        verify(bridgeEventLogger, times(1)).logUpdateCollections(any());
    }

    @Test
    void release_after_papyrus_before_iris_rejected_contractCaller_throws_exception() {
        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0L);

        bridgeSupport = initBridgeSupport(eventLogger, papyrusActivations);
        releaseTx = buildReleaseRskTx_fromContract(co.rsk.core.Coin.fromBitcoin(Coin.COIN));

        assertThrows(Program.OutOfGasException.class, () -> bridgeSupport.releaseBtc(releaseTx));
    }

    @Test
    void release_after_iris_rejected_contractCaller_emits_rejection_event() throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, ACTIVATIONS_ALL);

        releaseTx = buildReleaseRskTx_fromContract(co.rsk.core.Coin.fromBitcoin(Coin.COIN));
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(
            any(), any(), any()
        );

        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        verify(bridgeEventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        assertEquals(2, logInfo.size());

        verify(bridgeEventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
        verify(bridgeEventLogger, times(1)).logUpdateCollections(any());
    }

    @Test
    void release_after_fingerroot() throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, ACTIVATIONS_ALL);

        // Get a value between old and new minimum pegout values
        Coin middle = BRIDGE_CONSTANTS.getLegacyMinimumPegoutTxValue().subtract(BRIDGE_CONSTANTS.getMinimumPegoutTxValue()).div(2);
        Coin value = BRIDGE_CONSTANTS.getMinimumPegoutTxValue().add(middle);
        assertTrue(value.isLessThan(BRIDGE_CONSTANTS.getLegacyMinimumPegoutTxValue()));
        assertTrue(value.isGreaterThan(BRIDGE_CONSTANTS.getMinimumPegoutTxValue()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(co.rsk.core.Coin.fromBitcoin(value)));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        Object btcDestinationAddress = event.decodeEventData(firstLog.getData())[0];
        assertInstanceOf(String.class, btcDestinationAddress);
    }

    @Test
    void release_before_iris() throws IOException {
        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0L);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            papyrusActivations,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, papyrusActivations);

        // Get a value between old and new minimum pegout values
        Coin middle = BRIDGE_CONSTANTS.getLegacyMinimumPegoutTxValue().subtract(BRIDGE_CONSTANTS.getMinimumPegoutTxValue()).div(2);
        Coin value = BRIDGE_CONSTANTS.getMinimumPegoutTxValue().add(middle);
        assertTrue(value.isLessThan(BRIDGE_CONSTANTS.getLegacyMinimumPegoutTxValue()));
        assertTrue(value.isGreaterThan(BRIDGE_CONSTANTS.getMinimumPegoutTxValue()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(co.rsk.core.Coin.fromBitcoin(value)));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(0, logInfo.size());
        verify(bridgeEventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(bridgeEventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void release_before_iris_minimum_exclusive() throws IOException {
        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0L);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            papyrusActivations,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, papyrusActivations);

        // Get a value exactly to legacy minimum
        Coin value = BRIDGE_CONSTANTS.getLegacyMinimumPegoutTxValue();
        bridgeSupport.releaseBtc(buildReleaseRskTx(co.rsk.core.Coin.fromBitcoin(value)));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(0, logInfo.size());
        verify(bridgeEventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(bridgeEventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void release_after_iris_before_fingerroot_minimum_inclusive() throws IOException {
        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            irisActivations,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, irisActivations);

        // Get a value exactly to current minimum
        Coin value = BRIDGE_CONSTANTS.getMinimumPegoutTxValue();
        bridgeSupport.releaseBtc(buildReleaseRskTx(co.rsk.core.Coin.fromBitcoin(value)));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        Object btcDestinationAddress = event.decodeEventData(firstLog.getData())[0];
        assertInstanceOf(byte[].class, btcDestinationAddress);
    }

    @Test
    void release_after_fingerroot_minimum_inclusive() throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(bridgeEventLogger, ACTIVATIONS_ALL);

        // Get a value exactly to current minimum
        Coin value = BRIDGE_CONSTANTS.getMinimumPegoutTxValue();
        bridgeSupport.releaseBtc(buildReleaseRskTx(co.rsk.core.Coin.fromBitcoin(value)));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        Object btcDestinationAddress = event.decodeEventData(firstLog.getData())[0];
        assertInstanceOf(String.class, btcDestinationAddress);
    }

    @Test
    void release_verify_fee_below_fee_is_rejected() throws IOException {
        Coin value = BRIDGE_CONSTANTS.getMinimumPegoutTxValue().add(Coin.SATOSHI);
        testPegoutMinimumWithFeeVerificationRejectedByFeeAboveValue(Coin.COIN, co.rsk.core.Coin.fromBitcoin(value));
    }

    @Test
    void release_verify_fee_above_fee_but_below_gap_is_rejected_before_hop() throws IOException {
        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);
        Coin feePerKB = Coin.COIN;

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(
            irisActivations,
            federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, irisActivations)
        );
        Coin value = feePerKB.div(1000).times(pegoutSize);
        testPegoutMinimumWithFeeVerificationRejectedByFeeAboveValue(feePerKB, co.rsk.core.Coin.fromBitcoin(value));
    }

    @Test
    void release_verify_fee_above_fee_but_below_gap_is_rejected_after_hop() throws IOException {
        Coin feePerKB = Coin.COIN;

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(
            ACTIVATIONS_ALL,
            federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, ACTIVATIONS_ALL)
        );
        Coin value = feePerKB.div(1000).times(pegoutSize);
        testPegoutMinimumWithFeeVerificationRejectedByFeeAboveValue(feePerKB, co.rsk.core.Coin.fromBitcoin(value));
    }

    @Test
    void release_verify_fee_above_fee_but_below_minimum_is_rejected() throws IOException {
        Coin valueBelowMinimum =  BRIDGE_CONSTANTS.getMinimumPegoutTxValue().minus(Coin.SATOSHI);
        testPegoutMinimumWithFeeVerificationRejectedByLowAmount(
            Coin.MILLICOIN,
            co.rsk.core.Coin.fromBitcoin(valueBelowMinimum)
        );
    }

    @Test
    void release_verify_fee_above_fee_and_minimum_is_accepted() throws IOException {
        testPegoutMinimumWithFeeVerificationPass(Coin.COIN, co.rsk.core.Coin.fromBitcoin(Coin.FIFTY_COINS));
    }

    @Test
    void processPegoutsIndividually_before_hop() throws IOException {
        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);

        List<UTXO> utxos = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(activeFederation.getAddress());
        Coin value = Coin.COIN.multiply(3);
        UTXO utxo = UTXOBuilder.builder()
            .withTransactionHash(createHash(1))
            .withHeight(10)
            .withValue(value)
            .withOutputScript(outputScript)
            .build();
        utxos.add(utxo);

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, irisActivations)).thenReturn(utxos);

        BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.COIN),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "two"), Coin.COIN))
        ));
        when(bridgeStorageProvider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(bridgeStorageProvider)
            .withActivations(irisActivations)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // assert pegouts were not batched
        assertEquals(1, bridgeStorageProvider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size());

        verify(bridgeStorageProvider, never()).getNextPegoutHeight();
        verify(bridgeStorageProvider, never()).setNextPegoutHeight(any(Long.class));
    }

    @Test
    void processPegoutsInBatch_after_hop() throws IOException {
        List<UTXO> utxos = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(activeFederation.getAddress());
        Coin value = Coin.COIN.multiply(4);
        UTXO utxo = UTXOBuilder.builder()
            .withTransactionHash(createHash(1))
            .withValue(value)
            .withHeight(10)
            .withOutputScript(outputScript)
            .build();
        utxos.add(utxo);

        ReleaseRequestQueue pegoutRequests = new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.MILLICOIN),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "two"), Coin.MILLICOIN),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "three"), Coin.MILLICOIN)
        ));

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL)).thenReturn(utxos);

        provider = mock(BridgeStorageProvider.class);
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

        bridgeSupport = bridgeSupportBuilder
            .withActivations(ACTIVATIONS_ALL)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withEventLogger(eventLogger)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        BtcTransaction generatedTransaction = provider.getPegoutsWaitingForConfirmations().getEntries().iterator().next().getBtcTransaction();

        verify(provider, times(1)).getNextPegoutHeight();
        verify(provider, times(1)).setNextPegoutHeight(any(Long.class));

        verify(eventLogger, times(1)).logBatchPegoutCreated(generatedTransaction.getHash(), rskHashesList);
        verify(eventLogger, times(1)).logReleaseBtcRequested(rskTx.getHash().getBytes(), generatedTransaction, totalValue);
    }

    @Test
    void processPegoutsInBatch_after_hop_next_pegout_height_not_reached() throws IOException {
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(100L);

        long executionBlockNumber = executionBlock.getNumber();

        provider = mock(BridgeStorageProvider.class);
        when(provider.getNextPegoutHeight()).thenReturn(Optional.of(executionBlockNumber + BRIDGE_CONSTANTS.getNumberOfBlocksBetweenPegouts() - 1));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.MILLICOIN),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "two"), Coin.MILLICOIN)
        )));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(ACTIVATIONS_ALL)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        verify(provider, times(1)).getNextPegoutHeight();
        verify(provider, never()).setNextPegoutHeight(any(Long.class));

        assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void processPegoutsInBatch_hop_activation_no_requests_in_queue_updates_next_pegout_height() throws IOException {
        provider = mock(BridgeStorageProvider.class);
        when(provider.getNextPegoutHeight()).thenReturn(Optional.of(100L));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(100L);

        bridgeSupport = bridgeSupportBuilder
            .withActivations(ACTIVATIONS_ALL)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        long nextPegoutHeight = executionBlock.getNumber() + BRIDGE_CONSTANTS.getNumberOfBlocksBetweenPegouts();

        verify(provider, times(1)).getNextPegoutHeight();
        verify(provider, times(1)).setNextPegoutHeight(nextPegoutHeight);
    }

    @Test
    void processPegoutsInBatch_after_hop_Insufficient_Money() throws IOException {
        List<UTXO> utxos = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(activeFederation.getAddress());
        Coin value = Coin.COIN.multiply(4);
        UTXO utxo = UTXOBuilder.builder()
            .withTransactionHash(createHash(2))
            .withValue(value)
            .withHeight(10)
            .withOutputScript(outputScript)
            .build();
        utxos.add(utxo);

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL)).thenReturn(utxos);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL)).thenReturn(utxos);

        provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.COIN),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "two"), Coin.COIN),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "three"), Coin.COIN),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "four"), Coin.COIN),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "five"), Coin.COIN)
        )));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withActivations(ACTIVATIONS_ALL)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // Insufficient_Money i.e 4 BTC UTXO Available For 5 BTC Transaction.
        // Pegout requests can't be processed and remains in the queue
        assertEquals(5, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void processPegoutsInBatch_after_hop_divide_transaction_when_max_size_exceeded() throws IOException {
        List<UTXO> utxos = PegTestUtils.createUTXOs(310, activeFederation.getAddress());

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL)).thenReturn(utxos);
        when(federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, ACTIVATIONS_ALL)).thenReturn(activeFederation);
        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(ACTIVATIONS_ALL)
            .build();

        provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(PegTestUtils.createReleaseRequestQueueEntries(300)));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withActivations(ACTIVATIONS_ALL)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withFederationSupport(federationSupport)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // First Half of the PegoutRequests 300 / 2 = 150 Is Batched For The First Time
        assertEquals(150, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // The Rest PegoutRequests 300 / 2 = 150 Is Batched The 2nd Time updateCollections Is Called
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(2, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void processPegoutsInBatch_after_hop_when_max_size_exceeded_for_one_pegout() throws IOException {
        List<UTXO> utxos = PegTestUtils.createUTXOs(700, activeFederation.getAddress());

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL)).thenReturn(utxos);

        provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.singletonList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.COIN.multiply(700))
        )));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withActivations(ACTIVATIONS_ALL)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void processPegoutsInBatch_after_hop_when_max_size_exceeded_for_two_pegout() throws IOException {
        List<UTXO> utxos = PegTestUtils.createUTXOs(1400, activeFederation.getAddress());

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL)).thenReturn(utxos);

        provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.COIN.multiply(700)),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "two"), Coin.COIN.multiply(700))
        )));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withActivations(ACTIVATIONS_ALL)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void processPegoutsIndividually_before_hop_no_funds_to_process_any_requests() throws IOException {
        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);

        List<UTXO> utxos = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(activeFederation.getAddress());
        UTXOBuilder utxoBuilder = UTXOBuilder.builder().withOutputScript(outputScript).withHeight(10);
        utxos.add(utxoBuilder.withTransactionHash(createHash(1)).withTransactionIndex(0).build());
        utxos.add(utxoBuilder.withTransactionHash(createHash(2)).withTransactionIndex(1).build());

        List<ReleaseRequestQueue.Entry> entries = Arrays.asList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.COIN.multiply(5)),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "two"), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "three"), Coin.COIN.multiply(3))
        );

        ReleaseRequestQueue originalPegoutRequests = new ReleaseRequestQueue(entries);
        ReleaseRequestQueue pegoutRequests = new ReleaseRequestQueue(entries);

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, irisActivations)).thenReturn(utxos);

        provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(pegoutRequests);
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withActivations(irisActivations)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(originalPegoutRequests, provider.getReleaseRequestQueue());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void processPegoutsIndividually_before_hop_no_funds_to_process_any_requests_order_changes_in_queue() throws IOException {
        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);

        List<UTXO> utxos = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(activeFederation.getAddress());
        UTXOBuilder utxoBuilder = UTXOBuilder.builder().withOutputScript(outputScript).withHeight(10);
        utxos.add(utxoBuilder.withTransactionHash(createHash(1)).withTransactionIndex(0).build());
        utxos.add(utxoBuilder.withTransactionHash(createHash(2)).withTransactionIndex(1).build());

        List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();
        int entriesSizeAboveMaxIterations = BridgeSupport.MAX_RELEASE_ITERATIONS + 10;
        for (int i = 0; i < entriesSizeAboveMaxIterations; i++) {
            entries.add(
                new ReleaseRequestQueue.Entry(
                    BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), String.valueOf(i)),
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

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, irisActivations)).thenReturn(utxos);

        provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(pegoutRequests);
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withActivations(irisActivations)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertNotEquals(originalPegoutRequests, provider.getReleaseRequestQueue());
        assertEquals(expectedPegoutRequests, provider.getReleaseRequestQueue());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void check_wallet_balance_before_hop_process_at_least_one_request() throws IOException {
        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);

        List<UTXO> utxos = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(activeFederation.getAddress());
        Coin value = Coin.COIN.multiply(2);
        UTXO utxo = UTXOBuilder.builder()
            .withTransactionHash(createHash(1))
            .withValue(value)
            .withHeight(10)
            .withOutputScript(outputScript)
            .build();
        utxos.add(utxo);

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, irisActivations)).thenReturn(utxos);

        provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "two"), Coin.COIN.multiply(3)),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "three"), Coin.COIN.multiply(2))
        )));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withActivations(irisActivations)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // 2 remains in queue, 1 is processed to the transaction set
        assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void check_wallet_balance_after_hop_process_no_requests() throws IOException {
        List<UTXO> utxos = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(activeFederation.getAddress());
        UTXOBuilder utxoBuilder = UTXOBuilder.builder().withOutputScript(outputScript).withHeight(10);
        utxos.add(utxoBuilder.withTransactionHash(createHash(1)).withTransactionIndex(0).withValue(Coin.COIN.multiply(4)).build());
        utxos.add(utxoBuilder.withTransactionHash(createHash(2)).withTransactionIndex(1).withValue(Coin.COIN.multiply(4)).build());
        utxos.add(utxoBuilder.withTransactionHash(createHash(3)).withTransactionIndex(2).withValue(Coin.COIN.multiply(3)).build());

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL)).thenReturn(utxos);

        provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.COIN.multiply(5)),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "two"), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "three"), Coin.COIN.multiply(3))
        )));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withActivations(ACTIVATIONS_ALL)
            .withEventLogger(eventLogger)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(3, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        verify(eventLogger, never()).logBatchPegoutCreated(any(), any());
        verify(provider, never()).setNextPegoutHeight(any(Long.class));
    }

    @Test
    void check_wallet_balance_after_hop_process_all_requests_when_utxos_available() throws IOException {
        List<UTXO> utxos = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(activeFederation.getAddress());
        UTXOBuilder utxoBuilder = UTXOBuilder.builder().withOutputScript(outputScript).withHeight(10);
        utxos.add(utxoBuilder.withTransactionHash(createHash(1)).withTransactionIndex(0).withValue(Coin.COIN.multiply(4)).build());
        utxos.add(utxoBuilder.withTransactionHash(createHash(2)).withTransactionIndex(1).withValue(Coin.COIN.multiply(4)).build());
        utxos.add(utxoBuilder.withTransactionHash(createHash(3)).withTransactionIndex(2).withValue(Coin.COIN.multiply(3)).build());

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL)).thenReturn(utxos);
        when(federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, ACTIVATIONS_ALL)).thenReturn(activeFederation);

        provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "one"), Coin.COIN.multiply(5)),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "two"), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(BitcoinTestUtils.createP2PKHAddress(BRIDGE_CONSTANTS.getBtcParams(), "three"), Coin.COIN.multiply(3))
        )));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withActivations(ACTIVATIONS_ALL)
            .withEventLogger(eventLogger)
            .build();

        // First Call To updateCollections
        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(3, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        verify(eventLogger, never()).logBatchPegoutCreated(any(), any());
        verify(provider, never()).setNextPegoutHeight(any(Long.class));

        utxos.add(utxoBuilder.withTransactionHash(createHash(4)).withTransactionIndex(3).withValue(Coin.COIN.multiply(1)).build());
        when(federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL)).thenReturn(utxos);
        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(ACTIVATIONS_ALL)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withActivations(ACTIVATIONS_ALL)
            .withEventLogger(eventLogger)
            .withFederationSupport(federationSupport)
            .build();

        // Second Call To updateCollections
        Transaction rskTx2 = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx2);

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        verify(eventLogger, times(1)).logBatchPegoutCreated(any(), any());
        verify(provider, times(1)).setNextPegoutHeight(any(Long.class));
    }

    private void testPegoutMinimumWithFeeVerificationPass(Coin feePerKB, co.rsk.core.Coin pegoutRequestedValue) throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        eventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(eventLogger, ACTIVATIONS_ALL);
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKB);

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(
            ACTIVATIONS_ALL,
            federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, ACTIVATIONS_ALL)
        );
        Coin minValueAccordingToFee = feePerKbSupport.getFeePerKb().div(1000).times(pegoutSize);
        Coin minValueWithGapAboveFee = minValueAccordingToFee.add(minValueAccordingToFee.times(
            BRIDGE_CONSTANTS.getMinimumPegoutValuePercentageToReceiveAfterFee()).div(100));

        Coin valueToRelease = pegoutRequestedValue.toBitcoin();
        assertFalse(valueToRelease.isLessThan(minValueWithGapAboveFee) ||
            valueToRelease.isLessThan(BRIDGE_CONSTANTS.getMinimumPegoutTxValue()));

        bridgeSupport.releaseBtc(buildReleaseRskTx(pegoutRequestedValue));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    private void testPegoutMinimumWithFeeVerificationRejectedByLowAmount(
        Coin feePerKB,
        co.rsk.core.Coin pegoutRequestValue
    ) throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        eventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(eventLogger, ACTIVATIONS_ALL);
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKB);

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(
            ACTIVATIONS_ALL,
            federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, ACTIVATIONS_ALL)
        );
        Coin minValueAccordingToFee = feePerKbSupport.getFeePerKb().div(1000).times(pegoutSize);
        Coin minValueWithGapAboveFee = minValueAccordingToFee.add(minValueAccordingToFee.times(
            BRIDGE_CONSTANTS.getMinimumPegoutValuePercentageToReceiveAfterFee()).div(100));

        Coin valueToRelease = pegoutRequestValue.toBitcoin();
        assertTrue(valueToRelease.isGreaterThan(minValueWithGapAboveFee));

        bridgeSupport.releaseBtc(buildReleaseRskTx(pegoutRequestValue));

        RskAddress senderAddress = new RskAddress(SENDER.getAddress());

        verify(repository, times(1)).transfer(BRIDGE_ADDRESS, senderAddress, pegoutRequestValue);
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, logInfo.size());

        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(
            senderAddress,
            pegoutRequestValue,
            RejectedPegoutReason.LOW_AMOUNT
        );
    }

    private void testPegoutMinimumWithFeeVerificationRejectedByFeeAboveValue(
        Coin feePerKB,
        co.rsk.core.Coin pegoutRequestValue
    ) throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        eventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(eventLogger, ACTIVATIONS_ALL);
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKB);

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(
            ACTIVATIONS_ALL,
            federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, ACTIVATIONS_ALL)
        );
        Coin minValueAccordingToFee = feePerKbSupport.getFeePerKb().div(1000).times(pegoutSize);
        Coin minValueWithGapAboveFee = minValueAccordingToFee.add(minValueAccordingToFee.times(
            BRIDGE_CONSTANTS.getMinimumPegoutValuePercentageToReceiveAfterFee()).div(100));

        Coin valueToRelease = pegoutRequestValue.toBitcoin();
        assertTrue(valueToRelease.isLessThan(minValueWithGapAboveFee));

        bridgeSupport.releaseBtc(buildReleaseRskTx(pegoutRequestValue));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        RskAddress senderAddress = new RskAddress(SENDER.getAddress());

        verify(repository, times(1)).transfer(
            BRIDGE_ADDRESS,
            senderAddress,
            pegoutRequestValue
        );
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, logInfo.size());

        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(
            senderAddress,
            pegoutRequestValue,
            RejectedPegoutReason.FEE_ABOVE_VALUE
        );
    }

    @Test
    @DisplayName("A rejected pegout due to low amount. Pre lovell, the value is rounded down to the nearest satoshi. Both in the emitted event and the value refunded.")
    void low_amount_release_request_rejected_before_lovell() throws IOException {
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);

        List<LogInfo> logInfo = new ArrayList<>();
        eventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            arrowheadActivations,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(eventLogger, arrowheadActivations);

        Coin belowPegoutMinimumValue = BRIDGE_CONSTANTS.getMinimumPegoutTxValue().minus(Coin.SATOSHI);
        co.rsk.core.Coin pegoutRequestValue = co.rsk.core.Coin.fromBitcoin(belowPegoutMinimumValue);
        // Add some extra weis to the value, but less than 1 satoshi.
        // To ensure that the pegout value is rounded down to fit in satoshis.
        co.rsk.core.Coin oneSatoshiInWeis = co.rsk.core.Coin.fromBitcoin(Coin.SATOSHI);
        co.rsk.core.Coin oneWei = co.rsk.core.Coin.valueOf(Denomination.WEI.longValue());
        co.rsk.core.Coin extraWeis = oneSatoshiInWeis.subtract(oneWei);
        pegoutRequestValue = pegoutRequestValue.add(extraWeis);

        bridgeSupport.releaseBtc(buildReleaseRskTx(pegoutRequestValue));

        RskAddress senderAddress = new RskAddress(SENDER.getAddress());

        // Pre lovell, the refund should be rounded down to the nearest satoshi.
        co.rsk.core.Coin expectedRefundValue = pegoutRequestValue.subtract(extraWeis);
        verify(repository, times(1)).transfer(
            BRIDGE_ADDRESS,
            senderAddress,
            expectedRefundValue
        );

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(
            senderAddress,
            pegoutRequestValue,
            RejectedPegoutReason.LOW_AMOUNT
        );

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        // Same case in the log, the value should be rounded down to the nearest satoshi.
        BigInteger amount = (BigInteger) event.decodeEventData(firstLog.getData())[0];
        long expectedAmountLogged = expectedRefundValue.toBitcoin().longValue();
        assertEquals(expectedAmountLogged, amount.longValue());
    }

    @Test
    @DisplayName("A rejected pegout due to low amount. Post lovell, the pegout value is preserved in weis. Both in the emitted event and the value refunded.")
    void low_amount_release_request_rejected_after_lovell() throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        eventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(eventLogger, ACTIVATIONS_ALL);

        Coin belowPegoutMinimumValue = BRIDGE_CONSTANTS.getMinimumPegoutTxValue().minus(Coin.SATOSHI);
        co.rsk.core.Coin pegoutRequestValue = co.rsk.core.Coin.fromBitcoin(belowPegoutMinimumValue);

        bridgeSupport.releaseBtc(buildReleaseRskTx(pegoutRequestValue));

        RskAddress senderAddress = new RskAddress(SENDER.getAddress());

        verify(repository, times(1)).transfer(
            BRIDGE_ADDRESS,
            senderAddress,
            pegoutRequestValue
        );

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(
            senderAddress,
            pegoutRequestValue,
            RejectedPegoutReason.LOW_AMOUNT
        );

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        BigInteger amount = (BigInteger) event.decodeEventData(firstLog.getData())[0];
        assertEquals(pegoutRequestValue.asBigInteger(), amount);
    }

    @Test
    @DisplayName("A pegout from a contract is rejected. Pre lovell, the pegout value is rounded down to the nearest satoshi. Both in the emitted event and the value refunded.")
    void contract_caller_release_request_rejected_before_lovell() throws IOException {
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);

        List<LogInfo> logInfo = new ArrayList<>();
        eventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            arrowheadActivations,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(eventLogger, arrowheadActivations);

        co.rsk.core.Coin pegoutRequestValue = co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMinimumPegoutTxValue());
        // Add some extra weis to the value, but less than 1 satoshi.
        // To ensure that the pegout value is rounded down to fit in satoshis.
        co.rsk.core.Coin oneSatoshiInWeis = co.rsk.core.Coin.fromBitcoin(Coin.SATOSHI);
        co.rsk.core.Coin oneWei = co.rsk.core.Coin.valueOf(Denomination.WEI.longValue());
        co.rsk.core.Coin extraWeis = oneSatoshiInWeis.subtract(oneWei);
        pegoutRequestValue = pegoutRequestValue.add(extraWeis);

        bridgeSupport.releaseBtc(buildReleaseRskTx_fromContract(pegoutRequestValue));

        RskAddress senderAddress = new RskAddress(SENDER.getAddress());

        // No refund is made to a contract
        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(
            senderAddress,
            pegoutRequestValue,
            RejectedPegoutReason.CALLER_CONTRACT
        );

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        BigInteger amount = (BigInteger) event.decodeEventData(firstLog.getData())[0];
        // Pre lovell, the logged value should be rounded down to the nearest satoshi.
        co.rsk.core.Coin expectedLoggedValue = pegoutRequestValue.subtract(extraWeis);
        long expectedAmountLogged = expectedLoggedValue.toBitcoin().longValue();
        assertEquals(expectedAmountLogged, amount.longValue());
    }

    @Test
    @DisplayName("A pegout from a contract is rejected. Post lovell, the pegout value is preserved in weis. Both in the emitted event and the value refunded.")
    void contract_caller_release_request_rejected_after_lovell() throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        eventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(eventLogger, ACTIVATIONS_ALL);

        co.rsk.core.Coin pegoutRequestValue = co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMinimumPegoutTxValue());
        // Add some extra weis to the value, but less than 1 satoshi.
        // To ensure that the pegout value is rounded down to fit in satoshis.
        co.rsk.core.Coin oneSatoshiInWeis = co.rsk.core.Coin.fromBitcoin(Coin.SATOSHI);
        co.rsk.core.Coin oneWei = co.rsk.core.Coin.valueOf(Denomination.WEI.longValue());
        co.rsk.core.Coin extraWeis = oneSatoshiInWeis.subtract(oneWei);
        pegoutRequestValue = pegoutRequestValue.add(extraWeis);

        bridgeSupport.releaseBtc(buildReleaseRskTx_fromContract(pegoutRequestValue));

        RskAddress senderAddress = new RskAddress(SENDER.getAddress());

        // No refund is made to a contract
        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(
            senderAddress,
            pegoutRequestValue,
            RejectedPegoutReason.CALLER_CONTRACT
        );

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        BigInteger amount = (BigInteger) event.decodeEventData(firstLog.getData())[0];
        assertEquals(pegoutRequestValue.asBigInteger(), amount);
    }

    @Test
    @DisplayName("A pegout rejected due to high fees. Pre lovell, the pegout value is rounded down to the nearest satoshi. Both in the emitted event and the value refunded.")
    void fee_above_value_release_request_rejected_before_lovell() throws IOException {
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead631().forBlock(0L);

        List<LogInfo> logInfo = new ArrayList<>();
        eventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            arrowheadActivations,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(eventLogger, arrowheadActivations);
        // Set a high fee per kb to ensure the resulting pegout is above the min pegout value
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.COIN);

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(
            ACTIVATIONS_ALL,
            federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, arrowheadActivations)
        );
        Coin minValueAccordingToFee = feePerKbSupport.getFeePerKb().div(1000).times(pegoutSize);
        Coin minValueWithGapAboveFee = minValueAccordingToFee.add(minValueAccordingToFee.times(
            BRIDGE_CONSTANTS.getMinimumPegoutValuePercentageToReceiveAfterFee()).div(100)
        );

        Coin pegoutRequestValueWithGapAboveFee = minValueWithGapAboveFee.minus(Coin.SATOSHI);
        co.rsk.core.Coin pegoutRequestValue = co.rsk.core.Coin.fromBitcoin(pegoutRequestValueWithGapAboveFee);
        // Add some extra weis to the value, but less than 1 satoshi.
        // To ensure that the pegout value is rounded down to fit in satoshis.
        co.rsk.core.Coin oneSatoshiInWeis = co.rsk.core.Coin.fromBitcoin(Coin.SATOSHI);
        co.rsk.core.Coin oneWei = co.rsk.core.Coin.valueOf(Denomination.WEI.longValue());
        co.rsk.core.Coin extraWeis = oneSatoshiInWeis.subtract(oneWei);
        pegoutRequestValue = pegoutRequestValue.add(extraWeis);

        bridgeSupport.releaseBtc(buildReleaseRskTx(pegoutRequestValue));

        RskAddress senderAddress = new RskAddress(SENDER.getAddress());

        // Pre lovell, the refund should be rounded down to the nearest satoshi.
        co.rsk.core.Coin expectedRefundValue = pegoutRequestValue.subtract(extraWeis);
        verify(repository, times(1)).transfer(
            BRIDGE_ADDRESS,
            senderAddress,
            expectedRefundValue
        );

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(
            senderAddress,
            pegoutRequestValue,
            RejectedPegoutReason.FEE_ABOVE_VALUE
        );

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        // Same case in the log, the value should be rounded down to the nearest satoshi.
        BigInteger amount = (BigInteger) event.decodeEventData(firstLog.getData())[0];
        long expectedAmountLogged = expectedRefundValue.toBitcoin().longValue();
        assertEquals(expectedAmountLogged, amount.longValue());
    }

    @Test
    @DisplayName("A pegout rejected due to high fees. Post lovell, the pegout value is preserved in weis. Both in the emitted event and the value refunded.")
    void fee_above_value_release_request_rejected_after_lovell() throws IOException {
        List<LogInfo> logInfo = new ArrayList<>();
        eventLogger = spy(new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS_ALL,
            logInfo
        ));
        bridgeSupport = initBridgeSupport(eventLogger, ACTIVATIONS_ALL);
        // Set a high fee per kb to ensure the resulting pegout is above the min pegout value
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.COIN);

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(
            ACTIVATIONS_ALL,
            federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, ACTIVATIONS_ALL)
        );
        Coin minValueAccordingToFee = feePerKbSupport.getFeePerKb().div(1000).times(pegoutSize);
        Coin minValueWithGapAboveFee = minValueAccordingToFee.add(minValueAccordingToFee.times(
            BRIDGE_CONSTANTS.getMinimumPegoutValuePercentageToReceiveAfterFee()).div(100)
        );

        Coin pegoutRequestValueWithGapAboveFee = minValueWithGapAboveFee.minus(Coin.SATOSHI);
        co.rsk.core.Coin pegoutRequestValue = co.rsk.core.Coin.fromBitcoin(pegoutRequestValueWithGapAboveFee);
        // Add some extra weis to the value, but less than 1 satoshi.
        // To ensure that the pegout value is rounded down to fit in satoshis.
        co.rsk.core.Coin oneSatoshiInWeis = co.rsk.core.Coin.fromBitcoin(Coin.SATOSHI);
        co.rsk.core.Coin oneWei = co.rsk.core.Coin.valueOf(Denomination.WEI.longValue());
        co.rsk.core.Coin extraWeis = oneSatoshiInWeis.subtract(oneWei);
        pegoutRequestValue = pegoutRequestValue.add(extraWeis);

        bridgeSupport.releaseBtc(buildReleaseRskTx(pegoutRequestValue));

        RskAddress senderAddress = new RskAddress(SENDER.getAddress());

        verify(repository, times(1)).transfer(
            BRIDGE_ADDRESS,
            senderAddress,
            pegoutRequestValue
        );

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(
            senderAddress,
            pegoutRequestValue,
            RejectedPegoutReason.FEE_ABOVE_VALUE
        );

        LogInfo firstLog = logInfo.get(0);
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent();
        assertArrayEquals(event.encodeSignatureLong(), firstLog.getTopics().get(0).getData());

        BigInteger amount = (BigInteger) event.decodeEventData(firstLog.getData())[0];
        assertEquals(pegoutRequestValue.asBigInteger(), amount);
    }

    /**********************************
     *  -------     UTILS     ------- *
     *********************************/

    private UTXO buildUTXO() {
        return UTXOBuilder.builder()
            .withTransactionHash(createHash(11))
            .withValue(Coin.COIN.multiply(2))
            .withHeight(1)
            .withOutputScript(activeFederation.getP2SHScript())
            .build();
    }

    private Transaction buildReleaseRskTx(co.rsk.core.Coin coin) {
        Transaction releaseTransaction = Transaction.builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(BRIDGE_ADDRESS)
            .data(Hex.decode(DATA))
            .chainId(Constants.MAINNET_CHAIN_ID)
            .value(coin)
            .build();
        releaseTransaction.sign(SENDER.getPrivKeyBytes());

        return releaseTransaction;
    }

    private Transaction buildReleaseRskTx_fromContract(co.rsk.core.Coin pegoutRequestValue) {
        Keccak256 parentHash = RskTestUtils.createHash(4);

        return new InternalTransaction(
            parentHash.getBytes(),
            400,
            0,
            NONCE.toByteArray(),
            DataWord.valueOf(GAS_PRICE.longValue()),
            DataWord.valueOf(GAS_LIMIT.longValue()),
            SENDER.getAddress(),
            BRIDGE_ADDRESS.getBytes(),
            pegoutRequestValue.getBytes(),
            Hex.decode(DATA),
            "",
            new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        );
    }

    private Transaction buildUpdateTx() {
        final BigInteger value = new BigInteger("1");

        Transaction updateCollectionsTx = Transaction.builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(BRIDGE_ADDRESS)
            .data(Hex.decode(DATA))
            .chainId(Constants.MAINNET_CHAIN_ID)
            .value(value)
            .build();
        updateCollectionsTx.sign(SENDER.getPrivKeyBytes());

        return updateCollectionsTx;
    }

    private BridgeSupport initBridgeSupport(BridgeEventLogger eventLogger, ActivationConfig.ForBlock activations) {
        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .build();

        return bridgeSupportBuilder
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withRepository(repository)
            .withEventLogger(eventLogger)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();
    }

    private BridgeStorageProvider initProvider() {
        return new BridgeStorageProvider(
            repository,
            NETWORK_PARAMETERS,
            ACTIVATIONS_ALL
        );
    }

    private FederationStorageProvider initFederationStorageProvider() {
        UTXO utxo = buildUTXO();
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
        FederationStorageProvider storageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        storageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ACTIVATIONS_ALL).add(utxo);
        storageProvider.setNewFederation(activeFederation);

        return storageProvider;
    }
}
