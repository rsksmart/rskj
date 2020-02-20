package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.MerkleTreeUtils;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BridgeSupportTest {
    private static final co.rsk.core.Coin LIMIT_MONETARY_BASE = new co.rsk.core.Coin(new BigInteger("21000000000000000000000000"));

    private BridgeConstants bridgeConstants;
    private NetworkParameters btcParams;

    private static final RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;

    @Before
    public void setUpOnEachTest() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        btcParams = bridgeConstants.getBtcParams();
    }

    @Test
    public void activations_is_set() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP124)).thenReturn(true);

        BridgeSupport bridgeSupport = getBridgeSupport(
                mock(BridgeConstants.class),
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations);

        Assert.assertTrue(bridgeSupport.getActivations().isActive(ConsensusRule.RSKIP124));
    }

    @Test(expected = NullPointerException.class)
    public void voteFeePerKbChange_nullFeeThrows() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(null));
        when(tx.getSender())
                .thenReturn(new RskAddress(ByteUtil.leftPadBytes(new byte[]{0x43}, 20)));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider);

        bridgeSupport.voteFeePerKbChange(tx, null);
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_unauthorized() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(false);

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider);

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(-10));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_negativeFeePerKb() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider);

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.NEGATIVE_SATOSHI), is(-1));
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.ZERO), is(-1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_excessiveFeePerKb() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);
        when(constants.getMaxFeePerKb())
                .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider);

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB)), is(1));
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB + 1)), is(-2));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVote() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);
        when(constants.getMaxFeePerKb())
                .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider);

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVoteWithFeeChange() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(1);
        when(constants.getMaxFeePerKb())
                .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider);

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider).setFeePerKb(Coin.CENT);
    }

    @Test
    public void getLockingCap() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getInitialLockingCap()).thenReturn(Coin.SATOSHI);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getLockingCap()).thenReturn(null).thenReturn(constants.getInitialLockingCap());

        BridgeSupport bridgeSupport = getBridgeSupport(
                constants, provider, mock(Repository.class), null, null, null, activations
        );

        // First time should also call setLockingCap as it was null
        assertEquals(constants.getInitialLockingCap(), bridgeSupport.getLockingCap());
        // Second time should just return the value
        assertEquals(constants.getInitialLockingCap(), bridgeSupport.getLockingCap());
        // Verify the set was called just once
        verify(provider, times(1)).setLockingCap(constants.getInitialLockingCap());
    }

    @Test
    public void increaseLockingCap_unauthorized() {
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(authorizer.isAuthorized(any(Transaction.class))).thenReturn(false);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getIncreaseLockingCapAuthorizer()).thenReturn(authorizer);

        BridgeSupport bridgeSupport = getBridgeSupport(constants, mock(BridgeStorageProvider.class), ActivationConfigsForTest.all().forBlock(0));

        assertFalse(bridgeSupport.increaseLockingCap(mock(Transaction.class), Coin.SATOSHI));
    }

    @Test
    public void increaseLockingCap_below_current_value() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getLockingCap()).thenReturn(Coin.COIN);

        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(authorizer.isAuthorized(any(Transaction.class))).thenReturn(true);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getIncreaseLockingCapAuthorizer()).thenReturn(authorizer);

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider, ActivationConfigsForTest.all().forBlock(0));

        assertFalse(bridgeSupport.increaseLockingCap(mock(Transaction.class), Coin.SATOSHI));
    }

    @Test
    public void increaseLockingCap_above_upper_value() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getLockingCap()).thenReturn(Coin.COIN);

        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(authorizer.isAuthorized(any(Transaction.class))).thenReturn(true);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getIncreaseLockingCapAuthorizer()).thenReturn(authorizer);

        int multiplier = 2;
        when(constants.getLockingCapIncrementsMultiplier()).thenReturn(multiplier);

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider, ActivationConfigsForTest.all().forBlock(0));

        assertFalse(bridgeSupport.increaseLockingCap(mock(Transaction.class), Coin.COIN.multiply(multiplier).plus(Coin.SATOSHI)));
    }

    @Test
    public void increaseLockingCap() {
        Coin lastValue = Coin.COIN;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getLockingCap()).thenReturn(lastValue);

        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(authorizer.isAuthorized(any(Transaction.class))).thenReturn(true);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getIncreaseLockingCapAuthorizer()).thenReturn(authorizer);
        int multiplier = 2;
        when(constants.getLockingCapIncrementsMultiplier()).thenReturn(multiplier);

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider, ActivationConfigsForTest.all().forBlock(0));

        // Accepts up to the last value (increment 0)
        assertTrue(bridgeSupport.increaseLockingCap(mock(Transaction.class), lastValue));

        // Accepts up to the last value plus one
        assertTrue(bridgeSupport.increaseLockingCap(mock(Transaction.class), lastValue.plus(Coin.SATOSHI)));

        // Accepts a value in the middle
        assertTrue(bridgeSupport.increaseLockingCap(mock(Transaction.class), lastValue.plus(Coin.CENT)));

        // Accepts up to the last value times multiplier
        assertTrue(bridgeSupport.increaseLockingCap(mock(Transaction.class), lastValue.multiply(multiplier)));
    }

    @Test
    public void registerBtcTransaction_before_RSKIP134_activation_sends_above_lockingcap() throws IOException, BlockStoreException {
        // Sending above locking cap evaluating different conditions (sending to both fed, to one, including funds in wallet and in utxos waiting for signatures...)
        assertLockingCap(true, false , Coin.COIN.multiply(3), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN.multiply(2), Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN.multiply(2), Coin.ZERO);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.ZERO, Coin.COIN.multiply(2));

        // Right above locking cap
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.ZERO, Coin.SATOSHI);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.SATOSHI, Coin.ZERO);
    }

    @Test
    public void registerBtcTransaction_before_RSKIP134_activation_sends_exactly_lockingcap() throws IOException, BlockStoreException {
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
    }

    @Test
    public void registerBtcTransaction_before_RSKIP134_activation_sends_below_lockingcap() throws IOException, BlockStoreException {
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
    }

    @Test
    public void registerBtcTransaction_after_RSKIP134_activation_sends_above_lockingcap() throws IOException, BlockStoreException {
        // Sending above locking cap evaluating different conditions (sending to both fed, to one, including funds in wallet and in utxos waiting for signatures...)
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN, Coin.COIN.multiply(2), Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.ZERO, Coin.COIN.multiply(3), Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN.multiply(2), Coin.ZERO);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.ZERO, Coin.COIN.multiply(2));

        // Right above locking cap
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.ZERO, Coin.SATOSHI);
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.SATOSHI, Coin.ZERO);
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.SATOSHI, Coin.ZERO, Coin.ZERO);
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5).add(Coin.SATOSHI), Coin.ZERO, Coin.ZERO, Coin.ZERO);
    }

    @Test
    public void registerBtcTransaction_after_RSKIP134_activation_sends_exactly_lockingcap() throws IOException, BlockStoreException {
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
    }

    @Test
    public void registerBtcTransaction_after_RSKIP134_activation_sends_below_lockingcap() throws IOException, BlockStoreException {
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
    }

    @Test
    public void isBtcTxHashAlreadyProcessed() throws IOException {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0l);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(hash1)).thenReturn(Optional.of(1l));


        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, bridgeStorageProvider, activations);

        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(hash1));
        assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(hash2));
    }

    @Test
    public void getBtcTxHashProcessedHeight() throws IOException {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0l);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(hash1)).thenReturn(Optional.of(1l));


        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, bridgeStorageProvider, activations);

        assertEquals(Long.valueOf(1), bridgeSupport.getBtcTxHashProcessedHeight(hash1));
        assertEquals(Long.valueOf(-1), bridgeSupport.getBtcTxHashProcessedHeight(hash2));
    }

    @Test
    public void when_registerBtcTransaction_sender_not_recognized_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                btcLockSenderProvider,
                executionBlock,
                mockFactory,
                activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<BtcTransaction>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        track.commit();

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(true));

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.ZERO;

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesLegacyType_afterFork_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2PKH, btcAddress, rskAddress),
                executionBlock,
                mockFactory,
                activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<BtcTransaction>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        track.commit();

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN.multiply(5), provider.getNewFederationBtcUTXOs().get(0).getValue());

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesSegCompatibilityType_beforeFork_no_lock_and_no_refund() throws Exception {

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2SHP2WPKH, btcAddress, rskAddress),
                executionBlock,
                mockFactory,
                activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<BtcTransaction>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        track.commit();

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(true));

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.ZERO;

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesSegCompatibilityType_afterFork_lock_and_no_refund() throws Exception {

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2SHP2WPKH, btcAddress, rskAddress),
                executionBlock,
                mockFactory,
                activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<BtcTransaction>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        track.commit();

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN.multiply(5), provider.getNewFederationBtcUTXOs().get(0).getValue());

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesSegCompatibilityType_afterFork_notWhitelisted_no_lock_and_refund() throws Exception {

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Don't whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2SHP2WPKH, btcAddress, rskAddress),
                executionBlock,
                mockFactory,
                activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<BtcTransaction>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        track.commit();

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.ZERO;

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());

        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
                .stream()
                .map(e -> e.getTransaction())
                .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
                .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction releaseTx = releaseTxs.get(0);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(Coin.COIN.multiply(5).subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(srcKey1.toAddress(btcParams), releaseTx.getOutput(0).getAddressFromP2PKHScript(btcParams));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx1.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesMultisigType_afterFork_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        Repository track = repository.startTracking();

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2SHMULTISIG, btcAddress, rskAddress),
                executionBlock,
                mockFactory,
                activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<BtcTransaction>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        track.commit();

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(true));

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.ZERO;

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test(expected = VerificationException.EmptyInputsOrOutputs.class)
    public void registerBtcTransaction_rejects_tx_with_witness_before_rskip_143_activation() throws BlockStoreException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);
        tx1.addOutput(Coin.COIN, Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                Sha256Hash.ZERO_HASH,
                1,
                1,
                1,
                new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());

        BtcTransaction btcTx = mock(BtcTransaction.class);
        verify(btcTx, times(0)).verify();
    }

    @Test
    public void registerBtcTransaction_accepts_lock_tx_with_witness_after_rskip_143_activation() throws BlockStoreException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(10), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                blockMerkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmtWithWitness.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations));
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2SHP2WPKH, btcAddress, rskAddress),
                mock(Block.class),
                mockFactory,
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        doReturn(coinbaseInformation).when(provider).getCoinbaseInformation(registerHeader.getHash());

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        track.commit();

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(10, 0));

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN.multiply(10), provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    public void registerBtcTransaction_rejects_tx_with_witness_and_unregistered_coinbase_after_rskip_143_activation() throws BlockStoreException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(10), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                Sha256Hash.ZERO_HASH,
                1,
                1,
                1,
                new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations));
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2SHP2WPKH, btcAddress, rskAddress),
                mock(Block.class),
                mockFactory,
                activations
        );

        doReturn(null).when(provider).getCoinbaseInformation(registerHeader.getHash());

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        track.commit();

        BtcTransaction btcTx = mock(BtcTransaction.class);
        verify(btcTx, times(0)).verify();
    }

    @Test
    public void registerBtcTransaction_rejects_tx_with_witness_and_unqual_witness_root_after_rskip_143_activation() throws BlockStoreException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(10), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                Sha256Hash.ZERO_HASH,
                1,
                1,
                1,
                new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations));
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2SHP2WPKH, btcAddress, rskAddress),
                mock(Block.class),
                mockFactory,
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        doReturn(coinbaseInformation).when(provider).getCoinbaseInformation(registerHeader.getHash());

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        track.commit();

        BtcTransaction btcTx = mock(BtcTransaction.class);
        verify(btcTx, times(0)).verify();
    }

    @Test
    public void registerBtcTransaction_rejects_tx_without_witness_unequal_roots_after_rskip_143() throws BlockStoreException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(10), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                Sha256Hash.ZERO_HASH,
                1,
                1,
                1,
                new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations));
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2SHP2WPKH, btcAddress, rskAddress),
                mock(Block.class),
                mockFactory,
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        doReturn(coinbaseInformation).when(provider).getCoinbaseInformation(registerHeader.getHash());

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        track.commit();

        BtcTransaction btcTx = mock(BtcTransaction.class);
        verify(btcTx, times(0)).verify();
    }

    @Test
    public void registerBtcTransaction_accepts_lock_tx_without_witness_after_rskip_143_activation() throws BlockStoreException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        List<BtcECKey> federation1Keys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        tx1.addOutput(Coin.COIN.multiply(10), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                blockMerkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations));
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                getBtcLockSenderProvider(BtcLockSender.TxType.P2PKH, btcAddress, rskAddress),
                mock(Block.class),
                mockFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());
        track.commit();

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(10, 0));

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN.multiply(10), provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }


    @Test
    public void isBlockMerkleRootValid_equal_merkle_roots() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        Sha256Hash merkleRoot = PegTestUtils.createHash(1);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(merkleRoot);
        Assert.assertTrue(bridgeSupport.isBlockMerkleRootValid(merkleRoot, btcBlock));
    }

    @Test
    public void isBlockMerkleRootValid_unequal_merkle_roots_before_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress, bridgeConstants, activations);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        Sha256Hash merkleRoot = PegTestUtils.createHash(1);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        Assert.assertFalse(bridgeSupport.isBlockMerkleRootValid(merkleRoot, btcBlock));
    }

    @Test
    public void isBlockMerkleRootValid_coinbase_information_null_after_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(null);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlock.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        Assert.assertFalse(bridgeSupport.isBlockMerkleRootValid(PegTestUtils.createHash(1), btcBlock));
    }

    @Test
    public void isBlockMerkleRootValid_coinbase_information_not_null_and_unequal_mroots_after_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        CoinbaseInformation coinbaseInformation = mock(CoinbaseInformation.class);
        when(coinbaseInformation.getWitnessMerkleRoot()).thenReturn(PegTestUtils.createHash(1));

        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(coinbaseInformation);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlock.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        Assert.assertFalse(bridgeSupport.isBlockMerkleRootValid(PegTestUtils.createHash(2), btcBlock));
    }

    @Test
    public void isBlockMerkleRootValid_coinbase_information_not_null_and_equal_mroots_after_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        Sha256Hash merkleRoot = PegTestUtils.createHash(1);

        CoinbaseInformation coinbaseInformation = mock(CoinbaseInformation.class);
        when(coinbaseInformation.getWitnessMerkleRoot()).thenReturn(merkleRoot);
        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(coinbaseInformation);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlock.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        Assert.assertTrue(bridgeSupport.isBlockMerkleRootValid(merkleRoot, btcBlock));
    }

    @Test
    public void getBtcTransactionConfirmations_rejects_tx_with_witness_before_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001017db0c99ea2643e94f89e190807402c86b2d9ef83d96c0648ecfb11e6643eb4d90000" +
                "00001716001447866ddb7dd3180e99c8e3c6c85ebb0023b5d9b4ffffffff0200c2eb0b0000000017a914896ed9f3446d51b5510" +
                "f7f0b6ef81b2bde55140e87443324180100000017a914c7dcd0c19a12407a2fb5866bd44e4218888e4f0e870246304302207c9a" +
                "3d83409349d6676cec578c7fa5a2e83091abc2ab9d2bad3402a549568fa6021f1e43b726ec9c8199ddfb96d2e0e71e17ecc3416" +
                "f16073045528c0a28872d3d0121024be0015a1b0ab4a778e3abf81db544dcc18a18dc6a9a3da8d0c90286ef38a0b100000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                blockMerkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, track, mockFactory);

        MerkleBranch merkleBranch = mock(MerkleBranch.class);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(-5, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_accepts_tx_with_witness_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001017db0c99ea2643e94f89e190807402c86b2d9ef83d96c0648ecfb11e6643eb4d90000" +
                "00001716001447866ddb7dd3180e99c8e3c6c85ebb0023b5d9b4ffffffff0200c2eb0b0000000017a914896ed9f3446d51b5510" +
                "f7f0b6ef81b2bde55140e87443324180100000017a914c7dcd0c19a12407a2fb5866bd44e4218888e4f0e870246304302207c9a" +
                "3d83409349d6676cec578c7fa5a2e83091abc2ab9d2bad3402a549568fa6021f1e43b726ec9c8199ddfb96d2e0e71e17ecc3416" +
                "f16073045528c0a28872d3d0121024be0015a1b0ab4a778e3abf81db544dcc18a18dc6a9a3da8d0c90286ef38a0b100000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                blockMerkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activations));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        doReturn(coinbaseInformation).when(provider).getCoinbaseInformation(registerHeader.getHash());

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(132 - 50 + 1, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_unregistered_coinbase_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001017db0c99ea2643e94f89e190807402c86b2d9ef83d96c0648ecfb11e6643eb4d90000" +
                "00001716001447866ddb7dd3180e99c8e3c6c85ebb0023b5d9b4ffffffff0200c2eb0b0000000017a914896ed9f3446d51b5510" +
                "f7f0b6ef81b2bde55140e87443324180100000017a914c7dcd0c19a12407a2fb5866bd44e4218888e4f0e870246304302207c9a" +
                "3d83409349d6676cec578c7fa5a2e83091abc2ab9d2bad3402a549568fa6021f1e43b726ec9c8199ddfb96d2e0e71e17ecc3416" +
                "f16073045528c0a28872d3d0121024be0015a1b0ab4a778e3abf81db544dcc18a18dc6a9a3da8d0c90286ef38a0b100000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                blockMerkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activations));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        doReturn(null).when(provider).getCoinbaseInformation(registerHeader.getHash());

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(-5, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_registered_coinbase_unequal_witnessroot_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001017db0c99ea2643e94f89e190807402c86b2d9ef83d96c0648ecfb11e6643eb4d90000" +
                "00001716001447866ddb7dd3180e99c8e3c6c85ebb0023b5d9b4ffffffff0200c2eb0b0000000017a914896ed9f3446d51b5510" +
                "f7f0b6ef81b2bde55140e87443324180100000017a914c7dcd0c19a12407a2fb5866bd44e4218888e4f0e870246304302207c9a" +
                "3d83409349d6676cec578c7fa5a2e83091abc2ab9d2bad3402a549568fa6021f1e43b726ec9c8199ddfb96d2e0e71e17ecc3416" +
                "f16073045528c0a28872d3d0121024be0015a1b0ab4a778e3abf81db544dcc18a18dc6a9a3da8d0c90286ef38a0b100000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                blockMerkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activations));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        doReturn(coinbaseInformation).when(provider).getCoinbaseInformation(registerHeader.getHash());

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(-5, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_tx_without_witness_unequal_roots_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx1 = new BtcTransaction(btcParams);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                Sha256Hash.ZERO_HASH,
                1,
                1,
                1,
                new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activations));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash())).thenReturn(witnessMerkleRoot);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        doReturn(coinbaseInformation).when(provider).getCoinbaseInformation(registerHeader.getHash());

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(-5, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_accepts_tx_without_witness_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BtcTransaction tx1 = new BtcTransaction(btcParams);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                blockMerkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activations));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash())).thenReturn(blockMerkleRoot);

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(blockMerkleRoot);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(132 - 50 + 1, confirmations);
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void when_RegisterBtcCoinbaseTransaction_wrong_witnessReservedValue_noSent() throws BlockStoreException, AddressFormatException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        byte[] witnessReservedValue = new byte[10];

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, mock(BtcBlock.class), 5, height);
        when(btcBlockStore.getFromCache(mock(Sha256Hash.class))).thenReturn(new StoredBlock(mock(BtcBlock.class), BigInteger.ZERO, 0));

        bridgeSupport.registerBtcCoinbaseTransaction(txWithoutWitness.bitcoinSerialize(), mock(Sha256Hash.class), pmt.bitcoinSerialize(), mock(Sha256Hash.class), witnessReservedValue);
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void when_RegisterBtcCoinbaseTransaction_MerkleTreeWrongFormat_noSent() throws BlockStoreException, AddressFormatException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

       //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, mock(BtcBlock.class), 5, height);
        when(btcBlockStore.getFromCache(mock(Sha256Hash.class))).thenReturn(new StoredBlock(mock(BtcBlock.class), BigInteger.ZERO, 0));

        bridgeSupport.registerBtcCoinbaseTransaction(txWithoutWitness.bitcoinSerialize(), mock(Sha256Hash.class), pmt.bitcoinSerialize(), mock(Sha256Hash.class), witnessReservedValue);
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    public void when_RegisterBtcCoinbaseTransaction_HashNotInPmt_noSent() throws BlockStoreException, AddressFormatException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(new StoredBlock(registerHeader, BigInteger.ZERO, 0));

        bridgeSupport.registerBtcCoinbaseTransaction(txWithoutWitness.bitcoinSerialize(), mock(Sha256Hash.class), pmt.bitcoinSerialize(), mock(Sha256Hash.class), witnessReservedValue);
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test(expected = VerificationException.class)
    public void when_RegisterBtcCoinbaseTransaction_notVerify_noSent() throws BlockStoreException, AddressFormatException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 2);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(new StoredBlock(registerHeader, BigInteger.ZERO, 0));
        BtcTransaction btcTransaction = mock(BtcTransaction.class);
        doThrow(VerificationException.class).when(btcTransaction).verify();
        btcTransaction.verify();

        bridgeSupport.registerBtcCoinbaseTransaction(txWithoutWitness.bitcoinSerialize(), mock(Sha256Hash.class), pmt.bitcoinSerialize(), mock(Sha256Hash.class), witnessReservedValue);
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }


    @Test
    public void when_RegisterBtcCoinbaseTransaction_not_equal_merkle_root_noSent() throws BlockStoreException, AddressFormatException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 2);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);

        BtcBlock btcBlock = mock(BtcBlock.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(storedBlock.getHeader()).thenReturn(btcBlock);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(storedBlock);

        bridgeSupport.registerBtcCoinbaseTransaction(txWithoutWitness.bitcoinSerialize(), mock(Sha256Hash.class), pmt.bitcoinSerialize(), mock(Sha256Hash.class), witnessReservedValue);
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void when_RegisterBtcCoinbaseTransaction_null_stored_block_noSent() throws BlockStoreException, AddressFormatException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));

        txWithoutWitness.setWitness(0, null);
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 2);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);

        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(null);
        bridgeSupport.registerBtcCoinbaseTransaction(txWithoutWitness.bitcoinSerialize(), mock(Sha256Hash.class), pmt.bitcoinSerialize(), mock(Sha256Hash.class), witnessReservedValue);
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    public void registerBtcCoinbaseTransaction() throws BlockStoreException, AddressFormatException, IOException  {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        Sha256Hash mRoot = MerkleTreeUtils.combineLeftRight(tx1.getHash(), secondHashTx);

        txWithoutWitness.setWitness(0, null);
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);
        Sha256Hash witnessRoot = MerkleTreeUtils.combineLeftRight(Sha256Hash.ZERO_HASH, secondHashTx);
        byte[] witnessRootBytes = witnessRoot.getReversedBytes();
        byte[] wc = tx1.getOutputs().stream().filter(t -> t.getValue().getValue() == 0).collect(Collectors.toList()).get(0).getScriptPubKey().getChunks().get(1).data;
        wc = Arrays.copyOfRange(wc,4, 36);
        Sha256Hash witCom = Sha256Hash.wrap(wc);

        Assert.assertEquals(Sha256Hash.twiceOf(witnessRootBytes, witnessReservedValue), witCom);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 2);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        //Merkle root is from the original block
        Assert.assertEquals(merkleRoot, mRoot);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(1),
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(new StoredBlock(registerHeader, BigInteger.ZERO, 0));
        bridgeSupport.registerBtcCoinbaseTransaction(txWithoutWitness.bitcoinSerialize(), registerHeader.getHash(), pmt.bitcoinSerialize(), witnessRoot, witnessReservedValue);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessRoot);

        ArgumentCaptor<CoinbaseInformation> argumentCaptor = ArgumentCaptor.forClass(CoinbaseInformation.class);
        verify(provider).setCoinbaseInformation(eq(registerHeader.getHash()), argumentCaptor.capture());
        assertEquals(coinbaseInformation.getWitnessMerkleRoot(), argumentCaptor.getValue().getWitnessMerkleRoot());
    }

    @Test
    public void hasBtcCoinbaseTransaction_before_rskip_143_activation() throws AddressFormatException  {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Repository repository = createRepository();
        Repository track = repository.startTracking();
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(Sha256Hash.ZERO_HASH, coinbaseInformation);
        Assert.assertFalse(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    public void hasBtcCoinbaseTransaction_after_rskip_143_activation() throws AddressFormatException  {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(Sha256Hash.ZERO_HASH, coinbaseInformation);
        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(coinbaseInformation);
        Assert.assertTrue(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    public void hasBtcCoinbaseTransaction_fails_with_null_coinbase_information_after_rskip_143_activation() throws AddressFormatException  {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        Repository track = repository.startTracking();
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                mock(BtcLockSenderProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(Sha256Hash.ZERO_HASH, coinbaseInformation);
        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(null);
        Assert.assertFalse(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    private void assertLockingCap(boolean shouldLock, boolean isLockingCapEnabled, Coin lockingCap, Coin amountSentToNewFed, Coin amountSentToOldFed,
                                  Coin amountInNewFed, Coin amountInOldFed) throws BlockStoreException, IOException {
        // Configure if locking cap should be evaluated
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(isLockingCapEnabled);

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        when(bridgeConstants.getMinimumLockTxValue()).thenReturn(Coin.SATOSHI);
        when(bridgeConstants.getBtcParams()).thenReturn(BridgeRegTestConstants.getInstance().getBtcParams());
        when(bridgeConstants.getBtc2RskMinimumAcceptableConfirmations()).thenReturn(1);
        when(bridgeConstants.getGenesisFeePerKb()).thenReturn(BridgeRegTestConstants.getInstance().getGenesisFeePerKb());
        when(bridgeConstants.getMaxRbtc()).thenReturn(BridgeRegTestConstants.getInstance().getMaxRbtc());

        // Configure locking cap
        when(bridgeConstants.getInitialLockingCap()).thenReturn(lockingCap);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Federation federation = this.getFederation(bridgeConstants, null);

        BridgeStorageProvider provider =
                new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations);
        // We need a random new fed
        provider.setNewFederation(this.getFederation(bridgeConstants,
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(Hex.decode("fb01")),
                        BtcECKey.fromPrivate(Hex.decode("fb02")),
                        BtcECKey.fromPrivate(Hex.decode("fb03"))
                })
        ));
        // Use genesis fed as old
        provider.setOldFederation(federation);

        Coin currentFunds = Coin.ZERO;

        // Configure existing utxos in both federations
        if (amountInOldFed != null) {
            UTXO utxo = new UTXO(Sha256Hash.wrap(HashUtil.randomHash()), 0, amountInOldFed, 1, false, new Script(new byte[]{}));
            provider.getOldFederationBtcUTXOs().add(utxo);
            currentFunds = currentFunds.add(amountInOldFed);
        }
        if (amountInNewFed != null) {
            UTXO utxo = new UTXO(Sha256Hash.wrap(HashUtil.randomHash()), 0, amountInNewFed, 1, false, new Script(new byte[]{}));
            provider.getNewFederationBtcUTXOs().add(utxo);
            currentFunds = currentFunds.add(amountInNewFed);
        }
        // Fund bridge in RBTC, substracting the funds that we are indicating were locked in the federations (which means a user locked)
        co.rsk.core.Coin bridgeBalance = LIMIT_MONETARY_BASE.subtract(co.rsk.core.Coin.fromBitcoin(currentFunds));
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, bridgeBalance);

        // The locking cap shouldn't be surpassed by the initial configuration
        Assert.assertFalse(isLockingCapEnabled && currentFunds.isGreaterThan(lockingCap));

        Coin lockValue = Coin.ZERO;
        int newUtxos = 0;

        // Create transaction setting the outputs to the configured values
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        if (amountSentToOldFed != null) {
            tx.addOutput(amountSentToOldFed, provider.getOldFederation().getAddress());
            lockValue = lockValue.add(amountSentToOldFed);
            newUtxos++;
        }
        if (amountSentToNewFed != null) {
            tx.addOutput(amountSentToNewFed, provider.getNewFederation().getAddress());
            lockValue = lockValue.add(amountSentToNewFed);
            newUtxos++;
        }
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));


        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                        1, 1, 1, new ArrayList<BtcTransaction>());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(track)).thenReturn(btcBlockStore);

        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // Get the tx sender public key
        byte[] data = tx.getInput(0).getScriptSig().getChunks().get(1).data;
        BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        Address address = senderBtcKey.toAddress(bridgeConstants.getBtcParams());
        whitelist.put(address, new OneOffWhiteListEntry(address, lockValue));
        // The address is whitelisted
        Assert.assertThat(whitelist.isWhitelisted(address), is(true));

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, provider, track, new BtcLockSenderProvider(), executionBlock, mockFactory, activations);

        // Simulate blockchain
        int height = 1;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        Transaction rskTx = mock(Transaction.class);
        Keccak256 hash = new Keccak256(HashUtil.keccak256(new byte[]{}));
        when(rskTx.getHash()).thenReturn(hash);

        // Try to register tx
        bridgeSupport.registerBtcTransaction(rskTx, tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        // If the address is no longer whitelisted, it means it was consumed, whether the lock was rejected by lockingCap or not
        Assert.assertThat(whitelist.isWhitelisted(address), is(false));

        // The Btc transaction should have been processed
        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(tx.getHash()));

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(shouldLock ? lockValue : Coin.ZERO);
        RskAddress srcKeyRskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey.getPrivKey()).getAddress());

        // Verify amount was locked
        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(srcKeyRskAddress));
        Assert.assertEquals(bridgeBalance.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        if (!shouldLock) {
            // Release tx should have been created directly to the signatures stack
            BtcTransaction releaseTx = provider.getReleaseTransactionSet().getEntries().iterator().next().getTransaction();
            Assert.assertNotNull(releaseTx);
            // returns the funds to the sender
            Assert.assertEquals(1, releaseTx.getOutputs().size());
            Assert.assertEquals(address, releaseTx.getOutputs().get(0).getAddressFromP2PKHScript(bridgeConstants.getBtcParams()));
            Assert.assertEquals(lockValue, releaseTx.getOutputs().get(0).getValue().add(releaseTx.getFee()));
            // Uses the same UTXO(s)
            Assert.assertEquals(newUtxos, releaseTx.getInputs().size());
            for (int i = 0; i < newUtxos; i++) {
                TransactionInput input = releaseTx.getInput(i);
                Assert.assertEquals(tx.getHash(), input.getOutpoint().getHash());
                Assert.assertEquals(i, input.getOutpoint().getIndex());
            }
        }
    }


    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    private void mockChainOfStoredBlocks(BtcBlockStoreWithCache btcBlockStore, BtcBlock targetHeader, int headHeight, int targetHeight) throws BlockStoreException {
        // Simulate that the block is in there by mocking the getter by height,
        // and then simulate that the txs have enough confirmations by setting a high head.
        when(btcBlockStore.getStoredBlockAtMainChainHeight(targetHeight)).thenReturn(new StoredBlock(targetHeader, BigInteger.ONE, targetHeight));
        // Mock current pointer's header
        StoredBlock currentStored = mock(StoredBlock.class);
        BtcBlock currentBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(currentBlock).getHash();
        doReturn(currentBlock).when(currentStored).getHeader();
        when(currentStored.getHeader()).thenReturn(currentBlock);
        when(btcBlockStore.getChainHead()).thenReturn(currentStored);
        when(currentStored.getHeight()).thenReturn(headHeight);
    }

    private Federation getFederation(BridgeConstants bridgeConstants, List<BtcECKey> fedKeys) {
        List<BtcECKey> defaultFederationKeys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")),
        });
        List<BtcECKey> federationKeys = fedKeys ==  null ? defaultFederationKeys : fedKeys;
        federationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        return new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federationKeys),
                Instant.ofEpochMilli(1000L), 0L, bridgeConstants.getBtcParams());
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider) {
        return getBridgeSupport(constants, provider, null, null, null, null, null);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, ActivationConfig.ForBlock activations) {
        return getBridgeSupport(constants, provider, null, null, null, null, activations);

    }

    private BridgeSupport getBridgeSupport(BridgeStorageProvider provider, Repository track, BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(bridgeConstants, provider, track, mock(BtcLockSenderProvider.class), mock(Block.class), blockStoreFactory);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BtcLockSenderProvider btcLockSenderProvider, Block executionBlock,
                                           BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(
                constants,
                provider,
                track,
                btcLockSenderProvider,
                executionBlock,
                blockStoreFactory,
                mock(ActivationConfig.ForBlock.class)
        );
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BtcLockSenderProvider btcLockSenderProvider,
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
                mock(BridgeEventLogger.class),
                btcLockSenderProvider,
                track,
                executionBlock,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, executionBlock),
                blockStoreFactory,
                activations
        );
    }

    private BtcLockSenderProvider getBtcLockSenderProvider(BtcLockSender.TxType txType, Address btcAddress, RskAddress rskAddress) {
        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getType()).thenReturn(txType);
        when(btcLockSender.getBTCAddress()).thenReturn(btcAddress);
        when(btcLockSender.getRskAddress()).thenReturn(rskAddress);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));
        return btcLockSenderProvider;
    }
}
