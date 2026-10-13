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
package co.rsk.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public final class IntegrationTestUtils {

    private IntegrationTestUtils() {
    }

    public static int findFreePort() {
        return findFreePorts(1)[0];
    }

    /**
     * Returns {@code count} distinct free ports. All sockets stay bound until
     * every port is picked, so the OS cannot hand out the same port twice.
     * Calling {@link #findFreePort()} repeatedly gives no such guarantee: each
     * socket is released before the next bind, and the OS may reuse it.
     */
    public static int[] findFreePorts(int count) {
        ServerSocket[] sockets = new ServerSocket[count];
        try {
            int[] ports = new int[count];
            for (int i = 0; i < count; i++) {
                sockets[i] = new ServerSocket(0);
                ports[i] = sockets[i].getLocalPort();
            }
            return ports;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            for (ServerSocket socket : sockets) {
                closeQuietly(socket);
            }
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            // the port is only a hint for the caller; a failed close does not change it
        }
    }

    public static void waitFor(long amount, TimeUnit unit) {
        Duration delay = Duration.ofNanos(unit.toNanos(amount));
        Duration timeout = delay.plusMillis(1);
        await().atMost(timeout).pollDelay(delay).until(() -> true);
    }
}
