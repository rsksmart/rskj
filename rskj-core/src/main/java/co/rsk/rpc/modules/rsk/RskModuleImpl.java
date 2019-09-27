package co.rsk.rpc.modules.rsk;

import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.exception.JsonRpcUnimplementedMethodException;
import org.ethereum.util.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.ethereum.rpc.TypeConverter.stringHexToBigInteger;
import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class RskModuleImpl implements RskModule {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private final Blockchain blockchain;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    public RskModuleImpl(Blockchain blockchain, BlockStore blockStore, ReceiptStore receiptStore) {
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
    }

    @Override
    public String getRawTransactionReceiptByHash(String transactionHash) {
        String s = null;
        try {
            byte[] hash = stringHexToByteArray(transactionHash);
            TransactionInfo txInfo = receiptStore.getInMainChain(hash, blockStore);

            if (txInfo == null) {
                logger.trace("No transaction info for {}", transactionHash);
                return null;
            }
            return TypeConverter.toUnformattedJsonHex(txInfo.getReceipt().getEncoded());
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
            Keccak256 txHash = new Keccak256(stringHexToByteArray(transactionHash));
            Keccak256 bhash = new Keccak256(stringHexToByteArray(blockHash));
            Block block = this.blockchain.getBlockByHash(bhash.getBytes());
            List<Transaction> transactions = block.getTransactionsList();
            List<TransactionReceipt> receipts = new ArrayList<>();

            int ntxs = transactions.size();
            int ntx = -1;

            for (int k = 0; k < ntxs; k++) {
                Transaction transaction = transactions.get(k);
                Keccak256 txh = transaction.getHash();

                Optional<TransactionInfo> txinfoOpt = this.receiptStore.get(txh, bhash);
                if (!txinfoOpt.isPresent()) {
                    logger.error("Missing receipt for transaction {} in block {}", txh, bhash);
                    continue;
                }

                TransactionInfo txinfo = txinfoOpt.get();
                receipts.add(txinfo.getReceipt());

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
                encodedNodes[k] = TypeConverter.toUnformattedJsonHex(nodes.get(k).toMessage());
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
            byte[] bhash = stringHexToByteArray(blockHash);
            Block b = this.blockchain.getBlockByHash(bhash);
            return s = (b == null ? null : TypeConverter.toUnformattedJsonHex(b.getHeader().getEncoded()));
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
            Block b = getByJsonBlockId(bnOrId);
            return s = (b == null ? null : TypeConverter.toUnformattedJsonHex(b.getHeader().getEncoded()));
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("rsk_getRawBlockHeaderByNumber({}): {}", bnOrId, s);
            }
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
                throw invalidParamError("invalid blocknumber " + id);
            }
        }
    }

}
