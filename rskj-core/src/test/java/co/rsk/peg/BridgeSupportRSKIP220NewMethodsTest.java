package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BridgeSupportRSKIP220NewMethodsTest {
    private static final Random random = new Random(BridgeSupportRSKIP220NewMethodsTest.class.hashCode());

    private Sha256Hash hash;
    private byte[] header;
    private BtcBlockStoreWithCache btcBlockStore;
    private StoredBlock storedBlock;
    private BtcBlock btcBlock;
    private BridgeSupport bridgeSupport;

    @BeforeEach
    void setUpOnEachTest() throws BlockStoreException {
        BridgeConstants bridgeConstants = new BridgeRegTestConstants();
        ActivationConfig.ForBlock activationsAfterForks = ActivationConfigsForTest.all().forBlock(0);
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        byte[] hashBytes = new byte[32];
        random.nextBytes(hashBytes);
        hash = Sha256Hash.wrap(hashBytes);

        header = new byte[80];
        random.nextBytes(header);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
        btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        storedBlock = mock(StoredBlock.class);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        when(btcBlockStore.get(hash)).thenReturn(storedBlock);
        btcBlock = mock(BtcBlock.class);
        when(btcBlock.unsafeBitcoinSerialize()).thenReturn(header);
        when(storedBlock.getHeader()).thenReturn(btcBlock);
        when(btcBlock.getHash()).thenReturn(hash);
        when(storedBlock.getHeight()).thenReturn(30);

        int height = 30;

        when(btcBlockStore.getStoredBlockAtMainChainHeight(height)).thenReturn(new StoredBlock(btcBlock, BigInteger.ONE, height));

        bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activationsAfterForks)
            .withSignatureCache(signatureCache)
            .build();
    }

    @Test
    void getBtcBlockchainBestBlockHeader() throws BlockStoreException, IOException {
        byte[] result = bridgeSupport.getBtcBlockchainBestBlockHeader();

        assertArrayEquals(header, result);
    }

    @Test
    void getBtcBlockchainBlockHeaderByHash() throws BlockStoreException, IOException {
        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHash(hash);

        assertArrayEquals(header, result);
    }

    @Test
    void getBtcBlockchainBlockHeaderByUnknownHash() throws BlockStoreException, IOException {
        byte[] unknownHashBytes = new byte[32];
        random.nextBytes(unknownHashBytes);
        Sha256Hash unknownHash = Sha256Hash.wrap(unknownHashBytes);

        when(btcBlockStore.get(unknownHash)).thenReturn(null);

        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHash(unknownHash);

        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void getBtcBlockchainBlockHeaderByHeight() throws BlockStoreException, IOException {
        when(btcBlockStore.getStoredBlockAtMainChainHeight(20)).thenReturn(storedBlock);

        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHeight(20);

        assertArrayEquals(header, result);
    }

    @Test
    void getBtcBlockchainParentBlockHeaderByHash() throws BlockStoreException, IOException {
        byte[] parentHashBytes = new byte[32];
        random.nextBytes(parentHashBytes);
        Sha256Hash parentHash = Sha256Hash.wrap(parentHashBytes);

        StoredBlock parentStoredBlock = mock(StoredBlock.class);
        when(btcBlockStore.get(parentHash)).thenReturn(parentStoredBlock);
        when(btcBlock.getPrevBlockHash()).thenReturn(parentHash);
        BtcBlock parentBtcBlock = mock(BtcBlock.class);
        when(parentBtcBlock.unsafeBitcoinSerialize()).thenReturn(header);
        when(parentStoredBlock.getHeader()).thenReturn(parentBtcBlock);

        byte[] result = bridgeSupport.getBtcBlockchainParentBlockHeaderByHash(hash);

        assertArrayEquals(header, result);
    }

    @Test
    void getBtcBlockchainParentBlockHeaderByUnknownHash() throws BlockStoreException, IOException {
        when(btcBlockStore.get(hash)).thenReturn(null);

        byte[] result = bridgeSupport.getBtcBlockchainParentBlockHeaderByHash(hash);

        assertNotNull(result);
        assertEquals(0, result.length);
    }
}
