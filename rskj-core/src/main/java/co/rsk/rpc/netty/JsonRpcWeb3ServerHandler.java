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

import co.rsk.config.RskSystemProperties;
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
import com.sun.xml.ws.util.NoCloseInputStream;
import io.netty.buffer.*;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.RskErrorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError.INTERNAL_ERROR;

@ChannelHandler.Sharable
public class JsonRpcWeb3ServerHandler extends SimpleChannelInboundHandler<ByteBufHolder> {

    private static final Logger LOGGER = LoggerFactory.getLogger("jsonrpc");

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;
    private final JsonRpcBasicServer jsonRpcServer;
    private final TimeUnit timeoutUnit;
    private final int defaultTimeout;
    private final List<ModuleDescription> modules;

    @VisibleForTesting
    JsonRpcWeb3ServerHandler(JsonRpcBasicServer jsonRpcServer, RskSystemProperties rskSystemProperties) {
        this.jsonRpcServer = jsonRpcServer;

        this.timeoutUnit = rskSystemProperties.getRpcTimeoutUnit();
        this.defaultTimeout = rskSystemProperties.getRpcTimeout();
        this.modules = new ArrayList<>(rskSystemProperties.getRpcModules());
    }

    public JsonRpcWeb3ServerHandler(Web3 service, List<ModuleDescription> filteredModules, int maxBatchRequestsSize, RskSystemProperties rskSystemProperties) {
        this.jsonRpcServer = new JsonRpcBasicServer(service, service.getClass());
        List<JsonRpcInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new JsonRpcRequestValidatorInterceptor(maxBatchRequestsSize));
        jsonRpcServer.setInterceptorList(interceptors);
        jsonRpcServer.setRequestInterceptor(new JsonRpcMethodFilter(filteredModules));
        jsonRpcServer.setErrorResolver(new MultipleErrorResolver(new RskErrorResolver(), AnnotationsErrorResolver.INSTANCE, DefaultErrorResolver.INSTANCE));

        this.timeoutUnit = rskSystemProperties.getRpcTimeoutUnit();
        this.defaultTimeout = rskSystemProperties.getRpcTimeout();
        this.modules = new ArrayList<>(rskSystemProperties.getRpcModules());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBufHolder request) throws Exception {
        ByteBuf responseContent = Unpooled.buffer();
        int responseCode;
        try (ByteBufOutputStream os = new ByteBufOutputStream(responseContent);
             ByteBufInputStream is = new ByteBufInputStream(request.content().retain());
             InputStream localIs = new NoCloseInputStream(new ByteBufInputStream(request.content().copy()))) {

            responseCode = handleRequest(os, is, localIs);
        } catch (ExecTimeoutContext.TimeoutException e) {
            LOGGER.error(e.getMessage(), e);
            int errorCode = INTERNAL_ERROR.code;
            responseContent = buildErrorContent(errorCode, e.getMessage());
            responseCode = errorCode;
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

    private int handleRequest(ByteBufOutputStream os, ByteBufInputStream is, InputStream localIs) throws IOException {
        int responseCode;
        JsonNode node = mapper.readValue(localIs, JsonNode.class);

        String method = Optional.ofNullable(node.get("method")).map(JsonNode::asText).orElse("");

        if (method.isEmpty()) {
            return jsonRpcServer.handleRequest(is, os);
        }

        String[] methodParts = method.split("_");

        if (methodParts.length < 2) {
            return jsonRpcServer.handleRequest(is, os);
        }

        String moduleName = methodParts[0];
        String methodName = methodParts[1];

        Optional<ModuleDescription> optModule = modules.stream()
                .filter(m -> m.getName().equals(moduleName))
                .findFirst();

        int timeout = optModule
                .map(m -> m.getTimeout(methodName, defaultTimeout))
                .orElse(defaultTimeout);

        if (timeout <= 0) {
            responseCode = jsonRpcServer.handleRequest(is, os);
        } else {
            try (ExecTimeoutContext ignored = ExecTimeoutContext.create(timeout, timeoutUnit)) {
                responseCode = jsonRpcServer.handleRequest(is, os);
                ExecTimeoutContext.checkIfExpired();
            } catch (ExecTimeoutContext.TimeoutException e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }
        }

        return responseCode;
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
