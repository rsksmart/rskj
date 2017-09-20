package co.rsk.net.simples;

import co.rsk.net.*;
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

        return new NodeMessageHandler(processor, null, null, null, null);
    }

    public static NodeMessageHandler createHandlerWithSyncProcessor() {
        final World world = new World();
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = world.getBlockChain();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor bp = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);
        final SyncProcessor sp = new SyncProcessor(blockchain, blockSyncService);

        return new NodeMessageHandler(bp, sp, null, null, null);
    }
}
