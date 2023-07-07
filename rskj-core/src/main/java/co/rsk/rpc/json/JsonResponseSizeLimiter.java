/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.rpc.json;

import co.rsk.rpc.exception.JsonRpcResponseLimitError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.Iterator;
import java.util.Map;

public class JsonResponseSizeLimiter {
    private static final int BRACKETS_SIZE_IN_BYTES = 2;
    private static final int COMMA_SIZE = 1;
    private static final int JSON_FIELD_MISSING_SYMBOLS = 3;

    private int acc = 0;
    private final int max;
    private final JsonNode root;

    private JsonResponseSizeLimiter(int max, JsonNode root) {
        this.max = max;
        this.root = root;
    }

    public static int getSizeInBytesWithLimit(JsonNode node, int limit) {
        if (node == null) {
            return 0;
        }
        JsonResponseSizeLimiter limiter = new JsonResponseSizeLimiter(limit, node);
        return limiter.getJsonLength();
    }

    private int getJsonLength() {
        addLength(root);
        return acc;
    }

    private void addLength(JsonNode jsonNode) {
        if (jsonNode.isArray()) {
            handleArray((ArrayNode) jsonNode);
        }  else if (jsonNode.isObject()) {
            handleObject(jsonNode);
        } else if (jsonNode.isValueNode()) {
            handleValueNode(jsonNode);
        }
    }

    private void handleValueNode(JsonNode node) {
        sumAndCheck(getLength(node));
    }

    private void handleArray(ArrayNode arrayNode) {
        Iterator<JsonNode> elements = arrayNode.elements();
        int eleNo = elements.hasNext() ? -1 : 0;
        while (elements.hasNext()) {
            addLength(elements.next());
            eleNo++;
        }
        sumAndCheck(eleNo + BRACKETS_SIZE_IN_BYTES);
    }

    private void handleObject(JsonNode rootNode) {
        Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
        int fieldSeparatorCount = fields.hasNext() ? -1 : 0;
        while (fields.hasNext()) {
            fieldSeparatorCount++;
            handleField(fields.next());
        }
        sumAndCheck(BRACKETS_SIZE_IN_BYTES + (COMMA_SIZE * fieldSeparatorCount));
    }

    private void handleField(Map.Entry<String, JsonNode> field) {
        JsonNode value = field.getValue();
        if (value.isObject() || value.isArray()) {
            addLength(value);
            sumAndCheck(field.getKey().length() + JSON_FIELD_MISSING_SYMBOLS);
        } else {
            sumAndCheck(getLength(value) + field.getKey().length() + JSON_FIELD_MISSING_SYMBOLS);
        }
    }

    private void sumAndCheck(int value) {
        acc += value;
        if (acc > max) {
            throw new JsonRpcResponseLimitError(max);
        }
    }

    private int getLength(JsonNode node){
        if(node.isTextual()){
            //+2 is related wit the missing quotes
            return node.asText().length() + 2;
        }
        return node.asText().length();
    }
}
