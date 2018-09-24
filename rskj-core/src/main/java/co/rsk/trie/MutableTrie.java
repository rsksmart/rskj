package co.rsk.trie;

import co.rsk.crypto.Keccak256;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Set;

/**
 * Created by SerAdmin on 9/24/2018.
 */
public interface MutableTrie {
        Keccak256 getHash();

        byte[] get(byte[] key);

        byte[] get(String key);

        void put(byte[] key, byte[] value);

        void put(String key, byte[] value);

        void delete(byte[] key);

        void delete(String key);

        byte[] toMessage();

        void save();

        void commit();
        void rollback();

        boolean isCache();

        int trieSize();

        Set<ByteArrayWrapper> collectKeys(int size);

        MutableTrie getSnapshotTo(Keccak256 hash);

        byte[] serialize();

        boolean hasStore();


}
