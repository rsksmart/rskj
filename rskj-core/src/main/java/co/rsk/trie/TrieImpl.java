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
import co.rsk.panic.PanicProcessor;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * TrieImpl is the trie node.
 *
 * Each node have 2, 4 or 16 subnodes (depending on arity variable value)
 * and an optional associated value (a byte array)
 *
 * A node is referenced via a key (a byte array)
 *
 * A node can be serialized to/from a message (a byte array)
 *
 * A node has a hash (Sha3 of its serialization)
 *
 * A node is immutable: to add/change a value or key, a new node is created
 *
 * An empty node has no subnodes and a null value
 *
 * Created by ajlopez on 22/08/2016.
 */
public class TrieImpl implements Trie {
    private static final int ARITY = 2;

    private static final Logger logger = LoggerFactory.getLogger("newtrie");
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final String PANIC_TOPIC = "newtrie";
    private static final String INVALID_ARITY = "Invalid arity";
    private static final String ERROR_NON_EXISTENT_TRIE = "Error non existent trie with hash %s";

    private static final int MESSAGE_HEADER_LENGTH = 2 + Short.BYTES * 2;
    private static final int LAST_BYTE_ONLY_MASK = 0x000000ff;

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

    // sha3 is applied to keys
    private boolean isSecure;

    // associated store, to store or retrieve nodes in the trie
    private TrieStore store;

    // shared Path
    private final TrieKeySlice sharedPath;

    // default constructor, no secure
    public TrieImpl() {
        this(TrieKeySlice.empty(), null, NodeReference.empty(), NodeReference.empty(), null);
        this.isSecure = false;
    }

    public TrieImpl(boolean isSecure) {
        this(TrieKeySlice.empty(), null, NodeReference.empty(), NodeReference.empty(), null);
        this.isSecure = isSecure;
    }

    public TrieImpl(TrieStore store, boolean isSecure) {
        this(TrieKeySlice.empty(), null, NodeReference.empty(), NodeReference.empty(), store);
        this.isSecure = isSecure;
    }

    private TrieImpl(TrieStore store, TrieKeySlice sharedPath, byte[] value, boolean isSecure) {
        this(sharedPath, value, NodeReference.empty(), NodeReference.empty(), store);
        this.isSecure = isSecure;
    }

    // full constructor
    private TrieImpl(TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, TrieStore store) {
        this.value = value;
        this.left = left;
        this.right = right;
        this.store = store;
        this.sharedPath = sharedPath;
    }

    private TrieImpl withSecure(boolean isSecure) {
        this.isSecure = isSecure;
        return this;
    }

    /**
     * Pool method, to create a NewTrie from a serialized message
     * the store argument is used to retrieve any subnode
     * of the subnode
     *
     * @param message   the content (arity, hashes, value) serializaced as byte array
     * @param store     the store containing the rest of the trie nodes, to be used on retrieve them if they are needed in memory
     */
    public static TrieImpl fromMessage(byte[] message, TrieStore store) {
        if (message == null) {
            return null;
        }

        return fromMessage(message, 0, message.length, store);
    }

    // this methods reads a short as dataInputStream + byteArrayInputStream
    private static TrieImpl fromMessage(byte[] message, int position, int msglength, TrieStore store) {
        if (message == null) {
            return null;
        }
        int current = position;
        int arity = message[current];
        current += Byte.BYTES;

        if (arity != ARITY) {
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

        if (hasLongVal) {
            byte[] valueHash = readHash(message, current).getBytes();
            // current += keccakSize; current position is not more needed

            value = store.retrieveValue(valueHash);
        } else {
            int lvalue = msglength - offset;
            if (lvalue > 0) {
                if (message.length - current  < lvalue) {
                    throw new IllegalArgumentException(String.format(
                            "Left message is too short for value expected:%d actual:%d total:%d",
                            lvalue, message.length - current, message.length));
                }
                value = Arrays.copyOfRange(message, current, current + lvalue);
                //current += lvalue; current position is not more needed
            }
        }

        TrieImpl trie = new TrieImpl(sharedPath, value, left, right, store).withSecure(isSecure);

        if (store != null) {
            trie.saved = true;
        }

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
    @Override
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
    @Override
    public byte[] get(byte[] key) {
        TrieKeySlice keySlice = TrieKeySlice.fromKey(this.isSecure ? Keccak256Helper.keccak256(key) : key);
        return get(keySlice);
    }

    /**
     * get by string, utility method used from test methods
     *
     * @param key   a string, that is converted to a byte array
     * @return a byte array with the associated value
     */
    @Override
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
    @Override
    public Trie put(byte[] key, byte[] value) {
        TrieKeySlice keySlice = TrieKeySlice.fromKey(this.isSecure ? Keccak256Helper.keccak256(key) : key);
        Trie trie = put(keySlice, value);

        return trie == null ? new TrieImpl(this.store, this.isSecure) : trie;
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
    @Override
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
    @Override
    public Trie delete(byte[] key) {
        return put(key, null);
    }

    /**
     * delete string key, utility method to be used for testing
     *
     * @param key a string
     *
     * @return the new top node of the trie with the key removed
     */
    @Override
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
    @Override
    public byte[] toMessage() {
        int lvalue = this.value == null ? 0 : this.value.length;
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

        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_HEADER_LENGTH + lencoded + nnodes * Keccak256Helper.DEFAULT_SIZE_BYTES + (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES : lvalue));

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

        return buffer.array();
    }

    @Override
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
    @Override
    public void save() {
        if (this.saved) {
            return;
        }

        this.left.save();
        this.right.save();

        this.store.save(this);
        this.saved = true;
    }

    @Override
    public void copyTo(TrieStore target) {
        if (target.retrieve(this.getHash().getBytes()) != null) {
            return;
        }

        this.left.getNode().ifPresent(node -> node.copyTo(target));
        this.right.getNode().ifPresent(node -> node.copyTo(target));

        target.save(this);
    }

    /**
     * trieSize returns the number of nodes in trie
     *
     * @return the number of tries nodes, includes the current one
     */
    @Override
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
    private byte[] get(TrieKeySlice key) {
        if (sharedPath.length() > key.length()) {
            return null;
        }

        int commonPathLength = key.commonPath(sharedPath).length();
        if (commonPathLength < sharedPath.length()) {
            return null;
        }

        if (commonPathLength == key.length()) {
            return value;
        }

        TrieImpl node = this.retrieveNode(key.get(commonPathLength));
        if (node == null) {
            return null;
        }

        return node.get(key.slice(commonPathLength + 1, key.length()));
    }

    private TrieImpl retrieveNode(byte implicitByte) {
        return implicitByte == 0 ? this.left.getNode().orElse(null) : this.right.getNode().orElse(null);
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
     * serialize returns the full trie data (this node and its subnodes)
     * in a byte array
     *
     * @return full trie serialized as byte array
     */
    public byte[] serialize() {
        this.save();

        byte[] bytes = this.store.serialize();
        byte[] root = this.getHash().getBytes();

        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES + Keccak256Helper.DEFAULT_SIZE_BYTES + bytes.length);

        buffer.putShort((short) 0);
        buffer.put(root);
        buffer.put(bytes);

        return buffer.array();
    }

    /**
     * deserialize returns a NewTrieImpl, from its serialized bytes
     *
     * @return full trie deserialized from byte array
     */
    public static Trie deserialize(byte[] bytes) {
        final int keccakSize = Keccak256Helper.DEFAULT_SIZE_BYTES;
        int expectedSize = Short.BYTES + keccakSize;
        if (expectedSize > bytes.length) {
            throw new IllegalArgumentException(
                    String.format("Expected size is: %d actual size is %d", expectedSize, bytes.length));
        }

        byte[] root = Arrays.copyOfRange(bytes, Short.BYTES, expectedSize);
        TrieStore store = TrieStoreImpl.deserialize(bytes, expectedSize, new HashMapDB());

        return internalRetrieve(store, root);
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
    private TrieImpl put(TrieKeySlice key, byte[] value) {
        TrieImpl trie = this.internalPut(key, value);

        // the following code coalesces nodes if needed for delete operation

        // it's null or it is not a delete operation
        if (trie == null || value != null) {
            return trie;
        }

        if (trie.isEmptyTrie()) {
            return null;
        }

        // only coalesce if node has only one child and no value
        if (trie.value != null) {
            return trie;
        }

        Optional<TrieImpl> leftOpt = trie.left.getNode();
        Optional<TrieImpl> rightOpt = trie.right.getNode();
        if (leftOpt.isPresent() && rightOpt.isPresent()) {
            return trie;
        }

        if (!leftOpt.isPresent() && !rightOpt.isPresent()) {
            return trie;
        }

        TrieImpl child;
        byte childImplicitByte;
        if (leftOpt.isPresent()) {
            child = leftOpt.get();
            childImplicitByte = (byte) 0;
        } else { // has right node
            child = rightOpt.get();
            childImplicitByte = (byte) 1;
        }

        TrieKeySlice newSharedPath = trie.sharedPath.rebuildSharedPath(childImplicitByte, child.sharedPath);
        return new TrieImpl(newSharedPath, child.value, child.left, child.right, child.store).withSecure(child.isSecure);
    }

    private TrieImpl internalPut(TrieKeySlice key, byte[] value) {
        TrieKeySlice commonPath = key.commonPath(sharedPath);
        if (commonPath.length() < sharedPath.length()) {
            return this.split(commonPath).put(key, value);
        }

        if (sharedPath.length() >= key.length()) {
            if (Arrays.equals(this.value, value)) {
                return this;
            }

            if (isEmptyTrie(value, this.left, this.right)) {
                return null;
            }

            return new TrieImpl(this.sharedPath, value, this.left, this.right, this.store).withSecure(this.isSecure);
        }

        if (isEmptyTrie()) {
            return new TrieImpl(this.store, key, value, this.isSecure);
        }

        // this bit will be implicit and not present in a shared path
        byte pos = key.get(sharedPath.length());

        TrieImpl node = retrieveNode(pos);
        if (node == null) {
            node = new TrieImpl(this.store, this.isSecure);
        }

        TrieKeySlice subKey = key.slice(sharedPath.length() + 1, key.length());
        TrieImpl newNode = node.put(subKey, value);

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

        if (isEmptyTrie(value, newLeft, newRight)) {
            return null;
        }

        return new TrieImpl(this.sharedPath, this.value, newLeft, newRight, this.store).withSecure(this.isSecure);
    }

    private TrieImpl split(TrieKeySlice commonPath) {
        int commonPathLength = commonPath.length();
        TrieKeySlice newChildSharedPath = sharedPath.slice(commonPathLength + 1, sharedPath.length());
        TrieImpl newChildTrie = new TrieImpl(newChildSharedPath, this.value, this.left, this.right, this.store).withSecure(this.isSecure);
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

        return new TrieImpl(commonPath, null, newLeft, newRight, this.store).withSecure(this.isSecure);
    }

    @Override
    public boolean hasStore() {
        return this.store != null;
    }

    public boolean isEmptyTrie() {
        return isEmptyTrie(this.value, this.left, this.right);
    }

    /**
     * isEmptyTrie checks the existence of subnodes, subnodes hashes or value
     *
     * @param value     current value
     * @param left      a reference to the left node
     * @param right     a reference to the right node
     *
     * @return true if no data
     */
    private static boolean isEmptyTrie(byte[] value, NodeReference left, NodeReference right) {
        if (value != null && value.length != 0) {
            return false;
        }

        return left.isEmpty() && right.isEmpty();
    }

    public Trie getSnapshotTo(Keccak256 hash) {
        // This call shouldn't be needed since internally try can know it should store data
        //this.save();
        if (getHash().equals(hash)) {
            return this;
        }

        if (emptyHash.equals(hash)) {
            return new TrieImpl(this.store, this.isSecure);
        }

        return internalRetrieve(this.store, hash.getBytes());
    }

    public TrieStore getStore() {
        return this.store;
    }

    public boolean hasLongValue() {
        return this.value != null && this.value.length > 32;
    }

    public byte[] getValueHash() {
        if (this.hasLongValue()) {
            return Keccak256Helper.keccak256(this.value);
        }

        return null;
    }

    public byte[] getValue() { return this.value; }

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
}
