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
package co.rsk.rest.modules;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckModuleTest {

    private HealthCheckModule healthCheckModule;

    @BeforeEach
    public void setup() {
        healthCheckModule = new HealthCheckModule("url.path", true);
    }

    @Test
    void testProcessRequest_getMethod_pingUri_returnsPongMessage() {
        // Given
        String url = "/health-check/ping";
        HttpMethod method = HttpMethod.GET;

        // When
        DefaultFullHttpResponse response = healthCheckModule.processRequest(url, method);

        // Then
        assertNotNull(response);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(Unpooled.copiedBuffer("pong", StandardCharsets.UTF_8),
                response.content());
    }

    @Test
    void testProcessRequest_unsupportedMethod_pingUri_returnsNull() {
        // Given
        String url = "/health-check/ping";
        HttpMethod method = HttpMethod.POST;

        // When
        DefaultFullHttpResponse response = healthCheckModule.processRequest(url, method);

        // Then
        assertNull(response);
    }

    @Test
    void testProcessRequest_unsupportedUri_returnsNull() {
        // Given
        String url = "/health-check/foo";

        // When
        DefaultFullHttpResponse response = healthCheckModule.processRequest(url, HttpMethod.GET);

        // Then
        assertNull(response);
    }

}
