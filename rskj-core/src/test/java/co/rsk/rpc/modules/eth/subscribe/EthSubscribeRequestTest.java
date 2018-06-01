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
package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.rpc.JacksonBasedRpcSerializer;
import co.rsk.rpc.JsonRpcSerializer;
import co.rsk.rpc.modules.RskJsonRpcRequest;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class EthSubscribeRequestTest {
    private JsonRpcSerializer serializer = new JacksonBasedRpcSerializer();

    @Test
    public void deserializeNewHeads() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"newHeads\"]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(request, instanceOf(EthSubscribeRequest.class));
        EthSubscribeRequest subscribeRequest = (EthSubscribeRequest) request;
        assertThat(subscribeRequest.getParams().getSubscription(), is(EthSubscribeTypes.NEW_HEADS));
    }

    @Test(expected = JsonMappingException.class)
    public void logsIsUnsupported() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\"]}";
        serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );
    }
}