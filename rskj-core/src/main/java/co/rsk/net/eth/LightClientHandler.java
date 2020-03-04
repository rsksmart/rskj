package co.rsk.net.eth;

import co.rsk.config.RskSystemProperties;
import co.rsk.net.MessageHandler;
import co.rsk.net.StatusResolver;
import co.rsk.net.light.message.TestMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.ethereum.core.Genesis;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.server.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the equivalent to the RSKWireProtocol, but both classes are
 * really Handlers for the communication channel
 */

public class LightClientHandler extends SimpleChannelInboundHandler<TestMessage> {
    private static final Logger logger = LoggerFactory.getLogger("lightnet");

    private final RskSystemProperties config;
    private final StatusResolver statusResolver;
    private final MessageHandler messageHandler;
    private final MessageRecorder messageRecorder;
    private final Genesis genesis;
    private final MessageQueue msgQueue;
    private final Channel channel;

    public LightClientHandler(RskSystemProperties config,
                              MessageHandler messageHandler,
                              Genesis genesis,
                              MessageRecorder messageRecorder,
                              StatusResolver statusResolver,
                              MessageQueue msgQueue,
                              Channel channel) {
        this.channel = channel;
        this.messageHandler = messageHandler;
        this.config = config;
        this.statusResolver = statusResolver;
        this.messageRecorder = messageRecorder;
        this.genesis = genesis;
        this.msgQueue = msgQueue;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TestMessage msg) throws Exception {

        switch (msg.getCommand()) {
            case TEST:
                logger.debug("Read message: {}", msg);
                msgQueue.receivedMessage(msg);
                break;
            default:
                break;
        }

    }

    private enum LightState {
        INIT,
        STATUS_SENT,
        STATUS_SUCCEEDED,
        STATUS_FAILED
    }

    public interface Factory {
        LightClientHandler newInstance(MessageQueue messageQueue, Channel channel);
    }
}
