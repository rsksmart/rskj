package co.rsk.trie;

import org.ethereum.core.AccountState;

/**
 * Created by SerAdmin on 11/12/2018.
 */
public interface TrieIteratorListener {
    // 0 = no error
    // !=0 = abourt
    public int process(byte[] hashedKey, byte[] value);
}
