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
package co.rsk.cli.tools;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidateBtcHeadersTest {

    @TempDir
    private Path tempDir;

    @Test
    void reportsInvalidHeadersFetchedFromRpc() throws IOException {
        String invalidHeader = "0xdeadbeef";
        String validHeader = "0x" + "aa".repeat(80);

        try (RpcTestServer server = new RpcTestServer(batchResponse(invalidHeader, validHeader))) {
            Path outputFile = tempDir.resolve("invalid-btc-headers.csv");

            int exitCode = new CommandLine(new ValidateBtcHeaders()).execute(
                    "--network", "testnet",
                    "--rpcUrl", server.getUrl(),
                    "--file", outputFile.toString(),
                    "--fromBlock", "1",
                    "--toBlock", "2",
                    "--batchSize", "2"
            );

            String csvOutput = Files.readString(outputFile, StandardCharsets.UTF_8);

            assertEquals(0, exitCode);
            assertTrue(csvOutput.contains("blockNumber,btcHeaderByteCount,btcHeaderHex"));
            assertTrue(csvOutput.contains("1,4," + invalidHeader));
            assertFalse(csvOutput.contains("2,80," + validHeader));
        }
    }

    private static String batchResponse(String invalidHeader, String validHeader) {
        return "[" +
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"bitcoinMergedMiningHeader\":\"" + invalidHeader + "\"}}," +
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"bitcoinMergedMiningHeader\":\"" + validHeader + "\"}}" +
                "]";
    }

    private static final class RpcTestServer implements AutoCloseable {
        private final HttpServer server;

        private RpcTestServer(String responseBody) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> handle(exchange, responseBody));
            server.start();
        }

        private String getUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void handle(HttpExchange exchange, String responseBody) throws IOException {
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        }
    }
}
