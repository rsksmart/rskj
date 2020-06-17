package co.rsk.net;

import co.rsk.db.RepositoryLocator;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.LightMessageHandler;
import co.rsk.net.light.LightPeer;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.LightSyncProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.server.Channel;

import static org.mockito.Mockito.*;

public class LightClientTestUtils {
    private final MessageQueue messageQueue;
    private final Blockchain blockchain;
    private final BlockStore blockStore;
    private final LightSyncProcessor lightSyncProcessor;
    private final SystemProperties config;
    private final RepositoryLocator repositoryLocator;
    private final LightProcessor lightProcessor;
    private final LightMessageHandler lightMessageHandler;
    private final LightClientHandler.Factory factory;

    public LightClientTestUtils() {
        messageQueue = mock(MessageQueue.class);
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        config = mock(SystemProperties.class);
        repositoryLocator = mock(RepositoryLocator.class);
        Genesis genesis = mock(Genesis.class);
        lightProcessor = new LightProcessor(blockchain, blockStore, repositoryLocator);
        lightSyncProcessor = new LightSyncProcessor(config, genesis, blockStore, blockchain);
        lightMessageHandler = new LightMessageHandler(lightProcessor, lightSyncProcessor);
        factory = (lightPeer) -> new LightClientHandler(lightPeer, lightSyncProcessor, lightMessageHandler);
    }

    private void includeBlockInBlockchain(Block block) {
        when(blockchain.getBlockByNumber(block.getNumber())).thenReturn(block);
        when(blockchain.getBlockByHash(block.getHash().getBytes())).thenReturn(block);
    }

    public MessageQueue getMessageQueue() {
        return messageQueue;
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public LightSyncProcessor getLightSyncProcessor() {
        return lightSyncProcessor;
    }

    public SystemProperties getConfig() {
        return config;
    }

    public RepositoryLocator getRepositoryLocator() {
        return repositoryLocator;
    }

    public LightProcessor getLightProcessor() {
        return lightProcessor;
    }

    public LightPeer createPeer() {
        return spy(new LightPeer(mock(Channel.class), messageQueue));
    }

    public LightClientHandler generateLightClientHandler(LightPeer lightPeer) {
        return factory.newInstance(lightPeer);
    }

    public ChannelHandlerContext hookLightPeerToCtx(LightPeer lightPeer, LightClientHandler lightClientHandler) {
        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(lightClientHandler);
        return ch.pipeline().firstContext();
    }
}
