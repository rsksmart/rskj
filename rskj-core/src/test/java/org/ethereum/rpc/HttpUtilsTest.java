package org.ethereum.rpc;

import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HttpUtilsTest {

    @Test
    public void mimeTest() {

        final String SIMPLE_CONTENT_TYPE = "text/html";
        final String NORMAL_CONTENT_TYPE = "text/html; charset=utf-8";

        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders headers = message.headers();
        String contentType = HttpHeaders.Names.CONTENT_TYPE;
        Assertions.assertNull(HttpUtils.getMimeType(headers.get(contentType)));
        headers.set(contentType, "");
        Assertions.assertNull(HttpUtils.getMimeType(headers.get(contentType)));
        Assertions.assertNull(HttpUtils.getMimeType(""));
        headers.set(contentType, SIMPLE_CONTENT_TYPE);
        Assertions.assertEquals("text/html", HttpUtils.getMimeType(headers.get(contentType)));
        Assertions.assertEquals("text/html", HttpUtils.getMimeType(SIMPLE_CONTENT_TYPE));

        headers.set(contentType, NORMAL_CONTENT_TYPE);
        Assertions.assertEquals("text/html", HttpUtils.getMimeType(headers.get(contentType)));
        Assertions.assertEquals("text/html", HttpUtils.getMimeType(NORMAL_CONTENT_TYPE));

    }
}
