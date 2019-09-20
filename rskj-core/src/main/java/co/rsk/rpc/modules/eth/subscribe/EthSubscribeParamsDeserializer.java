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

package co.rsk.rpc.modules.eth.subscribe;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class EthSubscribeParamsDeserializer extends JsonDeserializer {
    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (!p.isExpectedStartArrayToken()) {
            return ctxt.handleUnexpectedToken(
                    EthSubscribeParams.class,
                    p.currentToken(),
                    p,
                    "eth_subscribe parameters are expected to be arrays"
            );
        }
        p.nextToken(); // skip '['
        EthSubscribeTypes subscriptionType = p.readValueAs(EthSubscribeTypes.class);
        p.nextToken();
        EthSubscribeParams params;
        if (p.isExpectedStartObjectToken()) {
            params = p.readValueAs(subscriptionType.requestClass());
            p.nextToken();
        } else {
            try {
                params = subscriptionType.requestClass().newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                return ctxt.handleInstantiationProblem(
                        subscriptionType.requestClass(),
                        null,
                        e
                );
            }
        }
        if (p.currentToken() != JsonToken.END_ARRAY) {
            return ctxt.handleUnexpectedToken(
                    EthSubscribeParams.class,
                    p.currentToken(),
                    p,
                    "eth_subscribe can only have one object to configure subscription"
            );
        }
        return params;
    }
}
