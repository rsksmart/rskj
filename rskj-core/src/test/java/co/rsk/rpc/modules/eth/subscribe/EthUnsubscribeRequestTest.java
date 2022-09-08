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

import co.rsk.jsonrpc.JsonRpcVersion;
import co.rsk.rpc.JacksonBasedRpcSerializer;
import co.rsk.rpc.JsonRpcSerializer;
import co.rsk.rpc.modules.RskJsonRpcMethod;
import co.rsk.rpc.modules.RskJsonRpcRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class EthUnsubscribeRequestTest {
    private JsonRpcSerializer serializer = new JacksonBasedRpcSerializer();

    @Test
    public void deserializeUnsubscribe() throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":100,\"method\":\"eth_unsubscribe\",\"params\":[\"0x0204\"]}";
        ByteArrayInputStream bais = new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8));
        RskJsonRpcRequest request = serializer.deserializeRequest(bais);

        assertThat(request, instanceOf(EthUnsubscribeRequest.class));
        EthUnsubscribeRequest unsubscribeRequest = (EthUnsubscribeRequest) request;
        assertThat(unsubscribeRequest.getParams().getSubscriptionId(), is(new SubscriptionId("0x0204")));
    }

    @Test
    public void unsubscribe_withWrongParameter_thenThrowException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new EthUnsubscribeRequest(JsonRpcVersion.V2_0, RskJsonRpcMethod.ETH_SUBSCRIBE, "test", null));
    }

}
