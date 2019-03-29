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

import co.rsk.crypto.Keccak256;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

public class NodeReference {
    private final TrieStore store;

    private Trie lazyNode;
    private Keccak256 lazyHash;

    public NodeReference(TrieStore store, @Nullable Trie node, @Nullable Keccak256 hash) {
        this.store = store;
        if (node != null && node.isEmptyTrie()) {
            this.lazyNode = null;
            this.lazyHash = null;
        } else {
            this.lazyNode = node;
            this.lazyHash = hash;
        }
    }

    public boolean isEmpty() {
        return lazyHash == null && lazyNode == null;
    }

    /**
     * If the node is not present, this is either an empty reference, or the hash points to a node already in storage.
     */
    public void save() {
        if (lazyNode != null) {
            lazyNode.save();
        }
    }

    /**
     * The node or empty if this is an empty reference.
     * If the node is not present but its hash is known, it will be retrieved from the store.
     */
    public Optional<Trie> getNode() {
        if (lazyNode != null) {
            return Optional.of(lazyNode);
        }

        if (lazyHash == null) {
            return Optional.empty();
        }

        lazyNode = Objects.requireNonNull(
                store.retrieve(lazyHash.getBytes()),
                "The node with this hash is not present in the trie store"
        );
        return Optional.of(lazyNode);
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

    public static NodeReference empty() {
        return new NodeReference(null, null, null);
    }
}
