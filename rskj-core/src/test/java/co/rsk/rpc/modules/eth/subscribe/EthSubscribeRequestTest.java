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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.ethereum.rpc.Topic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonMappingException;

import co.rsk.core.RskAddress;
import co.rsk.jsonrpc.JsonRpcVersion;
import co.rsk.rpc.JacksonBasedRpcSerializer;
import co.rsk.rpc.JsonRpcSerializer;
import co.rsk.rpc.modules.RskJsonRpcMethod;
import co.rsk.rpc.modules.RskJsonRpcRequest;

public class EthSubscribeRequestTest {
    private JsonRpcSerializer serializer = new JacksonBasedRpcSerializer();

    @Test
    public void deserializeSync() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"syncing\"]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        validateParams(request, EthSubscribeSyncParams.class);
    }

    @Test
    public void deserializePendingTransactions() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"newPendingTransactions\"]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        validateParams(request, EthSubscribePendingTransactionsParams.class);
    }

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

        assertThat(logsParams.getAddresses(), is(arrayWithSize(0)));
        assertThat(logsParams.getTopics(), is(arrayWithSize(0)));
    }

    @Test
    public void deserializeLogsWithoutConfig() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\"]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        EthSubscribeLogsParams logsParams = validateParams(request, EthSubscribeLogsParams.class);

        assertThat(logsParams.getAddresses(), is(arrayWithSize(0)));
        assertThat(logsParams.getTopics(), is(arrayWithSize(0)));
    }

    @Test
    public void deserializeLogsSingleParameters() throws IOException {
        RskAddress logAddress = new RskAddress("0x3e1127bf1a673d378a8570f7a79cea4f10e20489");
        Topic logTopic = new Topic("0x2809c7e17bf978fbc7194c0a694b638c4215e9140cacc6c38ca36010b45697df");
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\", {\"address\":\"" + logAddress.toJsonString() + "\",\"topics\":\"" + logTopic.toJsonString() + "\"}]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        EthSubscribeLogsParams logsParams = validateParams(request, EthSubscribeLogsParams.class);

        assertThat(logsParams.getAddresses(), is(arrayWithSize(1)));
        assertThat(logsParams.getAddresses(), hasItemInArray(logAddress));
        assertThat(logsParams.getTopics(), is(arrayWithSize(1)));
        assertThat(logsParams.getTopics()[0], is(arrayWithSize(1)));
        assertThat(logsParams.getTopics()[0], hasItemInArray(logTopic));
    }

    @Test
    public void deserializeLogsParametersAsArrays() throws IOException {
        RskAddress logAddress = new RskAddress("0x3e1127bf1a673d378a8570f7a79cea4f10e20489");
        Topic logTopic = new Topic("0x2809c7e17bf978fbc7194c0a694b638c4215e9140cacc6c38ca36010b45697df");
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\", {\"address\":[\"" + logAddress.toJsonString() + "\"],\"topics\":[\"" + logTopic.toJsonString() + "\"]}]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        EthSubscribeLogsParams logsParams = validateParams(request, EthSubscribeLogsParams.class);

        assertThat(logsParams.getAddresses(), is(arrayWithSize(1)));
        assertThat(logsParams.getAddresses(), hasItemInArray(logAddress));
        assertThat(logsParams.getTopics(), is(arrayWithSize(1)));
        assertThat(logsParams.getTopics()[0], is(arrayWithSize(1)));
        assertThat(logsParams.getTopics()[0], hasItemInArray(logTopic));
    }

    @Test
    public void deserializeLogsNestedTopicArrays() throws IOException {
        RskAddress logAddress = new RskAddress("0x3e1127bf1a673d378a8570f7a79cea4f10e20489");
        Topic logTopic1 = new Topic("0x2809c7e17bf978fbc7194c0a694b638c4215e9140cacc6c38ca36010b45697df");
        Topic logTopic2 = new Topic("0x4c0a694b638c4215e9140b6f08ecb38c4215e9140b6f08ecbdc8ab6b8ef9b245");
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\", {\"address\":[\"" + logAddress.toJsonString() + "\"],\"topics\":[[\"" + logTopic1.toJsonString() + "\"], [\"" + logTopic2.toJsonString() + "\"]]}]}";
        RskJsonRpcRequest request = serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        );

        EthSubscribeLogsParams logsParams = validateParams(request, EthSubscribeLogsParams.class);

        assertThat(logsParams.getAddresses(), is(arrayWithSize(1)));
        assertThat(logsParams.getAddresses(), hasItemInArray(logAddress));
        assertThat(logsParams.getTopics(), is(arrayWithSize(2)));
        assertThat(logsParams.getTopics()[0], is(arrayWithSize(1)));
        assertThat(logsParams.getTopics()[0], hasItemInArray(logTopic1));
        assertThat(logsParams.getTopics()[1], is(arrayWithSize(1)));
        assertThat(logsParams.getTopics()[1], hasItemInArray(logTopic2));
    }

    @Test
    public void allowOnlyASingleConfiguration() {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":333,\"method\":\"eth_subscribe\",\"params\":[\"logs\", {}, {}]}";
        Assertions.assertThrows(JsonMappingException.class, () -> serializer.deserializeRequest(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))
        ));
    }

    private <T extends EthSubscribeParams> T validateParams(RskJsonRpcRequest request, Class<T> paramsClass) {
        assertThat(request, instanceOf(EthSubscribeRequest.class));
        EthSubscribeRequest subscribeRequest = (EthSubscribeRequest) request;
        EthSubscribeParams params = subscribeRequest.getParams();
        assertThat(params, instanceOf(paramsClass));
        return paramsClass.cast(params);
    }

    @Test
    public void subscribe_withWrongParameter_thenThrowException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new EthSubscribeRequest(JsonRpcVersion.V2_0, RskJsonRpcMethod.ETH_UNSUBSCRIBE, "test", null));
    }

}
