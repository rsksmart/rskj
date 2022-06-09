package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.PegUtils;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeSupportRSKIP220NewMethodsTest {
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

    private final PegUtils pegUtils = PegUtils.getInstance(); // TODO:I get from TestContext

    @Before
    public void setUpOnEachTest() throws BlockStoreException {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        btcParams = bridgeConstants.getBtcParams();
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
        activationsAfterForks = ActivationConfigsForTest.all().forBlock(0);

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

        bridgeSupport = getBridgeSupport(bridgeConstants, btcBlockStoreFactory, activationsAfterForks);
    }

    @Test
    public void getBtcBlockchainBestBlockHeader() throws BlockStoreException, IOException {
        byte[] result = bridgeSupport.getBtcBlockchainBestBlockHeader();

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockchainBlockHeaderByHash() throws BlockStoreException, IOException {
        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHash(hash);

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockchainBlockHeaderByUnknownHash() throws BlockStoreException, IOException {
        byte[] unknownHashBytes = new byte[32];
        random.nextBytes(unknownHashBytes);
        Sha256Hash unknownHash = Sha256Hash.wrap(unknownHashBytes);

        when(btcBlockStore.get(unknownHash)).thenReturn(null);

        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHash(unknownHash);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }

    @Test
    public void getBtcBlockchainBlockHeaderByHeight() throws BlockStoreException, IOException {
        when(btcBlockStore.getStoredBlockAtMainChainHeight(20)).thenReturn(storedBlock);

        byte[] result = bridgeSupport.getBtcBlockchainBlockHeaderByHeight(20);

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockchainParentBlockHeaderByHash() throws BlockStoreException, IOException {
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

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockchainParentBlockHeaderByUnknownHash() throws BlockStoreException, IOException {
        when(btcBlockStore.get(hash)).thenReturn(null);

        byte[] result = bridgeSupport.getBtcBlockchainParentBlockHeaderByHash(hash);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
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
                activations,
                pegUtils
        );
    }
}
