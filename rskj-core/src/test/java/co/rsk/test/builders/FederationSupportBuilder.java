package co.rsk.test.builders;

import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.FederationSupportImpl;
import co.rsk.peg.federation.constants.FederationConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;

import static org.mockito.Mockito.mock;

public class FederationSupportBuilder {
    private FederationConstants federationConstants;
    private FederationStorageProvider federationStorageProvider;
    private Block rskExecutionBlock;
    private ActivationConfig.ForBlock activations;

    public FederationSupportBuilder() {
        this.federationConstants = mock(FederationConstants.class);
        this.federationStorageProvider = mock(FederationStorageProvider.class);
        this.rskExecutionBlock = mock(Block.class);
        this.activations = mock(ActivationConfig.ForBlock.class);
    }

    public FederationSupportBuilder withFederationConstants(FederationConstants federationConstants) {
        this.federationConstants = federationConstants;
        return this;
    }

    public FederationSupportBuilder withFederationStorageProvider(FederationStorageProvider federationStorageProvider) {
        this.federationStorageProvider = federationStorageProvider;
        return this;
    }

    public FederationSupportBuilder withRskExecutionBlock(Block rskExecutionBlock) {
        this.rskExecutionBlock = rskExecutionBlock;
        return this;
    }

    public FederationSupportBuilder withActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
        return this;
    }

    public FederationSupport build() {
        return new FederationSupportImpl(
            federationConstants,
            federationStorageProvider,
            rskExecutionBlock,
            activations
        );
    }

}
