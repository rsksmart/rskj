/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.db;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.trie.TrieCopier;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.core.Blockchain;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ethereum.datasource.DataSourcePool.levelDbByName;

/**
 * Created by ajlopez on 21/03/2018.
 */
public class PruneService {
    private static final Logger logger = LoggerFactory.getLogger("prune");

    private final RskSystemProperties rskConfiguration;
    private final PruneConfiguration pruneConfiguration;
    private final Blockchain blockchain;
    private final RskAddress contractAddress;

    private boolean stopped;
    private long nextBlockNumber;

    public PruneService(PruneConfiguration pruneConfiguration, RskSystemProperties rskConfiguration, Blockchain blockchain, RskAddress contractAddress) {
        this.pruneConfiguration = pruneConfiguration;
        this.rskConfiguration = rskConfiguration;
        this.blockchain = blockchain;
        this.contractAddress = contractAddress;
        this.nextBlockNumber = pruneConfiguration.getNoBlocksToWait();
    }

    public void start() {
        long bestBlockNumber = this.blockchain.getStatus().getBestBlockNumber();
        if (shouldStopPruning(bestBlockNumber)) {
            logger.info("Prune is not starting because we're already past RSKIP85 at height {}", bestBlockNumber);
            return;
        }

        this.stopped = false;
        new Thread(this::run).start();
        logger.info("launched");
    }

    public void run() {
        while (this.stopped == false) {
            long bestBlockNumber = this.blockchain.getStatus().getBestBlockNumber();

            if (bestBlockNumber > nextBlockNumber) {
                logger.info("Starting prune at height {}", bestBlockNumber);

                try {
                    this.process();
                }
                catch (Throwable t) {
                    logger.error("Error {}", t.getMessage());
                }

                logger.info("Prune done");

                nextBlockNumber = this.blockchain.getStatus().getBestBlockNumber() + this.pruneConfiguration.getNoBlocksToWait();
            }

            if (shouldStopPruning(bestBlockNumber)) {
                logger.info("RSKIP85 activated, prune is not necessary anymore");
                stop();
                // returning will stop the thread
                return;
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                logger.error("Interrupted {}", e.getMessage());
            }
        }
    }

    private boolean shouldStopPruning(long bestBlockNumber) {
        BlockchainConfig configForBlock = this.rskConfiguration.getBlockchainConfig().getConfigForBlock(bestBlockNumber);
        return configForBlock.isRskip85();
    }

    public void stop() {
        this.stopped = true;
    }

    public void process() {
        return;
        // NO prune until retested
        /*
        long from = this.blockchain.getBestBlock().getNumber() - this.pruneConfiguration.getNoBlocksToCopy();
        long to = this.blockchain.getBestBlock().getNumber() - this.pruneConfiguration.getNoBlocksToAvoidForks();

        String dataSourceName = getDataSourceName(contractAddress);
        // this is here because TrieCopier would fail otherwise
        KeyValueDataSource sourceDataSource = levelDbByName(dataSourceName, this.rskConfiguration.databaseDir());
        sourceDataSource.init();
        KeyValueDataSource targetDataSource = levelDbByName(dataSourceName + "B", this.rskConfiguration.databaseDir());
        TrieStore targetStore = new TrieStoreImpl(targetDataSource);

        TrieCopier.trieContractStateCopy(targetStore, blockchain, from, to, blockchain.getRepository(), this.contractAddress);

        long to2 = this.blockchain.getBestBlock().getNumber() - this.pruneConfiguration.getNoBlocksToAvoidForks();

        TrieCopier.trieContractStateCopy(targetStore, blockchain, to, to2, blockchain.getRepository(), this.contractAddress);

        blockchain.suspendProcess();

        logger.info("Suspend blockchain process");

        try {
            TrieCopier.trieContractStateCopy(targetStore, blockchain, to2, 0, blockchain.getRepository(), this.contractAddress);

            // we close both datasources to release LevelDB resources before renaming and deleting directories
            targetDataSource.close();
            sourceDataSource.close();

            String contractDirectoryName = getDatabaseDirectory(rskConfiguration, dataSourceName);

            removeDirectory(contractDirectoryName);

            boolean result = FileUtil.fileRename(contractDirectoryName + "B", contractDirectoryName);

            if (!result) {
                logger.error("Unable to rename contract storage");
            }

            // re-init this datasource since it is managed by the DataSourcePool, and other parts of the code assume it will be open
            sourceDataSource.init();
        }
        finally {
            blockchain.resumeProcess();

            logger.info("Resume blockchain process");
        }
        */
    }

    private static String getDatabaseDirectory(RskSystemProperties config, String subdirectoryName) {
        return FileUtil.getDatabaseDirectoryPath(config.databaseDir(), subdirectoryName).toString();
    }

    private static String getDataSourceName(RskAddress contractAddress) {
        return "details-storage/" + contractAddress;
    }

    private static void removeDirectory(String directoryName) {
        FileUtil.recursiveDelete(directoryName);
    }
}

