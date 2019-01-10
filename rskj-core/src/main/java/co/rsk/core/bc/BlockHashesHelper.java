package co.rsk.core.bc;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;

import java.util.List;

public class BlockHashesHelper {

    public static boolean isRskipUnitrie(long number) {
        return SystemProperties.DONOTUSE_blockchainConfig.getConfigForBlock(number).isRskipUnitrie();
    }

    public static byte[] calculateReceiptsTrieRoot(List<TransactionReceipt> receipts) {
        Trie receiptsTrie = new TrieImpl();
        if (receipts.isEmpty()) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());
        }

        return receiptsTrie.getHash().getBytes();
    }

    public static byte[] getTxTrieRoot(List<Transaction> transactions) {
        Trie txsState = new TrieImpl();
        if (transactions == null) {
            return txsState.getHash().getBytes();
        }

        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            txsState = txsState.put(RLP.encodeInt(i), transaction.getEncoded());
        }

        return txsState.getHash().getBytes();
    }
}
