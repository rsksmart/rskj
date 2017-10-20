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

package co.rsk;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Rsk;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.mine.TxBuilder;
import co.rsk.mine.TxBuilderEx;
import co.rsk.net.Metrics;
import co.rsk.net.discovery.UDPServer;
import co.rsk.rpc.CorsConfiguration;
import co.rsk.rpc.Web3RskImpl;
import org.ethereum.cli.CLIInterface;
import org.ethereum.config.DefaultConfig;
import org.ethereum.rpc.JsonRpcNettyServer;
import org.ethereum.rpc.JsonRpcWeb3ServerHandler;
import org.ethereum.rpc.Web3;
import org.ethereum.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Start {
    private static Logger logger = LoggerFactory.getLogger("start");

    private Rsk rsk;
    private UDPServer udpServer;
    private MinerServer minerServer;
    private MinerClient minerClient;

    public static void main(String[] args) throws Exception {
        if (RskSystemProperties.CONFIG.databaseReset()){ //FIXME: move this outside main
            FileUtil.recursiveDelete(RskSystemProperties.CONFIG.databaseDir());
            logger.info("Database reset done");
        }
        ApplicationContext ctx = new AnnotationConfigApplicationContext(DefaultConfig.class);
        Start start = ctx.getBean(Start.class);
        start.startNode(args);
    }

    @Autowired
    public Start(Rsk rsk, UDPServer udpServer, MinerServer minerServer, MinerClient minerClient) {
        this.rsk = rsk;
        this.udpServer = udpServer;
        this.minerServer = minerServer;
        this.minerClient = minerClient;
    }

    public void startNode(String[] args) throws Exception {
        logger.info("Starting RSK");

        CLIInterface.call(args);

        if (!"".equals(RskSystemProperties.CONFIG.blocksLoader())) {
            RskSystemProperties.CONFIG.setSyncEnabled(Boolean.FALSE);
            RskSystemProperties.CONFIG.setDiscoveryEnabled(Boolean.FALSE);
        }

        Metrics.registerNodeID(RskSystemProperties.CONFIG.nodeId());

        if (RskSystemProperties.CONFIG.simulateTxs()) {
            enableSimulateTxs(rsk);
        }

        if (RskSystemProperties.CONFIG.simulateTxsEx()) {
            enableSimulateTxsEx(rsk);
        }

        if (RskSystemProperties.CONFIG.isRpcEnabled()) {
            logger.info("RPC enabled");
            enableRpc(rsk);
        }
        else {
            logger.info("RPC disabled");
        }

        if (RskSystemProperties.CONFIG.waitForSync()) {
            waitRskSyncDone(rsk);
        }

        if (RskSystemProperties.CONFIG.minerServerEnabled()) {
            minerServer.start();

            if (RskSystemProperties.CONFIG.minerClientEnabled()) {
                minerClient.mine();
            }
        }

        if (RskSystemProperties.CONFIG.peerDiscovery()) {
            enablePeerDiscovery();
        }
    }

    private void enablePeerDiscovery() {
        udpServer.start();
    }

    private void enableRpc(Rsk rsk) throws Exception {
        Web3 web3Service = new Web3RskImpl(rsk, minerServer, minerClient);
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Service, RskSystemProperties.CONFIG.getRpcModules());
        new JsonRpcNettyServer(
            RskSystemProperties.CONFIG.rpcPort(),
            RskSystemProperties.CONFIG.soLingerTime(),
            Boolean.TRUE,
            new CorsConfiguration(),
            serverHandler
        ).start();
    }

    private void enableSimulateTxs(Rsk rsk) {
        new TxBuilder(rsk).simulateTxs();
    }

    private void enableSimulateTxsEx(Rsk rsk) {
        new TxBuilderEx().simulateTxs(rsk, RskSystemProperties.CONFIG);
    }

    private void waitRskSyncDone(Rsk rsk) throws InterruptedException {
        while (rsk.isBlockchainEmpty() || rsk.isSyncingBlocks() || rsk.isPlayingBlocks()) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e1) {
                logger.trace("Wait sync done was interrupted", e1);
                throw e1;
            }
        }
    }
}
