/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.net.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("Use this class to check the behavior of the SimpleHttpClient class. Disabled by default to not run in CI.")
public class SimpleHttpClientIntTest {
    private static SimpleHttpTestServer server;
    private static String baseUrl;
    private static int port;


    @BeforeAll
    public static void setUp() throws Exception {
        port = generateRandomPort();
        server = new SimpleHttpTestServer();
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.start(port);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        // Give the server a second to ensure it's up and running
        Thread.sleep(1000);
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    public static void tearDown() {
        server.stop();
    }

    @Test
    void testGetRequest() throws HttpException {
        SimpleHttpClient client = new SimpleHttpClient(25000); // 5 seconds timeout
        String response = client.doGet(baseUrl + SimpleHttpTestServer.HELLO_PATH);
        assertEquals(SimpleHttpTestServer.HELLO_RESPONSE, response);
    }

    @Test
    void testNotFoundRequest() {
        SimpleHttpClient client = new SimpleHttpClient(25000);
        HttpException exception = assertThrows(HttpException.class, () -> {
            client.doGet(baseUrl + SimpleHttpTestServer.NOT_FOUND_PATH);
        });
        assertEquals("Http request failed with code :  404 - Not Found", exception.getMessage());
    }

    @Test
    void testInternalServerError() {
        SimpleHttpClient client = new SimpleHttpClient(25000);
        HttpException exception = assertThrows(HttpException.class, () -> {
            client.doGet(baseUrl + SimpleHttpTestServer.ERROR_PATH);
        });
        assertEquals("Http request failed with code :  500 - Internal Server Error", exception.getMessage());
    }

    @Test
    void testInvalidUrl() {
        SimpleHttpClient client = new SimpleHttpClient(25000);
        assertThrows(HttpException.class, () -> {
            client.doGet("invalid_url");
        });
    }

    @Test
    void testTimeout() {
        SimpleHttpClient client = new SimpleHttpClient(1); // set timeout to 1 ms
        assertThrows(HttpException.class, () -> {
            client.doGet(baseUrl + SimpleHttpTestServer.SLOW_PATH);
        });
    }

    private static int generateRandomPort() {
        Random random = new Random();
        int minPort = 49152;
        int maxPort = 65535;
        return random.nextInt((maxPort - minPort) + 1) + minPort;
    }
}
