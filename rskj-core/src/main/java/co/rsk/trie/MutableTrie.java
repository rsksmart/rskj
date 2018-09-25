package co.rsk.trie;

import co.rsk.crypto.Keccak256;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Set;

/**
 * Created by SerAdmin on 9/24/2018.
 * Every operation of a MutableTrie mutates the parent trie top node and therefore its stateRoot.
 */

public interface MutableTrie {
        Keccak256 getHash();

        byte[] get(byte[] key);

        byte[] get(String key);

        void put(byte[] key, byte[] value);

        void put(String key, byte[] value);

        void deleteRecursive(byte[] key);

        void delete(byte[] key);

        void delete(String key);

        byte[] toMessage();

        void save();

        void commit();
        void rollback();

        boolean isCache();
        boolean isSecure();

        int trieSize();

        Set<ByteArrayWrapper> collectKeys(int size);
        Set<ByteArrayWrapper> collectKeysFrom(byte[] key);

        MutableTrie getSnapshotTo(Keccak256 hash);
        void setSnapshotTo(Keccak256 hash);

        byte[] serialize();

        boolean hasStore();

        Trie getTrie();

        // This allows to root the trie at a specific node eliminating the prefix.
        // I would be much better if the path were a suffix reather than a prefix.
        // because the prefix in a node can change depending on the node sibling,
        // which prevents decoupling of tree branches.
        // void setIgnoreSharedPath(boolean value);
}
