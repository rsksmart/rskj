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

package co.rsk.rpc.netty;

import co.rsk.jsonrpc.JsonRpcError;
import co.rsk.rpc.exception.JsonRpcResponseLimitError;
import co.rsk.rpc.json.JsonResponseSizeLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class ResponseSizeLimitContext implements AutoCloseable {

    private static final ThreadLocal<ResponseSizeLimitContext> accumulatedResponseSize = new ThreadLocal<>();

    private int size = 0;
    private final int limit;

    private ResponseSizeLimitContext(int limit) {
        this.limit = limit;
    }

    private void add(int size) {

        this.size += size;
        if (this.size > limit) {
            throw new JsonRpcResponseLimitError(limit);
        }
    }

    private void add(JsonNode response) {
        if (limit <= 0) {
            return;
        }

        if (response != null) {
            add(JsonResponseSizeLimiter.getSizeInBytesWithLimit(response, limit));
        }
    }

    @Override
    public void close() throws Exception {
        ResponseSizeLimitContext ctx = accumulatedResponseSize.get();
        if (ctx == this) {
            accumulatedResponseSize.remove();
        }
    }

    public static ResponseSizeLimitContext createResponseSizeContext(int limit) {
        if (limit <= 0) {
            return createEmptyContext();
        }
        ResponseSizeLimitContext existingContext = accumulatedResponseSize.get();
        if (existingContext != null) {
            throw new RskJsonRpcRequestException(JsonRpcError.INTERNAL_ERROR, "ResponseSizeLimitContext already exists");
        }
        ResponseSizeLimitContext ctx = new ResponseSizeLimitContext(limit);
        accumulatedResponseSize.set(ctx);
        return ctx;
    }

    private static ResponseSizeLimitContext createEmptyContext() {
        return new ResponseSizeLimitContext(0) {
            @Override
            public void close() throws Exception {
                // do nothing
            }

        };
    }

    public static void addResponse(JsonNode response) {
        ResponseSizeLimitContext ctx = accumulatedResponseSize.get();
        if (ctx != null) {
            ctx.add(response);
        }
    }


}
