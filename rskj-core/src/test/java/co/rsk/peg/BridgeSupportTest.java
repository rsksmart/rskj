package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.BridgeSupport.TxType;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import co.rsk.peg.pegininstructions.PeginInstructions;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsVersion1;
import co.rsk.peg.simples.SimpleRskTransaction;
import co.rsk.peg.utils.*;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.trie.Trie;
import com.google.common.collect.Lists;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
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
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.InternalTransaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.rsk.peg.PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation;
import static co.rsk.peg.PegTestUtils.createBaseRedeemScriptThatSpendsFromTheFederation;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class BridgeSupportTest {
    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger DUST_AMOUNT = new BigInteger("1");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final co.rsk.core.Coin LIMIT_MONETARY_BASE = new co.rsk.core.Coin(new BigInteger("21000000000000000000000000"));
    private static final RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private static final List<BtcECKey> REGTEST_OLD_FEDERATION_PRIVATE_KEYS = Arrays.asList(
        BtcECKey.fromPrivate(Hex.decode("47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83")),
        BtcECKey.fromPrivate(Hex.decode("9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35")),
        BtcECKey.fromPrivate(Hex.decode("e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788"))
    );

    private BridgeConstants bridgeConstants;
    private NetworkParameters btcParams;
    private ActivationConfig.ForBlock activationsBeforeForks;
    private ActivationConfig.ForBlock activationsAfterForks;
    private BridgeSupportBuilder bridgeSupportBuilder;

    @Before
    public void setUpOnEachTest() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        btcParams = bridgeConstants.getBtcParams();
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
        activationsAfterForks = ActivationConfigsForTest.all().forBlock(0);
        bridgeSupportBuilder = new BridgeSupportBuilder();
    }

    @Test
    public void activations_is_set() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP124)).thenReturn(true);

        BridgeSupport bridgeSupport = getBridgeSupport(
                mock(BridgeConstants.class),
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BridgeEventLogger.class),
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
                constants, provider, mock(Repository.class), mock(BridgeEventLogger.class), null, null, activations
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
    public void registerBtcTransaction_before_RSKIP134_activation_sends_above_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // Sending above locking cap evaluating different conditions (sending to both fed, to one, including funds in wallet and in utxos waiting for signatures...)
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
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
    public void registerBtcTransaction_before_RSKIP134_activation_sends_exactly_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
    }

    @Test
    public void registerBtcTransaction_before_RSKIP134_activation_sends_below_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
    }

    @Test
    public void registerBtcTransaction_after_RSKIP134_activation_sends_above_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerBtcTransaction_after_RSKIP134_activation_sends_exactly_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
    }

    @Test
    public void registerBtcTransaction_after_RSKIP134_activation_sends_below_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(hash1)).thenReturn(Optional.of(1L));

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, bridgeStorageProvider, activations);

        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(hash1));
        assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(hash2));
    }

    @Test
    public void getBtcTxHashProcessedHeight() throws IOException {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(hash1)).thenReturn(Optional.of(1L));

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, bridgeStorageProvider, activations);

        assertEquals(Long.valueOf(1), bridgeSupport.getBtcTxHashProcessedHeight(hash1));
        assertEquals(Long.valueOf(-1), bridgeSupport.getBtcTxHashProcessedHeight(hash2));
    }

    @Test
    public void eventLoggerLogLockBtc_before_rskip_146_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstants.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        FederationSupport federationSupport = new FederationSupport(bridgeConstants, mockBridgeStorageProvider, executionBlock);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                        1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mockedEventLogger,
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, never()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
    }

    @Test
    public void eventLoggerLogLockBtc_after_rskip_146_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstants.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        FederationSupport federationSupport = new FederationSupport(bridgeConstants, mockBridgeStorageProvider, executionBlock);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 1;

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mockedEventLogger,
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        verify(mockedEventLogger, atLeastOnce()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
    }
    @Test
    public void eventLoggerLogPeginRejectionEvents_before_rskip_181_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP181)).thenReturn(false);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstants.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        FederationSupport federationSupport = new FederationSupport(bridgeConstants, mockBridgeStorageProvider, executionBlock);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                        1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any(BtcTransaction.class))).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any(BtcTransaction.class))).thenReturn(Optional.empty());

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mockedEventLogger,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
                mock(Repository.class),
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, never()).logRejectedPegin(any(BtcTransaction.class), any(RejectedPeginReason.class));
        verify(mockedEventLogger, never()).logUnrefundablePegin(any(BtcTransaction.class), any(UnrefundablePeginReason.class));
    }

    @Test
    public void eventLoggerLogPeginRejectionEvents_after_rskip_181_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP181)).thenReturn(true);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstants.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        FederationSupport federationSupport = new FederationSupport(bridgeConstants, mockBridgeStorageProvider, executionBlock);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                        1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any(BtcTransaction.class))).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any(BtcTransaction.class))).thenReturn(Optional.empty());

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mockedEventLogger,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
                mock(Repository.class),
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, atLeastOnce()).logRejectedPegin(any(BtcTransaction.class), any(RejectedPeginReason.class));
        verify(mockedEventLogger, atLeastOnce()).logUnrefundablePegin(any(BtcTransaction.class), any(UnrefundablePeginReason.class));
    }

    @Test
    public void eventLoggerLogPeginBtc_before_rskip_170_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstants.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        FederationSupport federationSupport = new FederationSupport(bridgeConstants, mockBridgeStorageProvider, executionBlock);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                        1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mockedEventLogger,
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, atLeastOnce()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
        verify(mockedEventLogger, never()).logPeginBtc(any(RskAddress.class), any(BtcTransaction.class), any(Coin.class), anyInt());
    }

    @Test
    public void eventLoggerLogPeginBtc_after_rskip_170_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstants.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        FederationSupport federationSupport = new FederationSupport(bridgeConstants, mockBridgeStorageProvider, executionBlock);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                        1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock,
                height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mockedEventLogger,
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, never()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
        verify(mockedEventLogger, atLeastOnce()).logPeginBtc(any(RskAddress.class), any(BtcTransaction.class), any(Coin.class), anyInt());
    }

    @Test
    public void registerBtcTransactionLockTxNotWhitelisted_before_rskip_146_activation() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        //Creates federation 1
        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        //Creates federation 2
        List<BtcECKey> federation2Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03")));

        Federation federation2 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
                Instant.ofEpochMilli(2000L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(btcParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(btcParams);
        tx3.addOutput(Coin.COIN.multiply(3), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(4), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey3));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);
        provider.setOldFederation(federation2);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            bridgeEventLogger,
            executionBlock,
            mockFactory,
            activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3);
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

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        Transaction rskTx1 = getMockedRskTxWithHash("aa");
        Transaction rskTx2 = getMockedRskTxWithHash("bb");
        Transaction rskTx3 = getMockedRskTxWithHash("cc");

        bridgeSupport.registerBtcTransaction(rskTx1, tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx2, tx2.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx3, tx3.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        RskAddress srcKey1RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress());
        RskAddress srcKey2RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress());
        RskAddress srcKey3RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress());

        Assert.assertEquals(0, repository.getBalance(srcKey1RskAddress).asBigInteger().intValue());
        Assert.assertEquals(0, repository.getBalance(srcKey2RskAddress).asBigInteger().intValue());
        Assert.assertEquals(0, repository.getBalance(srcKey3RskAddress).asBigInteger().intValue());
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(0, provider.getOldFederationBtcUTXOs().size());

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(3, provider.getReleaseTransactionSet().getEntriesWithoutHash().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntriesWithHash().size());

        List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
                .stream()
                .map(ReleaseTransactionSet.Entry::getTransaction)
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
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());

        // Second release tx should correspond to the 7 (3+4) BTC lock tx
        releaseTx = releaseTxs.get(1);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(Coin.COIN.multiply(7).subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(srcKey3.toAddress(btcParams), releaseTx.getOutput(0).getAddressFromP2PKHScript(btcParams));
        Assert.assertEquals(2, releaseTx.getInputs().size());
        List<TransactionOutPoint> releaseOutpoints = releaseTx.getInputs().stream().map(TransactionInput::getOutpoint).sorted(Comparator.comparing(TransactionOutPoint::getIndex)).collect(Collectors.toList());
        Assert.assertEquals(tx3.getHash(), releaseOutpoints.get(0).getHash());
        Assert.assertEquals(tx3.getHash(), releaseOutpoints.get(1).getHash());
        Assert.assertEquals(0, releaseOutpoints.get(0).getIndex());
        Assert.assertEquals(1, releaseOutpoints.get(1).getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx3.getHash()).isPresent());

        // Third release tx should correspond to the 10 BTC lock tx
        releaseTx = releaseTxs.get(2);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(Coin.COIN.multiply(10).subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(srcKey2.toAddress(btcParams), releaseTx.getOutput(0).getAddressFromP2PKHScript(btcParams));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx2.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx2.getHash()).isPresent());

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    public void registerBtcTransactionLockTxNotWhitelisted_after_rskip_146_activation() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        //Creates federation 1
        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        //Creates federation 2
        List<BtcECKey> federation2Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03")));

        Federation federation2 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
                Instant.ofEpochMilli(2000L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(btcParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(btcParams);
        tx3.addOutput(Coin.COIN.multiply(3), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(4), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey3));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);
        provider.setOldFederation(federation2);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            bridgeEventLogger,
            executionBlock,
            mockFactory,
            activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3);
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

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        Transaction rskTx1 = getMockedRskTxWithHash("aa");
        Transaction rskTx2 = getMockedRskTxWithHash("bb");
        Transaction rskTx3 = getMockedRskTxWithHash("cc");

        bridgeSupport.registerBtcTransaction(rskTx1, tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx2, tx2.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx3, tx3.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        RskAddress srcKey1RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress());
        RskAddress srcKey2RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress());
        RskAddress srcKey3RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress());

        Assert.assertEquals(0, repository.getBalance(srcKey1RskAddress).asBigInteger().intValue());
        Assert.assertEquals(0, repository.getBalance(srcKey2RskAddress).asBigInteger().intValue());
        Assert.assertEquals(0, repository.getBalance(srcKey3RskAddress).asBigInteger().intValue());
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(0, provider.getOldFederationBtcUTXOs().size());

        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntriesWithoutHash().size());
        Assert.assertEquals(3, provider.getReleaseTransactionSet().getEntriesWithHash().size());

        List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
                .stream()
                .map(ReleaseTransactionSet.Entry::getTransaction)
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
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
        // First Rsk tx corresponds to this release
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(rskTx1.getHash().getBytes(), releaseTx, Coin.COIN.multiply(5));

        // Second release tx should correspond to the 7 (3+4) BTC lock tx
        releaseTx = releaseTxs.get(1);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(Coin.COIN.multiply(7).subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(srcKey3.toAddress(btcParams), releaseTx.getOutput(0).getAddressFromP2PKHScript(btcParams));
        Assert.assertEquals(2, releaseTx.getInputs().size());
        List<TransactionOutPoint> releaseOutpoints = releaseTx.getInputs().stream().map(TransactionInput::getOutpoint).sorted(Comparator.comparing(TransactionOutPoint::getIndex)).collect(Collectors.toList());
        Assert.assertEquals(tx3.getHash(), releaseOutpoints.get(0).getHash());
        Assert.assertEquals(tx3.getHash(), releaseOutpoints.get(1).getHash());
        Assert.assertEquals(0, releaseOutpoints.get(0).getIndex());
        Assert.assertEquals(1, releaseOutpoints.get(1).getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx3.getHash()).isPresent());
        // third Rsk tx corresponds to this release
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(rskTx3.getHash().getBytes(), releaseTx, Coin.COIN.multiply(7));

        // Third release tx should correspond to the 10 BTC lock tx
        releaseTx = releaseTxs.get(2);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(Coin.COIN.multiply(10).subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(srcKey2.toAddress(btcParams), releaseTx.getOutput(0).getAddressFromP2PKHScript(btcParams));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx2.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx2.getHash()).isPresent());
        // Second Rsk tx corresponds to this release
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(rskTx2.getHash().getBytes(), releaseTx, Coin.COIN.multiply(10));

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void registerBtcTransaction_sending_segwit_tx_twice_locks_just_once() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock mockedActivations = mock(ActivationConfig.ForBlock.class);
        when(mockedActivations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        BtcTransaction txWithWitness = new BtcTransaction(btcParams);

        // first input spends P2PKH
        BtcECKey srcKey1 = new BtcECKey();
        txWithWitness.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // second input spends P2SH-P2PWKH (actually, just has a witness doesn't matter if it truly spends a witness for the test's sake)
        txWithWitness.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        txWithWitness.setWitness(0, txWit);

        List<BtcECKey> fedKeys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        Federation fed = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(fedKeys),
                Instant.ofEpochMilli(1000L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        txWithWitness.addOutput(Coin.COIN.multiply(5), fed.getAddress());

        // Create the pmt without witness and calculate the block merkle root
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcParams, bits,
                Collections.singletonList(txWithWitness.getHash()), 1);
        Sha256Hash merkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(new ArrayList<>());

        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcParams, bits,
                Collections.singletonList(txWithWitness.getHash(true)), 1);

        Sha256Hash witnessMerkleRoot = pmtWithWitness.getTxnHashAndMerkleRoot(new ArrayList<>());

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

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederation()).thenReturn(fed);
        when(provider.getCoinbaseInformation(registerHeader.getHash())).thenReturn(new CoinbaseInformation(witnessMerkleRoot));
        when(provider.getLockWhitelist()).thenReturn(new LockWhitelist(new HashMap<>(), 0));
        when(provider.getLockingCap()).thenReturn(Coin.FIFTY_COINS);
        // mock an actual store for the processed txs
        HashMap<Sha256Hash, Long> processedTxs = new HashMap<>();
        doAnswer(a -> {
            processedTxs.put(a.getArgument(0), a.getArgument(1));
            return null;
        }).when(provider).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        doAnswer(a -> Optional.ofNullable(processedTxs.get(a.getArgument(0))))
                .when(provider).getHeightIfBtcTxhashIsAlreadyProcessed(any());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(666L);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                mock(Repository.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                executionBlock,
                mockFactory,
                mockedActivations
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        // Tx is locked
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), txWithWitness.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(txWithWitness.getHash(true), executionBlock.getNumber());
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(txWithWitness.getHash(false), executionBlock.getNumber());

        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, txWithWitness.bitcoinSerialize());
        txWithoutWitness.setWitness(0, null);
        assertFalse(txWithoutWitness.hasWitness());

        // Tx is NOT locked again!
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), txWithoutWitness.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(txWithoutWitness.getHash(), executionBlock.getNumber());

        assertNotEquals(txWithWitness.getHash(true), txWithoutWitness.getHash());
    }

    @Test
    public void callProcessFundsMigration_is_migrating_before_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstants.getGenesisFederation();

        Federation newFederation = new Federation(
                FederationTestUtils.getFederationMembers(1),
                Instant.EPOCH,
                5L,
                bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
                .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
                .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getReleaseTransactionSet())
                .thenReturn(new ReleaseTransactionSet(Collections.emptySet()));
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(provider.getNewFederation())
                .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(35, 1);
        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants, provider, mock(Repository.class), bridgeEventLogger, rskCurrentBlock, null, activations
        );

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
                .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntriesWithoutHash().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntriesWithHash().size());

        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    public void callProcessFundsMigration_is_migrating_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstants.getGenesisFederation();

        Federation newFederation = new Federation(
                FederationTestUtils.getFederationMembers(1),
                Instant.EPOCH,
                5L,
                bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
                .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
                .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getReleaseTransactionSet())
                .thenReturn(new ReleaseTransactionSet(Collections.emptySet()));
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(provider.getNewFederation())
                .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(35, 1);
        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants, provider, mock(Repository.class), bridgeEventLogger, rskCurrentBlock, null, activations
        );

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
                .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntriesWithoutHash().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntriesWithHash().size());
        ReleaseTransactionSet.Entry entry = (ReleaseTransactionSet.Entry) provider.getReleaseTransactionSet().getEntriesWithHash().toArray()[0];
        // Should have been logged with the migrated UTXO
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(tx.getHash().getBytes(), entry.getTransaction(), Coin.COIN);
    }

    @Test
    public void callProcessFundsMigration_is_migrated_before_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstants.getGenesisFederation();

        Federation newFederation = new Federation(
                FederationTestUtils.getFederationMembers(1),
                Instant.EPOCH,
                5L,
                bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
                .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
                .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getReleaseTransactionSet())
                .thenReturn(new ReleaseTransactionSet(Collections.emptySet()));
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(provider.getNewFederation())
                .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);
        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            mock(Repository.class),
            bridgeEventLogger,
            rskCurrentBlock,
            null,
            activations
        );

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
                .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntriesWithoutHash().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntriesWithHash().size());

        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    public void callProcessFundsMigration_is_migrated_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstants.getGenesisFederation();

        Federation newFederation = new Federation(
                FederationTestUtils.getFederationMembers(1),
                Instant.EPOCH,
                5L,
                bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
                .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
                .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getReleaseTransactionSet())
                .thenReturn(new ReleaseTransactionSet(Collections.emptySet()));
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(provider.getNewFederation())
                .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);
        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants, provider, mock(Repository.class), bridgeEventLogger, rskCurrentBlock, null, activations
        );

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
                .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntriesWithoutHash().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntriesWithHash().size());
        ReleaseTransactionSet.Entry entry = (ReleaseTransactionSet.Entry) provider.getReleaseTransactionSet().getEntriesWithHash().toArray()[0];
        // Should have been logged with the migrated UTXO
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(tx.getHash().getBytes(), entry.getTransaction(), Coin.COIN);
    }

    @Test
    public void updateFederationCreationBlockHeights_before_rskip_186_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstants.getGenesisFederation();

        Federation newFederation = new Federation(
                FederationTestUtils.getFederationMembers(1),
                Instant.EPOCH,
                5L,
                bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
                .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
                .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getReleaseTransactionSet())
                .thenReturn(new ReleaseTransactionSet(Collections.emptySet()));
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(provider.getNewFederation())
                .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);
        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants, provider, mock(Repository.class), bridgeEventLogger, rskCurrentBlock, null, activations
        );

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration1);

        when(provider.getNextFederationCreationBlockHeight()).thenReturn(Optional.of(1L));

        bridgeSupport.updateCollections(tx);

        verify(provider, never()).getNextFederationCreationBlockHeight();
        verify(provider, never()).setActiveFederationCreationBlockHeight(any(Long.class));
        verify(provider, never()).clearNextFederationCreationBlockHeight();
    }

    @Test
    public void updateFederationCreationBlockHeights_after_rskip_186_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstants.getGenesisFederation();

        Federation newFederation = new Federation(
                FederationTestUtils.getFederationMembers(1),
                Instant.EPOCH,
                5L,
                bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
                .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
                .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getReleaseTransactionSet())
                .thenReturn(new ReleaseTransactionSet(Collections.emptySet()));
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(provider.getNewFederation())
                .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);
        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants, provider, mock(Repository.class), bridgeEventLogger, rskCurrentBlock, null, activations
        );

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
                .thenReturn(sufficientUTXOsForMigration1);

        when(provider.getNextFederationCreationBlockHeight()).thenReturn(Optional.empty());

        bridgeSupport.updateCollections(tx);

        verify(provider, times(1)).getNextFederationCreationBlockHeight();
        verify(provider, never()).setActiveFederationCreationBlockHeight(any(Long.class));
        verify(provider, never()).clearNextFederationCreationBlockHeight();

        when(provider.getNextFederationCreationBlockHeight()).thenReturn(Optional.of(1L));

        bridgeSupport.updateCollections(tx);

        verify(provider, times(2)).getNextFederationCreationBlockHeight();
        verify(provider, times(1)).setActiveFederationCreationBlockHeight(1L);
        verify(provider, times(1)).clearNextFederationCreationBlockHeight();
    }

    @Test
    public void rskTxWaitingForSignature_uses_updateCollection_rskTxHash_before_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<ReleaseTransactionSet.Entry> set = new HashSet<>();
        set.add(new ReleaseTransactionSet.Entry(btcTx, 1L)); // no rsk tx hash
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(set));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getRskTxsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeSupport bridgeSupport = getBridgeSupport(spiedBridgeConstants, provider, mock(Repository.class),
                mock(BridgeEventLogger.class), executionBlock, null, activations);

        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getRskTxsWaitingForSignatures().get(tx.getHash()));
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    public void rskTxWaitingForSignature_uses_updateCollection_rskTxHash_after_rskip_146_activation_if_release_transaction_doesnt_have_rstTxHash() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<ReleaseTransactionSet.Entry> set = new HashSet<>();
        set.add(new ReleaseTransactionSet.Entry(btcTx, 1L)); // no rsk tx hash
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(set));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getRskTxsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeSupport bridgeSupport = getBridgeSupport(spiedBridgeConstants, provider, mock(Repository.class),
                mock(BridgeEventLogger.class), executionBlock, null, activations);

        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getRskTxsWaitingForSignatures().get(tx.getHash()));
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    public void rskTxWaitingForSignature_uses_release_transaction_rstTxHash_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<ReleaseTransactionSet.Entry> set = new HashSet<>();
        Keccak256 rskTxHash = Keccak256.ZERO_HASH;
        set.add(new ReleaseTransactionSet.Entry(btcTx, 1L, rskTxHash)); // HAS rsk tx hash
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(set));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getRskTxsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeSupport bridgeSupport = getBridgeSupport(spiedBridgeConstants, provider, mock(Repository.class),
                mock(BridgeEventLogger.class), executionBlock, null, activations);

        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getRskTxsWaitingForSignatures().get(rskTxHash));
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    public void rskTxWaitingForSignature_uses_updateCollection_rskTxHash_after_rskip_176_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<ReleaseTransactionSet.Entry> set = new HashSet<>();
        Keccak256 rskTxHash = Keccak256.ZERO_HASH;
        set.add(new ReleaseTransactionSet.Entry(btcTx, 1L, rskTxHash)); // HAS rsk tx hash
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(set));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getRskTxsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeSupport bridgeSupport = getBridgeSupport(spiedBridgeConstants, provider, mock(Repository.class),
            mock(BridgeEventLogger.class), executionBlock, null, activations);

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getRskTxsWaitingForSignatures().get(tx.getHash()));
        assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
    }

    @Test
    public void when_registerBtcTransaction_sender_not_recognized_before_rskip170_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        Assert.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_sender_not_recognized_after_rskip170_lock() throws Exception {
        // Assert
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(rskDestinationAddress, Optional.empty());

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
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
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assert.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskDestinationAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesLegacyType_beforeFork_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesLegacyType_afterFork_notWhitelisted_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Don't whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        Assert.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
                .stream()
                .map(ReleaseTransactionSet.Entry::getTransaction)
                .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
                .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction releaseTx = releaseTxs.get(0);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(amountToLock.subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(btcAddress, releaseTx.getOutput(0).getAddressFromP2PKHScript(btcParams));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx1.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesLegacyType_afterFork_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        //First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        //Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));
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

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        Assert.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
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

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, amountToLock));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));

        Assert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesSegCompatibilityType_afterFork_notWhitelisted_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        Assert.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
                .stream()
                .map(ReleaseTransactionSet.Entry::getTransaction)
                .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
                .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction releaseTx = releaseTxs.get(0);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(amountToLock.subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(btcAddress, releaseTx.getOutput(0).getAddressFromP2PKHScript(btcParams));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx1.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesMultisigType_beforeFork_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHMULTISIG, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        Assert.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesMultisigType_afterFork_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        Address btcAddress = srcKey1.toAddress(btcParams);
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHMULTISIG, btcAddress, null);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
                .stream()
                .map(ReleaseTransactionSet.Entry::getTransaction)
                .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction releaseTx = releaseTxs.get(0);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(amountToLock.subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(btcAddress, releaseTx.getOutput(0).getScriptPubKey().getToAddress(btcParams));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx1.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesMultisigWithWitnessType_beforeFork_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WSH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        Assert.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_usesMultisigWithWitnessType_afterFork_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        Address btcAddress = srcKey1.toAddress(btcParams);
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WSH, btcAddress, null);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
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
                new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
                .stream()
                .map(ReleaseTransactionSet.Entry::getTransaction)
                .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction releaseTx = releaseTxs.get(0);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(amountToLock.subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(btcAddress, releaseTx.getOutput(0).getScriptPubKey().getToAddress(btcParams));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx1.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test(expected = VerificationException.EmptyInputsOrOutputs.class)
    public void registerBtcTransaction_rejects_tx_with_witness_before_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Repository repository = createRepository();

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

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(2),
                Sha256Hash.of(new byte[]{1}),
                1,
                1,
                1,
                new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = getBridgeSupport(provider, repository, mockFactory);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());

        // When we send a segwit tx when the fork is not enabled, the tx is rejected because it does not have the
        // expected input format, therefore this method is never reached
        verify(btcBlockStore, never()).getStoredBlockAtMainChainHeight(height);
    }

    @Test
    public void registerBtcTransaction_accepts_lock_tx_with_witness_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);
        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, federation1.getAddress());
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

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(2),
                Sha256Hash.of(new byte[]{1}),
                1,
                1,
                1,
                new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstants,
            activations
        );
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
                new PeginInstructionsProvider(),
                mock(Block.class),
                mockFactory,
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(false)).isPresent());
    }

    @Test
    public void registerBtcTransaction_rejects_tx_with_witness_and_unregistered_coinbase_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
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

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(2),
                Sha256Hash.of(new byte[]{1}),
                1,
                1,
                1,
                new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
                new PeginInstructionsProvider(),
                mock(Block.class),
                mockFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(tx1.getHash(true), height);
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(Sha256Hash.class), anyLong());
    }

    @Test
    public void registerBtcTransaction_rejects_tx_with_witness_and_unqual_witness_root_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, Address.fromBase58(btcParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
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

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(2),
                Sha256Hash.of(new byte[]{1}),
                1,
                1,
                1,
                new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstants,
            activations)
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
                new PeginInstructionsProvider(),
                mock(Block.class),
                mockFactory,
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(tx1.getHash(true), height);
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(Sha256Hash.class), anyLong());
    }

    @Test
    public void registerBtcTransaction_rejects_tx_without_witness_unequal_roots_after_rskip_143() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, Address.fromBase58(btcParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
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

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(2),
                Sha256Hash.of(new byte[]{1}),
                1,
                1,
                1,
                new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstants,
            activations)
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress),
                new PeginInstructionsProvider(),
                mock(Block.class),
                mockFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(tx1.getHash(), height);
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(Sha256Hash.class), anyLong());
    }

    @Test
    public void registerBtcTransaction_accepts_lock_tx_without_witness_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);
        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, federation1.getAddress());
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

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
                btcParams,
                1,
                PegTestUtils.createHash(2),
                Sha256Hash.of(new byte[]{1}),
                1,
                1,
                1,
                new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress),
                new PeginInstructionsProvider(),
                mock(Block.class),
                mockFactory,
                activations
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    public void registerBtcTransaction_accepts_lock_tx_version1_after_rskip_170_activation()
        throws BlockStoreException, IOException, PeginInstructionsException, BridgeIllegalArgumentException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);
        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddressFromBtcLockSender = srcKey1.toAddress(btcParams);
        RskAddress rskDerivedAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);

        Coin amountToLock = Coin.COIN.multiply(10);

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
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

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcAddressFromBtcLockSender,
            rskDerivedAddress
        );
        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(
            rskDestinationAddress,
            Optional.empty()
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mockFactory,
            activations
        );

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assert.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskDerivedAddress));
        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskDestinationAddress));
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    public void registerBtcTransaction_ignores_pegin_instructions_before_rskip_170_activation()
        throws BlockStoreException, IOException, PeginInstructionsException, BridgeIllegalArgumentException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        Federation federation1 = getFederation(bridgeConstants);
        Repository repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddressFromBtcLockSender = srcKey1.toAddress(btcParams);
        RskAddress rskDerivedAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);

        Coin amountToLock = Coin.COIN.multiply(10);

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
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

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstants,
            activations
        );
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddressFromBtcLockSender, new OneOffWhiteListEntry(btcAddressFromBtcLockSender, amountToLock));

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcAddressFromBtcLockSender,
            rskDerivedAddress
        );
        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(
            rskDestinationAddress,
            Optional.empty()
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mockFactory,
            activations
        );

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assert.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskDestinationAddress));
        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskDerivedAddress));
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(0, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    public void when_registerBtcTransaction_invalidPeginProtocolVersion_afterFork_no_lock_and_refund()
        throws BlockStoreException, IOException, PeginInstructionsException, BridgeIllegalArgumentException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Federation federation1 = getFederation(bridgeConstants);
        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddressFromBtcLockSender = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);

        Coin amountToLock = Coin.COIN.multiply(10);

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
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

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstants, activations);
        provider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddressFromBtcLockSender, rskAddress);

        PeginInstructions peginInstructions = mock(PeginInstructions.class);
        when(peginInstructions.getProtocolVersion()).thenReturn(99);
        when(peginInstructions.getRskDestinationAddress()).thenReturn(rskDestinationAddress);
        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.of(peginInstructions));

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mockFactory,
            activations
        );

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        // Assert
        Assert.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assert.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assert.assertEquals(1, provider.getReleaseTransactionSet().getEntries().size());

        List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
            .stream()
            .map(ReleaseTransactionSet.Entry::getTransaction)
            .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction releaseTx = releaseTxs.get(0);
        Assert.assertEquals(1, releaseTx.getOutputs().size());
        Assert.assertThat(amountToLock.subtract(releaseTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assert.assertEquals(btcAddressFromBtcLockSender, releaseTx.getOutput(0).getScriptPubKey().getToAddress(btcParams));
        Assert.assertEquals(1, releaseTx.getInputs().size());
        Assert.assertEquals(tx1.getHash(), releaseTx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, releaseTx.getInput(0).getOutpoint().getIndex());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    public void isBlockMerkleRootValid_equal_merkle_roots() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
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

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
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

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(null);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                mock(Repository.class),
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
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

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(PegTestUtils.createHash(1));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(coinbaseInformation);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                mock(Repository.class),
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
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

        Sha256Hash merkleRoot = PegTestUtils.createHash(1);
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(merkleRoot);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(coinbaseInformation);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                mock(Repository.class),
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
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

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

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
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(provider, mock(Repository.class), mockFactory);

        MerkleBranch merkleBranch = mock(MerkleBranch.class);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(-5, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_accepts_tx_with_witness_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

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
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations
        ));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(chainHead.getHeight() - block.getHeight() + 1, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_unregistered_coinbase_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

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

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations)
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(-5, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_registered_coinbase_unequal_witnessroot_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

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
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants,
                activations));

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(-5, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_tx_without_witness_unequal_roots_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcParams);

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

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations)
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash())).thenReturn(PegTestUtils.createHash(5));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        doReturn(coinbaseInformation).when(provider).getCoinbaseInformation(registerHeader.getHash());

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(-5, confirmations);
    }

    @Test
    public void getBtcTransactionConfirmations_accepts_tx_without_witness_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

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
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations)
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash())).thenReturn(blockMerkleRoot);

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(blockMerkleRoot);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(), registerHeader.getHash(), merkleBranch);
        Assert.assertEquals(chainHead.getHeight() - block.getHeight() + 1, confirmations);
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void when_RegisterBtcCoinbaseTransaction_wrong_witnessReservedValue_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        byte[] witnessReservedValue = new byte[10];

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
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

        bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            mock(Sha256Hash.class),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            witnessReservedValue
        );
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void when_RegisterBtcCoinbaseTransaction_MerkleTreeWrongFormat_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, mock(BtcBlock.class), 5, height);
        when(btcBlockStore.getFromCache(mock(Sha256Hash.class))).thenReturn(new StoredBlock(mock(BtcBlock.class), BigInteger.ZERO, 0));

        bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            mock(Sha256Hash.class),
            new byte[]{6, 6, 6},
            mock(Sha256Hash.class),
            witnessReservedValue
        );
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    public void when_RegisterBtcCoinbaseTransaction_HashNotInPmt_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
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
    public void when_RegisterBtcCoinbaseTransaction_notVerify_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx = new BtcTransaction(btcParams);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
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

        bridgeSupport.registerBtcCoinbaseTransaction(
            tx.bitcoinSerialize(),
            registerHeader.getHash(),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            Sha256Hash.ZERO_HASH.getBytes()
        );
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    public void when_RegisterBtcCoinbaseTransaction_not_equal_merkle_root_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);
        txWithoutWitness.setWitness(0, null);

        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mockFactory,
                activations
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(txWithoutWitness.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

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

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);

        BtcBlock btcBlock = mock(BtcBlock.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(storedBlock.getHeader()).thenReturn(btcBlock);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(storedBlock);

        bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            registerHeader.getHash(),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            witnessReservedValue
        );
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void when_RegisterBtcCoinbaseTransaction_null_stored_block_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
                "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
                "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
                "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));

        txWithoutWitness.setWitness(0, null);
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
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
        bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            mock(Sha256Hash.class),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            witnessReservedValue
        );
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    public void registerBtcCoinbaseTransaction() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

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
        wc = Arrays.copyOfRange(wc, 4, 36);
        Sha256Hash witCom = Sha256Hash.wrap(wc);

        Assert.assertEquals(Sha256Hash.twiceOf(witnessRootBytes, witnessReservedValue), witCom);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
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
        bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            registerHeader.getHash(),
            pmt.bitcoinSerialize(),
            witnessRoot,
            witnessReservedValue
        );

        ArgumentCaptor<CoinbaseInformation> argumentCaptor = ArgumentCaptor.forClass(CoinbaseInformation.class);
        verify(provider).setCoinbaseInformation(eq(registerHeader.getHash()), argumentCaptor.capture());
        assertEquals(witnessRoot, argumentCaptor.getValue().getWitnessMerkleRoot());
    }

    @Test
    public void hasBtcCoinbaseTransaction_before_rskip_143_activation() throws AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(Sha256Hash.ZERO_HASH, coinbaseInformation);
        Assert.assertFalse(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    public void hasBtcCoinbaseTransaction_after_rskip_143_activation() throws AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(Sha256Hash.ZERO_HASH, coinbaseInformation);
        Assert.assertTrue(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    public void hasBtcCoinbaseTransaction_fails_with_null_coinbase_information_after_rskip_143_activation() throws AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                mock(BtcLockSenderProvider.class),
                mock(PeginInstructionsProvider.class),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        Assert.assertFalse(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    public void isAlreadyBtcTxHashProcessedHeight_true() throws IOException {
        Repository repository = createRepository();
        BtcTransaction btcTransaction = new BtcTransaction(btcParams);
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        provider.setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(), 1L);
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, provider);

        Assert.assertTrue(bridgeSupport.isAlreadyBtcTxHashProcessed(btcTransaction.getHash()));
    }

    @Test
    public void isAlreadyBtcTxHashProcessedHeight_false() throws IOException {
        BtcTransaction btcTransaction = new BtcTransaction(btcParams);
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class));

        Assert.assertFalse(bridgeSupport.isAlreadyBtcTxHashProcessed(btcTransaction.getHash()));
    }

    @Test
    public void validationsForRegisterBtcTransaction_negative_height() throws BlockStoreException, BridgeIllegalArgumentException {
        BtcTransaction tx = new BtcTransaction(btcParams);
        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, provider);

        byte[] data = Hex.decode("ab");

        Assert.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(tx.getHash(), -1, data, data));
    }

    @Test
    public void validationsForRegisterBtcTransaction_insufficient_confirmations() throws BlockStoreException, BridgeIllegalArgumentException {
        BtcTransaction tx = new BtcTransaction(btcParams);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            btcBlockStoreFactory
        );

        byte[] data = Hex.decode("ab");

        Assert.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(tx.getHash(), 100, data, data));
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void validationsForRegisterBtcTransaction_invalid_pmt() throws BlockStoreException, BridgeIllegalArgumentException {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        String pmtSerializedEncoded = "030000000279e7c0da739df8a00f12c0bff55e5438f530aa5859ff9874258cd7bad3fe709746aff89" +
                "7e6a851faa80120d6ae99db30883699ac0428fc7192d6c3fec0ca64010d";
        byte[] pmtSerialized = Hex.decode(pmtSerializedEncoded);

        int btcTxHeight = 2;

        doReturn(btcParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                null,
                mockFactory
        );

        bridgeSupport.validationsForRegisterBtcTransaction(
            btcTx.getHash(),
            btcTxHeight,
            pmtSerialized,
            btcTx.bitcoinSerialize()
        );
    }

    @Test
    public void validationsForRegisterBtcTransaction_hash_not_in_pmt() throws BlockStoreException, AddressFormatException, BridgeIllegalArgumentException {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash(0));

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        int btcTxHeight = 2;

        doReturn(btcParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                null,
                mockFactory
        );

        Assert.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(btcTx.getHash(), 0, pmt.bitcoinSerialize(), btcTx.bitcoinSerialize()));
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void validationsForRegisterBtcTransaction_exception_in_getTxnHashAndMerkleRoot()
        throws BlockStoreException, AddressFormatException, BridgeIllegalArgumentException {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash(0));

        PartialMerkleTree pmt = mock(PartialMerkleTree.class);
        when(pmt.getTxnHashAndMerkleRoot(anyList())).thenReturn(Sha256Hash.ZERO_HASH).thenThrow(VerificationException.class);

        int btcTxHeight = 2;

        doReturn(btcParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                null,
                mockFactory
        );

        Assert.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(btcTx.getHash(), 0, pmt.bitcoinSerialize(), btcTx.bitcoinSerialize()));
    }


    @Test(expected = VerificationException.class)
    public void validationsForRegisterBtcTransaction_tx_without_inputs_before_rskip_143() throws BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        BtcTransaction btcTx = new BtcTransaction(btcParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        int btcTxHeight = 2;

        doReturn(btcParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                null,
                mockFactory,
                activations
        );

        bridgeSupport.validationsForRegisterBtcTransaction(btcTx.getHash(), btcTxHeight, pmt.bitcoinSerialize(), btcTx.bitcoinSerialize());
    }

    @Test(expected = VerificationException.class)
    public void validationsForRegisterBtcTransaction_tx_without_inputs_after_rskip_143() throws BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        BtcTransaction btcTx = new BtcTransaction(btcParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        int btcTxHeight = 2;

        doReturn(btcParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                null,
                mockFactory,
                activations
        );

        bridgeSupport.validationsForRegisterBtcTransaction(btcTx.getHash(), 0, pmt.bitcoinSerialize(), Hex.decode("00000000000100"));
    }

    @Test
    public void validationsForRegisterBtcTransaction_invalid_block_merkle_root() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create tx and PMT. Also create a btc block, that has not relation with tx and PMT.
        // The tx will be rejected because merkle block doesn't match.
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), Sha256Hash.ZERO_HASH,
                        1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mock(BridgeEventLogger.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                mock(Block.class),
                mock(Context.class),
                mock(FederationSupport.class),
                btcBlockStoreFactory,
                mock(ActivationConfig.ForBlock.class)
        );

        Assert.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(tx.getHash(), height, pmt.bitcoinSerialize(), tx.bitcoinSerialize()));
    }

    @Test
    public void validationsForRegisterBtcTransaction_successful() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstants.getGenesisFederation());

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT. The block includes a valid merkleRoot calculated from the PMT.
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                new co.rsk.bitcoinj.core.BtcBlock(bridgeConstants.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                        1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                mockBridgeStorageProvider,
                mock(BridgeEventLogger.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                mock(Block.class),
                mock(Context.class),
                mock(FederationSupport.class),
                btcBlockStoreFactory,
                mock(ActivationConfig.ForBlock.class)
        );

        Assert.assertTrue(bridgeSupport.validationsForRegisterBtcTransaction(tx.getHash(), height, pmt.bitcoinSerialize(), tx.bitcoinSerialize()));
    }

    @Test
    public void addSignature_fedPubKey_belongs_to_active_federation() throws Exception {
        //Setup
        FederationSupport mockFederationSupport = mock(FederationSupport.class);

        // Creates new federation
        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                provider,
                mock(BridgeEventLogger.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                mock(Block.class),
                new Context(bridgeConstants.getBtcParams()),
                mockFederationSupport,
                null,
                null
        );

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(provider.getRskTxsWaitingForSignatures()).thenReturn(new TreeMap<>());

        bridgeSupport.addSignature(BtcECKey.fromPrivate(Hex.decode("fa01")), null,
                PegTestUtils.createHash3(1).getBytes());

        verify(provider, times(1)).getRskTxsWaitingForSignatures();
    }

    @Test
    public void addSignature_fedPubKey_belongs_to_retiring_federation() throws Exception {
        //Setup
        FederationSupport mockFederationSupport = mock(FederationSupport.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                provider,
                mock(BridgeEventLogger.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                mock(Block.class),
                new Context(bridgeConstants.getBtcParams()),
                mockFederationSupport,
                null,
                null
        );

        // Creates retiring federation
        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")));
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation retiringFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        // Creates active federation
        List<BtcECKey> activeFederationKeys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa03")),
                BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        activeFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(mockFederationSupport.getRetiringFederation()).thenReturn(retiringFederation);
        when(provider.getRskTxsWaitingForSignatures()).thenReturn(new TreeMap<>());

        bridgeSupport.addSignature(BtcECKey.fromPrivate(Hex.decode("fa01")), null,
                PegTestUtils.createHash3(1).getBytes());

        verify(provider, times(1)).getRskTxsWaitingForSignatures();
    }

    @Test
    public void addSignature_fedPubKey_no_belong_to_retiring_or_active_federation() throws Exception {
        //Setup
        FederationSupport mockFederationSupport = mock(FederationSupport.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
                bridgeConstants,
                provider,
                mock(BridgeEventLogger.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                mock(Block.class),
                new Context(bridgeConstants.getBtcParams()),
                mockFederationSupport,
                null,
                null
        );

        // Creates retiring federation
        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation retiringFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams
        );

        // Creates active federation
        List<BtcECKey> activeFederationKeys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa03")),
                BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        activeFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation activeFederation = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
                Instant.ofEpochMilli(1000L),
                0L,
                btcParams);

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(mockFederationSupport.getRetiringFederation()).thenReturn(retiringFederation);

        bridgeSupport.addSignature(BtcECKey.fromPrivate(Hex.decode("fa05")), null,
                PegTestUtils.createHash3(1).getBytes());

        verify(provider, times(0)).getRskTxsWaitingForSignatures();
    }

    @Test
    public void addSignature_fedPubKey_no_belong_to_active_federation_no_existing_retiring_fed() throws Exception {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                mock(Block.class),
                null
        );

        bridgeSupport.addSignature(BtcECKey.fromPrivate(Hex.decode("fa03")), null,
                PegTestUtils.createHash3(1).getBytes());

        verify(provider, times(0)).getRskTxsWaitingForSignatures();
    }

    @Test
    public void addSignatureToMissingTransaction() throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = createRepository();

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
                repository,
                PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants,
                activationsBeforeForks
        );
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, providerForSupport, repository,
                mock(BridgeEventLogger.class), mock(Block.class), null);

        bridgeSupport.addSignature(federation.getBtcPublicKeys().get(0), null, PegTestUtils.createHash().getBytes());
        bridgeSupport.save();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants, activationsBeforeForks);

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void addSignatureFromInvalidFederator() throws Exception {
        Repository repository = createRepository();

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants,
                new BridgeStorageProvider(
                        repository,
                        PrecompiledContracts.BRIDGE_ADDR,
                        bridgeConstants,
                        activationsBeforeForks),
                repository,
                mock(BridgeEventLogger.class),
                mock(Block.class), null);

        bridgeSupport.addSignature(new BtcECKey(), null, PegTestUtils.createHash().getBytes());
        bridgeSupport.save();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants, activationsBeforeForks);

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
    }

    @Test
    public void addSignatureWithInvalidSignature() throws Exception {
        addSignatureFromValidFederator(Lists.newArrayList(new BtcECKey()), 1, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureWithLessSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 0, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureWithMoreSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 2, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureNonCanonicalSignature() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 1, false, false, "InvalidParameters");
    }

    @Test
    public void addSignatureCreateEventLog() throws Exception {
        // Setup
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository track = createRepository().startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        // Build prev btc tx
        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);

        // Build btc tx to be signed
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(prevOut).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        TransactionOutput output = new TransactionOutput(btcParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcParams));
        btcTx.addOutput(output);

        // Save btc tx to be signed
        final Keccak256 rskTxHash = PegTestUtils.createHash3(1);
        provider.getRskTxsWaitingForSignatures().put(rskTxHash, btcTx);
        provider.save();
        track.commit();

        // Setup BridgeSupport
        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);
        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                track,
                eventLogger,
                mock(Block.class),
                null
        );

        // Create signed hash of Btc tx
        Script inputScript = btcTx.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        Sha256Hash sigHash = btcTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey privateKeyToSignWith = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0);

        BtcECKey.ECDSASignature sig = privateKeyToSignWith.sign(sigHash);
        List derEncodedSigs = Collections.singletonList(sig.encodeToDER());

        BtcECKey federatorPubKey = findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeyToSignWith);
        bridgeSupport.addSignature(federatorPubKey, derEncodedSigs, rskTxHash.getBytes());

        verify(eventLogger, times(1)).logAddSignature(federatorPubKey, btcTx, rskTxHash.getBytes());
    }

    @Test
    public void addSignatureTwice() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 1, true, true, "PartiallySigned");
    }

    @Test
    public void addSignatureOneSignature() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 1, true, false, "PartiallySigned");
    }

    @Test
    public void addSignatureTwoSignatures() throws Exception {
        List<BtcECKey> federatorPrivateKeys = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS;
        List<BtcECKey> keys = Arrays.asList(federatorPrivateKeys.get(0), federatorPrivateKeys.get(1));
        addSignatureFromValidFederator(keys, 1, true, false, "FullySigned");
    }

    @Test
    public void addSignatureMultipleInputsPartiallyValid() throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = createRepository();

        final Keccak256 keccak256 = PegTestUtils.createHash3(1);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants, activationsBeforeForks);

        BtcTransaction prevTx1 = new BtcTransaction(btcParams);
        TransactionOutput prevOut1 = new TransactionOutput(btcParams, prevTx1, Coin.FIFTY_COINS, federation.getAddress());
        prevTx1.addOutput(prevOut1);
        BtcTransaction prevTx2 = new BtcTransaction(btcParams);
        TransactionOutput prevOut2 = new TransactionOutput(btcParams, prevTx1, Coin.FIFTY_COINS, federation.getAddress());
        prevTx2.addOutput(prevOut2);
        BtcTransaction prevTx3 = new BtcTransaction(btcParams);
        TransactionOutput prevOut3 = new TransactionOutput(btcParams, prevTx1, Coin.FIFTY_COINS, federation.getAddress());
        prevTx3.addOutput(prevOut3);

        BtcTransaction t = new BtcTransaction(btcParams);
        TransactionOutput output = new TransactionOutput(btcParams, t, Coin.COIN, new BtcECKey().toAddress(btcParams));
        t.addOutput(output);
        t.addInput(prevOut1).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        t.addInput(prevOut2).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        t.addInput(prevOut3).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        provider.getRskTxsWaitingForSignatures().put(keccak256, t);
        provider.save();

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(bridgeConstants, activations, logs);
        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                new BridgeStorageProvider(
                        repository,
                        contractAddress,
                        bridgeConstants,
                        activationsAfterForks
                ),
                repository,
                eventLogger,
                mock(Block.class),
                null
        );

        // Generate valid signatures for inputs
        List<byte[]> derEncodedSigsFirstFed = new ArrayList<>();
        List<byte[]> derEncodedSigsSecondFed = new ArrayList<>();
        BtcECKey privateKeyOfFirstFed = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0);
        BtcECKey privateKeyOfSecondFed = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(1);

        BtcECKey.ECDSASignature lastSig = null;
        for (int i = 0; i < 3; i++) {
            Script inputScript = t.getInput(i).getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            Sha256Hash sighash = t.hashForSignature(i, redeemScript, BtcTransaction.SigHash.ALL, false);

            // Sign the last input with a random key
            // but keep the good signature for a subsequent call
            BtcECKey.ECDSASignature sig = privateKeyOfFirstFed.sign(sighash);
            if (i == 2) {
                lastSig = sig;
                sig = new BtcECKey().sign(sighash);
            }
            derEncodedSigsFirstFed.add(sig.encodeToDER());
            derEncodedSigsSecondFed.add(privateKeyOfSecondFed.sign(sighash).encodeToDER());
        }

        // Sign with two valid signatures and one invalid signature
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256.getBytes());
        bridgeSupport.save();

        // Sign with two valid signatures and one malformed signature
        byte[] malformedSignature = new byte[lastSig.encodeToDER().length];
        for (int i = 0; i < malformedSignature.length; i++) {
            malformedSignature[i] = (byte) i;
        }
        derEncodedSigsFirstFed.set(2, malformedSignature);
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256.getBytes());
        bridgeSupport.save();

        // Sign with fully valid signatures for same federator
        derEncodedSigsFirstFed.set(2, lastSig.encodeToDER());
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256.getBytes());
        bridgeSupport.save();

        // Sign with second federation
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeyOfSecondFed), derEncodedSigsSecondFed, keccak256.getBytes());
        bridgeSupport.save();

        provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertThat(logs, is(not(empty())));
        Assert.assertThat(logs, hasSize(5));
        LogInfo releaseTxEvent = logs.get(4);
        Assert.assertThat(releaseTxEvent.getTopics(), hasSize(1));
        Assert.assertThat(releaseTxEvent.getTopics(), hasItem(Bridge.RELEASE_BTC_TOPIC));
        BtcTransaction releaseTx = new BtcTransaction(btcParams, ((RLPList) RLP.decode2(releaseTxEvent.getData()).get(0)).get(1).getRLPData());
        // Verify all inputs fully signed
        for (int i = 0; i < releaseTx.getInputs().size(); i++) {
            Script retrievedScriptSig = releaseTx.getInput(i).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            assertTrue(Objects.requireNonNull(retrievedScriptSig.getChunks().get(1).data).length > 0);
            assertTrue(Objects.requireNonNull(retrievedScriptSig.getChunks().get(2).data).length > 0);
        }
    }

    @Test
    public void getTransactionType_pegin_tx() {
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class));
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addOutput(Coin.COIN.multiply(10), bridgeConstants.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        Assert.assertEquals(TxType.PEGIN, bridgeSupport.getTransactionType(btcTx));
    }

    @Test
    public void getTransactionType_pegout_tx() {
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class), mock(ActivationConfig.ForBlock.class));
        Federation federation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> federationPrivateKeys = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS;
        Address randomAddress = new Address(
                        btcParams,
                        Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a")
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx1 = new BtcTransaction(btcParams);
        releaseTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput releaseInput1 = new TransactionInput(
            btcParams,
            releaseTx1,
            new byte[]{},
            new TransactionOutPoint(
                btcParams,
                0,
                Sha256Hash.ZERO_HASH
            )
        );

        releaseTx1.addInput(releaseInput1);

        // Sign it using the Federation members
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(federation);
        releaseInput1.setScriptSig(inputScript);

        Sha256Hash sighash = releaseTx1.hashForSignature(
            0,
            redeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        for (int i = 0; i < federation.getNumberOfSignaturesRequired(); i++) {
            BtcECKey federatorPrivKey = federationPrivateKeys.get(i);
            BtcECKey federatorPublicKey = federation.getBtcPublicKeys().get(i);

            BtcECKey.ECDSASignature sig = federatorPrivKey.sign(sighash);
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

            int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
            inputScript = ScriptBuilder.updateScriptWithSignature(
                inputScript,
                txSig.encodeToBitcoin(),
                sigIndex,
                1,
                1
            );
        }
        releaseInput1.setScriptSig(inputScript);

        Assert.assertEquals(TxType.PEGOUT, bridgeSupport.getTransactionType(releaseTx1));
    }

    @Test
    public void getTransactionType_migration_tx() {
        FederationSupport mockFederationSupport = mock(FederationSupport.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstants,
            provider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            new Context(bridgeConstants.getBtcParams()),
            mockFederationSupport,
            null,
            mock(ActivationConfig.ForBlock.class)
        );

        Federation retiringFederation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> retiringFederationPrivateKeys = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS;

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02")),
            BtcECKey.fromPrivate(Hex.decode("fb03"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation activeFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L,
            btcParams
        );

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(mockFederationSupport.getRetiringFederation()).thenReturn(retiringFederation);

        BtcTransaction migrationTx = new BtcTransaction(btcParams);
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());
        TransactionInput migrationTxInput = new TransactionInput(
            btcParams,
            null,
            new byte[]{},
            new TransactionOutPoint(
                btcParams,
                0,
                Sha256Hash.ZERO_HASH
            )
        );

        migrationTx.addInput(migrationTxInput);

        // Sign it using the Federation members
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(retiringFederation);
        Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(retiringFederation);
        migrationTxInput.setScriptSig(inputScript);

        Sha256Hash sighash = migrationTx.hashForSignature(
            0,
            redeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        for (int i = 0; i < retiringFederation.getNumberOfSignaturesRequired(); i++) {
            BtcECKey federatorPrivKey = retiringFederationPrivateKeys.get(i);
            BtcECKey federatorPublicKey = retiringFederation.getBtcPublicKeys().get(i);

            BtcECKey.ECDSASignature sig = federatorPrivKey.sign(sighash);
            TransactionSignature txSig = new TransactionSignature(
                sig,
                BtcTransaction.SigHash.ALL,
                false
            );

            int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
            inputScript = ScriptBuilder.updateScriptWithSignature(
                inputScript,
                txSig.encodeToBitcoin(),
                sigIndex,
                1,
                1
            );
        }
        migrationTxInput.setScriptSig(inputScript);
        Assert.assertEquals(TxType.MIGRATION, bridgeSupport.getTransactionType(migrationTx));

        when(mockFederationSupport.getRetiringFederation()).thenReturn(null);
        when(provider.getLastRetiredFederationP2SHScript()).thenReturn(Optional.of(retiringFederation.getP2SHScript()));
        Assert.assertEquals(TxType.MIGRATION, bridgeSupport.getTransactionType(migrationTx));
    }

    @Test
    public void getTransactionType_sentFromOldFed_afterRskip199_migration_tx() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP199)).thenReturn(true);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            activations
        );

        int multisigSigners = 2;
        Federation activeFederation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> oldFederationPrivateKeys = REGTEST_OLD_FEDERATION_PRIVATE_KEYS;
        Script redeemScript = ScriptBuilder.createRedeemScript(multisigSigners, oldFederationPrivateKeys);
        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        Address sender = Address.fromP2SHScript(btcParams, outputScript);

        Assert.assertEquals(bridgeConstants.getOldFederationAddress(), sender.toBase58());

        // Create a tx from the old fed address to the active fed
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, redeemScript);

        Script inputScript = outputScript.createEmptyInputScript(null, redeemScript);
        tx.getInput(0).setScriptSig(inputScript);

        Sha256Hash sighash = tx.hashForSignature(
            0,
            redeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        for (int i = 0; i < multisigSigners; i++) {
            BtcECKey privateKey = oldFederationPrivateKeys.get(i);
            BtcECKey publicKey = BtcECKey.fromPublicOnly(privateKey.getPubKeyPoint().getEncoded(true));

            BtcECKey.ECDSASignature sig = privateKey.sign(sighash);
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

            int sigIndex = inputScript.getSigInsertionIndex(sighash, publicKey);
            inputScript = ScriptBuilder.updateScriptWithSignature(
                inputScript,
                txSig.encodeToBitcoin(),
                sigIndex,
                1,
                1
            );
        }
        tx.getInput(0).setScriptSig(inputScript);

        Assert.assertEquals(TxType.MIGRATION, bridgeSupport.getTransactionType(tx));
    }

    @Test
    public void getTransactionType_sentFromOldFed_beforeRskip199_pegin_tx() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP199)).thenReturn(false);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            activations
        );

        int multisigSigners = 2;
        Federation activeFederation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> oldFederationPrivateKeys = REGTEST_OLD_FEDERATION_PRIVATE_KEYS;
        Script redeemScript = ScriptBuilder.createRedeemScript(multisigSigners, oldFederationPrivateKeys);
        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        Address sender = Address.fromP2SHScript(btcParams, outputScript);

        Assert.assertEquals(bridgeConstants.getOldFederationAddress(), sender.toBase58());

        // Create a tx from the old fed address to the active fed
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, redeemScript);

        Script inputScript = outputScript.createEmptyInputScript(null, redeemScript);
        tx.getInput(0).setScriptSig(inputScript);

        Sha256Hash sighash = tx.hashForSignature(
            0,
            redeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        for (int i = 0; i < multisigSigners; i++) {
            BtcECKey privateKey = oldFederationPrivateKeys.get(i);
            BtcECKey publicKey = BtcECKey.fromPublicOnly(privateKey.getPubKeyPoint().getEncoded(true));

            BtcECKey.ECDSASignature sig = privateKey.sign(sighash);
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

            int sigIndex = inputScript.getSigInsertionIndex(sighash, publicKey);
            inputScript = ScriptBuilder.updateScriptWithSignature(
                inputScript,
                txSig.encodeToBitcoin(),
                sigIndex,
                1,
                1
            );
        }
        tx.getInput(0).setScriptSig(inputScript);

        Assert.assertEquals(TxType.PEGIN, bridgeSupport.getTransactionType(tx));
    }

    @Test
    public void getTransactionType_sentFromP2SH_afterRskip199_pegin_tx() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP199)).thenReturn(true);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            activations
        );

        int multisigSigners = 2;
        Federation activeFederation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> privateKeys = Arrays.asList(new BtcECKey(), new BtcECKey(), new BtcECKey());
        Script redeemScript = ScriptBuilder.createRedeemScript(multisigSigners, privateKeys);
        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        Address sender = Address.fromP2SHScript(btcParams, outputScript);

        Assert.assertNotEquals(bridgeConstants.getOldFederationAddress(), sender.toBase58());

        // Create a tx from a p2sh multisig address to the active fed
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, redeemScript);

        Script inputScript = outputScript.createEmptyInputScript(null, redeemScript);
        tx.getInput(0).setScriptSig(inputScript);

        Sha256Hash sighash = tx.hashForSignature(
            0,
            redeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        for (int i = 0; i < multisigSigners; i++) {
            BtcECKey privateKey = privateKeys.get(i);
            BtcECKey publicKey = BtcECKey.fromPublicOnly(privateKey.getPubKeyPoint().getEncoded(true));

            BtcECKey.ECDSASignature sig = privateKey.sign(sighash);
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

            int sigIndex = inputScript.getSigInsertionIndex(sighash, publicKey);
            inputScript = ScriptBuilder.updateScriptWithSignature(
                inputScript,
                txSig.encodeToBitcoin(),
                sigIndex,
                1,
                1
            );
        }
        tx.getInput(0).setScriptSig(inputScript);

        Assert.assertEquals(TxType.PEGIN, bridgeSupport.getTransactionType(tx));
    }

    @Test
    public void getTransactionType_unknown_tx() {
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class), mock(ActivationConfig.ForBlock.class));
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        Assert.assertEquals(TxType.UNKNOWN, bridgeSupport.getTransactionType(btcTx));
    }

    @Test
    public void processPegIn_version0_tx_no_lockable_by_invalid_sender() throws IOException, RegisterBtcTransactionException {
        assertRefundInProcessPegInVersionLegacy(
            true,
            false,
            TxSenderAddressType.P2SHMULTISIG,
            ConsensusRule.RSKIP143
        );
    }

    @Test
    public void processPegIn_version0_tx_no_lockable_by_not_whitelisted_address() throws IOException, RegisterBtcTransactionException {
        assertRefundInProcessPegInVersionLegacy(
            false,
            false,
            TxSenderAddressType.P2PKH,
            null
        );
    }

    @Test
    public void processPegIn_version0_tx_no_lockable_by_surpassing_locking_cap() throws IOException, RegisterBtcTransactionException {
        assertRefundInProcessPegInVersionLegacy(
            true,
            true,
            TxSenderAddressType.P2PKH,
            ConsensusRule.RSKIP134
        );
    }

    @Test
    public void processPegIn_version1_tx_no_lockable_by_surpassing_locking_cap() throws IOException,
        RegisterBtcTransactionException, PeginInstructionsException {

        assertRefundInProcessPegInVersion1(
            TxSenderAddressType.P2PKH,
            Optional.empty(),
            Arrays.asList(ConsensusRule.RSKIP134, ConsensusRule.RSKIP170)
        );
    }

    @Test
    public void processPegIn_version1_tx_no_lockable_by_surpassing_locking_cap_unknown_sender_with_refund_address()
        throws IOException, RegisterBtcTransactionException, PeginInstructionsException {

        BtcECKey key = new BtcECKey();
        Address btcRefundAddress = key.toAddress(btcParams);

        assertRefundInProcessPegInVersion1(
            TxSenderAddressType.UNKNOWN,
            Optional.of(btcRefundAddress),
            Arrays.asList(ConsensusRule.RSKIP134, ConsensusRule.RSKIP170)
        );
    }

    @Test
    public void processPegIn_version1_tx_no_lockable_by_surpassing_locking_cap_unknown_sender_without_refund_address()
        throws IOException, RegisterBtcTransactionException, PeginInstructionsException {

        assertRefundInProcessPegInVersion1(
            TxSenderAddressType.UNKNOWN,
            Optional.empty(),
            Arrays.asList(ConsensusRule.RSKIP134, ConsensusRule.RSKIP170)
        );
    }

    @Test(expected = RegisterBtcTransactionException.class)
    public void processPegIn_noPeginInstructions() throws IOException, RegisterBtcTransactionException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            activations
        );

        BtcTransaction btcTx = mock(BtcTransaction.class);
        when(btcTx.getValueSentToMe(any())).thenReturn(Coin.valueOf(1));

        bridgeSupport.processPegIn(
            btcTx,
            mock(Transaction.class),
            0,
            mock(Sha256Hash.class)
        );
    }

    @Test
    public void processPegIn_errorParsingPeginInstructions_beforeRskip170_dontRefundSender() throws IOException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        Repository repository = createRepository();

        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addOutput(Coin.COIN.multiply(10), bridgeConstants.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(btcTx)).thenReturn(Optional.empty());

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            btcLockSenderProvider,
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        // Act
        try {
            bridgeSupport.processPegIn(btcTx, mock(Transaction.class), 0, mock(Sha256Hash.class));
            Assert.fail(); // Should have thrown a RegisterBtcTransactionException
        } catch (Exception e) {
            // Assert
            Assert.assertTrue(e instanceof RegisterBtcTransactionException);
            Assert.assertEquals(0, releaseTransactionSet.getEntries().size());
        }
    }

    @Test
    public void processPegIn_errorParsingPeginInstructions_afterRskip170_refundSender()
        throws IOException, PeginInstructionsException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Repository repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Address btcSenderAddress = srcKey1.toAddress(btcParams);

        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addOutput(Coin.COIN.multiply(10), bridgeConstants.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcSenderAddress,
            rskAddress
        );

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(btcTx)).thenThrow(PeginInstructionsException.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        // Act
        try {
            bridgeSupport.processPegIn(btcTx, mock(Transaction.class), 0, mock(Sha256Hash.class));
            Assert.fail(); // Should have thrown a RegisterBtcTransactionException
        } catch (Exception ex) {
            // Assert
            Assert.assertTrue(ex instanceof RegisterBtcTransactionException);
            Assert.assertEquals(1, releaseTransactionSet.getEntries().size());

            // Check rejection tx input was created from btc tx and sent to the btc refund address indicated by the user
            boolean successfulRejection = false;
            for (ReleaseTransactionSet.Entry e : releaseTransactionSet.getEntries()) {
                BtcTransaction refundTx = e.getTransaction();
                if (refundTx.getInput(0).getOutpoint().getHash() == btcTx.getHash() &&
                    refundTx.getOutput(0).getScriptPubKey().getToAddress(btcParams).equals(btcSenderAddress)) {
                    successfulRejection = true;
                    break;
                }
            }

            Assert.assertTrue(successfulRejection);
        }
    }

    @Test
    public void receiveHeader_time_not_present_in_storage() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstants,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            activationsBeforeForks
        );

        StoredBlock storedBlock2 = mock(StoredBlock.class);
        when(storedBlock.build(btcBlock2)).thenReturn(storedBlock2);

        bridgeSupport.receiveHeader(btcBlock2);

        verify(btcBlockStore, times(1)).put(storedBlock2);
        verify(provider, times(1)).getReceiveHeadersLastTimestamp();
        verify(provider, times(1)).setReceiveHeadersLastTimestamp(anyLong());
    }

    @Test
    public void receiveHeader_time_exceed_X() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        Block executionBlockMock = mock(Block.class);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstants,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            executionBlockMock,
            activationsAfterForks
        );

        long timeStamp_old = executionBlockMock.getTimestamp() - (bridgeConstants.getMinSecondsBetweenCallsToReceiveHeader() * 2L);
        doReturn(Optional.of(timeStamp_old)).when(provider).getReceiveHeadersLastTimestamp();

        StoredBlock storedBlock2 = mock(StoredBlock.class);
        when(storedBlock.build(btcBlock2)).thenReturn(storedBlock2);

        bridgeSupport.receiveHeader(btcBlock2);

        verify(btcBlockStore, times(1)).put(storedBlock2);
        verify(provider, times(1)).setReceiveHeadersLastTimestamp(anyLong());
    }

    @Test
    public void receiveHeader_time_less_than_X() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        Block executionBlockMock = mock(Block.class);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstants,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            executionBlockMock,
            activationsAfterForks
        );

        long timeStamp_old = executionBlockMock.getTimestamp() - (bridgeConstants.getMinSecondsBetweenCallsToReceiveHeader() / 2L);
        doReturn(Optional.of(timeStamp_old)).when(provider).getReceiveHeadersLastTimestamp();

        int result = bridgeSupport.receiveHeader(btcBlock2);

        StoredBlock storedBlock2 = storedBlock.build(btcBlock2);

        verify(btcBlockStore, never()).put(storedBlock2);
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assert.assertEquals(-1, result);
    }

    @Test
    public void receiveHeader_unexpected_exception() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.of(new byte[]{}));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstants,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            activationsAfterForks
        );

        int result = bridgeSupport.receiveHeader(btcBlock2);

        verify(btcBlockStore, never()).put(storedBlock);
        verify(provider, times(1)).getReceiveHeadersLastTimestamp();
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assert.assertEquals(-99, result);
    }

    @Test
    public void receiveHeader_previous_block_not_in_storage() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstants,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            activationsAfterForks
        );

        when(btcBlockStore.get(any())).thenReturn(null);
        int result = bridgeSupport.receiveHeader(btcBlock2);

        StoredBlock storedBlock2 = storedBlock.build(btcBlock2);

        // Calls put when is adding the block header. (Saves his storedBlock)
        verify(btcBlockStore, never()).put(storedBlock2);
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assert.assertEquals(-3, result);
    }

    @Test
    public void receiveHeader_block_too_old() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstants,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            activationsAfterForks
        );
        ;
        when(storedBlock.getHeight()).thenReturn(10, 5000, 10);

        int result = bridgeSupport.receiveHeader(btcBlock2);

        StoredBlock storedBlock2 = storedBlock.build(btcBlock2);

        verify(btcBlockStore, never()).put(storedBlock2);
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assert.assertEquals(-2, result);
    }

    @Test
    public void receiveHeader_block_exist_in_storage() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock = mock(BtcBlock.class);
        Sha256Hash btcBlockHash = PegTestUtils.createHash(1);
        when(btcBlock.getHash()).thenReturn(btcBlockHash);
        when(btcBlockStore.get(btcBlockHash)).thenReturn(mock(StoredBlock.class));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                        repository,
                        contractAddress,
                        bridgeConstants,
                        activationsAfterForks
                )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
                btcBlock,
                btcBlockStore,
                provider,
                storedBlock,
                activationsAfterForks
        );

       // when(btcBlockStore.get(any())).thenReturn(nul);
        int result = bridgeSupport.receiveHeader(btcBlock);

        // Calls put when is adding the block header. (Saves his storedBlock)
        verify(btcBlockStore, never()).put(any(StoredBlock.class));
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assert.assertEquals(-4, result);
    }

    private void assertRefundInProcessPegInVersionLegacy(
        boolean isWhitelisted,
        boolean mockLockingCap,
        TxSenderAddressType lockSenderAddressType,
        @Nullable ConsensusRule consensusRule) throws IOException, RegisterBtcTransactionException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        if (consensusRule != null) {
            when(activations.isActive(consensusRule)).thenReturn(true);
        }

        Repository repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addOutput(Coin.COIN.multiply(10), bridgeConstants.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(eq(btcAddress), any(Coin.class), any(int.class))).thenReturn(isWhitelisted);
        when(provider.getLockWhitelist()).thenReturn(lockWhitelist);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);

        if (mockLockingCap) {
            when(provider.getLockingCap()).thenReturn(Coin.COIN.multiply(1));
        }

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(lockSenderAddressType, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                provider,
                repository,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
                mock(Block.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        // Act
        bridgeSupport.processPegIn(btcTx, mock(Transaction.class), 0, mock(Sha256Hash.class));

        // Assert
        Assert.assertEquals(1, releaseTransactionSet.getEntries().size());

        // Check rejection tx input was created from btc tx
        boolean successfulRejection = false;
        for (ReleaseTransactionSet.Entry e : releaseTransactionSet.getEntries()) {
            if (e.getTransaction().getInput(0).getOutpoint().getHash() == btcTx.getHash()) {
                successfulRejection = true;
                break;
            }
        }

        Assert.assertTrue(successfulRejection);

        // Check tx was not marked as processed
        Assert.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTx.getHash()).isPresent());
    }

    @Test
    public void registerFastBridgeBtcTransaction_is_not_contract()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class));
        Transaction rskTxMock = mock(Transaction.class);
        Keccak256 hash = new Keccak256(HashUtil.keccak256(new byte[]{}));
        when(rskTxMock.getHash()).thenReturn(hash);

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
                rskTxMock,
                new byte[]{},
                0,
                new byte[]{},
                PegTestUtils.createHash3(0),
                mock(Address.class),
                mock(RskAddress.class),
                mock(Address.class),
                false
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_UNPROCESSABLE_TX_NOT_CONTRACT_ERROR_CODE, result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_sender_is_not_lbc()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            mock(Repository.class),
            mock(BridgeEventLogger.class),
            null,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            mock(Address.class),
            mock(RskAddress.class),
            mock(Address.class),
            false
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_UNPROCESSABLE_TX_INVALID_SENDER_ERROR_CODE, result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_TxAlreadySavedInStorage_returnsError()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.isFastBridgeFederationDerivationHashUsed(any(), any())).thenReturn(true);
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BridgeSupport bridgeSupport = spy(getBridgeSupport(
                bridgeConstants,
                provider,
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                null,
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        ));

        doReturn(PegTestUtils.createHash3(5))
                .when(bridgeSupport)
                .getFastBridgeDerivationHash(any(), any(), any(), any());

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
                rskTx,
                tx.bitcoinSerialize(),
                100,
                Hex.decode("ab"),
                PegTestUtils.createHash3(0),
                mock(Address.class),
                lbcAddress,
                mock(Address.class),
                false
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR_CODE, result);
    }


    @Test
    public void registerFastBridgeBtcTransaction_validationsForRegisterBtcTransaction_returns_false()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BridgeSupport bridgeSupport = spy(getBridgeSupport(
            bridgeConstants,
            provider,
            mock(Repository.class),
            mock(BridgeEventLogger.class),
            null,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(PegTestUtils.createHash3(5))
                .when(bridgeSupport)
                .getFastBridgeDerivationHash(any(), any(), any(), any());

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            mock(Address.class),
            lbcAddress,
            mock(Address.class),
            false
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_UNPROCESSABLE_TX_VALIDATIONS_ERROR, result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_amount_sent_is_0()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, new BtcECKey().toAddress(btcParams));
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            mock(Address.class),
            lbcAddress,
            mock(Address.class),
            false
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_UNPROCESSABLE_TX_VALUE_ZERO_ERROR, result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_TxWitnessAlreadySavedInStorage_returnsError()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.isFastBridgeFederationDerivationHashUsed(any(), any())).thenReturn(false).thenReturn(true);

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
                bridgeConstants,
                provider,
                mock(BridgeEventLogger.class),
                new BtcLockSenderProvider(),
                new PeginInstructionsProvider(),
                mock(Repository.class),
                mock(Block.class),
                btcContext,
                mock(FederationSupport.class),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        ));

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
                any(Keccak256.class),
                any(Address.class),
                any(Address.class),
                any(RskAddress.class)
        );

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, new BtcECKey().toAddress(btcParams));
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx.setWitness(0, txWit);

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
                rskTx,
                tx.bitcoinSerialize(),
                100,
                Hex.decode("ab"),
                PegTestUtils.createHash3(0),
                mock(Address.class),
                lbcAddress,
                mock(Address.class),
                false
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR_CODE, result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_surpasses_locking_cap_and_shouldTransfer_is_true()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Repository repository = mock(Repository.class);
        when(repository.getBalance(any())).thenReturn(co.rsk.core.Coin.valueOf(1));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(Coin.COIN).when(bridgeSupport).getLockingCap();
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
                btcParams,
                "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, getFastBridgeFederationAddress());
        byte[] pmtSerialized = Hex.decode("ab");
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            pmtSerialized,
            PegTestUtils.createHash3(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            true
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_REFUNDED_LP_ERROR_CODE, result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_surpasses_locking_cap_and_shouldTransfer_is_false()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);
        when(provider.isFastBridgeFederationDerivationHashUsed(any(), any())).thenReturn(false);

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Repository repository = mock(Repository.class);
        when(repository.getBalance(any())).thenReturn(co.rsk.core.Coin.valueOf(1));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(Coin.COIN).when(bridgeSupport).getLockingCap();
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, getFastBridgeFederationAddress());
        byte[] pmtSerialized = Hex.decode("ab");
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            pmtSerialized,
            PegTestUtils.createHash3(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            false
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_REFUNDED_USER_ERROR_CODE, result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_surpasses_locking_cap_and_tries_to_register_again()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.valueOf(1));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations
        );

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(
            Coin.COIN, // The first time we simulate a lower locking cap than the value to register, to force the reimburse
            Coin.FIFTY_COINS // The next time we simulate a hight locking cap, to verify the user can't attempt to register the already reimbursed tx
        ).when(bridgeSupport).getLockingCap();
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, getFastBridgeFederationAddress());
        byte[] pmtSerialized = Hex.decode("ab");
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        Keccak256 dHash = PegTestUtils.createHash3(0);

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            pmtSerialized,
            dHash,
            btcAddress,
            lbcAddress,
            btcAddress,
            false
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_REFUNDED_USER_ERROR_CODE, result);

        // Update repository
        bridgeSupport.save();

        result = bridgeSupport.registerFastBridgeBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            pmtSerialized,
            dHash,
            btcAddress,
            lbcAddress,
            btcAddress,
            false
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR_CODE, result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_OK()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        Repository repository = spy(createRepository());
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations
        );

        FederationSupport federationSupportMock = mock(FederationSupport.class);
        doReturn(provider.getNewFederationBtcUTXOs()).when(federationSupportMock).getActiveFederationBtcUTXOs();

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
            provider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            federationSupportMock,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        Coin valueToSend = Coin.COIN;
        BtcTransaction tx = createBtcTransactionWithOutputToAddress(valueToSend, getFastBridgeFederationAddress());
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        co.rsk.core.Coin preCallLbcAddressBalance = repository.getBalance(lbcAddress);

        long result = bridgeSupport.registerFastBridgeBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            true
        );

        Assert.assertEquals(valueToSend.getValue(), result);

        co.rsk.core.Coin postCallLbcAddressBalance = repository.getBalance(lbcAddress);
        Assert.assertEquals(
            preCallLbcAddressBalance.add(co.rsk.core.Coin.fromBitcoin(Coin.COIN)),
            postCallLbcAddressBalance
        );

        bridgeSupport.save();
        Assert.assertTrue(
            provider.isFastBridgeFederationDerivationHashUsed(
                tx.getHash(),
                bridgeSupport.getFastBridgeDerivationHash(PegTestUtils.createHash3(0), btcAddress, btcAddress, lbcAddress)
            )
        );
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());

        // Trying to register the same transaction again fails
        result = bridgeSupport.registerFastBridgeBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            true
        );

        Assert.assertEquals(BridgeSupport.FAST_BRIDGE_UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR_CODE, result);
    }

    @Test
    public void createFastBridgeFederationInformation_OK() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Federation fed = bridgeConstants.getGenesisFederation();
        FederationSupport federationSupport = mock(FederationSupport.class);
        when(federationSupport.getActiveFederation()).thenReturn(fed);

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            mock(Context.class),
            federationSupport,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            bridgeConstants.getGenesisFederation().getRedeemScript(),
            PegTestUtils.createHash(1)
        );

        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);
        Keccak256 derivationHash = PegTestUtils.createHash3(1);

        FastBridgeFederationInformation expectedFastBridgeFederationInformation =
            new FastBridgeFederationInformation(derivationHash,
                fed.getP2SHScript().getPubKeyHash(),
                fastBridgeP2SH.getPubKeyHash()
            );

        FastBridgeFederationInformation obtainedFastBridgeFedInfo =
            bridgeSupport.createFastBridgeFederationInformation(derivationHash);

        Assert.assertEquals(
            expectedFastBridgeFederationInformation.getFastBridgeFederationAddress(bridgeConstants.getBtcParams()),
            obtainedFastBridgeFedInfo.getFastBridgeFederationAddress(bridgeConstants.getBtcParams())
        );
    }

    @Test
    public void getUTXOsForAddress_OK() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BridgeEventLogger.class),
            null,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            bridgeConstants.getGenesisFederation().getRedeemScript(),
            Sha256Hash.of(new byte[1])
        );

        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);

        Address fastBridgeFedAddress =
            Address.fromP2SHScript(bridgeConstants.getBtcParams(), fastBridgeP2SH);

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, fastBridgeFedAddress);

        List<UTXO> utxoList = new ArrayList<>();
        UTXO utxo = new UTXO(tx.getHash(), 0, Coin.COIN, 0, false, fastBridgeP2SH);
        utxoList.add(utxo);

        Assert.assertEquals(utxoList, bridgeSupport.getUTXOsForAddress(tx, fastBridgeFedAddress));
    }

    @Test
    public void getUTXOsForAddress_no_utxos_for_address() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BridgeEventLogger.class),
            null,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        Address btcAddress = Address.fromBase58(
            btcParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        Assert.assertEquals(Collections.emptyList(), bridgeSupport.getUTXOsForAddress(tx, btcAddress));
    }

    @Test
    public void getFastBridgeWallet_OK() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        Federation fed = bridgeConstants.getGenesisFederation();
        Keccak256 derivationHash = PegTestUtils.createHash3(1);

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            fed.getRedeemScript(),
            Sha256Hash.wrap(derivationHash.getBytes())
        );

        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);

        FastBridgeFederationInformation fastBridgeFederationInformation =
            new FastBridgeFederationInformation(
                derivationHash,
                fed.getP2SHScript().getPubKeyHash(),
                fastBridgeP2SH.getPubKeyHash()
            );

        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(Coin.COIN,
            fastBridgeFederationInformation.getFastBridgeFederationAddress(
                bridgeConstants.getBtcParams()
            )
        );

        List<UTXO> utxoList = new ArrayList<>();
        UTXO utxo = new UTXO(tx.getHash(), 0, Coin.COIN, 0, false, fastBridgeP2SH);
        utxoList.add(utxo);

        Wallet obtainedWallet = bridgeSupport.getFastBridgeWallet(btcContext, utxoList, fastBridgeFederationInformation);
        Assert.assertEquals(Coin.COIN, obtainedWallet.getBalance());
    }

    @Test
    public void getFastBridgeDerivationHash_Ok() throws BridgeIllegalArgumentException {
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class));

        Address userRefundBtcAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "mgy8yiUZYB7o9vvCu2Yi8GB3Vr32MQsyQJ"
        );

        byte[] userRefundBtcAddressBytes = bridgeSupport.getBytesFromBtcAddress(userRefundBtcAddress);
        int refundVersion = BridgeUtils.extractAddressVersionFromBytes(userRefundBtcAddressBytes);
        byte[] refundHash160 = BridgeUtils.extractHash160FromBytes(userRefundBtcAddressBytes);

        Address lpBtcAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "mhoDGMzHHDq2ZD6cFrKV9USnMfpxEtLwGm"
        );

        byte[] lpBtcAddressBytes = bridgeSupport.getBytesFromBtcAddress(lpBtcAddress);
        int lpVersion = BridgeUtils.extractAddressVersionFromBytes(lpBtcAddressBytes);
        byte[] lpHash160 = BridgeUtils.extractHash160FromBytes(lpBtcAddressBytes);

        byte[] derivationArgumentsHash = ByteUtil.leftPadBytes(new byte[]{0x01}, 32);
        byte[] lbcAddress = ByteUtil.leftPadBytes(new byte[]{0x03}, 20);
        byte[] result = ByteUtil.merge(derivationArgumentsHash, userRefundBtcAddressBytes, lbcAddress, lpBtcAddressBytes);

        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFastBridgeDerivationHash(
                new Keccak256(derivationArgumentsHash),
                new Address(btcParams, refundVersion, refundHash160),
                new Address(btcParams, lpVersion, lpHash160),
                new RskAddress(lbcAddress)
        );

        Assert.assertArrayEquals(HashUtil.keccak256(result), fastBridgeDerivationHash.getBytes());
    }

    @Test
    public void getAmountSentToAddress_coin() {
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class));

        Coin valueToTransfer = Coin.COIN;
        Address receiver = bridgeConstants.getGenesisFederation().getAddress();

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(valueToTransfer, receiver);

        Assert.assertEquals(Coin.COIN, bridgeSupport.getAmountSentToAddress(btcTx, receiver));
    }

    @Test
    public void getAmountSentToAddress_no_output_for_address() {
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class));
        Address receiver = bridgeConstants.getGenesisFederation().getAddress();
        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());

        Assert.assertEquals(Coin.ZERO, bridgeSupport.getAmountSentToAddress(btcTx, receiver));
    }

    @Test
    public void getAmountSentToAddress_output_value_is_0() {
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class));

        Coin valueToTransfer = Coin.ZERO;
        Address receiver = bridgeConstants.getGenesisFederation().getAddress();

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(valueToTransfer, receiver);

        Assert.assertEquals(Coin.ZERO, bridgeSupport.getAmountSentToAddress(btcTx, receiver));
    }

    @Test
    public void saveFastBridgeDataInStorage_OK() throws IOException {
        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activationsAfterForks);
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, provider, activationsAfterForks);

        Sha256Hash btcTxHash = PegTestUtils.createHash(1);
        Keccak256 derivationHash = PegTestUtils.createHash3(1);

        byte[] fastBridgeScriptHash = new byte[]{0x1};
        FastBridgeFederationInformation fastBridgeFederationInformation = new FastBridgeFederationInformation(
                PegTestUtils.createHash3(2),
                new byte[]{0x1},
                fastBridgeScriptHash
        );

        List<UTXO> utxos = new ArrayList<>();
        Sha256Hash utxoHash = PegTestUtils.createHash(1);
        UTXO utxo = new UTXO(utxoHash, 0, Coin.COIN.multiply(2), 0, false, new Script(new byte[]{}));
        utxos.add(utxo);

        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        bridgeSupport.saveFastBridgeDataInStorage(btcTxHash, derivationHash, fastBridgeFederationInformation,  utxos);

        bridgeSupport.save();

        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        assertEquals(utxo, provider.getNewFederationBtcUTXOs().get(0));
        Assert.assertTrue(provider.isFastBridgeFederationDerivationHashUsed(btcTxHash, derivationHash));
        Optional<FastBridgeFederationInformation> optionalFastBridgeFederationInformation = provider.getFastBridgeFederationInformation(fastBridgeScriptHash);
        Assert.assertTrue(optionalFastBridgeFederationInformation.isPresent());
        FastBridgeFederationInformation obtainedFastBridgeFederationInformation = optionalFastBridgeFederationInformation.get();
        Assert.assertEquals(fastBridgeFederationInformation.getDerivationHash(), obtainedFastBridgeFederationInformation.getDerivationHash() );
        Assert.assertArrayEquals(fastBridgeFederationInformation.getFederationScriptHash(), obtainedFastBridgeFederationInformation.getFederationScriptHash() );
    }

    @Test
    public void getBytesFromBtcAddress() {
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, mock(BridgeStorageProvider.class));

        Address btcAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "mgy8yiUZYB7o9vvCu2Yi8GB3Vr32MQsyQJ"
        );

        byte[] expectedBytes = Hex.decode("6f0febdbf4739e9fe6724370a7e99cb25d7be5ca99");
        byte[] obtainedBytes = bridgeSupport.getBytesFromBtcAddress(btcAddress);

        Assert.assertArrayEquals(expectedBytes, obtainedBytes);
    }

    private Address getFastBridgeFederationAddress() {
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            bridgeConstants.getGenesisFederation().getRedeemScript(),
            PegTestUtils.createHash(1)
        );

        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);
        return Address.fromP2SHScript(bridgeConstants.getBtcParams(), fastBridgeP2SH);
    }

    private BtcTransaction createBtcTransactionWithOutputToAddress(Coin amount, Address btcAddress) {
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(amount, btcAddress);
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1),
            0, ScriptBuilder.createInputScript(null, srcKey));
        return tx;
    }

    private void assertRefundInProcessPegInVersion1(
        TxSenderAddressType lockSenderAddressType,
        Optional<Address> btcRefundAddress,
        List<ConsensusRule> consensusRules)
        throws IOException, RegisterBtcTransactionException, PeginInstructionsException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        for (ConsensusRule consensusRule : consensusRules) {
            when(activations.isActive(consensusRule)).thenReturn(true);
        }

        Repository repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Address btcSenderAddress = null;
        if (lockSenderAddressType != TxSenderAddressType.UNKNOWN) {
            btcSenderAddress = srcKey1.toAddress(btcParams);
        }

        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addOutput(Coin.COIN.multiply(10), bridgeConstants.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);

        when(provider.getLockingCap()).thenReturn(Coin.COIN.multiply(1));

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(lockSenderAddressType, btcSenderAddress, rskAddress);

        if (!btcRefundAddress.isPresent()  && btcSenderAddress != null) {
            btcRefundAddress = Optional.of(btcSenderAddress);
        }
        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(
            rskAddress,
            btcRefundAddress
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        // Act
        bridgeSupport.processPegIn(btcTx, mock(Transaction.class), 0, mock(Sha256Hash.class));

        // Assert
        if (lockSenderAddressType == TxSenderAddressType.UNKNOWN && !btcRefundAddress.isPresent()) {
            // Unknown sender and no refund address. Can't refund
            Assert.assertEquals(0, releaseTransactionSet.getEntries().size());
        } else {
            Assert.assertEquals(1, releaseTransactionSet.getEntries().size());

            // Check rejection tx input was created from btc tx and sent to the btc refund address indicated by the user
            boolean successfulRejection = false;
            for (ReleaseTransactionSet.Entry e : releaseTransactionSet.getEntries()) {
                BtcTransaction refundTx = e.getTransaction();
                if (refundTx.getInput(0).getOutpoint().getHash() == btcTx.getHash() &&
                    refundTx.getOutput(0).getScriptPubKey().getToAddress(btcParams).equals(btcRefundAddress.get())) {
                    successfulRejection = true;
                    break;
                }
            }

            Assert.assertTrue(successfulRejection);
        }
    }

    private void assertLockingCap(
        boolean shouldLock,
        boolean isLockingCapEnabled,
        Coin lockingCap,
        Coin amountSentToNewFed,
        Coin amountSentToOldFed,
        Coin amountInNewFed,
        Coin amountInOldFed) throws BlockStoreException, IOException, BridgeIllegalArgumentException {

        // Configure if locking cap should be evaluated
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(isLockingCapEnabled);

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        when(bridgeConstants.getLegacyMinimumPeginTxValueInSatoshis()).thenReturn(Coin.SATOSHI);
        when(bridgeConstants.getBtcParams()).thenReturn(BridgeRegTestConstants.getInstance().getBtcParams());
        when(bridgeConstants.getBtc2RskMinimumAcceptableConfirmations()).thenReturn(1);
        when(bridgeConstants.getGenesisFeePerKb()).thenReturn(BridgeRegTestConstants.getInstance().getGenesisFeePerKb());
        when(bridgeConstants.getMaxRbtc()).thenReturn(BridgeRegTestConstants.getInstance().getMaxRbtc());

        // Configure locking cap
        when(bridgeConstants.getInitialLockingCap()).thenReturn(lockingCap);

        Repository repository = createRepository();

        Federation oldFederation = this.getFederation(bridgeConstants);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations
        );
        // We need a random new fed
        provider.setNewFederation(this.getFederation(
            bridgeConstants,
            Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03"))
            )
        ));
        // Use genesis fed as old
        provider.setOldFederation(oldFederation);

        Coin currentFunds = Coin.ZERO;

        // Configure existing utxos in both federations
        if (amountInOldFed != null) {
            UTXO utxo = new UTXO(
                Sha256Hash.wrap(HashUtil.randomHash()),
                0,
                amountInOldFed,
                1,
                false,
                new Script(new byte[]{})
            );
            provider.getOldFederationBtcUTXOs().add(utxo);
            currentFunds = currentFunds.add(amountInOldFed);
        }
        if (amountInNewFed != null) {
            UTXO utxo = new UTXO(
                Sha256Hash.wrap(HashUtil.randomHash()),
                0,
                amountInNewFed,
                1,
                false,
                new Script(new byte[]{})
            );
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
        tx.addInput(
            PegTestUtils.createHash(1),
            0,
            ScriptBuilder.createInputScript(null, srcKey)
        );

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<BtcTransaction>()
        );

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

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

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations
        );

        // Simulate blockchain
        int height = 1;
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        Transaction rskTx = mock(Transaction.class);
        Keccak256 hash = new Keccak256(HashUtil.keccak256(new byte[]{}));
        when(rskTx.getHash()).thenReturn(hash);

        // Try to register tx
        bridgeSupport.registerBtcTransaction(rskTx, tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

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

    private Federation getFederation(BridgeConstants bridgeConstants) {
        List<BtcECKey> defaultFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        return getFederation(bridgeConstants, defaultFederationKeys);
    }

    private Federation getFederation(BridgeConstants bridgeConstants, List<BtcECKey> federationKeys) {
        federationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        return new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federationKeys),
                Instant.ofEpochMilli(1000L),
                0L,
                bridgeConstants.getBtcParams()
        );
    }

    /**
     * Helper method to test addSignature() with a valid federatorPublicKey parameter and both valid/invalid signatures
     *
     * @param privateKeysToSignWith keys used to sign the tx. Federator key when we want to produce a valid signature, a random key when we want to produce an invalid signature
     * @param numberOfInputsToSign  There is just 1 input. 1 when testing the happy case, other values to test attacks/bugs.
     * @param signatureCanonical    Signature should be canonical. true when testing the happy case, false to test attacks/bugs.
     * @param signTwice             Sign again with the same key
     * @param expectedResult        "InvalidParameters", "PartiallySigned" or "FullySigned"
     */
    private void addSignatureFromValidFederator(List<BtcECKey> privateKeysToSignWith, int numberOfInputsToSign, boolean signatureCanonical, boolean signTwice, String expectedResult) throws Exception {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = createRepository();

        final Keccak256 keccak256 = PegTestUtils.createHash3();

        Repository track = repository.startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);

        BtcTransaction t = new BtcTransaction(btcParams);
        TransactionOutput output = new TransactionOutput(btcParams, t, Coin.COIN, new BtcECKey().toAddress(btcParams));
        t.addOutput(output);
        t.addInput(prevOut).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        provider.getRskTxsWaitingForSignatures().put(keccak256, t);
        provider.save();
        track.commit();

        track = repository.startTracking();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(bridgeConstants, activations, logs);
        BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                new BridgeStorageProvider(
                        track,
                        contractAddress,
                        bridgeConstants,
                        activationsAfterForks
                ),
                track,
                eventLogger,
                mock(Block.class),
                null
        );

        Script inputScript = t.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        Sha256Hash sighash = t.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        BtcECKey.ECDSASignature sig = privateKeysToSignWith.get(0).sign(sighash);
        if (!signatureCanonical) {
            sig = new BtcECKey.ECDSASignature(sig.r, BtcECKey.CURVE.getN().subtract(sig.s));
        }
        byte[] derEncodedSig = sig.encodeToDER();

        List derEncodedSigs = new ArrayList();
        for (int i = 0; i < numberOfInputsToSign; i++) {
            derEncodedSigs.add(derEncodedSig);
        }
        bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeysToSignWith.get(0)), derEncodedSigs, keccak256.getBytes());
        if (signTwice) {
            // Create another valid signature with the same private key
            ECDSASigner signer = new ECDSASigner();
            X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
            ECDomainParameters CURVE = new ECDomainParameters(CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());
            ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(privateKeysToSignWith.get(0).getPrivKey(), CURVE);
            signer.init(true, privKey);
            BigInteger[] components = signer.generateSignature(sighash.getBytes());
            BtcECKey.ECDSASignature sig2 = new BtcECKey.ECDSASignature(components[0], components[1]).toCanonicalised();
            bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeysToSignWith.get(0)), Lists.newArrayList(sig2.encodeToDER()), keccak256.getBytes());
        }
        if (privateKeysToSignWith.size() > 1) {
            BtcECKey.ECDSASignature sig2 = privateKeysToSignWith.get(1).sign(sighash);
            byte[] derEncodedSig2 = sig2.encodeToDER();
            List derEncodedSigs2 = new ArrayList();
            for (int i = 0; i < numberOfInputsToSign; i++) {
                derEncodedSigs2.add(derEncodedSig2);
            }
            bridgeSupport.addSignature(findPublicKeySignedBy(federation.getBtcPublicKeys(), privateKeysToSignWith.get(1)), derEncodedSigs2, keccak256.getBytes());
        }
        bridgeSupport.save();
        track.commit();

        provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationsBeforeForks);

        if ("FullySigned".equals(expectedResult)) {
            Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
            Assert.assertThat(logs, is(not(empty())));
            Assert.assertThat(logs, hasSize(3));
            LogInfo releaseTxEvent = logs.get(2);
            Assert.assertThat(releaseTxEvent.getTopics(), hasSize(1));
            Assert.assertThat(releaseTxEvent.getTopics(), hasItem(Bridge.RELEASE_BTC_TOPIC));
            BtcTransaction releaseTx = new BtcTransaction(btcParams, ((RLPList) RLP.decode2(releaseTxEvent.getData()).get(0)).get(1).getRLPData());
            Script retrievedScriptSig = releaseTx.getInput(0).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(1).data.length > 0);
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(2).data.length > 0);
        } else {
            Script retrievedScriptSig = provider.getRskTxsWaitingForSignatures().get(keccak256).getInput(0).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            boolean expectSignatureToBePersisted = false; // for "InvalidParameters"
            if ("PartiallySigned".equals(expectedResult)) {
                expectSignatureToBePersisted = true;
            }
            Assert.assertEquals(expectSignatureToBePersisted, retrievedScriptSig.getChunks().get(1).data.length > 0);
            Assert.assertEquals(false, retrievedScriptSig.getChunks().get(2).data.length > 0);
        }
    }

    private BtcECKey findPublicKeySignedBy(List<BtcECKey> pubs, BtcECKey pk) {
        for (BtcECKey pub : pubs) {
            if (Arrays.equals(pk.getPubKey(), pub.getPubKey())) {
                return pub;
            }
        }
        return pk;
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider) {
        return getBridgeSupport(constants, provider, null, mock(BridgeEventLogger.class), null, null, null);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, ActivationConfig.ForBlock activations) {
        return getBridgeSupport(constants, provider, null, mock(BridgeEventLogger.class), null, null, activations);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BtcLockSenderProvider btcLockSenderProvider, PeginInstructionsProvider peginInstructionsProvider,
                                           Block executionBlock, BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(constants,
                provider,
                track,
                btcLockSenderProvider,
                peginInstructionsProvider,
                executionBlock,
                blockStoreFactory,
                mock(ActivationConfig.ForBlock.class)
        );
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
                                           BtcLockSenderProvider btcLockSenderProvider, PeginInstructionsProvider peginInstructionsProvider,
                                           Block executionBlock, BtcBlockStoreWithCache.Factory blockStoreFactory,
                                           ActivationConfig.ForBlock activations) {

        if (btcLockSenderProvider == null) {
            btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        }
        if (peginInstructionsProvider == null) {
            peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        }
        if (blockStoreFactory == null) {
            blockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
        }
        return new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                btcLockSenderProvider,
                peginInstructionsProvider,
                track,
                executionBlock,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, executionBlock),
                blockStoreFactory,
                activations
        );
    }

    private BridgeSupport getBridgeSupport(BridgeStorageProvider provider, Repository track, BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(
            bridgeConstants,
            provider,
            track,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            blockStoreFactory
        );
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BridgeEventLogger eventLogger, Block executionBlock,
                                           BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(constants,
                provider,
                track,
                eventLogger,
                new BtcLockSenderProvider(),
                executionBlock,
                blockStoreFactory,
                mock(ActivationConfig.ForBlock.class)
        );
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BridgeEventLogger eventLogger, BtcLockSenderProvider btcLockSenderProvider,
                                           Block executionBlock, BtcBlockStoreWithCache.Factory blockStoreFactory,
                                           ActivationConfig.ForBlock activations) {
        if (eventLogger == null) {
            eventLogger = mock(BridgeEventLogger.class);
        }
        if (btcLockSenderProvider == null) {
            btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        }
        if (executionBlock == null) {
            executionBlock = mock(Block.class);
        }
        if (blockStoreFactory == null) {
            blockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
        }
        if (activations == null) {
            activations = mock(ActivationConfig.ForBlock.class);
        }
        return new BridgeSupport(
                constants,
                provider,
                eventLogger,
                btcLockSenderProvider,
                new PeginInstructionsProvider(),
                track,
                executionBlock,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, executionBlock),
                blockStoreFactory,
                activations
        );
    }

    private BtcLockSenderProvider getBtcLockSenderProvider(TxSenderAddressType txSenderAddressType, Address btcAddress, RskAddress rskAddress) {
        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getTxSenderAddressType()).thenReturn(txSenderAddressType);
        when(btcLockSender.getBTCAddress()).thenReturn(btcAddress);
        when(btcLockSender.getRskAddress()).thenReturn(rskAddress);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        return btcLockSenderProvider;
    }

    private PeginInstructionsProvider getPeginInstructionsProviderForVersion1(RskAddress rskDestinationAddress, Optional<Address> btcRefundAddress)
        throws PeginInstructionsException {
        PeginInstructionsVersion1 peginInstructions = mock(PeginInstructionsVersion1.class);
        when(peginInstructions.getProtocolVersion()).thenReturn(1);
        when(peginInstructions.getRskDestinationAddress()).thenReturn(rskDestinationAddress);
        when(peginInstructions.getBtcRefundAddress()).thenReturn(btcRefundAddress);

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.of(peginInstructions));

        return peginInstructionsProvider;
    }

    private UTXO createUTXO(Coin value, Address address) {
        return new UTXO(
                PegTestUtils.createHash(),
                1,
                value,
                0,
                false,
                ScriptBuilder.createOutputScript(address));
    }

    private Transaction getMockedRskTxWithHash(String s) {
        byte[] hash = Keccak256Helper.keccak256(s);
        return new SimpleRskTransaction(hash);
    }

    private BridgeSupport getBridgeSupportConfiguredToTestReceiveHeader(
            BtcBlock btcBlock,
            BtcBlockStoreWithCache btcBlockStore,
            BridgeStorageProvider provider,
            StoredBlock storedBlock,
            ActivationConfig.ForBlock activation
    ) throws BlockStoreException {
        return getBridgeSupportConfiguredToTestReceiveHeader(
                btcBlock,
                btcBlockStore,
                provider,
                storedBlock,
                mock(Block.class),
                activation
        );
    }

    private BridgeSupport getBridgeSupportConfiguredToTestReceiveHeader(
            BtcBlock btcBlock,
            BtcBlockStoreWithCache btcBlockStore,
            BridgeStorageProvider provider,
            StoredBlock storedBlock,
            Block rskBlock,
            ActivationConfig.ForBlock activation
    ) throws BlockStoreException {

        doReturn(10).when(storedBlock).getHeight();

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        doReturn(PegTestUtils.createHash(1)).when(btcBlock2).getHash();
        doReturn(btcBlock2).when(storedBlock).getHeader();

        doReturn(storedBlock).when(btcBlockStore).getChainHead();

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        when(btcBlock.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        when(rskBlock.getTimestamp()).thenReturn(1611169584L);

        return getBridgeSupport(
                bridgeConstants,
                provider,
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                rskBlock,
                mockFactory,
                activation
        );
    }

}
