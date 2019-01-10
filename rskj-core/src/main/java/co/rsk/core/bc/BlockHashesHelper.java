package co.rsk.core.bc;

import co.rsk.trie.OldTrieImpl;
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

    public static byte[] calculateReceiptsTrieRoot(List<TransactionReceipt> receipts, boolean isRskipUnitrieEnabled) {
        if (isRskipUnitrieEnabled) {
            return calculateReceiptsTrieRootNew(receipts);
        }

        return calculateReceiptsTrieRootOld(receipts);
    }

    public static byte[] calculateReceiptsTrieRootNew(List<TransactionReceipt> receipts) {
        return calculateReceiptsTrieRootFor(receipts, new TrieImpl(false));
    }

    public static byte[] calculateReceiptsTrieRootOld(List<TransactionReceipt> receipts) {
        return (calculateReceiptsTrieRootFor(receipts, new OldTrieImpl(false)));
    }

    public static byte[] calculateReceiptsTrieRootFor(List<TransactionReceipt> receipts, Trie receiptsTrie) {
        if (receipts.isEmpty()) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        for (int i = 0; i < receipts.size(); i++) {
            receiptsTrie = receiptsTrie.put(RLP.encodeInt(i), receipts.get(i).getEncoded());
        }

        return receiptsTrie.getHash().getBytes();
    }

    public static Trie getTxTrieOld(List<Transaction> transactions){
        return getTxTrieFor(transactions, new OldTrieImpl(false));
    }

    public static Trie getTxTrieNew(List<Transaction> transactions){
        return getTxTrieFor(transactions, new TrieImpl(false));
    }

    public static Trie getTxTrieFor(List<Transaction> transactions, Trie txsState){
        if (transactions == null) {
            return txsState;
        }

        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            txsState = txsState.put(RLP.encodeInt(i), transaction.getEncoded());
        }

        return txsState;
    }

    public static byte[] getTxTrieRoot(List<Transaction> transactions, boolean isRskipUnitrieEnabled){
        Trie trie;
        if (isRskipUnitrieEnabled) {
            trie = getTxTrieNew(transactions);
        } else {
            trie = getTxTrieOld(transactions);
        }

        return trie.getHash().getBytes();
    }
}
