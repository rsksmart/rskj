package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.utils.RejectedPegoutReason;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
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
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @BeforeEach
    void setUpOnEachTest() throws IOException {
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
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        verify(eventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void handmade_release_after_rskip_146() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(new ECKey().getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
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

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(3, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequested(
            any(byte[].class),
            any(BtcTransaction.class),
            any(Coin.class)
        );
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logUpdateCollections(any());
    }

    @Test
    void handmade_release_after_rskip_146_rejected_lowAmount() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);


        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        releaseTx = buildReleaseRskTx(Coin.ZERO);
        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        verify(eventLogger, never()).logReleaseBtcRequestRejected(any(), any(), any());

        assertEquals(1, logInfo.size());

        verify(eventLogger, times(1)).logUpdateCollections(any());
    }

    @Test
    void handmade_release_after_rskip_146_185_rejected_lowAmount() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);


        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
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

        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(2, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
        verify(eventLogger, times(1)).logUpdateCollections(any());
    }


    @Test
    void handmade_release_after_rskip_146_185_rejected_contractCaller() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);


        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
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

        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());

        assertEquals(2, logInfo.size());

        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
        verify(eventLogger, times(1)).logUpdateCollections(any());
    }

    @Test
    void handmade_release_after_rskip_146_rejected_contractCaller() throws IOException {

        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = new BridgeEventLoggerImpl(bridgeConstants, logInfo);
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
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value between old and new minimum pegout values
        Coin middle = bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis().subtract(bridgeConstants.getMinimumPegoutTxValueInSatoshis()).div(2);
        Coin value = bridgeConstants.getMinimumPegoutTxValueInSatoshis().add(middle);
        Assertions.assertTrue(value.isLessThan(bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis()));
        Assertions.assertTrue(value.isGreaterThan(bridgeConstants.getMinimumPegoutTxValueInSatoshis()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
    }

    @Test
    void release_before_rskip_219() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value between old and new minimum pegout values
        Coin middle = bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis().subtract(bridgeConstants.getMinimumPegoutTxValueInSatoshis()).div(2);
        Coin value = bridgeConstants.getMinimumPegoutTxValueInSatoshis().add(middle);
        Assertions.assertTrue(value.isLessThan(bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis()));
        Assertions.assertTrue(value.isGreaterThan(bridgeConstants.getMinimumPegoutTxValueInSatoshis()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void release_before_rskip_219_minimum_exclusive() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value exactly to legacy minimum
        Coin value = bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis();
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    void release_after_rskip_219_minimum_inclusive() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value exactly to current minimum
        Coin value = bridgeConstants.getMinimumPegoutTxValueInSatoshis();
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, times(1)).logReleaseBtcRequestReceived(any(), any(), any());
    }

    @Test
    void release_verify_fee_below_fee_is_rejected() throws IOException {
        Coin value = bridgeConstants.getMinimumPegoutTxValueInSatoshis().add(Coin.SATOSHI);

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
            bridgeConstants.getMinimumPegoutTxValueInSatoshis().minus(Coin.SATOSHI),
            false
        );
    }

    @Test
    void release_verify_fee_above_fee_and_minimum_is_accepted() throws IOException {
        testPegoutMinimumWithFeeVerification(Coin.COIN, Coin.FIFTY_COINS, true);
    }

    @Test
    void test_pegout_creation_before_RSKIP298_activation_does_not_set_index() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP298)).thenReturn(false);
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(4), federation.getAddress()));

        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(
            Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN)));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(releaseRequestQueue);
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        Coin totalValue = releaseRequestQueue.getEntries()
            .stream()
            .map(ReleaseRequestQueue.Entry::getAmount)
            .reduce(Coin.ZERO, Coin::add);

        List<Keccak256> rskHashesList = releaseRequestQueue.getEntries()
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

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        verify(provider, never()).setPegoutCreationEntry(any());
    }

    @Test
    void test_pegout_creation_after_RSKIP298_activation_sets_index() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP298)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(4), federation.getAddress()));

        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(
            Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN)));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(releaseRequestQueue);
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        Coin totalValue = releaseRequestQueue.getEntries()
            .stream()
            .map(ReleaseRequestQueue.Entry::getAmount)
            .reduce(Coin.ZERO, Coin::add);

        List<Keccak256> rskHashesList = releaseRequestQueue.getEntries()
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

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        BtcTransaction generatedTransaction = provider.getReleaseTransactionSet().getEntries().iterator().next().getTransaction();

        verify(provider, times(1)).setPegoutCreationEntry(new PegoutCreationEntry(generatedTransaction.getHash(), rskTx.getHash()));
    }

    @Test
    void test_processPegoutsIndividually_before_RSKIP271_activation() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(3), federation.getAddress()));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN))));
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // assert pegouts were not batched
        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        verify(provider, never()).getNextPegoutHeight();
        verify(provider, never()).setNextPegoutHeight(any(Long.class));
    }

    @Test
    void test_processPegoutsInBatch_after_RSKIP271() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(4), federation.getAddress()));

        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(
            Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.MILLICOIN)));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(releaseRequestQueue);
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        Coin totalValue = releaseRequestQueue.getEntries()
            .stream()
            .map(ReleaseRequestQueue.Entry::getAmount)
            .reduce(Coin.ZERO, Coin::add);

        List<Keccak256> rskHashesList = releaseRequestQueue.getEntries()
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

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        BtcTransaction generatedTransaction = provider.getReleaseTransactionSet().getEntries().iterator().next().getTransaction();

        verify(provider, times(1)).getNextPegoutHeight();
        verify(provider, times(1)).setNextPegoutHeight(any(Long.class));

        verify(eventLogger, times(1)).logBatchPegoutCreated(generatedTransaction, rskHashesList);
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
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

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

        assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    void test_processPegoutsInBatch_after_RSKIP271_activation_no_requests_in_queue_updates_next_pegout_height() throws IOException {

        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNextPegoutHeight()).thenReturn(Optional.of(100L));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

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

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(2, 0, Coin.COIN.multiply(4), federation.getAddress()));

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
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // Insufficient_Money i.e 4 BTC UTXO Available For 5 BTC Transaction.
        // Pegout requests can't be processed and remains in the queue
        assertEquals(5, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    void test_processPegoutsInBatch_after_rskip_271_divide_transaction_when_max_size_exceeded() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = PegTestUtils.createUTXOs(610, federation.getAddress());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(PegTestUtils.createReleaseRequestQueueEntries(600)));
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // First Half of the PegoutRequests 600 / 2 = 300 Is Batched For The First Time
        assertEquals(300, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // The Rest PegoutRequests 600 / 2 = 300 Is Batched The 2nd Time updateCollections Is Called
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(2, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    void test_processPegoutsInBatch_after_rskip_271_when_max_size_exceeded_for_one_pegout() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = PegTestUtils.createUTXOs(700, federation.getAddress());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(
            new ReleaseRequestQueue(Collections.singletonList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(700)))));
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    void test_processPegoutsInBatch_after_rskip_271_when_max_size_exceeded_for_two_pegout() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = PegTestUtils.createUTXOs(1400, federation.getAddress());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Arrays.asList(
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(700)),
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(700)))));
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activationMock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    void test_processPegoutsIndividually_before_rskip_271_no_funds_to_process_any_requests() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN, federation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(2, 1, Coin.COIN, federation.getAddress()));

        List<ReleaseRequestQueue.Entry> entries = Arrays.asList(
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(5)),
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(4)),
            new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(3))
        );

        ReleaseRequestQueue originalReleaseRequestQueue = new ReleaseRequestQueue(entries);
        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(entries);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(releaseRequestQueue);
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(originalReleaseRequestQueue, provider.getReleaseRequestQueue());
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    void test_processPegoutsIndividually_before_rskip_271_no_funds_to_process_any_requests_order_changes_in_queue() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN, federation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(2, 1, Coin.COIN, federation.getAddress()));

        List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();
        int entriesSizeAboveMaxIterations = BridgeSupport.MAX_RELEASE_ITERATIONS + 10;
        for (int i = 0; i < entriesSizeAboveMaxIterations; i++) {
            entries.add(
                new ReleaseRequestQueue.Entry(
                    PegTestUtils.createP2PKHBtcAddress(bridgeConstants.getBtcParams(), i+2),
                    Coin.COIN.multiply(5)
                )
            );
        }

        List<ReleaseRequestQueue.Entry> expectedEntries = new ArrayList<>();
        expectedEntries.addAll(entries.subList(BridgeSupport.MAX_RELEASE_ITERATIONS, entries.size()));
        expectedEntries.addAll(entries.subList(0, BridgeSupport.MAX_RELEASE_ITERATIONS));

        ReleaseRequestQueue expectedReleaseRequestQueue = new ReleaseRequestQueue(expectedEntries);
        ReleaseRequestQueue originalReleaseRequestQueue = new ReleaseRequestQueue(entries);
        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(entries);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(releaseRequestQueue);
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        Assertions.assertNotEquals(originalReleaseRequestQueue, provider.getReleaseRequestQueue());

        assertEquals(expectedReleaseRequestQueue, provider.getReleaseRequestQueue());

        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    void test_check_wallet_balance_before_rskip_271_process_at_least_one_request() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(2), federation.getAddress()));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(4)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(3)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(2)))));
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        // 2 remains in queue, 1 is processed to the transaction set
        assertEquals(2, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    void test_check_wallet_balance_after_rskip_271_process_no_requests() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(4), federation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(2, 1, Coin.COIN.multiply(4), federation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(3, 2, Coin.COIN.multiply(3), federation.getAddress()));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(5)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(4)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(3)))));
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .withEventLogger(eventLogger)
            .build();

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(3, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());

        verify(eventLogger, never()).logBatchPegoutCreated(any(), any());
        verify(provider, never()).setNextPegoutHeight(any(Long.class));
    }

    @Test
    void test_check_wallet_balance_after_rskip_271_process_all_requests_when_utxos_available() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<UTXO> utxos = new ArrayList<>();
        utxos.add(PegTestUtils.createUTXO(1, 0, Coin.COIN.multiply(4), federation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(2, 1, Coin.COIN.multiply(4), federation.getAddress()));
        utxos.add(PegTestUtils.createUTXO(3, 2, Coin.COIN.multiply(3), federation.getAddress()));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxos);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Arrays.asList(
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(5)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(4)),
                new ReleaseRequestQueue.Entry(PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()), Coin.COIN.multiply(3)))));
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withActivations(activationMock)
            .withEventLogger(eventLogger)
            .build();

        // First Call To updateCollections
        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(3, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());

        verify(eventLogger, never()).logBatchPegoutCreated(any(), any());
        verify(provider, never()).setNextPegoutHeight(any(Long.class));

        utxos.add(PegTestUtils.createUTXO(4, 3, Coin.COIN.multiply(1), federation.getAddress()));
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

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        verify(eventLogger, times(1)).logBatchPegoutCreated(any(), any());
        verify(provider, times(1)).setNextPegoutHeight(any(Long.class));
    }

    private void testPegoutMinimumWithFeeVerification(Coin feePerKB, Coin value, boolean shouldPegout)
        throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        provider.setFeePerKb(feePerKB);

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(activationMock, provider.getNewFederation());
        Coin minValueAccordingToFee = provider.getFeePerKb().div(1000).times(pegoutSize);
        Coin minValueWithGapAboveFee = minValueAccordingToFee.add(minValueAccordingToFee.times(bridgeConstants.getMinimumPegoutValuePercentageToReceiveAfterFee()).div(100));
        // if shouldPegout true then value should be greater or equals than both required fee plus gap and min pegout value
        // if shouldPegout false then value should be smaller than any of those minimums
        assertEquals(!shouldPegout, value.isLessThan(minValueWithGapAboveFee) ||
            value.isLessThan(bridgeConstants.getMinimumPegoutTxValueInSatoshis()));

        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, shouldPegout ? never() : times(1)).transfer(any(), any(), any());

        assertEquals(shouldPegout ? 1 : 0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger, shouldPegout ? times(1) : never()).logReleaseBtcRequestReceived(any(), any(), any());
        ArgumentCaptor<RejectedPegoutReason> argumentCaptor = ArgumentCaptor.forClass(RejectedPegoutReason.class);
        verify(eventLogger, shouldPegout ? never() : times(1)).logReleaseBtcRequestRejected(any(), any(), argumentCaptor.capture());
        if (!shouldPegout) {
            // Verify rejected pegout reason using value in comparison with fee and pegout minimum
            assertEquals(
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
        return new UTXO(Sha256Hash.wrap(HashUtil.randomHash()), 0, Coin.COIN.multiply(2), 1, false, activeFederation.getP2SHScript());
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
            Hex.decode(DATA), "");
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
        return new Federation(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
    }

}
