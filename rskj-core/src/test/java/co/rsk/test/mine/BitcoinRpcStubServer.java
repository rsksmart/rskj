/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.test.mine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Lightweight Bitcoin Core JSON-RPC stub for miner fork-balance tests.
 */
public final class BitcoinRpcStubServer implements AutoCloseable {

    private HttpServer server;
    private final int httpStatus;
    private final Map<String, String> responsesByMethod = new HashMap<>();
    private final Map<String, Supplier<String>> dynamicResponses = new HashMap<>();
    private final Map<String, AtomicInteger> requestCounts = new HashMap<>();

    private BitcoinRpcStubServer(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public static BitcoinRpcStubServer ok() {
        return new BitcoinRpcStubServer(200);
    }

    public static BitcoinRpcStubServer httpStatus(int status) {
        return new BitcoinRpcStubServer(status);
    }

    public static String rpcOk(String resultJson) {
        return "{\"jsonrpc\":\"1.0\",\"id\":\"rskj-fac\",\"result\":" + resultJson + "}";
    }

    public static String rpcError(int code, String message) {
        return "{\"jsonrpc\":\"1.0\",\"id\":\"rskj-fac\",\"error\":{\"code\":"
                + code + ",\"message\":\"" + message + "\"}}";
    }

    public BitcoinRpcStubServer onMethod(String method, String responseBody) {
        responsesByMethod.put(method, responseBody);
        requestCounts.putIfAbsent(method, new AtomicInteger());
        return this;
    }

    public BitcoinRpcStubServer onDynamicMethod(String method, Supplier<String> responseSupplier) {
        dynamicResponses.put(method, responseSupplier);
        requestCounts.putIfAbsent(method, new AtomicInteger());
        return this;
    }

    public BitcoinRpcStubServer start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        return this;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int requestCount(String method) {
        AtomicInteger counter = requestCounts.get(method);
        return counter != null ? counter.get() : 0;
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (httpStatus != 200) {
            exchange.sendResponseHeaders(httpStatus, -1);
            exchange.close();
            return;
        }
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String method = extractMethod(requestBody);
        requestCounts.computeIfAbsent(method, ignored -> new AtomicInteger()).incrementAndGet();
        String body = dynamicResponses.containsKey(method)
                ? dynamicResponses.get(method).get()
                : responsesByMethod.getOrDefault(method, rpcError(-32601, "Method not found: " + method));
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private static String extractMethod(String requestBody) {
        int marker = requestBody.indexOf("\"method\"");
        if (marker < 0) {
            return "";
        }
        int firstQuote = requestBody.indexOf('"', marker + 8);
        int secondQuote = requestBody.indexOf('"', firstQuote + 1);
        return requestBody.substring(firstQuote + 1, secondQuote);
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }
}
