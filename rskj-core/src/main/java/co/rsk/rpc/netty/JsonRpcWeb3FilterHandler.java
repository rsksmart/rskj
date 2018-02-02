package co.rsk.rpc.netty;

import co.rsk.rpc.OriginValidator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.ethereum.rpc.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

/**
 * Created by ajlopez on 18/10/2017.
 */

@ChannelHandler.Sharable
public class JsonRpcWeb3FilterHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger("jsonrpc");
    private final InetAddress rpcHost;

    private OriginValidator originValidator;

    public JsonRpcWeb3FilterHandler(String corsDomains, InetAddress rpcHost) {
        this.originValidator = new OriginValidator(corsDomains);
        this.rpcHost = rpcHost;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        HttpMethod httpMethod = request.getMethod();
        HttpResponse response;

        HttpHeaders headers = request.headers();

        String host = headers.get(HttpHeaders.Names.HOST);
        if (!isLocalIpAddress(InetAddress.getByName(host)) &&
                !host.equals(rpcHost.getHostName())) {
            LOGGER.error("Invalid host");
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        if (HttpMethod.POST.equals(httpMethod)) {

            String mimeType = HttpUtils.getMimeType(headers.get(HttpHeaders.Names.CONTENT_TYPE));
            String origin = headers.get(HttpHeaders.Names.ORIGIN);
            String referer = headers.get(HttpHeaders.Names.REFERER);

            if (!"application/json".equals(mimeType) && !"application/json-rpc".equals(mimeType)) {
                LOGGER.error("Unsupported content type");
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
            } else if (origin != null && !this.originValidator.isValidOrigin(origin)) {
                LOGGER.error("Invalid origin");
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
            } else if (referer != null && !this.originValidator.isValidReferer(referer)) {
                LOGGER.error("Invalid referer");
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
            }
            else {
                ctx.fireChannelRead(request);
                return;
            }
        } else {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_IMPLEMENTED);
        }

        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    private boolean isLocalIpAddress(final InetAddress address) {
        // Check if the address is a valid special local or loop back
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
            return true;
        }
        // Check if the address is defined on any interface
        try {
            return NetworkInterface.getByInetAddress(address) != null;
        } catch (SocketException se) {
            return false;
        }
    }

}
