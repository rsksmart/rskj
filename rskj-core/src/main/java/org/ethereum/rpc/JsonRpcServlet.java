/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.rpc;

import co.rsk.core.Wallet;
import co.rsk.rpc.CorsConfiguration;
import co.rsk.rpc.JsonRpcFilterServer;
import com.googlecode.jsonrpc4j.AnnotationsErrorResolver;
import com.googlecode.jsonrpc4j.DefaultErrorResolver;
import com.googlecode.jsonrpc4j.JsonRpcServer;
import com.googlecode.jsonrpc4j.MultipleErrorResolver;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.WalletFactory;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.exception.RskErrorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JsonRpcServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger("rpcServer");


    public static Ethereum eth;
    public static Web3 service;
    private JsonRpcServer jsonRpcServer;
    private CorsConfiguration corsConfiguration = new CorsConfiguration();

    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        long start = System.nanoTime();
        processCorsHeaders(resp);
        jsonRpcServer.handle(req, resp);
        logger.debug("RPC call finished after [{}] nano", System.nanoTime() - start);
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        long start = System.nanoTime();
        processCorsHeaders(resp);
        jsonRpcServer.handle(req, resp);
        logger.debug("RPC call finished after [{}] nano", System.nanoTime() - start);
    }

    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        long start = System.nanoTime();
        processCorsHeaders(resp);
        resp.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.addHeader("Access-Control-Max-Age", "600");
        super.doOptions(req, resp);
        logger.debug("RPC call finished after [{}] nano", System.nanoTime() - start);
    }

    public void init(ServletConfig config) {
        if (this.service == null) {
            Wallet wallet = WalletFactory.createPersistentWallet();
            // Clean unlocked account every 10 seconds
            wallet.start(10);
            this.service = new Web3Impl(JsonRpcServlet.eth, RskSystemProperties.RSKCONFIG, wallet);
        }

        this.jsonRpcServer = this.getJsonRpcServer();
        this.jsonRpcServer.setErrorResolver(new MultipleErrorResolver(new RskErrorResolver(), AnnotationsErrorResolver.INSTANCE, DefaultErrorResolver.INSTANCE));
    }

    private JsonRpcServer getJsonRpcServer() {
        return new JsonRpcFilterServer(this.service, this.service.getClass(), RskSystemProperties.RSKCONFIG.getRpcModules());
    }

    private void processCorsHeaders(HttpServletResponse resp) {
        if (this.corsConfiguration.hasHeader()) {
            resp.addHeader("Access-Control-Allow-Origin", this.corsConfiguration.getHeader());
            resp.addHeader("Vary", "Origin");
        }
    }
}
