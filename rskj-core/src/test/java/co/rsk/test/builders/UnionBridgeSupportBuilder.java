package co.rsk.test.builders;

import static org.mockito.Mockito.mock;

import co.rsk.peg.union.UnionBridgeStorageProvider;
import co.rsk.peg.union.UnionBridgeSupport;
import co.rsk.peg.union.UnionBridgeSupportImpl;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.SignatureCache;

public class UnionBridgeSupportBuilder {
    private ActivationConfig.ForBlock activations;
    private UnionBridgeConstants constants;
    private UnionBridgeStorageProvider storageProvider;
    private SignatureCache signatureCache;


    private UnionBridgeSupportBuilder() {
        this.activations = mock(ActivationConfig.ForBlock.class);
        this.constants = mock(UnionBridgeConstants.class);
        this.storageProvider = mock(UnionBridgeStorageProvider.class);
        this.signatureCache = mock(SignatureCache.class);
    }

    public static UnionBridgeSupportBuilder builder() {
        return new UnionBridgeSupportBuilder();
    }

    public UnionBridgeSupportBuilder withActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
        return this;
    }

    public UnionBridgeSupportBuilder withConstants(UnionBridgeConstants constants) {
        this.constants = constants;
        return this;
    }

    public UnionBridgeSupportBuilder withStorageProvider(UnionBridgeStorageProvider storageProvider) {
        this.storageProvider = storageProvider;
        return this;
    }

    public UnionBridgeSupportBuilder withSignatureCache(SignatureCache signatureCache) {
        this.signatureCache = signatureCache;
        return this;
    }

    public UnionBridgeSupport build() {
        return new UnionBridgeSupportImpl(
            activations,
            constants,
            storageProvider,
            signatureCache
        );
    }
}
