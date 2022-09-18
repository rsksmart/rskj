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

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import co.rsk.jsonrpc.JsonRpcBooleanResult;
import co.rsk.jsonrpc.JsonRpcError;
import co.rsk.jsonrpc.JsonRpcIdentifiableMessage;
import co.rsk.jsonrpc.JsonRpcResultOrError;
import co.rsk.rpc.EthSubscriptionNotificationEmitter;
import co.rsk.rpc.modules.RskJsonRpcRequest;
import co.rsk.rpc.modules.RskJsonRpcRequestVisitor;
import co.rsk.rpc.modules.eth.subscribe.EthSubscribeRequest;
import co.rsk.rpc.modules.eth.subscribe.EthUnsubscribeRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * This handler decodes inbound messages and dispatches valid JSON-RPC requests.
 *
 * Note that we split JSON-RPC handling in two because jsonrpc4j wasn't able to handle the PUB-SUB model.
 * Eventually, we might want to implement all methods in this style and remove jsonrpc4j.
 *
 * We make this object Sharable so it can be instanced once in the netty pipeline
 * and since all objects used by this object are thread safe,
 */
@Sharable
public class RskWebSocketJsonRpcHandler extends SimpleChannelInboundHandler<ByteBufHolder> implements RskJsonRpcRequestVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RskWebSocketJsonRpcHandler.class);

    private static final String ID = "id";

    private final EthSubscriptionNotificationEmitter emitter;

    private final ObjectMapper mapper = new ObjectMapper();

    private final RskWebSocketJsonParameterValidator parameterValidator = new RskWebSocketJsonParameterValidator();

    public RskWebSocketJsonRpcHandler(EthSubscriptionNotificationEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBufHolder msg) {
        ByteBuf content = msg.copy().content();

        try (ByteBufInputStream source = new ByteBufInputStream(content)) {

            final JsonNode jsonNodeRequest = mapper.readTree(source);

            if (jsonNodeRequest.isEmpty()) {
                throw JsonMappingException.from(jsonNodeRequest.traverse(), "Request is empty");
            }

            RskWebSocketJsonParameterValidator.Result validationResult = parameterValidator.validate(jsonNodeRequest);

            RskJsonRpcRequest request = mapper.treeToValue(jsonNodeRequest, RskJsonRpcRequest.class);

            if (request == null) {
                throw new NullPointerException();
            }

            JsonRpcResultOrError resultOrError = null;

            if (validationResult.isValid()) {
                // TODO(mc) we should support the ModuleDescription method filters
                resultOrError = request.accept(this, ctx);
            } else {
                resultOrError = new JsonRpcError(JsonRpcError.INVALID_PARAMS, validationResult.getMessage());
            }

            JsonRpcIdentifiableMessage response = resultOrError.responseFor(request.getId());

            ctx.writeAndFlush(new TextWebSocketFrame(getJsonWithTypedId(jsonNodeRequest, response)));

            return;

        } catch (IOException e) {
            LOGGER.trace("Not a known or valid JsonRpcRequest", e);

            // We need to release this resource, netty only takes care about 'ByteBufHolder msg'
            content.release(content.refCnt());
        }

        // delegate to the next handler if the message can't be matched to a known JSON-RPC request
        ctx.fireChannelRead(msg);
    }

    /**
     * Uses the ID of the request to set the response so it can have the same type in json payload
     */
    private String getJsonWithTypedId(JsonNode jsonNodeRequest, JsonRpcIdentifiableMessage response) throws JsonProcessingException {

        // get the json representation of the response object
        JsonNode jsonNodeResponse = mapper.valueToTree(response);

        // set its ID with the the one that was provided in the request
        ((ObjectNode) jsonNodeResponse).set(ID, jsonNodeRequest.get(ID));

        // creates the string json payload
        return mapper.writeValueAsString(jsonNodeResponse);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        emitter.unsubscribe(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public JsonRpcResultOrError visit(EthUnsubscribeRequest request, ChannelHandlerContext ctx) {
        boolean unsubscribed = emitter.unsubscribe(request.getParams().getSubscriptionId());
        return new JsonRpcBooleanResult(unsubscribed);
    }

    @Override
    public JsonRpcResultOrError visit(EthSubscribeRequest request, ChannelHandlerContext ctx) {
        return request.getParams().accept(emitter, ctx.channel());
    }
}
