package co.rsk.remasc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 14/11/2017.
 */
class RemascFederationProviderTest {
    @Test
    void getDefaultFederationSize() {
        RemascFederationProvider provider = getRemascFederationProvider();
        Assertions.assertEquals(3, provider.getFederationSize());
    }

    @Test
    void getFederatorAddress() {
        RemascFederationProvider provider = getRemascFederationProvider();

        byte[] address = provider.getFederatorAddress(0).getBytes();

        Assertions.assertNotNull(address);
        Assertions.assertEquals(20, address.length);
    }

    private static RemascFederationProvider getRemascFederationProvider() {
        Genesis genesisBlock = new BlockGenerator().getGenesisBlock();
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock);
        Blockchain blockchain = builder.build();

        return new RemascFederationProvider(
                ActivationConfigsForTest.all(),
                BridgeRegTestConstants.getInstance(),
                builder.getRepository(),
                blockchain.getBestBlock()
        );
    }
}
