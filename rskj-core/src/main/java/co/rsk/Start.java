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
import org.ethereum.cli.CLIInterface;
import org.ethereum.config.DefaultConfig;
import org.ethereum.rpc.JsonRpcNettyServer;
import org.ethereum.rpc.JsonRpcWeb3ServerHandler;
import org.ethereum.rpc.Web3;
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
    private RskSystemProperties rskSystemProperties;
    private final Web3Factory web3Factory;

    public static void main(String[] args) throws Exception {
        ApplicationContext ctx = new AnnotationConfigApplicationContext(DefaultConfig.class);
        Start start = ctx.getBean(Start.class);
        start.startNode(args);
    }

    @Autowired
    public Start(Rsk rsk, UDPServer udpServer, MinerServer minerServer, MinerClient minerClient, RskSystemProperties rskSystemProperties, Web3Factory web3Factory) {
        this.rsk = rsk;
        this.udpServer = udpServer;
        this.minerServer = minerServer;
        this.minerClient = minerClient;
        this.rskSystemProperties = rskSystemProperties;
        this.web3Factory = web3Factory;
    }

    public void startNode(String[] args) throws Exception {
        logger.info("Starting RSK");

        CLIInterface.call(args);

        if (!"".equals(rskSystemProperties.blocksLoader())) {
            rskSystemProperties.setSyncEnabled(Boolean.FALSE);
            rskSystemProperties.setDiscoveryEnabled(Boolean.FALSE);
        }

        Metrics.registerNodeID(rskSystemProperties.nodeId());

        if (rskSystemProperties.simulateTxs()) {
            enableSimulateTxs(rsk);
        }

        if (rskSystemProperties.simulateTxsEx()) {
            enableSimulateTxsEx(rsk);
        }

        if (rskSystemProperties.isRpcEnabled()) {
            logger.info("RPC enabled");
            enableRpc();
        }
        else {
            logger.info("RPC disabled");
        }

        if (rskSystemProperties.waitForSync()) {
            waitRskSyncDone(rsk);
        }

        if (rskSystemProperties.minerServerEnabled()) {
            minerServer.start();

            if (rskSystemProperties.minerClientEnabled()) {
                minerClient.mine();
            }
        }

        if (rskSystemProperties.peerDiscovery()) {
            enablePeerDiscovery();
        }
    }

    private void enablePeerDiscovery() {
        udpServer.start();
    }

    private void enableRpc() throws InterruptedException {
        Web3 web3Service = web3Factory.newInstance();
        JsonRpcWeb3ServerHandler serverHandler = new JsonRpcWeb3ServerHandler(web3Service, rskSystemProperties.getRpcModules());
        new JsonRpcNettyServer(
            rskSystemProperties.rpcPort(),
            rskSystemProperties.soLingerTime(),
            true,
            new CorsConfiguration(),
            serverHandler
        ).start();
    }

    private void enableSimulateTxs(Rsk rsk) {
        new TxBuilder(rsk).simulateTxs();
    }

    private void enableSimulateTxsEx(Rsk rsk) {
        new TxBuilderEx().simulateTxs(rsk, rskSystemProperties);
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

    public interface Web3Factory {
        Web3 newInstance();
    }
}
