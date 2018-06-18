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
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
    private static final String ERROR_CREATING_TRIE = "Error creating trie from message";
    private static final String ERROR_NON_EXISTENT_TRIE_LOGGER = "Error non existent trie with hash {}";
    private static final String ERROR_NON_EXISTENT_TRIE = "Error non existent trie with hash ";

    private static final int MESSAGE_HEADER_LENGTH = 2 + Short.BYTES * 2;
    private static final int SERIALIZATION_HEADER_LENGTH = Short.BYTES * 2 + Integer.BYTES * 2;

    // all zeroed, default hash for empty nodes
    private static Keccak256 emptyHash = makeEmptyHash();

    // this node associated value, if any
    private byte[] value;

    // the list of subnodes
    private TrieImpl[] nodes;

    // the list of subnode hashes
    private Keccak256[] hashes;

    // this node hash value
    private Keccak256 hash;

    // it is saved to store
    private boolean saved;

    // sha3 is applied to keys
    private boolean isSecure;

    // associated store, to store or retrieve nodes in the trie
    private TrieStore store;

    // shared Path
    private byte[] encodedSharedPath;
    private int sharedPathLength;

    // default constructor, no secure
    public TrieImpl() {
        this(null, 0, null, null, null, null);
        this.isSecure = false;
    }

    public TrieImpl(boolean isSecure) {
        this(null, 0, null, null, null, null);
        this.isSecure = isSecure;
    }

    public TrieImpl(TrieStore store, boolean isSecure) {
        this(null, 0, null, null, null, store);
        this.isSecure = isSecure;
    }

    private TrieImpl(TrieStore store, byte[] encodedSharedPath, int sharedPathLength, byte[] value, boolean isSecure) {
        this(encodedSharedPath, sharedPathLength, value, null, null, store);
        this.isSecure = isSecure;
    }

    // full constructor
    private TrieImpl(byte[] encodedSharedPath, int sharedPathLength, byte[] value, TrieImpl[] nodes, Keccak256[] hashes, TrieStore store) {
        this.value = value;
        this.nodes = nodes;
        this.hashes = hashes;
        this.store = store;
        this.encodedSharedPath = encodedSharedPath;
        this.sharedPathLength = sharedPathLength;
    }

    private TrieImpl withSecure(boolean isSecure) {
        this.isSecure = isSecure;
        return this;
    }

    @Override
    public Trie cloneTrie() {
        return new TrieImpl(this.encodedSharedPath, this.sharedPathLength, this.value, cloneNodes(true), cloneHashes(), this.store).withSecure(this.isSecure);
    }

    @Override
    public Trie cloneTrie(byte[] newValue) {
        TrieImpl trie = new TrieImpl(this.encodedSharedPath, this.sharedPathLength, this.value, cloneNodes(true), cloneHashes(), this.store).withSecure(this.isSecure);
        trie.setValue(newValue);
        return trie;
    }

    @Override
    public void removeNode(int position) {
        if (this.nodes != null) {
            this.nodes[position] = null;
        }

        if (this.hashes != null) {
            this.hashes[position] = null;
        }
    }

    @Override
    public void removeValue() {
        this.value = null;
    }

    private void setValue(byte[] value) {
        this.value = value;
    }

    /**
     * Factory method, to create a NewTrie from a serialized message
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

    private static TrieImpl fromMessage(byte[] message, int position, int msglength, TrieStore store) {
        if (message == null) {
            return null;
        }

        ByteArrayInputStream bstream = new ByteArrayInputStream(message, position, msglength);
        DataInputStream istream = new DataInputStream(bstream);

        try {
            int arity = istream.readByte();

            if (arity != ARITY) {
                throw new IllegalArgumentException(INVALID_ARITY);
            }

            int flags = istream.readByte();
            boolean isSecure = (flags & 0x01) == 1;
            boolean hasLongVal = (flags & 0x02) == 2;
            int bhashes = istream.readShort();
            int lshared = istream.readShort();

            int nhashes = 0;
            int lencoded = TrieImpl.getEncodedPathLength(lshared);

            byte[] encodedSharedPath = null;

            if (lencoded > 0) {
                encodedSharedPath = new byte[lencoded];
                if (istream.read(encodedSharedPath) != lencoded) {
                    throw new EOFException();
                }
            }

            Keccak256[] hashes = new Keccak256[arity];

            for (int k = 0; k < arity; k++) {
                if ((bhashes & (1 << k)) == 0) {
                    continue;
                }

                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];

                if (istream.read(valueHash) != Keccak256Helper.DEFAULT_SIZE_BYTES) {
                    throw new EOFException();
                }

                hashes[k] = new Keccak256(valueHash);
                nhashes++;
            }

            int offset = MESSAGE_HEADER_LENGTH + lencoded + nhashes * Keccak256Helper.DEFAULT_SIZE_BYTES;
            byte[] value = null;

            if (hasLongVal) {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];

                if (istream.read(valueHash) != Keccak256Helper.DEFAULT_SIZE_BYTES) {
                    throw new EOFException();
                }

                value = store.retrieveValue(valueHash);
            }
            else {
                int lvalue = msglength - offset;

                if (lvalue > 0) {
                    value = new byte[lvalue];
                    if (istream.read(value) != lvalue) {
                        throw new EOFException();
                    }
                }
            }

            TrieImpl trie = new TrieImpl(encodedSharedPath, lshared, value, null, hashes, store).withSecure(isSecure);

            if (store != null) {
                trie.saved = true;
            }

            return trie;
        } catch (IOException ex) {
            logger.error(ERROR_CREATING_TRIE, ex);
            panicProcessor.panic(PANIC_TOPIC, ERROR_CREATING_TRIE +": " + ex.getMessage());
            throw new TrieSerializationException(ERROR_CREATING_TRIE, ex);
        }
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

        if (isEmptyTrie(this.value, this.nodes, this.hashes)) {
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
        byte[] keyBytes = this.isSecure ? bytesToKey(Keccak256Helper.keccak256(key)) : bytesToKey(key);
        return get(keyBytes, keyBytes.length, 0);
    }

    @Override
    public PartialMerkleTree getPartialMerkleTree(byte[] key) {
        byte[] keyBytes = this.isSecure ? bytesToKey(Keccak256Helper.keccak256(key)) : bytesToKey(key);
        return getPartialMerkleTree(keyBytes, keyBytes.length, 0);
    }

    private PartialMerkleTree getPartialMerkleTree(byte[] key, int length, int keyPosition) {
        int position = keyPosition;

        if (position >= length) {
            return new PartialMerkleTree(this);
        }

        if (this.encodedSharedPath != null) {
            byte[] sharedPath = PathEncoder.decode(this.encodedSharedPath, this.sharedPathLength);

            for (int k = 0; k < sharedPath.length; k++, position++) {
                if (position >= length) {
                    return null;
                }

                if (key[position] != sharedPath[k]) {
                    return null;
                }
            }

            if (position >= length) {
                return new PartialMerkleTree(this);
            }
        }

        int pos = key[position];

        Trie node = this.retrieveNode(pos);

        if (node == null) {
            return null;
        }

        PartialMerkleTree tree = ((TrieImpl)node).getPartialMerkleTree(key, length, position + 1);

        tree.addTrie(this, pos);

        return tree;
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
        byte[] keyBytes = this.isSecure ? bytesToKey(Keccak256Helper.keccak256(key)) : bytesToKey(key);
        Trie trie = put(keyBytes, keyBytes.length, 0, value);

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
        int nnodes = this.getNodeCount();
        int lshared = this.sharedPathLength;
        int lencoded = getEncodedPathLength(lshared);
        boolean hasLongVal = this.hasLongValue();

        int bits = 0;

        for (int k = 0; k < ARITY; k++) {
            Keccak256 nodeHash = this.getHash(k);

            if (nodeHash == null) {
                continue;
            }

            bits |= 1 << k;
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
            buffer.put(encodedSharedPath);
        }

        for (int k = 0; k < ARITY; k++) {
            Keccak256 nodeHash = this.getHash(k);

            if (nodeHash == null) {
                continue;
            }

            buffer.put(nodeHash.getBytes());
        }

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

    /**
     * save saves the unsaved current trie and subnodes to their associated store
     *
     */
    @Override
    public void save() {
        if (this.saved) {
            return;
        }

        if (this.nodes != null) {
            for (TrieImpl node : this.nodes) {
                if (node != null) {
                    node.save();
                }
            }
        }

        this.store.save(this);
        this.saved = true;
    }

    @Override
    public void copyTo(TrieStore target) {
        if (target.retrieve(this.getHash().getBytes()) != null) {
            return;
        }

        for (int k = 0; k < ARITY; k++) {
            this.retrieveNode(k);
        }

        if (this.nodes != null) {
            for (TrieImpl node : this.nodes) {
                if (node != null) {
                    node.copyTo(target);
                }
            }
        }

        target.save(this);
    }

    /**
     * trieSize returns the number of nodes in trie
     *
     * @return the number of tries nodes, includes the current one
     */
    @Override
    public int trieSize() {
        int size = 1;

        for (int k = 0; k < ARITY; k++) {
            Trie node = this.retrieveNode(k);

            if (node != null) {
                size += node.trieSize();
            }
        }

        return size;
    }

    /**
     * get retrieves the associated value given the key
     *
     * @param key   full key
     * @param length key total length
     * @param keyPosition  position of key being examined/added
     *
     * @return the associated value, null if the key is not found
     *
     */
    @Nullable
    private byte[] get(byte[] key, int length, int keyPosition) {
        int position = keyPosition;

        if (position >= length) {
            return this.value;
        }

        if (this.encodedSharedPath != null) {
            byte[] sharedPath = PathEncoder.decode(this.encodedSharedPath, this.sharedPathLength);

            for (int k = 0; k < sharedPath.length; k++, position++) {
                if (position >= length) {
                    return null;
                }

                if (key[position] != sharedPath[k]) {
                    return null;
                }
            }

            if (position >= length) {
                return this.value;
            }
        }

        Trie node = this.retrieveNode(key[position]);

        if (node == null) {
            return null;
        }

        return ((TrieImpl)node).get(key, length, position + 1);
    }

    /**
     * getNodeCount the number of direct subnodes in this node
     *
     * @return the number of direct subnodes in this node
     *
     * Takes into account that a node could be not present as an object and
     * only be referenced via its unique hash
     */
    private int getNodeCount() {
        int count = 0;

        for (int k = 0; k < ARITY; k++) {
            TrieImpl node = this.getNode(k);
            Keccak256 localHash = this.getHash(k);

            if (node != null && !isEmptyTrie(node.value, node.nodes, node.hashes) || localHash != null) {
                count++;
            }
        }

        return count;
    }

    /**
     * retrieveNode get the subnode at position n. If it is not present but its hash is known,
     * the node is retrieved from the store
     *
     * @param n position of subnode (0 to arity - 1)
     *
     * @return  the node or null if no subnode at position
     */
    private Trie retrieveNode(int n) {
        Trie node = this.getNode(n);

        if (node != null) {
            return node;
        }

        if (this.hashes == null) {
            return null;
        }

        Keccak256 localHash = this.hashes[n];

        if (localHash == null) {
            return null;
        }

        node = this.store.retrieve(localHash.getBytes());

        if (node == null) {
            String strHash = localHash.toHexString();
            logger.error(ERROR_NON_EXISTENT_TRIE_LOGGER, strHash);
            panicProcessor.panic(PANIC_TOPIC, ERROR_NON_EXISTENT_TRIE + " " + strHash);
            throw new TrieSerializationException(ERROR_NON_EXISTENT_TRIE + " " + strHash, null);
        }

        if (this.nodes == null) {
            this.nodes = new TrieImpl[ARITY];
        }

        this.nodes[n] = (TrieImpl)node;

        return node;
    }

    /**
     * getHash get hash associated to subnode at positin n. If the hash is known
     * because it is in the internal hash cache, no access to subnode is needed.
     *
     * @param n     subnode position
     *
     * @return  node hash or null if no node is present
     */
    @Nullable
    private Keccak256 getHash(int n) {
        if (this.hashes != null && this.hashes[n] != null) {
            return this.hashes[n];
        }

        if (this.nodes == null || this.nodes[n] == null) {
            return null;
        }

        TrieImpl node = this.nodes[n];

        if (isEmptyTrie(node.value, node.nodes, node.hashes)) {
            return null;
        }

        Keccak256 localHash = node.getHash();

        this.setHash(n, localHash);

        return localHash;
    }

    /**
     * setHash save subnode hash at position n, in order to keep an internal cache
     *
     * @param n     the subnode position (0 to arity - 1)
     * @param hash  the subnode hash
     */
    @Override
    public void setHash(int n, Keccak256 hash) {
        if (this.hashes == null) {
            this.hashes = new Keccak256[ARITY];
        }

        this.hashes[n] = hash;
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

    public byte[] serializeTrie() {
        byte[] message = this.toMessage();

        List<byte[]> subnodes = new ArrayList<>();

        for (int k = 0; k < ARITY; k++) {
            TrieImpl subnode = this.getNode(k);

            if (subnode != null) {
                subnodes.add(subnode.serializeTrie());
            }
        }

        int totalSize = message.length;

        for (byte[] sn : subnodes) {
            totalSize += sn.length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(SERIALIZATION_HEADER_LENGTH + totalSize);

        buffer.putShort((short)0); // serialize version
        buffer.putShort((short)subnodes.size());   // no of subnodes
        buffer.putInt(message.length);  // this node length
        buffer.putInt(totalSize);   // all trie length

        buffer.put(message);

        for (byte[] sn : subnodes) {
            buffer.put(sn);
        }

        return buffer.array();
    }

    /**
     * deserialize returns a NewTrieImpl, from its serialized bytes
     *
     * @return full trie deserialized from byte array
     */
    public static Trie deserialize(byte[] bytes) {
        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream dstream = new DataInputStream(bstream);

        try {
            dstream.readShort();

            byte[] root = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];

            if (dstream.read(root) != Keccak256Helper.DEFAULT_SIZE_BYTES) {
                throw new EOFException();
            }

            TrieStoreImpl store = TrieStoreImpl.deserialize(bytes, Short.BYTES + Keccak256Helper.DEFAULT_SIZE_BYTES, bytes.length - Short.BYTES - Keccak256Helper.DEFAULT_SIZE_BYTES, new HashMapDB());

            Trie newTrie = store.retrieve(root);

            if (newTrie == null) {
                String strHash = Hex.toHexString(root);
                logger.error(ERROR_NON_EXISTENT_TRIE_LOGGER, strHash);
                panicProcessor.panic(PANIC_TOPIC, ERROR_CREATING_TRIE + " " + strHash);
                throw new TrieSerializationException(ERROR_CREATING_TRIE + " " + strHash, null);
            }

            return newTrie;
        }
        catch (IOException ex) {
            logger.error(ERROR_CREATING_TRIE, ex);
            panicProcessor.panic(PANIC_TOPIC, ERROR_CREATING_TRIE +": " + ex.getMessage());
            throw new TrieSerializationException(ERROR_CREATING_TRIE, ex);
        }
    }

    /**
     * getNode gets the subnode at position n
     *
     * @param n
     * @return
     */
    private TrieImpl getNode(int n) {
        if (this.nodes == null) {
            return null;
        }

        return this.nodes[n];
    }

    /**
     * put key with associated value, returning a new NewTrie
     *
     * @param key   key to be updated
     * @param length    total length of key
     * @param keyPosition  current position of the key to be processed
     * @param value     associated value
     *
     * @return the new NewTrie containing the tree with the new key value association
     *
     */
    private TrieImpl put(byte[] key, int length, int keyPosition, byte[] value) {
        TrieImpl trie = this.internalPut(key, length, keyPosition, value);

        // the following code coalesces nodes if needed for delete operation

        // it's null or it is not a delete operation
        if (trie == null || value != null) {
            return trie;
        }

        if (isEmptyTrie(trie.value, trie.nodes, trie.hashes)) {
            return null;
        }

        // only coalesce if node has only one child and no value
        if (trie.value != null || trie.getNodeCount() !=1) {
            return trie;
        }

        TrieImpl firstChild = null;
        int firstChildPosition = 0;

        for (int k = 0; firstChild == null && k < ARITY; k++) {
            firstChildPosition = k;
            firstChild = (TrieImpl)trie.retrieveNode(k);
        }

        if (firstChild == null) {
            throw new NullPointerException();
        }

        byte[] trieSharedPath;
        byte[] positionPath = new byte[] { (byte) firstChildPosition };

        if (trie.sharedPathLength == 0) {
            trieSharedPath = positionPath;
        }
        else {
            trieSharedPath = ByteUtils.concatenate(PathEncoder.decode(trie.encodedSharedPath, trie.sharedPathLength), positionPath);
        }

        byte[] newSharedPath;

        if (firstChild.sharedPathLength == 0) {
            newSharedPath = trieSharedPath;
        }
        else {
            byte[] childSharedPath = PathEncoder.decode(firstChild.encodedSharedPath, firstChild.sharedPathLength);
            newSharedPath = ByteUtils.concatenate(trieSharedPath, childSharedPath);
        }

        TrieImpl newTrie = (TrieImpl)firstChild.cloneTrie();
        newTrie.sharedPathLength = newSharedPath.length;
        newTrie.encodedSharedPath = PathEncoder.encode(newSharedPath);

        return newTrie;
    }

    private TrieImpl internalPut(byte[] key, int length, int keyPosition, byte[] value) {
        int position = keyPosition;

        if (this.encodedSharedPath != null) {
            byte[] sharedPath = PathEncoder.decode(this.encodedSharedPath, this.sharedPathLength);

            int k = lengthOfCommonPath(key, length, keyPosition, sharedPath);

            if (k >= sharedPath.length) {
                position += sharedPath.length;
            }
            else {
                return this.split(k).put(key, length, position, value);
            }
        }

        if (position >= length) {
            TrieImpl[] newNodes = cloneNodes(false);
            Keccak256[] newHashes = cloneHashes();

            if (isEmptyTrie(value, newNodes, newHashes)) {
                return null;
            }

            return new TrieImpl(this.encodedSharedPath, this.sharedPathLength, value, newNodes, newHashes, this.store).withSecure(this.isSecure);
        }

        if (isEmptyTrie(this.value, this.nodes, this.hashes)) {
            int lshared = length - position;
            byte[] shared = new byte[lshared];
            System.arraycopy(key, position, shared, 0, lshared);
            return new TrieImpl(this.store, PathEncoder.encode(shared), lshared, value, this.isSecure);
        }

        TrieImpl[] newNodes = cloneNodes(true);
        Keccak256[] newHashes = cloneHashes();

        int pos = key[position];

        TrieImpl node = (TrieImpl)retrieveNode(pos);

        if (node == null) {
            node = new TrieImpl(this.store, this.isSecure);
        }

        node = node.put(key, length, position + 1, value);

        newNodes[pos] = node;

        if (newHashes != null) {
            newHashes[pos] = null;
        }

        if (isEmptyTrie(value, newNodes, newHashes)) {
            return null;
        }

        return new TrieImpl(this.encodedSharedPath, this.sharedPathLength, this.value, newNodes, newHashes, this.store).withSecure(this.isSecure);
    }

    private int lengthOfCommonPath(byte[] key, int length, int position, byte[] sharedPath) {
        int k;

        for (k = 0; k < sharedPath.length && position + k < length; k++) {
            if (sharedPath[k] != key[position + k]) {
                break;
            }
        }

        return k;
    }

    private TrieImpl split(int nshared) {
        TrieImpl[] newChildNodes = this.cloneNodes(false);
        Keccak256[] newChildHashes = this.cloneHashes();

        TrieImpl newChildTrie = new TrieImpl(null, 0, this.value, newChildNodes, newChildHashes, this.store).withSecure(this.isSecure);

        byte[] sharedPath = PathEncoder.decode(this.encodedSharedPath, this.sharedPathLength);

        if (sharedPath.length > nshared + 1) {
            int newSharedLength = sharedPath.length - nshared - 1;
            byte[] newShared = new byte[newSharedLength];
            System.arraycopy(sharedPath, nshared + 1, newShared, 0, newSharedLength);
            newChildTrie.encodedSharedPath = PathEncoder.encode(newShared);
            newChildTrie.sharedPathLength = newSharedLength;
        }

        TrieImpl newTrie = new TrieImpl(this.store, this.isSecure);
        TrieImpl[] newNodes = new TrieImpl[ARITY];
        int pos = sharedPath[nshared];
        newNodes[pos] = newChildTrie;
        newTrie.nodes = newNodes;

        if (nshared > 0) {
            byte[] newSharedPath = new byte[nshared];
            System.arraycopy(sharedPath, 0, newSharedPath, 0, nshared);
            newTrie.encodedSharedPath = PathEncoder.encode(newSharedPath);
            newTrie.sharedPathLength = nshared;
        }

        return newTrie;
    }

    /**
     * cloneHashes clone the internal subnode hashes cache
     *
     * @return a copy of the original hashes
     */
    @Nullable
    private Keccak256[] cloneHashes() {
        if (this.hashes == null) {
            return null;
        }

        int nhashes = this.hashes.length;
        Keccak256[] newHashes = new Keccak256[nhashes];

        for (int k = 0; k < nhashes; k++) {
            newHashes[k] = this.hashes[k];
        }

        return newHashes;
    }

    /**
     * cloneNodes clone the current list of nodes
     *
     * @param create    create an empty list if the current list is null
     *
     * @return the new list of nodes
     */
    @Nullable
    private TrieImpl[] cloneNodes(boolean create) {
        if (nodes == null && !create) {
            return null;
        }

        TrieImpl[] newnodes = new TrieImpl[ARITY];

        if (nodes != null) {
            for (int k = 0; k < ARITY; k++) {
                newnodes[k] = nodes[k];
            }
        }

        return newnodes;
    }

    @Override
    public boolean hasStore() {
        return this.store != null;
    }

    /**
     * isEmptyTrie checks the existence of subnodes, subnodes hashes or value
     *
     * @param value     current value
     * @param nodes     list of subnodes
     * @param hashes    list of subnodes hashes
     *
     * @return true if no data
     */
    private static boolean isEmptyTrie(byte[] value, TrieImpl[] nodes, Keccak256[] hashes) {
        if (value != null && value.length != 0) {
            return false;
        }

        if (nodes != null) {
            for (int k = 0; k < nodes.length; k++) {
                if (nodes[k] != null) {
                    return false;
                }
            }
        }

        if (hashes != null) {
            for (int k = 0; k < hashes.length; k++) {
                if (hashes[k] != null) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * bytesToKey expand key to bytes according to node arity
     * Example: if arity is 16 (16 subnodes per node) each key byte is expanded to
     * two bytes, representing two nibbles
     * if arity is 2 (binary trie) each original byte is expanded 8 bytes, each representing
     * a bit value of the original byte
     *
     * @param bytes original key
     * @param arity number of subnodes in each node trie
     *
     * @return expanded key
     */
    public static byte[] bytesToKey(byte[] bytes) {
        int factor = 8;
        int mask = 0x01;
        int nbits = 1;

        byte[] keyBytes = new byte[bytes.length * factor];
        int l = bytes.length;
        int j = 0;

        for (int k = 0; k < l; k++) {
            byte b = bytes[k];

            for (int i = 0; i < factor; i++) {
                keyBytes[j++] = (byte) ((b >> (nbits * (factor - i - 1))) & mask);
            }
        }

        return keyBytes;
    }

    public Trie getSnapshotTo(Keccak256 hash) {
        // This call shouldn't be needed since internally try can know it should store data
        //this.save();

        if (emptyHash.equals(hash)) {
            return new TrieImpl(this.store, this.isSecure);
        }

        Trie newTrie = this.store.retrieve(hash.getBytes());

        if (newTrie == null) {
            String strHash = hash.toHexString();
            logger.error(ERROR_NON_EXISTENT_TRIE_LOGGER, strHash);
            panicProcessor.panic(PANIC_TOPIC, ERROR_CREATING_TRIE + " " + strHash);

            throw new TrieSerializationException(ERROR_CREATING_TRIE + " " + strHash, null);
        }

        return newTrie;
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

    private static int getEncodedPathLength(int length) {
        return length / 8 + (length % 8 == 0 ? 0 : 1);
    }

    /**
     * makeEmpyHash creates the hash associated to empty nodes
     *
     * @return a hash with zeroed bytes
     */
    private static Keccak256 makeEmptyHash() {
        return new Keccak256(Keccak256Helper.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }
}
