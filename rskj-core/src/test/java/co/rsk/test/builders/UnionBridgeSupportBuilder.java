package co.rsk.test.builders;

import static org.mockito.Mockito.mock;

import co.rsk.peg.union.UnionBridgeStorageProvider;
import co.rsk.peg.union.UnionBridgeSupport;
import co.rsk.peg.union.UnionBridgeSupportImpl;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import org.ethereum.core.SignatureCache;

public class UnionBridgeSupportBuilder {
    private UnionBridgeConstants constants;
    private UnionBridgeStorageProvider storageProvider;
    private SignatureCache signatureCache;
    private BridgeEventLogger eventLogger;

    private UnionBridgeSupportBuilder() {
        this.constants = mock(UnionBridgeConstants.class);
        this.storageProvider = mock(UnionBridgeStorageProvider.class);
        this.signatureCache = mock(SignatureCache.class);
        this.eventLogger = mock(BridgeEventLogger.class);
    }

    public static UnionBridgeSupportBuilder builder() {
        return new UnionBridgeSupportBuilder();
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

public UnionBridgeSupportBuilder withEventLogger(BridgeEventLogger eventLogger) {
        this.eventLogger = eventLogger;
        return this;
    }

    public UnionBridgeSupport build() {
        return new UnionBridgeSupportImpl(
            constants,
            storageProvider,
            signatureCache,
            eventLogger
        );
    }
}
