package co.rsk.test.builders;

import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.Context;
import co.rsk.config.BridgeConstants;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.FederationSupport;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;

public class BridgeSupportBuilder {
    private BridgeConstants bridgeConstants;
    private BridgeStorageProvider provider;
    private BridgeEventLogger eventLogger;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;
    private Repository repository;
    private Block executionBlock;
    private Context btcContext;
    private FederationSupport federationSupport;
    private Factory btcBlockStoreFactory;
    private ActivationConfig.ForBlock activations;

    public BridgeSupportBuilder() {
        this.bridgeConstants = mock(BridgeConstants.class);
        this.provider = mock(BridgeStorageProvider.class);
        this.eventLogger = mock(BridgeEventLogger.class);
        this.btcLockSenderProvider= mock(BtcLockSenderProvider.class);
        this.peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        this.repository = mock(Repository.class);
        this.executionBlock = mock(Block.class);
        this.btcContext = mock(Context.class);
        this.federationSupport = mock(FederationSupport.class);
        this.btcBlockStoreFactory = mock(Factory.class);
        this.activations = mock(ActivationConfig.ForBlock.class);
    }

    public BridgeSupportBuilder bridgeConstants(BridgeConstants bridgeConstants) {
        this.bridgeConstants = bridgeConstants;
        return this;
    }

    public BridgeSupportBuilder provider(BridgeStorageProvider provider) {
        this.provider = provider;
        return this;
    }

    public BridgeSupportBuilder eventLogger(BridgeEventLogger eventLogger) {
        this.eventLogger = eventLogger;
        return this;
    }

    public BridgeSupportBuilder btcLockSenderProvider(BtcLockSenderProvider btcLockSenderProvider) {
        this.btcLockSenderProvider = btcLockSenderProvider;
        return this;
    }

    public BridgeSupportBuilder peginInstructionsProvider(PeginInstructionsProvider peginInstructionsProvider) {
        this.peginInstructionsProvider = peginInstructionsProvider;
        return this;
    }

    public BridgeSupportBuilder repository(Repository repository) {
        this.repository = repository;
        return this;
    }

    public BridgeSupportBuilder executionBlock(Block executionBlock) {
        this.executionBlock = executionBlock;
        return this;
    }

    public BridgeSupportBuilder btcContext(Context btcContext) {
        this.btcContext = btcContext;
        return this;
    }

    public BridgeSupportBuilder federationSupport(FederationSupport federationSupport) {
        this.federationSupport = federationSupport;
        return this;
    }

    public BridgeSupportBuilder btcBlockStoreFactory(Factory btcBlockStoreFactory) {
        this.btcBlockStoreFactory = btcBlockStoreFactory;
        return this;
    }

    public BridgeSupportBuilder activations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
        return this;
    }

    public BridgeSupport build() {
        return new BridgeSupport(
            bridgeConstants,
            provider,
            eventLogger,
            btcLockSenderProvider,
            peginInstructionsProvider,
            repository,
            executionBlock,
            btcContext,
            federationSupport,
            btcBlockStoreFactory,
            activations
        );
    }
}
