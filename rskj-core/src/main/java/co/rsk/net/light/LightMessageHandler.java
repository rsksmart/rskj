package co.rsk.net.light;

import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.message.LightClientMessage;
import io.netty.channel.ChannelHandlerContext;

public class LightMessageHandler {

    private final LightProcessor lightProcessor;
    private final LightSyncProcessor lightSyncProcessor;

    public LightMessageHandler(LightProcessor lightProcessor, LightSyncProcessor lightSyncProcessor) {
        this.lightProcessor = lightProcessor;
        this.lightSyncProcessor = lightSyncProcessor;
    }

    public void processMessage(LightPeer lightPeer, LightClientMessage message,
                               ChannelHandlerContext ctx, LightClientHandler lightClientHandler) {
        LightClientMessageVisitor visitor = new LightClientMessageVisitor(lightPeer, lightProcessor, lightSyncProcessor, ctx, lightClientHandler);
        message.accept(visitor);
    }

    public void postMessage(LightPeer sender, LightClientMessage message) throws InterruptedException {

    }

    public long getMessageQueueSize() {
        return 0;
    }
}
