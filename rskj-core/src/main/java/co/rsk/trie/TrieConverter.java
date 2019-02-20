package co.rsk.trie;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.RLP;

import java.util.Arrays;
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

    private final Map<Keccak256, byte[]> cacheHashes;
    private final Map<Keccak256, TrieImpl> cacheStorage;
//    private final List<String> dump = new ArrayList<>();

    public TrieConverter() {
        cacheHashes = new MaxSizeHashMap<>(500_000, true);
        cacheStorage = new MaxSizeHashMap<>(600_000, true);
    }

    public byte[] getOrchidAccountTrieRoot(TrieImpl src) {
//        dump.clear();
//        try {
//            FileWriter writer = new FileWriter("output.txt");
//            for(String str: dump) {
//                writer.write(str);
//            }
//            writer.close();
//        } catch (Exception e) {
//            System.out.println("SALIO MAL");
//        }
        return cacheHashes.computeIfAbsent(src.getHash(), k -> {
            TrieImpl trie = getOrchidAccountTrieRoot(new byte[]{}, src, true);
            return trie == null ? HashUtil.EMPTY_TRIE_HASH : trie.getHashOrchid().getBytes();
        });
    }

    private TrieImpl getOrchidAccountTrieRoot(byte[] key, TrieImpl src, boolean removeFirst8bits) {
        if (src == null) {
            return null;
        }

        // TODO(mc) use the TrieKeySlice native operations
        byte[] encodedSharedPath = src.getEncodedSharedPath();
        int sharedPathLength = src.getSharedPathLength();
        if (encodedSharedPath != null) {
            byte[] sharedPath = PathEncoder.decode(encodedSharedPath, sharedPathLength);
            key = concat(key, sharedPath);
        }
        if (removeFirst8bits) {
            if (sharedPathLength < 8) {
                throw new IllegalStateException("Unable to remove first 8-bits if path length is less than 8");
            }
            sharedPathLength -= 8;
            encodedSharedPath = Arrays.copyOfRange(encodedSharedPath,1, encodedSharedPath.length);
        }
        TrieImpl child0 = (TrieImpl) src.retrieveNode(0);
        TrieImpl child0Hash = null;
        TrieImpl child1 = (TrieImpl) src.retrieveNode(1);
        TrieImpl child1Hash = null;

        boolean isRemascAccount = key.length == (1 + MutableRepository.SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length) * Byte.SIZE;
        if ((key.length == (1 + MutableRepository.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE || isRemascAccount) && src.getValue() != null) {
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
                if (child0.getSharedPathLength()!=7) {
                    throw new IllegalStateException("First child must be 7-bits length");
                }
                // We'll create an ad-hoc trie for storage cells, the first
                // child0's child is the root of this try. What if it has two children?
                // This can happen if there are two hashed storage keys, one begining with
                // 0 and another with 1.
                byte[] stateRoot = getOrchidStateRoot(child0);
                oldState.setStateRoot(stateRoot);
            } else if (isRemascAccount) {
                oldState.setStateRoot(Keccak256Helper.keccak256(RLP.encodeElement(new byte[0])));
            }

            byte[] avalue = oldState.getEncoded();
            byte[] orchidKey;
            if (isRemascAccount) {
                orchidKey = extractOrchidAccountKeyPathFromUnitrieKey(key, sharedPathLength, RemascTransaction.REMASC_ADDRESS.getBytes().length, MutableRepository.REMASC_ACCOUNT_KEY_SIZE);
            } else {
                orchidKey = extractOrchidAccountKeyPathFromUnitrieKey(key, sharedPathLength, RskAddress.LENGTH_IN_BYTES, MutableRepository.ACCOUNT_KEY_SIZE);
            }

            encodedSharedPath = PathEncoder.encode(orchidKey);
            TrieImpl newNode = new TrieImpl(
                    TrieKeySlice.fromEncoded(encodedSharedPath, 0, orchidKey.length, encodedSharedPath.length),
                    avalue, null, null, null,
                    avalue.length, null, src.isSecure()
            );
//            dump.add(key.toString() + "\n");
//            dump.add(newNode.toString());
            return newNode;
        }

        if (child0 != null) {
            child0Hash = getOrchidAccountTrieRoot(concat(key, LEFT_CHILD_IMPLICIT_KEY), child0, false);
        }

        if (child1 != null) {
            child1Hash = getOrchidAccountTrieRoot(concat(key, RIGHT_CHILD_IMPLICIT_KEY), child1, false);
        }

        TrieImpl[] nodes = new TrieImpl[]{child0Hash, child1Hash};

        TrieImpl newNode = new TrieImpl(
                encodedSharedPath == null ? TrieKeySlice.empty() : TrieKeySlice.fromEncoded(encodedSharedPath, 0, sharedPathLength, encodedSharedPath.length),
                src.getValue(), nodes, null, null, src.valueLength,
                src.getValueHash(), src.isSecure()
        );
//        dump.add(key.toString());
//        dump.add(newNode.toString());

        return newNode;
    }

    private byte[] getOrchidStateRoot(TrieImpl unitrieStorageRoot) {
        TrieImpl trie = getOrchidStateRoot(new byte[] {}, unitrieStorageRoot, true, false, LEFT_CHILD_IMPLICIT_KEY);
        return trie == null ? HashUtil.EMPTY_TRIE_HASH : trie.getHashOrchid().getBytes();
    }

    private TrieImpl getOrchidStateRoot(
            byte[] key,
            TrieImpl unitrieStorageRoot,
            boolean removeFirstNodePrefix,
            boolean onlyChild,
            byte ancestor) {

        TrieImpl storageNodeHash = cacheStorage.get(unitrieStorageRoot.getHash());
        if (storageNodeHash != null && !onlyChild  && !removeFirstNodePrefix) {
            return storageNodeHash;
        }

        // shared Path
        byte[] encodedSharedPath = unitrieStorageRoot.getEncodedSharedPath();
        int sharedPathLength = unitrieStorageRoot.getSharedPathLength();
        if (encodedSharedPath != null) {
            byte[] sharedPath = PathEncoder.decode(encodedSharedPath, sharedPathLength);
            key = concat(key, sharedPath);
        }

        byte[] value = unitrieStorageRoot.getValue();
        int valueLength = unitrieStorageRoot.valueLength;
        byte[] valueHash = unitrieStorageRoot.getValueHash();

        TrieImpl child0 = (TrieImpl) unitrieStorageRoot.retrieveNode(0);
        TrieImpl child1 = (TrieImpl) unitrieStorageRoot.retrieveNode(1);

        TrieImpl child0Hash = null;
        if (child0 != null) {
            child0Hash = getOrchidStateRoot(concat(key, LEFT_CHILD_IMPLICIT_KEY), child0, false, removeFirstNodePrefix && child1 == null, LEFT_CHILD_IMPLICIT_KEY);
        }

        TrieImpl child1Hash = null;
        if (child1 != null) {
            child1Hash = getOrchidStateRoot(concat(key, RIGHT_CHILD_IMPLICIT_KEY), child1, false, removeFirstNodePrefix && child0 == null, RIGHT_CHILD_IMPLICIT_KEY);
        }

        TrieImpl[] nodes = new TrieImpl[]{child0Hash, child1Hash};

        if (removeFirstNodePrefix) {
            encodedSharedPath = null;
            sharedPathLength = 0;
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
            byte[] keyCopy;
            if (encodedSharedPath != null){
                byte[] expandedKey = PathEncoder.decode(encodedSharedPath, sharedPathLength);
                keyCopy = new byte[sharedPathLength + 1];
                System.arraycopy(expandedKey, 0, keyCopy, 1, sharedPathLength);
            } else {
                sharedPathLength = 0; // just in case
                keyCopy = new byte[1];
            }
            keyCopy[0] = ancestor;
            encodedSharedPath = PathEncoder.encode(keyCopy);
            sharedPathLength++;
        }


        if ((nodes[0]==null) && (nodes[1]==null)) { // terminal node
            byte[] expandedSharedPath = extractOrchidStorageKeyPathFromUnitrieKey(key ,sharedPathLength);
            encodedSharedPath = PathEncoder.encode(expandedSharedPath);
            sharedPathLength = expandedSharedPath.length;
        }

        TrieImpl newNode = new TrieImpl(
                encodedSharedPath == null ? TrieKeySlice.empty() : TrieKeySlice.fromEncoded(encodedSharedPath, 0, sharedPathLength, encodedSharedPath.length),
                value, nodes, null, null,
                valueLength, valueHash, unitrieStorageRoot.isSecure()
        );
        if (!onlyChild) {
            cacheStorage.put(unitrieStorageRoot.getHash(), newNode);
        }
        return newNode;
    }

    private static byte[] concat(byte[] first, byte b) {
        return concat(first, new byte[]{b});
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

    private byte[] extractOrchidAccountKeyPathFromUnitrieKey(byte[] key, int sharedPathLength, int addressLengthInBytes, int unitrieKeySizeInBytes) {
        byte[] unsecuredKey = Arrays.copyOfRange(key, key.length - addressLengthInBytes * Byte.SIZE, key.length);
        byte[] encodedKey = PathEncoder.encode(unsecuredKey);
        byte[] orchidTrieSecureKey = Keccak256Helper.keccak256(encodedKey);

        if (sharedPathLength < (unitrieKeySizeInBytes - MutableRepository.SECURE_KEY_SIZE) * Byte.SIZE) { // = 20 bytes = RskAddress.LENGTH_IN_BYTES
            throw new IllegalArgumentException("The unitrie doesn't share as much structure as we need to rebuild the Orchid trie");
        }

        byte[] expandedOrchidTrieSecureKey = PathEncoder.decode(orchidTrieSecureKey, Keccak256Helper.DEFAULT_SIZE);
        // the length of the structure that's shared between the Orchid trie and the Unitrie
        int commonTriePathLength  = unitrieKeySizeInBytes * Byte.SIZE - sharedPathLength;
        // the old key had 256 bits so the new node must contain what's needed to complete that information for an account
        int newPrefixSize = Keccak256Helper.DEFAULT_SIZE - commonTriePathLength;

        byte[] newDecodedPrefix = new byte[newPrefixSize];
        System.arraycopy(expandedOrchidTrieSecureKey, commonTriePathLength, newDecodedPrefix, 0, newPrefixSize);

        return newDecodedPrefix;
    }

    // FIXME(diegoll) we need to add the same handling for remasc keys length
    private byte[] extractOrchidStorageKeyPathFromUnitrieKey(byte[] key, int sharedPathLength) {
        byte[] unsecuredKey = Arrays.copyOfRange(key, key.length - 256, key.length);
        byte[] encodedKey = PathEncoder.encode(unsecuredKey);
        byte[] orchidTrieSecureKey = Keccak256Helper.keccak256(encodedKey);

        //(MutableRepository.STORAGE_KEY_SIZE - MutableRepository.SECURE_KEY_SIZE * 2 + MutableRepository.STORAGE_PREFIX.length )
        if (sharedPathLength < 256) { //TODO(diegoll) review 248 = SECURE_KEY_SIZE + RskAddress size + STORAGE_PREFIX + SECURE_KEY_SIZE
            throw new IllegalArgumentException("The unitrie storage doesn't share as much structure as we need to rebuild the Orchid trie");
        }

        byte[] expandedOrchidTrieSecureKey = PathEncoder.decode(orchidTrieSecureKey, Keccak256Helper.DEFAULT_SIZE);

        int consumedFrom80bitPrefix  = 42 * Byte.SIZE - sharedPathLength;
        int newPrefixSize  = sharedPathLength - 10 * Byte.SIZE;
        byte[] newDecodedPrefix = new byte[newPrefixSize];

        System.arraycopy(expandedOrchidTrieSecureKey, consumedFrom80bitPrefix, newDecodedPrefix, 0, newPrefixSize);

        return newDecodedPrefix;
    }

}
