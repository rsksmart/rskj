/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.config;

import co.rsk.config.GasLimitConfig;
import co.rsk.config.MiningConfig;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.NetworkStateExporter;
import co.rsk.metrics.BlockHeaderElement;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.metrics.HashRateCalculatorMining;
import co.rsk.metrics.HashRateCalculatorNonMining;
import co.rsk.net.discovery.PeerExplorer;
import co.rsk.net.discovery.UDPServer;
import co.rsk.net.discovery.table.KademliaOptions;
import co.rsk.net.discovery.table.NodeDistanceTable;
import co.rsk.util.RskCustomCache;
import co.rsk.validators.*;
import org.apache.commons.collections4.CollectionUtils;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.*;
import org.ethereum.net.rlpx.Node;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.ethereum.db.IndexedBlockStore.BLOCK_INFO_SERIALIZER;

/**
 * @author Roman Mandeleil
 *         Created on: 27/01/2015 01:05
 */
@Configuration
@Import(CommonConfig.class)
public class DefaultConfig {
    private static Logger logger = LoggerFactory.getLogger("general");

    @Bean
    public BlockStore blockStore(RskSystemProperties config) {
        String database = config.databaseDir();

        File blockIndexDirectory = new File(database + "/blocks/");
        File dbFile = new File(blockIndexDirectory, "index");
        if (!blockIndexDirectory.exists()) {
            boolean mkdirsSuccess = blockIndexDirectory.mkdirs();
            if (!mkdirsSuccess) {
                logger.error("Unable to create blocks directory: {}", blockIndexDirectory);
            }
        }

        DB indexDB = DBMaker.fileDB(dbFile)
                .closeOnJvmShutdown()
                .make();

        Map<Long, List<IndexedBlockStore.BlockInfo>> indexMap = indexDB.hashMapCreate("index")
                .keySerializer(Serializer.LONG)
                .valueSerializer(BLOCK_INFO_SERIALIZER)
                .counterEnable()
                .makeOrGet();

        KeyValueDataSource blocksDB = new LevelDbDataSource(config, "blocks");
        blocksDB.init();

        IndexedBlockStore indexedBlockStore = new IndexedBlockStore(indexMap, blocksDB, indexDB);

        return indexedBlockStore;
    }

    @Bean
    public ReceiptStore receiptStore(RskSystemProperties config) {
        KeyValueDataSource ds = new LevelDbDataSource(config, "receipts");
        ds.init();
        return new ReceiptStoreImpl(ds);
    }

    @Bean
    public HashRateCalculator hashRateCalculator(RskSystemProperties rskSystemProperties, BlockStore blockStore, MiningConfig miningConfig) {
        RskCustomCache<ByteArrayWrapper, BlockHeaderElement> cache = new RskCustomCache<>(60000L);
        if (!rskSystemProperties.isMinerServerEnabled()) {
            return new HashRateCalculatorNonMining(blockStore, cache);
        }

        return new HashRateCalculatorMining(blockStore, cache, miningConfig.getCoinbaseAddress());
    }

    @Bean
    public MiningConfig miningConfig(RskSystemProperties rskSystemProperties) {
        return new MiningConfig(
                rskSystemProperties.coinbaseAddress(),
                rskSystemProperties.minerMinFeesNotifyInDollars(),
                rskSystemProperties.minerGasUnitInDollars(),
                rskSystemProperties.minerMinGasPrice(),
                rskSystemProperties.getBlockchainConfig().getCommonConstants().getUncleListLimit(),
                rskSystemProperties.getBlockchainConfig().getCommonConstants().getUncleGenerationLimit(),
                new GasLimitConfig(
                        rskSystemProperties.getBlockchainConfig().getCommonConstants().getMinGasLimit(),
                        rskSystemProperties.getTargetGasLimit(),
                        rskSystemProperties.getForceTargetGasLimit()
                )
        );
    }

    @Bean
    public RskSystemProperties rskSystemProperties() {
        return new RskSystemProperties();
    }

    @Bean
    public BlockParentDependantValidationRule blockParentDependantValidationRule(
            Repository repository,
            RskSystemProperties config,
            DifficultyCalculator difficultyCalculator) {
        BlockTxsValidationRule blockTxsValidationRule = new BlockTxsValidationRule(repository);
        BlockTxsFieldsValidationRule blockTxsFieldsValidationRule = new BlockTxsFieldsValidationRule();
        PrevMinGasPriceRule prevMinGasPriceRule = new PrevMinGasPriceRule();
        BlockParentNumberRule parentNumberRule = new BlockParentNumberRule();
        BlockDifficultyRule difficultyRule = new BlockDifficultyRule(difficultyCalculator);
        BlockParentGasLimitRule parentGasLimitRule = new BlockParentGasLimitRule(config.getBlockchainConfig().
                getCommonConstants().getGasLimitBoundDivisor());

        return new BlockParentCompositeRule(blockTxsFieldsValidationRule, blockTxsValidationRule, prevMinGasPriceRule, parentNumberRule, difficultyRule, parentGasLimitRule);
    }

    @Bean(name = "blockValidationRule")
    public BlockValidationRule blockValidationRule(
            BlockStore blockStore,
            RskSystemProperties config,
            DifficultyCalculator difficultyCalculator,
            ProofOfWorkRule proofOfWorkRule) {
        Constants commonConstants = config.getBlockchainConfig().getCommonConstants();
        int uncleListLimit = commonConstants.getUncleListLimit();
        int uncleGenLimit = commonConstants.getUncleGenerationLimit();
        int validPeriod = commonConstants.getNewBlockMaxSecondsInTheFuture();
        BlockTimeStampValidationRule blockTimeStampValidationRule = new BlockTimeStampValidationRule(validPeriod);

        BlockParentGasLimitRule parentGasLimitRule = new BlockParentGasLimitRule(commonConstants.getGasLimitBoundDivisor());
        BlockParentCompositeRule unclesBlockParentHeaderValidator = new BlockParentCompositeRule(new PrevMinGasPriceRule(), new BlockParentNumberRule(), blockTimeStampValidationRule, new BlockDifficultyRule(difficultyCalculator), parentGasLimitRule);

        BlockCompositeRule unclesBlockHeaderValidator = new BlockCompositeRule(proofOfWorkRule, blockTimeStampValidationRule, new ValidGasUsedRule());

        BlockUnclesValidationRule blockUnclesValidationRule = new BlockUnclesValidationRule(config, blockStore, uncleListLimit, uncleGenLimit, unclesBlockHeaderValidator, unclesBlockParentHeaderValidator);

        int minGasLimit = commonConstants.getMinGasLimit();
        int maxExtraDataSize = commonConstants.getMaximumExtraDataSize();

        return new BlockCompositeRule(new TxsMinGasPriceRule(), blockUnclesValidationRule, new BlockRootValidationRule(), new RemascValidationRule(), blockTimeStampValidationRule, new GasLimitRule(minGasLimit), new ExtraDataRule(maxExtraDataSize));
    }

    @Bean
    public NetworkStateExporter networkStateExporter(Repository repository) {
        return new NetworkStateExporter(repository);
    }


    @Bean(name = "minerServerBlockValidation")
    public BlockValidationRule minerServerBlockValidationRule(
            BlockStore blockStore,
            RskSystemProperties config,
            DifficultyCalculator difficultyCalculator,
            ProofOfWorkRule proofOfWorkRule) {
        Constants commonConstants = config.getBlockchainConfig().getCommonConstants();
        int uncleListLimit = commonConstants.getUncleListLimit();
        int uncleGenLimit = commonConstants.getUncleGenerationLimit();

        BlockParentGasLimitRule parentGasLimitRule = new BlockParentGasLimitRule(commonConstants.getGasLimitBoundDivisor());
        BlockParentCompositeRule unclesBlockParentHeaderValidator = new BlockParentCompositeRule(new PrevMinGasPriceRule(), new BlockParentNumberRule(), new BlockDifficultyRule(difficultyCalculator), parentGasLimitRule);

        int validPeriod = commonConstants.getNewBlockMaxSecondsInTheFuture();
        BlockTimeStampValidationRule blockTimeStampValidationRule = new BlockTimeStampValidationRule(validPeriod);
        BlockCompositeRule unclesBlockHeaderValidator = new BlockCompositeRule(proofOfWorkRule, blockTimeStampValidationRule, new ValidGasUsedRule());

        return new BlockUnclesValidationRule(config, blockStore, uncleListLimit, uncleGenLimit, unclesBlockHeaderValidator, unclesBlockParentHeaderValidator);
    }

    @Bean
    public PeerExplorer peerExplorer(RskSystemProperties rskConfig) {
        ECKey key = rskConfig.getMyKey();
        Node localNode = new Node(key.getNodeId(), rskConfig.getPublicIp(), rskConfig.getPeerPort());
        NodeDistanceTable distanceTable = new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, localNode);
        long msgTimeOut = rskConfig.peerDiscoveryMessageTimeOut();
        long refreshPeriod = rskConfig.peerDiscoveryRefreshPeriod();
        List<String> initialBootNodes = rskConfig.peerDiscoveryIPList();
        List<Node> activePeers = rskConfig.peerActive();
        if(CollectionUtils.isNotEmpty(activePeers)) {
            for(Node n : activePeers) {
                InetSocketAddress address = n.getAddress();
                initialBootNodes.add(address.getHostName() + ":" + address.getPort());
            }
        }
        return new PeerExplorer(initialBootNodes, localNode, distanceTable, key, msgTimeOut, refreshPeriod);
    }

    @Bean
    public UDPServer udpServer(PeerExplorer peerExplorer, RskSystemProperties rskConfig) {
        return new UDPServer(rskConfig.getBindAddress().getHostAddress(), rskConfig.getPeerPort(), peerExplorer);
    }
}
