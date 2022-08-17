package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.BridgeConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.apache.commons.lang3.RandomUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeSupportPegoutCreationTest extends BridgeSupportTestBase {
    @Before
    public void setUpOnEachTest() {
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeSupportBuilder = new BridgeSupportBuilder();
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_before_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(false);

        Sha256Hash btcTxHash = PegTestUtils.createHash(RandomUtils.nextInt());
        Keccak256 rskTxHash = PegTestUtils.createHash3(RandomUtils.nextInt());

        BridgeConstants bridgeConstants = this.bridgeConstantsRegtest;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getPegoutCreationRskTxHashByBtcTxHash(any())).thenReturn(Optional.of(rskTxHash));
        Repository repository = createRepository();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        Block executionBlock = Mockito.mock(Block.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Optional<Keccak256> pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);

        assertFalse(pegoutRskTxHash.isPresent());
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_ok_after_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(true);

        Sha256Hash btcTxHash = PegTestUtils.createHash(RandomUtils.nextInt());
        Keccak256 rskTxHash = PegTestUtils.createHash3(RandomUtils.nextInt());

        BridgeConstants bridgeConstants = this.bridgeConstantsRegtest;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash)).thenReturn(Optional.of(rskTxHash));
        Repository repository = createRepository();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        Block executionBlock = Mockito.mock(Block.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Optional<Keccak256> pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);

        assertTrue(pegoutRskTxHash.isPresent());
        assertEquals(rskTxHash, pegoutRskTxHash.get());
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_not_found_after_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(true);

        Sha256Hash btcTxHash = PegTestUtils.createHash(RandomUtils.nextInt());

        BridgeConstants bridgeConstants = this.bridgeConstantsRegtest;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getPegoutCreationRskTxHashByBtcTxHash(any())).thenReturn(Optional.empty());
        Repository repository = createRepository();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        Block executionBlock = Mockito.mock(Block.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Optional<Keccak256> pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);

        assertFalse(pegoutRskTxHash.isPresent());
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_by_zero_hash_after_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(true);

        Sha256Hash btcTxHash = Sha256Hash.ZERO_HASH;
        Keccak256 rskTxHash = PegTestUtils.createHash3(RandomUtils.nextInt());

        BridgeConstants bridgeConstants = this.bridgeConstantsRegtest;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash)).thenReturn(Optional.of(rskTxHash));
        Repository repository = createRepository();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        Block executionBlock = Mockito.mock(Block.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Optional<Keccak256> pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);

        assertTrue(pegoutRskTxHash.isPresent());
        assertEquals(rskTxHash, pegoutRskTxHash.get());
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_btc_tx_hash_is_null_after_RSKIP298_activation() {
        when(activations.isActive(ConsensusRule.RSKIP298)).thenReturn(true);

        BridgeConstants bridgeConstants = this.bridgeConstantsRegtest;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getPegoutCreationRskTxHashByBtcTxHash(isNull())).thenReturn(Optional.empty());

        Repository repository = createRepository();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        Block executionBlock = Mockito.mock(Block.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Optional<Keccak256> pegoutRskTxHash = bridgeSupport.getPegoutCreationRskTxHashByBtcTxHash(null);

        assertFalse(pegoutRskTxHash.isPresent());
    }
}
