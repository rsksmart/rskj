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
import co.rsk.core.RskFactory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import static co.rsk.config.RskSystemProperties.CONFIG;

/**
 * Created by ajlopez on 3/3/2016.
 */
public class Start {
    private static Logger logger = LoggerFactory.getLogger("start");

    private String[] args;
    private Class config;

    public Start(String[] args, Class nodeConfig) {
        this.args = args;
        this.config = nodeConfig;
    }

    public static void main(String[] args) throws Exception {
        Start start = new Start(args, DefaultConfig.class);
        start.startNode();
    }

    public void startNode() throws Exception {
        logger.info("Starting RSK");

        CLIInterface.call(args);

        if (!"".equals(CONFIG.blocksLoader())) {
            CONFIG.setSyncEnabled(Boolean.FALSE);
            CONFIG.setDiscoveryEnabled(Boolean.FALSE);
        }

        Rsk rsk = RskFactory.createRsk(config);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                ((ConfigurableApplicationContext) RskFactory.getContext()).close();
            }
        });
        Metrics.registerNodeID(CONFIG.nodeId());

        enableSimulateTx(rsk);

        enableRpc(rsk);

        if (RskSystemProperties.CONFIG.waitForSync()) {
            waitRskSyncDone(rsk);
        }

        enableMiningFunctionality(rsk);

        enablePeerDiscovery();
    }

    private void enablePeerDiscovery() {
        if(RskSystemProperties.CONFIG.peerDiscovery()) {
            UDPServer udpServer = RskFactory.getContext().getBean(UDPServer.class);
            udpServer.start();
        }
    }

    private void enableMiningFunctionality(Rsk rsk) {
        if (RskSystemProperties.CONFIG.minerServerEnabled()) {
            rsk.getMinerServer().start();
        }

        if (RskSystemProperties.CONFIG.minerServerEnabled() && RskSystemProperties.CONFIG.minerClientEnabled()) {
            rsk.getMinerClient().mine();
        }
    }

    private void enableRpc(Rsk rsk) throws Exception {
        if (RskSystemProperties.CONFIG.isRpcEnabled()) {
            logger.info("RPC enabled");
            Web3 web3Service = new Web3RskImpl(rsk);
            JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Service, RskSystemProperties.CONFIG.getRpcModules());
            new JsonRpcNettyServer(
                RskSystemProperties.CONFIG.rpcPort(),
                RskSystemProperties.CONFIG.soLingerTime(),
                Boolean.TRUE,
                new CorsConfiguration(),
                serverHandler
            ).start();
        }
        else {
            logger.info("RPC disabled");
        }
    }

    private void enableSimulateTx(Rsk rsk) {
        if (RskSystemProperties.CONFIG.simulateTxs()) {
            new TxBuilder(rsk).simulateTxs();
        }

        if (RskSystemProperties.CONFIG.simulateTxsEx()) {
            new TxBuilderEx().simulateTxs(rsk, RskSystemProperties.CONFIG);
        }
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
