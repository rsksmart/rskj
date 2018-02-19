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

package co.rsk.rpc.modules.txpool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TxPoolModuleImpl implements TxPoolModule {

    private final JsonNodeFactory jsonNodeFactory;

    public TxPoolModuleImpl() {
        jsonNodeFactory = JsonNodeFactory.instance;
    }

    /**
     * This method should return 2 dictionaries containing pending and queued transactions
     * Each entry is an origin-address to a batch of scheduled transactions
     * These batches themselves are maps associating nonces with actual transactions.
     * When there are no transactions the answer would be
     * "{"pending": {}, "queued": {}}"
     */
    @Override
    public String content() {
        Map<String, JsonNode> txProps = new HashMap<>();
        txProps.put("pending", jsonNodeFactory.objectNode());
        txProps.put("queued", jsonNodeFactory.objectNode());
        JsonNode node = jsonNodeFactory.objectNode().setAll(txProps);
        return node.toString();
    }

    /**
     * This method should return 2 dictionaries containing pending and queued transactions
     * Each entry is an origin-address to a batch of scheduled transactions
     * These batches themselves are maps associating nonces with transactions summary strings.
     * When there are no transactions the answer would be
     * "{"pending": {}, "queued": {}}"
     */
    @Override
    public String inspect() {
        Map<String, JsonNode> txProps = new HashMap<>();
        txProps.put("pending", jsonNodeFactory.objectNode());
        txProps.put("queued", jsonNodeFactory.objectNode());
        JsonNode node = jsonNodeFactory.objectNode().setAll(txProps);
        return node.toString();
    }

    /**
     * This method should return 2 integers for pending and queued transactions
     * These value represents
     * the number of transactions currently pending for inclusion in the next block(s),
     * as well as the ones that are being scheduled for future execution only.
     * "{"pending": 0, "queued": 0}"
     */
    @Override
    public String status() {
        Map<String, JsonNode> txProps = new HashMap<>();
        txProps.put("pending", jsonNodeFactory.numberNode(0));
        txProps.put("queued", jsonNodeFactory.numberNode(0));
        JsonNode node = jsonNodeFactory.objectNode().setAll(txProps);
        return node.toString();
    }
}