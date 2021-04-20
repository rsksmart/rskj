package co.rsk.rpc.netty;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.WriteTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RskWebSocketServerProtocolHandler extends WebSocketServerProtocolHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RskWebSocketServerProtocolHandler.class);

    public RskWebSocketServerProtocolHandler(String websocketPath) {
        super(websocketPath);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if(cause instanceof WriteTimeoutException) {
            ctx.writeAndFlush(new CloseWebSocketFrame(1000, "Exceeded write timout")).addListener(ChannelFutureListener.CLOSE);
            LOGGER.error("Write timout exceeded, closing web socket channel", cause);
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }
}
