package co.rsk.peg;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.utils.BridgeEventLogger;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BridgeSupportTest {

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
                activations,
                null
        );
    }
}
