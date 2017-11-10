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
import co.rsk.core.Rsk;
import co.rsk.core.SnapshotManager;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerManager;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.rpc.ModuleDescription;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.scoring.InvalidInetAddressException;
import co.rsk.scoring.PeerScoringInformation;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.TransactionInfo;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.client.Capability;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.rpc.dto.TransactionResultDTO;
import org.ethereum.rpc.exception.JsonRpcInvalidParamException;
import org.ethereum.rpc.exception.JsonRpcUnimplementedMethodException;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;
import static org.ethereum.rpc.TypeConverter.*;

public class Web3Impl implements Web3 {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private SnapshotManager snapshotManager = new SnapshotManager();
    private MinerManager minerManager = new MinerManager();

    public WorldManager worldManager;

    public org.ethereum.facade.Repository repository;

    public Ethereum eth;

    private String baseClientVersion = "RskJ";

    CompositeEthereumListener compositeEthereumListener;

    long initialBlockNumber;

    private final Object filterLock = new Object();

    private MinerClient minerClient;
    protected MinerServer minerServer;
    private ChannelManager channelManager;

    private PeerScoringManager peerScoringManager;

    private PersonalModule personalModule;
    private EthModule ethModule;

    protected Web3Impl(Ethereum eth,
                       RskSystemProperties properties,
                       MinerClient minerClient,
                       MinerServer minerServer,
                       PersonalModule personalModule,
                       EthModule ethModule,
                       ChannelManager channelManager) {
        this.eth = eth;
        this.worldManager = eth.getWorldManager();
        this.repository = eth.getRepository();
        this.minerClient = minerClient;
        this.minerServer = minerServer;
        this.personalModule = personalModule;
        this.ethModule = ethModule;
        this.channelManager = channelManager;

        if (eth instanceof Rsk)
            this.peerScoringManager = ((Rsk) eth).getPeerScoringManager();

        initialBlockNumber = this.worldManager.getBlockchain().getBestBlock().getNumber();

        compositeEthereumListener = new CompositeEthereumListener();

        compositeEthereumListener.addListener(this.setupListener());

        this.eth.addListener(compositeEthereumListener);
        personalModule.init(properties);
    }

    public EthereumListener setupListener() {
        return new EthereumListenerAdapter() {
            @Override
            public void onBlock(Block block, List<TransactionReceipt> receipts) {
                logger.trace("Start onBlock");

                synchronized (filterLock) {
                    for (Filter filter : installedFilters.values()) {
                        filter.newBlockReceived(block);
                    }
                }

                logger.trace("End onBlock");
            }

            @Override
            public void onPendingTransactionsReceived(List<Transaction> transactions) {
                synchronized (filterLock) {
                    for (Filter filter : installedFilters.values()) {
                        for (Transaction tx : transactions) {
                            filter.newPendingTx(tx);
                        }
                    }
                }
            }
        };
    }

    public long JSonHexToLong(String x) throws Exception {
        if (!x.startsWith("0x"))
            throw new Exception("Incorrect hex syntax");
        x = x.substring(2);
        return Long.parseLong(x, 16);
    }

    public int JSonHexToInt(String x) throws Exception {
        if (!x.startsWith("0x"))
            throw new Exception("Incorrect hex syntax");
        x = x.substring(2);
        return Integer.parseInt(x, 16);
    }

    public String JSonHexToHex(String x) throws Exception {
        if (!x.startsWith("0x"))
            throw new Exception("Incorrect hex syntax");
        x = x.substring(2);
        return x;
    }

    public String[] toJsonHexArray(Collection<String> c) {
        String[] arr = new String[c.size()];
        int i = 0;
        for (String item : c) {
            arr[i++] = toJsonHex(item);
        }
        return arr;
    }

    @Override
    public String web3_clientVersion() {

        String clientVersion = baseClientVersion + "/" + RskSystemProperties.CONFIG.projectVersion() + "/" +
                System.getProperty("os.name") + "/Java1.8/" +
                RskSystemProperties.CONFIG.projectVersionModifier() + "-" + BuildInfo.getBuildHash();

        if (logger.isDebugEnabled()) {
            logger.debug("web3_clientVersion(): " + clientVersion);
        }

        return clientVersion;

    }

    @Override
    public String web3_sha3(String data) throws Exception {
        String s = null;
        try {
            byte[] result = HashUtil.sha3(data.getBytes(StandardCharsets.UTF_8));
            return s = TypeConverter.toJsonHex(result);
        } finally {
            if (logger.isDebugEnabled())
                logger.debug("web3_sha3({}): {}", data, s);
        }
    }


    @Override
    public String net_version() {
        String s = null;
        try {
            byte netVersion = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getChainId();
            return s = Byte.toString(netVersion);
        }
        finally {
            if (logger.isDebugEnabled()) {
                logger.debug("net_version(): " + s);
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
                logger.debug("net_peerCount(): " + s);
            }
        }
    }


    @Override
    public boolean net_listening() {
        Boolean s = null;
        try {
            return s = eth.getPeerServer().isListening();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("net_listening(): " + s);
            }
        }
    }

    @Override
    public String rsk_protocolVersion() {
        String s = null;
        try {
            int version = 0;
            for (Capability capability : worldManager.getConfigCapabilities().getConfigCapabilities()) {
                if (capability.isRSK()) {
                    version = max(version, capability.getVersion());
                }
            }
            return s = Integer.toString(version);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("rsk_protocolVersion(): " + s);
            }
        }
    }

    @Override
    public String eth_protocolVersion() {
        return rsk_protocolVersion();
    }

    @Override
    public Object eth_syncing() {
        BlockProcessor processor = worldManager.getNodeBlockProcessor();
        // TODO(raltman): processor should never be null. Change Web3Impl to request BlockProcessor from Roostock.
        //if (processor == null || !processor.hasBetterBlockToSync()) {
        if (processor == null){
            return false;
        }

        long currentBlock = worldManager.getBlockchain().getBestBlock().getNumber();
        long highestBlock = processor.getLastKnownBlockNumber();

        if (highestBlock <= currentBlock){
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
            return s = toJsonHex(minerServer.getCoinbaseAddress());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_coinbase(): " + s);
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
                logger.debug("eth_mining(): " + s);
            }
        }
    }

    @Override
    public String eth_hashrate() {
        BigInteger hashesPerHour = this.worldManager.getHashRateCalculator().calculateNodeHashRate(Duration.ofHours(1));
        BigDecimal hashesPerSecond = new BigDecimal(hashesPerHour)
                .divide(new BigDecimal(TimeUnit.HOURS.toSeconds(1)), 3, RoundingMode.HALF_UP);

        String result = hashesPerSecond.toString();

        if (logger.isDebugEnabled()) {
            logger.debug("eth_hashrate(): " + result);
        }

        return result;
    }

    @Override
    public String eth_netHashrate() {
        BigInteger hashesPerHour = this.worldManager.getHashRateCalculator().calculateNetHashRate(Duration.ofHours(1));
        BigDecimal hashesPerSecond = new BigDecimal(hashesPerHour)
                .divide(new BigDecimal(TimeUnit.HOURS.toSeconds(1)), 3, RoundingMode.HALF_UP);

        String result = hashesPerSecond.toString();

        if (logger.isDebugEnabled()) {
            logger.debug("eth_netHashrate(): " + result);
        }

        return result;
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
            return gasPrice = TypeConverter.toJsonHex(eth.getGasPrice());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_gasPrice(): " + gasPrice);
            }
        }
    }

    @Override
    public String[] eth_accounts() {
        return ethModule.accounts();
    }

    @Override
    public String eth_blockNumber() {
        Blockchain blockchain = worldManager.getBlockchain();
        Block bestBlock;

        synchronized (blockchain) {
            bestBlock = blockchain.getBestBlock();
        }

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
        Repository repository = getRepoByJsonBlockId(block);

        if (repository == null)
            throw new NullPointerException();

        byte[] addressAsByteArray = stringHexToByteArray(address);
        BigInteger balance = repository.getBalance(addressAsByteArray);

        return toJsonHex(balance);
    }

    @Override
    public String eth_getBalance(String address) throws Exception {
        byte[] addressAsByteArray = stringHexToByteArray(address);
        BigInteger balance = this.repository.getBalance(addressAsByteArray);

        return toJsonHex(balance);
    }

    @Override
    public String eth_getStorageAt(String address, String storageIdx, String blockId) throws Exception {
        String s = null;
        try {
            byte[] addressAsByteArray = stringHexToByteArray(address);
            Repository repository = getRepoByJsonBlockId(blockId);
            if(repository == null)
                return null;
            DataWord storageValue = repository.
                    getStorageValue(addressAsByteArray, new DataWord(stringHexToByteArray(storageIdx)));
            if (storageValue != null) {
                return s = TypeConverter.toJsonHex(storageValue.getData());
            } else {
                return null;
            }
        } finally {
            if (logger.isDebugEnabled())
                logger.debug("eth_getStorageAt(" + address + ", " + storageIdx + ", " + blockId + "): " + s);
        }
    }

    @Override
    public String eth_getTransactionCount(String address, String blockId) throws Exception {
        String s = null;
        try {
            byte[] addressAsByteArray = TypeConverter.stringHexToByteArray(address);

            Repository repository = getRepoByJsonBlockId(blockId);
            if (repository != null) {
                BigInteger nonce = repository.getNonce(addressAsByteArray);
                return s = TypeConverter.toJsonHex(nonce);
            } else {
                return null;
            }
        } finally {
            if (logger.isDebugEnabled())
                logger.debug("eth_getTransactionCount(" + address + ", " + blockId + "): " + s);
        }
    }

    public Block getBlockByJSonHash(String blockHash) throws Exception {
        byte[] bhash = stringHexToByteArray(blockHash);
        return worldManager.getBlockchain().getBlockByHash(bhash);
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
                logger.debug("eth_getBlockTransactionCountByHash(" + blockHash + "): " + s);
            }
        }
    }

    public Block getBlockByNumberOrStr(String bnOrId) throws Exception {
        synchronized (worldManager.getBlockchain()) {
            Block b;
            if (bnOrId.equals("latest"))
                b = worldManager.getBlockchain().getBestBlock();
            else if (bnOrId.equals("earliest"))
                b = worldManager.getBlockchain().getBlockByNumber(0);
            else if (bnOrId.equals("pending"))
                throw new JsonRpcUnimplementedMethodException("The method don't support 'pending' as a parameter yet");
            else {
                long bn = JSonHexToLong(bnOrId);
                b = worldManager.getBlockchain().getBlockByNumber(bn);
            }
            return b;
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
                logger.debug("eth_getBlockTransactionCountByNumber(" + bnOrId + "): " + s);
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
        Block b = getBlockByNumberOrStr(bnOrId);
        long n = b.getUncleList().size();
        return toJsonHex(n);
    }

    @Override
    public String eth_getCode(String address, String blockId) throws Exception {
        if (blockId == null)
            throw new NullPointerException();

        String s = null;
        try {
            Block block = getByJsonBlockId(blockId);
            if(block == null) {
                return null;
            }
            byte[] addressAsByteArray = TypeConverter.stringHexToByteArray(address);
            Repository repository = getRepoByJsonBlockId(blockId);
            if(repository != null) {
                byte[] code = repository.getCode(addressAsByteArray);
                s = TypeConverter.toJsonHex(code);
            }
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getCode(" + address + ", " + blockId + "): " + s);
            }
        }
    }

    @Override
    public String eth_sign(String addr, String data) throws Exception {
        return ethModule.sign(addr, data);
    }

    @Override
    public String eth_sendTransaction(CallArguments args) throws Exception {
        return this.ethModule.sendTransaction(args);
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

            return s = TypeConverter.toJsonHex(tx.getHash());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_sendRawTransaction(" + rawData + "): " + s);
            }
        }
    }

    @Override
    public String eth_call(CallArguments args, String bnOrId) throws Exception {
        return ethModule.call(args, bnOrId);
    }

    @Override
    public String eth_estimateGas(CallArguments args) throws Exception {
        return ethModule.estimateGas(args);
    }

    public BlockInformationResult getBlockInformationResult(BlockInformation blockInformation) {
        BlockInformationResult bir = new BlockInformationResult();
        bir.hash = TypeConverter.toJsonHex(blockInformation.getHash());
        bir.totalDifficulty = TypeConverter.toJsonHex(blockInformation.getTotalDifficulty());
        bir.inMainChain = blockInformation.isInMainChain();

        return bir;
    }

    public BlockResult getBlockResult(Block b, boolean fullTx) {
        if (b==null)
            return null;

        byte[] mergeHeader = b.getBitcoinMergedMiningHeader();

        boolean isPending = (mergeHeader == null || mergeHeader.length == 0) && !b.isGenesis();

        BlockResult br = new BlockResult();
        br.number = isPending ? null : TypeConverter.toJsonHex(b.getNumber());
        br.hash = isPending ? null : TypeConverter.toJsonHex(b.getHash());
        br.parentHash = TypeConverter.toJsonHex(b.getParentHash());
        br.sha3Uncles= TypeConverter.toJsonHex(b.getUnclesHash());
        br.logsBloom = isPending ? null : TypeConverter.toJsonHex(b.getLogBloom());
        br.transactionsRoot = TypeConverter.toJsonHex(b.getTxTrieRoot());
        br.stateRoot = TypeConverter.toJsonHex(b.getStateRoot());
        br.receiptsRoot = TypeConverter.toJsonHex(b.getReceiptsRoot());
        br.miner = isPending ? null : TypeConverter.toJsonHex(b.getCoinbase());
        br.difficulty = TypeConverter.toJsonHex(b.getDifficulty());
        br.totalDifficulty = TypeConverter.toJsonHex(worldManager.getBlockchain().getBlockStore().getTotalDifficultyForHash(b.getHash()));
        br.extraData = TypeConverter.toJsonHex(b.getExtraData());
        br.size = TypeConverter.toJsonHex(b.getEncoded().length);
        br.gasLimit = TypeConverter.toJsonHex(b.getGasLimit());
        BigInteger mgp = b.getMinGasPriceAsInteger();
        br.minimumGasPrice = mgp != null ? mgp.toString() : "";
        br.gasUsed = TypeConverter.toJsonHex(b.getGasUsed());
        br.timestamp = TypeConverter.toJsonHex(b.getTimestamp());

        List<Object> txes = new ArrayList<>();
        if (fullTx) {
            for (int i = 0; i < b.getTransactionsList().size(); i++) {
                txes.add(new TransactionResultDTO(b, i, b.getTransactionsList().get(i)));
            }
        } else {
            for (Transaction tx : b.getTransactionsList()) {
                txes.add(toJsonHex(tx.getHash()));
            }
        }
        br.transactions = txes.toArray();

        List<String> ul = new ArrayList<>();
        for (BlockHeader header : b.getUncleList()) {
            ul.add(toJsonHex(header.getHash()));
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
        Blockchain blockchain = this.worldManager.getBlockchain();

        List<BlockInformation> binfos = blockchain.getBlocksInformationByNumber(blockNumber);

        for (BlockInformation binfo : binfos)
            result.add(getBlockInformationResult(binfo));

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
                logger.debug("eth_getBlockByHash(" +  blockHash + ", " + fullTransactionObjects + "): " + s);
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
                logger.debug("eth_getBlockByNumber(" +  bnOrId + ", " + fullTransactionObjects + "): " + s);
            }
        }
    }

    @Override
    public TransactionResultDTO eth_getTransactionByHash(String transactionHash) throws Exception {
        TransactionResultDTO s = null;
        try {
            Blockchain blockchain = worldManager.getBlockchain();

            byte[] txHash = stringHexToByteArray(transactionHash);
            Block block = null;

            TransactionInfo txInfo = blockchain.getTransactionInfo(txHash);

            if (txInfo == null) {
                if (transactionHash != null && transactionHash.startsWith("0x"))
                    transactionHash = transactionHash.substring(2);

                List<Transaction> txs = this.getTransactionsByJsonBlockId("pending");

                for (Transaction tx : txs) {
                    if (Hex.toHexString(tx.getHash()).equals(transactionHash))
                    {
                        return s = new TransactionResultDTO(null, null, tx);
                    }
                }
            } else {
                block = blockchain.getBlockByHash(txInfo.getBlockHash());
                // need to return txes only from main chain
                Block mainBlock = blockchain.getBlockByNumber(block.getNumber());
                if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
                    return null;
                }
                txInfo.setTransaction(block.getTransactionsList().get(txInfo.getIndex()));
            }

            if (txInfo == null) {
                return null;
            }
            return s = new TransactionResultDTO(block, txInfo.getIndex(), txInfo.getReceipt().getTransaction());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getTransactionByHash(" + transactionHash + "): " + s);
            }
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
                logger.debug("eth_getTransactionByBlockHashAndIndex(" + blockHash + ", " + index + "): " + s);
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
                logger.debug("eth_getTransactionByBlockNumberAndIndex(" + bnOrId + ", " + index + "): " + s);
            }
        }
    }

    @Override
    public TransactionReceiptDTO eth_getTransactionReceipt(String transactionHash) throws Exception {
        logger.trace("eth_getTransactionReceipt(" + transactionHash + ")");

        Blockchain blockchain = worldManager.getBlockchain();
        byte[] hash = stringHexToByteArray(transactionHash);
        TransactionInfo txInfo = blockchain.getReceiptStore().getInMainChain(hash, worldManager.getBlockStore());

        if (txInfo == null) {
            logger.trace("No transaction info for " + transactionHash);
            return null;
        }

        Block block = worldManager.getBlockStore().getBlockByHash(txInfo.getBlockHash());
        Transaction tx = block.getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        return new TransactionReceiptDTO(block, txInfo);
    }

    @Override
    public BlockResult eth_getUncleByBlockHashAndIndex(String blockHash, String uncleIdx) throws Exception {
        BlockResult s = null;
        try {
            Block block = this.worldManager.getBlockchain().getBlockByHash(stringHexToByteArray(blockHash));
            if (block == null) {
                return null;
            }
            int idx = JSonHexToInt(uncleIdx);
            if (idx >= block.getUncleList().size()) {
                return null;
            }
            BlockHeader uncleHeader = block.getUncleList().get(idx);
            Block uncle = this.worldManager.getBlockchain().getBlockByHash(uncleHeader.getHash());
            if (uncle == null) {
                uncle = new Block(uncleHeader, Collections.emptyList(), Collections.emptyList());
            }
            return s = getBlockResult(uncle, false);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getUncleByBlockHashAndIndex(" + blockHash + ", " + uncleIdx + "): " + s);
            }
        }
    }

    @Override
    public BlockResult eth_getUncleByBlockNumberAndIndex(String blockId, String uncleIdx) throws Exception {
        BlockResult s = null;
        try {
            Block block = getByJsonBlockId(blockId);
            return s = block == null ? null :
                    eth_getUncleByBlockHashAndIndex(Hex.toHexString(block.getHash()), uncleIdx);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getUncleByBlockNumberAndIndex(" + blockId + ", " + uncleIdx + "): " + s);
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
                logger.debug("eth_getCompilers(): " + Arrays.toString(s));
            }
        }
    }

    @Override
    public Map<String, CompilationResultDTO> eth_compileLLL(String contract) {
        throw new UnsupportedOperationException("LLL compiler not supported");
    }

    @Override
    public Map<String, CompilationResultDTO> eth_compileSolidity(String contract) throws Exception {
        return ethModule.compileSolidity(contract);
    }

    @Override
    public Map<String, CompilationResultDTO> eth_compileSerpent(String contract) {
        throw new UnsupportedOperationException("Serpent compiler not supported");
    }

    static class Filter {
        abstract static class FilterEvent {
            public abstract Object getJsonEventObject();
        }

        List<FilterEvent> events = new ArrayList<>();

        public synchronized boolean hasNew() {
            return !events.isEmpty();
        }

        public synchronized Object[] poll() {
            Object[] ret = new Object[events.size()];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = events.get(i).getJsonEventObject();
            }
            this.events.clear();
            return ret;
        }

        protected synchronized void add(FilterEvent evt) {
            events.add(evt);
        }

        public void newBlockReceived(Block b) {
        }

        public void newPendingTx(Transaction tx) {
            // add TransactionReceipt for PendingTx
        }
    }

    static class NewBlockFilter extends Filter {
        class NewBlockFilterEvent extends FilterEvent {
            public final Block b;

            NewBlockFilterEvent(Block b) {
                this.b = b;
            }

            @Override
            public String getJsonEventObject() {
                return toJsonHex(b.getHash());
            }
        }

        @Override
        public void newBlockReceived(Block b) {
            add(new NewBlockFilterEvent(b));
        }
    }

    static class PendingTransactionFilter extends Filter {
        class PendingTransactionFilterEvent extends FilterEvent {
            private final Transaction tx;

            PendingTransactionFilterEvent(Transaction tx) {
                this.tx = tx;
            }

            @Override
            public String getJsonEventObject() {
                return toJsonHex(tx.getHash());
            }
        }

        @Override
        public void newPendingTx(Transaction tx) {
            add(new PendingTransactionFilterEvent(tx));
        }
    }

    class JsonLogFilter extends Filter {
        class LogFilterEvent extends FilterEvent {
            private final LogFilterElement el;

            LogFilterEvent(LogFilterElement el) {
                this.el = el;
            }

            @Override
            public LogFilterElement getJsonEventObject() {
                return el;
            }
        }

        LogFilter logFilter;
        boolean onNewBlock;
        boolean onPendingTx;

        public JsonLogFilter(LogFilter logFilter) {
            this.logFilter = logFilter;
        }

        void onLogMatch(LogInfo logInfo, Block b, int txIndex, Transaction tx, int logIdx) {
            add(new LogFilterEvent(new LogFilterElement(logInfo, b, txIndex, tx, logIdx)));
        }

        void onTransactionReceipt(TransactionReceipt receipt, Block b, int txIndex) {
            if (logFilter.matchBloom(receipt.getBloomFilter())) {
                int logIdx = 0;
                for (LogInfo logInfo : receipt.getLogInfoList()) {
                    if (logFilter.matchBloom(logInfo.getBloom()) && logFilter.matchesExactly(logInfo)) {
                        onLogMatch(logInfo, b, txIndex, receipt.getTransaction(), logIdx);
                    }
                    logIdx++;
                }
            }
        }

        void onTransaction(Transaction tx, Block b, int txIndex) {
            TransactionInfo txInfo = worldManager.getBlockchain().getTransactionInfo(tx.getHash());
            TransactionReceipt receipt = txInfo.getReceipt();

            LogFilterElement[] logs = new LogFilterElement[receipt.getLogInfoList().size()];
            for (int i = 0; i < logs.length; i++) {
                LogInfo logInfo = receipt.getLogInfoList().get(i);
                if (logFilter.matchesContractAddress(logInfo.getAddress())) {
                    onTransactionReceipt(receipt, b, txIndex);
                }
            }
        }

        void onBlock(Block b) {
            if (logFilter.matchBloom(new Bloom(b.getLogBloom()))) {
                int txIdx = 0;
                for (Transaction tx : b.getTransactionsList()) {
                    onTransaction(tx, b, txIdx);
                    txIdx++;
                }
            }
        }

        @Override
        public void newBlockReceived(Block b) {
            if (onNewBlock) {
                onBlock(b);
            }
        }

        @Override
        public void newPendingTx(Transaction tx) {
            //empty method
        }
    }

    AtomicInteger filterCounter = new AtomicInteger(1);
    Map<Integer, Filter> installedFilters = new Hashtable<>();

    @Override
    public String eth_newFilter(FilterRequest fr) throws Exception {
        String str = null;
        try {
            LogFilter logFilter = new LogFilter();

            if (fr.address instanceof String) {
                logFilter.withContractAddress(stringHexToByteArray((String) fr.address));
            } else if (fr.address instanceof Collection<?>) {
                Collection<?> iterable = (Collection<?>)fr.address;

                byte[][] addresses = iterable.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(TypeConverter::stringHexToByteArray)
                        .toArray(byte[][]::new);

                logFilter.withContractAddress(addresses);
            }

            if (fr.topics != null) {
                for (Object topic : fr.topics) {
                    if (topic == null) {
                        logFilter.withTopic(null);
                    } else if (topic instanceof String) {
                        logFilter.withTopic(new DataWord(stringHexToByteArray((String) topic)).getData());
                    } else if (topic instanceof Collection<?>) {
                        Collection<?> iterable = (Collection<?>)topic;

                        byte[][] topics = iterable.stream()
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .map(TypeConverter::stringHexToByteArray)
                                .map(DataWord::new)
                                .map(DataWord::getData)
                                .toArray(byte[][]::new);

                        logFilter.withTopic(topics);
                    }
                }
            }

            JsonLogFilter filter = new JsonLogFilter(logFilter);

            int id;

            synchronized (filterLock) {
                id = filterCounter.getAndIncrement();
                installedFilters.put(id, filter);
            }

            Block blockFrom = fr.fromBlock == null ? null : getBlockByNumberOrStr(fr.fromBlock);
            Block blockTo = fr.toBlock == null ? null : getBlockByNumberOrStr(fr.toBlock);

            if (blockFrom != null) {
                // need to add historical data
                blockTo = blockTo == null ? worldManager.getBlockchain().getBestBlock() : blockTo;
                for (long blockNum = blockFrom.getNumber(); blockNum <= blockTo.getNumber(); blockNum++) {
                    filter.onBlock(worldManager.getBlockchain().getBlockByNumber(blockNum));
                }
            }

            // the following is not precisely documented
            if ("pending".equalsIgnoreCase(fr.fromBlock) || "pending".equalsIgnoreCase(fr.toBlock)) {
                filter.onPendingTx = true;
            } else if ("latest".equalsIgnoreCase(fr.fromBlock) || "latest".equalsIgnoreCase(fr.toBlock)) {
                filter.onNewBlock = true;
            }

            // RSK brute force
            filter.onNewBlock = true;

            return str = toJsonHex(id);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_newFilter(" + fr + "): " + str);
            }
        }
    }

    @Override
    public String eth_newBlockFilter() {
        String s = null;
        try {
            int id;

            synchronized (filterLock) {
                id = filterCounter.getAndIncrement();
                installedFilters.put(id, new NewBlockFilter());
            }

            return s = toJsonHex(id);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_newBlockFilter(): " + s);
            }
        }
    }

    @Override
    public String eth_newPendingTransactionFilter() {
        String s = null;
        try {
            int id;

            synchronized (filterLock) {
                id = filterCounter.getAndIncrement();
                installedFilters.put(id, new PendingTransactionFilter());
            }

            return s = toJsonHex(id);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_newPendingTransactionFilter(): " + s);
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

            synchronized (filterLock) {
                return s = installedFilters.remove(stringHexToBigInteger(id).intValue()) != null;
            }
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_uninstallFilter(" + id + "): " + s);
            }
        }
    }

    @Override
    public Object[] eth_getFilterChanges(String id) {
        Object[] s = null;
        try {
            synchronized (filterLock) {
                Filter filter = installedFilters.get(stringHexToBigInteger(id).intValue());
                if (filter == null) {
                    return null;
                }
                return s = filter.poll();
            }
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getFilterChanges(" + id + "): " + Arrays.toString(s));
            }
        }
    }

    @Override
    public Object[] eth_getFilterLogs(String id) {
        logger.debug("eth_getFilterLogs ...");
        return eth_getFilterChanges(id);
    }

    @Override
    public Object[] eth_getLogs(FilterRequest fr) throws Exception {
        logger.debug("eth_getLogs ...");
        String id = eth_newFilter(fr);
        Object[] ret = eth_getFilterChanges(id);
        eth_uninstallFilter(id);
        return ret;
    }

    @Override
    public Map<String, String> rpc_modules() {
        logger.debug("rpc_modules...");

        Map<String, String> map = new HashMap<>();

        for (ModuleDescription module : RskSystemProperties.CONFIG.getRpcModules())
            if (module.isEnabled())
                map.put(module.getName(), module.getVersion());

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
            return worldManager.getPendingState().getAllPendingTransactions();
        } else {
            Block block = getByJsonBlockId(id);
            return block != null ? block.getTransactionsList() : null;
        }
    }

    private Block getByJsonBlockId(String id) {
        if ("earliest".equalsIgnoreCase(id)) {
            return worldManager.getBlockchain().getBlockByNumber(0);
        } else if ("latest".equalsIgnoreCase(id)) {
            return worldManager.getBlockchain().getBestBlock();
        } else if ("pending".equalsIgnoreCase(id)) {
            throw new JsonRpcUnimplementedMethodException("The method don't support 'pending' as a parameter yet");
        } else {
            try {
                long blockNumber = stringHexToBigInteger(id).longValue();
                return worldManager.getBlockchain().getBlockByNumber(blockNumber);
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                throw new JsonRpcInvalidParamException("invalid blocknumber " + id);
            }
        }
    }

    private Repository getRepoByJsonBlockId(String id) {
        if ("pending".equalsIgnoreCase(id)) {
            return worldManager.getPendingState().getRepository();
        } else {
            Block block = getByJsonBlockId(id);
            if (block != null) {
                return ((Repository) this.repository).getSnapshotTo(block.getStateRoot());
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
    public Map<String, Object> eth_bridgeState() throws Exception {
        return ethModule.bridgeState();
    }

    @Override
    public String evm_snapshot() {
        Blockchain blockchain = worldManager.getBlockchain();

        int snapshotId = snapshotManager.takeSnapshot(blockchain);

        logger.debug("evm_snapshot(): {}", snapshotId);

        return toJsonHex(snapshotId);
    }

    @Override
    public boolean evm_revert(String snapshotId) {
        try {
            int sid = stringHexToBigInteger(snapshotId).intValue();
            return snapshotManager.revertToSnapshot(worldManager.getBlockchain(), sid);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw new JsonRpcInvalidParamException("invalid snapshot id " + snapshotId, e);
        } finally {
            if (logger.isDebugEnabled())
                logger.debug("evm_revert({})", snapshotId);
        }
    }

    @Override
    public void evm_reset() {
        snapshotManager.resetSnapshots(worldManager.getBlockchain());
        if (logger.isDebugEnabled())
            logger.debug("evm_reset()");
    }

    @Override
    public void evm_mine() {
        minerManager.mineBlock(worldManager.getBlockchain(), minerClient, minerServer);
        if (logger.isDebugEnabled())
            logger.debug("evm_mine()");
    }

    @Override
    public String evm_increaseTime(String seconds) {
        try {
            long nseconds = stringHexToBigInteger(seconds).longValue();
            String result = toJsonHex(minerServer.increaseTime(nseconds));
            if (logger.isDebugEnabled())
                logger.debug("evm_increaseTime({}): {}", seconds, result);
            return result;
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw new JsonRpcInvalidParamException("invalid number of seconds " + seconds, e);
        }
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
        if (this.peerScoringManager == null)
            return;

        try {
            this.peerScoringManager.banAddress(address);
        } catch (InvalidInetAddressException e) {
            throw new JsonRpcInvalidParamException("invalid banned address " + address, e);
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
        if (this.peerScoringManager == null)
            return;

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
        if (this.peerScoringManager != null)
            return this.peerScoringManager.getPeersInformation().toArray(new PeerScoringInformation[0]);

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
