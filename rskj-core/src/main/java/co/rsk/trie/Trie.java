/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.trie;

import co.rsk.bitcoinj.core.VarInt;
import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint16;
import co.rsk.core.types.ints.Uint24;
import co.rsk.core.types.ints.Uint8;
import co.rsk.crypto.Keccak256;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.trie.serializer.RSKIP107Serializer;
import co.rsk.trie.serializer.RSKIP240Serializer;
import co.rsk.trie.serializer.TrieSerializer;
import co.rsk.util.NodeStopper;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * A binary trie node.
 *
 * Each node has an optional associated value (a byte array)
 *
 * A node is referenced via a key (a byte array)
 *
 * A node can be serialized to/from a message (a byte array)
 *
 * A node has a hash (keccak256 of its serialization)
 *
 * A node is immutable: to add/change a value or key, a new node is created
 *
 * An empty node has no subnodes and a null value
 */
public class Trie {

    private static final Logger logger = LoggerFactory.getLogger(Trie.class);

    private static final Profiler profiler = ProfilerFactory.getInstance();

    private static final int ARITY = 2;
    private static final int MAX_EMBEDDED_NODE_SIZE_IN_BYTES = 44;
    private static final String INVALID_ARITY = "Invalid arity";

    private static final int MESSAGE_HEADER_LENGTH = 2 + Short.BYTES * 2;
    private static final String INVALID_VALUE_LENGTH = "Invalid value length";

    // all zeroed, default hash for empty nodes
    private static final Keccak256 EMPTY_HASH = makeEmptyHash();

    // a long value exceeds 32 bytes
    public static final int LONG_VALUE = 32 + 1;
    public static final int TIMESTAMP_SIZE = Long.BYTES;
    public static final int FLAGS_SIZE = Byte.BYTES;
    public static final int EMBEDDED_CHILD_LENGTH_SIZE = Uint8.BYTES;
    public static final int CHILD_HASH_SIZE = 32;

    // to represent a non-initialized rent timestamp
    public static final long NO_RENT_TIMESTAMP = -1;

    public static final int HOP_TRIE_VERSION = 0b10000000; // todo(fedejinich) this is actually 128 and -128 (casted to byte)
    public static final int RSKIP107_TRIE_VERSION = 0b01000000;

    // this node associated value, if any
    private byte[] value;

    private final NodeReference left;

    private final NodeReference right;

    private final NodeStopper nodeStopper;

    // this node hash value
    private Keccak256 hash;

    // this node hash value as calculated before RSKIP 107
    // we need to cache it, otherwise TrieConverter is prohibitively slow.
    private Keccak256 hashOrchid;

    // temporary storage of encoding. Removed after save()
    private byte[] encoded;

    // valueLength enables lazy long value retrieval.
    // The length of the data is now stored. This allows EXTCODESIZE to
    // execute much faster without the need to actually retrieve the data.
    // if valueLength>32 and value==null this means the value has not been retrieved yet.
    // if valueLength==0, then there is no value AND no node.
    // This trie structure does not distinguish between empty arrays
    // and nulls. Storing an empty byte array has the same effect as removing the node.
    //
    private final Uint24 valueLength;

    // For lazy retrieval and also for cache.
    private Keccak256 valueHash;

    // the size of this node along with its children (in bytes)
    // note that we use a long because an int would allow only up to 4GB of state to be stored.
    private VarInt childrenSize;

    // associated store, to store or retrieve nodes in the trie
    private final TrieStore store;

    // already saved in store flag
    private volatile boolean saved;

    // shared Path
    private final TrieKeySlice sharedPath;

    // rent timestamp (checkout rskip240)
    private final long lastRentPaidTimestamp;

    // default constructor, no secure
    public Trie() {
        this(null);
    }

    // root node
    public Trie(TrieStore store) {
        this(store, TrieKeySlice.empty(), null, NO_RENT_TIMESTAMP);
    }

    // leaf node
    private Trie(TrieStore store, TrieKeySlice sharedPath, byte[] value, long lastRentPaidTimestamp) {
        this(store, sharedPath, value, NodeReference.empty(), NodeReference.empty(), getDataLength(value), null, new VarInt(0), lastRentPaidTimestamp);
    }

    // full constructor
    private Trie(TrieStore store, TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, Uint24 valueLength, Keccak256 valueHash, VarInt childrenSize, long lastRentPaidTimestamp) {
        this.value = value;
        this.left = left;
        this.right = right;
        this.store = store;
        this.sharedPath = sharedPath;
        this.valueLength = valueLength;
        this.valueHash = valueHash;
        this.childrenSize = childrenSize;
        this.lastRentPaidTimestamp = lastRentPaidTimestamp;
        this.nodeStopper = exitStatus -> System.exit(exitStatus);
        checkValueLength();
    }

    /**
     * Deserialize a Trie, either using the original format or RSKIP 107 format, based on version flags.
     * The original trie wasted the first byte by encoding the arity, which was always 2. We use this marker to
     * recognize the old serialization format.
     */
    public static Trie fromMessage(byte[] message, TrieStore store) {
        Trie trie;
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BUILD_TRIE_FROM_MSG);
        if (message[0] == ARITY) {
            trie = fromMessageOrchid(message, store);
        } else {
            trie = internalFromMessage(ByteBuffer.wrap(message), store);
        }

        profiler.stop(metric);

        return trie;
    }

    private static Trie fromMessageOrchid(byte[] message, TrieStore store) {
        int current = 0;
        int arity = message[current];
        current += Byte.BYTES;

        if (arity != ARITY) {
            throw new IllegalArgumentException(INVALID_ARITY);
        }

        int flags = message[current];
        current += Byte.BYTES;

        boolean hasLongVal = (flags & 0x02) == 2;
        int bhashes = Uint16.decodeToInt(message, current);
        current += Uint16.BYTES;
        int lshared = Uint16.decodeToInt(message, current);
        current += Uint16.BYTES;

        TrieKeySlice sharedPath = TrieKeySlice.empty();
        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        if (lencoded > 0) {
            if (message.length - current < lencoded) {
                throw new IllegalArgumentException(String.format(
                        "Left message is too short for encoded shared path expected:%d actual:%d total:%d",
                        lencoded, message.length - current, message.length));
            }
            sharedPath = TrieKeySlice.fromEncoded(message, current, lshared, lencoded);
            current += lencoded;
        }

        int keccakSize = Keccak256Helper.DEFAULT_SIZE_BYTES;
        NodeReference left = NodeReference.empty();
        NodeReference right = NodeReference.empty();

        int nhashes = 0;
        if ((bhashes & 0b01) != 0) {
            Keccak256 nodeHash = readHash(message, current);
            left = new NodeReference(store, null, nodeHash);
            current += keccakSize;
            nhashes++;
        }
        if ((bhashes & 0b10) != 0) {
            Keccak256 nodeHash = readHash(message, current);
            right = new NodeReference(store, null, nodeHash);
            current += keccakSize;
            nhashes++;
        }

        int offset = MESSAGE_HEADER_LENGTH + lencoded + nhashes * keccakSize;
        byte[] value;
        Uint24 lvalue;
        Keccak256 valueHash;

        if (hasLongVal) {
            valueHash = readHash(message, current);
            value = store.retrieveValue(valueHash.getBytes());
            lvalue = new Uint24(value.length);
        } else {
            int remaining = message.length - offset;
            if (remaining > 0) {
                if (message.length - current  < remaining) {
                    throw new IllegalArgumentException(String.format(
                            "Left message is too short for value expected:%d actual:%d total:%d",
                            remaining, message.length - current, message.length));
                }

                value = Arrays.copyOfRange(message, current, current + remaining);
                valueHash = null;
                lvalue = new Uint24(remaining);
            } else {
                value = null;
                valueHash = null;
                lvalue = Uint24.ZERO;
            }
        }

        // it doesn't need to clone value since it's retrieved from store or created from message
        // todo(fedejinich) an orchid message should never contain a rent timestamp
        return new Trie(store, sharedPath, value, left, right, lvalue, valueHash, null, NO_RENT_TIMESTAMP);
    }

//    private static Trie fromMessageRskip107(ByteBuffer message, TrieStore store) {
//        byte flags = message.get();
//        // if we reached here, we don't need to check the version flag
//        boolean hasLongVal = (flags & 0b00100000) == 0b00100000;
//        boolean sharedPrefixPresent = (flags & 0b00010000) == 0b00010000;
//        boolean leftNodePresent = (flags & 0b00001000) == 0b00001000;
//        boolean rightNodePresent = (flags & 0b00000100) == 0b00000100;
//        boolean leftNodeEmbedded = (flags & 0b00000010) == 0b00000010;
//        boolean rightNodeEmbedded = (flags & 0b00000001) == 0b00000001;
//
//        TrieKeySlice sharedPath = SharedPathSerializer.deserialize(message, sharedPrefixPresent);
//
//        NodeReference left = NodeReference.empty();
//        NodeReference right = NodeReference.empty();
//        if (leftNodePresent) {
//            if (leftNodeEmbedded) {
//                byte[] lengthBytes = new byte[Uint8.BYTES];
//                message.get(lengthBytes);
//                Uint8 length = Uint8.decode(lengthBytes, 0);
//
//                byte[] serializedNode = new byte[length.intValue()];
//                message.get(serializedNode);
//                Trie node = fromMessageRskip107(ByteBuffer.wrap(serializedNode), store);
//                left = new NodeReference(store, node, null);
//            } else {
//                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
//                message.get(valueHash);
//                Keccak256 nodeHash = new Keccak256(valueHash);
//                left = new NodeReference(store, null, nodeHash);
//            }
//        }
//
//        if (rightNodePresent) {
//            if (rightNodeEmbedded) {
//                byte[] lengthBytes = new byte[Uint8.BYTES];
//                message.get(lengthBytes);
//                Uint8 length = Uint8.decode(lengthBytes, 0);
//
//                byte[] serializedNode = new byte[length.intValue()];
//                message.get(serializedNode);
//                Trie node = fromMessageRskip107(ByteBuffer.wrap(serializedNode), store);
//                right = new NodeReference(store, node, null);
//            } else {
//                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
//                message.get(valueHash);
//                Keccak256 nodeHash = new Keccak256(valueHash);
//                right = new NodeReference(store, null, nodeHash);
//            }
//        }
//
//        VarInt childrenSize = new VarInt(0);
//        if (leftNodePresent || rightNodePresent) {
//            childrenSize = readVarInt(message);
//        }
//
//        byte[] value;
//        Uint24 lvalue;
//        Keccak256 valueHash;
//
//        if (hasLongVal) {
//            value = null;
//            byte[] valueHashBytes = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
//            message.get(valueHashBytes);
//            valueHash = new Keccak256(valueHashBytes);
//            byte[] lvalueBytes = new byte[Uint24.BYTES];
//            message.get(lvalueBytes);
//            lvalue = Uint24.decode(lvalueBytes, 0);
//        } else {
//            int remaining = message.remaining();
//            if (remaining != 0) {
//                value = new byte[remaining];
//                message.get(value);
//                valueHash = new Keccak256(Keccak256Helper.keccak256(value));
//                lvalue = new Uint24(remaining);
//            } else {
//                value = null;
//                valueHash = null;
//                lvalue = Uint24.ZERO;
//            }
//        }
//
//        if (message.hasRemaining()) {
//            throw new IllegalArgumentException("The message had more data than expected");
//        }
//
//        Trie trie = new Trie(store, sharedPath, value, left, right, lvalue, valueHash, childrenSize);
//
//        return trie;
//    }

    private static Trie internalFromMessage(ByteBuffer message, TrieStore store) {
        byte flags = message.get();
        TrieSerializer trieSerializer = new RSKIP107Serializer();

        // check if it's an rskip240 trie node
        if((flags & 0b10000000) == 0b10000000) {
            trieSerializer = new RSKIP240Serializer();
        }

        // if we reached here, we don't need to check the version flag
        boolean hasLongVal = (flags & 0b00100000) == 0b00100000;
        boolean sharedPrefixPresent = (flags & 0b00010000) == 0b00010000;
        boolean leftNodePresent = (flags & 0b00001000) == 0b00001000;
        boolean rightNodePresent = (flags & 0b00000100) == 0b00000100;
        boolean leftNodeEmbedded = (flags & 0b00000010) == 0b00000010;
        boolean rightNodeEmbedded = (flags & 0b00000001) == 0b00000001;

//        message.getLong()
        long lastRentPaidTimestamp = trieSerializer.deserializeLastRentPaidTimestamp(message);

        TrieKeySlice sharedPath = SharedPathSerializer.deserialize(message, sharedPrefixPresent);

        NodeReference left = NodeReference.empty();
        NodeReference right = NodeReference.empty();
        if (leftNodePresent) {
            if (leftNodeEmbedded) {
                byte[] lengthBytes = new byte[Uint8.BYTES];
                message.get(lengthBytes);
                Uint8 length = Uint8.decode(lengthBytes, 0);

                byte[] serializedNode = new byte[length.intValue()];
                message.get(serializedNode);
                Trie node = internalFromMessage(ByteBuffer.wrap(serializedNode), store);
                left = new NodeReference(store, node, null);
            } else {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                message.get(valueHash);
                Keccak256 nodeHash = new Keccak256(valueHash);
                left = new NodeReference(store, null, nodeHash);
            }
        }

        if (rightNodePresent) {
            if (rightNodeEmbedded) {
                byte[] lengthBytes = new byte[Uint8.BYTES];
                message.get(lengthBytes);
                Uint8 length = Uint8.decode(lengthBytes, 0);

                byte[] serializedNode = new byte[length.intValue()];
                message.get(serializedNode);
                Trie node = internalFromMessage(ByteBuffer.wrap(serializedNode), store);
                right = new NodeReference(store, node, null);
            } else {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                message.get(valueHash);
                Keccak256 nodeHash = new Keccak256(valueHash);
                right = new NodeReference(store, null, nodeHash);
            }
        }

        VarInt childrenSize = new VarInt(0);
        if (leftNodePresent || rightNodePresent) {
            childrenSize = readVarInt(message);
        }

        byte[] value;
        Uint24 lvalue;
        Keccak256 valueHash;

        if (hasLongVal) {
            value = null;
            byte[] valueHashBytes = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
            message.get(valueHashBytes);
            valueHash = new Keccak256(valueHashBytes);
            byte[] lvalueBytes = new byte[Uint24.BYTES];
            message.get(lvalueBytes);
            lvalue = Uint24.decode(lvalueBytes, 0);
        } else {
            int remaining = message.remaining();
            if (remaining != 0) {
                value = new byte[remaining];
                message.get(value);
                valueHash = new Keccak256(Keccak256Helper.keccak256(value));
                lvalue = new Uint24(remaining);
            } else {
                value = null;
                valueHash = null;
                lvalue = Uint24.ZERO;
            }
        }

        if (message.hasRemaining()) {
            throw new IllegalArgumentException("The message had more data than expected");
        }

        Trie trie = new Trie(store, sharedPath, value, left, right, lvalue, valueHash, childrenSize, lastRentPaidTimestamp);

        return trie;
    }

    // todo(fedejinich) it'll be inclued in production in the last storage rent stage.
    //  this is duplicated code, but the truth is that the old de/serialization method won't be changed because
    //  it impacts directly in consensus rules (same aplies to further de/serialization methods)
    @VisibleForTesting
    private static Trie fromMessageRskip240(ByteBuffer message, TrieStore store) {
        byte flags = message.get();
        // if we reached here, we don't need to check the version flag
        boolean hasLongVal = (flags & 0b00100000) == 0b00100000;
        boolean sharedPrefixPresent = (flags & 0b00010000) == 0b00010000;
        boolean leftNodePresent = (flags & 0b00001000) == 0b00001000;
        boolean rightNodePresent = (flags & 0b00000100) == 0b00000100;
        boolean leftNodeEmbedded = (flags & 0b00000010) == 0b00000010;
        boolean rightNodeEmbedded = (flags & 0b00000001) == 0b00000001;

        long lastRentPaidTimestamp = message.getLong();

        TrieKeySlice sharedPath = SharedPathSerializer.deserialize(message, sharedPrefixPresent);

        NodeReference left = NodeReference.empty();
        NodeReference right = NodeReference.empty();
        if (leftNodePresent) {
            if (leftNodeEmbedded) {
                byte[] lengthBytes = new byte[Uint8.BYTES];
                message.get(lengthBytes);
                Uint8 length = Uint8.decode(lengthBytes, 0);

                byte[] serializedNode = new byte[length.intValue()];
                message.get(serializedNode);
                Trie node = fromMessageRskip240(ByteBuffer.wrap(serializedNode), store);
                left = new NodeReference(store, node, null);
            } else {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                message.get(valueHash);
                Keccak256 nodeHash = new Keccak256(valueHash);
                left = new NodeReference(store, null, nodeHash);
            }
        }

        if (rightNodePresent) {
            if (rightNodeEmbedded) {
                byte[] lengthBytes = new byte[Uint8.BYTES];
                message.get(lengthBytes);
                Uint8 length = Uint8.decode(lengthBytes, 0);

                byte[] serializedNode = new byte[length.intValue()];
                message.get(serializedNode);
                Trie node = fromMessageRskip240(ByteBuffer.wrap(serializedNode), store);
                right = new NodeReference(store, node, null);
            } else {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                message.get(valueHash);
                Keccak256 nodeHash = new Keccak256(valueHash);
                right = new NodeReference(store, null, nodeHash);
            }
        }

        VarInt childrenSize = new VarInt(0);
        if (leftNodePresent || rightNodePresent) {
            childrenSize = readVarInt(message);
        }

        byte[] value;
        Uint24 lvalue;
        Keccak256 valueHash;

        if (hasLongVal) {
            value = null;
            byte[] valueHashBytes = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
            message.get(valueHashBytes);
            valueHash = new Keccak256(valueHashBytes);
            byte[] lvalueBytes = new byte[Uint24.BYTES];
            message.get(lvalueBytes);
            lvalue = Uint24.decode(lvalueBytes, 0);
        } else {
            int remaining = message.remaining();
            if (remaining != 0) {
                value = new byte[remaining];
                message.get(value);
                valueHash = new Keccak256(Keccak256Helper.keccak256(value));
                lvalue = new Uint24(remaining);
            } else {
                value = null;
                valueHash = null;
                lvalue = Uint24.ZERO;
            }
        }

        if (message.hasRemaining()) {
            throw new IllegalArgumentException("The message had more data than expected");
        }

        // todo(fedejinich) an rskip107 message should never contain a rent timestamp
        Trie trie = new Trie(store, sharedPath, value, left, right, lvalue, valueHash, childrenSize, lastRentPaidTimestamp);

        return trie;
    }

    /**
     * getHash calculates and/or returns the hash associated with this node content
     *
     * the internal variable hash could contains the cached hash.
     *
     * This method is not synchronized because the result of it's execution
     *
     * disregarding the lazy initialization is idempotent. It's better to keep
     *
     * it out of synchronized.
     *
     * @return  a byte array with the node serialized to bytes
     */
    public Keccak256 getHash() {
        if (this.hash != null) {
            return this.hash.copy();
        }

        if (isEmptyTrie()) {
            return EMPTY_HASH.copy();
        }

        byte[] message = this.toMessage();

        this.hash = new Keccak256(Keccak256Helper.keccak256(message));

        return this.hash.copy();
    }

    /**
     * The hash based on pre-RSKIP 107 serialization
     */
    public Keccak256 getHashOrchid(boolean isSecure) {
        if (this.hashOrchid != null) {
            return this.hashOrchid.copy();
        }

        if (isEmptyTrie()) {
            return EMPTY_HASH.copy();
        }

        byte[] message = this.toMessageOrchid(isSecure);

        this.hashOrchid = new Keccak256(Keccak256Helper.keccak256(message));

        return this.hashOrchid.copy();
    }

    /**
     * get returns the value associated with a key
     *
     * @param key the key associated with the value, a byte array (variable length)
     *
     * @return  the associated value, a byte array, or null if there is no associated value to the key
     */
    @Nullable
    public byte[] get(byte[] key) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.TRIE_GET_VALUE_FROM_KEY);
        Trie node = find(key);
        if (node == null) {
            profiler.stop(metric);
            return null;
        }

        byte[] result = node.getValue();
        profiler.stop(metric);
        return result;
    }

    /**
     * get by string, utility method used from test methods
     *
     * @param key   a string, that is converted to a byte array
     * @return a byte array with the associated value
     */
    public byte[] get(String key) {
        return this.get(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * put key value association, returning a new NewTrie
     *
     * @param key   key to be updated or created, a byte array
     * @param value value to associated to the key, a byte array
     *
     * @return a new NewTrie node, the top node of the new tree having the
     * key-value association. The original node is immutable, a new tree
     * is build, adding some new nodes
     */
    public Trie put(byte[] key, byte[] value) {
        TrieKeySlice keySlice = TrieKeySlice.fromKey(key);
        Trie trie = put(keySlice, value, false);

        return trie == null ? new Trie(this.store) : trie;
    }

    public Trie put(ByteArrayWrapper key, byte[] value) {
        return put(key.getData(), value);
    }
    /**
     * put string key to value, the key is converted to byte array
     * utility method to be used from testing
     *
     * @param key   a string
     * @param value an associated value, a byte array
     *
     * @return  a new NewTrie, the top node of a new trie having the key
     * value association
     */
    @VisibleForTesting
    public Trie put(String key, byte[] value) {
        return put(key.getBytes(StandardCharsets.UTF_8), value);
    }

    /**
     * delete update the key to null value
     *
     * @param key   a byte array
     *
     * @return the new top node of the trie with the association removed
     *
     */
    public Trie delete(byte[] key) {
        return put(key, null);
    }

    // This is O(1). The node with exact key "key" MUST exists.
    public Trie deleteRecursive(byte[] key) {
        TrieKeySlice keySlice = TrieKeySlice.fromKey(key);
        Trie trie = put(keySlice, null, true);

        return trie == null ? new Trie(this.store) : trie;
    }

    /**
     * delete string key, utility method to be used for testing
     *
     * @param key a string
     *
     * @return the new top node of the trie with the key removed
     */
    public Trie delete(String key) {
        return delete(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * toMessage serialize the node to bytes. Used to persist the node in a key-value store
     * like levelDB or redis.
     *
     * The serialization includes:
     * - arity: byte
     * - bits with present hashes: two bytes (example: 0x0203 says that the node has
     * hashes at index 0, 1, 9 (the other subnodes are null)
     * - present hashes: 32 bytes each
     * - associated value: remainder bytes (no bytes if null)
     *
     * @return a byte array with the serialized info
     */
    public byte[] toMessage() {
        if (encoded == null) {
            internalToMessage();
        }

        return cloneArray(encoded);
    }

    public int getMessageLength() {
        if (encoded == null) {
            internalToMessage();
        }

        return encoded.length;
    }

    /**
     * Serialize the node to bytes with the pre-RSKIP 107 format
     */
    public byte[] toMessageOrchid(boolean isSecure) {
        Uint24 lvalue = this.valueLength;
        int lshared = this.sharedPath.length();
        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        boolean hasLongVal = this.hasLongValue();
        Optional<Keccak256> leftHashOpt = this.left.getHashOrchid(isSecure);
        Optional<Keccak256> rightHashOpt = this.right.getHashOrchid(isSecure);

        int nnodes = 0;
        int bits = 0;
        if (leftHashOpt.isPresent()) {
            bits |= 0b01;
            nnodes++;
        }

        if (rightHashOpt.isPresent()) {
            bits |= 0b10;
            nnodes++;
        }

        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_HEADER_LENGTH  + (lshared > 0 ? lencoded: 0)
                + nnodes * Keccak256Helper.DEFAULT_SIZE_BYTES
                + (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES : lvalue.intValue()));

        buffer.put((byte) ARITY);

        byte flags = 0;

        if (isSecure) {
            flags |= 1;
        }

        if (hasLongVal) {
            flags |= 2;
        }

        buffer.put(flags);
        buffer.putShort((short) bits);
        buffer.putShort((short) lshared);

        if (lshared > 0) {
            buffer.put(this.sharedPath.encode());
        }

        leftHashOpt.ifPresent(h -> buffer.put(h.getBytes()));

        rightHashOpt.ifPresent(h -> buffer.put(h.getBytes()));

        if (lvalue.compareTo(Uint24.ZERO) > 0) {
            if (hasLongVal) {
                buffer.put(this.getValueHash().getBytes());
            }
            else {
                buffer.put(this.getValue());
            }
        }

        return buffer.array();
    }

    // This method should only be called DURING save(). It should not be called in other places
    // because it will expand the node encoding in a memory cache that is ONLY removed after save()
    public boolean isEmbeddable() {
        return isTerminal() && getMessageLength() <= MAX_EMBEDDED_NODE_SIZE_IN_BYTES;
    }

    // key is the key with exactly collectKeyLen bytes.
    // in non-expanded form (binary)
    // special value Integer.MAX_VALUE means collect them all.
    private void collectKeys(Set<ByteArrayWrapper> set, TrieKeySlice key, int collectKeyLen) {
        if (collectKeyLen != Integer.MAX_VALUE && key.length() > collectKeyLen) {
            return;
        }

        boolean shouldCollect = collectKeyLen == Integer.MAX_VALUE || key.length() == collectKeyLen;
        if (valueLength.compareTo(Uint24.ZERO) > 0 && shouldCollect) {
            // convert bit string into byte[]
            set.add(new ByteArrayWrapper(key.encode()));
        }

        for (byte k = 0; k < ARITY; k++) {
            Trie node = this.retrieveNode(k);

            if (node == null) {
                continue;
            }

            TrieKeySlice nodeKey = key.rebuildSharedPath(k, node.getSharedPath());
            node.collectKeys(set, nodeKey, collectKeyLen);
        }
    }

    // Special value Integer.MAX_VALUE means collect them all.
    public Set<ByteArrayWrapper> collectKeys(int byteSize) {
        Set<ByteArrayWrapper> set = new HashSet<>();

        int bitSize;
        if (byteSize == Integer.MAX_VALUE) {
            bitSize = Integer.MAX_VALUE;
        } else {
            bitSize = byteSize * 8;
        }

        collectKeys(set, getSharedPath(), bitSize);
        return set;
    }

    /**
     * trieSize returns the number of nodes in trie
     *
     * @return the number of tries nodes, includes the current one
     */
    public int trieSize() {
        return 1 + this.left.getNode().map(Trie::trieSize).orElse(0)
                + this.right.getNode().map(Trie::trieSize).orElse(0);
    }

    /**
     * get retrieves the associated value given the key
     *
     * @param key   full key
     * @return the associated value, null if the key is not found
     *
     */
    @Nullable
    public Trie find(byte[] key) {
        return find(TrieKeySlice.fromKey(key));
    }

    @Nullable
    private Trie find(TrieKeySlice key) {
        if (sharedPath.length() > key.length()) {
            return null;
        }

        int commonPathLength = key.commonPath(sharedPath).length();
        if (commonPathLength < sharedPath.length()) {
            return null;
        }

        if (commonPathLength == key.length()) {
            return this;
        }

        Trie node = this.retrieveNode(key.get(commonPathLength));
        if (node == null) {
            return null;
        }

        return node.find(key.slice(commonPathLength + 1, key.length()));
    }

    private void internalToMessage() {
        Uint24 lvalue = this.valueLength;
        boolean hasLongVal = this.hasLongValue();
        TrieSerializer trieSerializer = new RSKIP107Serializer();

        if(lastRentPaidTimestamp != NO_RENT_TIMESTAMP) {
            trieSerializer = new RSKIP240Serializer();
        }

        SharedPathSerializer sharedPathSerializer = new SharedPathSerializer(this.sharedPath);
        VarInt childrenSize = getChildrenSize();

        ByteBuffer buffer = ByteBuffer.allocate(
                1 + // flags
                trieSerializer.lastPaidRentTimestampSize() + // it depends on whether it is timestamped or not
                        sharedPathSerializer.serializedLength() +
                        this.left.serializedLength() +
                        this.right.serializedLength() +
                        (this.isTerminal() ? 0 : childrenSize.getSizeInBytes()) +
                        (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES + Uint24.BYTES : lvalue.intValue())
        );

        // nodeVersion: 2 bits indicate serialization version (bits 6,7). Currently 01 (bit 6=1).
        byte flags = trieSerializer.trieVersion();

        // hasLongValue: 1 bit indicate if value length > 32 bytes (bit 5)
        if (hasLongVal) {
            flags = (byte) (flags | 0b00100000);
        }

        // sharedPrefixPresent: 1 bit indicates if there is any prefix (bit 4)
        if (sharedPathSerializer.isPresent()) {
            flags = (byte) (flags | 0b00010000);
        }

        // nodePresent: 2 bits indicate left/right embedded node (bit 2 = left, bit 3 = right)
        if (!this.left.isEmpty()) {
            flags = (byte) (flags | 0b00001000);
        }

        // nodePresent: 2 bits indicate left/right embedded node (bit 2 = left, bit 3 = right)
        if (!this.right.isEmpty()) {
            flags = (byte) (flags | 0b00000100);
        }

        // nodeIsEmbedded: 2 bits indicate left/right node presence (bit 0 = left, bit 1=right)
        if (this.left.isEmbeddable()) {
            flags = (byte) (flags | 0b00000010);
        }

        // nodeIsEmbedded: 2 bits indicate left/right node presence (bit 0 = left, bit 1=right)
        if (this.right.isEmbeddable()) {
            flags = (byte) (flags | 0b00000001);
        }

        buffer.put(flags);

        trieSerializer.serializeLastRentPaidTimestamp(buffer, lastRentPaidTimestamp);

        sharedPathSerializer.serializeInto(buffer);

        this.left.serializeInto(buffer);

        this.right.serializeInto(buffer);

        if (!this.isTerminal()) {
            buffer.put(childrenSize.encode());
        }

        if (hasLongVal) {
            buffer.put(this.getValueHash().getBytes());
            buffer.put(lvalue.encode());
        } else if (lvalue.compareTo(Uint24.ZERO) > 0) {
            buffer.put(this.getValue());
        }

        encoded = buffer.array();
    }

    private Trie retrieveNode(byte implicitByte) {
        return getNodeReference(implicitByte).getNode().orElse(null);
    }

    public NodeReference getNodeReference(byte implicitByte) {
        return implicitByte == 0 ? this.left : this.right;
    }

    public NodeReference getLeft() {
        return left;
    }

    public NodeReference getRight() {
        return right;
    }

    /**
     * put key with associated value, returning a new NewTrie
     *
     * @param key   key to be updated
     * @param value     associated value
     *
     * @return the new NewTrie containing the tree with the new key value association
     */
    private Trie put(TrieKeySlice key, byte[] value, boolean isRecursiveDelete) {
        // First of all, setting the value as an empty byte array is equivalent
        // to removing the key/value. This is because other parts of the trie make
        // this equivalent. Use always null to mark a node for deletion.
        if (value != null && value.length == 0) {
            value = null;
        }

        Trie trie = this.internalPut(key, value, isRecursiveDelete);

        // the following code coalesces nodes if needed for delete operation

        // it's null or it is not a delete operation
        if (trie == null || value != null) {
            return trie;
        }

        // todo(techdebt) this doesn't make sense: internalPut returns null for empty tries
        if (trie.isEmptyTrie()) {
            return null;
        }

        // only coalesce if node has only one child and no value
        if (trie.valueLength.compareTo(Uint24.ZERO) > 0) {
            return trie;
        }

        // both left and right exist (or not) at the same time
        if (trie.left.isEmpty() == trie.right.isEmpty()) {
            return trie;
        }

        Trie child;
        byte childImplicitByte;
        if (!trie.left.isEmpty()) {
            child = trie.left.getNode().orElse(null);
            childImplicitByte = (byte) 0;
        } else { // has right node
            child = trie.right.getNode().orElse(null);
            childImplicitByte = (byte) 1;
        }

        // could not retrieve from database
        if (child == null) {
            logger.error("Broken database, execution can't continue");
            nodeStopper.stop(1);
            return trie;
        }

        TrieKeySlice newSharedPath = trie.sharedPath.rebuildSharedPath(childImplicitByte, child.sharedPath);

        return new Trie(child.store, newSharedPath, child.value, child.left, child.right, child.valueLength,
                child.valueHash, child.childrenSize, child.lastRentPaidTimestamp); // todo(fedejinich) is this ok?
    }

    private static Uint24 getDataLength(byte[] value) {
        if (value == null) {
            return Uint24.ZERO;
        }

        return new Uint24(value.length);
    }

    private Trie internalPut(TrieKeySlice key, byte[] value, boolean isRecursiveDelete) {
        TrieKeySlice commonPath = key.commonPath(sharedPath);
        if (commonPath.length() < sharedPath.length()) {
            // when we are removing a key we know splitting is not necessary. the key wasn't found at this point.
            if (value == null) {
                return this;
            }

            return this.split(commonPath).put(key, value, isRecursiveDelete);
        }

        // todo(fedejinich) already exising key
        //  probably the part that we need for updatingRent
        if (sharedPath.length() >= key.length()) {
            // To compare values we need to retrieve the previous value
            // if not already done so. We could also compare by hash, to avoid retrieval
            // We do a small optimization here: if sizes are not equal, then values
            // obviously are not.
            if (this.valueLength.equals(getDataLength(value)) && Arrays.equals(this.getValue(), value)) {
                return this;
            }

            if (isRecursiveDelete) {
                // todo(fedejinich) a recursive delete will also clear the rent timestamp
                return new Trie(this.store, this.sharedPath, null, NO_RENT_TIMESTAMP);
            }

            if (isEmptyTrie(getDataLength(value), this.left, this.right)) {
                return null;
            }

            // todo(fedejinich) updates the already existing key
            return new Trie(
                    this.store,
                    this.sharedPath,
                    cloneArray(value),
                    this.left,
                    this.right,
                    getDataLength(value),
                    null,
                    this.childrenSize,
                    this.lastRentPaidTimestamp); // todo(fedejinich) is this ok?
        }

        if (isEmptyTrie()) {
            // todo(fedejinich) is this ok? i think we should set the existing timestamp
            //   or should we initialize with NO_RENT_TIMESTAMP?
            // new trie leaf
            return new Trie(this.store, key, cloneArray(value), this.lastRentPaidTimestamp);
//            return new Trie(this.store, key, cloneArray(value), NO_RENT_TIMESTAMP);
        }

        // this bit will be implicit and not present in a shared path
        byte pos = key.get(sharedPath.length());

        Trie node = retrieveNode(pos);
        if (node == null) {
            node = new Trie(this.store);
        }

        TrieKeySlice subKey = key.slice(sharedPath.length() + 1, key.length());
        Trie newNode = node.put(subKey, value, isRecursiveDelete);

        // reference equality
        if (newNode == node) {
            return this;
        }

        VarInt childrenSize = this.childrenSize;

        NodeReference newNodeReference = new NodeReference(this.store, newNode, null);
        NodeReference newLeft;
        NodeReference newRight;
        if (pos == 0) {
            newLeft = newNodeReference;
            newRight = this.right;

            if (childrenSize != null) {
                childrenSize = new VarInt(childrenSize.value - this.left.referenceSize() + newLeft.referenceSize());
            }
        } else {
            newLeft = this.left;
            newRight = newNodeReference;

            if (childrenSize != null) {
                childrenSize = new VarInt(childrenSize.value - this.right.referenceSize() + newRight.referenceSize());
            }
        }

        if (isEmptyTrie(this.valueLength, newLeft, newRight)) {
            return null;
        }

        // todo(fedejinich) going up from the already updated/created key, updates the new left or right
        return new Trie(this.store, this.sharedPath, this.value, newLeft, newRight,
                this.valueLength, this.valueHash, childrenSize, this.lastRentPaidTimestamp); // todo(fedejinich) is this ok?
    }

    private Trie split(TrieKeySlice commonPath) {
        int commonPathLength = commonPath.length();
        TrieKeySlice newChildSharedPath = sharedPath.slice(commonPathLength + 1, sharedPath.length());
        Trie newChildTrie = new Trie(this.store, newChildSharedPath, this.value, this.left, this.right, this.valueLength, this.valueHash, this.childrenSize, this.lastRentPaidTimestamp); // todo(fedejinich) is this ok?
        NodeReference newChildReference = new NodeReference(this.store, newChildTrie, null);

        // this bit will be implicit and not present in a shared path
        byte pos = sharedPath.get(commonPathLength);

        VarInt childrenSize = new VarInt(newChildReference.referenceSize());
        NodeReference newLeft;
        NodeReference newRight;
        if (pos == 0) {
            newLeft = newChildReference;
            newRight = NodeReference.empty();
        } else {
            newLeft = NodeReference.empty();
            newRight = newChildReference;
        }

        return new Trie(this.store, commonPath, null, newLeft, newRight, Uint24.ZERO, null, childrenSize, this.lastRentPaidTimestamp); // todo(fedejinich) is this ok?
    }

    public boolean isTerminal() {
        return this.left.isEmpty() && this.right.isEmpty();
    }

    public boolean isEmptyTrie() {
        return isEmptyTrie(this.valueLength, this.left, this.right);
    }

    /**
     * isEmptyTrie checks the existence of subnodes, subnodes hashes or value
     *
     * @param valueLength     length of current value
     * @param left      a reference to the left node
     * @param right     a reference to the right node
     *
     * @return true if no data
     */
    private static boolean isEmptyTrie(Uint24 valueLength, NodeReference left, NodeReference right) {
        if (valueLength.compareTo(Uint24.ZERO) > 0) {
            return false;
        }

        return left.isEmpty() && right.isEmpty();
    }

    public boolean hasLongValue() {
        return this.valueLength.compareTo(new Uint24(32)) > 0;
    }

    public Uint24 getValueLength() {
        return this.valueLength;
    }

    public Keccak256 getValueHash() {
        // For empty values (valueLength==0) we return the null hash because
        // in this trie empty arrays cannot be stored.
        if (valueHash == null && valueLength.compareTo(Uint24.ZERO) > 0) {
            valueHash = new Keccak256(Keccak256Helper.keccak256(getValue()));
        }

        return valueHash;
    }

    public byte[] getValue() {
        if (value == null && valueLength.compareTo(Uint24.ZERO) > 0) {
            value = retrieveLongValue();
            checkValueLengthAfterRetrieve();
        }

        return cloneArray(value);
    }

    /**
     * @return the tree size in bytes as specified in RSKIP107
     *
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * It shouldn't be called from outside. It's still public for NodeReference call
     *
     */
    public VarInt getChildrenSize() {
        if (childrenSize == null) {
            if (isTerminal()) {
                childrenSize = new VarInt(0);
            } else {
                childrenSize = new VarInt(this.left.referenceSize() + this.right.referenceSize());
            }
        }

        return childrenSize;
    }

    private byte[] retrieveLongValue() {
        return store.retrieveValue(getValueHash().getBytes());
    }

    private void checkValueLengthAfterRetrieve() {
        // At this time value==null and value.length!=null is really bad.
        if (value == null && valueLength.compareTo(Uint24.ZERO) > 0) {
            // Serious DB inconsistency here
            throw new IllegalArgumentException(INVALID_VALUE_LENGTH);
        }

        checkValueLength();
    }

    private void checkValueLength() {
        if (value != null && value.length != valueLength.intValue()) {
            // Serious DB inconsistency here
            throw new IllegalArgumentException(INVALID_VALUE_LENGTH);
        }

        if (value == null && valueLength.compareTo(Uint24.ZERO) > 0 && valueHash == null) {
            // Serious DB inconsistency here
            throw new IllegalArgumentException(INVALID_VALUE_LENGTH);
        }
    }

    public TrieKeySlice getSharedPath() {
        return sharedPath;
    }

    public Iterator<IterationElement> getInOrderIterator() {
        return new InOrderIterator(this);
    }

    public Iterator<IterationElement> getPreOrderIterator() {
        return new PreOrderIterator(this,false);
    }

     /**
     * Returns a Pre-order iterator
     *
     * @param stopAtAccountDepth     if true, only nodes up to the account level will ve traversed
     *                               excluding contract storage and contract code nodes.
     *
     * @return iterator
     */
    public Iterator<IterationElement> getPreOrderIterator(boolean stopAtAccountDepth) {
        return new PreOrderIterator(this,stopAtAccountDepth);
    }

    private static byte[] cloneArray(byte[] array) {
        return array == null ? null : Arrays.copyOf(array, array.length);
    }

    public Iterator<IterationElement> getPostOrderIterator() {
        return new PostOrderIterator(this);
    }

    /**
     * makeEmpyHash creates the hash associated to empty nodes
     *
     * @return a hash with zeroed bytes
     */
    private static Keccak256 makeEmptyHash() {
        return new Keccak256(Keccak256Helper.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }

    private static Keccak256 readHash(byte[] bytes, int position) {
        int keccakSize = Keccak256Helper.DEFAULT_SIZE_BYTES;
        if (bytes.length - position < keccakSize) {
            throw new IllegalArgumentException(String.format(
                    "Left message is too short for hash expected:%d actual:%d total:%d",
                    keccakSize, bytes.length - position, bytes.length
            ));
        }

        return new Keccak256(Arrays.copyOfRange(bytes, position, position + keccakSize));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getHash());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        Trie otherTrie = (Trie) other;
        return getHash().equals(otherTrie.getHash());
    }

    @Override
    public String toString() {
        String s = printParam("TRIE: ", "value", getValue());
        s = printParam(s, "hash0", left.getHash().orElse(null));
        s = printParam(s, "hash1", right.getHash().orElse(null));
        s = printParam(s, "hash", getHash());
        s = printParam(s, "valueHash", getValueHash());
        s = printParam(s, "encodedSharedPath", sharedPath.encode());
        s += "sharedPathLength: " + sharedPath.length() + "\n";
        return s;
    }

    private String printParam(String s, String name, byte[] param) {
        if (param != null) {
            s += name + ": " + ByteUtil.toHexString(param) + "\n";
        }
        return s;
    }

    private String printParam(String s, String name, Keccak256 param) {
        if (param != null) {
            s += name + ": " + param.toHexString() + "\n";
        }
        return s;
    }

    private static VarInt readVarInt(ByteBuffer message) {
        // read without touching the buffer position so when we read into bytes it contains the header
        int first = Byte.toUnsignedInt(message.get(message.position()));
        byte[] bytes;
        if (first < 253) {
            bytes = new byte[1];
        } else if (first == 253) {
            bytes = new byte[3];
        } else if (first == 254) {
            bytes = new byte[5];
        } else {
            bytes = new byte[9];
        }

        message.get(bytes);
        return new VarInt(bytes, 0);
    }

    // todo(fedejinich) need to use internalPut
    public Trie setLastRentPaidTimestamp(long lastPaidRentTimestamp) {
        return new Trie(this.store, this.sharedPath, this.value, this.left, this.right,
                this.valueLength, this.valueHash, this.childrenSize, lastPaidRentTimestamp);
    }

//     todo(fedejinich) we don't need any 'value', it will be removed and replaced by the already existing 'this.value'
    public Trie updateLastRentPaidTimestamp(TrieKeySlice key, long lastRentPaidTimestamp) {
//        todo(fedejinich) i'm not sure about this part yet, why would we split the trie for updates?
//        TrieKeySlice commonPath = key.commonPath(sharedPath);
//        if (commonPath.length() < sharedPath.length()) {
//            // when we are removing a key we know splitting is not necessary. the key wasn't found at this point.
//            if (value == null) {
//                return this;
//            }
//
//            return this.split(commonPath).put(key, value, isRecursiveDelete);
//        }

        // todo(fedejinich) already exising key
        //  probably the part that we need for updatingRent
        if (sharedPath.length() >= key.length()) {
            // To compare values we need to retrieve the previous value
            // if not already done so. We could also compare by hash, to avoid retrieval
            // We do a small optimization here: if sizes are not equal, then values
            // obviously are not.
            // todo(fedejinich) why would we need this? is it trying to update a value with the same value?
            //   for storage rent this should never happen
//            if (this.valueLength.equals(getDataLength(value)) && Arrays.equals(this.getValue(), value)) {
//                return this;
//            }

//            todo(fedejinich) no deletes for this operation
//            if (isRecursiveDelete) {
//                return new Trie(this.store, this.sharedPath, null, NO_RENT_TIMESTAMP);
//            }

//            todo(fedejinich) there are no cases to return a null value
//            if (isEmptyTrie(getDataLength(value), this.left, this.right)) {
//                return null;
//            }

            // todo(fedejinich) updates timestamp from the already existing key
            return new Trie(
                    this.store,
                    this.sharedPath,
                    cloneArray(this.value), // todo(fedejinich) should be 'this.value' because we are updating the timestamp
                    this.left,
                    this.right,
                    getDataLength(this.value),
                    null,
                    this.childrenSize,
                    lastRentPaidTimestamp); // todo(fedejinich) updates the rent timestamp
        }

//        todo(fedejinich) there won't be new trie leafs
//        if (isEmptyTrie()) {
//            return new Trie(this.store, key, cloneArray(value), this.lastRentPaidTimestamp);
//        }

        // todo(fedejinich) the following part goes down to the next child,
        //   and tries to update the timestamp (or put a new value for internalPut)

        // this bit will be implicit and not present in a shared path
        byte pos = key.get(sharedPath.length());

        Trie node = retrieveNode(pos);
        // todo(fedejinich) this should never happen, it will update timestamp from an already existing trie
//        if (node == null) {
//            node = new Trie(this.store);
//        }

        TrieKeySlice subKey = key.slice(sharedPath.length() + 1, key.length());
        Trie newNode = node.updateLastRentPaidTimestamp(subKey, lastRentPaidTimestamp);

        // reference equality
        if (newNode == node) {
            return this;
        }

        // todo(fedejinich) if there are new branches, it creates new nodes references,
        //  and updates child references (from the bottom up to the trie root)

        VarInt childrenSize = this.childrenSize;

        NodeReference newNodeReference = new NodeReference(this.store, newNode, null);

        // todo(techdebt) duplicated code, this should be handled by a method like 'newChildByPos' (same happens at internalPut)
        NodeReference newLeft;
        NodeReference newRight;
        if (pos == 0) {
            newLeft = newNodeReference;
            newRight = this.right;

            if (childrenSize != null) {
                childrenSize = new VarInt(childrenSize.value - this.left.referenceSize() + newLeft.referenceSize());
            }
        } else {
            newLeft = this.left;
            newRight = newNodeReference;

            if (childrenSize != null) {
                childrenSize = new VarInt(childrenSize.value - this.right.referenceSize() + newRight.referenceSize());
            }
        }
        // todo(techdebt) end of duplicated code

//        todo(fedejinich) there are no cases to return null value
//        if (isEmptyTrie(this.valueLength, newLeft, newRight)) {
//            return null;
//        }

        // goes up to the trie root with new updated left/right
        // todo(fedejinich) here we can update also the intermediate nodes by passing newNode.lastRentTimestamp instead of this.lastPaidRentTimestamp
        return new Trie(this.store, this.sharedPath, this.value, newLeft, newRight,
                this.valueLength, this.valueHash, childrenSize, this.lastRentPaidTimestamp); // todo(fedejinich) use the already existing timestamp
    }

    public long getLastRentPaidTimestamp() {
        return this.lastRentPaidTimestamp;
    }

    /**
     * Returns the leftmost node that has not yet been visited that node is normally on top of the stack
     */
    private static class InOrderIterator implements Iterator<IterationElement> {

        private final Deque<IterationElement> visiting;

        public InOrderIterator(Trie root) {
            Objects.requireNonNull(root);
            TrieKeySlice traversedPath = root.getSharedPath();
            this.visiting = new LinkedList<>();
            // find the leftmost node, pushing all the intermediate nodes onto the visiting stack
            visiting.push(new IterationElement(traversedPath, root));
            pushLeftmostNode(traversedPath, root);
            // now the leftmost unvisited node is on top of the visiting stack
        }

        /**
         * return the leftmost node that has not yet been visited that node is normally on top of the stack
         */
        @Override
        @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#pop()} when it's empty
        public IterationElement next() {
            IterationElement visitingElement = visiting.pop();
            Trie node = visitingElement.getNode();
            // if the node has a right child, its leftmost node is next
            Trie rightNode = node.retrieveNode((byte) 0x01);
            if (rightNode != null) {
                TrieKeySlice rightNodeKey = visitingElement.getNodeKey().rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
                visiting.push(new IterationElement(rightNodeKey, rightNode)); // push the right node
                // find the leftmost node of the right child
                pushLeftmostNode(rightNodeKey, rightNode);
                // note "node" has been replaced on the stack by its right child
            } // else: no right subtree, go back up the stack
            // next node on stack will be next returned
            return visitingElement;
        }

        @Override
        public boolean hasNext() {
            return !visiting.isEmpty(); // no next node left
        }

        /**
         * Find the leftmost node from this root, pushing all the intermediate nodes onto the visiting stack
         *
         * @param nodeKey
         * @param node the root of the subtree for which we are trying to reach the leftmost node
         */
        private void pushLeftmostNode(TrieKeySlice nodeKey, Trie node) {
            // find the leftmost node
            Trie leftNode = node.retrieveNode((byte) 0x00);
            if (leftNode != null) {
                TrieKeySlice leftNodeKey = nodeKey.rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
                visiting.push(new IterationElement(leftNodeKey, leftNode)); // push the left node
                pushLeftmostNode(leftNodeKey, leftNode); // recurse on next left node
            }
        }
    }

    private class PreOrderIterator implements Iterator<IterationElement> {

        private final Deque<IterationElement> visiting;
        private boolean stopAtAccountDepth;

        public PreOrderIterator(Trie root,boolean stopAtAccountDepth) {
            Objects.requireNonNull(root);
            this.stopAtAccountDepth = stopAtAccountDepth;
            TrieKeySlice traversedPath = root.getSharedPath();
            this.visiting = new LinkedList<>();
            this.visiting.push(new IterationElement(traversedPath, root));
        }

        @Override
        @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#pop()} when it's empty
        public IterationElement next() {
            IterationElement visitingElement = visiting.pop();
            Trie node = visitingElement.getNode();
            TrieKeySlice nodeKey = visitingElement.getNodeKey();

            int nodeKeyLength = nodeKey.length();
            // If we're stoping at accounts, do not add children
            if ((!stopAtAccountDepth) || (nodeKeyLength < (1 + TrieKeyMapper.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE))
            {
                // need to visit the left subtree first, then the right since a stack is a LIFO, push the right subtree first,
                // then the left
                Trie rightNode = node.retrieveNode((byte) 0x01);
                if (rightNode != null) {
                    TrieKeySlice rightNodeKey = nodeKey.rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
                    visiting.push(new IterationElement(rightNodeKey, rightNode));
                }
                Trie leftNode = node.retrieveNode((byte) 0x00);
                if (leftNode != null) {
                    TrieKeySlice leftNodeKey = nodeKey.rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
                    visiting.push(new IterationElement(leftNodeKey, leftNode));
                }
            }
            // may not have pushed anything.  If so, we are at the end
            return visitingElement;
        }

        @Override
        public boolean hasNext() {
            return !visiting.isEmpty(); // no next node left
        }
    }

    private class PostOrderIterator implements Iterator<IterationElement> {

        private final Deque<IterationElement> visiting;
        private final Deque<Boolean> visitingRightChild;

        public PostOrderIterator(Trie root) {
            Objects.requireNonNull(root);
            TrieKeySlice traversedPath = root.getSharedPath();
            this.visiting = new LinkedList<>();
            this.visitingRightChild = new LinkedList<>();
            // find the leftmost node, pushing all the intermediate nodes onto the visiting stack
            visiting.push(new IterationElement(traversedPath, root));
            visitingRightChild.push(Boolean.FALSE);
            pushLeftmostNodeRecord(traversedPath, root);
            // the node on top of the visiting stack is the next one to be visited, unless it has a right subtree
        }

        @Override
        public boolean hasNext() {
            return !visiting.isEmpty(); // no next node left
        }

        @Override
        @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#element()} when it's empty
        public IterationElement next() {
            IterationElement visitingElement = visiting.element();
            Trie node = visitingElement.getNode();
            Trie rightNode = node.retrieveNode((byte) 0x01);
            if (rightNode == null || visitingRightChild.peek()) { // no right subtree, or right subtree already visited
                // already visited right child, time to visit the node on top
                visiting.removeFirst(); // it was already picked
                visitingRightChild.removeFirst(); // it was already picked
                return visitingElement;
            } else { // now visit this node's right subtree
                // mark that we're visiting this element's right subtree
                visitingRightChild.removeFirst();
                visitingRightChild.push(Boolean.TRUE);

                TrieKeySlice rightNodeKey = visitingElement.getNodeKey().rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
                visiting.push(new IterationElement(rightNodeKey, rightNode)); // push the right node
                visitingRightChild.push(Boolean.FALSE); // we're visiting the left subtree of the right node

                // now push everything down to the leftmost node in the right subtree
                pushLeftmostNodeRecord(rightNodeKey, rightNode);
                return next(); // use recursive call to visit that node
            }
        }

        /**
         * Find the leftmost node from this root, pushing all the intermediate nodes onto the visiting stack
         * and also stating that each is a left child of its parent
         * @param nodeKey
         * @param node the root of the subtree for which we are trying to reach the leftmost node
         */
        private void pushLeftmostNodeRecord(TrieKeySlice nodeKey, Trie node) {
            // find the leftmost node
            Trie leftNode = node.retrieveNode((byte) 0x00);
            if (leftNode != null) {
                TrieKeySlice leftNodeKey = nodeKey.rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
                visiting.push(new IterationElement(leftNodeKey, leftNode)); // push the left node
                visitingRightChild.push(Boolean.FALSE); // record that it is on the left
                pushLeftmostNodeRecord(leftNodeKey, leftNode); // continue looping
            }
        }
    }

    public static class IterationElement {
        private final TrieKeySlice nodeKey;
        private final Trie node;

        public IterationElement(final TrieKeySlice nodeKey, final Trie node) {
            this.nodeKey = nodeKey;
            this.node = node;
        }

        public Trie getNode() {
            return node;
        }

        public final TrieKeySlice getNodeKey() {
            return nodeKey;
        }

        public String toString() {
            byte[] encodedFullKey = nodeKey.encode();
            StringBuilder ouput = new StringBuilder();
            for (byte b : encodedFullKey) {
                ouput.append( b == 0 ? '0': '1');
            }
            return ouput.toString();
        }
    }

    private static class SharedPathSerializer {
        private final TrieKeySlice sharedPath;
        private final int lshared;

        private SharedPathSerializer(TrieKeySlice sharedPath) {
            this.sharedPath = sharedPath;
            this.lshared = this.sharedPath.length();
        }

        private int serializedLength() {
            if (!isPresent()) {
                return 0;
            }

            return lsharedSize() + PathEncoder.calculateEncodedLength(lshared);
        }

        public boolean isPresent() {
            return lshared > 0;
        }

        private void serializeInto(ByteBuffer buffer) {
            if (!isPresent()) {
                return;
            }

            if (1 <= lshared && lshared <= 32) {
                // first byte in [0..31]
                buffer.put((byte) (lshared - 1));
            } else if (160 <= lshared && lshared <= 382) {
                // first byte in [32..254]
                buffer.put((byte) (lshared - 128));
            } else {
                buffer.put((byte) 255);
                buffer.put(new VarInt(lshared).encode());
            }

            buffer.put(this.sharedPath.encode());
        }

        private int lsharedSize() {
            if (!isPresent()) {
                return 0;
            }

            if (1 <= lshared && lshared <= 32) {
                return 1;
            }

            if (160 <= lshared && lshared <= 382) {
                return 1;
            }

            return 1 + VarInt.sizeOf(lshared);
        }

        private static TrieKeySlice deserialize(ByteBuffer message, boolean sharedPrefixPresent) {
            if (!sharedPrefixPresent) {
                return TrieKeySlice.empty();
            }

            int lshared;
            // upgrade to int so we can compare positive values
            int lsharedFirstByte = Byte.toUnsignedInt(message.get());
            if (0 <= lsharedFirstByte && lsharedFirstByte <= 31) {
                // lshared in [1..32]
                lshared = lsharedFirstByte + 1;
            } else if (32 <= lsharedFirstByte && lsharedFirstByte <= 254) {
                // lshared in [160..382]
                lshared = lsharedFirstByte + 128;
            } else {
                lshared = (int) readVarInt(message).value;
            }

            int lencoded = PathEncoder.calculateEncodedLength(lshared);
            byte[] encodedKey = new byte[lencoded];
            message.get(encodedKey);
            return TrieKeySlice.fromEncoded(encodedKey, 0, lshared, lencoded);
        }
    }

    // Additional auxiliary methods for Merkle Proof

    @Nullable
    public List<Trie> getNodes(byte[] key) {
        return findNodes(key);
    }

    @Nullable
    public List<Trie> getNodes(String key) {
        return this.getNodes(key.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private List<Trie> findNodes(byte[] key) {
        return findNodes(TrieKeySlice.fromKey(key));
    }

    @Nullable
    private List<Trie> findNodes(TrieKeySlice key) {
        if (sharedPath.length() > key.length()) {
            return null;
        }

        int commonPathLength = key.commonPath(sharedPath).length();

        if (commonPathLength < sharedPath.length()) {
            return null;
        }

        if (commonPathLength == key.length()) {
            List<Trie> nodes = new ArrayList<>();
            nodes.add(this);
            return nodes;
        }

        Trie node = this.retrieveNode(key.get(commonPathLength));

        if (node == null) {
            return null;
        }

        List<Trie> subnodes = node.findNodes(key.slice(commonPathLength + 1, key.length()));

        if (subnodes == null) {
            return null;
        }

        subnodes.add(this);

        return subnodes;
    }

    public boolean wasSaved() {
        return this.saved;
    }

    public Trie markAsSaved() {
        this.saved = true;
        return this;
    }
}
