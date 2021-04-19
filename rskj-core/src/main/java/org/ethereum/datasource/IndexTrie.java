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
package org.ethereum.datasource;

import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import co.rsk.trie.TrieKeySlice;
import co.rsk.trie.PathEncoder;
import javax.annotation.Nullable;
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
public class FastTrie {
    static final int nullValue = -1;

    private static final Profiler profiler = ProfilerFactory.getInstance();


    // this node associated value, if any
    private final int value;

    private final FastTrie left;

    private final FastTrie right;

    // shared Path
    private TrieKeySlice sharedPath;

    static FastTrie empty() {
        return new FastTrie();
    }

    public FastTrie() {
        this( TrieKeySlice.empty(), nullValue);
    }

    private FastTrie(TrieKeySlice sharedPath, int value) {
        this( sharedPath, value, null, null);
    }

    public boolean equals(Object obj) {
        FastTrie t2 = (FastTrie) obj;

        if (t2.value!=this.value) return false;
        if (!t2.sharedPath.equalPath(this.sharedPath)) {
            return false;
        }
        return true;
    }

    // full constructor
    private FastTrie( TrieKeySlice sharedPath, int value, FastTrie left, FastTrie right) {
        this.value = value;
        this.left = left;
        this.right = right;
        this.sharedPath = sharedPath;
    }

    /**
     * Deserialize a FastTrie, either using the original format or RSKIP 107 format, based on version flags.
     * The original trie wasted the first byte by encoding the arity, which was always 2. We use this marker to
     * recognize the old serialization format.
     */

    /**
     * get returns the value associated with a key
     *
     * @param key the key associated with the value, a byte array (variable length)
     *
     * @return  the associated value, a byte array, or null if there is no associated value to the key
     */
    @Nullable
    public int get(byte[] key) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.TRIE_GET_VALUE_FROM_KEY);
        FastTrie node = find(key);
        if (node == null) {
            profiler.stop(metric);
            return nullValue;
        }

        int  result = node.getValue();
        profiler.stop(metric);
        return result;
    }

    /**
     * get by string, utility method used from test methods
     *
     * @param key   a string, that is converted to a byte array
     * @return a byte array with the associated value
     */
    public int get(String key) {
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
    public FastTrie put(byte[] key,int value) {
        TrieKeySlice keySlice = TrieKeySlice.fromKey(key);
        FastTrie trie = put(keySlice, value, false);

        return trie == null ? new FastTrie() : trie;
    }

    public FastTrie put(ByteArrayWrapper key, int value) {
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
    public FastTrie put(String key, int value) {
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
    public FastTrie delete(byte[] key) {
        return put(key, nullValue);
    }

    // This is O(1). The node with exact key "key" MUST exists.
    public FastTrie deleteRecursive(byte[] key) {
        TrieKeySlice keySlice = TrieKeySlice.fromKey(key);
        FastTrie trie = put(keySlice, nullValue, true);

        return trie == null ? new FastTrie() : trie;
    }

    /**
     * delete string key, utility method to be used for testing
     *
     * @param key a string
     *
     * @return the new top node of the trie with the key removed
     */
    public FastTrie delete(String key) {
        return delete(key.getBytes(StandardCharsets.UTF_8));
    }

    // key is the key with exactly collectKeyLen bytes.
    // in non-expanded form (binary)
    // special value Integer.MAX_VALUE means collect them all.
    private void collectKeys(Set<ByteArrayWrapper> set, TrieKeySlice key, int collectKeyLen) {
        if (collectKeyLen != Integer.MAX_VALUE && key.length() > collectKeyLen) {
            return;
        }

        boolean shouldCollect = collectKeyLen == Integer.MAX_VALUE || key.length() == collectKeyLen;
        if (value >= 0 && shouldCollect) {
            // convert bit string into byte[]
            set.add(new ByteArrayWrapper(key.encode()));
        }

        for (byte k = 0; k < 2; k++) {
            FastTrie node = this.retrieveNode(k);

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
        int r =1;
        if (left!=null)
            r += this.left.trieSize();
        if (right!=null)
            r+= this.right.trieSize();
        return r;

    }

    /**
     * get retrieves the associated value given the key
     *
     * @param key   full key
     * @return the associated value, null if the key is not found
     *
     */
    @Nullable
    public FastTrie find(byte[] key) {
        return find(TrieKeySlice.fromKey(key));
    }

    @Nullable
    private FastTrie find(TrieKeySlice key) {
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

        FastTrie node = this.retrieveNode(key.get(commonPathLength));
        if (node == null) {
            return null;
        }

        return node.find(key.slice(commonPathLength + 1, key.length()));
    }


    private FastTrie retrieveNode(byte implicitByte) {
        return getNodeReference(implicitByte);
    }

    public FastTrie getNodeReference(byte implicitByte) {
        return implicitByte == 0 ? this.left : this.right;
    }

    public FastTrie getLeft() {
        return left;
    }

    public FastTrie getRight() {
        return right;
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

    static FastTrie coalesce(FastTrie trie) {
        // the following code coalesces nodes if needed for delete operation

        // it's null or it is not a delete operation
        if (trie == null || trie.value >=0) {
            return trie;
        }

        if (trie.isEmptyTrie()) {
            return null;
        }

        // only coalesce if node has only one child and no value
        if (trie.value>=0) {
            return trie;
        }

        if (trie.left!=null  && trie.right!=null) {
            return trie;
        }

        if (trie.left==null && trie.right==null) {
            return trie;
        }

        FastTrie child;
        byte childImplicitByte;
        if (trie.left!=null) {
            child = trie.left;
            childImplicitByte = (byte) 0;
        } else { // has right node
            child = trie.right;
            childImplicitByte = (byte) 1;
        }

        TrieKeySlice newSharedPath = trie.sharedPath.rebuildSharedPath(childImplicitByte, child.sharedPath);
        return new FastTrie(newSharedPath, child.value, child.left, child.right);
    }

    private FastTrie put(TrieKeySlice key, int value, boolean isRecursiveDelete) {

        FastTrie trie = this.internalPut(key, value, isRecursiveDelete);

        // If it's not deletion, there is no need to coalesce
        if (value>=0)
            return trie;
        return coalesce(trie);

    }

    private FastTrie internalPut(TrieKeySlice key, int value, boolean isRecursiveDelete) {
        TrieKeySlice commonPath = key.commonPath(sharedPath);
        if (commonPath.length() < sharedPath.length()) {
            // when we are removing a key we know splitting is not necessary. the key wasn't found at this point.
            if (value <0) {
                return this;
            }

            return this.split(commonPath).put(key, value, isRecursiveDelete);
        }

        if (sharedPath.length() >= key.length()) {
            // To compare values we need to retrieve the previous value
            // if not already done so. We could also compare by hash, to avoid retrieval
            // We do a small optimization here: if sizes are not equal, then values
            // obviously are not.
            if (this.getValue()==value) {
                return this;
            }

            if (isRecursiveDelete) {
                return new FastTrie(this.sharedPath, nullValue);
            }

            if (isEmptyTrie(value, this.left, this.right)) {
                return null;
            }

            return new FastTrie(
                    this.sharedPath,
                    value,
                    this.left,
                    this.right
            );
        }

        if (isEmptyTrie()) {
            return new FastTrie( key, value);
        }

        // this bit will be implicit and not present in a shared path
        byte pos = key.get(sharedPath.length());

        FastTrie node = retrieveNode(pos);
        if (node == null) {
            node = new FastTrie();
        }

        TrieKeySlice subKey = key.slice(sharedPath.length() + 1, key.length());
        FastTrie newNode = node.put(subKey, value, isRecursiveDelete);

        // reference equality
        if (newNode == node) {
            return this;
        }

        FastTrie newLeft;
        FastTrie newRight;
        if (pos == 0) {
            newLeft = newNode;
            newRight = this.right;
        } else {
            newLeft = this.left;
            newRight = newNode;
        }

        if (isEmptyTrie(this.value, newLeft, newRight)) {
            return null;
        }

        return new FastTrie(this.sharedPath, this.value, newLeft, newRight);
    }

    private FastTrie split(TrieKeySlice commonPath) {
        int commonPathLength = commonPath.length();
        TrieKeySlice newChildSharedPath = sharedPath.slice(commonPathLength + 1, sharedPath.length());
        FastTrie newChildTrie = new FastTrie(newChildSharedPath, this.value, this.left, this.right);

        // this bit will be implicit and not present in a shared path
        byte pos = sharedPath.get(commonPathLength);

        FastTrie newLeft;
        FastTrie newRight;
        if (pos == 0) {
            newLeft = newChildTrie;
            newRight = null;
        } else {
            newLeft = null;
            newRight = newChildTrie;
        }

        return new FastTrie(commonPath, nullValue, newLeft, newRight);
    }

    public boolean isTerminal() {
        return this.left.isEmpty() && this.right.isEmpty();
    }

    public boolean isEmpty() {
        return isEmptyTrie(this.value, this.left, this.right);
    }

    public boolean isEmptyTrie() {
        return isEmptyTrie(this.value, this.left, this.right);
    }

    private static boolean isEmptyTrieRecursive(FastTrie node) {
        if (node==null)
            return true;
        return (isEmptyTrieRecursive(node.value,node.left,node.right));
    }
    /**
     * isEmptyTrie checks the existence of subnodes, subnodes hashes or value
     *
     * @param value     value
     * @param left      a reference to the left node
     * @param right     a reference to the right node
     *
     * @return true if no data
     */

    private static boolean isEmptyTrie(int value,FastTrie left, FastTrie right) {
        if (value>=0)
            return false;
        if (left!=null)
            return false;
        if (right!=null)
            return false;
        return true;

    }
    private static boolean isEmptyTrieRecursive(int value,FastTrie left, FastTrie right) {
        if (value>=0)
            return false;
        if (!isEmptyTrieRecursive(left))
            return false;
        if (!isEmptyTrieRecursive(right))
            return false;
        return true;

    }

    public int getValue() {
        return value;
    }
    public TrieKeySlice getSharedPath() {
        return sharedPath;
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
    /*
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

        FastTrie otherTrie = (FastTrie) other;
        return getHash().equals(otherTrie.getHash());
    }
    */
    @Override
    public String toString() {
        String s = printParam("TRIE: ", "value", getValue());
        return s;
    }

    private String printParam(String s, String name, int  param) {
            s += name + ": " +param + "\n";
        return s;
    }

    /**
     * Returns the leftmost node that has not yet been visited that node is normally on top of the stack
     */
    private static class InOrderIterator implements Iterator<IterationElement> {

        private final Deque<IterationElement> visiting;

        public InOrderIterator(FastTrie root) {
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
            FastTrie node = visitingElement.getNode();
            // if the node has a right child, its leftmost node is next
            FastTrie rightNode = node.retrieveNode((byte) 0x01);
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
        private void pushLeftmostNode(TrieKeySlice nodeKey, FastTrie node) {
            // find the leftmost node
            FastTrie leftNode = node.retrieveNode((byte) 0x00);
            if (leftNode != null) {
                TrieKeySlice leftNodeKey = nodeKey.rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
                visiting.push(new IterationElement(leftNodeKey, leftNode)); // push the left node
                pushLeftmostNode(leftNodeKey, leftNode); // recurse on next left node
            }
        }
    }

    private class PreOrderIterator implements Iterator<IterationElement> {

        private final Deque<IterationElement> visiting;

        public PreOrderIterator(FastTrie root) {
            Objects.requireNonNull(root);
            TrieKeySlice traversedPath = root.getSharedPath();
            this.visiting = new LinkedList<>();
            this.visiting.push(new IterationElement(traversedPath, root));
        }

        @Override
        @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#pop()} when it's empty
        public IterationElement next() {
            IterationElement visitingElement = visiting.pop();
            FastTrie node = visitingElement.getNode();
            TrieKeySlice nodeKey = visitingElement.getNodeKey();
            // need to visit the left subtree first, then the right since a stack is a LIFO, push the right subtree first,
            // then the left
            FastTrie rightNode = node.retrieveNode((byte) 0x01);
            if (rightNode != null) {
                TrieKeySlice rightNodeKey = nodeKey.rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
                visiting.push(new IterationElement(rightNodeKey, rightNode));
            }
            FastTrie leftNode = node.retrieveNode((byte) 0x00);
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

        public PostOrderIterator(FastTrie root) {
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
            FastTrie node = visitingElement.getNode();
            FastTrie rightNode = node.retrieveNode((byte) 0x01);
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
        private void pushLeftmostNodeRecord(TrieKeySlice nodeKey, FastTrie node) {
            // find the leftmost node
            FastTrie leftNode = node.retrieveNode((byte) 0x00);
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
        private final FastTrie node;

        public IterationElement(final TrieKeySlice nodeKey, final FastTrie node) {
            this.nodeKey = nodeKey;
            this.node = node;
        }

        public FastTrie getNode() {
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

        public boolean isPresent() {
            return lshared > 0;
        }
    }

    // Additional auxiliary methods for Merkle Proof

    @Nullable
    public List<FastTrie> getNodes(byte[] key) {
        return findNodes(key);
    }

    @Nullable
    public List<FastTrie> getNodes(String key) {
        return this.getNodes(key.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private List<FastTrie> findNodes(byte[] key) {
        return findNodes(TrieKeySlice.fromKey(key));
    }

    @Nullable
    private List<FastTrie> findNodes(TrieKeySlice key) {
        if (sharedPath.length() > key.length()) {
            return null;
        }

        int commonPathLength = key.commonPath(sharedPath).length();

        if (commonPathLength < sharedPath.length()) {
            return null;
        }

        if (commonPathLength == key.length()) {
            List<FastTrie> nodes = new ArrayList<>();
            nodes.add(this);
            return nodes;
        }

        FastTrie node = this.retrieveNode(key.get(commonPathLength));

        if (node == null) {
            return null;
        }

        List<FastTrie> subnodes = node.findNodes(key.slice(commonPathLength + 1, key.length()));

        if (subnodes == null) {
            return null;
        }

        subnodes.add(this);

        return subnodes;
    }
}
