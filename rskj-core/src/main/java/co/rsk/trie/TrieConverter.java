package co.rsk.trie;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import java.util.Arrays;
import java.util.Map;

public class TrieConverter {

    private static final byte LEFT_CHILD_IMPLICIT_KEY = (byte) 0x00;
    private static final byte RIGHT_CHILD_IMPLICIT_KEY = (byte) 0x01;
    private static final byte[] REMASC_SENDER_UNITRIE_EXPANDED_KEY = ByteUtil.merge(
            TrieKeyMapper.DOMAIN_PREFIX,
            Arrays.copyOfRange(Keccak256Helper.keccak256(RemascTransaction.REMASC_ADDRESS.getBytes()), 0, TrieKeyMapper.SECURE_KEY_SIZE),
            RemascTransaction.REMASC_ADDRESS.getBytes()
    );
    private static final byte[] REMASC_SENDER_UNITRIE_KEY = PathEncoder.decode(
            REMASC_SENDER_UNITRIE_EXPANDED_KEY, REMASC_SENDER_UNITRIE_EXPANDED_KEY.length * Byte.SIZE
    );

    private final Map<Keccak256, Keccak256> cacheHashes;
    private final Map<Keccak256, byte[]> cacheStorage;
//    private final List<String> dump = new ArrayList<>();

    public TrieConverter() {
        cacheHashes = new MaxSizeHashMap<>(500_000, true);
        cacheStorage = new MaxSizeHashMap<>(600_000, true);
    }

    public byte[] getOrchidAccountTrieRoot(Trie src) {
//        dump.clear();
        byte[] oldAccountTrieRoot = getOrchidAccountTrieRoot(src.getSharedPath(), src, true);
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

    private byte[] getOrchidAccountTrieRoot(TrieKeySlice key, Trie src, boolean removeFirst8bits) {
        if (src == null) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        Keccak256 cacheHash = cacheHashes.get(src.getHash());
        if (cacheHash != null) {
            return cacheHash.getBytes();
        }

        TrieKeySlice sharedPath = src.getSharedPath();
        if (removeFirst8bits) {
            if (sharedPath.length() < 8) {
                throw new IllegalStateException("Unable to remove first 8-bits if path length is less than 8");
            }

            sharedPath = sharedPath.slice(8, sharedPath.length());
        }

        Trie child0 = src.getNodeReference(LEFT_CHILD_IMPLICIT_KEY).getNode().orElse(null);
        byte[] child0Hash = null;
        Trie child1 = src.getNodeReference(RIGHT_CHILD_IMPLICIT_KEY).getNode().orElse(null);
        byte[] child1Hash = null;

        boolean isRemascAccount = key.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length) * Byte.SIZE;
        if ((key.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE || isRemascAccount) && src.getValue() != null) {
            // We've reached the Account level. From now on everything will be different.
            AccountState astate = new AccountState(src.getValue());
            OldAccountState oldState = new OldAccountState(astate.getNonce(),astate.getBalance());
            // child1 (0x80) will be the code
            if (child1 != null) {
                oldState.setCodeHash(child1.getValueHash());
            } else {
                oldState.setCodeHash(Keccak256Helper.keccak256(new byte[0]));
            }
            // the child0 side will be the storage. The first child corresponds to the
            // 8-bit zero prefix. 1 bit is consumed by the branch. 7-bits left. We check that
            if (child0 != null) {
                if (child0.getSharedPath().length() != 7) {
                    throw new IllegalStateException("First child must be 7-bits length");
                }
                // We'll create an ad-hoc trie for storage cells, the first
                // child0's child is the root of this try. What if it has two children?
                // This can happen if there are two hashed storage keys, one begining with
                // 0 and another with 1.
                TrieKeySlice child0Key = key.rebuildSharedPath(LEFT_CHILD_IMPLICIT_KEY, child0.getSharedPath());
                byte[] stateRoot = getOrchidStateRoot(child0Key, child0, true, false);
                oldState.setStateRoot(stateRoot);
            } else if (isRemascAccount) {
                oldState.setStateRoot(Keccak256Helper.keccak256(RLP.encodeElement(new byte[0])));
            }

            byte[] avalue = oldState.getEncoded();
            TrieKeySlice orchidKey;
            if (isRemascAccount) {
                orchidKey = extractOrchidAccountKeyPathFromUnitrieKey(key, sharedPath.length(), RemascTransaction.REMASC_ADDRESS.getBytes().length, TrieKeyMapper.REMASC_ACCOUNT_KEY_SIZE);
            } else {
                orchidKey = extractOrchidAccountKeyPathFromUnitrieKey(key, sharedPath.length(), RskAddress.LENGTH_IN_BYTES, TrieKeyMapper.SECURE_ACCOUNT_KEY_SIZE);
            }

            Trie newNode = new Trie(
                    orchidKey, avalue, NodeReference.empty(), NodeReference.empty(), null,
                    avalue.length, null, src.isSecure()
            );
//            dump.add(key.toString() + "\n");
//            dump.add(newNode.toString());
            cacheHashes.put(src.getHash(), newNode.getHash());
            return newNode.getHash().getBytes();
        }

        if (child0 != null) {
            TrieKeySlice child0Key = key.rebuildSharedPath(LEFT_CHILD_IMPLICIT_KEY, child0.getSharedPath());
            child0Hash = getOrchidAccountTrieRoot(child0Key, child0, false);
        }

        if (child1 != null) {
            TrieKeySlice child1Key = key.rebuildSharedPath(RIGHT_CHILD_IMPLICIT_KEY, child1.getSharedPath());
            child1Hash = getOrchidAccountTrieRoot(child1Key, child1, false);
        }

        NodeReference left = new NodeReference(null, null, child0Hash == null ? null : new Keccak256(child0Hash));
        NodeReference right = new NodeReference(null, null, child1Hash == null ? null : new Keccak256(child1Hash));

        Trie newNode = new Trie(
                sharedPath, src.getValue(), left, right, null,
                src.getValueLength(), src.getValueHash(), src.isSecure()
        );
//        dump.add(key.toString());
//        dump.add(newNode.toString());

        cacheHashes.put(src.getHash(), newNode.getHash());
        return newNode.getHash().getBytes();
    }

    private byte[] getOrchidStateRoot(TrieKeySlice key, Trie unitrieStorageRoot, boolean removeFirstNodePrefix, boolean onlyChild) {
        if (unitrieStorageRoot == null) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        byte[] storageNodeHash = cacheStorage.get(unitrieStorageRoot.getHash());
        if (storageNodeHash != null && !onlyChild  && !removeFirstNodePrefix) {
            return storageNodeHash;
        }

        Trie child0 = unitrieStorageRoot.getNodeReference(LEFT_CHILD_IMPLICIT_KEY).getNode().orElse(null);
        Trie child1 = unitrieStorageRoot.getNodeReference(RIGHT_CHILD_IMPLICIT_KEY).getNode().orElse(null);
        byte[] child0Hash = null;
        if (child0 != null) {
            TrieKeySlice child0Key = key.rebuildSharedPath(LEFT_CHILD_IMPLICIT_KEY, child0.getSharedPath());
            child0Hash = getOrchidStateRoot(child0Key, child0, false, removeFirstNodePrefix && child1 == null);
        }

        byte[] child1Hash = null;
        if (child1 != null) {
            TrieKeySlice child1Key = key.rebuildSharedPath(RIGHT_CHILD_IMPLICIT_KEY, child1.getSharedPath());
            child1Hash = getOrchidStateRoot(child1Key, child1, false, removeFirstNodePrefix && child0 == null);
        }

        TrieKeySlice sharedPath = unitrieStorageRoot.getSharedPath();
        byte[] value = unitrieStorageRoot.getValue();
        int valueLength = unitrieStorageRoot.getValueLength();
        byte[] valueHash = unitrieStorageRoot.getValueHash();

        if (removeFirstNodePrefix) {
            sharedPath = TrieKeySlice.empty();
            value = null; // also remove value
            valueLength = 0;
            valueHash = null;
            if (child0 != null && child1 == null) {
                return child0Hash;
            }
            if (child0 == null && child1 != null ) {
                return child1Hash;
            }
        }

        if (onlyChild) {
            sharedPath = key.slice(key.length() - (sharedPath.length() + 1), key.length());
        }

        if (!removeFirstNodePrefix && child0Hash == null && child1Hash == null) { // terminal node
            sharedPath = extractOrchidStorageKeyPathFromUnitrieKey(key, sharedPath.length());
        }

        NodeReference left = new NodeReference(null, null, child0Hash == null ? null : new Keccak256(child0Hash));
        NodeReference right = new NodeReference(null, null, child1Hash == null ? null : new Keccak256(child1Hash));
        Trie newNode = new Trie(
                sharedPath, value, left, right, null,
                valueLength, valueHash, unitrieStorageRoot.isSecure()
        );
        if (!onlyChild) {
            cacheStorage.put(unitrieStorageRoot.getHash(), newNode.getHash().getBytes());
        }
        return newNode.getHash().getBytes();
    }

    private TrieKeySlice extractOrchidAccountKeyPathFromUnitrieKey(TrieKeySlice key, int sharedPathLength, int addressLengthInBytes, int unitrieKeySizeInBytes) {
        if (sharedPathLength < (unitrieKeySizeInBytes - TrieKeyMapper.SECURE_KEY_SIZE) * Byte.SIZE) { // = 20 bytes = RskAddress.LENGTH_IN_BYTES
            throw new IllegalArgumentException("The unitrie doesn't share as much structure as we need to rebuild the Orchid trie");
        }

        byte[] encodedKey = key.slice(key.length() - addressLengthInBytes * Byte.SIZE, key.length()).encode();
        byte[] orchidTrieSecureKey = Keccak256Helper.keccak256(encodedKey);
        TrieKeySlice expandedOrchidTrieSecureKey = TrieKeySlice.fromKey(orchidTrieSecureKey);
        // the length of the structure that's shared between the Orchid trie and the Unitrie
        int commonTriePathLength  = unitrieKeySizeInBytes * Byte.SIZE - sharedPathLength;

        return expandedOrchidTrieSecureKey.slice(commonTriePathLength, expandedOrchidTrieSecureKey.length());
    }

    // FIXME(diegoll) we need to add the same handling for remasc keys length
    private TrieKeySlice extractOrchidStorageKeyPathFromUnitrieKey(TrieKeySlice key, int sharedPathLength) {
        TrieKeySlice unsecuredKey = key.slice(key.length() - 256, key.length());
        byte[] orchidTrieSecureKey = Keccak256Helper.keccak256(unsecuredKey.encode());

        //(MutableRepository.STORAGE_KEY_SIZE - MutableRepository.SECURE_KEY_SIZE * 2 + MutableRepository.STORAGE_PREFIX.length )
        if (sharedPathLength < 256) { //TODO(diegoll) review 248 = SECURE_KEY_SIZE + RskAddress size + STORAGE_PREFIX + SECURE_KEY_SIZE
            throw new IllegalArgumentException("The unitrie storage doesn't share as much structure as we need to rebuild the Orchid trie");
        }

        TrieKeySlice expandedOrchidTrieSecureKey = TrieKeySlice.fromKey(orchidTrieSecureKey);

        int consumedFrom80bitPrefix = 42 * Byte.SIZE - sharedPathLength;

        return expandedOrchidTrieSecureKey.slice(consumedFrom80bitPrefix, expandedOrchidTrieSecureKey.length());
    }

}
