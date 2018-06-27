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

package co.rsk.rpc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.NetworkStateExporter;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.*;
import co.rsk.net.BlockProcessor;
import co.rsk.net.notifications.processing.FederationNotificationProcessor;
import co.rsk.net.notifications.processing.NodeFederationNotificationProcessor;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.rpc.Web3Impl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Handles requests for work and block submission.
 * Full responsibility for processing the request is delegated to MinerServer.
 *
 * @author Adrian Eidelman
 * @author Martin Medina
 */
public class Web3RskImpl extends Web3Impl {
    private static final Logger logger = LoggerFactory.getLogger("web3");
    private final NetworkStateExporter networkStateExporter;
    private final BlockStore blockStore;

    public Web3RskImpl(Ethereum eth,
                       Blockchain blockchain,
                       TransactionPool transactionPool,
                       RskSystemProperties properties,
                       MinerClient minerClient,
                       MinerServer minerServer,
                       PersonalModule personalModule,
                       EthModule ethModule,
                       TxPoolModule txPoolModule,
                       MnrModule mnrModule,
                       DebugModule debugModule,
                       ChannelManager channelManager,
                       Repository repository,
                       PeerScoringManager peerScoringManager,
                       NetworkStateExporter networkStateExporter,
                       BlockStore blockStore,
                       ReceiptStore receiptStore,
                       PeerServer peerServer,
                       BlockProcessor nodeBlockProcessor,
                       FederationNotificationProcessor notificationProcessor,
                       HashRateCalculator hashRateCalculator,
                       ConfigCapabilities configCapabilities) {
        super(eth, blockchain, transactionPool, blockStore, receiptStore, properties, minerClient, minerServer,
              personalModule, ethModule, txPoolModule, mnrModule, debugModule,
              channelManager, repository, peerScoringManager, peerServer, nodeBlockProcessor, notificationProcessor,
              hashRateCalculator, configCapabilities);

        this.networkStateExporter = networkStateExporter;
        this.blockStore = blockStore;
    }

    // For testing purposes only
    public Web3RskImpl(Ethereum eth,
                       Blockchain blockchain,
                       TransactionPool transactionPool,
                       RskSystemProperties properties,
                       MinerClient minerClient,
                       MinerServer minerServer,
                       PersonalModule personalModule,
                       EthModule ethModule,
                       TxPoolModule txPoolModule,
                       MnrModule mnrModule,
                       DebugModule debugModule,
                       ChannelManager channelManager,
                       Repository repository,
                       PeerScoringManager peerScoringManager,
                       NetworkStateExporter networkStateExporter,
                       BlockStore blockStore,
                       ReceiptStore receiptStore,
                       PeerServer peerServer,
                       BlockProcessor nodeBlockProcessor,
                       HashRateCalculator hashRateCalculator,
                       ConfigCapabilities configCapabilities) {
        this(eth, blockchain, transactionPool, properties, minerClient, minerServer, personalModule, ethModule, txPoolModule, mnrModule, debugModule,
                channelManager, repository, peerScoringManager, networkStateExporter, blockStore, receiptStore, peerServer, nodeBlockProcessor,
                new NodeFederationNotificationProcessor(properties, nodeBlockProcessor), hashRateCalculator, configCapabilities);
    }

    public void ext_dumpState() {
        Block bestBlcock = blockStore.getBestBlock();
        logger.info("Dumping state for block hash {}, block number {}", bestBlcock.getHash(), bestBlcock.getNumber());
        networkStateExporter.exportStatus(System.getProperty("user.dir") + "/" + "rskdump.json");
    }

    /**
     * Export the blockchain tree as a tgf file to user.dir/rskblockchain.tgf
     *
     * @param numberOfBlocks Number of block heights to include. Eg if best block is block 2300 and numberOfBlocks is 10, the graph will include blocks in heights 2290 to 2300.
     * @param includeUncles  Whether to show uncle links (recommended value is false)
     */
    public void ext_dumpBlockchain(long numberOfBlocks, boolean includeUncles) {
        Block bestBlock = blockStore.getBestBlock();
        logger.info("Dumping blockchain starting on block number {}, to best block number {}", bestBlock.getNumber() - numberOfBlocks, bestBlock.getNumber());
        File graphFile = new File(System.getProperty("user.dir") + "/" + "rskblockchain.tgf");
        try (PrintWriter writer = new PrintWriter(new FileWriter(graphFile))) {

            List<Block> result = new LinkedList<>();
            long firstBlock = bestBlock.getNumber() - numberOfBlocks;
            if (firstBlock < 0) {
                firstBlock = 0;
            }
            for (long i = firstBlock; i < bestBlock.getNumber(); i++) {
                result.addAll(blockStore.getChainBlocksByNumber(i));
            }
            for (Block block : result) {
                writer.println(toSmallHash(block.getHash().getBytes()) + " " + block.getNumber() + "-" + toSmallHash(
                        block.getHash().getBytes()));
            }
            writer.println("#");
            for (Block block : result) {
                writer.println(toSmallHash(block.getHash().getBytes()) + " " + toSmallHash(block.getParentHash().getBytes()) + " P");
                if (includeUncles) {
                    for (BlockHeader uncleHeader : block.getUncleList()) {
                        writer.println(toSmallHash(block.getHash().getBytes()) + " " + toSmallHash(uncleHeader.getHash().getBytes()) + " U");
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Could nos save node graph to file", e);
        }
    }

    private String toSmallHash(byte[] input) {
        return Hex.toHexString(input).substring(56, 64);
    }
}
