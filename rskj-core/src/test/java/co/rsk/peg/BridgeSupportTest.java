package co.rsk.peg;

import co.rsk.bitcoinj.core.Context;
import co.rsk.config.BridgeConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeSupportTest {

    @Test
    public void activations_is_set() {
        Block block = mock(Block.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP124)).thenReturn(true);

        BridgeSupport bridgeSupport = new BridgeSupport(
                mock(BridgeConstants.class), provider, mock(BridgeEventLogger.class),
                mock(Repository.class), block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class), activations
        );

        Assert.assertTrue(bridgeSupport.getActivations().isActive(ConsensusRule.RSKIP124));
    }
}