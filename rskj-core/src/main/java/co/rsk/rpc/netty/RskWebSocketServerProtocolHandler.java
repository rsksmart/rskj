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
    public static final String WRITE_TIMEOUT_REASON = "Exceeded write timout";
    public static final int NORMAL_CLOSE_WEBSOCKET_STATUS = 1000;

    public RskWebSocketServerProtocolHandler(String websocketPath, int maxFrameSize) {
        // there are no subprotocols nor extensions
        // Note: A port:host can only have one webSocketPath, and that's a Netty limitation.
        // For more information about
        // Netty limitations: https://stackoverflow.com/questions/8778806/how-to-handle-different-url-websocket-connections-in-netty/71757361#71757361
        super(websocketPath, null, false, maxFrameSize, false, true);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if(cause instanceof WriteTimeoutException) {
            ctx.writeAndFlush(new CloseWebSocketFrame(NORMAL_CLOSE_WEBSOCKET_STATUS, WRITE_TIMEOUT_REASON))
                    .addListener(ChannelFutureListener.CLOSE);
            LOGGER.error("Write timeout exceeded, closing web socket channel", cause);
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }
}
