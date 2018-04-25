package co.rsk.net;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.test.World;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.DummyBlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.Blockchain;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.ChannelManagerImpl;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.sync.SyncPool;
import org.ethereum.util.RskMockFactory;
import org.mockito.Mockito;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;

public class NodeMessageHandlerUtil {
    private static final TestSystemProperties config = new TestSystemProperties();
    private static final DifficultyCalculator DIFFICULTY_CALCULATOR = new DifficultyCalculator(config);

    public static NodeMessageHandler createHandler(BlockValidationRule validationRule) {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        SyncProcessor syncProcessor = new SyncProcessor(config, blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), RskMockFactory.getChannelManager(), syncConfiguration, new DummyBlockValidationRule(), DIFFICULTY_CALCULATOR);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        return new NodeMessageHandler(config, processor, syncProcessor, new SimpleChannelManager(), null, null, RskMockFactory.getPeerScoringManager(), validationRule);
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor() {
        return createHandlerWithSyncProcessor(SyncConfiguration.IMMEDIATE_FOR_TESTING);
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor(SyncConfiguration syncConfiguration) {
        return createHandlerWithSyncProcessor(syncConfiguration, new ChannelManagerImpl(config, mock(SyncPool.class)));
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor(ChannelManager channelManager) {
        return createHandlerWithSyncProcessor(SyncConfiguration.IMMEDIATE_FOR_TESTING, channelManager);
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor(SyncConfiguration syncConfiguration, ChannelManager channelManager) {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        return createHandlerWithSyncProcessor(blockchain, syncConfiguration, channelManager);
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor(
            Blockchain blockchain,
            SyncConfiguration syncConfiguration,
            ChannelManager channelManager) {
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        ProofOfWorkRule blockValidationRule = new ProofOfWorkRule(config);
        PeerScoringManager peerScoringManager = mock(PeerScoringManager.class);
        Mockito.when(peerScoringManager.hasGoodReputation(isA(NodeID.class))).thenReturn(true);
        SyncProcessor syncProcessor = new SyncProcessor(config, blockchain, blockSyncService, peerScoringManager, channelManager, syncConfiguration, blockValidationRule, DIFFICULTY_CALCULATOR);
        return new NodeMessageHandler(config, processor, syncProcessor, channelManager, null, null, null, blockValidationRule);
    }
}
