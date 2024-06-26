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

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class SimpleHttpTestServer {

    protected static final String HELLO_RESPONSE = "Hello, world!";
    protected static final String NOT_FOUND_RESPONSE = "Not Found";
    protected static final String ERROR_RESPONSE = "Internal Server Error";
    protected static final String SLOW_RESPONSE = "Slow Response";
    protected static final String HELLO_PATH = "/hello";
    protected static final String NOT_FOUND_PATH = "/notfound";
    protected static final String ERROR_PATH = "/error";
    protected static final String SLOW_PATH = "/slow";   private HttpServer server;

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(HELLO_PATH, new HelloHandler());
        server.createContext(NOT_FOUND_PATH, new NotFoundHandler());
        server.createContext(ERROR_PATH, new ErrorHandler());
        server.createContext(SLOW_PATH, new SlowHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    static class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = HELLO_RESPONSE;
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class NotFoundHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = NOT_FOUND_RESPONSE;
            t.sendResponseHeaders(404, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class ErrorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = ERROR_RESPONSE;
            t.sendResponseHeaders(500, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class SlowHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                Thread.sleep(5000); // sleep for 5 seconds to simulate slow response
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String response = SLOW_RESPONSE;
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
