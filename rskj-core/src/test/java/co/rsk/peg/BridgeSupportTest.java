package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BridgeSupportTest {
    private static final co.rsk.core.Coin LIMIT_MONETARY_BASE = new co.rsk.core.Coin(new BigInteger("21000000000000000000000000"));

    @Test
    public void activations_is_set() {
        Block block = mock(Block.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP124)).thenReturn(true);

        BridgeSupport bridgeSupport = new BridgeSupport(
                mock(BridgeConstants.class),
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        Assert.assertTrue(bridgeSupport.getActivations().isActive(ConsensusRule.RSKIP124));
    }

    @Test(expected = NullPointerException.class)
    public void voteFeePerKbChange_nullFeeThrows() {
        Block block = mock(Block.class);
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

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        bridgeSupport.voteFeePerKbChange(tx, null);
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_unauthorized() {
        Block block = mock(Block.class);
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

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(-10));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_negativeFeePerKb() {
        Block block = mock(Block.class);
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

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.NEGATIVE_SATOSHI), is(-1));
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.ZERO), is(-1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_excessiveFeePerKb() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        Block block = mock(Block.class);
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

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB)), is(1));
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB + 1)), is(-2));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVote() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        Block block = mock(Block.class);
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

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVoteWithFeeChange() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        Block block = mock(Block.class);
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

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

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

        BridgeSupport bridgeSupport = getBridgeSupport(
                constants, mock(BridgeStorageProvider.class)
        );

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

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider);

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

        BridgeSupport bridgeSupport = getBridgeSupport(constants, provider);

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

        BridgeSupport bridgeSupport = getBridgeSupport(
                constants, provider
        );

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
    public void registerBtcTransaction_aboveLockingCap_newFed_beforeRSKIP134Activation() throws IOException, BlockStoreException {
        assertLockingCap(false, true, true);
    }

    @Test
    public void registerBtcTransaction_aboveLockingCap_oldFed_beforeRSKIP134Activation() throws IOException, BlockStoreException {
        assertLockingCap(false, false, true);
    }

    @Test
    public void registerBtcTransaction_aboveLockingCap_newFed_afterRSKIP134Activation() throws IOException, BlockStoreException {
        assertLockingCap(true, true, true);
    }

    @Test
    public void registerBtcTransaction_aboveLockingCap_oldFed_afterRSKIP134Activation() throws IOException, BlockStoreException {
        assertLockingCap(true, false, true);
    }

    @Test
    public void registerBtcTransaction_belowLockingCap_newFed_afterRSKIP134Activation() throws IOException, BlockStoreException {
        assertLockingCap(true, true, false);
    }

    @Test
    public void registerBtcTransaction_belowLockingCap_oldFed_afterRSKIP134Activation() throws IOException, BlockStoreException {
        assertLockingCap(true, false, false);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider) {
        return getBridgeSupport(constants, provider, null, null, null, null);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BridgeEventLogger eventLogger, Block executionBlock,
                                           BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return getBridgeSupport(
                constants, provider, track, eventLogger, executionBlock,
                blockStoreFactory, mock(ActivationConfig.ForBlock.class)
        );
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
                                           BridgeEventLogger eventLogger, Block executionBlock,
                                           BtcBlockStoreWithCache.Factory blockStoreFactory,
                                           ActivationConfig.ForBlock activations) {
        if (eventLogger == null) {
            eventLogger = mock(BridgeEventLogger.class);
        }
        if (blockStoreFactory == null) {
            blockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
        }
        return new BridgeSupport(
                constants, provider, eventLogger, track, executionBlock,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, executionBlock),
                blockStoreFactory, activations
        );
    }

    private Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    private void assertLockingCap(boolean isLockingCapEnabled, boolean useNewFederation, boolean sendsAboveLockingCap) throws BlockStoreException, IOException {

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(isLockingCapEnabled);

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        when(bridgeConstants.getMinimumLockTxValue()).thenReturn(Coin.SATOSHI);
        when(bridgeConstants.getBtcParams()).thenReturn(BridgeRegTestConstants.getInstance().getBtcParams());
        when(bridgeConstants.getBtc2RskMinimumAcceptableConfirmations()).thenReturn(1);
        when(bridgeConstants.getGenesisFeePerKb()).thenReturn(BridgeRegTestConstants.getInstance().getGenesisFeePerKb());
        // Force the initial locking cap to 1 BTC
        when(bridgeConstants.getInitialLockingCap()).thenReturn(Coin.COIN);

        Repository repository = createRepository();
        // Fund bridge
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Repository track = repository.startTracking();

        Federation federation = this.getFederation(bridgeConstants, null);

        // Send 50BTC or 0.0001BTC depending on the configuration
        Coin lockValue = sendsAboveLockingCap ? Coin.FIFTY_COINS : Coin.MILLICOIN;

        // Create transaction
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(lockValue, federation.getAddress());
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

        BridgeStorageProvider provider =
                new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations);
        if (useNewFederation) {
            provider.setNewFederation(federation);
        } else {
            // We need a random new fed
            provider.setNewFederation(this.getFederation(bridgeConstants,
                    Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(Hex.decode("fb01")),
                        BtcECKey.fromPrivate(Hex.decode("fb02")),
                        BtcECKey.fromPrivate(Hex.decode("fb03")),
                    })
            ));
            provider.setOldFederation(federation);
        }

        // Get the tx sender public key
        byte[] data = tx.getInput(0).getScriptSig().getChunks().get(1).data;
        BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        Address address = senderBtcKey.toAddress(bridgeConstants.getBtcParams());
        whitelist.put(address, new OneOffWhiteListEntry(address, lockValue));
        // The address is whitelisted
        Assert.assertThat(whitelist.isWhitelisted(address), is(true));

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, provider, track, null, executionBlock, mockFactory, activations);

        // Simulate blockchain
        int height = 1;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        Transaction rskTx = mock(Transaction.class);
        Keccak256 hash = new Keccak256(HashUtil.keccak256(new byte[] {}));
        when(rskTx.getHash()).thenReturn(hash);

        // Try to register tx
        bridgeSupport.registerBtcTransaction(rskTx, tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        track.commit();

        // If the address is no longer whitelisted, it means it was consumed, whether the lock was rejected by lockingCap or not
        Assert.assertThat(whitelist.isWhitelisted(address), is(false));

        boolean shouldHaveLocked = !isLockingCapEnabled || isLockingCapEnabled && !sendsAboveLockingCap;

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(shouldHaveLocked ? lockValue : Coin.ZERO);
        RskAddress srcKeyRskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey.getPrivKey()).getAddress());

        // Verify amount was locked
        Assert.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(srcKeyRskAddress));
        Assert.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        if (!shouldHaveLocked) {
            // Release tx should have been created directly to the signatures stack
            BtcTransaction releaseTx = provider.getRskTxsWaitingForSignatures().get(hash);
            Assert.assertNotNull(releaseTx);
            // returns the funds to the sender
            Assert.assertEquals(1, releaseTx.getOutputs().size());
            Assert.assertEquals(address, releaseTx.getOutputs().get(0).getAddressFromP2PKHScript(bridgeConstants.getBtcParams()));
            Assert.assertEquals(lockValue, releaseTx.getOutputs().get(0).getValue().add(releaseTx.getFee()));
            // Uses the same UTXO
            Assert.assertEquals(1, releaseTx.getInputs().size());
            Assert.assertEquals(tx.getHash(), releaseTx.getInputs().get(0).getOutpoint().getHash());
            Assert.assertEquals(0, releaseTx.getInputs().get(0).getOutpoint().getIndex());
        }
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
}
