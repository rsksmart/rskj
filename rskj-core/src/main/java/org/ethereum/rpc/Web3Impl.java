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
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.SnapshotManager;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.crypto.Keccak256;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerManager;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.net.notifications.processing.FederationNotificationProcessor;
import co.rsk.net.notifications.processing.NodeFederationNotificationProcessor;
import co.rsk.net.notifications.panics.PanicFlag;
import co.rsk.net.notifications.alerts.FederationAlert;
import co.rsk.rpc.ModuleDescription;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.scoring.InvalidInetAddressException;
import co.rsk.scoring.PeerScoringInformation;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.client.Capability;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.ethereum.rpc.exception.JsonRpcInvalidParamException;
import org.ethereum.rpc.exception.JsonRpcUnimplementedMethodException;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static java.lang.Math.max;
import static org.ethereum.rpc.TypeConverter.*;

public class Web3Impl implements Web3 {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private final MinerManager minerManager = new MinerManager();
    private final String baseClientVersion = "RskJ";
    private final MinerClient minerClient;
    private final ChannelManager channelManager;
    private final PeerScoringManager peerScoringManager;
    private final PeerServer peerServer;
    private final Blockchain blockchain;
    private final ReceiptStore receiptStore;
    private final BlockProcessor nodeBlockProcessor;
    private final HashRateCalculator hashRateCalculator;
    private final ConfigCapabilities configCapabilities;
    private final BlockStore blockStore;
    private final TransactionPool transactionPool;
    private final RskSystemProperties config;
    private final FilterManager filterManager;
    private final SnapshotManager snapshotManager;
    private final PersonalModule personalModule;
    private final EthModule ethModule;
    private final TxPoolModule txPoolModule;
    private final MnrModule mnrModule;
    private final DebugModule debugModule;
    private final FederationNotificationProcessor notificationProcessor;
    public org.ethereum.core.Repository repository;
    public Ethereum eth;
    protected MinerServer minerServer;
    private long initialBlockNumber;

    protected Web3Impl(
            Ethereum eth,
            Blockchain blockchain,
            TransactionPool transactionPool,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            RskSystemProperties config,
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
            PeerServer peerServer,
            BlockProcessor nodeBlockProcessor,
            FederationNotificationProcessor notificationProcessor,
            HashRateCalculator hashRateCalculator,
            ConfigCapabilities configCapabilities) {
        this.eth = eth;
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.repository = repository;
        this.transactionPool = transactionPool;
        this.minerClient = minerClient;
        this.minerServer = minerServer;
        this.personalModule = personalModule;
        this.ethModule = ethModule;
        this.txPoolModule = txPoolModule;
        this.mnrModule = mnrModule;
        this.debugModule = debugModule;
        this.channelManager = channelManager;
        this.peerScoringManager = peerScoringManager;
        this.peerServer = peerServer;
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.notificationProcessor = notificationProcessor;
        this.hashRateCalculator = hashRateCalculator;
        this.configCapabilities = configCapabilities;
        this.config = config;
        filterManager = new FilterManager(eth);
        snapshotManager = new SnapshotManager(blockchain, transactionPool);
        initialBlockNumber = this.blockchain.getBestBlock().getNumber();

        personalModule.init(this.config);
    }

    protected Web3Impl(
            Ethereum eth,
            Blockchain blockchain,
            TransactionPool transactionPool,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            RskSystemProperties config,
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
            PeerServer peerServer,
            BlockProcessor nodeBlockProcessor,
            HashRateCalculator hashRateCalculator,
            ConfigCapabilities configCapabilities) {
        this(eth, blockchain, transactionPool, blockStore, receiptStore, config, minerClient, minerServer, personalModule, ethModule, txPoolModule,
                mnrModule, debugModule, channelManager, repository, peerScoringManager, peerServer, nodeBlockProcessor,
                new NodeFederationNotificationProcessor(config, nodeBlockProcessor), hashRateCalculator, configCapabilities);
    }

    public static Block getBlockByNumberOrStr(String bnOrId, Blockchain blockchain) throws Exception {
        Block b;

        if ("latest".equals(bnOrId)) {
            b = blockchain.getBestBlock();
        } else if ("earliest".equals(bnOrId)) {
            b = blockchain.getBlockByNumber(0);
        } else if ("pending".equals(bnOrId)) {
            throw new JsonRpcUnimplementedMethodException("The method don't support 'pending' as a parameter yet");
        } else {
            long bn = JSonHexToLong(bnOrId);
            b = blockchain.getBlockByNumber(bn);
        }

        return b;
    }

    @Override
    public void start() {
        hashRateCalculator.start();
    }

    @Override
    public void stop() {
        hashRateCalculator.stop();
    }

    public int JSonHexToInt(String x) throws Exception {
        if (!x.startsWith("0x")) {
            throw new Exception("Incorrect hex syntax");
        }
        x = x.substring(2);
        return Integer.parseInt(x, 16);
    }

    @Override
    public String web3_clientVersion() {
        String clientVersion = baseClientVersion + "/" + config.projectVersion() + "/" +
                System.getProperty("os.name") + "/Java1.8/" +
                config.projectVersionModifier() + "-" + BuildInfo.getBuildHash();

        if (logger.isDebugEnabled()) {
            logger.debug("web3_clientVersion(): {}", clientVersion);
        }

        return clientVersion;
    }

    @Override
    public String web3_sha3(String data) throws Exception {
        String s = null;
        try {
            byte[] result = HashUtil.keccak256(data.getBytes(StandardCharsets.UTF_8));
            return s = TypeConverter.toJsonHex(result);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("web3_sha3({}): {}", data, s);
            }
        }
    }

    @Override
    public String net_version() {
        String s = null;
        try {
            byte netVersion = config.getBlockchainConfig().getCommonConstants().getChainId();
            return s = Byte.toString(netVersion);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("net_version(): {}", s);
            }
        }
    }

    @Override
    public String net_peerCount() {
        String s = null;
        try {
            int n = channelManager.getActivePeers().size();
            return s = TypeConverter.toJsonHex(n);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("net_peerCount(): {}", s);
            }
        }
    }

    @Override
    public boolean net_listening() {
        Boolean s = null;
        try {
            return s = peerServer.isListening();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("net_listening(): {}", s);
            }
        }
    }

    @Override
    public String rsk_protocolVersion() {
        String s = null;
        try {
            int version = 0;

            for (Capability capability : configCapabilities.getConfigCapabilities()) {
                if (capability.isRSK()) {
                    version = max(version, capability.getVersion());
                }
            }

            return s = Integer.toString(version);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("rsk_protocolVersion(): {}", s);
            }
        }
    }

    @Override
    public String eth_protocolVersion() {
        return rsk_protocolVersion();
    }

    @Override
    public Object eth_syncing() {
        long currentBlock = this.blockchain.getBestBlock().getNumber();
        long highestBlock = this.nodeBlockProcessor.getLastKnownBlockNumber();

        if (highestBlock <= currentBlock) {
            return false;
        }

        SyncingResult s = new SyncingResult();
        try {
            s.startingBlock = TypeConverter.toJsonHex(initialBlockNumber);
            s.currentBlock = TypeConverter.toJsonHex(currentBlock);
            s.highestBlock = toJsonHex(highestBlock);

            return s;
        } finally {
            logger.debug("eth_syncing(): starting {}, current {}, highest {} ", s.startingBlock, s.currentBlock, s.highestBlock);
        }
    }

    @Override
    public String eth_coinbase() {
        String s = null;
        try {
            return s = toJsonHex(minerServer.getCoinbaseAddress().getBytes());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_coinbase(): {}", s);
            }
        }
    }

    @Override
    public List<FederationAlert> eth_getFederationAlerts() {
        return notificationProcessor.getFederationAlerts();
    }

    @Override
    public PanicFlag eth_getPanicStatus() {
        return notificationProcessor.getPanicStatus();
    }

    @Override
    public long eth_getPanickingBlockNumber() {
        return notificationProcessor.getPanicSinceBlockNumber();
    }

    @Override
    public boolean eth_mining() {
        Boolean s = null;
        try {
            return s = minerClient.isMining();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_mining(): {}", s);
            }
        }
    }

    @Override
    public BigInteger eth_hashrate() {
        BigInteger hashesPerHour = hashRateCalculator.calculateNodeHashRate(Duration.ofHours(1));
        BigInteger hashesPerSecond = hashesPerHour.divide(BigInteger.valueOf(Duration.ofHours(1).getSeconds()));

        logger.debug("eth_hashrate(): {}", hashesPerSecond);

        return hashesPerSecond;
    }

    @Override
    public BigInteger eth_netHashrate() {
        BigInteger hashesPerHour = hashRateCalculator.calculateNetHashRate(Duration.ofHours(1));
        BigInteger hashesPerSecond = hashesPerHour.divide(BigInteger.valueOf(Duration.ofHours(1).getSeconds()));

        logger.debug("eth_netHashrate(): {}", hashesPerSecond);

        return hashesPerSecond;
    }

    @Override
    public String[] net_peerList() {
        Collection<Channel> peers = channelManager.getActivePeers();
        List<String> response = new ArrayList<>();
        peers.forEach(channel -> response.add(channel.toString()));

        return response.stream().toArray(String[]::new);
    }

    @Override
    public String eth_gasPrice() {
        String gasPrice = null;
        try {
            return gasPrice = TypeConverter.toJsonHex(eth.getGasPrice().asBigInteger().longValue());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_gasPrice(): {}", gasPrice);
            }
        }
    }

    @Override
    public String eth_blockNumber() {
        Block bestBlock = blockchain.getBestBlock();

        long b = 0;
        if (bestBlock != null) {
            b = bestBlock.getNumber();
        }

        logger.debug("eth_blockNumber(): {}", b);

        return toJsonHex(b);
    }

    @Override
    public String eth_getBalance(String address, String block) throws Exception {
        /* HEX String  - an integer block number
        *  String "earliest"  for the earliest/genesis block
        *  String "latest"  - for the latest mined block
        *  String "pending"  - for the pending state/transactions
        */
        AccountInformationProvider accountInformationProvider = getAccountInformationProvider(block);

        if (accountInformationProvider == null) {
            throw new NullPointerException();
        }

        RskAddress addr = new RskAddress(address);
        BigInteger balance = accountInformationProvider.getBalance(addr).asBigInteger();

        return toJsonHex(balance);
    }

    @Override
    public String eth_getBalance(String address) throws Exception {
        RskAddress addr = new RskAddress(address);
        BigInteger balance = this.repository.getBalance(addr).asBigInteger();

        return toJsonHex(balance);
    }

    @Override
    public String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception {
        String s = null;

        try {
            RskAddress addr = new RskAddress(address);
            AccountInformationProvider accountInformationProvider = getAccountInformationProvider(blockId);

            if(accountInformationProvider == null) {
                return null;
            }

            DataWord storageValue = accountInformationProvider.
                    getStorageValue(addr, new DataWord(stringHexToByteArray(storageIdx)));
            if (storageValue != null) {
                return s = TypeConverter.toJsonHex(storageValue.getData());
            } else {
                return null;
            }
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getStorageAt({}, {}, {}): {}", address, storageIdx, blockId, s);
            }
        }
    }

    @Override
    public String eth_getTransactionCount(String address, String blockId) throws Exception {
        String s = null;
        try {
            RskAddress addr = new RskAddress(address);

            AccountInformationProvider accountInformationProvider = getAccountInformationProvider(blockId);

            if (accountInformationProvider != null) {
                BigInteger nonce = accountInformationProvider.getNonce(addr);
                return s = TypeConverter.toJsonHex(nonce);
            } else {
                return null;
            }
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getTransactionCount({}, {}): {}", address, blockId, s);
            }
        }
    }

    public Block getBlockByJSonHash(String blockHash) throws Exception {
        byte[] bhash = stringHexToByteArray(blockHash);
        return this.blockchain.getBlockByHash(bhash);
    }

    @Override
    public String eth_getBlockTransactionCountByHash(String blockHash) throws Exception {
        String s = null;
        try {
            Block b = getBlockByJSonHash(blockHash);

            if (b == null) {
                return null;
            }

            long n = b.getTransactionsList().size();

            return s = TypeConverter.toJsonHex(n);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockTransactionCountByHash({}): {}", blockHash, s);
            }
        }
    }

    @Override
    public String eth_getBlockTransactionCountByNumber(String bnOrId) throws Exception {
        String s = null;
        try {
            List<Transaction> list = getTransactionsByJsonBlockId(bnOrId);

            if (list == null) {
                return null;
            }

            long n = list.size();

            return s = TypeConverter.toJsonHex(n);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockTransactionCountByNumber({}): {}", bnOrId, s);
            }
        }
    }

    @Override
    public String eth_getUncleCountByBlockHash(String blockHash) throws Exception {
        Block b = getBlockByJSonHash(blockHash);
        long n = b.getUncleList().size();

        return toJsonHex(n);
    }

    @Override
    public String eth_getUncleCountByBlockNumber(String bnOrId) throws Exception {
        Block b = getBlockByNumberOrStr(bnOrId, blockchain);
        long n = b.getUncleList().size();

        return toJsonHex(n);
    }

    @Override
    public String eth_getCode(String address, String blockId) throws Exception {
        if (blockId == null) {
            throw new NullPointerException();
        }

        String s = null;
        try {
            Block block = getByJsonBlockId(blockId);

            if (block == null) {
                return null;
            }

            RskAddress addr = new RskAddress(address);

            AccountInformationProvider accountInformationProvider = getAccountInformationProvider(blockId);

            if(accountInformationProvider != null) {
                byte[] code = accountInformationProvider.getCode(addr);
                s = TypeConverter.toJsonHex(code);
            }

            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getCode({}, {}): {}", address, blockId, s);
            }
        }
    }

    @Override
    public String eth_sendRawTransaction(String rawData) throws Exception {
        String s = null;
        try {
            Transaction tx = new ImmutableTransaction(stringHexToByteArray(rawData));

            if (null == tx.getGasLimit()
                    || null == tx.getGasPrice()
                    || null == tx.getValue()) {
                throw new JsonRpcInvalidParamException("Missing parameter, gasPrice, gas or value");
            }

            eth.submitTransaction(tx);

            return s = tx.getHash().toJsonString();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_sendRawTransaction({}): {}", rawData, s);
            }
        }
    }

    public BlockInformationResult getBlockInformationResult(BlockInformation blockInformation) {
        BlockInformationResult bir = new BlockInformationResult();
        bir.hash = TypeConverter.toJsonHex(blockInformation.getHash());
        bir.totalDifficulty = TypeConverter.toJsonHex(blockInformation.getTotalDifficulty().asBigInteger());
        bir.inMainChain = blockInformation.isInMainChain();

        return bir;
    }

    public BlockResult getBlockResult(Block b, boolean fullTx) {
        if (b == null) {
            return null;
        }

        byte[] mergeHeader = b.getBitcoinMergedMiningHeader();

        boolean isPending = (mergeHeader == null || mergeHeader.length == 0) && !b.isGenesis();

        BlockResult br = new BlockResult();
        br.number = isPending ? null : TypeConverter.toJsonHex(b.getNumber());
        br.hash = isPending ? null : b.getHashJsonString();
        br.parentHash = b.getParentHashJsonString();
        br.sha3Uncles = TypeConverter.toJsonHex(b.getUnclesHash());
        br.logsBloom = isPending ? null : TypeConverter.toJsonHex(b.getLogBloom());
        br.transactionsRoot = TypeConverter.toJsonHex(b.getTxTrieRoot());
        br.stateRoot = TypeConverter.toJsonHex(b.getStateRoot());
        br.receiptsRoot = TypeConverter.toJsonHex(b.getReceiptsRoot());
        br.miner = isPending ? null : TypeConverter.toJsonHex(b.getCoinbase().getBytes());
        br.difficulty = TypeConverter.toJsonHex(b.getDifficulty().getBytes());
        br.totalDifficulty = TypeConverter.toJsonHex(this.blockchain.getBlockStore().getTotalDifficultyForHash(b.getHash().getBytes()).asBigInteger());
        br.extraData = TypeConverter.toJsonHex(b.getExtraData());
        br.size = TypeConverter.toJsonHex(b.getEncoded().length);
        br.gasLimit = TypeConverter.toJsonHex(b.getGasLimit());
        Coin mgp = b.getMinimumGasPrice();
        br.minimumGasPrice = mgp != null ? mgp.asBigInteger().toString() : "";
        br.gasUsed = TypeConverter.toJsonHex(b.getGasUsed());
        br.timestamp = TypeConverter.toJsonHex(b.getTimestamp());

        List<Object> txes = new ArrayList<>();

        if (fullTx) {
            for (int i = 0; i < b.getTransactionsList().size(); i++) {
                txes.add(new TransactionResultDTO(b, i, b.getTransactionsList().get(i)));
            }
        } else {
            for (Transaction tx : b.getTransactionsList()) {
                txes.add(tx.getHash().toJsonString());
            }
        }

        br.transactions = txes.toArray();

        List<String> ul = new ArrayList<>();

        for (BlockHeader header : b.getUncleList()) {
            ul.add(toJsonHex(header.getHash().getBytes()));
        }

        br.uncles = ul.toArray(new String[ul.size()]);

        return br;
    }

    public BlockInformationResult[] eth_getBlocksByNumber(String number) {
        long blockNumber;

        try {
            blockNumber = TypeConverter.stringNumberAsBigInt(number).longValue();
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw new JsonRpcInvalidParamException("invalid blocknumber " + number);
        }

        List<BlockInformationResult> result = new ArrayList<>();

        List<BlockInformation> binfos = blockchain.getBlocksInformationByNumber(blockNumber);

        for (BlockInformation binfo : binfos) {
            result.add(getBlockInformationResult(binfo));
        }

        return result.toArray(new BlockInformationResult[result.size()]);
    }

    @Override
    public BlockResult eth_getBlockByHash(String blockHash, Boolean fullTransactionObjects) throws Exception {
        BlockResult s = null;
        try {
            Block b = getBlockByJSonHash(blockHash);

            return getBlockResult(b, fullTransactionObjects);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockByHash({}, {}): {}", blockHash, fullTransactionObjects, s);
            }
        }
    }

    @Override
    public BlockResult eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) throws Exception {
        BlockResult s = null;
        try {
            Block b = getByJsonBlockId(bnOrId);

            return s = (b == null ? null : getBlockResult(b, fullTransactionObjects));
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockByNumber({}, {}): {}", bnOrId, fullTransactionObjects, s);
            }
        }
    }

    @Override
    public TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception {
        TransactionResultDTO s = null;
        try {
            Keccak256 txHash = new Keccak256(stringHexToByteArray(transactionHash));
            Block block = null;

            TransactionInfo txInfo = this.receiptStore.getInMainChain(txHash.getBytes(), blockStore);

            if (txInfo == null) {
                List<Transaction> txs = this.getTransactionsByJsonBlockId("pending");

                for (Transaction tx : txs) {
                    if (tx.getHash().equals(txHash)) {
                        return s = new TransactionResultDTO(null, null, tx);
                    }
                }
            } else {
                block = blockchain.getBlockByHash(txInfo.getBlockHash());
                // need to return txes only from main chain
                Block mainBlock = blockchain.getBlockByNumber(block.getNumber());
                if (!block.getHash().equals(mainBlock.getHash())) {
                    return null;
                }
                txInfo.setTransaction(block.getTransactionsList().get(txInfo.getIndex()));
            }

            if (txInfo == null) {
                return null;
            }

            return s = new TransactionResultDTO(block, txInfo.getIndex(), txInfo.getReceipt().getTransaction());
        } finally {
            logger.debug("eth_getTransactionByHash({}): {}", transactionHash, s);
        }
    }

    @Override
    public TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) throws Exception {
        TransactionResultDTO s = null;
        try {
            Block b = getBlockByJSonHash(blockHash);

            if (b == null) {
                return null;
            }

            int idx = JSonHexToInt(index);

            if (idx >= b.getTransactionsList().size()) {
                return null;
            }

            Transaction tx = b.getTransactionsList().get(idx);

            return s = new TransactionResultDTO(b, idx, tx);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getTransactionByBlockHashAndIndex({}, {}): {}", blockHash, index, s);
            }
        }
    }

    @Override
    public TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) throws Exception {
        TransactionResultDTO s = null;
        try {
            Block b = getByJsonBlockId(bnOrId);
            List<Transaction> txs = getTransactionsByJsonBlockId(bnOrId);

            if (txs == null) {
                return null;
            }

            int idx = JSonHexToInt(index);

            if (idx >= txs.size()) {
                return null;
            }

            Transaction tx = txs.get(idx);

            return s = new TransactionResultDTO(b, idx, tx);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getTransactionByBlockNumberAndIndex({}, {}): {}", bnOrId, index, s);
            }
        }
    }

    @Override
    public TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception {
        logger.trace("eth_getTransactionReceipt({})", transactionHash);

        byte[] hash = stringHexToByteArray(transactionHash);
        TransactionInfo txInfo = receiptStore.getInMainChain(hash, blockStore);

        if (txInfo == null) {
            logger.trace("No transaction info for {}", transactionHash);
            return null;
        }

        Block block = blockStore.getBlockByHash(txInfo.getBlockHash());
        Transaction tx = block.getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        return new TransactionReceiptDTO(block, txInfo);
    }

    @Override
    public BlockResult eth_getUncleByBlockHashAndIndex(String blockHash, String uncleIdx) throws Exception {
        BlockResult s = null;
        try {
            Block block = blockchain.getBlockByHash(stringHexToByteArray(blockHash));

            if (block == null) {
                return null;
            }

            int idx = JSonHexToInt(uncleIdx);

            if (idx >= block.getUncleList().size()) {
                return null;
            }

            BlockHeader uncleHeader = block.getUncleList().get(idx);
            Block uncle = blockchain.getBlockByHash(uncleHeader.getHash().getBytes());

            if (uncle == null) {
                uncle = new Block(uncleHeader, Collections.emptyList(), Collections.emptyList());
            }

            return s = getBlockResult(uncle, false);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getUncleByBlockHashAndIndex({}, {}): {}", blockHash, uncleIdx, s);
            }
        }
    }

    @Override
    public BlockResult eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) throws Exception {
        BlockResult s = null;
        try {
            Block block = getByJsonBlockId(blockId);

            return s = block == null ? null :
                    eth_getUncleByBlockHashAndIndex(Hex.toHexString(block.getHash().getBytes()), uncleIdx);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getUncleByBlockNumberAndIndex({}, {}): {}", blockId, uncleIdx, s);
            }
        }
    }

    @Override
    public String[] eth_getCompilers() {
        String[] s = null;
        try {
            return s = new String[]{"solidity"};
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getCompilers(): {}", Arrays.toString(s));
            }
        }
    }

    @Override
    public Map<String, CompilationResultDTO> eth_compileLLL(String contract) {
        throw new UnsupportedOperationException("LLL compiler not supported");
    }

    @Override
    public Map<String, CompilationResultDTO> eth_compileSerpent(String contract) {
        throw new UnsupportedOperationException("Serpent compiler not supported");
    }

    @Override
    public String eth_newFilter(FilterRequest fr) throws Exception {
        String str = null;

        try {
            Filter filter = LogFilter.fromFilterRequest(fr, blockchain);
            int id = filterManager.registerFilter(filter);

            return str = toJsonHex(id);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_newFilter({}): {}", fr, str);
            }
        }
    }

    @Override
    public String eth_newBlockFilter() {
        String s = null;
        try {
            int id = filterManager.registerFilter(new NewBlockFilter());

            return s = toJsonHex(id);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_newBlockFilter(): {}", s);
            }
        }
    }

    @Override
    public String eth_newPendingTransactionFilter() {
        String s = null;
        try {
            int id = filterManager.registerFilter(new PendingTransactionFilter());

            return s = toJsonHex(id);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_newPendingTransactionFilter(): {}", s);
            }
        }
    }

    @Override
    public boolean eth_uninstallFilter(String id) {
        Boolean s = null;

        try {
            if (id == null) {
                return false;
            }

            return filterManager.removeFilter(stringHexToBigInteger(id).intValue());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_uninstallFilter({}): {}", id, s);
            }
        }
    }

    @Override
    public Object[] eth_getFilterChanges(String id) {
        logger.debug("eth_getFilterChanges ...");

        Object[] s = null;

        try {
            s = getFilterEvents(id, true);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getFilterChanges({}): {}", id, Arrays.toString(s));
            }
        }

        return s;
    }

    @Override
    public Object[] eth_getFilterLogs(String id) {
        logger.debug("eth_getFilterLogs ...");

        Object[] s = null;

        try {
            s = getFilterEvents(id, false);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getFilterLogs({}): {}", id, Arrays.toString(s));
            }
        }

        return s;
    }

    private Object[] getFilterEvents(String id, boolean newevents) {
        return this.filterManager.getFilterEvents(stringHexToBigInteger(id).intValue(), newevents);
    }

    @Override
    public Object[] eth_getLogs(FilterRequest fr) throws Exception {
        logger.debug("eth_getLogs ...");
        String id = eth_newFilter(fr);
        Object[] ret = eth_getFilterLogs(id);
        eth_uninstallFilter(id);
        return ret;
    }

    @Override
    public Map<String, String> rpc_modules() {
        logger.debug("rpc_modules...");

        Map<String, String> map = new HashMap<>();

        for (ModuleDescription module : config.getRpcModules()) {
            if (module.isEnabled()) {
                map.put(module.getName(), module.getVersion());
            }
        }

        return map;
    }

    @Override
    public void db_putString() {
    }

    @Override
    public void db_getString() {
    }

    @Override
    public boolean eth_submitWork(String nonce, String header, String mince) {
        throw new UnsupportedOperationException("Not implemeted yet");
    }

    @Override
    public boolean eth_submitHashrate(String hashrate, String id) {
        throw new UnsupportedOperationException("Not implemeted yet");
    }

    @Override
    public void db_putHex() {
    }

    @Override
    public void db_getHex() {
    }

    private List<Transaction> getTransactionsByJsonBlockId(String id) {
        if ("pending".equalsIgnoreCase(id)) {
            return transactionPool.getPendingTransactions();
        } else {
            Block block = getByJsonBlockId(id);
            return block != null ? block.getTransactionsList() : null;
        }
    }

    private Block getByJsonBlockId(String id) {
        if ("earliest".equalsIgnoreCase(id)) {
            return this.blockchain.getBlockByNumber(0);
        } else if ("latest".equalsIgnoreCase(id)) {
            return this.blockchain.getBestBlock();
        } else if ("pending".equalsIgnoreCase(id)) {
            throw new JsonRpcUnimplementedMethodException("The method don't support 'pending' as a parameter yet");
        } else {
            try {
                long blockNumber = stringHexToBigInteger(id).longValue();
                return this.blockchain.getBlockByNumber(blockNumber);
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                throw new JsonRpcInvalidParamException("invalid blocknumber " + id);
            }
        }
    }

    private AccountInformationProvider getAccountInformationProvider(String id) {
        if ("pending".equalsIgnoreCase(id)) {
            return transactionPool.getPendingState();
        } else {
            Block block = getByJsonBlockId(id);
            if (block != null) {
                return this.repository.getSnapshotTo(block.getStateRoot());
            } else {
                return null;
            }
        }
    }

    @Override
    public String personal_newAccountWithSeed(String seed) {
        return personalModule.newAccountWithSeed(seed);
    }

    @Override
    public String personal_newAccount(String passphrase) {
        return personalModule.newAccount(passphrase);
    }

    @Override
    public String personal_importRawKey(String key, String passphrase) {
        return personalModule.importRawKey(key, passphrase);
    }

    @Override
    public String personal_dumpRawKey(String address) throws Exception {
        return personalModule.dumpRawKey(address);
    }

    @Override
    public String[] personal_listAccounts() {
        return personalModule.listAccounts();
    }

    @Override
    public String personal_sendTransaction(CallArguments args, String passphrase) throws Exception {
        return personalModule.sendTransaction(args, passphrase);
    }

    @Override
    public boolean personal_unlockAccount(String address, String passphrase, String duration) {
        return personalModule.unlockAccount(address, passphrase, duration);
    }

    @Override
    public boolean personal_lockAccount(String address) {
        return personalModule.lockAccount(address);
    }

    @Override
    public EthModule getEthModule() {
        return ethModule;
    }

    @Override
    public TxPoolModule getTxPoolModule() {
        return txPoolModule;
    }

    @Override
    public MnrModule getMnrModule() {
        return mnrModule;
    }

    @Override
    public DebugModule getDebugModule() {
        return debugModule;
    }

    @Override
    public String evm_snapshot() {
        int snapshotId = snapshotManager.takeSnapshot();

        logger.debug("evm_snapshot(): {}", snapshotId);

        return toJsonHex(snapshotId);
    }

    @Override
    public boolean evm_revert(String snapshotId) {
        try {
            int sid = stringHexToBigInteger(snapshotId).intValue();
            return snapshotManager.revertToSnapshot(sid);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw new JsonRpcInvalidParamException("invalid snapshot id " + snapshotId, e);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("evm_revert({})", snapshotId);
            }
        }
    }

    @Override
    public void evm_reset() {
        snapshotManager.resetSnapshots();
        if (logger.isDebugEnabled()) {
            logger.debug("evm_reset()");
        }
    }

    @Override
    public void evm_mine() {
        minerManager.mineBlock(this.blockchain, minerClient, minerServer);
        if (logger.isDebugEnabled()) {
            logger.debug("evm_mine()");
        }
    }

    @Override
    public void evm_fallbackMine() {
        minerManager.fallbackMineBlock(this.blockchain, minerClient, minerServer);
        if (logger.isDebugEnabled()) {
            logger.debug("evm_fallbackMine()");
        }
    }

    @Override
    public void evm_startMining() {
        minerServer.start();
        if (logger.isDebugEnabled()) {
            logger.debug("evm_startMining()");
        }
    }

    @Override
    public void evm_stopMining() {
        minerServer.stop();
        if (logger.isDebugEnabled()) {
            logger.debug("evm_stopMining()");
        }
    }

    @Override
    public String evm_increaseTime(String seconds) {
        try {
            long nseconds = stringNumberAsBigInt(seconds).longValue();
            String result = toJsonHex(minerServer.increaseTime(nseconds));
            if (logger.isDebugEnabled()) {
                logger.debug("evm_increaseTime({}): {}", nseconds, result);
            }
            return result;
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw new JsonRpcInvalidParamException("invalid number of seconds " + seconds, e);
        }
    }

    /**
     * Adds an address or block to the list of banned addresses
     * It supports IPV4 and IPV6 addresses with an optional number of bits to ignore
     * <p>
     * "192.168.51.1" is a valid address
     * "192.168.51.1/16" is a valid block
     *
     * @param address the address or block to be banned
     */
    @Override
    public void sco_banAddress(String address) {
        if (this.peerScoringManager == null) {
            return;
        }

        try {
            this.peerScoringManager.banAddress(address);
        } catch (InvalidInetAddressException e) {
            throw new JsonRpcInvalidParamException("invalid banned address " + address, e);
        }
    }

    /**
     * Removes an address or block to the list of banned addresses
     * It supports IPV4 and IPV6 addresses with an optional number of bits to ignore
     * <p>
     * "192.168.51.1" is a valid address
     * "192.168.51.1/16" is a valid block
     *
     * @param address the address or block to be removed
     */
    @Override
    public void sco_unbanAddress(String address) {
        if (this.peerScoringManager == null) {
            return;
        }

        try {
            this.peerScoringManager.unbanAddress(address);
        } catch (InvalidInetAddressException e) {
            throw new JsonRpcInvalidParamException("invalid banned address " + address, e);
        }
    }

    /**
     * Returns the collected peer scoring information
     * since the start of the node start
     *
     * @return the list of scoring information, per node id and address
     */
    @Override
    public PeerScoringInformation[] sco_peerList() {
        if (this.peerScoringManager != null) {
            return this.peerScoringManager.getPeersInformation().toArray(new PeerScoringInformation[0]);
        }

        return null;
    }

    /**
     * Returns the list of banned addresses and blocks
     *
     * @return the list of banned addresses and blocks
     */
    @Override
    public String[] sco_bannedAddresses() {
        return this.peerScoringManager.getBannedAddresses().toArray(new String[0]);
    }
}
