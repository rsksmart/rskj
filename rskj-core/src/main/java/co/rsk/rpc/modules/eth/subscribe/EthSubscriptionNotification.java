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

import co.rsk.jsonrpc.JsonRpcMessage;
import co.rsk.jsonrpc.JsonRpcVersion;
import com.fasterxml.jackson.annotation.JsonInclude;

public class EthSubscriptionNotification extends JsonRpcMessage {

    private final EthSubscriptionParams params;

    public EthSubscriptionNotification(EthSubscriptionParams params) {
        super(JsonRpcVersion.V2_0);
        this.params = params;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getMethod() {
        return "eth_subscription";
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public EthSubscriptionParams getParams() {
        return params;
    }
}
