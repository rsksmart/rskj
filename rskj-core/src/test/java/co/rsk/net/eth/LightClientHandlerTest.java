package co.rsk.net.eth;

import co.rsk.db.RepositoryLocator;
import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.message.TestMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockStore;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.server.Channel;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class LightClientHandlerTest {
    private MessageQueue messageQueue;
    private LightClientHandler lightClientHandler;
    private Channel channel;
    private ChannelHandlerContext ctx;
    private LightProcessor lightProcessor;
    private Blockchain blockchain;
    private BlockStore blockStore;
    private RepositoryLocator repositoryLocator;

    @Before
    public void setup() {
        messageQueue = spy(MessageQueue.class);
        channel = mock(Channel.class);
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        repositoryLocator = mock(RepositoryLocator.class);
        lightProcessor = new LightProcessor(blockchain, blockStore, repositoryLocator);
        LightClientHandler.Factory factory = msgQueue -> new LightClientHandler(msgQueue, lightProcessor);
        lightClientHandler = factory.newInstance(messageQueue);

        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(lightClientHandler);
        ctx = ch.pipeline().firstContext();
    }

    @Test
    public void lightClientHandlerSendsMessageToQueue() throws Exception {
        TestMessage m = new TestMessage();
        lightClientHandler.channelRead0(ctx, m);
        verify(messageQueue, times(1)).sendMessage(any());
    }

}
