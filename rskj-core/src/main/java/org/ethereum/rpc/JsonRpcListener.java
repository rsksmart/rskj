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

import co.rsk.config.RskSystemProperties;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.ethereum.facade.Ethereum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Ruben on 3/11/2015.
 */
public class JsonRpcListener {

    private Logger logger = LoggerFactory.getLogger("jsonrpc");

    private Ethereum eth;

    private Web3 service;

    public JsonRpcListener(Ethereum eth, Web3 service)
    {
        this.eth = eth;
        this.service = service;
    }

    public void start() throws Exception {
        logger.info("Starting RPC Server on PORT [{}]", RskSystemProperties.RSKCONFIG.rpcPort());

        Server server = new Server(RskSystemProperties.RSKCONFIG.rpcPort());
        server.setConnectors(buildConnectors(server));

        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        JsonRpcServlet.eth = this.eth;
        JsonRpcServlet.service = this.service;
        handler.addServletWithMapping(JsonRpcServlet.class, "/*");

        server.start();
    }

    private Connector[] buildConnectors(Server server) {
        ServerConnector connector = new ServerConnector(server, RskSystemProperties.RSKCONFIG.acceptorsNumber(), -1);
        connector.setSoLingerTime(RskSystemProperties.RSKCONFIG.soLingerTime());
        connector.setAcceptQueueSize(RskSystemProperties.RSKCONFIG.acceptQueueSize());
        connector.setReuseAddress(Boolean.TRUE);
        connector.setPort(RskSystemProperties.RSKCONFIG.rpcPort());
        return new Connector[] {connector};
    }
}
