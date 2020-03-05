package co.rsk.net.eth;

import co.rsk.net.light.message.TestMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
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

    @Before
    public void setup() {
        messageQueue = spy(MessageQueue.class);
        channel = mock(Channel.class);
        LightClientHandler.Factory factory = LightClientHandler::new;
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
