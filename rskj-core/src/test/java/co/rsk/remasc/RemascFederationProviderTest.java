package co.rsk.remasc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.RskAddress;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.FederationSupportImpl;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.util.HexUtils;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.core.Repository;
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
        assertEquals(15, provider.getFederationSize());
    }

    @Test
    void getFederatorAddress_preRSKIP415_returnsRskAddressDerivedFromBtcPublicKey() {
        int federatorIndex = 0;

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP415)).thenReturn(false);

        byte[] btcPublicKey = HexUtils.strHexOrStrNumberToByteArray(BTC_PUBLIC_KEY);
        FederationSupport federationSupport =  mock(FederationSupport.class);
        when(federationSupport.getActiveFederatorBtcPublicKey(federatorIndex)).thenReturn(btcPublicKey);

        RemascFederationProvider provider = new RemascFederationProvider(activations, federationSupport);

        RskAddress actualRskAddress = provider.getFederatorAddress(federatorIndex);

        assertEquals(RSK_ADDRESS_FROM_BTC_PUBLIC_KEY, actualRskAddress.toHexString());
        verify(federationSupport, never()).getActiveFederatorPublicKeyOfType(federatorIndex, FederationMember.KeyType.RSK);
        verify(federationSupport, times(1)).getActiveFederatorBtcPublicKey(federatorIndex);
    }

    @Test
    void getFederatorAddress_postRSKIP415_returnsRskAddressDerivedFromRskPublicKey() {
        int federatorIndex = 0;

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP415)).thenReturn(true);

        byte[] rskPublicKey = HexUtils.strHexOrStrNumberToByteArray(RSK_PUBLIC_KEY);
        FederationSupport federationSupport =  mock(FederationSupport.class);
        when(federationSupport.getActiveFederatorPublicKeyOfType(
            federatorIndex,
            FederationMember.KeyType.RSK
        )).thenReturn(rskPublicKey);

        RemascFederationProvider provider = new RemascFederationProvider(activations, federationSupport);

        RskAddress actualRskAddress = provider.getFederatorAddress(federatorIndex);

        assertEquals(RSK_ADDRESS_FROM_RSK_PUBLIC_KEY, actualRskAddress.toHexString());
        verify(federationSupport, times(1)).getActiveFederatorPublicKeyOfType(
            federatorIndex,
            FederationMember.KeyType.RSK
        );
        verify(federationSupport, never()).getActiveFederatorBtcPublicKey(federatorIndex);
    }

    private static RemascFederationProvider getRemascFederationProvider() {
        Genesis genesisBlock = new BlockGenerator().getGenesisBlock();
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock);
        Blockchain blockchain = builder.build();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(blockchain.getBestBlock().getNumber());

        final FederationSupport federationSupport = getFederationSupport(
            builder.getRepository(),
            blockchain,
            activations
        );

        return new RemascFederationProvider(
            activations,
            federationSupport
        );
    }

    private static FederationSupport getFederationSupport(Repository repository, Blockchain blockchain, ForBlock activations) {
        FederationConstants federationMainNetConstants = FederationMainNetConstants.getInstance();
        StorageAccessor storageAccessor = new BridgeStorageAccessorImpl(repository);
        FederationStorageProvider storageProvider = new FederationStorageProviderImpl(storageAccessor);

        return new FederationSupportImpl(
            federationMainNetConstants,
            storageProvider,
            blockchain.getBestBlock(),
            activations
        );
    }
}
