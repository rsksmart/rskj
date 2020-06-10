package co.rsk.net;

import co.rsk.db.RepositoryLocator;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.LightMessageHandler;
import co.rsk.net.light.LightPeer;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.LightSyncProcessor;
import co.rsk.net.light.message.GetAccountsMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.server.Channel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class LightMessageHandlerTest {
    LightMessageHandler lightMessageHandler;
    private MessageQueue messageQueue;
    private Blockchain blockchain;
    private BlockStore blockStore;
    private LightSyncProcessor lightSyncProcessor;
    private LightPeer lightPeer;
    private LightClientHandler lightClientHandler;
    private SystemProperties config;
    private RepositoryLocator repositoryLocator;
    private ChannelHandlerContext ctx;
    private LightProcessor lightProcessor;

    @Before
    public void setUp() {
        messageQueue = mock(MessageQueue.class);
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        config = mock(SystemProperties.class);
        repositoryLocator = mock(RepositoryLocator.class);
        Genesis genesis = mock(Genesis.class);
        lightProcessor = mock(LightProcessor.class);
        lightSyncProcessor = new LightSyncProcessor(config, genesis, blockStore, blockchain);
        lightPeer = spy(new LightPeer(mock(Channel.class), messageQueue));
        LightClientHandler.Factory factory = (lightPeer) -> new LightClientHandler(lightPeer, lightSyncProcessor, lightMessageHandler);
        lightClientHandler = factory.newInstance(lightPeer);
        lightMessageHandler = new LightMessageHandler(lightProcessor, lightSyncProcessor);

        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(lightClientHandler);
        ctx = ch.pipeline().firstContext();
    }

    /**
     * We check that, given a random message,
     * the processing for that message is called
     */

    @Test
    public void lightMessageHandlerHandlesAMessageCorrectly() {
        GetAccountsMessage m = new GetAccountsMessage(0, new byte[] {0x00}, new byte[] {0x00});
        lightMessageHandler.postMessage(lightPeer, m, ctx, lightClientHandler);
        assertEquals(1,lightMessageHandler.getMessageQueueSize());
        lightMessageHandler.handleMessage();
        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

    @Test
    public void lightMessageHandlerHandlesTwoMessagesCorrectly() {
        GetAccountsMessage m1 = new GetAccountsMessage(0, new byte[] {0x00}, new byte[] {0x00});
        GetAccountsMessage m2 = new GetAccountsMessage(0, new byte[] {0x00}, new byte[] {0x00});
        lightMessageHandler.postMessage(lightPeer, m1, ctx, lightClientHandler);
        lightMessageHandler.postMessage(lightPeer, m2, ctx, lightClientHandler);
        assertEquals(2,lightMessageHandler.getMessageQueueSize());
        lightMessageHandler.handleMessage();
        lightMessageHandler.handleMessage();
        assertEquals(0,lightMessageHandler.getMessageQueueSize());
    }

}
