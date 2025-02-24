/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import co.rsk.core.types.ints.Uint8;
import co.rsk.crypto.Keccak256;
import co.rsk.util.NodeStopper;
import org.ethereum.crypto.Keccak256Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

public class NodeReference {

    private static final Logger logger = LoggerFactory.getLogger(NodeReference.class);
    private static final NodeReference EMPTY = new NodeReference(null, null, null);

    private final TrieStore store;
    private final NodeStopper nodeStopper;

    private Trie lazyNode;
    private Keccak256 lazyHash;

    public NodeReference(TrieStore store, @Nullable Trie node, @Nullable Keccak256 hash, @Nonnull NodeStopper nodeStopper) {
        this.store = store;
        if (node != null && node.isEmptyTrie()) {
            this.lazyNode = null;
            this.lazyHash = null;
        } else {
            this.lazyNode = node;
            this.lazyHash = hash;
        }

        this.nodeStopper = Objects.requireNonNull(nodeStopper);
    }

    public NodeReference(TrieStore store, @Nullable Trie node, @Nullable Keccak256 hash) {
        this(store, node, hash, exitStatus -> System.exit(exitStatus));
    }

    public boolean isEmpty() {
        return lazyHash == null && lazyNode == null;
    }

    /**
     * The node or empty if this is an empty reference.
     * If the node is not present but its hash is known, it will be retrieved from the store.
     * If the node could not be retrieved from the store, the Node is stopped using System.exit(1)
     */
    public Optional<Trie> getNode() {
        if (lazyNode != null) {
            return Optional.of(lazyNode);
        }

        if (lazyHash == null) {
            return Optional.empty();
        }

        Optional<Trie> node = store.retrieve(lazyHash.getBytes());

        // Broken database, can't continue
        if (!node.isPresent()) {
            logger.error("Broken database, execution can't continue");
            nodeStopper.stop(1);
            return Optional.empty();
        }

        lazyNode = node.get();

        return node;
    }

    public Optional<Trie> getNodeDetached() {
        if (lazyNode != null) {
            return Optional.of(lazyNode);
        }

        if (lazyHash == null) {
            return Optional.empty();
        }

        Optional<Trie> node = store.retrieve(lazyHash.getBytes());

        // Broken database, can't continue
        if (!node.isPresent()) {
            logger.error("Broken database, execution can't continue");
            nodeStopper.stop(1);
            return Optional.empty();
        }

        return node;
    }

    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHash() {
        if (lazyHash != null) {
            return Optional.of(lazyHash);
        }

        if (lazyNode == null) {
            return Optional.empty();
        }

        lazyHash = lazyNode.getHash();
        return Optional.of(lazyHash);
    }

    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHashOrchid(boolean isSecure) {
        return getNode().map(trie -> trie.getHashOrchid(isSecure));
    }

    @SuppressWarnings("squid:S2384") // private method knows it can avoid copying the byte[] field
    private byte[] getSerialized() {
        return lazyNode.toMessage();
    }

    public boolean isEmbeddable() {
        // if the node is embeddable then this reference must have a reference in memory
        if (lazyNode == null) {
            return false;
        }
        return lazyNode.isEmbeddable();

    }

    // the referenced node was loaded
    public boolean wasLoaded() {
        return lazyNode != null;
    }

    // This method should only be called from save()
    public int serializedLength() {
        if (!isEmpty()) {
            if (isEmbeddable()) {
                return lazyNode.getMessageLength() + 1;
            }

            return Keccak256Helper.DEFAULT_SIZE_BYTES;
        }

        return 0;
    }

    public void serializeInto(ByteBuffer buffer) {
        if (!isEmpty()) {
            if (isEmbeddable()) {
                byte[] serialized = getSerialized();
                buffer.put(new Uint8(serialized.length).encode());
                buffer.put(serialized);
            } else {
                byte[] hash = getHash().map(Keccak256::getBytes)
                        .orElseThrow(() -> new IllegalStateException("The hash should always exists at this point"));
                buffer.put(hash);
            }
        }
    }

    /**
     * @return the tree size in bytes as specified in RSKIP107 plus the actual serialized size
     *
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * Do not use.
     */
    public long referenceSize() {
        return getNode().map(this::nodeSize).orElse(0L);
    }

    private long nodeSize(Trie trie) {
        long externalValueLength = trie.hasLongValue() ? trie.getValueLength().intValue() : 0L;
        return trie.getChildrenSize().value + externalValueLength + trie.getMessageLength();
    }

    public static NodeReference empty() {
        return EMPTY;
    }
}
