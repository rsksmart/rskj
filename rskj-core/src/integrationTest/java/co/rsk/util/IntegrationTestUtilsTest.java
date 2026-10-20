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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationTestUtilsTest {

    @Test
    void findFreePortsReturnsTheRequestedNumberOfValidPorts() {
        int count = 5;

        int[] ports = IntegrationTestUtils.findFreePorts(count);

        assertEquals(count, ports.length);
        for (int port : ports) {
            assertTrue(port > 0 && port <= 65535, "invalid port " + port);
        }
    }

    @Test
    void findFreePortsHoldsEverySocketUntilAllPortsArePicked() {
        // Models an OS that reuses a port as soon as its socket closes: each
        // open hands out the lowest port that no open socket holds.
        int count = 5;
        int firstPort = 40000;
        Set<Integer> heldPorts = new HashSet<>();
        IntegrationTestUtils.SocketOpener reusingOpener = () -> {
            int port = firstPort;
            while (heldPorts.contains(port)) {
                port++;
            }
            heldPorts.add(port);
            return new FakeServerSocket(port, heldPorts);
        };

        int[] ports = IntegrationTestUtils.findFreePorts(count, reusingOpener);

        assertEquals(count, Arrays.stream(ports).distinct().count());
        assertTrue(heldPorts.isEmpty(), "sockets still open: " + heldPorts);
    }

    private static final class FakeServerSocket extends ServerSocket {
        private final int port;
        private final Set<Integer> heldPorts;

        // the no-arg super constructor leaves the socket unbound
        FakeServerSocket(int port, Set<Integer> heldPorts) throws IOException {
            this.port = port;
            this.heldPorts = heldPorts;
        }

        @Override
        public int getLocalPort() {
            return port;
        }

        @Override
        public void close() throws IOException {
            heldPorts.remove(port);
            super.close();
        }
    }
}
