package co.rsk.net;

import co.rsk.net.sync.SyncConfiguration;
import co.rsk.test.World;
import org.ethereum.core.Blockchain;

public class NodeMessageHandlerUtil {

    public static NodeMessageHandler createHandler() {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        return new NodeMessageHandler(processor, null, null, null, null, null);
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
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);
        SyncProcessor syncProcessor = new SyncProcessor(blockchain, blockSyncService, syncConfiguration);
        return new NodeMessageHandler(processor, syncProcessor, null, null, null, null);
    }
}
