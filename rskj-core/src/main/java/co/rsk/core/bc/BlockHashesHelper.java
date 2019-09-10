package co.rsk.core.bc;

import co.rsk.trie.Trie;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.util.RLP;

import java.util.List;

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
