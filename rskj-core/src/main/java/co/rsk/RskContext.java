/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.cli.CliArgs;
import co.rsk.config.*;
import co.rsk.core.*;
import co.rsk.core.bc.*;
import co.rsk.crypto.Keccak256;
import co.rsk.db.*;
import co.rsk.db.importer.BootstrapImporter;
import co.rsk.db.importer.BootstrapURLProvider;
import co.rsk.db.importer.provider.BootstrapDataProvider;
import co.rsk.db.importer.provider.BootstrapDataVerifier;
import co.rsk.db.importer.provider.BootstrapFileHandler;
import co.rsk.db.importer.provider.Unzipper;
import co.rsk.db.importer.provider.index.BootstrapIndexCandidateSelector;
import co.rsk.db.importer.provider.index.BootstrapIndexRetriever;
import co.rsk.logfilter.BlocksBloomService;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.metrics.BlockHeaderElement;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.metrics.HashRateCalculatorMining;
import co.rsk.metrics.HashRateCalculatorNonMining;
import co.rsk.mine.*;
import co.rsk.net.*;
import co.rsk.net.discovery.PeerExplorer;
import co.rsk.net.discovery.UDPServer;
import co.rsk.net.discovery.table.KademliaOptions;
import co.rsk.net.discovery.table.NodeDistanceTable;
import co.rsk.net.eth.MessageFilter;
import co.rsk.net.eth.MessageRecorder;
import co.rsk.net.eth.RskWireProtocol;
import co.rsk.net.eth.WriterMessageRecorder;
import co.rsk.net.handler.quota.TxQuotaChecker;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.rpc.*;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.eth.*;
import co.rsk.rpc.modules.eth.subscribe.BlockHeaderNotificationEmitter;
import co.rsk.rpc.modules.eth.subscribe.LogsNotificationEmitter;
import co.rsk.rpc.modules.eth.subscribe.PendingTransactionsNotificationEmitter;
import co.rsk.rpc.modules.eth.subscribe.SyncNotificationEmitter;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.evm.EvmModuleImpl;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.mnr.MnrModuleImpl;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletDisabled;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.rsk.RskModuleImpl;
import co.rsk.rpc.modules.trace.TraceModule;
import co.rsk.rpc.modules.trace.TraceModuleImpl;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.rpc.netty.*;
import co.rsk.scoring.PeerScoring;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PeerScoringReporterService;
import co.rsk.scoring.PunishmentParameters;
import co.rsk.trie.MultiTrieStore;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.util.RskCustomCache;
import co.rsk.validators.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.core.genesis.BlockChainLoader;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.core.genesis.GenesisLoaderImpl;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.datasource.CacheSnapshotHandler;
import org.ethereum.datasource.DataSourceWithCache;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImplV2;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.net.EthereumChannelInitializerFactory;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.client.ConfigCapabilitiesImpl;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.eth.message.Eth62MessageFactory;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.*;
import org.ethereum.rpc.Web3;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.sync.SyncPool;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.FileUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates the initial object graph without a DI framework.
 * <p>
 * When an object construction has to be tweaked, create a buildXXX method and call it from getXXX.
 * This way derived classes don't have to store their own instances.
 * <p>
 * Note that many methods are public to allow the fed node overriding.
 */
public class RskContext implements NodeContext, NodeBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(RskContext.class);

    private static final String CACHE_FILE_NAME = "rskcache";

    private final CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;

    private RskSystemProperties rskSystemProperties;
    private Blockchain blockchain;
    private MiningMainchainView miningMainchainView;
    private ConsensusValidationMainchainView consensusValidationMainchainView;
    private BlockFactory blockFactory;
    private BlockChainLoader blockChainLoader;
    private org.ethereum.db.BlockStore blockStore;
    private NetBlockStore netBlockStore;
    private TrieStore trieStore;
    private StateRootsStore stateRootsStore;
    private GenesisLoader genesisLoader;
    private Genesis genesis;
    private CompositeEthereumListener compositeEthereumListener;
    private DifficultyCalculator difficultyCalculator;
    private ForkDetectionDataCalculator forkDetectionDataCalculator;
    private ProofOfWorkRule proofOfWorkRule;
    private ForkDetectionDataRule forkDetectionDataRule;
    private BlockParentDependantValidationRule blockParentDependantValidationRule;
    private BlockValidationRule blockValidationRule;
    private BlockValidationRule minerServerBlockValidationRule;
    private BlockValidator blockValidator;
    private BlockValidator blockHeaderValidator;
    private BlockValidator blockRelayValidator;
    private ReceiptStore receiptStore;
    private ProgramInvokeFactory programInvokeFactory;
    private TransactionPool transactionPool;
    private RepositoryLocator repositoryLocator;
    private StateRootHandler stateRootHandler;
    private EvmModule evmModule;
    private BlockToMineBuilder blockToMineBuilder;
    private BlockNodeInformation blockNodeInformation;
    private Ethereum rsk;
    private PeerScoringManager peerScoringManager;
    private NodeBlockProcessor nodeBlockProcessor;
    private SyncProcessor syncProcessor;
    private BlockSyncService blockSyncService;
    private SyncPool syncPool;
    private Web3 web3;
    private JsonRpcWeb3FilterHandler jsonRpcWeb3FilterHandler;
    private JsonRpcWeb3ServerHandler jsonRpcWeb3ServerHandler;
    private Web3WebSocketServer web3WebSocketServer;
    private JacksonBasedRpcSerializer jacksonBasedRpcSerializer;
    private Web3HttpServer web3HttpServer;
    private WriterMessageRecorder writerMessageRecorder;
    private Wallet wallet;
    private PeerServer peerServer;
    private PersonalModule personalModule;
    private EthModuleWallet ethModuleWallet;
    private EthModuleTransaction ethModuleTransaction;
    private MinerClient minerClient;
    private SyncConfiguration syncConfiguration;
    private TransactionGateway transactionGateway;
    private BuildInfo buildInfo;
    private MinerClock minerClock;
    private MiningConfig miningConfig;
    private NetworkStateExporter networkStateExporter;
    private PeerExplorer peerExplorer;
    private EthereumChannelInitializerFactory ethereumChannelInitializerFactory;
    private HashRateCalculator hashRateCalculator;
    private EthModule ethModule;
    private ChannelManager channelManager;
    private NodeRunner nodeRunner;
    private NodeMessageHandler nodeMessageHandler;
    private ConfigCapabilities configCapabilities;
    private DebugModule debugModule;
    private TraceModule traceModule;
    private MnrModule mnrModule;
    private TxPoolModule txPoolModule;
    private RskModule rskModule;
    private RskWireProtocol.Factory rskWireProtocolFactory;
    private Eth62MessageFactory eth62MessageFactory;
    private GasLimitCalculator gasLimitCalculator;
    private ReversibleTransactionExecutor reversibleTransactionExecutor;
    private TransactionExecutorFactory transactionExecutorFactory;
    private ExecutionBlockRetriever executionBlockRetriever;
    private NodeManager nodeManager;
    private StaticMessages staticMessages;
    private MinerServer minerServer;
    private BlocksBloomStore blocksBloomStore;
    private KeyValueDataSource blocksBloomDataSource;
    private BlockExecutor blockExecutor;
    private BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private PrecompiledContracts precompiledContracts;
    private BridgeSupportFactory bridgeSupportFactory;
    private PeersInformation peersInformation;
    private StatusResolver statusResolver;
    private Web3InformationRetriever web3InformationRetriever;
    private BootstrapImporter bootstrapImporter;
    private ReceivedTxSignatureCache receivedTxSignatureCache;
    private BlockTxSignatureCache blockTxSignatureCache;
    private PeerScoringReporterService peerScoringReporterService;
    private TxQuotaChecker txQuotaChecker;
    private GasPriceTracker gasPriceTracker;

    private volatile boolean closed;

    /***** Constructors ***********************************************************************************************/

    public RskContext(String[] args) {
        this(new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        ).parse(args));
    }

    private RskContext(CliArgs<NodeCliOptions, NodeCliFlags> cliArgs) {
        this.cliArgs = cliArgs;
        initializeSingletons();
    }

    /***** Public Methods *********************************************************************************************/

    public CliArgs<NodeCliOptions, NodeCliFlags> getCliArgs() {
        return cliArgs;
    }

    public synchronized BootstrapImporter getBootstrapImporter() {
        checkIfNotClosed();

        if (bootstrapImporter == null) {
            RskSystemProperties systemProperties = getRskSystemProperties();
            List<String> publicKeys = systemProperties.importTrustedKeys();
            int minimumRequired = publicKeys.size() / 2 + 1;
            if (minimumRequired < 2) {
                logger.warn("Configuration has less trusted sources than the minimum required {} of 2", minimumRequired);
                minimumRequired = 2;
            }

            BootstrapURLProvider bootstrapUrlProvider = new BootstrapURLProvider(systemProperties.importUrl());

            bootstrapImporter = new BootstrapImporter(
                    getBlockStore(),
                    getTrieStore(),
                    blockFactory,
                    new BootstrapDataProvider(
                            new BootstrapDataVerifier(),
                            new BootstrapFileHandler(bootstrapUrlProvider, new Unzipper()),
                            new BootstrapIndexCandidateSelector(publicKeys, minimumRequired),
                            new BootstrapIndexRetriever(publicKeys, bootstrapUrlProvider, new ObjectMapper()),
                            minimumRequired
                    )
            );
        }
        return bootstrapImporter;
    }

    @Override
    public synchronized NodeRunner getNodeRunner() {
        checkIfNotClosed();

        if (nodeRunner == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            if (rskSystemProperties.databaseReset() || rskSystemProperties.importEnabled()) {
                FileUtil.recursiveDelete(rskSystemProperties.databaseDir());
            }

            if (rskSystemProperties.importEnabled()) {
                getBootstrapImporter().importData();
            }

            nodeRunner = buildNodeRunner();
        }

        return nodeRunner;
    }

    public synchronized Blockchain getBlockchain() {
        checkIfNotClosed();

        if (blockchain == null) {
            blockchain = getBlockChainLoader().loadBlockchain();
        }

        return blockchain;
    }

    public synchronized MiningMainchainView getMiningMainchainView() {
        checkIfNotClosed();

        // One would expect getBlockStore to be used here. However, when the BlockStore is created,
        // it does not have any blocks, resulting in a NullPointerException when trying to initially
        // fill the mainchain view. Hence why we wait for the blockchain to perform its required
        // initialization tasks and then we ask for the store
        getBlockchain();
        if (miningMainchainView == null) {
            miningMainchainView = new MiningMainchainViewImpl(
                    getBlockStore(),
                    MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION
            );
        }

        return miningMainchainView;
    }

    public synchronized ConsensusValidationMainchainView getConsensusValidationMainchainView() {
        checkIfNotClosed();

        if (consensusValidationMainchainView == null) {
            consensusValidationMainchainView = new ConsensusValidationMainchainViewImpl(getBlockStore());
        }

        return consensusValidationMainchainView;
    }

    public synchronized BlockFactory getBlockFactory() {
        checkIfNotClosed();

        if (blockFactory == null) {
            blockFactory = new BlockFactory(getRskSystemProperties().getActivationConfig());
        }

        return blockFactory;
    }

    public synchronized TransactionPool getTransactionPool() {
        checkIfNotClosed();

        if (transactionPool == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            transactionPool = new TransactionPoolImpl(
                    rskSystemProperties,
                    getRepositoryLocator(),
                    getBlockStore(),
                    getBlockFactory(),
                    getCompositeEthereumListener(),
                    getTransactionExecutorFactory(),
                    getReceivedTxSignatureCache(),
                    rskSystemProperties.txOutdatedThreshold(),
                    rskSystemProperties.txOutdatedTimeout(),
                    getTxQuotaChecker(),
                    getGasPriceTracker());
        }

        return transactionPool;
    }

    private TxQuotaChecker getTxQuotaChecker() {
        if (this.txQuotaChecker == null) {
            this.txQuotaChecker = new TxQuotaChecker(System::currentTimeMillis);
        }
        return txQuotaChecker;
    }

    public synchronized ReceivedTxSignatureCache getReceivedTxSignatureCache() {
        checkIfNotClosed();

        if (receivedTxSignatureCache == null) {
            receivedTxSignatureCache = new ReceivedTxSignatureCache();
        }

        return receivedTxSignatureCache;
    }

    public synchronized RepositoryLocator getRepositoryLocator() {
        checkIfNotClosed();

        if (repositoryLocator == null) {
            repositoryLocator = buildRepositoryLocator();
        }

        return repositoryLocator;
    }

    public synchronized StateRootHandler getStateRootHandler() {
        checkIfNotClosed();

        if (stateRootHandler == null) {
            stateRootHandler = buildStateRootHandler();
        }

        return stateRootHandler;
    }

    public synchronized ReceiptStore getReceiptStore() {
        checkIfNotClosed();

        if (receiptStore == null) {
            receiptStore = buildReceiptStore();
        }

        return receiptStore;
    }

    public synchronized TrieStore getTrieStore() {
        checkIfNotClosed();

        if (trieStore == null) {
            trieStore = buildAbstractTrieStore(Paths.get(getRskSystemProperties().databaseDir()));
        }

        return trieStore;
    }

    public synchronized StateRootsStore getStateRootsStore() {
        checkIfNotClosed();

        if (stateRootsStore == null) {
            stateRootsStore = buildStateRootsStore();
        }

        return stateRootsStore;
    }

    public synchronized BlockExecutor getBlockExecutor() {
        checkIfNotClosed();

        if (blockExecutor == null) {
            blockExecutor = new BlockExecutor(
                    getRskSystemProperties().getActivationConfig(),
                    getRepositoryLocator(),
                    getTransactionExecutorFactory()
            );
        }

        return blockExecutor;
    }

    public synchronized PrecompiledContracts getPrecompiledContracts() {
        checkIfNotClosed();

        if (precompiledContracts == null) {
            precompiledContracts = new PrecompiledContracts(getRskSystemProperties(), getBridgeSupportFactory());
        }

        return precompiledContracts;
    }

    public synchronized BridgeSupportFactory getBridgeSupportFactory() {
        checkIfNotClosed();

        if (bridgeSupportFactory == null) {
            bridgeSupportFactory = new BridgeSupportFactory(getBtcBlockStoreFactory(),
                    getRskSystemProperties().getNetworkConstants().getBridgeConstants(),
                    getRskSystemProperties().getActivationConfig());
        }

        return bridgeSupportFactory;
    }

    public synchronized BtcBlockStoreWithCache.Factory getBtcBlockStoreFactory() {
        checkIfNotClosed();

        if (btcBlockStoreFactory == null) {
            NetworkParameters btcParams = getRskSystemProperties().getNetworkConstants().getBridgeConstants().getBtcParams();
            btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                    btcParams,
                    getRskSystemProperties().getBtcBlockStoreCacheDepth(),
                    getRskSystemProperties().getBtcBlockStoreCacheSize()
            );
        }

        return btcBlockStoreFactory;
    }

    public synchronized org.ethereum.db.BlockStore getBlockStore() {
        checkIfNotClosed();

        if (blockStore == null) {
            blockStore = buildBlockStore();
        }

        return blockStore;
    }

    public synchronized Ethereum getRsk() {
        checkIfNotClosed();

        if (rsk == null) {
            rsk = new EthereumImpl(
                    getChannelManager(),
                    getTransactionGateway(),
                    getCompositeEthereumListener(),
                    getBlockchain(),
                    getGasPriceTracker()
            );
        }

        return rsk;
    }

    private GasPriceTracker getGasPriceTracker() {
        if (this.gasPriceTracker == null) {
            this.gasPriceTracker = GasPriceTracker.create(getBlockStore());
        }
        return this.gasPriceTracker;
    }

    public synchronized ReversibleTransactionExecutor getReversibleTransactionExecutor() {
        checkIfNotClosed();

        if (reversibleTransactionExecutor == null) {
            reversibleTransactionExecutor = new ReversibleTransactionExecutor(
                    getRepositoryLocator(),
                    getTransactionExecutorFactory()
            );
        }

        return reversibleTransactionExecutor;
    }

    public synchronized TransactionExecutorFactory getTransactionExecutorFactory() {
        checkIfNotClosed();

        if (transactionExecutorFactory == null) {
            transactionExecutorFactory = new TransactionExecutorFactory(
                    getRskSystemProperties(),
                    getBlockStore(),
                    getReceiptStore(),
                    getBlockFactory(),
                    getProgramInvokeFactory(),
                    getPrecompiledContracts(),
                    getBlockTxSignatureCache()
            );
        }

        return transactionExecutorFactory;
    }

    public synchronized NodeBlockProcessor getNodeBlockProcessor() {
        checkIfNotClosed();

        if (nodeBlockProcessor == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            if (rskSystemProperties.fastBlockPropagation()) {
                nodeBlockProcessor = new AsyncNodeBlockProcessor(
                        getNetBlockStore(),
                        getBlockchain(),
                        getBlockNodeInformation(),
                        getBlockSyncService(),
                        getSyncConfiguration(),
                        getBlockHeaderValidator(),
                        getBlockRelayValidator()
                );
            } else {
                nodeBlockProcessor = new NodeBlockProcessor(
                        getNetBlockStore(),
                        getBlockchain(),
                        getBlockNodeInformation(),
                        getBlockSyncService(),
                        getSyncConfiguration()
                );
            }
        }

        return nodeBlockProcessor;
    }

    public synchronized RskSystemProperties getRskSystemProperties() {
        checkIfNotClosed();

        if (rskSystemProperties == null) {
            rskSystemProperties = buildRskSystemProperties();
        }

        return rskSystemProperties;
    }

    public synchronized PeerScoringManager getPeerScoringManager() {
        checkIfNotClosed();

        if (peerScoringManager == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            List<String> bannedPeerIPs = rskSystemProperties.bannedPeerIPList();
            List<String> bannedPeerIDs = rskSystemProperties.bannedPeerIDList();

            peerScoringManager = new PeerScoringManager(
                    () -> new PeerScoring(rskSystemProperties.scoringPunishmentEnabled()),
                    rskSystemProperties.scoringNumberOfNodes(),
                    new PunishmentParameters(
                            rskSystemProperties.scoringNodesPunishmentDuration(),
                            rskSystemProperties.scoringNodesPunishmentIncrement(),
                            rskSystemProperties.scoringNodesPunishmentMaximumDuration()
                    ),
                    new PunishmentParameters(
                            rskSystemProperties.scoringAddressesPunishmentDuration(),
                            rskSystemProperties.scoringAddressesPunishmentIncrement(),
                            rskSystemProperties.scoringAddressesPunishmentMaximumDuration()
                    ),
                    bannedPeerIPs,
                    bannedPeerIDs
            );
        }

        return peerScoringManager;
    }

    public synchronized HashRateCalculator getHashRateCalculator() {
        checkIfNotClosed();

        if (hashRateCalculator == null) {
            RskCustomCache<Keccak256, BlockHeaderElement> cache = new RskCustomCache<>(60000L);
            if (!getRskSystemProperties().isMinerServerEnabled()) {
                hashRateCalculator = new HashRateCalculatorNonMining(getBlockStore(), cache);
            } else {
                RskAddress coinbase = getMiningConfig().getCoinbaseAddress();
                hashRateCalculator = new HashRateCalculatorMining(getBlockStore(), cache, coinbase);
            }
        }

        return hashRateCalculator;
    }

    public synchronized EthModule getEthModule() {
        checkIfNotClosed();

        if (ethModule == null) {
            Constants networkConstants = getRskSystemProperties().getNetworkConstants();
            ethModule = new EthModule(
                    networkConstants.getBridgeConstants(),
                    networkConstants.getChainId(),
                    getBlockchain(),
                    getTransactionPool(),
                    getReversibleTransactionExecutor(),
                    getExecutionBlockRetriever(),
                    getRepositoryLocator(),
                    getEthModuleWallet(),
                    getEthModuleTransaction(),
                    getBridgeSupportFactory(),
                    getRskSystemProperties().getGasEstimationCap()
            );
        }

        return ethModule;
    }

    public synchronized EvmModule getEvmModule() {
        checkIfNotClosed();

        if (evmModule == null) {
            evmModule = new EvmModuleImpl(
                    getMinerServer(),
                    getMinerClient(),
                    getMinerClock(),
                    new SnapshotManager(getBlockchain(), getBlockStore(), getTransactionPool(), getMinerServer())
            );
        }

        return evmModule;
    }

    public synchronized PeerServer getPeerServer() {
        checkIfNotClosed();

        if (peerServer == null) {
            peerServer = new PeerServerImpl(
                    getRskSystemProperties(),
                    getCompositeEthereumListener(),
                    getEthereumChannelInitializerFactory()
            );
        }

        return peerServer;
    }

    public synchronized PersonalModule getPersonalModule() {
        checkIfNotClosed();

        if (personalModule == null) {
            Wallet wallet = getWallet();
            if (wallet == null) {
                personalModule = new PersonalModuleWalletDisabled();
            } else {
                personalModule = new PersonalModuleWalletEnabled(
                        getRskSystemProperties(),
                        getRsk(),
                        wallet,
                        getTransactionPool()
                );
            }
        }

        return personalModule;
    }

    public synchronized BuildInfo getBuildInfo() {
        checkIfNotClosed();

        if (buildInfo == null) {
            try {
                Properties props = new Properties();
                InputStream buildInfoFile = RskContext.class.getClassLoader().getResourceAsStream("build-info.properties");
                props.load(buildInfoFile);
                buildInfo = new BuildInfo(props.getProperty("build.hash"), props.getProperty("build.branch"));
            } catch (IOException | NullPointerException e) {
                logger.trace("Can't find build info class, using dev configuration", e);
                buildInfo = new BuildInfo("dev", "dev");
            }
        }

        return buildInfo;
    }

    public synchronized ChannelManager getChannelManager() {
        checkIfNotClosed();

        if (channelManager == null) {
            channelManager = new ChannelManagerImpl(getRskSystemProperties(), getSyncPool());
        }

        return channelManager;
    }

    public synchronized ConfigCapabilities getConfigCapabilities() {
        checkIfNotClosed();

        if (configCapabilities == null) {
            configCapabilities = new ConfigCapabilitiesImpl(getRskSystemProperties());
        }

        return configCapabilities;
    }

    public synchronized DebugModule getDebugModule() {
        checkIfNotClosed();

        if (debugModule == null) {
            debugModule = new DebugModuleImpl(
                    getBlockStore(),
                    getReceiptStore(),
                    getNodeMessageHandler(),
                    getBlockExecutor(),
                    getTxQuotaChecker()
            );
        }

        return debugModule;
    }

    public synchronized TraceModule getTraceModule() {
        checkIfNotClosed();

        if (traceModule == null) {
            traceModule = new TraceModuleImpl(
                    getBlockchain(),
                    getBlockStore(),
                    getReceiptStore(),
                    getBlockExecutor()
            );
        }

        return traceModule;
    }

    public synchronized MnrModule getMnrModule() {
        checkIfNotClosed();

        if (mnrModule == null) {
            mnrModule = new MnrModuleImpl(getMinerServer());
        }

        return mnrModule;
    }

    public synchronized TxPoolModule getTxPoolModule() {
        checkIfNotClosed();

        if (txPoolModule == null) {
            txPoolModule = new TxPoolModuleImpl(getTransactionPool());
        }

        return txPoolModule;
    }

    public synchronized RskModule getRskModule() {
        checkIfNotClosed();

        if (rskModule == null) {
            rskModule = new RskModuleImpl(
                    getBlockchain(),
                    getBlockStore(),
                    getReceiptStore(),
                    getWeb3InformationRetriever());
        }

        return rskModule;
    }

    public synchronized NetworkStateExporter getNetworkStateExporter() {
        checkIfNotClosed();

        if (networkStateExporter == null) {
            networkStateExporter = new NetworkStateExporter(getRepositoryLocator(), getBlockchain());
        }

        return networkStateExporter;
    }

    public synchronized MinerClient getMinerClient() {
        checkIfNotClosed();

        if (minerClient == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            if (rskSystemProperties.minerClientAutoMine()) {
                minerClient = new AutoMinerClient(getMinerServer());
            } else {
                minerClient = new MinerClientImpl(
                        getNodeBlockProcessor(),
                        getMinerServer(),
                        rskSystemProperties.minerClientDelayBetweenBlocks(),
                        rskSystemProperties.minerClientDelayBetweenRefreshes()
                );
            }
        }

        return minerClient;
    }

    public synchronized MinerServer getMinerServer() {
        checkIfNotClosed();

        if (minerServer == null) {
            minerServer = new MinerServerImpl(
                    getRskSystemProperties(),
                    getRsk(),
                    getMiningMainchainView(),
                    getNodeBlockProcessor(),
                    getProofOfWorkRule(),
                    getBlockToMineBuilder(),
                    getMinerClock(),
                    getBlockFactory(),
                    getBuildInfo(),
                    getMiningConfig()
            );
        }

        return minerServer;
    }

    public synchronized ProgramInvokeFactory getProgramInvokeFactory() {
        checkIfNotClosed();

        if (programInvokeFactory == null) {
            programInvokeFactory = new ProgramInvokeFactoryImpl();
        }

        return programInvokeFactory;
    }

    public synchronized CompositeEthereumListener getCompositeEthereumListener() {
        checkIfNotClosed();

        if (compositeEthereumListener == null) {
            compositeEthereumListener = buildCompositeEthereumListener();
        }

        return compositeEthereumListener;
    }

    public synchronized BlocksBloomStore getBlocksBloomStore() {
        checkIfNotClosed();

        if (blocksBloomStore == null) {
            blocksBloomStore = new BlocksBloomStore(getRskSystemProperties().bloomNumberOfBlocks(), getRskSystemProperties().bloomNumberOfConfirmations(), getBlocksBloomDataSource());
        }

        return blocksBloomStore;
    }

    public synchronized List<InternalService> buildInternalServices() {
        checkIfNotClosed();

        List<InternalService> internalServices = new ArrayList<>();
        internalServices.add(getTransactionPool());
        internalServices.add(getChannelManager());
        internalServices.add(getNodeMessageHandler());
        internalServices.add(getPeerServer());
        boolean rpcHttpEnabled = getRskSystemProperties().isRpcHttpEnabled();
        boolean rpcWebSocketEnabled = getRskSystemProperties().isRpcWebSocketEnabled();
        boolean bloomServiceEnabled = getRskSystemProperties().bloomServiceEnabled();

        if (bloomServiceEnabled) {
            internalServices.add(new BlocksBloomService(getCompositeEthereumListener(), getBlocksBloomStore(), getBlockStore()));
        }

        if (rpcHttpEnabled || rpcWebSocketEnabled) {
            internalServices.add(getWeb3());
        }

        if (rpcHttpEnabled) {
            internalServices.add(getWeb3HttpServer());
        }

        if (rpcWebSocketEnabled) {
            internalServices.add(getWeb3WebSocketServer());
        }

        if (getRskSystemProperties().isPeerDiscoveryEnabled()) {
            internalServices.add(new UDPServer(
                    getRskSystemProperties().getBindAddress().getHostAddress(),
                    getRskSystemProperties().getPeerPort(),
                    getPeerExplorer()
            ));
        }

        if (getRskSystemProperties().isSyncEnabled()) {
            internalServices.add(getSyncPool());
        }

        if (getRskSystemProperties().isMinerServerEnabled()) {
            internalServices.add(getMinerServer());

            if (getRskSystemProperties().isMinerClientEnabled()) {
                internalServices.add(getMinerClient());
            }
        }

        NodeBlockProcessor nodeBlockProcessor = getNodeBlockProcessor();
        if (nodeBlockProcessor instanceof InternalService) {
            internalServices.add((InternalService) nodeBlockProcessor);
        }

        GarbageCollectorConfig gcConfig = getRskSystemProperties().garbageCollectorConfig();
        if (gcConfig.enabled()) {
            internalServices.add(new GarbageCollector(
                    getCompositeEthereumListener(),
                    gcConfig.blocksPerEpoch(),
                    gcConfig.numberOfEpochs(),
                    (MultiTrieStore) getTrieStore(),
                    getBlockStore(),
                    getRepositoryLocator()
            ));
        }

        if (getRskSystemProperties().isPeerScoringStatsReportEnabled()) {
            internalServices.add(getPeerScoringReporterService());
        }

        internalServices.add(new BlockChainFlusher(
                getRskSystemProperties().flushNumberOfBlocks(),
                getCompositeEthereumListener(),
                getTrieStore(),
                getBlockStore(),
                getReceiptStore(),
                getBlocksBloomStore(),
                getStateRootsStore()));

        return Collections.unmodifiableList(internalServices);
    }

    public synchronized GenesisLoader getGenesisLoader() {
        checkIfNotClosed();

        if (genesisLoader == null) {
            genesisLoader = buildGenesisLoader();
        }

        return genesisLoader;
    }

    public synchronized Genesis getGenesis() {
        checkIfNotClosed();

        if (genesis == null) {
            genesis = getGenesisLoader().load();
        }

        return genesis;
    }

    public synchronized Wallet getWallet() {
        checkIfNotClosed();

        if (wallet == null) {
            wallet = buildWallet();
        }

        return wallet;
    }

    public synchronized BlockValidationRule getBlockValidationRule() {
        checkIfNotClosed();

        if (blockValidationRule == null) {
            final RskSystemProperties rskSystemProperties = getRskSystemProperties();
            final Constants commonConstants = rskSystemProperties.getNetworkConstants();
            final BlockTimeStampValidationRule blockTimeStampValidationRule = new BlockTimeStampValidationRule(
                    commonConstants.getNewBlockMaxSecondsInTheFuture(),
                    rskSystemProperties.getActivationConfig()
            );
            blockValidationRule = new BlockValidatorRule(
                    new TxsMinGasPriceRule(),
                    new BlockUnclesValidationRule(
                            getBlockStore(),
                            commonConstants.getUncleListLimit(),
                            commonConstants.getUncleGenerationLimit(),
                            new BlockHeaderCompositeRule(
                                    getProofOfWorkRule(),
                                    getForkDetectionDataRule(),
                                    blockTimeStampValidationRule,
                                    new ValidGasUsedRule()
                            ),
                            new BlockHeaderParentCompositeRule(
                                    new PrevMinGasPriceRule(),
                                    new BlockParentNumberRule(),
                                    blockTimeStampValidationRule,
                                    new BlockDifficultyRule(getDifficultyCalculator()),
                                    new BlockParentGasLimitRule(commonConstants.getGasLimitBoundDivisor())
                            )
                    ),
                    new BlockRootValidationRule(rskSystemProperties.getActivationConfig()),
                    getProofOfWorkRule(),
                    new RemascValidationRule(),
                    blockTimeStampValidationRule,
                    new GasLimitRule(commonConstants.getMinGasLimit()),
                    new ExtraDataRule(commonConstants.getMaximumExtraDataSize()),
                    getForkDetectionDataRule()
            );
        }

        return blockValidationRule;
    }

    public synchronized BlockParentDependantValidationRule getBlockParentDependantValidationRule() {
        checkIfNotClosed();

        if (blockParentDependantValidationRule == null) {
            Constants commonConstants = getRskSystemProperties().getNetworkConstants();
            blockParentDependantValidationRule = new BlockParentCompositeRule(
                    new BlockTxsFieldsValidationRule(),
                    new BlockTxsValidationRule(getRepositoryLocator(), getBlockTxSignatureCache()),
                    new PrevMinGasPriceRule(),
                    new BlockParentNumberRule(),
                    new BlockDifficultyRule(getDifficultyCalculator()),
                    new BlockParentGasLimitRule(commonConstants.getGasLimitBoundDivisor())
            );
        }

        return blockParentDependantValidationRule;
    }

    public synchronized org.ethereum.db.BlockStore buildBlockStore(String databaseDir) {
        checkIfNotClosed();

        File blockIndexDirectory = new File(databaseDir + "/blocks/");
        File dbFile = new File(blockIndexDirectory, "index");
        if (!blockIndexDirectory.exists()) {
            if (!blockIndexDirectory.mkdirs()) {
                throw new IllegalArgumentException(String.format(
                        "Unable to create blocks directory: %s", blockIndexDirectory
                ));
            }
        }

        DB indexDB = DBMaker.fileDB(dbFile)
                .make();

        KeyValueDataSource blocksDB = LevelDbDataSource.makeDataSource(Paths.get(databaseDir, "blocks"));

        return new IndexedBlockStore(getBlockFactory(), blocksDB, new MapDBBlocksIndex(indexDB));
    }

    public synchronized PeerScoringReporterService getPeerScoringReporterService() {
        checkIfNotClosed();

        if (peerScoringReporterService == null) {
            this.peerScoringReporterService = PeerScoringReporterService.withScheduler(getRskSystemProperties().getPeerScoringSummaryTime(), getPeerScoringManager());
        }

        return peerScoringReporterService;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * This method closes this RSK context.
     * <p>
     * Internally it stops a node runner, if started,
     * and closes / disposes data storages (db instances), if some has already been instantiated.
     * <p>
     * Note that this method is idempotent, which means that calling this method more than once does not have any
     * visible side effect.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            logger.warn("The context has already been closed. Ignoring");
            return;
        }

        closed = true;

        // as RskContext creates PeerExplorer and manages its lifecycle, dispose it here
        if (peerExplorer != null) {
            peerExplorer.dispose();
        }

        // stop node runner
        if (nodeRunner != null) {
            logger.trace("stopping nodeRunner.");
            nodeRunner.stop();
            logger.trace("nodeRunner stopped.");
        }

        // then close data stores
        if (trieStore != null) {
            logger.trace("disposing trieStore.");
            trieStore.dispose();
            logger.trace("trieStore disposed.");
        }

        if (stateRootsStore != null) {
            logger.trace("closing stateRootsStore.");
            stateRootsStore.close();
            logger.trace("stateRootsStore closed.");
        }

        if (receiptStore != null) {
            logger.trace("closing receiptStore.");
            receiptStore.close();
            logger.trace("receiptStore closed.");
        }

        if (blockStore != null) {
            logger.trace("closing blockStore.");
            blockStore.close();
            logger.trace("blockStore closed.");
        }

        if (blocksBloomStore != null) {
            logger.trace("closing blocksBloomStore.");
            blocksBloomStore.close();
            logger.trace("blocksBloomStore closed.");
        }

        if (wallet != null) {
            logger.trace("closing wallet.");
            wallet.close();
            logger.trace("wallet closed.");
        }
    }

    /***** Protected Methods ******************************************************************************************/

    @Nonnull
    protected Path resolveCacheSnapshotPath(@Nonnull Path baseStorePath) {
        return baseStorePath.resolve(CACHE_FILE_NAME);
    }

    protected synchronized KeyValueDataSource buildBlocksBloomDataSource() {
        checkIfNotClosed();

        int bloomsCacheSize = getRskSystemProperties().getBloomsCacheSize();
        Path bloomsStorePath = Paths.get(getRskSystemProperties().databaseDir(), "blooms");
        KeyValueDataSource ds = LevelDbDataSource.makeDataSource(bloomsStorePath);

        if (bloomsCacheSize != 0) {
            CacheSnapshotHandler cacheSnapshotHandler = getRskSystemProperties().shouldPersistBloomsCacheSnapshot()
                    ? new CacheSnapshotHandler(resolveCacheSnapshotPath(bloomsStorePath))
                    : null;
            ds = new DataSourceWithCache(ds, bloomsCacheSize, cacheSnapshotHandler);
        }

        return ds;
    }

    protected synchronized NodeRunner buildNodeRunner() {
        checkIfNotClosed();

        return new NodeRunnerImpl(
                this,
                buildInternalServices(),
                getRskSystemProperties(),
                getBuildInfo()
        );
    }

    @SuppressWarnings("unused")
    protected synchronized SolidityCompiler buildSolidityCompiler() {
        checkIfNotClosed();

        return new SolidityCompiler(getRskSystemProperties());
    }

    protected synchronized Web3 buildWeb3() {
        checkIfNotClosed();

        return new Web3RskImpl(
                getRsk(),
                getBlockchain(),
                getRskSystemProperties(),
                getMinerClient(),
                getMinerServer(),
                getPersonalModule(),
                getEthModule(),
                getEvmModule(),
                getTxPoolModule(),
                getMnrModule(),
                getDebugModule(),
                getTraceModule(),
                getRskModule(),
                getChannelManager(),
                getPeerScoringManager(),
                getNetworkStateExporter(),
                getBlockStore(),
                getReceiptStore(),
                getPeerServer(),
                getNodeBlockProcessor(),
                getHashRateCalculator(),
                getConfigCapabilities(),
                getBuildInfo(),
                getBlocksBloomStore(),
                getWeb3InformationRetriever(),
                getSyncProcessor());
    }

    protected synchronized Web3InformationRetriever getWeb3InformationRetriever() {
        checkIfNotClosed();

        if (web3InformationRetriever == null) {
            web3InformationRetriever = new Web3InformationRetriever(
                    getTransactionPool(),
                    getBlockchain(),
                    getRepositoryLocator(),
                    getExecutionBlockRetriever());
        }
        return web3InformationRetriever;
    }

    protected synchronized ReceiptStore buildReceiptStore() {
        checkIfNotClosed();

        int receiptsCacheSize = getRskSystemProperties().getReceiptsCacheSize();
        KeyValueDataSource ds = LevelDbDataSource.makeDataSource(Paths.get(getRskSystemProperties().databaseDir(), "receipts"));

        if (receiptsCacheSize != 0) {
            ds = new DataSourceWithCache(ds, receiptsCacheSize);
        }

        return new ReceiptStoreImplV2(ds);
    }

    protected synchronized BlockValidator buildBlockValidator() {
        checkIfNotClosed();

        return new BlockValidatorImpl(
                getBlockStore(),
                getBlockParentDependantValidationRule(),
                getBlockValidationRule()
        );
    }

    protected synchronized GenesisLoader buildGenesisLoader() {
        checkIfNotClosed();

        RskSystemProperties systemProperties = getRskSystemProperties();
        ActivationConfig.ForBlock genesisActivations = systemProperties.getActivationConfig().forBlock(0L);
        return new GenesisLoaderImpl(
                systemProperties.getActivationConfig(),
                getStateRootHandler(),
                getTrieStore(),
                systemProperties.genesisInfo(),
                systemProperties.getNetworkConstants().getInitialNonce(),
                true,
                genesisActivations.isActive(ConsensusRule.RSKIP92),
                genesisActivations.isActive(ConsensusRule.RSKIP126)
        );
    }

    protected synchronized TrieStore buildTrieStore(Path trieStorePath) {
        checkIfNotClosed();

        int statesCacheSize = getRskSystemProperties().getStatesCacheSize();
        KeyValueDataSource ds = LevelDbDataSource.makeDataSource(trieStorePath);

        if (statesCacheSize != 0) {
            CacheSnapshotHandler cacheSnapshotHandler = getRskSystemProperties().shouldPersistStatesCacheSnapshot()
                    ? new CacheSnapshotHandler(resolveCacheSnapshotPath(trieStorePath))
                    : null;
            ds = new DataSourceWithCache(ds, statesCacheSize, cacheSnapshotHandler);
        }

        return new TrieStoreImpl(ds);
    }

    protected synchronized RepositoryLocator buildRepositoryLocator() {
        checkIfNotClosed();

        return new RepositoryLocator(getTrieStore(), getStateRootHandler());
    }

    protected synchronized org.ethereum.db.BlockStore buildBlockStore() {
        checkIfNotClosed();

        return buildBlockStore(getRskSystemProperties().databaseDir());
    }

    protected StateRootsStore buildStateRootsStore() {
        checkIfNotClosed();

        int stateRootsCacheSize = getRskSystemProperties().getStateRootsCacheSize();
        KeyValueDataSource stateRootsDB = LevelDbDataSource.makeDataSource(Paths.get(getRskSystemProperties().databaseDir(), "stateRoots"));

        if (stateRootsCacheSize > 0) {
            stateRootsDB = new DataSourceWithCache(stateRootsDB, stateRootsCacheSize);
        }

        return new StateRootsStoreImpl(stateRootsDB);
    }

    protected synchronized RskSystemProperties buildRskSystemProperties() {
        checkIfNotClosed();

        return new RskSystemProperties(new ConfigLoader(cliArgs));
    }

    protected synchronized SyncConfiguration buildSyncConfiguration() {
        checkIfNotClosed();

        RskSystemProperties rskSystemProperties = getRskSystemProperties();
        return new SyncConfiguration(
                rskSystemProperties.getExpectedPeers(),
                rskSystemProperties.getTimeoutWaitingPeers(),
                rskSystemProperties.getTimeoutWaitingRequest(),
                rskSystemProperties.getExpirationTimePeerStatus(),
                rskSystemProperties.getMaxSkeletonChunks(),
                rskSystemProperties.getChunkSize(),
                rskSystemProperties.getMaxRequestedBodies(),
                rskSystemProperties.getLongSyncLimit());
    }

    protected synchronized StateRootHandler buildStateRootHandler() {
        checkIfNotClosed();

        return new StateRootHandler(getRskSystemProperties().getActivationConfig(), getStateRootsStore());
    }

    protected synchronized CompositeEthereumListener buildCompositeEthereumListener() {
        checkIfNotClosed();

        return new CompositeEthereumListener();
    }

    @Nullable
    protected synchronized Wallet buildWallet() {
        checkIfNotClosed();

        RskSystemProperties rskSystemProperties = getRskSystemProperties();
        if (!rskSystemProperties.isWalletEnabled()) {
            return null;
        }

        KeyValueDataSource ds = LevelDbDataSource.makeDataSource(Paths.get(rskSystemProperties.databaseDir(), "wallet"));
        return new Wallet(ds);
    }

    /***** Private Methods ********************************************************************************************/

    private void initializeSingletons() {
        Secp256k1.initialize(getRskSystemProperties());
    }

    private BlockTxSignatureCache getBlockTxSignatureCache() {
        if (blockTxSignatureCache == null) {
            blockTxSignatureCache = new BlockTxSignatureCache(getReceivedTxSignatureCache());
        }

        return blockTxSignatureCache;
    }

    private KeyValueDataSource getBlocksBloomDataSource() {
        if (this.blocksBloomDataSource == null) {
            this.blocksBloomDataSource = this.buildBlocksBloomDataSource();
        }

        return this.blocksBloomDataSource;
    }

    private TrieStore buildAbstractTrieStore(Path databasePath) {
        TrieStore newTrieStore;
        GarbageCollectorConfig gcConfig = getRskSystemProperties().garbageCollectorConfig();
        final String multiTrieStoreNamePrefix = "unitrie_";
        if (gcConfig.enabled()) {
            try {
                newTrieStore = buildMultiTrieStore(databasePath, multiTrieStoreNamePrefix, gcConfig.numberOfEpochs());
            } catch (IOException e) {
                throw new IllegalStateException("Unable to build multi trie store", e);
            }
        } else {
            Path trieStorePath = databasePath.resolve("unitrie");
            try (Stream<Path> databasePathFilesStream = Files.list(databasePath)) {
                List<Path> multiTrieStorePaths = databasePathFilesStream
                        .filter(p -> p.getFileName().toString().startsWith(multiTrieStoreNamePrefix))
                        .collect(Collectors.toList());

                boolean gcWasEnabled = !multiTrieStorePaths.isEmpty();
                if (gcWasEnabled) {
                    LevelDbDataSource.mergeDataSources(trieStorePath, multiTrieStorePaths);
                    // cleanup MultiTrieStore data sources
                    multiTrieStorePaths.stream()
                            .map(Path::toString)
                            .forEach(FileUtil::recursiveDelete);
                }
            } catch (IOException e) {
                logger.error("Unable to check if GC was ever enabled", e);
            }
            newTrieStore = buildTrieStore(trieStorePath);
        }
        return newTrieStore;
    }

    private TrieStore buildMultiTrieStore(Path databasePath, @SuppressWarnings("SameParameterValue") String namePrefix, int numberOfEpochs) throws IOException {
        int currentEpoch = numberOfEpochs;
        if (!getRskSystemProperties().databaseReset()) {
            try (Stream<Path> databasePaths = Files.list(databasePath)) {
                currentEpoch = databasePaths
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .filter(fileName -> fileName.startsWith(namePrefix))
                        .map(multiTrieStoreName -> multiTrieStoreName.replaceFirst(namePrefix, ""))
                        .map(Integer::valueOf)
                        .max(Comparator.naturalOrder())
                        .orElse(numberOfEpochs);
            }
            Path unitriePath = databasePath.resolve("unitrie");
            if (Files.exists(unitriePath)) {
                // moves the unitrie directory as the currentEpoch. It "knows" the internals of the MultiTrieStore constructor
                // to assign currentEpoch - 1 as the name
                Files.move(
                        unitriePath,
                        databasePath.resolve(namePrefix + (currentEpoch - 1))
                );
            }
        }

        return new MultiTrieStore(
                currentEpoch + 1,
                numberOfEpochs,
                name -> buildTrieStore(databasePath.resolve(namePrefix + name)),
                disposedEpoch -> FileUtil.recursiveDelete(databasePath.resolve(namePrefix + disposedEpoch).toString())
        );
    }

    protected PeerExplorer getPeerExplorer() {
        if (peerExplorer == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            ECKey key = rskSystemProperties.getMyKey();
            Node localNode = new Node(
                    key.getNodeId(),
                    rskSystemProperties.getPublicIp(),
                    rskSystemProperties.getPeerPort()
            );
            List<String> initialBootNodes = rskSystemProperties.peerDiscoveryIPList();
            List<Node> activePeers = rskSystemProperties.peerActive();
            if (activePeers != null) {
                for (Node n : activePeers) {
                    InetSocketAddress address = n.getAddress();
                    initialBootNodes.add(address.getHostName() + ":" + address.getPort());
                }
            }
            peerExplorer = new PeerExplorer(
                    initialBootNodes,
                    localNode,
                    new NodeDistanceTable(KademliaOptions.BINS, KademliaOptions.BUCKET_SIZE, localNode),
                    key,
                    rskSystemProperties.peerDiscoveryMessageTimeOut(),
                    rskSystemProperties.peerDiscoveryRefreshPeriod(),
                    rskSystemProperties.peerDiscoveryCleanPeriod(),
                    rskSystemProperties.networkId(),
                    getPeerScoringManager()
            );
        }

        return peerExplorer;
    }

    private BlockChainLoader getBlockChainLoader() {
        if (blockChainLoader == null) {
            blockChainLoader = new BlockChainLoader(
                    getBlockStore(),
                    getReceiptStore(),
                    getTransactionPool(),
                    getCompositeEthereumListener(),
                    getBlockValidator(),
                    getBlockExecutor(),
                    getGenesis(),
                    getStateRootHandler(),
                    getRepositoryLocator()
            );
        }

        return blockChainLoader;
    }

    private SyncConfiguration getSyncConfiguration() {
        if (syncConfiguration == null) {
            syncConfiguration = buildSyncConfiguration();
        }

        return syncConfiguration;
    }

    private BlockSyncService getBlockSyncService() {
        if (blockSyncService == null) {
            blockSyncService = new BlockSyncService(
                    getRskSystemProperties(),
                    getNetBlockStore(),
                    getBlockchain(),
                    getBlockNodeInformation(),
                    getSyncConfiguration(),
                    getBlockHeaderValidator());
        }

        return blockSyncService;
    }

    private BlockValidator getBlockValidator() {
        if (blockValidator == null) {
            blockValidator = buildBlockValidator();
        }

        return blockValidator;
    }

    private BlockValidator getBlockRelayValidator() {
        if (blockRelayValidator == null) {
            final RskSystemProperties rskSystemProperties = getRskSystemProperties();
            final Constants commonConstants = rskSystemProperties.getNetworkConstants();
            final BlockTimeStampValidationRule blockTimeStampValidationRule = new BlockTimeStampValidationRule(
                    commonConstants.getNewBlockMaxSecondsInTheFuture(),
                    rskSystemProperties.getActivationConfig()
            );

            final BlockHeaderParentDependantValidationRule blockParentValidator = new BlockHeaderParentCompositeRule(
                    new PrevMinGasPriceRule(),
                    new BlockParentGasLimitRule(commonConstants.getGasLimitBoundDivisor()),
                    new BlockDifficultyRule(getDifficultyCalculator()),
                    new BlockParentNumberRule(),
                    blockTimeStampValidationRule
            );

            final BlockValidationRule blockValidator = new ExtraDataRule(commonConstants.getMaximumExtraDataSize());

            blockRelayValidator = new BlockRelayValidatorImpl(
                    getBlockStore(),
                    blockParentValidator,
                    blockValidator
            );
        }

        return blockRelayValidator;
    }

    private BlockValidator getBlockHeaderValidator() {
        if (blockHeaderValidator == null) {
            final RskSystemProperties rskSystemProperties = getRskSystemProperties();
            final Constants commonConstants = rskSystemProperties.getNetworkConstants();
            final BlockTimeStampValidationRule blockTimeStampValidationRule = new BlockTimeStampValidationRule(
                    commonConstants.getNewBlockMaxSecondsInTheFuture(),
                    rskSystemProperties.getActivationConfig()
            );

            final BlockHeaderValidationRule blockHeaderValidationRule = new BlockHeaderCompositeRule(
                    getProofOfWorkRule(),
                    blockTimeStampValidationRule
            );

            blockHeaderValidator = new BlockHeaderValidatorImpl(blockHeaderValidationRule);
        }

        return blockHeaderValidator;
    }

    private EthereumChannelInitializerFactory getEthereumChannelInitializerFactory() {
        if (ethereumChannelInitializerFactory == null) {
            ethereumChannelInitializerFactory = remoteId -> new EthereumChannelInitializer(
                    remoteId,
                    getRskSystemProperties(),
                    getChannelManager(),
                    getCompositeEthereumListener(),
                    getConfigCapabilities(),
                    getNodeManager(),
                    getRskWireProtocolFactory(),
                    getEth62MessageFactory(),
                    getStaticMessages(),
                    getPeerScoringManager()
            );
        }

        return ethereumChannelInitializerFactory;
    }

    private Eth62MessageFactory getEth62MessageFactory() {
        if (eth62MessageFactory == null) {
            eth62MessageFactory = new Eth62MessageFactory(getBlockFactory());
        }

        return eth62MessageFactory;
    }

    private BlockValidationRule getMinerServerBlockValidationRule() {
        if (minerServerBlockValidationRule == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            Constants commonConstants = rskSystemProperties.getNetworkConstants();
            minerServerBlockValidationRule = new BlockUnclesValidationRule(
                    getBlockStore(),
                    commonConstants.getUncleListLimit(),
                    commonConstants.getUncleGenerationLimit(),
                    new BlockHeaderCompositeRule(
                            getProofOfWorkRule(),
                            new BlockTimeStampValidationRule(commonConstants.getNewBlockMaxSecondsInTheFuture(), rskSystemProperties.getActivationConfig()),
                            new ValidGasUsedRule()
                    ),
                    new BlockHeaderParentCompositeRule(
                            new PrevMinGasPriceRule(),
                            new BlockParentNumberRule(),
                            new BlockDifficultyRule(getDifficultyCalculator()),
                            new BlockParentGasLimitRule(commonConstants.getGasLimitBoundDivisor())
                    )
            );
        }

        return minerServerBlockValidationRule;
    }

    private ForkDetectionDataCalculator getForkDetectionDataCalculator() {
        if (forkDetectionDataCalculator == null) {
            forkDetectionDataCalculator = new ForkDetectionDataCalculator();
        }

        return forkDetectionDataCalculator;
    }

    private ProofOfWorkRule getProofOfWorkRule() {
        if (proofOfWorkRule == null) {
            proofOfWorkRule = new ProofOfWorkRule(getRskSystemProperties());
        }

        return proofOfWorkRule;
    }

    private ForkDetectionDataRule getForkDetectionDataRule() {
        if (forkDetectionDataRule == null) {
            forkDetectionDataRule = new ForkDetectionDataRule(
                    getRskSystemProperties().getActivationConfig(),
                    getConsensusValidationMainchainView(),
                    getForkDetectionDataCalculator(),
                    MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION
            );
        }

        return forkDetectionDataRule;
    }

    private DifficultyCalculator getDifficultyCalculator() {
        if (difficultyCalculator == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            difficultyCalculator = new DifficultyCalculator(
                    rskSystemProperties.getActivationConfig(),
                    rskSystemProperties.getNetworkConstants()
            );
        }

        return difficultyCalculator;
    }

    private BlockToMineBuilder getBlockToMineBuilder() {
        if (blockToMineBuilder == null) {
            blockToMineBuilder = new BlockToMineBuilder(
                    getRskSystemProperties().getActivationConfig(),
                    getMiningConfig(),
                    getRepositoryLocator(),
                    getBlockStore(),
                    getTransactionPool(),
                    getDifficultyCalculator(),
                    getGasLimitCalculator(),
                    getForkDetectionDataCalculator(),
                    getMinerServerBlockValidationRule(),
                    getMinerClock(),
                    getBlockFactory(),
                    getBlockExecutor(),
                    new MinimumGasPriceCalculator(Coin.valueOf(getMiningConfig().getMinGasPriceTarget())),
                    new MinerUtils()
            );
        }

        return blockToMineBuilder;
    }

    private BlockNodeInformation getBlockNodeInformation() {
        if (blockNodeInformation == null) {
            blockNodeInformation = new BlockNodeInformation();
        }

        return blockNodeInformation;
    }

    private MessageRecorder getMessageRecorder() {
        if (writerMessageRecorder == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            if (!rskSystemProperties.hasMessageRecorderEnabled()) {
                return null;
            }

            Path filePath = FileUtil.getDatabaseDirectoryPath(rskSystemProperties.databaseDir(), "messages");

            String fullFilename = filePath.toString();
            MessageFilter filter = new MessageFilter(rskSystemProperties.getMessageRecorderCommands());

            try {
                // This resource needs to remain open when method returns, so LGTM warning is disabled.
                writerMessageRecorder = new WriterMessageRecorder(
                        new BufferedWriter(
                                new OutputStreamWriter(new FileOutputStream(fullFilename), StandardCharsets.UTF_8) // lgtm [java/output-resource-leak]
                        ),
                        filter
                );
            } catch (IOException ex) {
                throw new IllegalArgumentException("Can't use this path to record messages", ex);
            }
        }

        return writerMessageRecorder;
    }

    private EthModuleWallet getEthModuleWallet() {
        if (ethModuleWallet == null) {
            Wallet wallet = getWallet();
            if (wallet == null) {
                ethModuleWallet = new EthModuleWalletDisabled();
            } else {
                ethModuleWallet = new EthModuleWalletEnabled(wallet);
            }
        }

        return ethModuleWallet;
    }

    private EthModuleTransaction getEthModuleTransaction() {
        if (ethModuleTransaction == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            Constants constants = rskSystemProperties.getNetworkConstants();
            Wallet wallet = getWallet();
            TransactionPool transactionPool = getTransactionPool();
            if (wallet == null) {
                ethModuleTransaction = new EthModuleTransactionDisabled(constants, transactionPool, getTransactionGateway());
            } else if (rskSystemProperties.minerClientAutoMine()) {
                ethModuleTransaction = new EthModuleTransactionInstant(
                        constants,
                        wallet,
                        transactionPool,
                        getMinerServer(),
                        getMinerClient(),
                        getBlockchain(),
                        getTransactionGateway(),
                        getBlockExecutor()
                );
            } else {
                ethModuleTransaction = new EthModuleTransactionBase(constants, wallet, transactionPool, getTransactionGateway());
            }
        }

        return ethModuleTransaction;
    }

    protected SyncProcessor getSyncProcessor() {
        if (syncProcessor == null) {
            syncProcessor = new SyncProcessor(
                    getBlockchain(),
                    getBlockStore(),
                    getConsensusValidationMainchainView(),
                    getBlockSyncService(),
                    getSyncConfiguration(),
                    getBlockFactory(),
                    getProofOfWorkRule(),
                    new SyncBlockValidatorRule(
                            new BlockUnclesHashValidationRule(),
                            new BlockRootValidationRule(getRskSystemProperties().getActivationConfig())
                    ),
                    getDifficultyCalculator(),
                    getPeersInformation(),
                    getGenesis(),
                    getCompositeEthereumListener());
        }

        return syncProcessor;
    }

    private PeersInformation getPeersInformation() {
        if (peersInformation == null) {
            peersInformation = new PeersInformation(
                    getChannelManager(),
                    getSyncConfiguration(),
                    getBlockchain(),
                    getPeerScoringManager());
        }
        return peersInformation;
    }

    private SyncPool getSyncPool() {
        if (syncPool == null) {
            syncPool = new SyncPool(
                    getCompositeEthereumListener(),
                    getBlockchain(),
                    getRskSystemProperties(),
                    getNodeManager(),
                    getNodeBlockProcessor(),
                    () -> new PeerClient(
                            getRskSystemProperties(),
                            getCompositeEthereumListener(),
                            getEthereumChannelInitializerFactory()
                    )
            );
        }

        return syncPool;
    }

    private Web3 getWeb3() {
        if (web3 == null) {
            web3 = buildWeb3();
        }

        return web3;
    }

    private JsonRpcWeb3FilterHandler getJsonRpcWeb3FilterHandler() {
        if (jsonRpcWeb3FilterHandler == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            jsonRpcWeb3FilterHandler = new JsonRpcWeb3FilterHandler(
                    rskSystemProperties.corsDomains(),
                    rskSystemProperties.rpcHttpBindAddress(),
                    rskSystemProperties.rpcHttpHost()
            );
        }

        return jsonRpcWeb3FilterHandler;
    }

    private JsonRpcWeb3ServerHandler getJsonRpcWeb3ServerHandler() {
        if (jsonRpcWeb3ServerHandler == null) {
            jsonRpcWeb3ServerHandler = new JsonRpcWeb3ServerHandler(
                    getWeb3(),
                    getRskSystemProperties().getRpcModules()
            );
        }

        return jsonRpcWeb3ServerHandler;
    }

    private Web3WebSocketServer getWeb3WebSocketServer() {
        if (web3WebSocketServer == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            JsonRpcSerializer jsonRpcSerializer = getJsonRpcSerializer();
            Ethereum rsk = getRsk();
            SyncProcessor syncProcessor = getSyncProcessor();
            EthSubscriptionNotificationEmitter emitter = new EthSubscriptionNotificationEmitter(
                    new BlockHeaderNotificationEmitter(rsk, jsonRpcSerializer),
                    new LogsNotificationEmitter(
                            rsk,
                            jsonRpcSerializer,
                            getReceiptStore(),
                            new BlockchainBranchComparator(getBlockStore())
                    ),
                    new PendingTransactionsNotificationEmitter(rsk, jsonRpcSerializer),
                    new SyncNotificationEmitter(rsk, jsonRpcSerializer, blockchain, syncProcessor)
            );
            RskWebSocketJsonRpcHandler jsonRpcHandler = new RskWebSocketJsonRpcHandler(emitter);
            web3WebSocketServer = new Web3WebSocketServer(
                    rskSystemProperties.rpcWebSocketBindAddress(),
                    rskSystemProperties.rpcWebSocketPort(),
                    jsonRpcHandler,
                    getJsonRpcWeb3ServerHandler(),
                    rskSystemProperties.rpcWebSocketServerWriteTimeoutSeconds(),
                    rskSystemProperties.rpcWebSocketMaxFrameSize(),
                    rskSystemProperties.rpcWebSocketMaxAggregatedFrameSize()
            );
        }

        return web3WebSocketServer;
    }

    private Web3HttpServer getWeb3HttpServer() {
        if (web3HttpServer == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            web3HttpServer = new Web3HttpServer(
                    rskSystemProperties.rpcHttpBindAddress(),
                    rskSystemProperties.rpcHttpPort(),
                    rskSystemProperties.soLingerTime(),
                    true,
                    new CorsConfiguration(rskSystemProperties.corsDomains()),
                    getJsonRpcWeb3FilterHandler(),
                    getJsonRpcWeb3ServerHandler()
            );
        }

        return web3HttpServer;
    }

    private JsonRpcSerializer getJsonRpcSerializer() {
        if (jacksonBasedRpcSerializer == null) {
            jacksonBasedRpcSerializer = new JacksonBasedRpcSerializer();
        }

        return jacksonBasedRpcSerializer;
    }

    private NetBlockStore getNetBlockStore() {
        if (netBlockStore == null) {
            netBlockStore = new NetBlockStore();
        }

        return netBlockStore;
    }

    private TransactionGateway getTransactionGateway() {
        if (transactionGateway == null) {
            transactionGateway = new TransactionGateway(
                    getChannelManager(),
                    getTransactionPool()
            );
        }

        return transactionGateway;
    }

    private NodeMessageHandler getNodeMessageHandler() {
        if (nodeMessageHandler == null) {
            nodeMessageHandler = new NodeMessageHandler(
                    getRskSystemProperties(),
                    getNodeBlockProcessor(),
                    getSyncProcessor(),
                    getChannelManager(),
                    getTransactionGateway(),
                    getPeerScoringManager(),
                    getStatusResolver());
        }

        return nodeMessageHandler;
    }

    private StatusResolver getStatusResolver() {
        if (statusResolver == null) {
            statusResolver = new StatusResolver(getBlockStore(), getGenesis());
        }
        return statusResolver;
    }

    private RskWireProtocol.Factory getRskWireProtocolFactory() {
        if (rskWireProtocolFactory == null) {
            rskWireProtocolFactory = (messageQueue, channel) -> new RskWireProtocol(
                    getRskSystemProperties(),
                    getPeerScoringManager(),
                    getNodeMessageHandler(),
                    getCompositeEthereumListener(),
                    getGenesis(),
                    getMessageRecorder(),
                    getStatusResolver(),
                    messageQueue,
                    channel);
        }

        return rskWireProtocolFactory;
    }

    private MiningConfig getMiningConfig() {
        if (miningConfig == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            miningConfig = new MiningConfig(
                    rskSystemProperties.coinbaseAddress(),
                    rskSystemProperties.minerMinFeesNotifyInDollars(),
                    rskSystemProperties.minerGasUnitInDollars(),
                    rskSystemProperties.minerMinGasPrice(),
                    rskSystemProperties.getNetworkConstants().getUncleListLimit(),
                    rskSystemProperties.getNetworkConstants().getUncleGenerationLimit(),
                    new GasLimitConfig(
                            rskSystemProperties.getNetworkConstants().getMinGasLimit(),
                            rskSystemProperties.getTargetGasLimit(),
                            rskSystemProperties.getForceTargetGasLimit()
                    ),
                    rskSystemProperties.isMinerServerFixedClock(),
                    rskSystemProperties.workSubmissionRateLimitInMills()
            );
        }

        return miningConfig;
    }

    private GasLimitCalculator getGasLimitCalculator() {
        if (gasLimitCalculator == null) {
            gasLimitCalculator = new GasLimitCalculator(getRskSystemProperties().getNetworkConstants());
        }

        return gasLimitCalculator;
    }

    private ExecutionBlockRetriever getExecutionBlockRetriever() {
        if (executionBlockRetriever == null) {
            executionBlockRetriever = new ExecutionBlockRetriever(
                    getMiningMainchainView(),
                    getBlockchain(),
                    getMinerServer(),
                    getBlockToMineBuilder()
            );
        }

        return executionBlockRetriever;
    }

    private NodeManager getNodeManager() {
        if (nodeManager == null) {
            nodeManager = new NodeManager(getPeerExplorer(), getRskSystemProperties());
        }

        return nodeManager;
    }

    private StaticMessages getStaticMessages() {
        if (staticMessages == null) {
            staticMessages = new StaticMessages(getRskSystemProperties(), getConfigCapabilities());
        }

        return staticMessages;
    }

    private MinerClock getMinerClock() {
        if (minerClock == null) {
            minerClock = new MinerClock(getMiningConfig().isFixedClock(), Clock.systemUTC());
        }

        return minerClock;
    }

    private void checkIfNotClosed() {
        if (closed) {
            throw new IllegalStateException("RSK Context is closed and cannot be in use anymore");
        }
    }
}
