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

import co.rsk.cli.CliArgs;
import co.rsk.config.*;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.*;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockValidatorImpl;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryImpl;
import co.rsk.db.StateRootHandler;
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
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.rpc.*;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.eth.*;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.evm.EvmModuleImpl;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.mnr.MnrModuleImpl;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletDisabled;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.rpc.netty.*;
import co.rsk.scoring.PeerScoring;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PunishmentParameters;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.util.RskCustomCache;
import co.rsk.validators.*;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.ethereum.core.genesis.BlockChainLoader;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.crypto.ECKey;
import org.ethereum.datasource.DataSourceWithCache;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.db.TrieStorePoolOnDisk;
import org.ethereum.listener.CompositeEthereumListener;
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
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.ethereum.db.IndexedBlockStore.BLOCK_INFO_SERIALIZER;


/**
 * Creates the initial object graph without a DI framework.
 *
 * When an object construction has to be tweaked, create a buildXXX method and call it from getXXX.
 * This way derived classes don't have to store their own instances.
 *
 * Note that many methods are public to allow the fed node overriding.
 */
public class RskContext implements NodeBootstrapper {
    private static Logger logger = LoggerFactory.getLogger(RskContext.class);

    private final CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;

    private RskSystemProperties rskSystemProperties;
    private Blockchain blockchain;
    private BlockFactory blockFactory;
    private BlockChainLoader blockChainLoader;
    private BlockExecutor blockExecutor;
    private org.ethereum.db.BlockStore blockStore;
    private co.rsk.net.BlockStore netBlockStore;
    private Repository repository;
    private Genesis genesis;
    private CompositeEthereumListener compositeEthereumListener;
    private DifficultyCalculator difficultyCalculator;
    private ProofOfWorkRule proofOfWorkRule;
    private BlockParentDependantValidationRule blockParentDependantValidationRule;
    private BlockValidationRule blockValidationRule;
    private BlockValidationRule minerServerBlockValidationRule;
    private BlockValidator blockValidator;
    private ReceiptStore receiptStore;
    private ProgramInvokeFactory programInvokeFactory;
    private TransactionPool transactionPool;
    private StateRootHandler stateRootHandler;
    private EvmModule evmModule;
    private BlockToMineBuilder blockToMineBuilder;
    private BlockNodeInformation blockNodeInformation;
    private Rsk rsk;
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
    private EthModuleSolidity ethModuleSolidity;
    private EthModuleTransaction ethModuleTransaction;
    private MinerClient minerClient;
    private SyncConfiguration syncConfiguration;
    private TransactionGateway transactionGateway;
    private BuildInfo buildInfo;
    private MinerClock minerClock;
    private MiningConfig miningConfig;
    private NetworkStateExporter networkStateExporter;
    private PeerExplorer peerExplorer;
    private UDPServer udpServer;
    private SyncPool.PeerClientFactory peerClientFactory;
    private EthereumChannelInitializerFactory ethereumChannelInitializerFactory;
    private HashRateCalculator hashRateCalculator;
    private EthModule ethModule;
    private ChannelManager channelManager;
    private NodeRunner nodeRunner;
    private NodeMessageHandler nodeMessageHandler;
    private ConfigCapabilities configCapabilities;
    private DebugModule debugModule;
    private MnrModule mnrModule;
    private TxPoolModule txPoolModule;
    private RskWireProtocol.Factory rskWireProtocolFactory;
    private Eth62MessageFactory eth62MessageFactory;
    private GasLimitCalculator gasLimitCalculator;
    private ReversibleTransactionExecutor reversibleTransactionExecutor;
    private TransactionExecutorFactory transactionExecutorFactory;
    private ExecutionBlockRetriever executionBlockRetriever;
    private NodeManager nodeManager;
    private StaticMessages staticMessages;
    private MinerServer minerServer;
    private SolidityCompiler solidityCompiler;
    private BlocksBloomStore blocksBloomStore;

    public RskContext(String[] args) {
        this(new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        ).parse(args));
    }

    private RskContext(CliArgs<NodeCliOptions, NodeCliFlags> cliArgs) {
        this.cliArgs = cliArgs;
    }

    @Override
    public NodeRunner getNodeRunner() {
        if (nodeRunner == null) {
            nodeRunner = buildNodeRunner();
        }

        return nodeRunner;
    }

    public Blockchain getBlockchain() {
        if (blockchain == null) {
            blockchain = getBlockChainLoader().loadBlockchain();
        }

        return blockchain;
    }

    public BlockFactory getBlockFactory() {
        if (blockFactory == null) {
            blockFactory = new BlockFactory(getRskSystemProperties().getActivationConfig());
        }

        return blockFactory;
    }

    public TransactionPool getTransactionPool() {
        if (transactionPool == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            transactionPool = new TransactionPoolImpl(
                    rskSystemProperties,
                    getRepository(),
                    getBlockStore(),
                    getBlockFactory(),
                    getCompositeEthereumListener(),
                    getTransactionExecutorFactory(),
                    rskSystemProperties.txOutdatedThreshold(),
                    rskSystemProperties.txOutdatedTimeout()
            );
        }

        return transactionPool;
    }

    public StateRootHandler getStateRootHandler() {
        if (stateRootHandler == null) {
            stateRootHandler = buildStateRootHandler();
        }

        return stateRootHandler;
    }

    public ReceiptStore getReceiptStore() {
        if (receiptStore == null) {
            receiptStore = buildReceiptStore();
        }

        return receiptStore;
    }

    public Repository getRepository() {
        if (repository == null) {
            repository = buildRepository();
        }

        return repository;
    }

    public org.ethereum.db.BlockStore getBlockStore() {
        if (blockStore == null) {
            blockStore = buildBlockStore();
        }

        return blockStore;
    }

    public Rsk getRsk() {
        if (rsk == null) {
            rsk = new RskImpl(
                    getChannelManager(),
                    getTransactionPool(),
                    getCompositeEthereumListener(),
                    getNodeBlockProcessor(),
                    getBlockchain()
            );
        }

        return rsk;
    }

    public ReversibleTransactionExecutor getReversibleTransactionExecutor() {
        if (reversibleTransactionExecutor == null) {
            reversibleTransactionExecutor = new ReversibleTransactionExecutor(
                    getRepository(),
                    getTransactionExecutorFactory()
            );
        }

        return reversibleTransactionExecutor;
    }

    public TransactionExecutorFactory getTransactionExecutorFactory() {
        if (transactionExecutorFactory == null) {
            transactionExecutorFactory = new TransactionExecutorFactory(
                    getRskSystemProperties(),
                    getBlockStore(),
                    getReceiptStore(),
                    getBlockFactory(),
                    getProgramInvokeFactory(),
                    getCompositeEthereumListener()
            );
        }

        return transactionExecutorFactory;
    }

    public NodeBlockProcessor getNodeBlockProcessor() {
        if (nodeBlockProcessor == null) {
            nodeBlockProcessor = new NodeBlockProcessor(
                    getNetBlockStore(),
                    getBlockchain(),
                    getBlockNodeInformation(),
                    getBlockSyncService(),
                    getSyncConfiguration()
            );
        }

        return nodeBlockProcessor;
    }

    public RskSystemProperties getRskSystemProperties() {
        if (rskSystemProperties == null) {
            rskSystemProperties = buildRskSystemProperties();
        }

        return rskSystemProperties;
    }

    public PeerScoringManager getPeerScoringManager() {
        if (peerScoringManager == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
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
                    )
            );
        }

        return peerScoringManager;
    }

    public HashRateCalculator getHashRateCalculator() {
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

    public EthModule getEthModule() {
        if (ethModule == null) {
            ethModule = new EthModule(
                    getRskSystemProperties().getBlockchainConfig().getCommonConstants().getBridgeConstants(),
                    getRskSystemProperties().getActivationConfig(),
                    getBlockchain(),
                    getReversibleTransactionExecutor(),
                    getExecutionBlockRetriever(),
                    getEthModuleSolidity(),
                    getEthModuleWallet(),
                    getEthModuleTransaction()
            );
        }

        return ethModule;
    }

    public EvmModule getEvmModule() {
        if (evmModule == null) {
            evmModule = new EvmModuleImpl(
                    getMinerServer(),
                    getMinerClient(),
                    getMinerClock(),
                    getBlockchain(),
                    getTransactionPool()
            );
        }

        return evmModule;
    }

    public PeerServer getPeerServer() {
        if (peerServer == null) {
            peerServer = new PeerServerImpl(
                    getRskSystemProperties(),
                    getCompositeEthereumListener(),
                    getEthereumChannelInitializerFactory()
            );
        }

        return peerServer;
    }

    public PersonalModule getPersonalModule() {
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

    public CliArgs<NodeCliOptions, NodeCliFlags> getCliArgs() {
        return cliArgs;
    }

    public BuildInfo getBuildInfo() {
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

    public ChannelManager getChannelManager() {
        if (channelManager == null) {
            channelManager = new ChannelManagerImpl(getRskSystemProperties(), getSyncPool());
        }

        return channelManager;
    }

    public ConfigCapabilities getConfigCapabilities() {
        if (configCapabilities == null) {
            configCapabilities = new ConfigCapabilitiesImpl(getRskSystemProperties());
        }

        return configCapabilities;
    }

    public DebugModule getDebugModule() {
        if (debugModule == null) {
            debugModule = new DebugModuleImpl(getNodeMessageHandler());
        }

        return debugModule;
    }

    public MnrModule getMnrModule() {
        if (mnrModule == null) {
            mnrModule = new MnrModuleImpl(getMinerServer());
        }

        return mnrModule;
    }

    public TxPoolModule getTxPoolModule() {
        if (txPoolModule == null) {
            txPoolModule = new TxPoolModuleImpl(getTransactionPool());
        }

        return txPoolModule;
    }

    public NetworkStateExporter getNetworkStateExporter() {
        if (networkStateExporter == null) {
            networkStateExporter = new NetworkStateExporter(getRepository());
        }

        return networkStateExporter;
    }

    public MinerClient getMinerClient() {
        if (minerClient == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            if (rskSystemProperties.minerClientAutoMine()) {
                minerClient = new AutoMinerClient(getMinerServer());
            } else {
                minerClient = new MinerClientImpl(
                        getRsk(),
                        getMinerServer(),
                        rskSystemProperties.minerClientDelayBetweenBlocks(),
                        rskSystemProperties.minerClientDelayBetweenRefreshes()
                );
            }
        }

        return minerClient;
    }

    public MinerServer getMinerServer() {
        if (minerServer == null) {
            minerServer = new MinerServerImpl(
                    getRskSystemProperties(),
                    getRsk(),
                    getBlockchain(),
                    getNodeBlockProcessor(),
                    getProofOfWorkRule(),
                    getBlockToMineBuilder(),
                    getMinerClock(),
                    getBlockFactory(),
                    getMiningConfig()
            );
        }

        return minerServer;
    }

    public ProgramInvokeFactory getProgramInvokeFactory() {
        if (programInvokeFactory == null) {
            programInvokeFactory = new ProgramInvokeFactoryImpl();
        }

        return programInvokeFactory;
    }

    public CompositeEthereumListener getCompositeEthereumListener() {
        if (compositeEthereumListener == null) {
            compositeEthereumListener = buildCompositeEthereumListener();
        }

        return compositeEthereumListener;
    }

    public BlocksBloomStore getBlocksBloomStore() {
        if (blocksBloomStore == null) {
            blocksBloomStore = new BlocksBloomStore(64, 20);
        }

        return blocksBloomStore;
    }

    protected NodeRunner buildNodeRunner() {
        return new FullNodeRunner(
                getRsk(),
                getUdpServer(),
                getMinerServer(),
                getMinerClient(),
                getRskSystemProperties(),
                getWeb3(),
                getWeb3HttpServer(),
                getWeb3WebSocketServer(),
                getRepository(),
                getBlockchain(),
                getChannelManager(),
                getSyncPool(),
                getNodeMessageHandler(),
                getNodeBlockProcessor(),
                getTransactionPool(),
                getPeerServer(),
                getPeerClientFactory(),
                getTransactionGateway(),
                getBuildInfo()
        );
    }

    protected SolidityCompiler buildSolidityCompiler() {
        return new SolidityCompiler(getRskSystemProperties());
    }

    protected Web3 buildWeb3() {
        return new Web3RskImpl(
                getRsk(),
                getBlockchain(),
                getTransactionPool(),
                getRskSystemProperties(),
                getMinerClient(),
                getMinerServer(),
                getPersonalModule(),
                getEthModule(),
                getEvmModule(),
                getTxPoolModule(),
                getMnrModule(),
                getDebugModule(),
                getChannelManager(),
                getRepository(),
                getPeerScoringManager(),
                getNetworkStateExporter(),
                getBlockStore(),
                getReceiptStore(),
                getPeerServer(),
                getNodeBlockProcessor(),
                getHashRateCalculator(),
                getConfigCapabilities(),
                getBuildInfo(),
                getBlocksBloomStore()
        );
    }

    protected ReceiptStore buildReceiptStore() {
        KeyValueDataSource ds = makeDataSource("receipts", getRskSystemProperties().databaseDir());
        return new ReceiptStoreImpl(ds);
    }

    protected BlockValidator buildBlockValidator() {
        return new BlockValidatorImpl(
                getBlockStore(),
                getBlockParentDependantValidationRule(),
                getBlockValidationRule()
        );
    }

    protected Genesis buildGenesis() {
        RskSystemProperties rskSystemProperties = getRskSystemProperties();
        BlockchainNetConfig blockchainConfig = rskSystemProperties.getBlockchainConfig();
        return GenesisLoader.loadGenesis(
                rskSystemProperties.genesisInfo(),
                blockchainConfig.getCommonConstants().getInitialNonce(),
                true,
                blockchainConfig.getConfigForBlock(0).isRskip92()
        );
    }

    protected Repository buildRepository() {
        RskSystemProperties rskSystemProperties = getRskSystemProperties();
        String databaseDir = rskSystemProperties.databaseDir();
        if (rskSystemProperties.databaseReset()) {
            FileUtil.recursiveDelete(databaseDir);
            try {
                Files.createDirectories(FileUtil.getDatabaseDirectoryPath(databaseDir, "database"));
            } catch (IOException e) {
                throw new IllegalStateException("Could not re-create database directory", e);
            }
        }

        int statesCacheSize = rskSystemProperties.getStatesCacheSize();
        KeyValueDataSource ds = makeDataSource("state", databaseDir);
        KeyValueDataSource detailsDS = makeDataSource("details", databaseDir);

        if (statesCacheSize != 0) {
            ds = new DataSourceWithCache(ds, statesCacheSize);
        }

        return new RepositoryImpl(
                new Trie(new TrieStoreImpl(ds), true),
                detailsDS,
                new TrieStorePoolOnDisk(databaseDir)
        );
    }

    protected org.ethereum.db.BlockStore buildBlockStore() {
        return buildBlockStore(getBlockFactory(), getRskSystemProperties().databaseDir());
    }

    protected RskSystemProperties buildRskSystemProperties() {
        return new RskSystemProperties(new ConfigLoader(cliArgs));
    }

    protected SyncConfiguration buildSyncConfiguration() {
        RskSystemProperties rskSystemProperties = getRskSystemProperties();
        return new SyncConfiguration(
                rskSystemProperties.getExpectedPeers(),
                rskSystemProperties.getTimeoutWaitingPeers(),
                rskSystemProperties.getTimeoutWaitingRequest(),
                rskSystemProperties.getExpirationTimePeerStatus(),
                rskSystemProperties.getMaxSkeletonChunks(),
                rskSystemProperties.getChunkSize()
        );
    }

    protected StateRootHandler buildStateRootHandler() {
        KeyValueDataSource stateRootsDB = makeDataSource("stateRoots", getRskSystemProperties().databaseDir());
        return new StateRootHandler(getRskSystemProperties().getActivationConfig(), stateRootsDB, new HashMap<>());
    }

    protected CompositeEthereumListener buildCompositeEthereumListener() {
        return new CompositeEthereumListener();
    }

    private PeerExplorer getPeerExplorer() {
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
                    rskSystemProperties.networkId()
            );
        }

        return peerExplorer;
    }

    protected Wallet buildWallet() {
        RskSystemProperties rskSystemProperties = getRskSystemProperties();
        if (!rskSystemProperties.isWalletEnabled()) {
            return null;
        }

        KeyValueDataSource ds = makeDataSource("wallet", rskSystemProperties.databaseDir());
        return new Wallet(ds);
    }

    public Genesis getGenesis() {
        if (genesis == null) {
            genesis = buildGenesis();
        }

        return genesis;
    }

    private BlockChainLoader getBlockChainLoader() {
        if (blockChainLoader == null) {
            blockChainLoader = new BlockChainLoader(
                    getRskSystemProperties(),
                    getRepository(),
                    getBlockStore(),
                    getReceiptStore(),
                    getTransactionPool(),
                    getCompositeEthereumListener(),
                    getBlockValidator(),
                    getBlockExecutor(),
                    getGenesis(),
                    getStateRootHandler()
            );
        }

        return blockChainLoader;
    }

    public BlockExecutor getBlockExecutor() {
        if (blockExecutor == null) {
            blockExecutor = new BlockExecutor(
                    getRepository(),
                    getTransactionExecutorFactory(),
                    getStateRootHandler()
            );
        }

        return blockExecutor;
    }

    private SyncConfiguration getSyncConfiguration() {
        if (syncConfiguration == null) {
            syncConfiguration = buildSyncConfiguration();
        }

        return syncConfiguration;
    }

    public Wallet getWallet() {
        if (wallet == null) {
            wallet = buildWallet();
        }

        return wallet;
    }

    private BlockSyncService getBlockSyncService() {
        if (blockSyncService == null) {
            blockSyncService = new BlockSyncService(
                    getRskSystemProperties(),
                    getNetBlockStore(),
                    getBlockchain(),
                    getBlockNodeInformation(),
                    getSyncConfiguration()
            );
        }

        return blockSyncService;
    }

    private BlockValidator getBlockValidator() {
        if (blockValidator == null) {
            blockValidator = buildBlockValidator();
        }

        return blockValidator;
    }

    private SyncPool.PeerClientFactory getPeerClientFactory() {
        if (peerClientFactory == null) {
            peerClientFactory = () -> new PeerClient(
                    getRskSystemProperties(),
                    getCompositeEthereumListener(),
                    getEthereumChannelInitializerFactory()
            );
        }

        return peerClientFactory;
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

    private BlockValidationRule getBlockValidationRule() {
        if (blockValidationRule == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            Constants commonConstants = rskSystemProperties.getBlockchainConfig().getCommonConstants();
            BlockTimeStampValidationRule blockTimeStampValidationRule = new BlockTimeStampValidationRule(
                    commonConstants.getNewBlockMaxSecondsInTheFuture()
            );
            blockValidationRule = new BlockCompositeRule(
                    new TxsMinGasPriceRule(),
                    new BlockUnclesValidationRule(
                            getBlockStore(),
                            commonConstants.getUncleListLimit(),
                            commonConstants.getUncleGenerationLimit(),
                            new BlockHeaderCompositeRule(
                                    getProofOfWorkRule(),
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
                    new BlockRootValidationRule(),
                    new RemascValidationRule(),
                    blockTimeStampValidationRule,
                    new GasLimitRule(commonConstants.getMinGasLimit()),
                    new ExtraDataRule(commonConstants.getMaximumExtraDataSize())
            );
        }

        return blockValidationRule;
    }

    private BlockValidationRule getMinerServerBlockValidationRule() {
        if (minerServerBlockValidationRule == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            Constants commonConstants = rskSystemProperties.getBlockchainConfig().getCommonConstants();
            minerServerBlockValidationRule = new BlockUnclesValidationRule(
                    getBlockStore(),
                    commonConstants.getUncleListLimit(),
                    commonConstants.getUncleGenerationLimit(),
                    new BlockHeaderCompositeRule(
                            getProofOfWorkRule(),
                            new BlockTimeStampValidationRule(commonConstants.getNewBlockMaxSecondsInTheFuture()),
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

    private UDPServer getUdpServer() {
        if (udpServer == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            udpServer = new UDPServer(
                    rskSystemProperties.getBindAddress().getHostAddress(),
                    rskSystemProperties.getPeerPort(),
                    getPeerExplorer()
            );
        }

        return udpServer;
    }

    private BlockParentDependantValidationRule getBlockParentDependantValidationRule() {
        if (blockParentDependantValidationRule == null) {
            Constants commonConstants = getRskSystemProperties().getBlockchainConfig().getCommonConstants();
            blockParentDependantValidationRule = new BlockParentCompositeRule(
                    new BlockTxsFieldsValidationRule(),
                    new BlockTxsValidationRule(getRepository(), getStateRootHandler()),
                    new PrevMinGasPriceRule(),
                    new BlockParentNumberRule(),
                    new BlockDifficultyRule(getDifficultyCalculator()),
                    new BlockParentGasLimitRule(commonConstants.getGasLimitBoundDivisor())
            );
        }

        return blockParentDependantValidationRule;
    }

    private ProofOfWorkRule getProofOfWorkRule() {
        if (proofOfWorkRule == null) {
            proofOfWorkRule = new ProofOfWorkRule(getRskSystemProperties());
        }

        return proofOfWorkRule;
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
                    getMiningConfig(),
                    getRepository(),
                    getBlockStore(),
                    getTransactionPool(),
                    getDifficultyCalculator(),
                    getGasLimitCalculator(),
                    getMinerServerBlockValidationRule(),
                    getMinerClock(),
                    getBlockFactory(),
                    getStateRootHandler(),
                    getTransactionExecutorFactory()
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
                writerMessageRecorder = new WriterMessageRecorder(
                        new BufferedWriter(
                                new OutputStreamWriter(new FileOutputStream(fullFilename), StandardCharsets.UTF_8)
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

    private EthModuleSolidity getEthModuleSolidity() {
        if (ethModuleSolidity == null) {
            try {
                ethModuleSolidity = new EthModuleSolidityEnabled(getSolidityCompiler());
            } catch (RuntimeException e) {
                logger.trace("Can't find find Solidity compiler, disabling Solidity support in Web3", e);
                ethModuleSolidity = new EthModuleSolidityDisabled();
            }
        }

        return ethModuleSolidity;
    }

    private SolidityCompiler getSolidityCompiler() {
        if (solidityCompiler == null) {
            solidityCompiler = buildSolidityCompiler();
        }

        return solidityCompiler;
    }

    private EthModuleTransaction getEthModuleTransaction() {
        if (ethModuleTransaction == null) {
            RskSystemProperties rskSystemProperties = getRskSystemProperties();
            Constants constants = rskSystemProperties.getNetworkConstants();
            Wallet wallet = getWallet();
            TransactionPool transactionPool = getTransactionPool();
            if (wallet == null) {
                ethModuleTransaction = new EthModuleTransactionDisabled(constants, transactionPool);
            } else if (rskSystemProperties.minerClientAutoMine()) {
                ethModuleTransaction = new EthModuleTransactionInstant(
                        constants,
                        wallet,
                        transactionPool,
                        getMinerServer(),
                        getMinerClient(),
                        getBlockchain()
                );
            } else {
                ethModuleTransaction = new EthModuleTransactionBase(constants, wallet, transactionPool);
            }
        }

        return ethModuleTransaction;
    }

    private SyncProcessor getSyncProcessor() {
        // TODO(lsebrie): add new BlockCompositeRule(new ProofOfWorkRule(), blockTimeStampValidationRule, new ValidGasUsedRule());
        if (syncProcessor == null) {
            syncProcessor = new SyncProcessor(
                    getBlockchain(),
                    getBlockSyncService(),
                    getPeerScoringManager(),
                    getChannelManager(),
                    getSyncConfiguration(),
                    getProofOfWorkRule(),
                    getDifficultyCalculator()
            );
        }

        return syncProcessor;
    }

    private SyncPool getSyncPool() {
        if (syncPool == null) {
            syncPool = new SyncPool(
                    getCompositeEthereumListener(),
                    getBlockchain(),
                    getRskSystemProperties(),
                    getNodeManager()
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
            EthSubscriptionNotificationEmitter emitter = new EthSubscriptionNotificationEmitter(
                    getRsk(),
                    jsonRpcSerializer
            );
            RskJsonRpcHandler jsonRpcHandler = new RskJsonRpcHandler(emitter, jsonRpcSerializer);
            web3WebSocketServer = new Web3WebSocketServer(
                    rskSystemProperties.rpcWebSocketBindAddress(),
                    rskSystemProperties.rpcWebSocketPort(),
                    jsonRpcHandler,
                    getJsonRpcWeb3ServerHandler()
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

    private co.rsk.net.BlockStore getNetBlockStore() {
        if (netBlockStore == null) {
            netBlockStore = new co.rsk.net.BlockStore();
        }

        return netBlockStore;
    }

    private TransactionGateway getTransactionGateway() {
        if (transactionGateway == null) {
            transactionGateway = new TransactionGateway(
                    getChannelManager(),
                    getTransactionPool(),
                    getCompositeEthereumListener()
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
                    getBlockValidationRule()
            );
        }

        return nodeMessageHandler;
    }

    private RskWireProtocol.Factory getRskWireProtocolFactory() {
        if (rskWireProtocolFactory == null) {
            rskWireProtocolFactory = () -> new RskWireProtocol(
                    getRskSystemProperties(),
                    getPeerScoringManager(),
                    getNodeMessageHandler(),
                    getBlockchain(),
                    getCompositeEthereumListener(),
                    getGenesis(),
                    getMessageRecorder());
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
                    rskSystemProperties.getBlockchainConfig().getCommonConstants().getUncleListLimit(),
                    rskSystemProperties.getBlockchainConfig().getCommonConstants().getUncleGenerationLimit(),
                    new GasLimitConfig(
                            rskSystemProperties.getBlockchainConfig().getCommonConstants().getMinGasLimit(),
                            rskSystemProperties.getTargetGasLimit(),
                            rskSystemProperties.getForceTargetGasLimit()
                    ),
                    rskSystemProperties.isMinerServerFixedClock()
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

    public static org.ethereum.db.BlockStore buildBlockStore(BlockFactory blockFactory, String databaseDir) {
        File blockIndexDirectory = new File(databaseDir + "/blocks/");
        File dbFile = new File(blockIndexDirectory, "index");
        if (!blockIndexDirectory.exists()) {
            boolean mkdirsSuccess = blockIndexDirectory.mkdirs();
            if (!mkdirsSuccess) {
                throw new IllegalArgumentException(String.format(
                        "Unable to create blocks directory: %s", blockIndexDirectory
                ));
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

        KeyValueDataSource blocksDB = makeDataSource("blocks", databaseDir);

        return new IndexedBlockStore(blockFactory, indexMap, blocksDB, indexDB);
    }

    private static KeyValueDataSource makeDataSource(String name, String databaseDir) {
        KeyValueDataSource ds = new LevelDbDataSource(name, databaseDir);
        ds.init();
        return ds;
    }
}
