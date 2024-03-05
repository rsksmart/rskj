package co.rsk.remasc;

import static org.mockito.Mockito.*;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.FederationSupport;
import co.rsk.peg.federation.FederationMember;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.util.HexUtils;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 14/11/2017.
 */
class RemascFederationProviderTest {

    private static final String BTC_PUBLIC_KEY = "0x03a5e32151f974c35c5d08af36a8d6af0593248e8e4adca7ed0148192eb2f5d1bf";
    private static final String RSK_ADDRESS_FROM_BTC_PUBLIC_KEY = "131c7c821b0e4a1ab570feed952d9304e03fddd1";
    private static final String RSK_PUBLIC_KEY = "0x02f3b5fb3121932db9450121b0a37f3f051dc8b3fd5f1ea5e7cf726947d8f69c28";
    private static final String RSK_ADDRESS_FROM_RSK_PUBLIC_KEY = "54a948b3d76ce84e5c7b4b3cd01f2af1f18d41e0";

    @Test
    void getDefaultFederationSize() {
        RemascFederationProvider provider = getRemascFederationProvider();
        Assertions.assertEquals(15, provider.getFederationSize());
    }

    @Test
    void getFederatorAddress_preRSKIP415_returnsRskAddressDerivedFromBtcPublicKey() {

        int federatorIndex = 0;

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP415)).thenReturn(false);

        FederationSupport federationSupport =  mock(FederationSupport.class);

        byte[] btcPublicKey = HexUtils.strHexOrStrNumberToByteArray(BTC_PUBLIC_KEY);

        when(federationSupport.getFederatorBtcPublicKey(federatorIndex))
                .thenReturn(btcPublicKey);

        RemascFederationProvider provider = new RemascFederationProvider(activations, federationSupport);

        RskAddress actualRskAddress = provider.getFederatorAddress(federatorIndex);

        Assertions.assertEquals(RSK_ADDRESS_FROM_BTC_PUBLIC_KEY, actualRskAddress.toHexString());
        verify(federationSupport, never()).getFederatorPublicKeyOfType(federatorIndex, FederationMember.KeyType.RSK);
        verify(federationSupport, times(1)).getFederatorBtcPublicKey(federatorIndex);

    }

    @Test
    void getFederatorAddress_postRSKIP415_returnsRskAddressDerivedFromRskPublicKey() {

        int federatorIndex = 0;

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP415)).thenReturn(true);

        FederationSupport federationSupport =  mock(FederationSupport.class);

        byte[] rskPublicKey = HexUtils.strHexOrStrNumberToByteArray(RSK_PUBLIC_KEY);

        when(federationSupport.getFederatorPublicKeyOfType(federatorIndex, FederationMember.KeyType.RSK))
                .thenReturn(rskPublicKey);

        RemascFederationProvider provider = new RemascFederationProvider(activations, federationSupport);

        RskAddress actualRskAddress = provider.getFederatorAddress(federatorIndex);

        Assertions.assertEquals(RSK_ADDRESS_FROM_RSK_PUBLIC_KEY, actualRskAddress.toHexString());
        verify(federationSupport, times(1)).getFederatorPublicKeyOfType(federatorIndex, FederationMember.KeyType.RSK);
        verify(federationSupport, never()).getFederatorBtcPublicKey(federatorIndex);

    }

    private static RemascFederationProvider getRemascFederationProvider() {

        Genesis genesisBlock = new BlockGenerator().getGenesisBlock();
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock);
        Blockchain blockchain = builder.build();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(blockchain.getBestBlock().getNumber());

        BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();

        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
                builder.getRepository(),
                PrecompiledContracts.BRIDGE_ADDR,
                bridgeMainNetConstants,
                activations
        );

        FederationSupport federationSupport = new FederationSupport(bridgeMainNetConstants, bridgeStorageProvider, blockchain.getBestBlock(), activations);

        return new RemascFederationProvider(
                activations,
                federationSupport
        );
    }
}
