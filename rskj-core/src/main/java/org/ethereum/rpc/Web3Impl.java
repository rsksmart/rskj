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

import co.rsk.core.SnapshotManager;
import co.rsk.mine.MinerManager;
import co.rsk.peg.Bridge;
import co.rsk.rpc.ModuleDescription;
import com.google.common.annotations.VisibleForTesting;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.WalletAccount;
import co.rsk.core.Wallet;
import co.rsk.net.BlockProcessor;
import co.rsk.peg.BridgeState;
import co.rsk.peg.BridgeStateReader;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
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
import org.ethereum.rpc.dto.*;
import org.ethereum.rpc.exception.JsonRpcInvalidParamException;
import org.ethereum.rpc.exception.JsonRpcUnimplementedMethodException;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
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
    long maxBlockNumberSeen;

    private final Object filterLock = new Object();

    private Wallet wallet;

    private SolidityCompiler solidityCompiler;

    public Web3Impl(SolidityCompiler compiler, Wallet wallet) {
        this.solidityCompiler = compiler;
        this.wallet = wallet;
    }

    public Web3Impl(Ethereum eth, RskSystemProperties properties, Wallet wallet) {
        this.eth = eth;
        this.worldManager = eth.getWorldManager();
        this.repository = eth.getRepository();
        this.wallet = wallet;

        initialBlockNumber = this.worldManager.getBlockchain().getBestBlock().getNumber();

        compositeEthereumListener = new CompositeEthereumListener();

        this.solidityCompiler = this.worldManager.getSolidityCompiler();

        compositeEthereumListener.addListener(this.setupListener());

        this.eth.addListener(compositeEthereumListener);

        // TODO adding default accounts, just so that integration tests pass
        if (properties.getBlockchainConfig() instanceof RegTestConfig) {
            personal_newAccountWithSeed("cow");
        }

        String secret = properties.coinbaseSecret();
        personal_newAccountWithSeed(secret);

        // initializes wallet accounts based on configuration
        List<WalletAccount> accs = properties.walletAccounts();

        for (WalletAccount acc : accs)
            eth_addAccount(acc.getPrivateKey());
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

    public String[] listObjectHashtoJsonHexArray(Collection<SerializableObject> c) {
        String[] arr = new String[c.size()];
        int i = 0;
        for (SerializableObject item : c) {
            // Todo: Which hash is required? RawHash or Hash ?
            arr[i++] = toJsonHex(item.getHash());
        }
        return arr;
    }

    public String web3_clientVersion() {

        String clientVersion = baseClientVersion + "/" + SystemProperties.CONFIG.projectVersion() + "/" +
                System.getProperty("os.name") + "/Java1.8/" +
                SystemProperties.CONFIG.projectVersionModifier() + "-" + BuildInfo.getBuildHash();

        if (logger.isDebugEnabled()) {
            logger.debug("web3_clientVersion(): " + clientVersion);
        }

        return clientVersion;

    }

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


    public String net_version() {
        String s = null;
        try {
            byte netVersion = RskSystemProperties.RSKCONFIG.getBlockchainConfig().getCommonConstants().getChainId();
            return s = Byte.toString(netVersion);
        }
        finally {
            if (logger.isDebugEnabled()) {
                logger.debug("net_version(): " + s);
            }
        }
    }


    public String net_peerCount() {
        String s = null;
        try {

            ChannelManager channelManager = worldManager.getChannelManager();
            int n = channelManager.getActivePeers().size();
            return s = TypeConverter.toJsonHex(n);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("net_peerCount(): " + s);
            }
        }
    }


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

    public String eth_protocolVersion() {
        return rsk_protocolVersion();
    }

    public Object eth_syncing() {
        Blockchain blockchain = worldManager.getBlockchain();

        long currentBlock = worldManager.getBlockchain().getBestBlock().getNumber();
        BlockProcessor processor = worldManager.getNodeBlockProcessor();
        if (processor == null) {
            // TODO(raltman): processor should never be null. Change Web3Impl to request BlockProcessor from Roostock.
            return false;
        }
        long highestBlock = processor.getLastKnownBlockNumber();

        if (currentBlock >= highestBlock)
            return false;

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

    public String eth_coinbase() {
        String s = null;
        try {
            return s = toJsonHex(worldManager.getMinerServer().getCoinbaseAddress());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_coinbase(): " + s);
            }
        }
    }


    public boolean eth_mining() {
        Boolean s = null;
        try {
            return s = worldManager.getMinerClient().isMining();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_mining(): " + s);
            }
        }
    }

    public String eth_hashrate() {
        BigDecimal hashesPerSecond = BigDecimal.ZERO;
        if(RskSystemProperties.RSKCONFIG.minerServerEnabled()) {
            BigInteger hashesPerHour = this.worldManager.getHashRateCalculator().calculateNodeHashRate(1L, TimeUnit.HOURS);
            hashesPerSecond = new BigDecimal(hashesPerHour)
                    .divide(new BigDecimal(TimeUnit.HOURS.toSeconds(1)), 3, RoundingMode.HALF_UP);
        }

        String result = hashesPerSecond.toString();

        if (logger.isDebugEnabled()) {
            logger.debug("eth_hashrate(): " + result);
        }

        return result;
    }

    public String eth_netHashrate() {
        BigInteger hashesPerHour = this.worldManager.getHashRateCalculator().calculateNetHashRate(1L, TimeUnit.HOURS);
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
        Collection<Channel> peers = worldManager.getChannelManager().getActivePeers();
        List<String> response = new ArrayList<>();
        peers.forEach(channel -> response.add(channel.toString()));
        return response.stream().toArray(String[]::new);
    }

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

    public String[] eth_accounts() {
        String[] s = null;
        try {
            return s = personal_listAccounts();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_accounts(): " + Arrays.toString(s));
            }
        }
    }

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

    public String eth_getUncleCountByBlockHash(String blockHash) throws Exception {
        Block b = getBlockByJSonHash(blockHash);
        long n = b.getUncleList().size();
        return toJsonHex(n);
    }

    public String eth_getUncleCountByBlockNumber(String bnOrId) throws Exception {
        Block b = getBlockByNumberOrStr(bnOrId);
        long n = b.getUncleList().size();
        return toJsonHex(n);
    }

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

    public String eth_sign(String addr, String data) throws Exception {
        String s = null;
        try {
            return s = this.sign(data, getAccount(JSonHexToHex(addr)));
        } finally {
            if (logger.isDebugEnabled())
                logger.debug("eth_sign({}, {}): {}", addr, data, s);
        }
    }

    private String sign(String data, Account account) {
        if (account == null)
            throw new JsonRpcInvalidParamException("Account not found");

        byte[] dataHash = TypeConverter.stringHexToByteArray(data);
        ECKey.ECDSASignature signature = account.getEcKey().sign(dataHash);

        String signatureAsString = signature.r.toString() + signature.s.toString() + signature.v;

        // byte[] rlpSig = RLP.encode(signature);
        return TypeConverter.toJsonHex(signatureAsString);
    }

    public String eth_sendTransaction(CallArguments args) throws Exception {
        return this.sendTransaction(args, this.getAccount(args.from));
    }

    private String sendTransaction(CallArguments args, Account account) throws Exception {
        String s = null;
        try {
            if (account == null)
                throw new JsonRpcInvalidParamException("From address private key could not be found in this node");

            String toAddress = args.to != null ? Hex.toHexString(stringHexToByteArray(args.to)) : null;

            BigInteger value = args.value != null ? TypeConverter.stringNumberAsBigInt(args.value) : BigInteger.ZERO;
            BigInteger gasPrice = args.gasPrice != null ? TypeConverter.stringNumberAsBigInt(args.gasPrice) : BigInteger.ZERO;
            BigInteger gasLimit = args.gas != null ? TypeConverter.stringNumberAsBigInt(args.gas) : BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);

            if (args.data != null && args.data.startsWith("0x"))
                args.data = args.data.substring(2);

            PendingState pendingState = worldManager.getPendingState();
            synchronized (pendingState) {
                BigInteger accountNonce = args.nonce != null ? TypeConverter.stringNumberAsBigInt(args.nonce) : (pendingState.getRepository().getNonce(account.getAddress()));
                Transaction tx = Transaction.create(toAddress, value, accountNonce, gasPrice, gasLimit, args.data);
                tx.sign(account.getEcKey().getPrivKeyBytes());
                eth.submitTransaction(tx);
                s = TypeConverter.toJsonHex(tx.getHash());
            }
            return s;
        } finally {
            if (logger.isDebugEnabled())
                logger.debug("eth_sendTransaction({}): {}", args, s);
        }
    }

    public String eth_sendRawTransaction(String rawData) throws Exception {
        String s = null;
        try {
            Transaction tx = new Transaction(stringHexToByteArray(rawData));

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

    public ProgramResult createCallTxAndExecute(CallArguments args, byte[] keyToSign) throws Exception {
        byte[] nonce = new byte[]{0};
        Transaction tx = Transaction.create(nonce, args);

        byte[] signingKey = (keyToSign == null) ? new byte[32] : keyToSign;

        tx.sign(signingKey);

        Block block = worldManager.getBlockchain().getBestBlock();

        return eth.callConstantCallTransaction(tx, block);
    }

    public String eth_call(CallArguments args, String bnOrId) throws Exception {
        if (!bnOrId.equals("latest"))
            throw new JsonRpcUnimplementedMethodException("Method only supports 'latest' as a parameter so far.");

        ProgramResult res = createCallTxAndExecute(args, this.getKeyToSign(args.from));
        return toJsonHex(res.getHReturn());
    }

    public String eth_estimateGas(CallArguments args) throws Exception {
        ProgramResult res = createCallTxAndExecute(args, this.getKeyToSign(args.from));
        return toJsonHex(res.getGasUsed());
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
        Map<String, CompilationResultDTO> compilationResultDTOMap = new HashedMap<>();
        try {
            SolidityCompiler.Result res = solidityCompiler.compile(contract.getBytes(StandardCharsets.UTF_8), true, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN);
            if (!res.errors.isEmpty()) {
                throw new RuntimeException("Compilation error: " + res.errors);
            }
            org.ethereum.solidity.compiler.CompilationResult result = org.ethereum.solidity.compiler.CompilationResult.parse(res.output);
            org.ethereum.solidity.compiler.CompilationResult.ContractMetadata contractMetadata = result.contracts.values().iterator().next();

            CompilationInfoDTO compilationInfo = new CompilationInfoDTO();
            compilationInfo.setSource(contract);
            compilationInfo.setLanguage("Solidity");
            compilationInfo.setLanguageVersion("0");
            compilationInfo.setCompilerVersion(result.version);
            compilationInfo.setAbiDefinition(new CallTransaction.Contract(contractMetadata.abi));

            CompilationResultDTO compilationResult = new CompilationResultDTO(contractMetadata, compilationInfo);
            String contractName = (String)result.contracts.keySet().toArray()[0];

            compilationResultDTOMap.put(contractName, compilationResult);

            return compilationResultDTOMap;
        } finally {
            if (logger.isDebugEnabled())
                logger.debug("eth_compileSolidity(" + contract + ")" + compilationResultDTOMap);
        }
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
            } else if (fr.address instanceof String[]) {
                List<byte[]> addr = new ArrayList<>();
                for (String s : ((String[]) fr.address)) {
                    addr.add(stringHexToByteArray(s));
                }
                logFilter.withContractAddress(addr.toArray(new byte[0][]));
            }

            if (fr.topics != null) {
                for (Object topic : fr.topics) {
                    if (topic == null) {
                        logFilter.withTopic(null);
                    } else if (topic instanceof String) {
                        logFilter.withTopic(new DataWord(stringHexToByteArray((String) topic)).getData());
                    } else if (topic instanceof String[]) {
                        List<byte[]> t = new ArrayList<>();
                        for (String s : ((String[]) topic)) {
                            t.add(new DataWord(stringHexToByteArray(s)).getData());
                        }
                        logFilter.withTopic(t.toArray(new byte[0][]));
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

        for (ModuleDescription module : RskSystemProperties.RSKCONFIG.getRpcModules())
            if (module.isEnabled())
                map.put(module.getName(), module.getVersion());

        return map;
    }

    public void db_putString() {
    }

    public void db_getString() {
    }

    public boolean eth_submitWork(String nonce, String header, String mince) {
        throw new UnsupportedOperationException("Not implemeted yet");
    }

    public boolean eth_submitHashrate(String hashrate, String id) {
        throw new UnsupportedOperationException("Not implemeted yet");
    }

    public void db_putHex() {
    }

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
        String s = null;
        try {
            byte[] address = this.wallet.addAccountWithSeed(seed);
            return s = toJsonHex(address);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("personal_newAccountWithSeed(*****): " + s);
            }
        }
    }

    @Override
    public String personal_newAccount(String passphrase) {
        String s = null;
        try {
            byte[] address = this.wallet.addAccount(passphrase);
            return s = toJsonHex(address);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("personal_newAccount(*****): " + s);
            }
        }
    }

    public String eth_addAccount(String privKey) {
        String s = null;
        try {
            byte[] address = this.wallet.addAccountWithPrivateKey(Hex.decode(privKey));
            return s = toJsonHex(address);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_addAccount(*****): " + s);
            }
        }
    }

    @Override
    public String personal_importRawKey(String key, String passphrase) {
        String s = null;
        try {
            byte[] address = this.wallet.addAccountWithPrivateKey(Hex.decode(key), passphrase);
            return s = toJsonHex(address);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("personal_importRawKey(*****): " + s);
            }
        }
    }

    @Override
    public String personal_dumpRawKey(String address) throws Exception {
        String s = null;
        try {
            Account account = getAccount(JSonHexToHex(address));

            if (account == null)
                throw new Exception("Address private key is locked or could not be found in this node");

            return s = toJsonHex(Hex.toHexString(account.getEcKey().getPrivKeyBytes()));
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("personal_dumpRawKey(*****): " + s);
            }
        }
    }

    @Override
    public String[] personal_listAccounts() {
        return this.listAccounts(this.wallet.getAccountAddresses());
    }

    private String[] listAccounts(List<byte[]> addresses) {
        String[] ret = new String[addresses.size()];
        try {
            int i = 0;
            for (byte[] address : addresses) {
                ret[i++] = toJsonHex(address);
            }
            return ret;
        } finally {
            if (logger.isDebugEnabled())
                logger.debug("personal_listAccounts(): {}", Arrays.toString(ret));
        }
    }

    @Override
    public String personal_sendTransaction(CallArguments args, String passphrase) throws Exception {
        String s = null;
        try {
            return s = sendPersonalTransacction(args, getAccount(args.from, passphrase));
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_sendTransaction(" + args + "): " + s);
            }
        }
    }

    private String sendPersonalTransacction(CallArguments args, Account account) throws Exception {
        if (account == null)
            throw new Exception("From address private key could not be found in this node");

        String toAddress = args.to != null ? Hex.toHexString(stringHexToByteArray(args.to)) : null;

        BigInteger accountNonce = args.nonce != null ? TypeConverter.stringNumberAsBigInt(args.nonce) : (worldManager.getPendingState().getRepository().getNonce(account.getAddress()));
        BigInteger value = args.value != null ? TypeConverter.stringNumberAsBigInt(args.value) : BigInteger.ZERO;
        BigInteger gasPrice = args.gasPrice != null ? TypeConverter.stringNumberAsBigInt(args.gasPrice) : BigInteger.ZERO;
        BigInteger gasLimit = args.gas != null ? TypeConverter.stringNumberAsBigInt(args.gas) : BigInteger.valueOf(GasCost.TRANSACTION);

        if (args.data != null && args.data.startsWith("0x"))
            args.data = args.data.substring(2);

        Transaction tx = Transaction.create(toAddress, value, accountNonce, gasPrice, gasLimit, args.data);

        tx.sign(account.getEcKey().getPrivKeyBytes());

        eth.submitTransaction(tx);

        return TypeConverter.toJsonHex(tx.getHash());
    }

    @Override
    public boolean personal_unlockAccount(String address, String passphrase, String duration) {
        long dur = (long)1000 * 60 * 30;
        if (duration != null && duration.length() > 0) {
            try {
                dur = JSonHexToLong(duration);
            } catch (Exception e) {
                throw new JsonRpcInvalidParamException("Can't parse duration param", e);
            }
        }

        return this.wallet.unlockAccount(stringHexToByteArray(address), passphrase, dur);
    }

    public Map<String, Object> eth_bridgeState() throws Exception {
        CallArguments arguments = new CallArguments();
        arguments.to = "0x" + PrecompiledContracts.BRIDGE_ADDR;
        arguments.data = Hex.toHexString(Bridge.GET_STATE_FOR_DEBUGGING.encodeSignature());
        arguments.gasPrice = "0x0";
        arguments.value = "0x0";
        arguments.gas = "0xf4240";
        ProgramResult res = createCallTxAndExecute(arguments, new byte[32]);
        BridgeState state = BridgeStateReader.readSate(TypeConverter.removeZeroX(toJsonHex(res.getHReturn())));
        return state.stateToMap();
    }

    @Override
    public boolean personal_lockAccount(String address) {
        return this.wallet.lockAccount(stringHexToByteArray(address));
    }

    private Account getDefaultAccount() {
        List<byte[]> accountAddresses = this.wallet.getAccountAddresses();

        if (!CollectionUtils.isEmpty(accountAddresses)) {
            return this.wallet.getAccount(accountAddresses.get(0));
        }

        return null;
    }

    @VisibleForTesting
    public Account getAccount(String address) {
        return this.wallet.getAccount(stringHexToByteArray(address));
    }

    @VisibleForTesting
    public Account getAccount(String address, String passphrase) {
        return this.wallet.getAccount(stringHexToByteArray(address), passphrase);
    }

    private byte[] getKeyToSign(String address) {
        byte[] privateKey = new byte[32];

        Account account;
        account = (address != null) ? this.wallet.getAccount(stringHexToByteArray(address)) : this.getDefaultAccount();

        if (account != null)
            privateKey = account.getEcKey().getPrivKeyBytes();

        return privateKey;
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
        minerManager.mineBlock(worldManager.getBlockchain(), worldManager.getMinerClient(), worldManager.getMinerServer());
        if (logger.isDebugEnabled())
            logger.debug("evm_mine()");
    }

    @Override
    public String evm_increaseTime(String seconds) {
        try {
            long nseconds = stringHexToBigInteger(seconds).longValue();
            String result = toJsonHex(worldManager.getMinerServer().increaseTime(nseconds));
            if (logger.isDebugEnabled())
                logger.debug("evm_increaseTime({}): {}", seconds, result);
            return result;
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw new JsonRpcInvalidParamException("invalid number of seconds " + seconds, e);
        }
    }
}
