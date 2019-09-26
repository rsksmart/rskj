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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class EthSubscribeRequestTest {
    private JsonRpcSerializer serializer = new JacksonBasedRpcSerializer();

    @Test
    public void deserializeNewHeads() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"newHeads\"]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        validateParams(request, EthSubscribeNewHeadsParams.class);
    }

    @Test
    public void deserializeLogsWithEmptyConfig() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\", {}]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        EthSubscribeLogsParams logsParams = validateParams(request, EthSubscribeLogsParams.class);

        assertThat(logsParams.getAddress(), is(nullValue()));
        assertThat(logsParams.getTopics(), is(arrayWithSize(0)));
    }

    @Test
    public void deserializeLogsWithoutConfig() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\"]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        EthSubscribeLogsParams logsParams = validateParams(request, EthSubscribeLogsParams.class);

        assertThat(logsParams.getAddress(), is(nullValue()));
        assertThat(logsParams.getTopics(), is(arrayWithSize(0)));
    }

    @Test
    public void deserializeLogs() throws IOException {
        String logAddress = "0x3e1127bf1a673d378a8570f7a79cea4f10e20489";
        String logTopic = "0x2809c7e17bf978fbc7194c0a694b638c4215e9140cacc6c38ca36010b45697df";
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\", {\"address\":\"" + logAddress + "\",\"topics\":[\"" + logTopic + "\"]}]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        EthSubscribeLogsParams logsParams = validateParams(request, EthSubscribeLogsParams.class);

        assertThat(logsParams.getAddress(), is(logAddress));
        assertThat(logsParams.getTopics(), is(arrayWithSize(1)));
        assertThat(logsParams.getTopics(), hasItemInArray(logTopic));
    }

    @Test(expected = JsonMappingException.class)
    public void allowOnlyASingleConfiguration() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\", {}, {}]}";
        serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );
    }

    private <T extends EthSubscribeParams> T validateParams(RskJsonRpcRequest request, Class<T> paramsClass) {
        assertThat(request, instanceOf(EthSubscribeRequest.class));
        EthSubscribeRequest subscribeRequest = (EthSubscribeRequest) request;
        EthSubscribeParams params = subscribeRequest.getParams();
        assertThat(params, instanceOf(paramsClass));
        return paramsClass.cast(params);
    }
}