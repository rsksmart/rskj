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

import co.rsk.panic.PanicProcessor;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.crypto.SHA3Helper.sha3;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Trie is the trie node.
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
 *
 * TODO(martin.medina): We shouldn't decode/encode the paths all the time. This makes multiple allocs and consumes CPU.
 * Avoiding that could bring performance gain.
 * 
 */
public class TrieImpl implements Trie {
    private static final Logger logger = LoggerFactory.getLogger("trie");
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final String PANIC_TOPIC = "trie";
    private static final String INVALID_ARITY = "Invalid arity";
    private static final String ERROR_CREATING_TRIE = "Error creating trie from message";

    private static final int MESSAGE_HEADER_LENGTH = 2 + Short.BYTES * 2;
    private static final int SERIALIZATION_HEADER_LENGTH = Short.BYTES * 2 + Integer.BYTES * 2;

    // all zeroed, default hash for empty nodes
    private static byte[] emptyHash = makeEmptyHash();

    // this node associated value, if any
    private byte[] value;

    // the list of subnodes
    private TrieImpl[] nodes;

    // the list of subnode hashes
    private byte[][] hashes;

    // this node hash value
    private byte[] hash;

    // it is saved to store
    private boolean saved;

    // sha3 is applied to keys
    private boolean isSecure;

    // associated store, to store or retrieve nodes in the trie
    private TrieStore store;

    // no of subnodes (2, 4 or 16 are supported)
    private int arity;

    // shared Path
    private byte[] encodedSharedPath;
    private int sharedPathLength;

    // default constructor, arity == 2 (binary tree), no secure
    public TrieImpl() {
        this(2, null, 0, null, null, null, null);
        this.isSecure = false;
    }

    public TrieImpl(boolean isSecure) {
        this(2, null, 0, null, null, null, null);
        this.isSecure = isSecure;
    }

    public TrieImpl(int arity, boolean isSecure) {
        this(arity, null, 0, null, null, null, null);
        this.isSecure = isSecure;
    }

    public TrieImpl(TrieStore store, boolean isSecure) {
        this(2, null, 0, null, null, null, store);
        this.isSecure = isSecure;
    }

    public TrieImpl(int arity, TrieStore store, boolean isSecure) {
        this(arity, null, 0, null, null, null, store);
        this.isSecure = isSecure;
    }

    private TrieImpl(int arity, TrieStore store, byte[] encodedSharedPath, int sharedPathLength, byte[] value, boolean isSecure) {
        this(arity, encodedSharedPath, sharedPathLength, value, null, null, store);
        this.isSecure = isSecure;
    }

    // full constructor
    private TrieImpl(int arity, byte[] encodedSharedPath, int sharedPathLength, byte[] value, TrieImpl[] nodes, byte[][] hashes, TrieStore store) {
        if (arity != 2 && arity != 4 && arity != 16)
            throw new IllegalArgumentException(INVALID_ARITY);

        this.arity = arity;
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
        return new TrieImpl(this.arity, this.encodedSharedPath, this.sharedPathLength, this.value, cloneNodes(true), cloneHashes(), this.store).withSecure(this.isSecure);
    }

    @Override
    public Trie cloneTrie(byte[] newValue) {
        TrieImpl trie = new TrieImpl(this.arity, this.encodedSharedPath, this.sharedPathLength, this.value, cloneNodes(true), cloneHashes(), this.store).withSecure(this.isSecure);
        trie.setValue(newValue);
        return trie;
    }

    @Override
    public void removeNode(int position) {
        if (this.nodes != null)
            this.nodes[position] = null;
        if (this.hashes != null)
            this.hashes[position] = null;
    }

    @Override
    public void removeValue() {
        this.value = null;
    }

    private void setValue(byte[] value) {
        this.value = value;
    }

    /**
     * Factory method, to create a Trie from a serialized message
     * the store argument is used to retrieve any subnode
     * of the subnode
     *
     * @param message   the content (arity, hashes, value) serializaced as byte array
     * @param store     the store containing the rest of the trie nodes, to be used on retrieve them if they are needed in memory
     */
    public static TrieImpl fromMessage(byte[] message, TrieStore store) {
        if (message == null)
            return null;

        return fromMessage(message, 0, message.length, store);
    }

    private static TrieImpl fromMessage(byte[] message, int position, int msglength, TrieStore store) {
        if (message == null)
            return null;

        ByteArrayInputStream bstream = new ByteArrayInputStream(message, position, msglength);
        DataInputStream istream = new DataInputStream(bstream);

        try {
            int arity = istream.readByte();
            boolean isSecure = istream.readByte() == 1;
            int bhashes = istream.readShort();
            int lshared = istream.readShort();

            int nhashes = 0;
            int lencoded = TrieImpl.getEncodedPathLength(lshared, arity);

            byte[] encodedSharedPath = null;

            if (lencoded > 0) {
                encodedSharedPath = new byte[lencoded];
                if (istream.read(encodedSharedPath) != lencoded)
                    throw new EOFException();
            }

            byte[][] hashes = new byte[arity][];

            for (int k = 0; k < arity; k++) {
                if ((bhashes & (1 << k)) == 0)
                    continue;
                hashes[k] = new byte[SHA3Helper.DEFAULT_SIZE_BYTES];

                if (istream.read(hashes[k]) != SHA3Helper.DEFAULT_SIZE_BYTES)
                    throw new EOFException();

                nhashes++;
            }

            int offset = MESSAGE_HEADER_LENGTH + lencoded + nhashes * SHA3Helper.DEFAULT_SIZE_BYTES;
            int lvalue = msglength - offset;
            byte[] value = null;

            if (lvalue > 0) {
                value = new byte[lvalue];
                if (istream.read(value) != lvalue)
                    throw new EOFException();
            }

            TrieImpl trie = new TrieImpl(arity, encodedSharedPath, lshared, value, null, hashes, store).withSecure(isSecure);

            if (store != null)
                trie.saved = true;

            return trie;
        } catch (IOException ex) {
            logger.error(ERROR_CREATING_TRIE, ex);
            panicProcessor.panic(PANIC_TOPIC, ERROR_CREATING_TRIE +": " + ex.getMessage());
            throw new TrieSerializationException(ERROR_CREATING_TRIE, ex);
        }
    }

    /**
     * getArity returns the number of subnodes
     *
     * @return  2, 4 or 16: the total number of subnodes in node
     */
    @Override
    public int getArity() {
        return this.arity;
    }

    /**
     * getHash calculates and/or returns the hash associated with this node content
     *
     * the internal variable hash could contains the cached hash
     *
     * @return  a byte array with the node serialized to bytes
     */
    @Override
    public byte[] getHash() {
        if (this.hash != null)
            return ByteUtils.clone(this.hash);

        if (isEmptyTrie(this.value, this.nodes, this.hashes))
            return ByteUtils.clone(emptyHash);

        byte[] message = this.toMessage();

        this.hash = SHA3Helper.sha3(message);

        return ByteUtils.clone(this.hash);
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
        byte[] keyBytes = this.isSecure ? bytesToKey(sha3(key), this.arity) : bytesToKey(key, this.arity);
        return get(keyBytes, keyBytes.length, 0);
    }

    @Override
    public PartialMerkleTree getPartialMerkleTree(byte[] key) {
        byte[] keyBytes = this.isSecure ? bytesToKey(sha3(key), this.arity) : bytesToKey(key, this.arity);

        return getPartialMerkleTree(keyBytes, keyBytes.length, 0);
    }

    private PartialMerkleTree getPartialMerkleTree(byte[] key, int length, int keyPosition) {
        int position = keyPosition;

        if (position >= length)
            return new PartialMerkleTree(this);

        if (this.encodedSharedPath != null) {
            byte[] sharedPath = PathEncoder.decode(this.encodedSharedPath, this.arity, this.sharedPathLength);

            for (int k = 0; k < sharedPath.length; k++, position++) {
                if (position >= length)
                    return null;
                if (key[position] != sharedPath[k])
                    return null;
            }

            if (position >= length)
                return new PartialMerkleTree(this);
        }

        int pos = key[position];

        Trie node = this.retrieveNode(pos);

        if (node == null)
            return null;

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
     * put key value association, returning a new Trie
     *
     * @param key   key to be updated or created, a byte array
     * @param value value to associated to the key, a byte array
     *
     * @return a new Trie node, the top node of the new tree having the
     * key-value association. The original node is immutable, a new tree
     * is build, adding some new nodes
     */
    @Override
    public Trie put(byte[] key, byte[] value) {
        byte[] keyBytes = this.isSecure ? bytesToKey(sha3(key), this.arity) : bytesToKey(key, this.arity);

        TriePutResult putResult = put(keyBytes, keyBytes.length, 0, value);
        Trie trie = putResult.getTrie();

        if (ResultAction.DELETE == putResult.getAction()) {
            trie = this.buildNewTrieAfterDelete(this, this, putResult);
        }

        return trie == null ? new TrieImpl(this.arity, this.store, this.isSecure) : trie;
    }

    /**
     * put string key to value, the key is converted to byte array
     * utility method to be used from testing
     *
     * @param key   a string
     * @param value an associated value, a byte array
     *
     * @return  a new Trie, the top node of a new trie having the key
     * value association
     */
    @Override
    public Trie put(String key, byte[] value) {
        return put(key.getBytes(StandardCharsets.UTF_8), value);
    }

    /**
     * delete update the key to null value and compress if necessary
     *
     * @param key a byte array
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
        int nnodes = this.getNumberOfChildren();
        int lshared = this.sharedPathLength;
        int lencoded = getEncodedPathLength(lshared, this.arity);

        int bits = 0;

        for (int k = 0; k < this.arity; k++) {
            byte[] nodeHash = this.getHash(k);

            if (nodeHash == null)
                continue;

            bits |= 1 << k;
        }

        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_HEADER_LENGTH + lencoded + nnodes * SHA3Helper.DEFAULT_SIZE_BYTES + lvalue);

        buffer.put((byte) this.arity);
        buffer.put((byte) (this.isSecure ? 1 : 0));
        buffer.putShort((short) bits);
        buffer.putShort((short) lshared);

        if (lshared > 0)
            buffer.put(encodedSharedPath);


        for (int k = 0; k < this.arity; k++) {
            byte[] nodeHash = this.getHash(k);

            if (nodeHash == null)
                continue;

            buffer.put(nodeHash);
        }

        if (lvalue > 0)
            buffer.put(this.value);

        return buffer.array();
    }

    /**
     * save saves the unsaved current trie and subnodes to their associated store
     *
     */
    @Override
    public void save() {
        if (this.saved)
            return;

        if (this.nodes != null)
            for (TrieImpl node : this.nodes)
                if (node != null)
                    node.save();

        this.store.save(this);
        this.saved = true;
    }

    /**
     * trieSize returns the number of nodes in trie
     *
     * @return the number of tries nodes, includes the current one
     */
    @Override
    public int trieSize() {
        int size = 1;

        for (int k = 0; k < this.arity; k++) {
            Trie node = this.retrieveNode(k);

            if (node != null)
                size += node.trieSize();
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
    private byte[] get(byte[] key, int length, int keyPosition) {
        int position = keyPosition;

        if (position >= length)
            return this.value;

        if (this.encodedSharedPath != null) {
            byte[] sharedPath = PathEncoder.decode(this.encodedSharedPath, this.arity, this.sharedPathLength);

            for (int k = 0; k < sharedPath.length; k++, position++) {
                if (position >= length)
                    return null;

                if (key[position] != sharedPath[k])
                    return null;
            }

            if (position >= length)
                return this.value;
        }

        Trie node = this.retrieveNode(key[position]);

        if (node == null)
            return null;

        return ((TrieImpl)node).get(key, length, position + 1);
    }

    /**
     * getNumberOfChildren the number of direct subnodes in this node
     *
     * @return the number of direct subnodes in this node
     *
     * Takes into account that a node could be not present as an object and
     * only be referenced via its unique hash
     */
    private int getNumberOfChildren() {
        int count = 0;

        for (int k = 0; k < this.arity; k++) {
            TrieImpl node = this.getNode(k);
            byte[] localHash = this.getHash(k);

            if (node != null && !isEmptyTrie(node.value, node.nodes, node.hashes) || localHash != null)
                count++;
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

        if (node != null)
            return node;

        if (this.hashes == null)
            return null;

        byte[] localHash = this.hashes[n];

        if (localHash == null)
            return null;

        node = this.store.retrieve(localHash);

        if (node == null)
            return null;

        if (this.nodes == null)
            this.nodes = new TrieImpl[this.arity];

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
    private byte[] getHash(int n) {
        if (this.hashes != null && this.hashes[n] != null)
            return this.hashes[n];

        if (this.nodes == null || this.nodes[n] == null)
            return null;

        TrieImpl node = this.nodes[n];

        if (isEmptyTrie(node.value, node.nodes, node.hashes))
            return null;

        byte[] localHash = node.getHash();

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
    public void setHash(int n, byte[] hash) {
        if (this.hashes == null)
            this.hashes = new byte[this.arity][];

        this.hashes[n] = hash;
    }

    /**
     * serialize returns the full trie data (this node and its subnodes)
     * in a byte array
     *
     * @return full trie serialized as byte array
     */
    @Override
    public byte[] serialize() {
        this.save();

        byte[] bytes = this.store.serialize();
        byte[] root = this.getHash();

        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES + SHA3Helper.DEFAULT_SIZE_BYTES + bytes.length);

        buffer.putShort((short) 0);
        buffer.put(root);
        buffer.put(bytes);

        return buffer.array();
    }

    public byte[] serializeTrie() {
        byte[] message = this.toMessage();

        List<byte[]> subnodes = new ArrayList<>();

        for (int k = 0; k < this.arity; k++) {
            TrieImpl subnode = this.getNode(k);

            if (subnode != null)
                subnodes.add(subnode.serializeTrie());
        }

        int totalSize = message.length;

        for (byte[] sn : subnodes)
            totalSize += sn.length;

        ByteBuffer buffer = ByteBuffer.allocate(SERIALIZATION_HEADER_LENGTH + totalSize);

        buffer.putShort((short)0); // serialize version
        buffer.putShort((short)subnodes.size());   // no of subnodes
        buffer.putInt(message.length);  // this node length
        buffer.putInt(totalSize);   // all trie length

        buffer.put(message);

        for (byte[] sn : subnodes)
            buffer.put(sn);

        return buffer.array();
    }

    /**
     * deserialize returns a TrieImpl, from its serialized bytes
     *
     * @return full trie deserialized from byte array
     */
    public static Trie deserialize(byte[] bytes) {
        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
        DataInputStream dstream = new DataInputStream(bstream);

        try {
            dstream.readShort();

            byte[] root = new byte[SHA3Helper.DEFAULT_SIZE_BYTES];

            if (dstream.read(root) != SHA3Helper.DEFAULT_SIZE_BYTES)
                throw new EOFException();

            TrieStoreImpl store = TrieStoreImpl.deserialize(bytes, Short.BYTES + SHA3Helper.DEFAULT_SIZE_BYTES, bytes.length - Short.BYTES - SHA3Helper.DEFAULT_SIZE_BYTES, new HashMapDB());

            return store.retrieve(root);
        } catch (IOException ex) {
            logger.error(ERROR_CREATING_TRIE, ex);
            panicProcessor.panic(PANIC_TOPIC, ERROR_CREATING_TRIE +": " + ex.getMessage());
            throw new TrieSerializationException(ERROR_CREATING_TRIE, ex);
        }

    }

    public static TrieImpl deserializeTrie(byte[] bytes) {
        return deserializeTrie(bytes, 0, bytes.length);
    }

    private static TrieImpl deserializeTrie(byte[] bytes, int offset, int length) {
        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes, offset, length);
        DataInputStream dstream = new DataInputStream(bstream);

        return getNewTrie(bytes, offset, dstream);
    }

    private static TrieImpl getNewTrie(byte[] bytes, int offset, DataInputStream dstream) {
        try {
            dstream.readShort();    // serialization version
            int nsubnodes = dstream.readShort();    // number of subnodes
            int messageLength = dstream.readInt();  // trie message length

            int messageOffset = offset + SERIALIZATION_HEADER_LENGTH;

            TrieImpl trie = fromMessage(bytes, messageOffset, messageLength, null);

            if (trie == null)
                throw new NullPointerException();

            if (nsubnodes > 0) {
                deserializeSubnodes(bytes, messageLength, messageOffset, trie);
            }
            return trie;
        } catch (IOException ex) {
            logger.error(ERROR_CREATING_TRIE, ex);
            panicProcessor.panic(PANIC_TOPIC, ERROR_CREATING_TRIE + ": " + ex.getMessage());
            throw new TrieSerializationException(ERROR_CREATING_TRIE, ex);
        }
    }

    private static void deserializeSubnodes(byte[] bytes, int messageLength, int messageOffset, TrieImpl trie) {
        int subnodeOffset = messageOffset + messageLength;

        if (trie.nodes == null)
            trie.nodes = new TrieImpl[trie.arity];

        for (int k = 0; k < trie.arity; k++) {
            if (trie.hashes[k] == null)
                continue;

            int subnodeLength = getSerializedNodeLength(bytes, subnodeOffset);

            trie.nodes[k] = deserializeTrie(bytes, subnodeOffset, subnodeLength);

            subnodeOffset += subnodeLength;
        }
    }

    private static int getSerializedNodeLength(byte[] bytes, int offset) {
        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes, offset, SERIALIZATION_HEADER_LENGTH);
        DataInputStream dstream = new DataInputStream(bstream);

        try {
            dstream.readShort();    // serialization version
            dstream.readShort();    // number of subnodes
            dstream.readInt();      // trie message length
            int totalLength = dstream.readInt();    // total message length, without this header

            return SERIALIZATION_HEADER_LENGTH + totalLength;
        } catch (IOException ex) {
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
        if (this.nodes == null)
            return null;

        return this.nodes[n];
    }

    /**
     * put key with associated value, returning a new Trie
     *
     * @param key   key to be updated
     * @param length    total length of key
     * @param keyPosition  current position of the key to be processed
     * @param value     associated value
     *
     * @return an object to indicate the result, it can be a put or a delete. That object also has some info
     *         to finish the last step of recursion at the caller.
     *
     */
    private TriePutResult put(byte[] key, int length, int keyPosition, byte[] value) {
        int position = keyPosition;

        if (this.encodedSharedPath != null) {
            byte[] sharedPath = PathEncoder.decode(this.encodedSharedPath, this.arity, this.sharedPathLength);

            int k = lengthOfCommonPath(key, length, keyPosition, sharedPath);

            if (k >= sharedPath.length)
                position += sharedPath.length;
            else
                return this.split(k).put(key, length, position, value);
        }

        if (position >= length) {
            TrieImpl[] newNodes = cloneNodes(false);
            byte[][] newHashes = cloneHashes();

            boolean isDeleteOperation = value == null;
            if (isDeleteOperation)
                return new TriePutResult(ResultAction.KEY_TO_DELETE_FOUND, this.getNumberOfChildren());

            TrieImpl trieToReturn = new TrieImpl(this.arity, this.encodedSharedPath, this.sharedPathLength, value, newNodes, newHashes, this.store).withSecure(this.isSecure);
            return new TriePutResult(ResultAction.PUT, trieToReturn);
        }

        if (isEmptyTrie(this.value, this.nodes, this.hashes)) {
            int lshared = length - position;
            byte[] shared = new byte[lshared];
            System.arraycopy(key, position, shared, 0, lshared);
            TrieImpl trieToReturn = new TrieImpl(this.arity, this.store, PathEncoder.encode(shared, this.arity), lshared, value, this.isSecure);

            return new TriePutResult(ResultAction.PUT, trieToReturn);
        }

        TrieImpl[] newNodes = cloneNodes(true);
        byte[][] newHashes = cloneHashes();

        int pos = key[position];

        TrieImpl node = (TrieImpl)retrieveNode(pos);

        if (node == null)
            node = new TrieImpl(this.arity, this.store, this.isSecure);

        // do recursion to continue adding the new key or searching for the key to delete.
        TriePutResult putResult = node.put(key, length, position + 1, value);

        if(ResultAction.KEY_TO_DELETE_FOUND == putResult.getAction()) {
            // tell my parent that he needs to delete me
            return new TriePutResult(ResultAction.DELETE, pos, putResult.sonToDeleteNumberOfSons());
        }

        newNodes[pos] = this.buildNewSonTrie(this, pos, putResult, newNodes[pos]);

        if (newHashes != null)
            newHashes[pos] = null;

        TrieImpl trieToReturn = new TrieImpl(this.arity, this.encodedSharedPath, this.sharedPathLength, this.value, newNodes, newHashes, this.store).withSecure(this.isSecure);

        return new TriePutResult(ResultAction.PUT, trieToReturn);
    }

    private TrieImpl buildNewSonTrie(TrieImpl node, int pos, TriePutResult putResult, TrieImpl oldSon) {

        if (ResultAction.PUT == putResult.getAction()) {
            return putResult.getTrie();
        }

        if (ResultAction.DELETE == putResult.getAction()) {
            return this.buildNewTrieAfterDelete(node, node.nodes[pos], putResult);
        }

        return oldSon;
    }

    private TrieImpl buildNewTrieAfterDelete(TrieImpl grandParent, TrieImpl parent, TriePutResult putResult) {

        TrieImpl newTrie;

        if(putResult.sonToDeleteNumberOfSons() == 0) {
            // a leaf will be deleted
            if (grandParent.getNumberOfChildren() == 2) {
                // leaf to be deleted has one sibling. As a consequence, there is no need
                // for a bifurcation at this level so the parent is merged with the survivor son.
                newTrie = grandParent.deleteSonAndCompress(parent, putResult.getSonToDelete());
            } else {
                // one or more than two sons remain, delete and that's it.
                newTrie = null;
            }
        } else {
            // a node that is not a leaf will be deleted
            if (putResult.sonToDeleteNumberOfSons() == 1) {
                // node to be deleted has only one son so compression needs to be done.
                newTrie = grandParent.deleteSonAndCompressOnlyOneSon(parent, putResult.getSonToDelete());
            } else {
                // node to be deleted has more than one son, value must be set to null.
                newTrie = new TrieImpl(grandParent.arity, grandParent.encodedSharedPath, grandParent.sharedPathLength, null, grandParent.nodes, null, grandParent.store).withSecure(grandParent.isSecure);
            }
        }

        return newTrie;
    }

    private int lengthOfCommonPath(byte[] key, int length, int position, byte[] sharedPath) {
        int k;

        for (k = 0; k < sharedPath.length && position + k < length; k++) {
            if (sharedPath[k] != key[position + k])
                break;
        }

        return k;
    }

    /**
     * Used when 'this' needs to be split because a new key is been inserted.
     * This new key can be a prefix of the one the node has now or vice versa
     * @param sharedBitsBetweenKeys number of shared bits between the new key and the one the node has now.
     * @return a new Trie which is 'this' with its key split by sharedBitsBetweenKeys
     */
    private TrieImpl split(int sharedBitsBetweenKeys) {
        // create a new child trie to store 'this' current value
        TrieImpl[] newChildNodes = this.cloneNodes(false);
        byte[][] newChildHashes = this.cloneHashes();
        TrieImpl newChildTrie = new TrieImpl(this.arity, null, 0, this.value, newChildNodes, newChildHashes, this.store).withSecure(this.isSecure);

        // set shared path for child
        byte[] sharedPath = PathEncoder.decode(this.encodedSharedPath, this.arity, this.sharedPathLength);
        boolean isNewKeyPrefix = sharedPath.length > sharedBitsBetweenKeys + 1;
        if (isNewKeyPrefix) {
            // new child trie shared path = the bits that are not shared between keys
            int newSharedLength = sharedPath.length - sharedBitsBetweenKeys - 1;
            this.setSharedPath(newChildTrie, sharedBitsBetweenKeys + 1, sharedPath, newSharedLength);
        }

        // create a new parent for the recently built child
        TrieImpl newTrie = new TrieImpl(this.arity, this.store, this.isSecure);
        TrieImpl[] newNodes = new TrieImpl[this.arity];
        int pos = sharedPath[sharedBitsBetweenKeys];
        newNodes[pos] = newChildTrie;
        newTrie.nodes = newNodes;

        // set shared path for parent
        if (sharedBitsBetweenKeys > 0) {
            this.setSharedPath(newTrie, 0, sharedPath, sharedBitsBetweenKeys);
        }

        return newTrie;
    }

    private void setSharedPath(TrieImpl newChildTrie, int pos, byte[] sharedPath, int newSharedLength) {
        byte[] newShared = new byte[newSharedLength];
        System.arraycopy(sharedPath, pos, newShared, 0, newSharedLength);
        newChildTrie.encodedSharedPath = PathEncoder.encode(newShared, this.arity);
        newChildTrie.sharedPathLength = newSharedLength;
    }

    private TrieImpl deleteSonAndCompressOnlyOneSon(TrieImpl parent, int sonToDeletePos) {

        TrieImpl sonToDelete = parent.nodes[sonToDeletePos];

        // search for the son that is not going to be deleted
        int grandSonToKeepPos = 0;
        for(int i = 0; i < sonToDelete.nodes.length; i++) {
            if(sonToDelete.nodes[i] != null) {
                grandSonToKeepPos = i;
            }
        }
        TrieImpl grandSonToKeep = sonToDelete.nodes[grandSonToKeepPos];

        // get prefix and suffix to compress
        byte[] decodedPrefix = sonToDelete.sharedPathLength == 0 ? new byte[] {} : PathEncoder.decode(sonToDelete.encodedSharedPath, sonToDelete.arity, sonToDelete.sharedPathLength);
        byte[] decodedSuffix = grandSonToKeep.sharedPathLength == 0 ? new byte[] {} : PathEncoder.decode(grandSonToKeep.encodedSharedPath, grandSonToKeep.arity, grandSonToKeep.sharedPathLength);
        byte[] newDecode = new byte[decodedPrefix.length + 1 + decodedSuffix.length];

        // build new shared path because of compression action
        System.arraycopy(decodedPrefix, 0, newDecode, 0, decodedPrefix.length);
        newDecode[decodedPrefix.length] = (byte)grandSonToKeepPos;
        System.arraycopy(decodedSuffix, 0, newDecode, decodedPrefix.length + 1, decodedSuffix.length);
        byte[] newEncode = PathEncoder.encode(newDecode, sonToDelete.arity);

        TrieImpl[] newNodes = this.cloneNodes(grandSonToKeep, false);
        byte[][] newHashes = this.cloneHashes(grandSonToKeep);
        TrieImpl newSon = new TrieImpl(grandSonToKeep.arity, newEncode, decodedPrefix.length + 1 + decodedSuffix.length, grandSonToKeep.value, newNodes, newHashes, grandSonToKeep.store).withSecure(grandSonToKeep.isSecure);
        TrieImpl[] newParentSons = this.cloneNodesNullSafe(parent);
        newParentSons[sonToDeletePos] = newSon;

        return new TrieImpl(parent.arity, parent.encodedSharedPath, parent.sharedPathLength, parent.value, newParentSons, null, parent.store).withSecure(parent.isSecure);
    }

    private TrieImpl deleteSonAndCompress(TrieImpl parentToReplace, int sonToDelete) {

        // search for the son that is not going to be deleted
        int sonToKeepPos = 0;
        for(int i = 0; i < parentToReplace.nodes.length; i++) {
            if(parentToReplace.nodes[i] != null && i != sonToDelete) {
                sonToKeepPos = i;
            }
        }
        TrieImpl sonToKeep = parentToReplace.nodes[sonToKeepPos];

        // get prefix and suffix to compress
        byte[] decodedPrefix = parentToReplace.sharedPathLength == 0 ? new byte[] {} : PathEncoder.decode(parentToReplace.encodedSharedPath, parentToReplace.arity, parentToReplace.sharedPathLength);
        byte[] decodedSuffix = sonToKeep.sharedPathLength == 0 ? new byte[] {} : PathEncoder.decode(sonToKeep.encodedSharedPath, sonToKeep.arity, sonToKeep.sharedPathLength);
        byte[] newDecode = new byte[decodedPrefix.length + 1 + decodedSuffix.length];

        // build new shared path because of compression action
        System.arraycopy(decodedPrefix, 0, newDecode, 0, decodedPrefix.length);
        newDecode[decodedPrefix.length] = (byte)sonToKeepPos;
        System.arraycopy(decodedSuffix, 0, newDecode, decodedPrefix.length + 1, decodedSuffix.length);
        byte[] newEncode = PathEncoder.encode(newDecode, sonToKeep.arity);

        return new TrieImpl(sonToKeep.arity, newEncode, decodedPrefix.length + 1 + decodedSuffix.length, sonToKeep.value, sonToKeep.nodes, sonToKeep.hashes, sonToKeep.store).withSecure(sonToKeep.isSecure);
    }

    /**
     * cloneHashes clone the internal subnode hashes cache
     *
     * @return a copy of the original hashes
     */
    private byte[][] cloneHashes() {
        return this.cloneHashes(this);
    }

    private byte[][] cloneHashes(TrieImpl nodeToClone) {
        if (nodeToClone.hashes == null)
            return null;

        int nhashes = nodeToClone.hashes.length;
        byte[][] newHashes = new byte[nhashes][];

        System.arraycopy(nodeToClone.hashes, 0, newHashes, 0, nhashes);

        return newHashes;
    }

    /**
     * cloneNodes clone the current list of nodes
     *
     * @param create    create an empty list if the current list is null
     *
     * @return the new list of nodes
     */
    private TrieImpl[] cloneNodes(boolean create) {
        return  this.cloneNodes(this, create);
    }

    private TrieImpl[] cloneNodes(TrieImpl nodeToClone, boolean create) {
        if (nodeToClone.nodes == null && !create)
            return null;

        return this.cloneNodesNullSafe(nodeToClone);
    }

    private TrieImpl[] cloneNodesNullSafe(TrieImpl nodeToClone) {
        TrieImpl[] newNodes = new TrieImpl[nodeToClone.arity];

        if (nodeToClone.nodes != null) {
            System.arraycopy(nodeToClone.nodes, 0, newNodes, 0, nodeToClone.arity);
        }

        return newNodes;
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
    private static boolean isEmptyTrie(byte[] value, TrieImpl[] nodes, byte[][]hashes) {
        if (value != null && value.length != 0)
            return false;

        if (nodes != null) {
            for (int k = 0; k < nodes.length; k++)
                if (nodes[k] != null)
                    return false;
        }

        if (hashes != null) {
            for (int k = 0; k < hashes.length; k++)
                if (hashes[k] != null)
                    return false;
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
    public static byte[] bytesToKey(byte[] bytes, int arity) {
        int factor = 8;
        int mask = 0x01;
        int nbits = 1;

        if (arity == 4) {
            factor = 4;
            mask = 0x03;
            nbits = 2;
        }
        else if (arity == 16) {
            factor = 2;
            mask = 0x0f;
            nbits = 4;
        }

        byte[] keyBytes = new byte[bytes.length * factor];
        int l = bytes.length;
        int j = 0;

        for (int k = 0; k < l; k++) {
            byte b = bytes[k];

            for (int i = 0; i < factor; i++)
                keyBytes[j++] = (byte) ((b >> (nbits * (factor - i - 1))) & mask);
        }

        return keyBytes;
    }

    @Override
    public Trie getSnapshotTo(byte[] hash) {
        this.save();

        if (Arrays.equals(emptyHash, hash))
            return new TrieImpl(this.store, this.isSecure);

        return this.store.retrieve(hash);
    }

    public TrieStore getStore() {
        return this.store;
    }

    private static int getEncodedPathLength(int length, int arity) {
        if (arity == 2)
            return length / 8 + (length % 8 == 0 ? 0 : 1);

        if (arity == 16)
            return length / 2 + (length % 2 == 0 ? 0 : 1);

        return 0;
    }

    /**
     * makeEmpyHash creates the hash associated to empty nodes
     *
     * @return a hash with zeroed bytes
     */
    private static byte[] makeEmptyHash() {
        return sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    }
}
