package co.rsk.rpc.netty;

import com.googlecode.jsonrpc4j.HttpStatusCodeProvider;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Web3HttpStatusCodeProviderTest {

    private HttpStatusCodeProvider httpStatusCodeProvider;

    @BeforeEach
    void setup() throws Exception {
        httpStatusCodeProvider = new Web3HttpStatusCodeProvider();
    }

    @Test
    void getHttpStatusCodeReturnsExpectedHttpStatusCodes() {
        assertThatOkHttpStatusIsReturnedWhenExpected(httpStatusCodeProvider);
        assertThatBadRequestHttpStatusIsReturnedWhenExpected(httpStatusCodeProvider);
    }

    private void assertThatBadRequestHttpStatusIsReturnedWhenExpected(HttpStatusCodeProvider httpStatusCodeProvider) {
        assertReturnedHttpStatusCodeIsExpected(httpStatusCodeProvider, OK.code, HttpResponseStatus.OK.code());
        assertReturnedHttpStatusCodeIsExpected(httpStatusCodeProvider, METHOD_NOT_FOUND.code, HttpResponseStatus.OK.code());
        assertReturnedHttpStatusCodeIsExpected(httpStatusCodeProvider, INTERNAL_ERROR.code, HttpResponseStatus.OK.code());
        assertReturnedHttpStatusCodeIsExpected(httpStatusCodeProvider, ERROR_NOT_HANDLED.code, HttpResponseStatus.OK.code());
        assertReturnedHttpStatusCodeIsExpected(httpStatusCodeProvider, BULK_ERROR.code, HttpResponseStatus.OK.code());
    }

    private void assertThatOkHttpStatusIsReturnedWhenExpected(HttpStatusCodeProvider httpStatusCodeProvider) {
        assertReturnedHttpStatusCodeIsExpected(httpStatusCodeProvider, METHOD_PARAMS_INVALID.code, HttpResponseStatus.BAD_REQUEST.code());
        assertReturnedHttpStatusCodeIsExpected(httpStatusCodeProvider, INVALID_REQUEST.code, HttpResponseStatus.BAD_REQUEST.code());
        assertReturnedHttpStatusCodeIsExpected(httpStatusCodeProvider, PARSE_ERROR.code, HttpResponseStatus.BAD_REQUEST.code());
    }

    private void assertReturnedHttpStatusCodeIsExpected(HttpStatusCodeProvider httpStatusCodeProvider,
                                                        int jsonRpcResultCode, int expectedHttpStatusCode) {
        int httpStatusCode = httpStatusCodeProvider.getHttpStatusCode(jsonRpcResultCode);
        assertEquals(expectedHttpStatusCode, httpStatusCode);
    }

    @Test
    void getJsonRpcCodeThrowsUnsupportedOperationException() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> httpStatusCodeProvider.getJsonRpcCode(INVALID_REQUEST.code));
    }
}
