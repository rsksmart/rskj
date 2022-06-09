package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.TestSystemProperties;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.utils.PegUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.vm.exception.VMException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

import static org.mockito.Mockito.*;

public class BridgeRSKIP220NewMethodsTest {
    private static Random random = new Random();

    private TestSystemProperties config;
    private Constants constants;
    private ActivationConfig activationConfig;
    private BridgeSupport bridgeSupport;
    private BridgeSupportFactory bridgeSupportFactory;
    private Block rskExecutionBlock;
    private Bridge bridge;

    private PegUtils pegUtils = PegUtils.getInstance();

    @Before
    public void beforeEach() {
        config = spy(new TestSystemProperties());
        constants = Constants.regtest();
        when(config.getNetworkConstants()).thenReturn(constants);
        activationConfig = spy(ActivationConfigsForTest.genesis());
        when(config.getActivationConfig()).thenReturn(activationConfig);
        bridgeSupport = mock(BridgeSupport.class);
        bridgeSupportFactory = mock(BridgeSupportFactory.class);
        when(bridgeSupportFactory.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupport);
        rskExecutionBlock = mock(Block.class);
        when(rskExecutionBlock.getNumber()).thenReturn(42L);

        bridge = new Bridge(
                null,
                constants,
                activationConfig,
                bridgeSupportFactory, pegUtils.getBridgeUtils(), MerkleBranch::new
        );

        bridge.init(null, rskExecutionBlock, null, null, null, null);
    }

    @Test
    public void getBtcBlockchainBestBlockHeader() throws IOException, BlockStoreException {
        byte[] header = new byte[80];
        random.nextBytes(header);
        when(bridgeSupport.getBtcBlockchainBestBlockHeader()).thenReturn(header);
        byte[] result = bridge.getBtcBlockchainBestBlockHeader(new Object[0]);

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockchainBestChainHeight() throws IOException, BlockStoreException, VMException {
        when(bridgeSupport.getBtcBlockchainBestChainHeight()).thenReturn(42);

        int result = bridge.getBtcBlockchainBestChainHeight(new Object[0]);

        Assert.assertEquals(42, result);
    }

    @Test
    public void getBtcBlockchainBlockHeaderByHash() throws IOException, BlockStoreException {
        byte[] hashBytes = new byte[32];
        random.nextBytes(hashBytes);
        byte[] header = new byte[80];
        random.nextBytes(header);

        when(bridgeSupport.getBtcBlockchainBlockHeaderByHash(Sha256Hash.wrap(hashBytes))).thenReturn(header);
        byte[] result = bridge.getBtcBlockchainBlockHeaderByHash(new Object[] { hashBytes });

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockchainBlockHeaderByHeight() throws IOException, BlockStoreException {
        byte[] header = new byte[80];
        random.nextBytes(header);
        BigInteger height = BigInteger.TEN;

        when(bridgeSupport.getBtcBlockchainBlockHeaderByHeight(10)).thenReturn(header);
        byte[] result = bridge.getBtcBlockchainBlockHeaderByHeight(new Object[] { height });

        Assert.assertArrayEquals(header, result);
    }

    @Test
    public void getBtcBlockchainParentBlockHeaderByHash() throws IOException, BlockStoreException {
        byte[] hashBytes = new byte[32];
        random.nextBytes(hashBytes);
        byte[] header = new byte[80];
        random.nextBytes(header);

        when(bridgeSupport.getBtcBlockchainParentBlockHeaderByHash(Sha256Hash.wrap(hashBytes))).thenReturn(header);
        byte[] result = bridge.getBtcBlockchainParentBlockHeaderByHash(new Object[] { hashBytes });

        Assert.assertArrayEquals(header, result);
    }
}
