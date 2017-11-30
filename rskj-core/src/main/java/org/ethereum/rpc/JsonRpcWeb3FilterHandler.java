package org.ethereum.rpc;

import co.rsk.rpc.OriginValidator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

/**
 * Created by ajlopez on 18/10/2017.
 */

@ChannelHandler.Sharable
public class JsonRpcWeb3FilterHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger("jsonrpc");

    private OriginValidator originValidator;

    public JsonRpcWeb3FilterHandler(String corsOrigins) {
        this.originValidator = new OriginValidator(corsOrigins);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        HttpMethod httpMethod = request.getMethod();
        HttpResponse response;

        if (HttpMethod.POST.equals(httpMethod)) {
            HttpHeaders headers = request.headers();

            String contentType = headers.get(HttpHeaders.Names.CONTENT_TYPE);
            String origin = headers.get(HttpHeaders.Names.ORIGIN);
            String referer = headers.get(HttpHeaders.Names.REFERER);

            if (!"application/json".equals(contentType) && !"application/json-rpc".equals(contentType)) {
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
}
