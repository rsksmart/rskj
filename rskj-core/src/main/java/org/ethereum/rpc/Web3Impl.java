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
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.SyncProcessor;
import co.rsk.rpc.ModuleDescription;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.trace.TraceModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.scoring.*;
import co.rsk.util.HexUtils;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.core.genesis.BlockTag;
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
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.*;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static co.rsk.util.HexUtils.*;
import static java.lang.Math.max;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.*;

@SuppressWarnings("java:S100")
public class Web3Impl implements Web3 {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private static final String CLIENT_VERSION_PREFIX = "RskJ";
    private static final String NON_EXISTING_KEY_RESPONSE = "0x0000000000000000000000000000000000000000000000000000000000000000";

    private final MinerClient minerClient;
    private final MinerServer minerServer;
    private final ChannelManager channelManager;
    private final PeerScoringManager peerScoringManager;
    private final PeerServer peerServer;

    private final Blockchain blockchain;
    private final ReceiptStore receiptStore;
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
    private final SyncProcessor syncProcessor;

    private final SignatureCache signatureCache;

    private Ethereum eth;

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
            Web3InformationRetriever web3InformationRetriever,
            SyncProcessor syncProcessor,
            SignatureCache signatureCache) {
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
        this.hashRateCalculator = hashRateCalculator;
        this.configCapabilities = configCapabilities;
        this.config = config;
        this.filterManager = new FilterManager(eth);
        this.buildInfo = buildInfo;
        this.blocksBloomStore = blocksBloomStore;
        this.web3InformationRetriever = web3InformationRetriever;
        this.syncProcessor = syncProcessor;
        this.signatureCache = signatureCache;

        personalModule.init();
    }

    @VisibleForTesting
    void setEth(Ethereum eth) {
        this.eth = eth;
    }

    @Override
    public void start() {
        hashRateCalculator.start();
    }

    @Override
    public void stop() {
        hashRateCalculator.stop();
    }

    @Override
    public String web3_clientVersion() {
        String javaVersion = System.getProperty("java.version");
        String javaMajorVersion = javaVersion.startsWith("1.") ? javaVersion.split("\\.")[1] : javaVersion.split("\\.")[0];
        String formattedJavaVersion = "Java" + javaMajorVersion;

        String clientVersion = CLIENT_VERSION_PREFIX + "/" + config.projectVersion() + "/" +
                System.getProperty("os.name") + "/" + formattedJavaVersion + "/" +
                config.projectVersionModifier() + "-" + buildInfo.getBuildHash();

        if (logger.isDebugEnabled()) {
            logger.debug("web3_clientVersion(): {}", clientVersion);
        }

        return clientVersion;
    }

    @Override
    public String web3_sha3(String data) throws Exception {

        String hash = null;

        try {

            if (HexUtils.isHexWithPrefix(data)) {

                byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
                byte[] result = HashUtil.keccak256(HexUtils.decode(dataBytes));
                hash = HexUtils.toJsonHex(result);

                return hash;

            } else {
                throw invalidParamError("Parameter must be hexadecimal encoded with the '0x' prefix.");
            }

        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("web3_sha3({}): {}", data, hash);
            }
        }

    }

    @Override
    public String net_version() {
        String s = null;
        try {
            byte netVersion = config.getNetworkConstants().getChainId();
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
            s = HexUtils.toQuantityJsonHex(n);
            return s;
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

            return s = HexUtils.toQuantityJsonHex(version);
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
        if (!syncProcessor.isSyncing()) {
            return false;
        }

        long initialBlockNum = syncProcessor.getInitialBlockNumber();
        long currentBlockNum = blockchain.getBestBlock().getNumber();
        long highestBlockNum = syncProcessor.getHighestBlockNumber();

        SyncingResult s = new SyncingResult();
        try {
            s.setStartingBlock(HexUtils.toQuantityJsonHex(initialBlockNum));
            s.setCurrentBlock(HexUtils.toQuantityJsonHex(currentBlockNum));
            s.setHighestBlock(toQuantityJsonHex(highestBlockNum));
            return s;
        } finally {
            logger.debug("eth_syncing(): starting {}, current {}, highest {} ", s.getStartingBlock(), s.getCurrentBlock(), s.getHighestBlock());
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
    public String eth_hashrate() {
        BigInteger hashesPerHour = hashRateCalculator.calculateNodeHashRate(Duration.ofHours(1));
        BigInteger hashesPerSecond = hashesPerHour.divide(BigInteger.valueOf(Duration.ofHours(1).getSeconds()));

        logger.debug("eth_hashrate(): {}", hashesPerSecond);

        return HexUtils.toQuantityJsonHex(hashesPerSecond);
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
            gasPrice = HexUtils.toQuantityJsonHex(eth.getGasPrice().asBigInteger().longValue());
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
    public String eth_call(CallArgumentsParam args, Map<String, String> inputs) {
        return invokeByBlockRef(inputs, blockNumber -> getEthModule().call(args, new BlockIdentifierParam(blockNumber)));
    }

    @Override
    public String eth_getCode(HexAddressParam address, BlockRefParam blockRefParam) {
        if (blockRefParam.getIdentifier() != null) {
            return this.getCode(address, blockRefParam.getIdentifier());
        } else {
            return this.eth_getCode(address, blockRefParam.getInputs());
        }
    }

    private String eth_getCode(HexAddressParam address, Map<String, String> inputs) {
        return invokeByBlockRef(inputs, blockNumber -> this.getCode(address, blockNumber));
    }

    @Override
    public String eth_getBalance(HexAddressParam address, BlockRefParam blockRefParam) {
        if (blockRefParam.getIdentifier() != null) {
            return this.eth_getBalance(address, blockRefParam.getIdentifier());
        } else {
            return this.eth_getBalance(address, blockRefParam.getInputs());
        }
    }

    private String eth_getBalance(HexAddressParam address, String block) {
        /* HEX String  - an integer block number
         *  String "earliest"  for the earliest/genesis block
         *  String "latest"  - for the latest mined block
         *  String "pending"  - for the pending state/transactions
         */

        AccountInformationProvider accountInformationProvider = web3InformationRetriever.getInformationProvider(block);

        RskAddress addr = address.getAddress();
        Coin balance = accountInformationProvider.getBalance(addr);

        return toQuantityJsonHex(balance.asBigInteger());
    }


    private String eth_getBalance(HexAddressParam address, Map<String, String> inputs) {
        return invokeByBlockRef(inputs, blockNumber -> this.eth_getBalance(address, blockNumber));
    }

    private Optional<String> applyIfPresent(final Map<String, String> inputs, final String reference, final UnaryOperator<String> function) {
        return Optional.ofNullable(inputs.get(reference)).map(function);
    }

    private boolean isInMainChain(Block block) {
        return this.blockchain.getBlocksInformationByNumber(block.getNumber())
                .stream().anyMatch(b -> b.isInMainChain() && Arrays.equals(b.getHash(), block.getHash().getBytes()));
    }

    @Override
    public String eth_getBalance(HexAddressParam address) {
        return eth_getBalance(address, "latest");
    }

    @Override
    public String eth_getStorageAt(HexAddressParam address, HexNumberParam storageIdx, BlockRefParam blockRefParam) {
        if (blockRefParam.getIdentifier() != null) {
            return this.eth_getStorageAt(address, storageIdx, blockRefParam.getIdentifier());
        }
        return this.eth_getStorageAt(address, storageIdx, blockRefParam.getInputs());
    }

    private String eth_getStorageAt(HexAddressParam address, HexNumberParam storageIdx, Map<String, String> blockRef) {
        return invokeByBlockRef(blockRef, blockNumber -> this.eth_getStorageAt(address, storageIdx, blockNumber));
    }

    private String eth_getStorageAt(HexAddressParam address, HexNumberParam storageIdx, String blockId) {
        String response = null;

        try {
            AccountInformationProvider accountInformationProvider =
                    web3InformationRetriever.getInformationProvider(blockId);
            DataWord key = DataWord.valueOf(HexUtils.strHexOrStrNumberToByteArray(storageIdx.getHexNumber()));

            response = Optional.ofNullable(accountInformationProvider.getStorageValue(address.getAddress(), key))
                    .map(DataWord::getData)
                    .map(HexUtils::toUnformattedJsonHex)
                    .orElse(NON_EXISTING_KEY_RESPONSE);
            return response;
        } finally {
            logger.debug("eth_getStorageAt({}, {}, {}): {}", address, storageIdx, blockId, response);
        }
    }

    @Override
    public String rsk_getStorageBytesAt(HexAddressParam address, HexNumberParam storageIdx, BlockRefParam blockRefParam) {
        if (blockRefParam.getIdentifier() != null) {
            return this.rsk_getStorageBytesAt(address, storageIdx, blockRefParam.getIdentifier());
        }
        return this.rsk_getStorageBytesAt(address, storageIdx, blockRefParam.getInputs());
    }

    private String rsk_getStorageBytesAt(HexAddressParam address, HexNumberParam storageIdx, String blockId) {
        String response = null;

        try {
            AccountInformationProvider accountInformationProvider =
                    web3InformationRetriever.getInformationProvider(blockId);
            DataWord key = DataWord.valueOf(HexUtils.strHexOrStrNumberToByteArray(storageIdx.getHexNumber()));

            response = Optional.ofNullable(accountInformationProvider.getStorageBytes(address.getAddress(), key))
                    .map(HexUtils::toUnformattedJsonHex)
                    .orElse("0x0");
            return response;
        } finally {
            logger.debug("rsk_getStorageAt({}, {}, {}): {}", address, storageIdx, blockId, response);
        }
    }

    private String rsk_getStorageBytesAt(HexAddressParam address, HexNumberParam storageIdx, Map<String, String> blockRef) {
        return invokeByBlockRef(blockRef, blockNumber -> this.rsk_getStorageBytesAt(address, storageIdx, blockNumber));
    }

    @Override
    public String eth_getTransactionCount(HexAddressParam address, BlockRefParam blockRefParam) {
        if (blockRefParam.getIdentifier() != null) {
            return this.eth_getTransactionCount(address, blockRefParam.getIdentifier());
        } else {
            return this.eth_getTransactionCount(address, blockRefParam.getInputs());
        }
    }

    private String eth_getTransactionCount(HexAddressParam address, Map<String, String> inputs) {
        return invokeByBlockRef(inputs, blockNumber -> this.eth_getTransactionCount(address, blockNumber));
    }

    /**
     * eip-1898 implementations.
     * It processes inputs maps ex: { "blockNumber": "0x0" },
     * { "blockHash": "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3", "requireCanonical": false }
     * and invoke a function after processing.
     *
     * @param inputs                map
     * @param toInvokeByBlockNumber a function that returns a string based on the block number
     * @return function invocation result
     */
    protected String invokeByBlockRef(Map<String, String> inputs, UnaryOperator<String> toInvokeByBlockNumber) {
        final boolean requireCanonical = Boolean.parseBoolean(inputs.get("requireCanonical"));
        return applyIfPresent(inputs, "blockHash", blockHash -> this.toInvokeByBlockHash(blockHash, requireCanonical, toInvokeByBlockNumber))
                .orElseGet(() -> applyIfPresent(inputs, "blockNumber", toInvokeByBlockNumber)
                        .orElseThrow(() -> invalidParamError("Invalid block input"))
                );
    }

    private String toInvokeByBlockHash(String blockHash, boolean requireCanonical, Function<String, String> toInvokeByBlockNumber) {
        Block block = Optional.ofNullable(this.blockchain.getBlockByHash(stringHexToByteArray(blockHash)))
                .orElseThrow(() -> blockNotFound(String.format("Block with hash %s not found", blockHash)));

        //check if is canonical required
        if (requireCanonical && !isInMainChain(block)) {
            throw blockNotFound(String.format("Block with hash %s is not canonical and it is required", blockHash));
        }

        return toInvokeByBlockNumber.apply(toQuantityJsonHex(block.getNumber()));
    }

    private String eth_getTransactionCount(HexAddressParam address, String blockId) {
        String s = null;
        try {
            RskAddress addr = address.getAddress();
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

        if (bhash.length != Keccak256.HASH_LEN) {
            throw invalidParamError("invalid argument 0: hex string has length " + (bhash.length * 2) + ", want " + (Keccak256.HASH_LEN * 2) + " for hash");
        }

        return this.blockchain.getBlockByHash(bhash);
    }

    @Override
    public String eth_getBlockTransactionCountByHash(BlockHashParam blockHash) {
        String s = null;
        try {
            Block b = blockchain.getBlockByHash(blockHash.getHash().getBytes());

            if (b == null) {
                return null;
            }

            long n = b.getTransactionsList().size();

            s = HexUtils.toQuantityJsonHex(n);
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
            long bn = jsonHexToLong(bnOrId);
            b = blockchain.getBlockByNumber(bn);
        }

        return b;
    }

    @Override
    public String eth_getBlockTransactionCountByNumber(BlockIdentifierParam bnOrId) {
        String s = null;
        try {

            List<Transaction> txs = web3InformationRetriever.getTransactions(bnOrId.getIdentifier());

            s = toQuantityJsonHex(txs.size());
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockTransactionCountByNumber({}): {}", bnOrId, s);
            }
        }
    }

    @Override
    public String eth_getUncleCountByBlockHash(BlockHashParam blockHashParam) {
        String blockHash = blockHashParam.getHash().toString();
        Block b = getBlockByJSonHash(blockHash);
        if (b == null) {
            throw blockNotFound(String.format("Block with hash %s not found", blockHash));
        }

        long n = b.getUncleList().size();
        return toQuantityJsonHex(n);
    }

    @Override
    public String eth_getUncleCountByBlockNumber(BlockIdentifierParam identifierParam) {
        String bnorId = identifierParam.getIdentifier();
        return web3InformationRetriever.getBlock(bnorId)
                .map(Block::getUncleList)
                .map(List::size)
                .map(HexUtils::toQuantityJsonHex)
                .orElseThrow(() -> blockNotFound(String.format("Block %s not found", bnorId)));
    }

    public BlockInformationResult getBlockInformationResult(BlockInformation blockInformation) {
        BlockInformationResult bir = new BlockInformationResult();
        bir.setHash(toUnformattedJsonHex(blockInformation.getHash()));
        bir.setTotalDifficulty(toQuantityJsonHex(blockInformation.getTotalDifficulty().asBigInteger()));
        bir.setInMainChain(blockInformation.isInMainChain());

        return bir;
    }

    public BlockResultDTO getBlockResult(Block b, boolean fullTx) {
        return BlockResultDTO.fromBlock(b, fullTx, this.blockStore, config.skipRemasc(), config.rpcZeroSignatureIfRemasc(), signatureCache);
    }

    public BlockInformationResult[] eth_getBlocksByNumber(String number) {
        long blockNumber;

        try {
            blockNumber = HexUtils.stringNumberAsBigInt(number).longValue();
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
    public BlockResultDTO eth_getBlockByHash(BlockHashParam blockHash, Boolean fullTransactionObjects) {
        if (blockHash == null) {
            throw invalidParamError("blockHash is null");
        }

        BlockResultDTO s = null;
        try {
            Block b = this.blockchain.getBlockByHash(blockHash.getHash().getBytes());
            s = (b == null ? null : getBlockResult(b, fullTransactionObjects));
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getBlockByHash({}, {}): {}", blockHash, fullTransactionObjects, s);
            }
        }
    }

    @Override
    public BlockResultDTO eth_getBlockByNumber(BlockIdentifierParam identifierParam, Boolean fullTransactionObjects) {
        BlockResultDTO s = null;
        String bnOrId = identifierParam.getIdentifier();

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
    public TransactionResultDTO eth_getTransactionByHash(TxHashParam transactionHash) {
        TransactionResultDTO s = null;
        try {
            Keccak256 txHash = transactionHash.getHash();
            Block block = null;

            TransactionInfo txInfo = this.receiptStore.getInMainChain(txHash.getBytes(), blockStore).orElse(null);

            if (txInfo == null) {
                List<Transaction> txs = web3InformationRetriever.getTransactions("pending");

                for (Transaction tx : txs) {
                    if (tx.getHash().equals(txHash)) {
                        s = new TransactionResultDTO(null, null, tx, config.rpcZeroSignatureIfRemasc(), signatureCache);
                        return s;
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
            s = new TransactionResultDTO(block, txInfo.getIndex(), txInfo.getReceipt().getTransaction(), config.rpcZeroSignatureIfRemasc(), signatureCache);
            return s;
        } finally {
            logger.debug("eth_getTransactionByHash({}): {}", transactionHash, s);
        }
    }

    @Override
    public TransactionResultDTO eth_getTransactionByBlockHashAndIndex(BlockHashParam blockHash, HexIndexParam index) {
        if (blockHash == null) {
            throw invalidParamError("blockHash is null");
        }

        if (index == null) {
            throw invalidParamError("index is null");
        }

        TransactionResultDTO s = null;
        try {
            Block b = blockchain.getBlockByHash(blockHash.getHash().getBytes());

            if (b == null) {
                return null;
            }

            int idx = index.getIndex();

            if (idx >= b.getTransactionsList().size()) {
                return null;
            }

            Transaction tx = b.getTransactionsList().get(idx);
            s = new TransactionResultDTO(b, idx, tx, config.rpcZeroSignatureIfRemasc(), signatureCache);
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getTransactionByBlockHashAndIndex({}, {}): {}", blockHash, index, s);
            }
        }
    }

    @Override
    public TransactionResultDTO eth_getTransactionByBlockNumberAndIndex(BlockIdentifierParam identifierParam, HexIndexParam index) {
        TransactionResultDTO s = null;
        String bnOrId = identifierParam.getIdentifier();
        try {
            Optional<Block> block = web3InformationRetriever.getBlock(bnOrId);
            if (!block.isPresent()) {
                return null;
            }

            int idx = index.getIndex();
            List<Transaction> txs = web3InformationRetriever.getTransactions(bnOrId);
            if (idx >= txs.size()) {
                return null;
            }

            s = new TransactionResultDTO(block.get(), idx, txs.get(idx), config.rpcZeroSignatureIfRemasc(), signatureCache);
            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getTransactionByBlockNumberAndIndex({}, {}): {}", bnOrId, index, s);
            }
        }
    }

    @Override
    public TransactionReceiptDTO eth_getTransactionReceipt(TxHashParam transactionHash) {
        logger.trace("eth_getTransactionReceipt({})", transactionHash);

        byte[] hash = stringHexToByteArray(transactionHash.getHash().toHexString());
        TransactionInfo txInfo = receiptStore.getInMainChain(hash, blockStore).orElse(null);

        if (txInfo == null) {
            logger.trace("No transaction info for {}", transactionHash);
            return null;
        }

        Block block = blockStore.getBlockByHash(txInfo.getBlockHash());
        Transaction tx = block.getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        return new TransactionReceiptDTO(block, txInfo, signatureCache);
    }

    @Override
    public TransactionResultDTO[] eth_pendingTransactions() {
        return ethModule.ethPendingTransactions().stream()
                .map(tx -> new TransactionResultDTO(null, null, tx, config.rpcZeroSignatureIfRemasc(), signatureCache))
                .toArray(TransactionResultDTO[]::new);
    }

    @Override
    public BlockResultDTO eth_getUncleByBlockHashAndIndex(BlockHashParam blockHash, HexIndexParam uncleIdx) {
        BlockResultDTO s = null;

        try {
            Block block = blockchain.getBlockByHash(blockHash.getHash().getBytes());

            if (block == null) {
                return null;
            }

            s = getUncleResultDTO(uncleIdx.getIndex(), block);

            return s;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getUncleByBlockHashAndIndex({}, {}): {}", blockHash, uncleIdx, s);
            }
        }
    }

    private BlockResultDTO getUncleResultDTO(Integer uncleIdx, Block block) {

        if (uncleIdx >= block.getUncleList().size()) {
            return null;
        }

        BlockHeader uncleHeader = block.getUncleList().get(uncleIdx);
        Block uncle = blockchain.getBlockByHash(uncleHeader.getHash().getBytes());

        if (uncle == null) {
            boolean isRskip126Enabled = config.getActivationConfig().isActive(ConsensusRule.RSKIP126, uncleHeader.getNumber());
            uncle = Block.createBlockFromHeader(uncleHeader, isRskip126Enabled);
        }

        return getBlockResult(uncle, false);
    }

    @Override
    public BlockResultDTO eth_getUncleByBlockNumberAndIndex(BlockIdentifierParam identifierParam, HexIndexParam uncleIdx) {
        BlockResultDTO s = null;
        String blockId = identifierParam.getIdentifier();

        try {
            Optional<Block> block = web3InformationRetriever.getBlock(blockId);

            if (!block.isPresent()) {
                return null;
            }
            int idx = uncleIdx.getIndex();
            s = getUncleResultDTO(idx, block.get());

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
    public String eth_newFilter(FilterRequestParam filterRequestParam) throws Exception {
        return newFilter(filterRequestParam.toFilterRequest());
    }

    private String newFilter(FilterRequest fr) {
        String str = null;
        try {
            Filter filter = LogFilter.fromFilterRequest(fr, blockchain, blocksBloomStore, config.getRpcEthGetLogsMaxBlockToQuery(), config.getRpcEthGetLogsMaxLogsToReturn());
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
    public boolean eth_uninstallFilter(HexIndexParam id) {
        Boolean s = null;

        try {
            if (id == null) {
                return false;
            }

            return filterManager.removeFilter(id.getIndex());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_uninstallFilter({}): {}", id, s);
            }
        }
    }

    @Override
    public Object[] eth_getFilterChanges(HexIndexParam id) {
        logger.debug("eth_getFilterChanges ...");

        // TODO(mc): this is a quick solution that seems to work with OpenZeppelin tests, but needs to be reviewed
        // We do the same as in Ganache: mine a block in each request to getFilterChanges so block filters work
        if (config.isMinerClientEnabled() && config.minerClientAutoMine()) {
            minerServer.buildBlockToMine(false);
            minerClient.mineBlock();
        }

        Object[] s = null;

        try {
            s = this.filterManager.getFilterEvents(id.getIndex(), true);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getFilterChanges({}): {}", id, Arrays.toString(s));
            }
        }

        return s;
    }

    @Override
    public Object[] eth_getFilterLogs(HexIndexParam id) {
        logger.debug("eth_getFilterLogs ...");

        Object[] s = null;

        try {
            s = this.filterManager.getFilterEvents(id.getIndex(), false);

        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_getFilterLogs({}): {}", id, Arrays.toString(s));
            }
        }

        return s;
    }

    @Override
    public Object[] eth_getLogs(FilterRequestParam fr) throws Exception {
        logger.debug("eth_getLogs ...");
        String id = newFilter(fr.toFilterRequest());
        HexIndexParam idParam = new HexIndexParam(id);
        Object[] ret = eth_getFilterLogs(idParam);
        eth_uninstallFilter(idParam);
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
        if (seed == null) {
            throw RskJsonRpcRequestException.invalidParamError("Seed is null");
        }
        return personalModule.newAccountWithSeed(seed);
    }

    @Override
    public String personal_newAccount(String passphrase) {
        if (passphrase == null) {
            throw RskJsonRpcRequestException.invalidParamError("Passphrase is null");
        }
        return personalModule.newAccount(passphrase);
    }

    @Override
    public String personal_importRawKey(HexKeyParam key, String passphrase) {
        return personalModule.importRawKey(key, passphrase);
    }

    @Override
    public String personal_dumpRawKey(HexAddressParam address) throws Exception {
        return personalModule.dumpRawKey(address);
    }

    @Override
    public String[] personal_listAccounts() {
        return personalModule.listAccounts();
    }

    @Override
    public String personal_sendTransaction(CallArgumentsParam args, String passphrase) throws Exception {
        return personalModule.sendTransaction(args, passphrase);
    }

    @Override
    public boolean personal_unlockAccount(HexAddressParam address, String passphrase, HexDurationParam duration) {
        return personalModule.unlockAccount(address, passphrase, duration);
    }

    @Override
    public boolean personal_lockAccount(HexAddressParam address) {
        return personalModule.lockAccount(address);
    }

    @Override
    public String eth_estimateGas(CallArgumentsParam args) {
        return eth_estimateGas(args, null);
    }

    @Override
    public String eth_estimateGas(CallArgumentsParam args, @Nullable BlockIdentifierParam bnOrId) {
        if (bnOrId == null) {
            bnOrId = new BlockIdentifierParam(BlockTag.LATEST.getTag());
        }
        return getEthModule().estimateGas(args, bnOrId);
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
            throw invalidParamError("invalid banned address " + address, e);
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

    /**
     * Clears scoring for the received id
     *
     * @param id peer identifier: firstly tried as an InetAddress, used as a NodeId otherwise
     * @return the list of scoring information, per node id and address
     */
    @SuppressWarnings("squid:S1166")
    @Override
    public PeerScoringInformation[] sco_clearPeerScoring(String id) {
        if (this.peerScoringManager == null) {
            return new PeerScoringInformation[]{};
        }

        try {
            InetAddress address = InetAddress.getByName(id);
            this.peerScoringManager.clearPeerScoring(address);
        } catch (UnknownHostException uhe) {
            logger.debug("Received id '{}' is not an InetAddress, using it as nodeId", id);
            this.peerScoringManager.clearPeerScoring(NodeID.ofHexString(id));
        }

        return sco_peerList();
    }
}
