/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.rpc;

import co.rsk.rpc.JsonRpcFilterServer;
import co.rsk.rpc.ModuleDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.googlecode.jsonrpc4j.AnnotationsErrorResolver;
import com.googlecode.jsonrpc4j.DefaultErrorResolver;
import com.googlecode.jsonrpc4j.DefaultHttpStatusCodeProvider;
import com.googlecode.jsonrpc4j.MultipleErrorResolver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.ethereum.rpc.exception.RskErrorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ChannelHandler.Sharable
public class JsonRpcWeb3ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger("jsonrpc");
    private static final int JSON_RPC_SERVER_ERROR_HIGH_CODE = -32099;

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;
    private final JsonRpcFilterServer jsonRpcServer;

    public JsonRpcWeb3ServerHandler(Web3 service, List<ModuleDescription> filteredModules) {
        this.jsonRpcServer = new JsonRpcFilterServer(service, service.getClass(), filteredModules);
        jsonRpcServer.setErrorResolver(new MultipleErrorResolver(new RskErrorResolver(), AnnotationsErrorResolver.INSTANCE, DefaultErrorResolver.INSTANCE));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        HttpMethod httpMethod = request.getMethod();
        HttpResponse response;
        if (HttpMethod.POST.equals(httpMethod)) {
            response = processRequest(request);
        } else {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_IMPLEMENTED);
        }
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    private HttpResponse processRequest(FullHttpRequest request) throws JsonProcessingException {
        HttpResponse response;
        ByteBuf responseContent = Unpooled.buffer();
        HttpResponseStatus responseStatus = HttpResponseStatus.OK;
        try (ByteBufOutputStream os = new ByteBufOutputStream(responseContent);
             ByteBufInputStream is = new ByteBufInputStream(request.content().retain())){
            int result = jsonRpcServer.handleRequest(is, os);
            responseStatus = HttpResponseStatus.valueOf(DefaultHttpStatusCodeProvider.INSTANCE.getHttpStatusCode(result));
        } catch (Exception e) {
            LOGGER.error("Unexpected error", e);
            responseContent = buildErrorContent(JSON_RPC_SERVER_ERROR_HIGH_CODE, HttpResponseStatus.INTERNAL_SERVER_ERROR.reasonPhrase());
            responseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR;
        } finally {
            response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                responseStatus,
                responseContent
            );
        }
        return response;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Unexpected exception", cause);
        ctx.close();
    }

    private ByteBuf buildErrorContent(int errorCode, String errorMessage) throws JsonProcessingException {
        Map<String, JsonNode> errorProperties = new HashMap<>();
        errorProperties.put("code", jsonNodeFactory.numberNode(errorCode));
        errorProperties.put("message", jsonNodeFactory.textNode(errorMessage));
        JsonNode error = jsonNodeFactory.objectNode().set("error", jsonNodeFactory.objectNode().setAll(errorProperties));
        return Unpooled.wrappedBuffer(mapper.writeValueAsBytes(mapper.treeToValue(error, Object.class)));
    }
}
