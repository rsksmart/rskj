package co.rsk.net;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.test.World;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.DummyBlockValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.util.RskMockFactory;
import org.mockito.Mockito;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;

public class NodeMessageHandlerUtil {
    private static final DifficultyCalculator DIFFICULTY_CALCULATOR = new DifficultyCalculator(RskSystemProperties.CONFIG);

    public static NodeMessageHandler createHandler(BlockValidationRule validationRule) {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        SyncProcessor syncProcessor = new SyncProcessor(blockchain, blockSyncService, RskMockFactory.getPeerScoringManager(), syncConfiguration, new DummyBlockValidationRule(), DIFFICULTY_CALCULATOR);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);

        return new NodeMessageHandler(processor, syncProcessor, new SimpleChannelManager(), null, null, RskMockFactory.getPeerScoringManager(), validationRule);
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor() {
        return createHandlerWithSyncProcessor(SyncConfiguration.IMMEDIATE_FOR_TESTING);
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor(SyncConfiguration syncConfiguration) {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        return createHandlerWithSyncProcessor(blockchain, syncConfiguration);
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor(Blockchain blockchain, SyncConfiguration syncConfiguration) {
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        ProofOfWorkRule blockValidationRule = new ProofOfWorkRule(RskSystemProperties.CONFIG);
        PeerScoringManager peerScoringManager = mock(PeerScoringManager.class);
        Mockito.when(peerScoringManager.hasGoodReputation(isA(NodeID.class))).thenReturn(true);
        SyncProcessor syncProcessor = new SyncProcessor(blockchain, blockSyncService, peerScoringManager, syncConfiguration, blockValidationRule, DIFFICULTY_CALCULATOR);
        return new NodeMessageHandler(processor, syncProcessor, null, null, null, null, blockValidationRule);
    }
}
