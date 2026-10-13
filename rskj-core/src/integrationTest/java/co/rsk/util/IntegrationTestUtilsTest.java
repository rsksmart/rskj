/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationTestUtilsTest {

    @Test
    void findFreePortsReturnsTheRequestedNumberOfDistinctPorts() {
        int count = 5;

        int[] ports = IntegrationTestUtils.findFreePorts(count);

        assertEquals(count, ports.length);
        assertEquals(count, Arrays.stream(ports).distinct().count());
        for (int port : ports) {
            assertTrue(port > 0 && port <= 65535, "invalid port " + port);
        }
    }

    @Test
    void findFreePortsReleasesThePorts() throws IOException {
        int[] ports = IntegrationTestUtils.findFreePorts(2);

        for (int port : ports) {
            try (ServerSocket socket = new ServerSocket(port)) {
                assertEquals(port, socket.getLocalPort());
            }
        }
    }

    @Test
    void findFreePortReturnsABindablePort() throws IOException {
        int port = IntegrationTestUtils.findFreePort();

        try (ServerSocket socket = new ServerSocket(port)) {
            assertEquals(port, socket.getLocalPort());
        }
    }
}
