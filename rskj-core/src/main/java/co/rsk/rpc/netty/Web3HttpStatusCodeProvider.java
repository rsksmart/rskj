package co.rsk.rpc.netty;

import com.googlecode.jsonrpc4j.HttpStatusCodeProvider;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError.*;

public class Web3HttpStatusCodeProvider implements HttpStatusCodeProvider {

    private static final List<Integer> BAD_REQUEST_JSON_ERRORS = Stream.of(METHOD_PARAMS_INVALID, INVALID_REQUEST, PARSE_ERROR)
            .map(jsonError -> jsonError.code)
            .collect(Collectors.toList());

    /**
     * Only invalid JSON-RPC requests (e.g. a malformed JSON) qualify for a Bad Request HTTP status.
     * In any other case, the HTTP protocol remains entirely independent from JSON-RPC (since its 2.0 version).
     * Reference: https://www.jsonrpc.org/
     */
    @Override
    public int getHttpStatusCode(int resultCode) {
        return isBadRequestResultCode(resultCode) ?
                HttpResponseStatus.BAD_REQUEST.code() :
                HttpResponseStatus.OK.code();
    }

    private static boolean isBadRequestResultCode(int resultCode) {
        return BAD_REQUEST_JSON_ERRORS.contains(resultCode);
    }

    /**
     * The default implementation wrongly assumes that an HTTP status code is mapped to a single JSON RPC error - this
     * is not always the case. Either way, this is not used in the current project.
     */
    @Override
    public Integer getJsonRpcCode(int httpStatusCode) {
        throw new UnsupportedOperationException("Cannot possibly determine a single JSON RPC from an HTTP status code");
    }

}
