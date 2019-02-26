package co.rsk.trie;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.RLP;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class TrieConverter {

    private static final byte LEFT_CHILD_IMPLICIT_KEY = (byte) 0x00;
    private static final byte RIGHT_CHILD_IMPLICIT_KEY = (byte) 0x01;
    private static final byte[] REMASC_SENDER_UNITRIE_EXPANDED_KEY = concat(
            MutableRepository.DOMAIN_PREFIX,
            Arrays.copyOfRange(Keccak256Helper.keccak256(RemascTransaction.REMASC_ADDRESS.getBytes()), 0, MutableRepository.SECURE_KEY_SIZE),
            RemascTransaction.REMASC_ADDRESS.getBytes()
    );
    private static final byte[] REMASC_SENDER_UNITRIE_KEY = PathEncoder.decode(
            REMASC_SENDER_UNITRIE_EXPANDED_KEY, REMASC_SENDER_UNITRIE_EXPANDED_KEY.length * Byte.SIZE
    );

    private final Map<Keccak256, Keccak256> cacheHashes;

    public TrieConverter() {
        cacheHashes = new HashMap<>();
    }

    public byte[] getOrchidAccountTrieRoot(TrieImpl src) {
        return getOrchidAccountTrieRoot(src.getSharedPath(), src, true);
    }

    private byte[] getOrchidAccountTrieRoot(TrieKeySlice key, TrieImpl src, boolean removeFirst8bits) {
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

        TrieImpl child0 = (TrieImpl) src.retrieveNode(0);
        byte[] child0Hash = null;
        TrieImpl child1 = (TrieImpl) src.retrieveNode(1);
        byte[] child1Hash = null;

        boolean isRemascAccount = key.length() == (1 + MutableRepository.SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length) * Byte.SIZE;
        if ((key.length() == (1 + MutableRepository.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE || isRemascAccount) && src.getValue() != null) {
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
                TrieKeySlice child0Key = key.rebuildSharedPath((byte) 0, child0.getSharedPath());
                byte[] stateRoot = getOrchidStateRoot(child0Key, child0, true, false);
                oldState.setStateRoot(stateRoot);
            } else if (isRemascAccount) {
                oldState.setStateRoot(Keccak256Helper.keccak256(RLP.encodeElement(new byte[0])));
            }

            byte[] avalue = oldState.getEncoded();
            TrieKeySlice orchidKey;
            if (isRemascAccount) {
                orchidKey = extractOrchidAccountKeyPathFromUnitrieKey(key, sharedPath.length(), RemascTransaction.REMASC_ADDRESS.getBytes().length, MutableRepository.REMASC_ACCOUNT_KEY_SIZE);
            } else {
                orchidKey = extractOrchidAccountKeyPathFromUnitrieKey(key, sharedPath.length(), RskAddress.LENGTH_IN_BYTES, MutableRepository.ACCOUNT_KEY_SIZE);
            }

            TrieImpl newNode = new TrieImpl(
                    orchidKey, avalue, null, null, null,
                    avalue.length, null, src.isSecure()
            );

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

        Keccak256[] hashes = new Keccak256[] {
                child0Hash == null ? null : new Keccak256(child0Hash),
                child1Hash == null ? null : new Keccak256(child1Hash)
        };

        TrieImpl newNode = new TrieImpl(
                sharedPath, src.getValue(), null, hashes, null, src.valueLength,
                src.getValueHash(), src.isSecure()
        );

        cacheHashes.put(src.getHash(), newNode.getHash());
        return newNode.getHash().getBytes();
    }

    private byte[] getOrchidStateRoot(TrieKeySlice key, TrieImpl unitrieStorageRoot, boolean removeFirstNodePrefix, boolean onlyChild) {
        if (unitrieStorageRoot == null) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        Keccak256 cacheHash = cacheHashes.get(unitrieStorageRoot.getHash());
        if (cacheHash != null) {
            return cacheHash.getBytes();
        }

        TrieImpl child0 = (TrieImpl) unitrieStorageRoot.retrieveNode(0);
        TrieImpl child1 = (TrieImpl) unitrieStorageRoot.retrieveNode(1);
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

        Keccak256[] hashes = Stream.of(child0Hash, child1Hash).map(hash -> hash==null? null : new Keccak256(hash)).toArray(Keccak256[]::new);

        TrieKeySlice sharedPath = unitrieStorageRoot.getSharedPath();
        byte[] value = unitrieStorageRoot.getValue();
        int valueLength = unitrieStorageRoot.valueLength;
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

        if (!removeFirstNodePrefix && hashes[0] == null && hashes[1] == null) { // terminal node
            sharedPath = extractOrchidStorageKeyPathFromUnitrieKey(key, sharedPath.length());
        }

        TrieImpl newNode = new TrieImpl(
                sharedPath, value, null, hashes, null,
                valueLength, valueHash, unitrieStorageRoot.isSecure()
        );

        cacheHashes.put(unitrieStorageRoot.getHash(), newNode.getHash());
        return newNode.getHash().getBytes();
    }

    private static byte[] concat(byte[]... arrays) {
        int length = Stream.of(arrays).mapToInt(array -> array.length).sum();
        byte[] result = new byte[length];
        int pos = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, pos, array.length);
            pos += array.length;
        }
        return result;
    }

    private TrieKeySlice extractOrchidAccountKeyPathFromUnitrieKey(TrieKeySlice key, int sharedPathLength, int addressLengthInBytes, int unitrieKeySizeInBytes) {
        if (sharedPathLength < (unitrieKeySizeInBytes - MutableRepository.SECURE_KEY_SIZE) * Byte.SIZE) { // = 20 bytes = RskAddress.LENGTH_IN_BYTES
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
