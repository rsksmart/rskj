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
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.crypto.Keccak256;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.net.Peer;
import co.rsk.rpc.ModuleDescription;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.getProof.ProofDTO;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.trace.TraceModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.scoring.*;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.client.Capability;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.rpc.dto.BlockResultDTO;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static java.lang.Math.max;
import static org.ethereum.rpc.TypeConverter.*;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.*;

public class Web3Impl implements Web3 {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    public Ethereum eth;

    private final String baseClientVersion = "RskJ";

    private long initialBlockNumber;

    private final MinerClient minerClient;
    protected MinerServer minerServer;
    private final ChannelManager channelManager;
    private final PeerScoringManager peerScoringManager;
    private final PeerServer peerServer;

    private final Blockchain blockchain;
    private final ReceiptStore receiptStore;
    private final BlockProcessor nodeBlockProcessor;
    private final HashRateCalculator hashRateCalculator;
    private final ConfigCapabilities configCapabilities;
    private final BlockStore blockStore;
    private final RskSystemProperties config;

    private final FilterManager filterManager;
    private final BuildInfo buildInfo;

    private final BlocksBloomStore blocksBloomStore;
    private final Web3InformationRetriever web3InformationRetriever;

    private final PersonalModule personalModule;
    private final EthModule ethModule;
    private final EvmModule evmModule;
    private final TxPoolModule txPoolModule;
    private final MnrModule mnrModule;
    private final DebugModule debugModule;
    private final TraceModule traceModule;
    private final RskModule rskModule;

    protected Web3Impl(
            Ethereum eth,
            Blockchain blockchain,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            RskSystemProperties config,
            MinerClient minerClient,
            MinerServer minerServer,
            PersonalModule personalModule,
            EthModule ethModule,
            EvmModule evmModule,
            TxPoolModule txPoolModule,
            MnrModule mnrModule,
            DebugModule debugModule,
            TraceModule traceModule,
            RskModule rskModule,
            ChannelManager channelManager,
            PeerScoringManager peerScoringManager,
            PeerServer peerServer,
            BlockProcessor nodeBlockProcessor,
            HashRateCalculator hashRateCalculator,
            ConfigCapabilities configCapabilities,
            BuildInfo buildInfo,
            BlocksBloomStore blocksBloomStore,
            Web3InformationRetriever web3InformationRetriever) {
        this.eth = eth;
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.evmModule = evmModule;
        this.minerClient = minerClient;
        this.minerServer = minerServer;
        this.personalModule = personalModule;
        this.ethModule = ethModule;
        this.txPoolModule = txPoolModule;
        this.mnrModule = mnrModule;
        this.debugModule = debugModule;
        this.traceModule = traceModule;
        this.rskModule = rskModule;
        this.channelManager = channelManager;
        this.peerScoringManager = peerScoringManager;
        this.peerServer = peerServer;
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.hashRateCalculator = hashRateCalculator;
        this.configCapabilities = configCapabilities;
        this.config = config;
        this.filterManager = new FilterManager(eth);
        this.buildInfo = buildInfo;
        this.blocksBloomStore = blocksBloomStore;
        this.web3InformationRetriever = web3InformationRetriever;
        initialBlockNumber = this.blockchain.getBestBlock().getNumber();

        personalModule.init();
    }

    @Override
    public void start() {
        hashRateCalculator.start();
    }

    @Override
    public void stop() {
        hashRateCalculator.stop();
    }

    private int JSonHexToInt(String x) {
        if (!x.startsWith("0x")) {
            throw invalidParamError("Incorrect hex syntax");
        }
        x = x.substring(2);
        return Integer.parseInt(x, 16);
    }

    @Override
    public String web3_clientVersion() {
        String clientVersion = baseClientVersion + "/" + config.projectVersion() + "/" +
                System.getProperty("os.name") + "/Java1.8/" +
                config.projectVersionModifier() + "-" + buildInfo.getBuildHash();

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
            byte netVersion = config.getNetworkConstants().getChainId();
            return s = Byte.toString(netVersion);
        }
        finally {
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
            return s = TypeConverter.toQuantityJsonHex(n);
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

        if (highestBlock <= currentBlock){
            return false;
        }

        SyncingResult s = new SyncingResult();
        try {
            s.startingBlock = TypeConverter.toQuantityJsonHex(initialBlockNumber);
            s.currentBlock = TypeConverter.toQuantityJsonHex(currentBlock);
            s.highestBlock = toQuantityJsonHex(highestBlock);

            return s;
        } finally {
            logger.debug("eth_syncing(): starting {}, current {}, highest {} ", s.startingBlock, s.currentBlock, s.highestBlock);
        }
    }

    @Override
    public String eth_coinbase() {
        String s = null;
        try {
            s = minerServer.getCoinbaseAddress().toJsonString();
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_coinbase(): {}", s);
            }
        }
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
        Collection<Peer> peers = channelManager.getActivePeers();
        List<String> response = new ArrayList<>();
        peers.forEach(channel -> response.add(channel.toString()));

        return response.stream().toArray(String[]::new);
    }

    @Override
    public String eth_gasPrice() {
        String gasPrice = null;
        try {
            gasPrice = TypeConverter.toQuantityJsonHex(eth.getGasPrice().asBigInteger().longValue());
            return gasPrice;
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

        return toQuantityJsonHex(b);
    }

    @Override
    public String eth_getBalance(String address, String block) {
        /* HEX String  - an integer block number
        *  String "earliest"  for the earliest/genesis block
        *  String "latest"  - for the latest mined block
        *  String "pending"  - for the pending state/transactions
        */

        AccountInformationProvider accountInformationProvider = web3InformationRetriever.getInformationProvider(block);

        RskAddress addr = new RskAddress(address);
        Coin balance = accountInformationProvider.getBalance(addr);

        return toQuantityJsonHex(balance.asBigInteger());
    }

    @Override
    public String eth_getBalance(String address) {
        return eth_getBalance(address, "latest");
    }

    @Override
    public String eth_getStorageAt(String address, String storageIdx, String blockId) {
        String s = null;

        try {
            RskAddress addr = new RskAddress(address);
            
            AccountInformationProvider accountInformationProvider =
                    web3InformationRetriever.getInformationProvider(blockId);

            DataWord sv = accountInformationProvider
                    .getStorageValue(addr, DataWord.valueOf(stringHexToByteArray(storageIdx)));

            if (sv == null) {
                s = "0x0";
            } else {
                s = toUnformattedJsonHex(sv.getData());
            }

            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getStorageAt({}, {}, {}): {}", address, storageIdx, blockId, s);
            }
        }
    }

    @Override
    public String eth_getTransactionCount(String address, String blockId) {
        String s = null;
        try {
            RskAddress addr = new RskAddress(address);
            AccountInformationProvider accountInformationProvider = web3InformationRetriever
                    .getInformationProvider(blockId);
            BigInteger nonce = accountInformationProvider.getNonce(addr);
            s = toQuantityJsonHex(nonce);
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getTransactionCount({}, {}): {}", address, blockId, s);
            }
        }
    }

    public Block getBlockByJSonHash(String blockHash) {
        byte[] bhash = stringHexToByteArray(blockHash);
        return this.blockchain.getBlockByHash(bhash);
    }

    @Override
    public String eth_getBlockTransactionCountByHash(String blockHash) {
        String s = null;
        try {
            Block b = getBlockByJSonHash(blockHash);

            if (b == null) {
                return null;
            }

            long n = b.getTransactionsList().size();

            s = TypeConverter.toQuantityJsonHex(n);
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockTransactionCountByHash({}): {}", blockHash, s);
            }
        }
    }

    public static Block getBlockByNumberOrStr(String bnOrId, Blockchain blockchain) {
        Block b;

        if ("latest".equals(bnOrId)) {
            b = blockchain.getBestBlock();
        } else if ("earliest".equals(bnOrId)) {
            b = blockchain.getBlockByNumber(0);
        } else if ("pending".equals(bnOrId)) {
            throw unimplemented("The method don't support 'pending' as a parameter yet");
        } else {
            long bn = JSonHexToLong(bnOrId);
            b = blockchain.getBlockByNumber(bn);
        }

        return b;
    }

    @Override
    public String eth_getBlockTransactionCountByNumber(String bnOrId) {
        String s = null;
        try {

            List<Transaction> txs = web3InformationRetriever.getTransactions(bnOrId);

            s = toQuantityJsonHex(txs.size());
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockTransactionCountByNumber({}): {}", bnOrId, s);
            }
        }
    }

    @Override
    public String eth_getUncleCountByBlockHash(String blockHash) {
        Block b = getBlockByJSonHash(blockHash);
        if (b == null) {
            throw blockNotFound(String.format("Block with hash %s not found", blockHash));
        }

        long n = b.getUncleList().size();
        return toQuantityJsonHex(n);
    }

    @Override
    public String eth_getUncleCountByBlockNumber(String bnOrId) {
        return web3InformationRetriever.getBlock(bnOrId)
                .map(Block::getUncleList)
                .map(List::size)
                .map(TypeConverter::toQuantityJsonHex)
                .orElseThrow(() -> blockNotFound(String.format("Block %s not found", bnOrId)));
    }

    public BlockInformationResult getBlockInformationResult(BlockInformation blockInformation) {
        BlockInformationResult bir = new BlockInformationResult();
        bir.hash = toUnformattedJsonHex(blockInformation.getHash());
        bir.totalDifficulty = toQuantityJsonHex(blockInformation.getTotalDifficulty().asBigInteger());
        bir.inMainChain = blockInformation.isInMainChain();

        return bir;
    }

    public BlockResultDTO getBlockResult(Block b, boolean fullTx) {
        return BlockResultDTO.fromBlock(b, fullTx, this.blockStore);
    }

    public BlockInformationResult[] eth_getBlocksByNumber(String number) {
        long blockNumber;

        try {
            blockNumber = TypeConverter.stringNumberAsBigInt(number).longValue();
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw invalidParamError(String.format("invalid blocknumber %s", number));
        }

        List<BlockInformationResult> result = new ArrayList<>();

        List<BlockInformation> binfos = blockchain.getBlocksInformationByNumber(blockNumber);

        for (BlockInformation binfo : binfos) {
            result.add(getBlockInformationResult(binfo));
        }

        return result.toArray(new BlockInformationResult[result.size()]);
    }

    @Override
    public BlockResultDTO eth_getBlockByHash(String blockHash, Boolean fullTransactionObjects) {
        BlockResultDTO s = null;
        try {
            Block b = getBlockByJSonHash(blockHash);

            return s = (b == null ? null : getBlockResult(b, fullTransactionObjects));
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockByHash({}, {}): {}", blockHash, fullTransactionObjects, s);
            }
        }
    }

    @Override
    public BlockResultDTO eth_getBlockByNumber(String bnOrId, Boolean fullTransactionObjects) {
        BlockResultDTO s = null;
        try {

            s = web3InformationRetriever.getBlock(bnOrId)
                    .map(b -> getBlockResult(b, fullTransactionObjects))
                    .orElse(null);

            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockByNumber({}, {}): {}", bnOrId, fullTransactionObjects, s);
            }
        }
    }

    @Override
    public TransactionResultDTO eth_getTransactionByHash(String transactionHash) {
        TransactionResultDTO s = null;
        try {
            Keccak256 txHash = new Keccak256(stringHexToByteArray(transactionHash));
            Block block = null;

            TransactionInfo txInfo = this.receiptStore.getInMainChain(txHash.getBytes(), blockStore);

            if (txInfo == null) {
                List<Transaction> txs =     web3InformationRetriever.getTransactions("pending");

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
    public TransactionResultDTO eth_getTransactionByBlockHashAndIndex(String blockHash, String index) {
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
    public TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(String bnOrId, String index) {
        TransactionResultDTO s = null;
        try {
            Optional<Block> block = web3InformationRetriever.getBlock(bnOrId);
            if (!block.isPresent()) {
                return null;
            }

            int idx = JSonHexToInt(index);
            List<Transaction> txs = web3InformationRetriever.getTransactions(bnOrId);
            if (idx >= txs.size()) {
                return null;
            }

            s = new TransactionResultDTO(block.get(), idx, txs.get(idx));
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getTransactionByBlockNumberAndIndex({}, {}): {}", bnOrId, index, s);
            }
        }
    }

    @Override
    public TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) {
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
    public BlockResultDTO eth_getUncleByBlockHashAndIndex(String blockHash, String uncleIdx) {
        BlockResultDTO s = null;

        try {
            Block block = blockchain.getBlockByHash(stringHexToByteArray(blockHash));

            if (block == null) {
                return null;
            }

            s = getUncleResultDTO(uncleIdx, block);

            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getUncleByBlockHashAndIndex({}, {}): {}", blockHash, uncleIdx, s);
            }
        }
    }

    private BlockResultDTO getUncleResultDTO(String uncleIdx, Block block) {
        int idx = JSonHexToInt(uncleIdx);

        if (idx >= block.getUncleList().size()) {
            return null;
        }

        BlockHeader uncleHeader = block.getUncleList().get(idx);
        Block uncle = blockchain.getBlockByHash(uncleHeader.getHash().getBytes());

        if (uncle == null) {
            boolean isRskip126Enabled = config.getActivationConfig().isActive(ConsensusRule.RSKIP126, uncleHeader.getNumber());
            uncle = Block.createBlockFromHeader(uncleHeader, isRskip126Enabled);
        }

        return getBlockResult(uncle, false);
    }

    @Override
    public BlockResultDTO eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) {
        BlockResultDTO s = null;
        try {
            Optional<Block> block = web3InformationRetriever.getBlock(blockId);

            if (!block.isPresent()) {
                return null;
            }

            s = getUncleResultDTO(uncleIdx, block.get());

            return s;
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
    public Map<String, CompilationResultDTO> eth_compileSolidity(String contract) {
        throw new UnsupportedOperationException("Solidity compiler not supported");
    }

    @Override
    public String eth_newFilter(FilterRequest fr) throws Exception {
        String str = null;

        try {
            Filter filter = LogFilter.fromFilterRequest(fr, blockchain, blocksBloomStore);
            int id = filterManager.registerFilter(filter);

            str = toQuantityJsonHex(id);
            return str;
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

            s = toQuantityJsonHex(id);
            return s;
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

            s = toQuantityJsonHex(id);
            return s;
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

        // TODO(mc): this is a quick solution that seems to work with OpenZeppelin tests, but needs to be reviewed
        // We do the same as in Ganache: mine a block in each request to getFilterChanges so block filters work
        if (config.isMinerClientEnabled() && config.minerClientAutoMine()) {
            minerServer.buildBlockToMine(false);
            minerClient.mineBlock();
        }

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
    public EvmModule getEvmModule() {
        return evmModule;
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
    public TraceModule getTraceModule() {
        return traceModule;
    }

    @Override
    public RskModule getRskModule() {
        return rskModule;
    }

    /**
     * Adds an address or block to the list of banned addresses
     * It supports IPV4 and IPV6 addresses with an optional number of bits to ignore
     *
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
            throw invalidParamError("invalid banned address " + address, e);
        }
    }

    /**
     * Removes an address or block to the list of banned addresses
     * It supports IPV4 and IPV6 addresses with an optional number of bits to ignore
     *
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
            throw invalidParamError("invalid banned address " + address, e);
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

    /**
     * Returns a reputation summary of all the peers connected to this node
     *
     * @return the actual summary
     */
    public PeerScoringReputationSummary sco_reputationSummary() {
        return PeerScoringReporterUtil.buildReputationSummary(peerScoringManager.getPeersInformation());
    }

    @Override
    public ProofDTO eth_getProof(String address, List<String> storageKeys, String blockOrId) {
        return ethModule.getProof(address, storageKeys, blockOrId);
    }
}
