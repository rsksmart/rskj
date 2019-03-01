package co.rsk.trie;

import co.rsk.crypto.Keccak256;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by SerAdmin on 10/23/2018.
 */
public class TrieConverter {
    private final TrieStoreImpl store;
    private final Map<Keccak256, Keccak256> cacheHashes;
    private final Map<Keccak256, byte[]> cacheStorage;
//    private final List<String> dump = new ArrayList<>();

    public TrieConverter() {
        store = new TrieStoreImpl(new HashMapDB());
        cacheHashes = new HashMap<>();
        cacheStorage = new HashMap<>();
    }

    private static byte[] concat(byte[] first, byte b) {
        return concat(first, new byte[]{b});
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    // This method converts and new trie into an old trie hash, but it works for tx tries
    // or receipt tries. It dosn't work for the account trie because it doesn't translate
    // new AccountStates into old AccountStates. For that, use computeOldAccountTrieRoot()

    public byte[] getOrchidAccountTrieRoot(TrieImpl src) {
//        dump.clear();
        byte[] oldAccountTrieRoot = getOrchidAccountTrieRoot(new byte[]{}, src, true);
//        try {
//            FileWriter writer = new FileWriter("output.txt");
//            for(String str: dump) {
//                writer.write(str);
//            }
//            writer.close();
//        } catch (Exception e) {
//            System.out.println("SALIO MAL");
//        }
        return oldAccountTrieRoot;
    }

    private byte[] getOrchidAccountTrieRoot(byte[] key, TrieImpl src, boolean removeFirst8bits) {
        if (src == null) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        Keccak256 cacheHash = cacheHashes.get(src.getHash());
        if (cacheHash != null) {
            return cacheHash.getBytes();
        }

        // shared Path
        byte[] encodedSharedPath = src.getEncodedSharedPath();
        int sharedPathLength = src.getSharedPathLength();
        if (encodedSharedPath != null) {
            //if (this.sharedPathLength+ key.length() > collectKeyLen)
            //    return;

            byte[] sharedPath = PathEncoder.decode(encodedSharedPath, sharedPathLength);
            key = concat(key, sharedPath);
        }
        if (removeFirst8bits) {
            if (sharedPathLength < 8) {
                throw new IllegalStateException("Unable to remove first 8-bits if path length is less than 8");
            }
            sharedPathLength -= 8;
            encodedSharedPath = Arrays.copyOfRange(encodedSharedPath, 1, encodedSharedPath.length);
        }
        TrieImpl child0 = (TrieImpl) src.retrieveNode(0);
        byte[] child0Hash = null;
        TrieImpl child1 = (TrieImpl) src.retrieveNode(1);
        byte[] child1Hash = null;

        if (key.length == 8 * 32 + 8) {
            // We've reached the Account level. From now on everything will be different.
            AccountState astate = new AccountState(src.getValue());
            OldAccountState oldState = new OldAccountState(astate.getNonce(), astate.getBalance());
            // child1 (0x80) will be the code
            if (child1 != null) {
                oldState.setCodeHash(child1.getValueHash());
            }
            // the child0 side will be the storage. The first child corresponds to the
            // 8-bit zero prefix. 1 bit is consumed by the branch. 7-bits left. We check that
            if (child0 != null) {
                if (child0.getSharedPathLength() != 7) {
                    throw new IllegalStateException("First child must be 7-bits length");
                }
                // We'll create an ad-hoc trie for storage cells, the first
                // child0's child is the root of this try. What if it has two children?
                // This can happen if there are two hashed storage keys, one begining with
                // 0 and another with 1.
                byte[] stateRoot = getOrchidStateRoot(child0);
                oldState.setStateRoot(stateRoot);
            }

            byte[] avalue = oldState.getEncoded();
            TrieImpl newNode = new TrieImpl(
                    encodedSharedPath, sharedPathLength,
                    avalue, null, null, store,
                    avalue.length, null, src.isSecure()
            );
//            dump.add(key.toString() + "\n");
//            dump.add(newNode.toString());
            cacheHashes.put(src.getHash(), newNode.getHash());
            return newNode.getHash().getBytes();
        }

        if (child0 != null) {
            child0Hash = getOrchidAccountTrieRoot(concat(key, (byte) 0), child0, false);
        }

        if (child1 != null) {
            child1Hash = getOrchidAccountTrieRoot(concat(key, (byte) 1), child1, false);
        }

        Keccak256[] hashes = new Keccak256[] {
                child0Hash == null ? null : new Keccak256(child0Hash),
                child1Hash == null ? null : new Keccak256(child1Hash)
        };

        TrieImpl newNode = new TrieImpl(
                encodedSharedPath, sharedPathLength,
                src.getValue(), null, hashes, store, src.valueLength,
                src.getValueHash(), src.isSecure()
        );
//        dump.add(key.toString());
//        dump.add(newNode.toString());

        cacheHashes.put(src.getHash(), newNode.getHash());
        return newNode.getHash().getBytes();
    }

    private byte[] getOrchidStateRoot(TrieImpl unitrieStorageRoot) {
        return getOrchidStateRoot(unitrieStorageRoot, true, false, (byte) 0);
    }

    private byte[] getOrchidStateRoot(
            TrieImpl unitrieStorageRoot,
            boolean removeFirstNodePrefix,
            boolean onlyChild,
            byte ancestor) {

        if (unitrieStorageRoot == null) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        byte[] storageNodeHash = cacheStorage.get(unitrieStorageRoot.getHash());
        if (storageNodeHash != null && !onlyChild  && !removeFirstNodePrefix) {
            return storageNodeHash;
        }

        // shared Path
        byte[] encodedSharedPath = unitrieStorageRoot.getEncodedSharedPath();
        int sharedPathLength = unitrieStorageRoot.getSharedPathLength();

        byte[] value = unitrieStorageRoot.getValue();
        int valueLength = unitrieStorageRoot.valueLength;
        byte[] valueHash = unitrieStorageRoot.getValueHash();

        TrieImpl child0 = (TrieImpl) unitrieStorageRoot.retrieveNode(0);
        TrieImpl child1 = (TrieImpl) unitrieStorageRoot.retrieveNode(1);

        byte[] child0Hash = null;
        if (child0 != null) {
            child0Hash = getOrchidStateRoot(child0, false, removeFirstNodePrefix && child1 == null, (byte) 0);
        }

        byte[] child1Hash = null;
        if (child1 != null) {
            child1Hash = getOrchidStateRoot(child1, false, removeFirstNodePrefix && child0 == null, (byte) 1);
        }

        Keccak256[] hashes = new Keccak256[] {
                child0Hash == null ? null : new Keccak256(child0Hash),
                child1Hash == null ? null : new Keccak256(child1Hash)
        };

        if (removeFirstNodePrefix) {
            encodedSharedPath = null;
            sharedPathLength = 0;
            value = null; // also remove value
            valueLength = 0;
            valueHash = null;
            if (child0 != null && child1 == null) {
                return child0Hash;
            }
            if (child0 == null && child1 != null) {
                return child1Hash;
            }
        }

        if (onlyChild) {
            byte[] expandedKey = encodedSharedPath != null ? PathEncoder.decode(encodedSharedPath, sharedPathLength) : new byte[0];
            byte[] keyCopy = new byte[sharedPathLength + 1];
            System.arraycopy(expandedKey, 0, keyCopy, 1, sharedPathLength);
            keyCopy[0] = ancestor;
            encodedSharedPath = PathEncoder.encode(keyCopy);
            sharedPathLength++;
        }

        TrieImpl newNode = new TrieImpl(
                encodedSharedPath, sharedPathLength,
                value, null, hashes, store,
                valueLength, valueHash, unitrieStorageRoot.isSecure()
        );
        if (!onlyChild) {
            cacheStorage.put(unitrieStorageRoot.getHash(), newNode.getHash().getBytes());
        }
        return newNode.getHash().getBytes();
    }
}