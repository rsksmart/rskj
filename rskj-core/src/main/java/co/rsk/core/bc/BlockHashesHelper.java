package co.rsk.core.bc;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.util.RLP;

import java.util.List;

public class BlockHashesHelper {

    public static boolean isRskipUnitrie(long number) {
        return SystemProperties.DONOTUSE_blockchainConfig.getConfigForBlock(number).isRskipUnitrie();
    }

    public static byte[] calculateReceiptsTrieRoot(List<TransactionReceipt> receipts, boolean isRskipUnitrieEnabled) {
        Trie trie = calculateReceiptsTrieRootFor(receipts);
        if (isRskipUnitrieEnabled) {
            return trie.getHash().getBytes();
        }

        return trie.getHashOrchid().getBytes();
    }

    private static Trie calculateReceiptsTrieRootFor(List<TransactionReceipt> receipts) {
        Trie receiptsTrie = new TrieImpl(false);
        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());
        }

        return receiptsTrie;
    }

    private static Trie getTxTrieFor(List<Transaction> transactions) {
        Trie txsState = new TrieImpl(false);
        if (transactions == null) {
            return txsState;
        }

        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            txsState = txsState.put(RLP.encodeInt(i), transaction.getEncoded());
        }

        return txsState;
    }

    public static byte[] getTxTrieRoot(List<Transaction> transactions, boolean isRskipUnitrieEnabled) {
        Trie trie = getTxTrieFor(transactions);
        if (isRskipUnitrieEnabled) {
            return trie.getHash().getBytes();
        }

        return trie.getHashOrchid().getBytes();
    }
}
