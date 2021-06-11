package co.rsk.core.bc;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.util.RLP;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockHashesHelper {

    private BlockHashesHelper() {
        // helper class
    }

    public static byte[] calculateReceiptsTrieRoot(List<TransactionReceipt> receipts, boolean isRskip126Enabled) {
        Trie trie = calculateReceiptsTrieRootFor(receipts);
        if (isRskip126Enabled) {
            return trie.getHash().getBytes();
        }

        return trie.getHashOrchid(false).getBytes();
    }

    public static Trie calculateReceiptsTrieRootFor(List<TransactionReceipt> receipts) {
        Trie receiptsTrie = new Trie();
        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());
        }

        return receiptsTrie;
    }

    public static List<Trie> calculateReceiptsTrieRootFor(Block block, ReceiptStore receiptStore, Keccak256 txHash)
            throws BlockHashesHelperException {
        Keccak256 bhash = block.getHash();

        List<Transaction> transactions = block.getTransactionsList();
        List<TransactionReceipt> receipts = new ArrayList<>();

        int ntxs = transactions.size();
        int ntx = -1;

        for (int k = 0; k < ntxs; k++) {
            Transaction transaction = transactions.get(k);
            Keccak256 txh = transaction.getHash();

            Optional<TransactionInfo> txInfoOpt = receiptStore.get(txh.getBytes(), bhash.getBytes());
            if (!txInfoOpt.isPresent()) {
                throw new BlockHashesHelperException(String.format("Missing receipt for transaction %s in block %s", txh, bhash));
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
        Trie trie = calculateReceiptsTrieRootFor(receipts);
        List<Trie> nodes = trie.getNodes(RLP.encodeInt(ntx));

        return nodes;
    }

    public static byte[] getTxTrieRoot(List<Transaction> transactions, boolean isRskip126Enabled) {
        Trie trie = getTxTrieRootFor(transactions);
        if (isRskip126Enabled) {
            return trie.getHash().getBytes();
        }

        return trie.getHashOrchid(false).getBytes();
    }

    private static Trie getTxTrieRootFor(List<Transaction> transactions) {
        Trie txsState = new Trie();
        if (transactions == null) {
            return txsState;
        }

        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            txsState = txsState.put(RLP.encodeInt(i), transaction.getEncoded());
        }

        return txsState;
    }
}
