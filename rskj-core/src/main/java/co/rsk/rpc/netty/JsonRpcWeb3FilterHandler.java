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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 18/10/2017.
 */

@ChannelHandler.Sharable
public class JsonRpcWeb3FilterHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger("jsonrpc");
    private final List<String> rpcHost;
    private final InetAddress rpcAddress;

    private OriginValidator originValidator;

    public JsonRpcWeb3FilterHandler(String corsDomains, InetAddress rpcAddress, List<String> rpcHost) {
        this.originValidator = new OriginValidator(corsDomains);
        this.rpcHost = rpcHost;
        this.rpcAddress = rpcAddress;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        HttpResponse response;
        HttpMethod httpMethod = request.getMethod();
        HttpHeaders headers = request.headers();
        String hostHeader = headers.get(HttpHeaders.Names.HOST).split(":")[0];
        List<String> hosts = new ArrayList<>();

        if (isAcceptedAddress(rpcAddress)) {
            hosts.add(rpcAddress.getHostName());
            hosts.add(rpcAddress.getHostAddress());
        } else {
            for (String host : rpcHost) {
                try {
                    InetAddress hostAddress = InetAddress.getByName(host);
                    if (!hostAddress.isAnyLocalAddress()) {
                        hosts.add(hostAddress.getHostAddress());
                        hosts.add(hostAddress.getHostName());
                    } else {
                        logger.warn("Wildcard address is not allowed on rpc host property {}", hostAddress);
                    }
                } catch (UnknownHostException e) {
                    logger.info("Invalid Host defined on rpc.host", e);
                }
            }
        }

        if (!hosts.contains(hostHeader)) {
            logger.error("Invalid hostHeader");
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        if (HttpMethod.POST.equals(httpMethod)) {

            String mimeType = HttpUtils.getMimeType(headers.get(HttpHeaders.Names.CONTENT_TYPE));
            String origin = headers.get(HttpHeaders.Names.ORIGIN);
            String referer = headers.get(HttpHeaders.Names.REFERER);

                logger.error("Unsupported content type");
            if (!"application/json".equals(mimeType) && !"application/json-rpc".equals(mimeType)) {
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
            } else if (origin != null && !this.originValidator.isValidOrigin(origin)) {
                logger.error("Invalid origin");
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            } else if (referer != null && !this.originValidator.isValidReferer(referer)) {
                logger.error("Invalid referer");
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
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

    private boolean isAcceptedAddress(final InetAddress address) {
        // Check if the address is a valid special local or loop back
        if (address.isLoopbackAddress() ) {
            return true;
        }
        // Check if the address is defined on any interface
        try {
            return !address.isAnyLocalAddress() && NetworkInterface.getByInetAddress(address) != null;
        } catch (SocketException se) {
            return false;
        }
    }

}
