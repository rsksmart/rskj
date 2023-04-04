package co.rsk.rpc.netty;

import co.rsk.rpc.exception.JsonRpcResponseLimitError;
import co.rsk.rpc.json.JsonResponseSizeLimiter;
import com.fasterxml.jackson.databind.JsonNode;

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
        accumulatedResponseSize.remove();
    }

    public static ResponseSizeLimitContext createResponseSizeContext(int limit) {
        ResponseSizeLimitContext ctx = new ResponseSizeLimitContext(limit);
        accumulatedResponseSize.set(ctx);
        return ctx;
    }

    public static void addResponseSize(int size) {
        ResponseSizeLimitContext ctx = accumulatedResponseSize.get();
        if (ctx != null) {
            ctx.add(size);
        }
    }

    public static void addResponse(JsonNode response) {
        ResponseSizeLimitContext ctx = accumulatedResponseSize.get();
        if (ctx != null) {
            ctx.add(response);
        }
    }


}
