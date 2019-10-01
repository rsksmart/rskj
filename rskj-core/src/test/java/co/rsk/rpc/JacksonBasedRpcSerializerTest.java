/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.rpc;

import co.rsk.jsonrpc.JsonRpcBooleanResult;
import co.rsk.jsonrpc.JsonRpcRequest;
import co.rsk.jsonrpc.JsonRpcResultOrError;
import co.rsk.rpc.modules.RskJsonRpcRequestParams;
import co.rsk.rpc.modules.Web3Api;
import co.rsk.rpc.netty.JsonRpcRequestHandler;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JacksonBasedRpcSerializerTest {
    /**
     * This test {@link JacksonBasedRpcSerializer} extensibility and shows how it could be used at a higher level
     * (such as for supporting fed methods in {@link JsonRpcRequestHandler}) through dynamic casts.
     */
    @Test
    public void providesExtensibilityForNewRpcMethods() throws IOException {
        JacksonBasedRpcSerializer serializer = new JacksonBasedRpcSerializer(new NamedType(MyMethodParams.class, "my_method"));
        String message = "{\"jsonrpc\":\"2.0\",\"id\":100,\"method\":\"my_method\",\"params\":[\"42\"]}";
        JsonRpcRequest<RskJsonRpcRequestParams> request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(request.getMethod(), is("my_method"));
        assertThat(request.getParams().resolve(null, visitor()), is(new JsonRpcBooleanResult(true)));

        assertThat(request.getParams(), instanceOf(MyMethodParams.class));
        MyMethodParams myMethodParams = (MyMethodParams) request.getParams();
        assertThat(myMethodParams.getTheAnswer(), is(42));
    }

    private CompositeRskJsonRpcRequestParamsVisitor visitor() {
        MyRskJsonRpcRequestParamsVisitor myVisitor = (a, b) -> new JsonRpcBooleanResult(true);
        CompositeRskJsonRpcRequestParamsVisitor visitor = mock(CompositeRskJsonRpcRequestParamsVisitor.class);
        when(visitor.myVisitor()).thenReturn(myVisitor);
        return visitor;
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"theAnswer"})
    private static class MyMethodParams implements MyRskJsonRpcRequestParams {
        private final int theAnswer;

        @JsonCreator
        public MyMethodParams(@JsonProperty("theAnswer") int theAnswer) {
            this.theAnswer = theAnswer;
        }

        public int getTheAnswer() {
            return theAnswer;
        }

        @Override
        public JsonRpcResultOrError resolve(ChannelHandlerContext ctx, MyRskJsonRpcRequestParamsVisitor visitor) {
            return visitor.visit(ctx, this);
        }
    }

    /**
     * Params classes of the new API must implement this interface in order to safely access the new API visitor.
     * We sacrifice a little type-safety by casting runtime, but it is completely hidden from the API implementors.
     */
    private interface MyRskJsonRpcRequestParams extends RskJsonRpcRequestParams {
        @Override
        default JsonRpcResultOrError resolve(ChannelHandlerContext ctx, Web3Api api) {
            if (!(api instanceof CompositeRskJsonRpcRequestParamsVisitor)) {
                throw new IllegalArgumentException();
            }

            CompositeRskJsonRpcRequestParamsVisitor compVisitor = (CompositeRskJsonRpcRequestParamsVisitor) api;
            return resolve(ctx, compVisitor.myVisitor());
        }

        JsonRpcResultOrError resolve(ChannelHandlerContext ctx, MyRskJsonRpcRequestParamsVisitor visitor);
    }

    /**
     * This visitor is the link between the standard API (it extends {@link Web3Api}) and the new
     * API (it returns a visitor that is completely decoupled from the standard API).
     */
    private interface CompositeRskJsonRpcRequestParamsVisitor extends Web3Api {
        MyRskJsonRpcRequestParamsVisitor myVisitor();
    }

    /**
     * This visitor would know how to implement the different methods of the new API that extends the standard one.
     * It only knows about the new problem domain, in this case {@link MyMethodParams}.
     */
    private interface MyRskJsonRpcRequestParamsVisitor {
        JsonRpcResultOrError visit(ChannelHandlerContext ctx, MyMethodParams request);
    }
}