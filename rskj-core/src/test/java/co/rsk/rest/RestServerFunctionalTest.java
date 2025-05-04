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

import co.rsk.rest.dto.RestModuleConfigDTO;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.awaitility.Durations.TEN_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class RestServerFunctionalTest {

    @Test
    void test_completeTrips() throws UnknownHostException {
        // Given

        InetAddress inetHost = InetAddress.getByName("localhost");
        int inetPort = 4447;
        RestModuleConfigDTO restModuleConfigDTO = new RestModuleConfigDTO(true);

        RestServer restServer = new RestServer(inetHost, inetPort, restModuleConfigDTO);

        OkHttpClient httpClient = new OkHttpClient();

        Thread thread = new Thread(restServer::start);

        try {

            // When
            thread.start();

            Awaitility.await().timeout(TEN_SECONDS).until(thread::isAlive);

            Request httpRequest;
            Response response;

            // Health-check ping should return "pong" (200)

            httpRequest = new Request.Builder().url("http://localhost:" + inetPort + "/health-check/ping").build();

            response = httpClient.newCall(httpRequest).execute();

            assertEquals(200, response.code());
            assertEquals("OK", response.message());
            assertEquals("pong", response.body().string());

            // Health-check module with wrong url should return "Not Found" (404)

            httpRequest = new Request.Builder().url("http://localhost:" + inetPort + "/health-check/unsupported").build();

            response = httpClient.newCall(httpRequest).execute();

            assertEquals(404, response.code());
            assertEquals("Not Found", response.message());
            assertEquals("Not Found", response.body().string());

            // Unhandled request should return "Not Found" (404)

            httpRequest = new Request.Builder().url("http://localhost:" + inetPort + "/non-existent-module/whatever").build();

            response = httpClient.newCall(httpRequest).execute();

            assertEquals(404, response.code());
            assertEquals("Not Found", response.message());
            assertEquals("Not Found", response.body().string());

        } catch (Exception ex) {
            ex.printStackTrace();
            fail("Rest Server test failed");
        } finally {
            restServer.stop();
            thread.interrupt();
            Thread.currentThread().interrupt();
        }

    }

}
