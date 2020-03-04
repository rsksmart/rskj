package co.rsk.net.eth;

import co.rsk.net.light.message.TestMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the equivalent to the RSKWireProtocol, but both classes are
 * really Handlers for the communication channel
 */

public class LightClientHandler extends SimpleChannelInboundHandler<TestMessage> {
    private static final Logger logger = LoggerFactory.getLogger("lightnet");

    public LightClientHandler() {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TestMessage msg) throws Exception {

        switch (msg.getCommand()) {
            case TEST:
                logger.debug("Read message: {}", msg);
                break;
            default:
                break;
        }

    }
}
