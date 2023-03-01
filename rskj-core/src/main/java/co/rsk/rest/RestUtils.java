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
package co.rsk.rest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

public class RestUtils {

    private static final String DEFAULT_CONTENT_TYPE = "text/plain;charset=UTF-8";
    private static final HttpVersion DEFAULT_HTTP_VERSION = HttpVersion.HTTP_1_1;
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private static final String NOT_FOUND_MESSAGE = "Not Found";

    private RestUtils() {}

    public static DefaultFullHttpResponse createResponse(String content) {
        return createResponse(content, HttpResponseStatus.OK);
    }

    public static DefaultFullHttpResponse createResponse(String content,
                                                         HttpResponseStatus httpResponseStatus) {
        return createResponse(DEFAULT_HTTP_VERSION,
                httpResponseStatus,
                content,
                DEFAULT_CHARSET,
                DEFAULT_CONTENT_TYPE);
    }

    public static DefaultFullHttpResponse createResponse(HttpVersion httpVersion,
                                                         HttpResponseStatus httpResponseStatus,
                                                         String content,
                                                         Charset charset,
                                                         String contentType) {
        ByteBuf bufContent = Unpooled.copiedBuffer(content, charset);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(httpVersion,
                httpResponseStatus,
                bufContent);

        response.headers().set(CONTENT_TYPE, contentType);
        response.headers().set(CONTENT_LENGTH, bufContent.readableBytes());

        return response;
    }

    public static DefaultFullHttpResponse createNotFoundResponse() {
        return createResponse(NOT_FOUND_MESSAGE, HttpResponseStatus.NOT_FOUND);
    }

}
