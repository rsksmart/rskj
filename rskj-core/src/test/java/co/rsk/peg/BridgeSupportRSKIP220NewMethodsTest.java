package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BridgeSupportRSKIP220NewMethodsTest {
    private static Random random = new Random();

    private BridgeConstants bridgeConstants;
    private NetworkParameters btcParams;
    private ActivationConfig.ForBlock activationsBeforeForks;
    private ActivationConfig.ForBlock activationsAfterForks;
    private byte[] hashBytes;
    private Sha256Hash hash;
    private byte[] header;
    private BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BtcBlockStoreWithCache btcBlockStore;
    private StoredBlock storedBlock;
    private BtcBlock btcBlock;
    private BridgeSupport bridgeSupport;
    private SignatureCache signatureCache;

    @BeforeEach
    void setUpOnEachTest() throws BlockStoreException {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        btcParams = bridgeConstants.getBtcParams();
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
        activationsAfterForks = ActivationConfigsForTest.all().forBlock(0);
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        hashBytes = new byte[32];
        random.nextBytes(hashBytes);
        hash = Sha256Hash.wrap(hashBytes);

        header = new byte[80];
        random.nextBytes(header);

        btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
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

        bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activationsAfterForks)
            .withSignatureCache(signatureCache)
            .build();
    }

    @Test
    void getBtcBlockchainBestBlockHeader() throws BlockStoreException, IOException {
        byte[] result = bridgeSupport.getBtcBlockchainBestBlockHeader();

        Assertions.assertArrayEquals(header, result);
    }

    @Test
    void getBtcBlockchainBlockHeaderByHash() throws BlockStoreException, IOException {
        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHash(hash);

        Assertions.assertArrayEquals(header, result);
    }

    @Test
    void getBtcBlockchainBlockHeaderByUnknownHash() throws BlockStoreException, IOException {
        byte[] unknownHashBytes = new byte[32];
        random.nextBytes(unknownHashBytes);
        Sha256Hash unknownHash = Sha256Hash.wrap(unknownHashBytes);

        when(btcBlockStore.get(unknownHash)).thenReturn(null);

        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHash(unknownHash);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.length);
    }

    @Test
    void getBtcBlockchainBlockHeaderByHeight() throws BlockStoreException, IOException {
        when(btcBlockStore.getStoredBlockAtMainChainHeight(20)).thenReturn(storedBlock);

        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHeight(20);

        Assertions.assertArrayEquals(header, result);
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

        Assertions.assertArrayEquals(header, result);
    }

    @Test
    void getBtcBlockchainParentBlockHeaderByUnknownHash() throws BlockStoreException, IOException {
        when(btcBlockStore.get(hash)).thenReturn(null);

        byte[] result = bridgeSupport.getBtcBlockchainParentBlockHeaderByHash(hash);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.length);
    }
}
