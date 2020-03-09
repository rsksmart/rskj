package co.rsk.net.eth;

import co.rsk.net.light.message.TestMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.ethereum.net.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the equivalent to the RSKWireProtocol, but both classes are
 * really Handlers for the communication channel
 */

public class LightClientHandler extends SimpleChannelInboundHandler<TestMessage> {
    private static final Logger logger = LoggerFactory.getLogger("lightnet");
    private final MessageQueue msgQueue;

    public LightClientHandler(MessageQueue msgQueue) {
        this.msgQueue = msgQueue;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TestMessage msg) throws Exception {
        switch (msg.getCommand()) {
            case TEST:
                logger.debug("Read message: {} TEST. Sending Test response", msg);
                msgQueue.sendMessage(new TestMessage());
                break;
            default:
                break;
        }

    }

    public void activate() {
        sendTest();
    }

    private void sendTest() {
        TestMessage testMessage = new TestMessage();
        msgQueue.sendMessage(new TestMessage());
        logger.info("LC [ Sending Message {} ]", testMessage.getCommand());
    }

    public interface Factory {
        LightClientHandler newInstance(MessageQueue messageQueue);
    }
}
