/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.rpc.exception.JsonRpcRequestPayloadException;
import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.jsonrpc4j.JsonRpcInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

public class JsonRpcRequestValidatorInterceptor implements JsonRpcInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcRequestValidatorInterceptor.class);
    private static final int MAX_JSON_REQUEST_DEPTH = 99;

    private final int maxBatchRequestsSize;

    public JsonRpcRequestValidatorInterceptor(int maxBatchRequestsSize) {
        this.maxBatchRequestsSize = maxBatchRequestsSize;
    }

    private JsonNode getMethod(JsonNode rootNode) {
        int depth = 0;
        JsonNode result = rootNode;

        while (depth < MAX_JSON_REQUEST_DEPTH && result.isArray() && !result.get(0).has("method")) {
            result = result.get(0);
            depth++;
        }

        return result;
    }

    private void validateRequest(JsonNode node) {
        JsonNode jsonRequest = getMethod(node);

        if (jsonRequest.isArray() && jsonRequest.size() > this.maxBatchRequestsSize) {
            String msg = String.format("Cannot dispatch batch requests. %s is the max number of supported batch requests", this.maxBatchRequestsSize);
            logger.warn(msg);

            throw new JsonRpcRequestPayloadException(msg);
        }
    }

    @Override
    public void preHandleJson(JsonNode json) {
        this.validateRequest(json);
    }

    @Override
    public void preHandle(Object target, Method method, List<JsonNode> params) {
        System.out.println("");
        // Ignoring this function as we don't need anything to perform here.
    }

    @Override
    public void postHandle(Object target, Method method, List<JsonNode> params, JsonNode result) {
        // Ignoring this function as we don't need anything to perform here.
    }

    @Override
    public void postHandleJson(JsonNode json) {
        // Ignoring this function as we don't need anything to perform here.
    }
}
