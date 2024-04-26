package co.rsk.test.builders;

import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.Context;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.FederationSupport;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.storage.BridgeStorageAccessor;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.feeperkb.FeePerKbStorageProvider;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;

public class BridgeSupportBuilder {
    private BridgeConstants bridgeConstants;
    private BridgeStorageProvider provider;
    private BridgeEventLogger eventLogger;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;
    private Repository repository;
    private Block executionBlock;
    private Factory btcBlockStoreFactory;
    private ActivationConfig.ForBlock activations;
    private SignatureCache signatureCache;

    public BridgeSupportBuilder() {
        this.bridgeConstants = mock(BridgeConstants.class);
        this.provider = mock(BridgeStorageProvider.class);
        this.eventLogger = mock(BridgeEventLogger.class);
        this.btcLockSenderProvider= mock(BtcLockSenderProvider.class);
        this.peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        this.repository = mock(Repository.class);
        this.executionBlock = mock(Block.class);
        this.btcBlockStoreFactory = mock(Factory.class);
        this.activations = mock(ActivationConfig.ForBlock.class);
        this.signatureCache = mock(BlockTxSignatureCache.class);
    }

    public BridgeSupportBuilder withBridgeConstants(BridgeConstants bridgeConstants) {
        this.bridgeConstants = bridgeConstants;
        return this;
    }

    public BridgeSupportBuilder withProvider(BridgeStorageProvider provider) {
        this.provider = provider;
        return this;
    }

    public BridgeSupportBuilder withEventLogger(BridgeEventLogger eventLogger) {
        this.eventLogger = eventLogger;
        return this;
    }

    public BridgeSupportBuilder withBtcLockSenderProvider(BtcLockSenderProvider btcLockSenderProvider) {
        this.btcLockSenderProvider = btcLockSenderProvider;
        return this;
    }

    public BridgeSupportBuilder withPeginInstructionsProvider(PeginInstructionsProvider peginInstructionsProvider) {
        this.peginInstructionsProvider = peginInstructionsProvider;
        return this;
    }

    public BridgeSupportBuilder withRepository(Repository repository) {
        this.repository = repository;
        return this;
    }

    public BridgeSupportBuilder withExecutionBlock(Block executionBlock) {
        this.executionBlock = executionBlock;
        return this;
    }

    public BridgeSupportBuilder withBtcBlockStoreFactory(Factory btcBlockStoreFactory) {
        this.btcBlockStoreFactory = btcBlockStoreFactory;
        return this;
    }

    public BridgeSupportBuilder withActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
        return this;
    }

    public BridgeSupportBuilder withSignatureCache(SignatureCache signatureCache) {
        this.signatureCache = signatureCache;
        return this;
    }

    public BridgeSupport build() {
        BridgeStorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);

        return new BridgeSupport(
            bridgeConstants,
            provider,
            eventLogger,
            btcLockSenderProvider,
            peginInstructionsProvider,
            repository,
            executionBlock,
            new Context(bridgeConstants.getBtcParams()),
            new FederationSupport(bridgeConstants, provider, executionBlock, activations),
            new FeePerKbSupport(bridgeConstants.getFeePerKbConstants(), new FeePerKbStorageProvider(bridgeStorageAccessor)),
            btcBlockStoreFactory,
            activations,
            signatureCache
        );
    }
}
