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

import co.rsk.crypto.Keccak256;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.panic.PanicProcessor;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
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
    private static final int ARITY = 2;

    private static final Logger logger = LoggerFactory.getLogger("newtrie");
    private static final Profiler profiler = ProfilerFactory.getInstance();
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final String PANIC_TOPIC = "newtrie";
    private static final String INVALID_ARITY = "Invalid arity";
    private static final String ERROR_NON_EXISTENT_TRIE = "Error non existent trie with hash %s";

    private static final int MESSAGE_HEADER_LENGTH = 2 + Short.BYTES * 2;
    private static final int LAST_BYTE_ONLY_MASK = 0x000000ff;
    private static final String INVALID_VALUE_LENGTH = "Invalid value length";

    // all zeroed, default hash for empty nodes
    private static Keccak256 emptyHash = makeEmptyHash();

    // this node associated value, if any
    private byte[] value;

    private final NodeReference left;

    private final NodeReference right;

    // this node hash value
    private Keccak256 hash;

    // it is saved to store
    private boolean saved;

    // valueLength enables lazy long value retrieval.
    // The length of the data is now stored. This allows EXTCODESIZE to
    // execute much faster without the need to actually retrieve the data.
    // if valueLength>32 and value==null this means the value has not been retrieved yet.
    // if valueLength==0, then there is no value AND no node.
    // This trie structure does not distinguish between empty arrays
    // and nulls. Storing an empty byte array has the same effect as removing the node.
    //
    private int valueLength;

    // For lazy retrieval and also for cache.
    private byte[] valueHash;

    // sha3 is applied to keys
    private final boolean isSecure;

    // associated store, to store or retrieve nodes in the trie
    private TrieStore store;

    // shared Path
    private final TrieKeySlice sharedPath;

    // default constructor, no secure
    public Trie() {
        this(false);
    }

    public Trie(boolean isSecure) {
        this(null, isSecure);
    }

    public Trie(TrieStore store, boolean isSecure) {
        this(store, TrieKeySlice.empty(), null, isSecure);
    }

    private Trie(TrieStore store, TrieKeySlice sharedPath, byte[] value, boolean isSecure) {
        this(store, sharedPath, value, isSecure, value == null ? 0 : value.length, null);
    }

    private Trie(TrieStore store, TrieKeySlice sharedPath, byte[] value, boolean isSecure, int valueLength, byte[] valueHash) {
        this(sharedPath, value, NodeReference.empty(), NodeReference.empty(), store, valueLength, valueHash, isSecure);
    }

    // full constructor
    public Trie(TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, TrieStore store, int valueLength, byte[] valueHash, boolean isSecure) {
        this.value = value;
        this.left = left;
        this.right = right;
        this.store = store;
        this.sharedPath = sharedPath;
        this.valueLength = valueLength;
        this.valueHash = valueHash;
        this.isSecure = isSecure;
    }

    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Pool method, to create a NewTrie from a serialized message
     * the store argument is used to retrieve any subnode
     * of the subnode
     *
     * @param message   the content (arity, hashes, value) serializaced as byte array
     * @param store     the store containing the rest of the trie nodes, to be used on retrieve them if they are needed in memory
     */
    public static Trie fromMessage(byte[] message, TrieStore store) {
        if (message == null) {
            return null;
        }

        return fromMessage(message, 0, message.length, store);
    }

    // this methods reads a short as dataInputStream + byteArrayInputStream
    private static Trie fromMessage(byte[] message, int position, int msglength, TrieStore store) {
        if (message == null) {
            return null;
        }

        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BUILD_TRIE_FROM_MSG);
        int current = position;
        int arity = message[current];
        current += Byte.BYTES;

        if (arity != ARITY) {
            profiler.stop(metric);
            throw new IllegalArgumentException(INVALID_ARITY);
        }

        int flags = message[current];
        current += Byte.BYTES;

        boolean isSecure = (flags & 0x01) == 1;
        boolean hasLongVal = (flags & 0x02) == 2;
        int bhashes = readShort(message, current);
        current += Short.BYTES;
        int lshared = readShort(message, current);
        current += Short.BYTES;

        TrieKeySlice sharedPath = TrieKeySlice.empty();
        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        if (lencoded > 0) {
            if (message.length - current < lencoded) {
                profiler.stop(metric);
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
        byte[] value = null;
        int lvalue;
        byte[] valueHash = null;

        if (hasLongVal) {
            valueHash = readHash(message, current).getBytes();
            // current += keccakSize; current position is not more needed

            value = store.retrieveValue(valueHash);
            lvalue = value.length;
        } else {
            lvalue = msglength - offset;
            if (lvalue > 0) {
                if (message.length - current  < lvalue) {
                    profiler.stop(metric);
                    throw new IllegalArgumentException(String.format(
                            "Left message is too short for value expected:%d actual:%d total:%d",
                            lvalue, message.length - current, message.length));
                }
                value = Arrays.copyOfRange(message, current, current + lvalue);
                //current += lvalue; current position is not more needed
            }
        }

        // it doesn't need to clone value since it's retrieved from store or created from message
        Trie trie = new Trie(sharedPath, value, left, right, store, lvalue, valueHash, isSecure);

        if (store != null) {
            trie.saved = true;
        }

        profiler.stop(metric);
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
            return emptyHash.copy();
        }

        byte[] message = this.toMessage();

        this.hash = new Keccak256(Keccak256Helper.keccak256(message));

        return this.hash.copy();
    }

    /**
     * get returns the value associated with a key
     *
     * @param key the key associated with the value, a byte array (variable length)
     *
     * @return  the associated value, a byte array, or null if there is no associated value to the key
     */
    public byte[] get(byte[] key) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.TRIE_GET_VALUE_FROM_KEY);
        Trie node = find(key);
        if (node == null) {
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
        Trie trie = put(keySlice, value);

        return trie == null ? new Trie(this.store, this.isSecure) : trie;
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
        /* Something Angel must do here !*/
        /* Something Angel must do here !*/
        /* Something Angel must do here !*/
        /* Something Angel must do here !*/
        /* Something Angel must do here !*/
        // do a node delete just to pass tests
        return delete(key);
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
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.TRIE_TO_MESSAGE);
        int lvalue = this.valueLength;
        int lshared = this.sharedPath.length();
        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        boolean hasLongVal = this.hasLongValue();
        Optional<Keccak256> leftHashOpt = this.left.getHash();
        Optional<Keccak256> rightHashOpt = this.right.getHash();

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

        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_HEADER_LENGTH  + (lshared > 0 ? lencoded: 0) // TODO(sergio): check if lencoded is 0 when lshared is zero
                + nnodes * Keccak256Helper.DEFAULT_SIZE_BYTES
                + (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES : lvalue)); //TODO(sergio) check lvalue == 0 case

        buffer.put((byte) ARITY);

        byte flags = 0;

        if (this.isSecure) {
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

        if (lvalue > 0) {
            if (hasLongVal) {
                buffer.put(this.getValueHash());
            }
            else {
                buffer.put(this.value);
            }
        }

        profiler.stop(metric); //buffer.array() is negligible
        return buffer.array();
    }

    /**
     * sends all data to disk. This applies to the store and all keys saved into the
     * store, not only to this node.
     */
    public void flush() {
        if (this.store==null) {
            return;
        }
        this.store.flush();
    }

    /**
     * save saves the unsaved current trie and subnodes to their associated store
     *
     */
    public void save() {
        if (this.saved) {
            return;
        }

        // Without store, nodes cannot be saved. Abort silently
        if (this.store == null) {
            return;
        }

        this.left.save();
        this.right.save();

        this.store.save(this);
        this.saved = true;
    }

    // key is the key with exactly collectKeyLen bytes.
    // in non-expanded form (binary)
    // special value Integer.MAX_VALUE means collect them all.
    private void collectKeys(Set<ByteArrayWrapper> set, TrieKeySlice key, int collectKeyLen) {
        if (collectKeyLen != Integer.MAX_VALUE && key.length() > collectKeyLen) {
            return;
        }

        if (valueLength != 0) {
            if (collectKeyLen == Integer.MAX_VALUE || key.length() == collectKeyLen) {
                // convert bit string into byte[]
                set.add(new ByteArrayWrapper(key.encode()));
            }
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

    public Set<ByteArrayWrapper> collectKeysFrom(byte[] key) {
        Set<ByteArrayWrapper> set = new HashSet<>();
        TrieKeySlice parentKey = TrieKeySlice.fromKey(key);
        Trie parent = find(parentKey);
        if (parent != null) {
            parent.collectKeys(set, parentKey, Integer.MAX_VALUE);
        }
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

    private Trie retrieveNode(byte implicitByte) {
        return getNodeReference(implicitByte).getNode().orElse(null);
    }

    public NodeReference getNodeReference(byte implicitByte) {
        return implicitByte == 0 ? this.left : this.right;
    }

    private static Trie internalRetrieve(TrieStore store, byte[] root) {
        Trie newTrie = store.retrieve(root);

        if (newTrie == null) {
            String log = String.format(ERROR_NON_EXISTENT_TRIE, Hex.toHexString(root));
            logger.error(log);
            panicProcessor.panic(PANIC_TOPIC, log);
            throw new IllegalArgumentException(log);
        }

        return newTrie;
    }

    /**
     * put key with associated value, returning a new NewTrie
     *
     * @param key   key to be updated
     * @param value     associated value
     *
     * @return the new NewTrie containing the tree with the new key value association
     *
     */
    private Trie put(TrieKeySlice key, byte[] value) {
        // First of all, setting the value as an empty byte array is equivalent
        // to removing the key/value. This is because other parts of the trie make
        // this equivalent. Use always null to mark a node for deletion.
        if (value != null && value.length == 0) {
            value = null;
        }

        Trie trie = this.internalPut(key, value);

        // the following code coalesces nodes if needed for delete operation

        // it's null or it is not a delete operation
        if (trie == null || value != null) {
            return trie;
        }

        if (trie.isEmptyTrie()) {
            return null;
        }

        // only coalesce if node has only one child and no value
        if (trie.valueLength != 0) {
            return trie;
        }

        Optional<Trie> leftOpt = trie.left.getNode();
        Optional<Trie> rightOpt = trie.right.getNode();
        if (leftOpt.isPresent() && rightOpt.isPresent()) {
            return trie;
        }

        if (!leftOpt.isPresent() && !rightOpt.isPresent()) {
            return trie;
        }

        Trie child;
        byte childImplicitByte;
        if (leftOpt.isPresent()) {
            child = leftOpt.get();
            childImplicitByte = (byte) 0;
        } else { // has right node
            child = rightOpt.get();
            childImplicitByte = (byte) 1;
        }

        TrieKeySlice newSharedPath = trie.sharedPath.rebuildSharedPath(childImplicitByte, child.sharedPath);
        return new Trie(newSharedPath, child.value, child.left, child.right, child.store, child.valueLength, child.valueHash, child.isSecure);
    }

    private static int getDataLength(byte[] value) {
        if (value == null) {
            return 0;
        }

        return value.length;
    }

    private Trie internalPut(TrieKeySlice key, byte[] value) {
        TrieKeySlice commonPath = key.commonPath(sharedPath);
        if (commonPath.length() < sharedPath.length()) {
            // when we are removing a key we know splitting is not necessary. the key wasn't found at this point.
            if (value == null) {
                return this;
            }

            return this.split(commonPath).put(key, value);
        }

        if (sharedPath.length() >= key.length()) {
            // To compare values we need to retrieve the previous value
            // if not already done so. We could also compare by hash, to avoid retrieval
            // We do a small optimization here: if sizes are not equal, then values
            // obviously are not.
            if (this.valueLength == getDataLength(value)) {
                if (Arrays.equals(this.getValue(), value)) {
                    return this;
                }
            }

            if (isEmptyTrie(getDataLength(value), this.left, this.right)) {
                return null;
            }

            return new Trie(
                    this.sharedPath,
                    cloneArray(value),
                    this.left,
                    this.right,
                    this.store,
                    getDataLength(value),
                    null,
                    this.isSecure
            );
        }

        if (isEmptyTrie()) {
            return new Trie(
                    key,
                    cloneArray(value),
                    NodeReference.empty(),
                    NodeReference.empty(),
                    this.store,
                    getDataLength(value),
                    null,
                    this.isSecure
            );
        }

        // this bit will be implicit and not present in a shared path
        byte pos = key.get(sharedPath.length());

        Trie node = retrieveNode(pos);
        if (node == null) {
            node = new Trie(this.store, this.isSecure);
        }

        TrieKeySlice subKey = key.slice(sharedPath.length() + 1, key.length());
        Trie newNode = node.put(subKey, value);

        // reference equality
        if (newNode == node) {
            return this;
        }

        NodeReference newNodeReference = new NodeReference(this.store, newNode, null);
        NodeReference newLeft;
        NodeReference newRight;
        if (pos == 0) {
            newLeft = newNodeReference;
            newRight = this.right;
        } else {
            newLeft = this.left;
            newRight = newNodeReference;
        }

        if (isEmptyTrie(this.valueLength, newLeft, newRight)) {
            return null;
        }

        return new Trie(this.sharedPath, this.value, newLeft, newRight, this.store, this.valueLength, this.valueHash, this.isSecure);
    }

    private Trie split(TrieKeySlice commonPath) {
        int commonPathLength = commonPath.length();
        TrieKeySlice newChildSharedPath = sharedPath.slice(commonPathLength + 1, sharedPath.length());
        Trie newChildTrie = new Trie(newChildSharedPath, this.value, this.left, this.right, this.store, this.valueLength, this.valueHash, this.isSecure);
        NodeReference newChildReference = new NodeReference(this.store, newChildTrie, null);

        // this bit will be implicit and not present in a shared path
        byte pos = sharedPath.get(commonPathLength);

        NodeReference newLeft;
        NodeReference newRight;
        if (pos == 0) {
            newLeft = newChildReference;
            newRight = NodeReference.empty();
        } else {
            newLeft = NodeReference.empty();
            newRight = newChildReference;
        }

        return new Trie(commonPath, null, newLeft, newRight, this.store, 0, null, this.isSecure);
    }

    public boolean hasStore() {
        return this.store != null;
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
    private static boolean isEmptyTrie(int valueLength, NodeReference left, NodeReference right) {
        if (valueLength != 0) {
            return false;
        }

        return left.isEmpty() && right.isEmpty();
    }

    public Trie getSnapshotTo(Keccak256 hash) {
        if (emptyHash.equals(hash)) {
            return new Trie(this.store, this.isSecure);
        }

        // check if saved to only return this when we know it is in disk storage
        if (this.saved && getHash().equals(hash)) {
            return this;
        }

        return internalRetrieve(this.store, hash.getBytes());
    }

    public TrieStore getStore() {
        return this.store;
    }

    public boolean hasLongValue() {
        return this.valueLength > 32;
    }

    public int getValueLength() {
        return this.valueLength;
    }

    // Now getValueHash() returns the hash of the value independently
    // on it's size: use hasLongValue() to known what type of value it will store in
    // the node.
    public byte[] getValueHash() {
        if (valueHash == null) {
            if (this.valueLength != 0) {
                valueHash = Keccak256Helper.keccak256(this.value);
            }
            // For empty values (valueLength==0) we return the null hash because
            // in this trie empty arrays cannot be stored.
        }

        //TODO(lsebrie): return a keccak256 instance
        return cloneArray(valueHash);
    }

    public byte[] getValue() {
        if (valueLength != 0 && value == null) {
            this.value = retrieveLongValue();
            checkValueLengthAfterRetrieve();
        }

        return cloneArray(this.value);
    }

    private byte[] retrieveLongValue() {
        return store.retrieveValue(valueHash);
    }

    private void checkValueLengthAfterRetrieve() {
        // At this time value==null and value.length!=null is really bad.
        if (value == null && valueLength != 0) {
            // Serious DB inconsistency here
            throw new IllegalArgumentException(INVALID_VALUE_LENGTH);
        }

        checkValueLength();
    }

    private void checkValueLength() {
        if (value != null && value.length != valueLength) {
            // Serious DB inconsistency here
            throw new IllegalArgumentException(INVALID_VALUE_LENGTH);
        }

        if (value == null && valueLength != 0 && (valueHash == null || valueHash.length == 0)) {
            // Serious DB inconsistency here
            throw new IllegalArgumentException(INVALID_VALUE_LENGTH);
        }
    }

    public TrieKeySlice getSharedPath() {
        return sharedPath;
    }

    // These two functions are for converting the trie to the old format
    public byte[] getEncodedSharedPath() {
        if (getSharedPathLength() == 0) {
            return null;
        }

        return sharedPath.encode();
    }

    public int getSharedPathLength() {
        return sharedPath.length();
    }

    public Iterator<IterationElement> getInOrderIterator() {
        return new InOrderIterator(this);
    }

    public Iterator<IterationElement> getPreOrderIterator() {
        return new PreOrderIterator(this);
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

    private static short readShort(byte[] bytes, int position) {
        int ch1 = bytes[position] & LAST_BYTE_ONLY_MASK;
        int ch2 = bytes[position + 1] & LAST_BYTE_ONLY_MASK;
        if ((ch1 | ch2) < 0) {
            throw new IllegalArgumentException(
                    String.format("On position %d there are invalid bytes for a short value %s %s", position, ch1, ch2));
        }
        return (short)((ch1 << 8) + (ch2));
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

        public PreOrderIterator(Trie root) {
            Objects.requireNonNull(root);
            TrieKeySlice traversedPath = root.getSharedPath();
            this.visiting = new LinkedList<>();
            this.visiting.push(new IterationElement(traversedPath, root));
        }

        @Override
        public IterationElement next() {
            IterationElement visitingElement = visiting.pop();
            Trie node = visitingElement.getNode();
            TrieKeySlice nodeKey = visitingElement.getNodeKey();
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
        public IterationElement next() {
            IterationElement visitingElement = visiting.peek();
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

        public final Trie getNode() {
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
}
