/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

import co.rsk.core.types.ints.Uint24;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class TrieChunkIterator implements Iterator<TrieChunk> {

    private final int maxChunkSize;

    private final Trie root;

    private final Deque<IterationElement> visiting = new LinkedList<>();

    public TrieChunkIterator(@Nonnull Trie root, @Nullable TrieKeySlice fromKey, int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        this.root = root;
        fillVisitingList(this.visiting, root, fromKey);
    }

    @Override
    public boolean hasNext() {
        return !visiting.isEmpty();
    }

    @Override
    public TrieChunk next() {
        if (!hasNext()) {
            throw new IllegalStateException("No more elements to iterate over");
        }

        LinkedHashMap<byte[], byte[]> keyValues = new LinkedHashMap<>();
        TrieChunk.Proof proof = TrieChunk.Proof.EMPTY;

        while (!visiting.isEmpty()) {
            IterationElement element = visiting.pop();
            Trie node = element.getNode();
            if (node.getValueLength().compareTo(Uint24.ZERO) > 0) {
                keyValues.put(element.getNodeKey().encode(), node.getValue());
            }

            if (!node.getRight().isEmpty()) {
                Trie rightNode = Trie.fromRef(node.getRight());
                TrieKeySlice rightKey = element.getNodeKey().rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
                visiting.push(new IterationElement(rightKey, rightNode));
            }
            if (!node.getLeft().isEmpty()) {
                Trie leftNode = Trie.fromRef(node.getLeft());
                TrieKeySlice leftKey = element.getNodeKey().rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
                visiting.push(new IterationElement(leftKey, leftNode));
            }

            if (keyValues.size() >= maxChunkSize) {
                Optional<TrieChunk.Proof> proofOpt = TrieChunk.Proof.from(this.root, element.getNodeKey());
                if (proofOpt.isEmpty()) {
                    throw new IllegalStateException("No proof found for node: " + this.root.getHash() + " at key: " + element.getNodeKey());
                }

                proof = proofOpt.get();
                break;
            }
        }

        return new TrieChunk(keyValues, proof);
    }

    private static void fillVisitingList(@Nonnull Deque<IterationElement> visiting, @Nonnull Trie root, @Nullable TrieKeySlice fromKey) {
        if (fromKey == null) {
            visiting.push(new IterationElement(root.getSharedPath(), root));
            return;
        }

        List<Trie> nodes = root.findNodes(fromKey);
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        TrieKeySlice currentKey = root.getSharedPath();
        for (int i = nodes.size() - 1; i > 0; i--) {
            Trie node = nodes.get(i);
            NodeReference leftNode = node.getLeft();
            NodeReference rightNode = node.getRight();

            Trie childNode = nodes.get(i - 1);
            if (childNode.getHash().equals(leftNode.getHash().orElse(null))) {
                if (!rightNode.isEmpty()) {
                    Trie rightTrie = Trie.fromRef(rightNode);
                    visiting.push(new IterationElement(currentKey.rebuildSharedPath((byte) 0x01, rightTrie.getSharedPath()), rightTrie));
                }

                currentKey = currentKey.rebuildSharedPath((byte) 0x00, childNode.getSharedPath());
            } else if (childNode.getHash().equals(rightNode.getHash().orElse(null))) {
                currentKey = currentKey.rebuildSharedPath((byte) 0x01, childNode.getSharedPath());
            }
        }

        Trie node = nodes.get(0);

        NodeReference rightNode = node.getRight();
        if (!rightNode.isEmpty()) {
            Trie rightTrie = Trie.fromRef(rightNode);
            visiting.push(new IterationElement(currentKey.rebuildSharedPath((byte) 0x01, rightTrie.getSharedPath()), rightTrie));
        }

        NodeReference leftNode = node.getLeft();
        if (!leftNode.isEmpty()) {
            Trie leftTrie = Trie.fromRef(leftNode);
            visiting.push(new IterationElement(currentKey.rebuildSharedPath((byte) 0x00, leftTrie.getSharedPath()), leftTrie));
        }
    }
}
