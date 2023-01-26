package co.rsk.net;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.test.World;
import co.rsk.validators.*;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.util.RskMockFactory;
import org.mockito.Mockito;

import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;

public class NodeMessageHandlerUtil {
    private static final TestSystemProperties config = new TestSystemProperties();
    private static final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private static final DifficultyCalculator DIFFICULTY_CALCULATOR = new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants());

    public static NodeMessageHandler createHandler(Blockchain blockchain) {
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        SyncProcessor syncProcessor = new SyncProcessor(
                blockchain, mock(org.ethereum.db.BlockStore.class), mock(ConsensusValidationMainchainView.class), blockSyncService,
                syncConfiguration, blockFactory, new DummyBlockValidationRule(),
                new SyncBlockValidatorRule(new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())),
                DIFFICULTY_CALCULATOR, new PeersInformation(RskMockFactory.getChannelManager(), syncConfiguration, blockchain, RskMockFactory.getPeerScoringManager(), config.getPercentageOfPeersToConsiderInRandomSelection()),
                mock(Genesis.class),
                mock(EthereumListener.class));
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        return new NodeMessageHandler(config, processor, syncProcessor, new SimpleChannelManager(), null, RskMockFactory.getPeerScoringManager(), mock(StatusResolver.class));
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor(SyncConfiguration syncConfiguration, ChannelManager channelManager) {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        final BlockStore blockStore = world.getBlockStore();
        return createHandlerWithSyncProcessor(blockchain, syncConfiguration, channelManager, blockStore);
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor(
            Blockchain blockchain,
            SyncConfiguration syncConfiguration,
            ChannelManager channelManager, BlockStore blockStore) {
        final NetBlockStore store = new NetBlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        ProofOfWorkRule blockValidationRule = new ProofOfWorkRule(config);
        PeerScoringManager peerScoringManager = mock(PeerScoringManager.class);
        Mockito.when(peerScoringManager.hasGoodReputation(isA(NodeID.class))).thenReturn(true);
        SyncProcessor syncProcessor = new SyncProcessor(
                blockchain, blockStore, mock(ConsensusValidationMainchainView.class), blockSyncService, syncConfiguration, blockFactory,
                blockValidationRule, new SyncBlockValidatorRule(new BlockUnclesHashValidationRule(),
                new BlockRootValidationRule(config.getActivationConfig())),
                DIFFICULTY_CALCULATOR,
                new PeersInformation(channelManager, syncConfiguration, blockchain, peerScoringManager, config.getPercentageOfPeersToConsiderInRandomSelection()),
                mock(Genesis.class),
                mock(EthereumListener.class)
        );

        return new NodeMessageHandler(config, processor, syncProcessor, channelManager, null, null, mock(StatusResolver.class));
    }
}
