package co.rsk.test.builders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.union.UnionBridgeSupport;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.whitelist.WhitelistSupport;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;

public class BridgeSupportBuilder {
    // From RSKIP643 on, building a federation spend wallet reads the BTC chain head
    private static final int DEFAULT_BTC_CHAIN_HEAD_HEIGHT = 870_000;

    private BridgeConstants bridgeConstants;
    private BridgeStorageProvider provider;
    private BridgeEventLogger eventLogger;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;
    private Repository repository;
    private Block executionBlock;
    private FeePerKbSupport feePerKbSupport;
    private WhitelistSupport whitelistSupport;
    private FederationSupport federationSupport;
    private LockingCapSupport lockingCapSupport;
    private UnionBridgeSupport unionBridgeSupport;
    private Factory btcBlockStoreFactory;
    private ActivationConfig.ForBlock activations;
    private SignatureCache signatureCache;

    private BridgeSupportBuilder() {
        this.bridgeConstants = mock(BridgeConstants.class);
        this.provider = mock(BridgeStorageProvider.class);
        this.eventLogger = mock(BridgeEventLogger.class);
        this.btcLockSenderProvider= mock(BtcLockSenderProvider.class);
        this.peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        this.repository = mock(Repository.class);
        this.executionBlock = mock(Block.class);
        this.feePerKbSupport = mock(FeePerKbSupport.class);
        this.whitelistSupport = mock(WhitelistSupport.class);
        this.federationSupport = mock(FederationSupport.class);
        this.lockingCapSupport = mock(LockingCapSupport.class);
        this.unionBridgeSupport = mock(UnionBridgeSupport.class);
        this.btcBlockStoreFactory = mockBtcBlockStoreFactoryWithChainHead();
        this.activations = mock(ActivationConfig.ForBlock.class);
        this.signatureCache = mock(BlockTxSignatureCache.class);
    }

    private static Factory mockBtcBlockStoreFactoryWithChainHead() {
        // Not the genesis hash, so building the block store does not load checkpoints
        BtcBlock chainHeadHeader = mock(BtcBlock.class);
        when(chainHeadHeader.getHash()).thenReturn(Sha256Hash.of(new byte[]{1}));

        StoredBlock chainHead = mock(StoredBlock.class);
        when(chainHead.getHeader()).thenReturn(chainHeadHeader);
        when(chainHead.getHeight()).thenReturn(DEFAULT_BTC_CHAIN_HEAD_HEIGHT);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        try {
            when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        } catch (BlockStoreException e) {
            throw new IllegalStateException(e);
        }

        Factory btcBlockStoreFactory = mock(Factory.class);
        when(btcBlockStoreFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);
        return btcBlockStoreFactory;
    }

    public static BridgeSupportBuilder builder() {
        return new BridgeSupportBuilder();
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

    public BridgeSupportBuilder withFeePerKbSupport(FeePerKbSupport feePerKbSupport) {
        this.feePerKbSupport = feePerKbSupport;
        return this;
    }

    public BridgeSupportBuilder withWhitelistSupport(WhitelistSupport whitelistSupport) {
        this.whitelistSupport = whitelistSupport;
        return this;
    }

    public BridgeSupportBuilder withFederationSupport(FederationSupport federationSupport) {
        this.federationSupport = federationSupport;
        return this;
    }

    public BridgeSupportBuilder withLockingCapSupport(LockingCapSupport lockingCapSupport) {
        this.lockingCapSupport = lockingCapSupport;
        return this;
    }

    public BridgeSupportBuilder withUnionBridgeSupport(UnionBridgeSupport unionBridgeSupport) {
        this.unionBridgeSupport = unionBridgeSupport;
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
        Context context = new Context(bridgeConstants.getBtcParams());

        return new BridgeSupport(
            bridgeConstants,
            provider,
            eventLogger,
            btcLockSenderProvider,
            peginInstructionsProvider,
            repository,
            executionBlock,
            context,
            feePerKbSupport,
            whitelistSupport,
            federationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            btcBlockStoreFactory,
            activations,
            signatureCache
        );
    }
}
