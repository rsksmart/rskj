package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

public class BridgeSupportNewMethodsTest {
    private static Random random = new Random();

    private BridgeConstants bridgeConstants;
    private NetworkParameters btcParams;
    private ActivationConfig.ForBlock activationsBeforeForks;
    private ActivationConfig.ForBlock activationsAfterForks;

    @Before
    public void setUpOnEachTest() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        btcParams = bridgeConstants.getBtcParams();
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
        activationsAfterForks = ActivationConfigsForTest.all().forBlock(0);
    }

    @Test
    public void getBtcBlockchainBestBlockHeader() throws BlockStoreException, IOException {
        byte[] hashBytes = new byte[32];
        random.nextBytes(hashBytes);
        Sha256Hash hash = Sha256Hash.wrap(hashBytes);

        byte[] header = new byte[80];
        random.nextBytes(header);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.unsafeBitcoinSerialize()).thenReturn(header);
        when(storedBlock.getHeader()).thenReturn(btcBlock);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactor = mock(BtcBlockStoreWithCache.Factory.class);
        when(btcBlockStoreFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        when(btcBlock.getHash()).thenReturn(hash);
        Assert.assertArrayEquals(header, btcBlock.unsafeBitcoinSerialize());
        Assert.assertArrayEquals(header, storedBlock.getHeader().unsafeBitcoinSerialize());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, btcBlockStoreFactory, activationsAfterForks);

        byte[] result = bridgeSupport.getBtcBlockchainBestBlockHeader();

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockchainBlockHeaderByHeight() throws BlockStoreException, IOException {
        byte[] hashBytes = new byte[32];
        random.nextBytes(hashBytes);
        Sha256Hash hash = Sha256Hash.wrap(hashBytes);

        byte[] header = new byte[80];
        random.nextBytes(header);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        when(btcBlockStore.getStoredBlockAtMainChainDepth(10)).thenReturn(storedBlock);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.unsafeBitcoinSerialize()).thenReturn(header);
        when(storedBlock.getHeader()).thenReturn(btcBlock);
        when(storedBlock.getHeight()).thenReturn(30);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactor = mock(BtcBlockStoreWithCache.Factory.class);
        when(btcBlockStoreFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        when(btcBlock.getHash()).thenReturn(hash);
        Assert.assertArrayEquals(header, btcBlock.unsafeBitcoinSerialize());
        Assert.assertArrayEquals(header, storedBlock.getHeader().unsafeBitcoinSerialize());

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, btcBlockStoreFactory, activationsAfterForks);

        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHeight(20);

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockHeaderByHash() throws BlockStoreException, IOException {
        byte[] hashBytes = new byte[32];
        random.nextBytes(hashBytes);
        Sha256Hash hash = Sha256Hash.wrap(hashBytes);

        byte[] header = new byte[80];
        random.nextBytes(header);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(btcBlockStore.get(hash)).thenReturn(storedBlock);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        when(btcBlockStore.getStoredBlockAtMainChainDepth(10)).thenReturn(storedBlock);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.unsafeBitcoinSerialize()).thenReturn(header);
        when(storedBlock.getHeader()).thenReturn(btcBlock);
        when(storedBlock.getHeight()).thenReturn(30);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactor = mock(BtcBlockStoreWithCache.Factory.class);
        when(btcBlockStoreFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        when(btcBlock.getHash()).thenReturn(hash);
        Assert.assertArrayEquals(header, btcBlock.unsafeBitcoinSerialize());
        Assert.assertArrayEquals(header, storedBlock.getHeader().unsafeBitcoinSerialize());

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, btcBlockStoreFactory, activationsAfterForks);

        byte[] result = bridgeSupport.getBtcBlockHeaderByHash(hash);

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockHeaderByUnknownHash() throws BlockStoreException, IOException {
        byte[] hashBytes = new byte[32];
        random.nextBytes(hashBytes);
        Sha256Hash hash = Sha256Hash.wrap(hashBytes);

        byte[] header = new byte[80];
        random.nextBytes(header);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(btcBlockStore.get(hash)).thenReturn(null);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        when(btcBlockStore.getStoredBlockAtMainChainDepth(10)).thenReturn(storedBlock);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.unsafeBitcoinSerialize()).thenReturn(header);
        when(storedBlock.getHeader()).thenReturn(btcBlock);
        when(storedBlock.getHeight()).thenReturn(30);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactor = mock(BtcBlockStoreWithCache.Factory.class);
        when(btcBlockStoreFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        when(btcBlock.getHash()).thenReturn(hash);
        Assert.assertArrayEquals(header, btcBlock.unsafeBitcoinSerialize());
        Assert.assertArrayEquals(header, storedBlock.getHeader().unsafeBitcoinSerialize());

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, btcBlockStoreFactory, activationsAfterForks);

        byte[] result = bridgeSupport.getBtcBlockHeaderByHash(hash);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }

    @Test
    public void getBtcBlockchainBlockHeaderByHeightTooHight() throws BlockStoreException, IOException {
        byte[] hashBytes = new byte[32];
        random.nextBytes(hashBytes);
        Sha256Hash hash = Sha256Hash.wrap(hashBytes);

        byte[] header = new byte[80];
        random.nextBytes(header);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
        when(btcBlockStore.getStoredBlockAtMainChainDepth(10)).thenReturn(storedBlock);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.unsafeBitcoinSerialize()).thenReturn(header);
        when(storedBlock.getHeader()).thenReturn(btcBlock);
        when(storedBlock.getHeight()).thenReturn(30);

        BtcBlockStoreWithCache.Factory btcBlockStoreFactor = mock(BtcBlockStoreWithCache.Factory.class);
        when(btcBlockStoreFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        when(btcBlock.getHash()).thenReturn(hash);
        Assert.assertArrayEquals(header, btcBlock.unsafeBitcoinSerialize());
        Assert.assertArrayEquals(header, storedBlock.getHeader().unsafeBitcoinSerialize());

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstants, btcBlockStoreFactory, activationsAfterForks);

        try {
            bridgeSupport.getBtcBlockchainBlockHeaderByHeight(40);
            Assert.fail();
        }
        catch (IndexOutOfBoundsException ex) {
            Assert.assertEquals("Height must be between 0 and 30", ex.getMessage());
        }
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants,
           BtcBlockStoreWithCache.Factory blockStoreFactory,
           ActivationConfig.ForBlock activations) {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Repository track = mock(Repository.class);
        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        Block executionBlock = mock(Block.class);

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
        //when(btcBlockStore.getChainHead()).thenReturn(currentStored);
        when(currentStored.getHeight()).thenReturn(headHeight);
    }
}
