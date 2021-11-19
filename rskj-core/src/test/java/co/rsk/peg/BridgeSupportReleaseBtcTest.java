package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.utils.RejectedPegoutReason;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BridgeSupportReleaseBtcTest {

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

    @Before
    public void setUpOnEachTest() throws IOException {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
        activeFederation = getFederation();
        repository = spy(createRepository());
        eventLogger = mock(BridgeEventLogger.class);
        utxo = buildUTXO();
        provider = initProvider(repository, activationMock);
        bridgeSupport = spy(initBridgeSupport(eventLogger, activationMock));
        releaseTx = buildReleaseRskTx();
    }

    @Test
    public void noLogEvents_before_rskip_146_185() throws IOException {
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
    public void eventLogger_logReleaseBtcRequested_after_rskip_146() throws IOException {
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
    public void eventLogger_logReleaseBtcRequested_after_rskip_146_185() throws IOException {
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
    public void eventLogger_logReleaseBtcRequested_release_before_activation_and_updateCollections_after_activation() throws IOException {
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
    public void handmade_release_before_rskip_146_185() throws IOException {
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
    public void handmade_release_after_rskip_146() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(new ECKey().getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        ReleaseTransactionSet.Entry entry = (ReleaseTransactionSet.Entry) provider.getReleaseTransactionSet().getEntries().toArray()[0];
        verify(eventLogger,times(1)).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    public void handmade_release_after_rskip_146_185() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());
        bridgeSupport.updateCollections(rskTx);

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        ReleaseTransactionSet.Entry entry = (ReleaseTransactionSet.Entry) provider.getReleaseTransactionSet().getEntries().toArray()[0];

        assertEquals(3, logInfo.size());
        verify(eventLogger,times(1)).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
        verify(eventLogger,times(1)).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger,times(1)).logUpdateCollections(any());
    }

    @Test
    public void handmade_release_after_rskip_146_rejected_lowAmount() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);


        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
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

        verify(eventLogger,times(1)).logUpdateCollections(any());
    }

    @Test
    public void handmade_release_after_rskip_146_185_rejected_lowAmount() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);


        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
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
        verify(eventLogger,times(1)).logReleaseBtcRequestRejected(any(), any(), any());
        verify(eventLogger,times(1)).logUpdateCollections(any());
    }


    @Test
    public void handmade_release_after_rskip_146_185_rejected_contractCaller() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);


        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
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

        verify(eventLogger,times(1)).logReleaseBtcRequestRejected(any(), any(), any());
        verify(eventLogger,times(1)).logUpdateCollections(any());
    }

    @Test
    public void handmade_release_after_rskip_146_rejected_contractCaller() throws IOException {

        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo);
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        releaseTx = buildReleaseRskTx_fromContract(Coin.COIN);
        try {
            bridgeSupport.releaseBtc(releaseTx);
            fail();
        }catch (Program.OutOfGasException e) {
            assertTrue(e.getMessage().contains("Contract calling releaseBTC"));
        }
    }

    @Test
    public void release_after_rskip_219() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value between old and new minimum pegout values
        Coin middle = bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis().subtract(bridgeConstants.getMinimumPegoutTxValueInSatoshis()).div(2);
        Coin value = bridgeConstants.getMinimumPegoutTxValueInSatoshis().add(middle);
        assertTrue(value.isLessThan(bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis()));
        assertTrue(value.isGreaterThan(bridgeConstants.getMinimumPegoutTxValueInSatoshis()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger,times(1)).logReleaseBtcRequestReceived(any(), any(), any());
    }

    @Test
    public void release_before_rskip_219() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value between old and new minimum pegout values
        Coin middle = bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis().subtract(bridgeConstants.getMinimumPegoutTxValueInSatoshis()).div(2);
        Coin value = bridgeConstants.getMinimumPegoutTxValueInSatoshis().add(middle);
        assertTrue(value.isLessThan(bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis()));
        assertTrue(value.isGreaterThan(bridgeConstants.getMinimumPegoutTxValueInSatoshis()));
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger,never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    public void release_before_rskip_219_minimum_exclusive() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(false);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value exactly to legacy minimum
        Coin value = bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis();
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger,never()).logReleaseBtcRequestReceived(any(), any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequestRejected(any(), any(), any());
    }

    @Test
    public void release_after_rskip_219_minimum_inclusive() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        // Get a value exactly to current minimum
        Coin value = bridgeConstants.getMinimumPegoutTxValueInSatoshis();
        bridgeSupport.releaseBtc(buildReleaseRskTx(value));

        Transaction rskTx = buildUpdateTx();
        rskTx.sign(SENDER.getPrivKeyBytes());

        verify(repository, never()).transfer(any(), any(), any());

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());

        assertEquals(1, logInfo.size());
        verify(eventLogger,times(1)).logReleaseBtcRequestReceived(any(), any(), any());
    }

    @Test
    public void release_verify_fee_below_fee_is_rejected() throws IOException {
        Coin value = bridgeConstants.getMinimumPegoutTxValueInSatoshis().add(Coin.SATOSHI);

        testPegoutMinimumWithFeeVerification(Coin.COIN, value, false);
    }

    @Test
    public void release_verify_fee_above_fee_but_below_gap_is_rejected_before_rskip_271() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);
        Coin feePerKB = Coin.COIN;

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(activationMock, provider.getNewFederation());
        Coin value = feePerKB.div(1000).times(pegoutSize);

        testPegoutMinimumWithFeeVerification(feePerKB, value, false);
    }

    @Test
    public void release_verify_fee_above_fee_but_below_gap_is_rejected_after_rskip_271() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        Coin feePerKB = Coin.COIN;

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(activationMock, provider.getNewFederation());
        Coin value = feePerKB.div(1000).times(pegoutSize);

        testPegoutMinimumWithFeeVerification(feePerKB, value, false);
    }

    @Test
    public void release_verify_fee_above_fee_but_below_minimum_is_rejected() throws IOException {
        testPegoutMinimumWithFeeVerification(
            Coin.MILLICOIN,
            bridgeConstants.getMinimumPegoutTxValueInSatoshis().minus(Coin.SATOSHI),
            false
        );
    }

    @Test
    public void release_verify_fee_above_fee_and_minimum_is_accepted() throws IOException {
        testPegoutMinimumWithFeeVerification(Coin.COIN, Coin.FIFTY_COINS, true);
    }

    @Test
    public void processReleasesInBatch_before_rskip_271() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    public void processReleasesInBatch_after_rskip_271() throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        bridgeSupport.releaseBtc(releaseTx);

        Transaction rskTx = buildUpdateTx();
        bridgeSupport.updateCollections(rskTx);

        assertEquals(1, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    private void testPegoutMinimumWithFeeVerification(Coin feePerKB, Coin value, boolean shouldPegout)
        throws IOException {
        when(activationMock.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP185)).thenReturn(true);
        when(activationMock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        List<LogInfo> logInfo = new ArrayList<>();
        BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activationMock, logInfo));
        bridgeSupport = initBridgeSupport(eventLogger, activationMock);

        provider.setFeePerKb(feePerKB);

        int pegoutSize = BridgeUtils.getRegularPegoutTxSize(activationMock, provider.getNewFederation());
        Coin minValueAccordingToFee = provider.getFeePerKb().div(1000).times(pegoutSize);
        Coin minValueWithGapAboveFee = minValueAccordingToFee.add(minValueAccordingToFee.times(bridgeConstants.getMinimumPegoutValuePercentageToReceiveAfterFee()).div(100));
        // if shouldPegout true then value should be greater or equals than both required fee plus gap and min pegout value
        // if shouldPegout false then value should be smaller than any of those minimums
        assertEquals(!shouldPegout,
            value.isLessThan(minValueWithGapAboveFee) ||
            value.isLessThan(bridgeConstants.getMinimumPegoutTxValueInSatoshis())
        );

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
            Assert.assertEquals(
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
                .value(co.rsk.core.Coin.fromBitcoin(Coin.COIN).asBigInteger())
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
        return getBridgeSupport(
                bridgeConstants, provider, repository, eventLogger, mock(Block.class), null, activationMock);
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

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BridgeEventLogger eventLogger, Block executionBlock,
                                           BtcBlockStoreWithCache.Factory blockStoreFactory,
                                           ActivationConfig.ForBlock activations) {
        return getBridgeSupport(
                constants,
                provider,
                track,
                eventLogger,
                new BtcLockSenderProvider(),
                executionBlock,
                blockStoreFactory,
                activations
        );
    }


    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BridgeEventLogger eventLogger, BtcLockSenderProvider btcLockSenderProvider,
                                           Block executionBlock, BtcBlockStoreWithCache.Factory blockStoreFactory,
                                           ActivationConfig.ForBlock activations) {
        if (btcLockSenderProvider == null) {
            btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        }
        if (blockStoreFactory == null) {
            blockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
        }
        return new BridgeSupport(
                constants,
                provider,
                eventLogger,
                btcLockSenderProvider,
                mock(PeginInstructionsProvider.class),
                track,
                executionBlock,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, executionBlock),
                blockStoreFactory,
                activations
        );
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
