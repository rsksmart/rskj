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

import co.rsk.jsonrpc.JsonRpcRequest;
import co.rsk.jsonrpc.JsonRpcRequestParams;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class JacksonBasedRpcSerializerTest {
    @Test
    public void providesExtensibilityBySupportingArbitraryJsonRpcRequestParams() throws IOException {
        JacksonBasedRpcSerializer serializer = new JacksonBasedRpcSerializer();
        String message = "{\"jsonrpc\":\"2.0\",\"id\":100,\"method\":\"my_method\",\"params\":[\"42\"]}";
        JsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(request.getMethod(), is("my_method"));
        MyMethodParams params = serializer.deserializeRequestParams(request, MyMethodParams.class);
        assertThat(params.getTheAnswer(), is(42));
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"theAnswer"})
    private static class MyMethodParams implements JsonRpcRequestParams {
        private final int theAnswer;

        @JsonCreator
        public MyMethodParams(@JsonProperty("theAnswer") int theAnswer) {
            this.theAnswer = theAnswer;
        }

        public int getTheAnswer() {
            return theAnswer;
        }
    }
}