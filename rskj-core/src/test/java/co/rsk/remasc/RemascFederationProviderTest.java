package co.rsk.remasc;

import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.junit.Assert;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.BridgeSupport;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by ajlopez on 14/11/2017.
 */
public class RemascFederationProviderTest {
    @Test
    public void getDefaultFederationSize() throws IOException, BlockStoreException {
        RemascFederationProvider provider = getRemascFederationProvider();
        Assert.assertEquals(3, provider.getFederationSize());
    }

    @Test
    public void getFederatorAddress() throws IOException, BlockStoreException {
        RemascFederationProvider provider = getRemascFederationProvider();

        byte[] address = provider.getFederatorAddress(0);

        Assert.assertNotNull(address);
        Assert.assertEquals(20, address.length);
    }

    private static RemascFederationProvider getRemascFederationProvider() throws IOException, BlockStoreException {
        Genesis genesisBlock = new BlockGenerator().getGenesisBlock();
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setRsk(true).setGenesis(genesisBlock);
        Blockchain blockchain = builder.build();

        BridgeSupport bridgeSupport = new BridgeSupport(blockchain.getRepository(),
                PrecompiledContracts.BRIDGE_ADDR,
                null,
                BridgeRegTestConstants.getInstance(),
                null);
        RemascFederationProvider provider = null;

        try {
            provider = new RemascFederationProvider(bridgeSupport);
        } catch (BlockStoreException | IOException e) {
            e.printStackTrace();
        }

        return provider;
    }
}
