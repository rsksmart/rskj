package co.rsk.remasc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.utils.BridgeSerializationUtils;
import co.rsk.peg.utils.PegUtils;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 14/11/2017.
 */
public class RemascFederationProviderTest {

    private static final BridgeSerializationUtils bridgeSerializationUtils = PegUtils.getInstance().getBridgeSerializationUtils();

    @Test
    public void getDefaultFederationSize() {
        RemascFederationProvider provider = getRemascFederationProvider();
        Assert.assertEquals(3, provider.getFederationSize());
    }

    @Test
    public void getFederatorAddress() {
        RemascFederationProvider provider = getRemascFederationProvider();

        byte[] address = provider.getFederatorAddress(0).getBytes();

        Assert.assertNotNull(address);
        Assert.assertEquals(20, address.length);
    }

    private static RemascFederationProvider getRemascFederationProvider() {
        Genesis genesisBlock = new BlockGenerator().getGenesisBlock();
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock);
        Blockchain blockchain = builder.build();

        return new RemascFederationProvider(
                ActivationConfigsForTest.all(),
                BridgeRegTestConstants.getInstance(),
                builder.getRepository(),
                blockchain.getBestBlock(),
                bridgeSerializationUtils

        );
    }
}
