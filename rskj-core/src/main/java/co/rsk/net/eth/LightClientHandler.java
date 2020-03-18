package co.rsk.net.eth;

import co.rsk.net.light.LightProcessor;
import co.rsk.net.light.message.BlockReceiptsMessage;
import co.rsk.net.light.message.GetBlockReceiptsMessage;
import co.rsk.net.light.message.LightClientMessage;
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

public class LightClientHandler extends SimpleChannelInboundHandler<LightClientMessage> {
    private static final Logger logger = LoggerFactory.getLogger("lightnet");
    private final MessageQueue msgQueue;
    private LightProcessor lightProcessor;

    public LightClientHandler(MessageQueue msgQueue, LightProcessor lightProcessor) {
        this.msgQueue = msgQueue;
        this.lightProcessor = lightProcessor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, LightClientMessage msg) throws Exception {
        switch (msg.getCommand()) {
            case TEST:
                logger.debug("Read message: {} TEST. Sending Test response", msg);
                lightProcessor.processTestMessage((TestMessage) msg, msgQueue);
                break;
            case GET_BLOCK_RECEIPTS:
                logger.debug("Read message: {} GET_BLOCK_RECEIPTS. Sending receipts request", msg);
                GetBlockReceiptsMessage getBlockReceiptsMsg = (GetBlockReceiptsMessage) msg;
                lightProcessor.processGetBlockReceiptsMessage(getBlockReceiptsMsg.getId(), getBlockReceiptsMsg.getBlockHash(), msgQueue);
                break;
            case BLOCK_RECEIPTS:
                logger.debug("Read message: {} BLOCK_RECEIPTS. Sending receipts response", msg);
                BlockReceiptsMessage blockReceiptsMsg = (BlockReceiptsMessage) msg;
                lightProcessor.processBlockReceiptsMessage(blockReceiptsMsg.getId(), blockReceiptsMsg.getBlockReceipts(), msgQueue);
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
