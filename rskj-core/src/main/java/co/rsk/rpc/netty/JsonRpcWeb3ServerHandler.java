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

package co.rsk.rpc.netty;

import co.rsk.rpc.JsonRpcMethodFilter;
import co.rsk.rpc.JsonRpcRequestValidatorInterceptor;
import co.rsk.rpc.ModuleDescription;
import co.rsk.util.JacksonParserUtil;
import co.rsk.rpc.exception.JsonRpcRequestPayloadException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.annotations.VisibleForTesting;
import com.googlecode.jsonrpc4j.*;
import io.netty.buffer.*;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.RskErrorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ChannelHandler.Sharable
public class JsonRpcWeb3ServerHandler extends SimpleChannelInboundHandler<ByteBufHolder> {

    private static final Logger LOGGER = LoggerFactory.getLogger("jsonrpc");

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;
    private final JsonRpcBasicServer jsonRpcServer;

    @VisibleForTesting
    JsonRpcWeb3ServerHandler(JsonRpcBasicServer jsonRpcServer) {
        this.jsonRpcServer = jsonRpcServer;
    }

    public JsonRpcWeb3ServerHandler(Web3 service, List<ModuleDescription> filteredModules, Integer maxBatchRequestsSize) {
        this.jsonRpcServer = new JsonRpcBasicServer(service, service.getClass());
        List<JsonRpcInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new JsonRpcRequestValidatorInterceptor(maxBatchRequestsSize));
        jsonRpcServer.setInterceptorList(interceptors);
        jsonRpcServer.setRequestInterceptor(new JsonRpcMethodFilter(filteredModules));
        jsonRpcServer.setErrorResolver(new MultipleErrorResolver(new RskErrorResolver(), AnnotationsErrorResolver.INSTANCE, DefaultErrorResolver.INSTANCE));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBufHolder request) throws Exception {
        ByteBuf responseContent = Unpooled.buffer();
        int responseCode;
        try (ByteBufOutputStream os = new ByteBufOutputStream(responseContent);
             ByteBufInputStream is = new ByteBufInputStream(request.content().retain())) {

            responseCode = jsonRpcServer.handleRequest(is, os);
        } catch (JsonRpcRequestPayloadException e) {
            String invalidReqMsg = "Invalid request";
            LOGGER.error(invalidReqMsg, e);
            responseContent = buildErrorContent(ErrorResolver.JsonError.INVALID_REQUEST.code, e.getMessage());
            responseCode = ErrorResolver.JsonError.INVALID_REQUEST.code;
        } catch (StackOverflowError e) {
            String stackOverflowErrorMsg = "Invalid request";
            LOGGER.error(stackOverflowErrorMsg, e);
            int errorCode = ErrorResolver.JsonError.INVALID_REQUEST.code;
            responseContent = buildErrorContent(errorCode, stackOverflowErrorMsg);
            responseCode = errorCode;
        } catch (Exception e) {
            String unexpectedErrorMsg = "Unexpected error";
            LOGGER.error(unexpectedErrorMsg, e);
            int errorCode = ErrorResolver.JsonError.CUSTOM_SERVER_ERROR_LOWER;
            responseContent = buildErrorContent(errorCode, unexpectedErrorMsg);
            responseCode = errorCode;
        }

        ctx.fireChannelRead(new Web3Result(
                responseContent,
                responseCode
        ));
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
        Object object = JacksonParserUtil.treeToValue(mapper, error, Object.class);

        return Unpooled.wrappedBuffer(mapper.writeValueAsBytes(object));
    }
}
