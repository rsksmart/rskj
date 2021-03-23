package co.rsk.peg;

import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.TestSystemProperties;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

public class BridgeNewMethodsTest {
    @Test
    public void getBestBlockNumber() throws IOException, BlockStoreException {
        TestSystemProperties config = spy(new TestSystemProperties());
        Constants constants = Constants.regtest();
        when(config.getNetworkConstants()).thenReturn(constants);
        ActivationConfig activationConfig = spy(ActivationConfigsForTest.genesis());
        when(config.getActivationConfig()).thenReturn(activationConfig);
        BridgeSupport bridgeSupport = mock(BridgeSupport.class);
        when(bridgeSupport.getBtcBlockchainBestChainHeight()).thenReturn(42);
        BridgeSupportFactory bridgeSupportFactory = mock(BridgeSupportFactory.class);
        when(bridgeSupportFactory.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupport);
        Block rskExecutionBlock = mock(Block.class);
        when(rskExecutionBlock.getNumber()).thenReturn(42L);

        Bridge bridge = new Bridge(
            null,
            constants,
            activationConfig,
            bridgeSupportFactory
        );

        bridge.init(null, rskExecutionBlock, null, null, null, null);

        long result = bridge.getBestBlockNumber(new Object[0]);

        Assert.assertEquals(42, result);
    }
}
