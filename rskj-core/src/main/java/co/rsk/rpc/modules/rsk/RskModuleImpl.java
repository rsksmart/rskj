package co.rsk.rpc.modules.rsk;

import co.rsk.ExecutionBenchmark;
import co.rsk.FhBenchmarks;
import co.rsk.Flusher;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.net.TransactionGateway;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.trie.Trie;
import co.rsk.util.HexUtils;
import co.rsk.util.NodeStopper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.FhContext;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.util.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RskModuleImpl implements RskModule {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private final Blockchain blockchain;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final Web3InformationRetriever web3InformationRetriever;
    private final Flusher flusher;

    private final NodeStopper nodeStopper;
    private final TransactionGateway transactionGateway;

    public RskModuleImpl(Blockchain blockchain,
                         BlockStore blockStore,
                         ReceiptStore receiptStore,
                         Web3InformationRetriever web3InformationRetriever,
                         Flusher flusher,
                         NodeStopper nodeStopper,
                         TransactionGateway transactionGateway) {
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.web3InformationRetriever = web3InformationRetriever;
        this.flusher = flusher;
        this.nodeStopper = nodeStopper;
        this.transactionGateway = transactionGateway;
    }

    public RskModuleImpl(Blockchain blockchain,
                         BlockStore blockStore,
                         ReceiptStore receiptStore,
                         Web3InformationRetriever web3InformationRetriever,
                         Flusher flusher) {
        this(blockchain, blockStore, receiptStore, web3InformationRetriever, flusher, System::exit, null);
    }

    public RskModuleImpl(Blockchain blockchain,
                         BlockStore blockStore,
                         ReceiptStore receiptStore,
                         Web3InformationRetriever web3InformationRetriever,
                         Flusher flusher,
                         TransactionGateway transactionGateway) {
        this(blockchain, blockStore, receiptStore, web3InformationRetriever, flusher, System::exit, transactionGateway);
    }

    @Override
    public void flush() {
        flusher.forceFlush();
    }

    @Override
    public String getBenchmarks() {
        List<ExecutionBenchmark> benchmarksAdd =  FhContext.getInstance().getBenchmarksAdd();
        List<ExecutionBenchmark> benchmarksTxExecution =  FhContext.getInstance().getBenchmarksTxExecution();
        List<ExecutionBenchmark> benchmarksTranscipher = FhContext.getInstance().getBenchmarksTranscipher();

        FhBenchmarks benchmarks = new FhBenchmarks(benchmarksAdd, benchmarksTxExecution, benchmarksTranscipher);

        String s;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper = objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            s = objectMapper.writeValueAsString(benchmarks);
        } catch (JsonProcessingException e) {
            System.out.println("error getBenchmarks");
            throw new RuntimeException(e);
        }

//        List<String> transcipherBenchmarks =  FhContext.getInstance()
//                .getAddOperationBenchmarks()
//                .entrySet()
//                .stream()
//                .map(e -> e)
//                .collect(Collectors.toList());

        if (logger.isDebugEnabled()) {
            logger.debug("rsk_getBenchmarks()");
        }

        return s;
    }

    private ExecutionBenchmark toRlpString(Map.Entry<String, Long> entry) {
        return new ExecutionBenchmark(entry.getKey(), entry.getValue());
    }

    @Override
    public void shutdown() {
        nodeStopper.stop(0);
    }

    @Override
    public String getRawTransactionReceiptByHash(String transactionHash) {
        String s = null;
        try {
            byte[] hash = HexUtils.stringHexToByteArray(transactionHash);
            TransactionInfo txInfo = receiptStore.getInMainChain(hash, blockStore).orElse(null);

            if (txInfo == null) {
                logger.trace("No transaction info for {}", transactionHash);
                return null;
            }
            return HexUtils.toUnformattedJsonHex(txInfo.getReceipt().getEncoded());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("rsk_getRawTransactionReceiptByHash({}): {}", transactionHash, s);
            }
        }
    }

    @Override
    public String[] getTransactionReceiptNodesByHash(String blockHash, String transactionHash) {
        String[] encodedNodes = null;

        try {
            Keccak256 txHash = new Keccak256(HexUtils.stringHexToByteArray(transactionHash));
            Keccak256 bhash = new Keccak256(HexUtils.stringHexToByteArray(blockHash));
            Block block = this.blockchain.getBlockByHash(bhash.getBytes());
            List<Transaction> transactions = block.getTransactionsList();
            List<TransactionReceipt> receipts = new ArrayList<>();

            int ntxs = transactions.size();
            int ntx = -1;

            for (int k = 0; k < ntxs; k++) {
                Transaction transaction = transactions.get(k);
                Keccak256 txh = transaction.getHash();

                Optional<TransactionInfo> txInfoOpt = this.receiptStore.get(txh.getBytes(), bhash.getBytes());
                if (!txInfoOpt.isPresent()) {
                    logger.error("Missing receipt for transaction {} in block {}", txh, bhash);
                    continue;
                }

                TransactionInfo txInfo = txInfoOpt.get();
                receipts.add(txInfo.getReceipt());

                if (txh.equals(txHash)) {
                    ntx = k;
                }
            }

            if (ntx == -1) {
                return null;
            }
            Trie trie = BlockHashesHelper.calculateReceiptsTrieRootFor(receipts);

            List<Trie> nodes = trie.getNodes(RLP.encodeInt(ntx));
            encodedNodes = new String[nodes.size()];

            for (int k = 0; k < encodedNodes.length; k++) {
                encodedNodes[k] = HexUtils.toUnformattedJsonHex(nodes.get(k).toMessage());
            }

            return encodedNodes;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("rsk_getTransactionReceiptNodesByHash({}): {}", blockHash, Arrays.toString(encodedNodes));
            }
        }
    }


    @Override
    public String getRawBlockHeaderByHash(String blockHash) {
        String s = null;
        try {
            byte[] bhash = HexUtils.stringHexToByteArray(blockHash);
            Block b = this.blockchain.getBlockByHash(bhash);
            return s = (b == null ? null : HexUtils.toUnformattedJsonHex(b.getHeader().getEncoded()));
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("rsk_getRawBlockHeaderByHash({}): {}", blockHash, s);
            }
        }
    }

    @Override
    public String getRawBlockHeaderByNumber(String bnOrId) {
        String s = null;
        try {
            return s = web3InformationRetriever.getBlock(bnOrId)
                    .map(b -> HexUtils.toUnformattedJsonHex(b.getHeader().getEncoded()))
                    .orElse(null);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("rsk_getRawBlockHeaderByNumber({}): {}", bnOrId, s);
            }
        }
    }


}
