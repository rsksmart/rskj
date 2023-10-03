package co.rsk.rpc.modules.rsk;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import co.rsk.core.bc.BlockChainFlusher;
import co.rsk.net.TransactionGateway;
import co.rsk.pcc.VotingMocks;
import co.rsk.util.NodeStopper;
import co.rsk.Flusher;
import co.rsk.util.RLPException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.core.*;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.*;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.TransactionArgumentsUtil;
import org.ethereum.vm.bfv.TranscipherCase;
import org.rsksmart.BFV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.trie.Trie;
import co.rsk.util.HexUtils;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

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


    // todo(fedejinich) duplicated method,remove it
    @Override
    public String sendEncryptedTransaction(String rawData) {
        try {
            EncryptedTransaction encryptedTransaction = new EncryptedTransaction(HexUtils.stringHexToByteArray(rawData));
            BFV bfv = new BFV();
            Transaction tx = encryptedTransaction.getTransaction();

            // transcipher encrypted params
            VotingMocks vm = new ObjectMapper().readValue(new File(
                                "/Users/fedejinich/Projects/rskj/rskj-core/src/test/java/org/ethereum/vm/bfv/votes.json"),
                       VotingMocks.class);
            byte[] data = encryptedTransaction.getEncryptedParams();
            byte[] pastaSK = vm.getPastaSK();
            byte[] rks = vm.getRk();
            byte[] bfvSK = vm.getBfvSK();
            byte[] fhData = bfv.transcipher(data, data.length, pastaSK, pastaSK.length, rks, rks.length,
                    bfvSK, bfvSK.length);
            byte[] hash = Keccak256Helper.keccak256(fhData);
            FhStore.getInstance().put(hash, fhData); // store encrypted params, so they can be accessed within tx execution

            tx.getSender(); // todo(fedejinich) signature cache is bringing problems, i had to do this ugly thing :(, research about it!
            tx = addEncryptedParams(tx, hash); // add encrypted params to tx.data

            // for the future, add logic to set keys to fetch encrypted data from storage

            if (null == tx.getGasLimit() || null == tx.getGasPrice() || null == tx.getValue()) {
                throw invalidParamError("Missing parameter, gasPrice, gas or value");
            }

            // todo(fedejinich) i think i don't need this
//            if (!tx.acceptTransactionSignature(constants.getChainId())) {
//                throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_INVALID_CHAIN_ID + tx.getChainId());
//            }

            TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx);
            if (!result.transactionsWereAdded()) {
                throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
            }

            return tx.getHash().toJsonString();
        } catch (RLPException e) {
            throw invalidParamError("Invalid input: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // todo(fedejinich) support this
//            if (LOGGER.isDebugEnabled()) {
//                LOGGER.debug("rsk_sendEncryptedTransaction({}): {}", rawData, s);
//            }
        }
    }

    private Transaction addEncryptedParams(Transaction tx, byte[] encryptedParamsHash) {
        byte[] data = tx.getData();

        ByteBuffer buff = ByteBuffer.allocate(data.length + 32 + 32);
        buff.put(data);
        buff.put(encryptedParamsHash);

        tx.setData(buff.array());

        return tx;
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
