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

import co.rsk.rest.modules.RestModule;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestServerDispatcherTest {

    private static class DummyTestModule extends RestModule {

        protected DummyTestModule(String uri, boolean active) {
            super(uri, active);
        }

        @Override
        public DefaultFullHttpResponse processRequest(String uri, HttpMethod request) {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("test", StandardCharsets.UTF_8));
        }

    }

    private RestServerDispatcher restServerDispatcher;

    @BeforeEach
    public void setup() {
        List<RestModule> moduleList = new ArrayList<>();

        DummyTestModule module1 = new DummyTestModule("test.url.active", true);
        DummyTestModule module2 = new DummyTestModule("test.url.inactive", false);

        moduleList.add(module1);
        moduleList.add(module2);

        restServerDispatcher = new RestServerDispatcher(moduleList);
    }

    @Test
    void testConstructor_nullModuleList_throwsException() {
        try {
            new RestServerDispatcher(null);
            fail("Null Pointer Exception should be thrown");
        } catch (NullPointerException npe) {
            assertEquals("Module List can not be null", npe.getMessage());
        }
    }

    @Test
    void testDispatch_unsupportedUri_returnsNotFound() throws URISyntaxException {
        // Given
        String uri = "/unsupported.uri/does.not.exists";
        HttpRequest requestMock = mock(HttpRequest.class);
        doReturn(uri).when(requestMock).uri();

        // When
        DefaultFullHttpResponse response = restServerDispatcher.dispatch(requestMock);

        // Then
        assertNotNull(response);
        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
        assertEquals(Unpooled.copiedBuffer("Not Found", StandardCharsets.UTF_8),
                response.content());
    }

    @Test
    void testDispatch_supportedUri_inactiveModule_returnsNotFound() throws URISyntaxException {
        // Given
        String uri = "test.url.inactive";
        HttpRequest requestMock = mock(HttpRequest.class);
        doReturn(uri).when(requestMock).uri();

        // When
        DefaultFullHttpResponse response = restServerDispatcher.dispatch(requestMock);

        // Then
        assertNotNull(response);
        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
        assertEquals(Unpooled.copiedBuffer("Not Found", StandardCharsets.UTF_8),
                response.content());
    }

    @Test
    void testDispatch_supportedUri_activeModule_executesAsExpected() throws URISyntaxException {
        // Given
        String uri = "test.url.active";
        HttpRequest requestMock = mock(HttpRequest.class);
        doReturn(uri).when(requestMock).uri();

        // When
        DefaultFullHttpResponse response = restServerDispatcher.dispatch(requestMock);

        // Then
        assertNotNull(response);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(Unpooled.copiedBuffer("test", StandardCharsets.UTF_8),
                response.content());
    }

}
