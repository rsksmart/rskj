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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.HashMap;

/**
 * This class is necessary until https://github.com/FasterXML/jackson-databind/issues/2467 is integrated.
 * It is expected to be included in Jackson 2.10 version.
 */
public class EthSubscribeParamsDeserializer extends JsonDeserializer {

    private final HashMap<String, Class<? extends EthSubscribeParams>> subscriptionTypes;

    public EthSubscribeParamsDeserializer() {
        this.subscriptionTypes = new HashMap<>();
        this.subscriptionTypes.put("newHeads", EthSubscribeNewHeadsParams.class);
        this.subscriptionTypes.put("logs", EthSubscribeLogsParams.class);
        this.subscriptionTypes.put("newPendingTransactions", EthSubscribePendingTransactionsParams.class);
        this.subscriptionTypes.put("syncing", EthSubscribeSyncParams.class);
    }

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
        String subscriptionType = p.getText();
        Class<? extends EthSubscribeParams> subscriptionTypeClass = subscriptionTypes.get(subscriptionType);
        p.nextToken();
        EthSubscribeParams params;
        if (p.isExpectedStartObjectToken()) {
            params = p.readValueAs(subscriptionTypeClass);
            p.nextToken();
        } else {
            try {
                params = subscriptionTypeClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                return ctxt.handleInstantiationProblem(
                        subscriptionTypeClass,
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
