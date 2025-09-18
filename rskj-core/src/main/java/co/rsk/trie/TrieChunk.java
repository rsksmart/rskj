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

import co.rsk.crypto.Keccak256;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record TrieChunk(@Nonnull LinkedHashMap<byte[], byte[]> keyValues, @Nonnull TrieChunk.Proof proof) {

    public static final int MAX_CHUNK_SIZE = 1024;
    public static final int MAX_KEY_SIZE = 1024; // TODO -> review this value

    public byte[] encode() {
        // Encode key-value pairs as a list of [key, value] pairs
        byte[][] keyValuePairs = new byte[keyValues.size() * 2][];
        int index = 0;
        for (Map.Entry<byte[], byte[]> entry : keyValues.entrySet()) {
            keyValuePairs[index++] = RLP.encodeElement(entry.getKey());
            keyValuePairs[index++] = RLP.encodeElement(entry.getValue());
        }
        byte[] rlpKeyValues = RLP.encodeList(keyValuePairs);
        
        // Encode proof
        byte[] rlpProof = RLP.encodeElement(proof.encode());
        
        // Combine into a list: [keyValues, proof]
        return RLP.encodeList(rlpKeyValues, rlpProof);
    }

    public static TrieChunk decode(RLPList list) {
        if (list.size() != 2) {
            throw new IllegalArgumentException("Invalid TrieChunk RLP structure: expected 2 elements, got " + list.size());
        }
        
        // Decode key-value pairs
        RLPList keyValuesRLP = (RLPList) RLP.decode2(list.get(0).getRLPData()).get(0);
        if (keyValuesRLP.size() % 2 != 0) {
            throw new IllegalArgumentException("Invalid key-value pairs: expected even number of elements, got " + keyValuesRLP.size());
        }
        
        LinkedHashMap<byte[], byte[]> keyValues = new LinkedHashMap<>();
        for (int i = 0; i < keyValuesRLP.size(); i += 2) {
            byte[] key = keyValuesRLP.get(i).getRLPData();
            byte[] value = keyValuesRLP.get(i + 1).getRLPData();
            if (key == null) {
                key = new byte[0];
            }
            if (value == null) {
                value = new byte[0];
            }
            keyValues.put(key, value);
        }
        
        // Decode proof
        byte[] proofData = list.get(1).getRLPData();
        if (proofData == null) {
            proofData = new byte[0];
        }
        RLPList proofRLP = (RLPList) RLP.decode2(proofData).get(0);
        TrieChunk.Proof proof = TrieChunk.Proof.decode(proofRLP);
        
        return new TrieChunk(keyValues, proof);
    }

    public record Proof (@Nonnull List<ProofNode> proofNodes, @Nullable Trie leftChildNode, @Nullable Trie rightChildNode) {

        public static final Proof EMPTY = new Proof(Collections.emptyList(), null, null);

        public boolean isEmpty() {
            return proofNodes.isEmpty() && leftChildNode == null && rightChildNode == null;
        }

        public boolean verify(@Nonnull Keccak256 expectedTrieHash, @Nonnull Trie root) {
            Objects.requireNonNull(expectedTrieHash);
            Objects.requireNonNull(root);
            Objects.requireNonNull(proofNodes);

            return applyProof(root, new LinkedList<>(proofNodes), leftChildNode, rightChildNode)
                    .map(trie -> expectedTrieHash.equals(trie.getHash()))
                    .orElse(false);
        }

        private Optional<Trie> applyProof(@Nonnull Trie trie,
                                          @Nonnull LinkedList<ProofNode> proofNodes,
                                          @Nullable Trie leftChildNode, @Nullable Trie rightChildNode) {
            if (proofNodes.isEmpty()) {
                if (leftChildNode == null && rightChildNode == null) {
                    return Optional.of(trie);
                }
                return Optional.of(new Trie(null, trie.getSharedPath(), trie.getValue(),
                        leftChildNode != null ? new NodeReference(null, leftChildNode, null) : trie.getLeft(),
                        rightChildNode != null ? new NodeReference(null, rightChildNode, null) : trie.getRight(),
                        trie.getValueLength(), trie.getValueHash()));
            }

            ProofNode proofNode = proofNodes.poll();

            if (proofNode.sharedPathLength() < trie.getSharedPath().length()) {
                trie = trie.split(trie.getSharedPath().slice(0, proofNode.sharedPathLength()));
            }

            if (proofNode.rightNode() != null && !trie.getRight().isEmpty()) {
                return Optional.empty();
            }

            NodeReference leftNodeRef;
            NodeReference rightNodeRef;

            if (trie.getRight().isEmpty()) {
                if (trie.getLeft().isEmpty()) {
                    return Optional.empty();
                }

                Optional<Trie> leftNodeOpt = applyProof(Trie.fromRef(trie.getLeft()), proofNodes, leftChildNode, rightChildNode);
                if (leftNodeOpt.isEmpty()) {
                    return Optional.empty();
                }

                leftNodeRef = new NodeReference(null, leftNodeOpt.get(), null);
                rightNodeRef = proofNode.rightNode() != null
                        ? new NodeReference(null, proofNode.rightNode(), null)
                        : trie.getRight();
            } else {
                Optional<Trie> rightNodeOpt = applyProof(Trie.fromRef(trie.getRight()), proofNodes, leftChildNode, rightChildNode);
                if (rightNodeOpt.isEmpty()) {
                    return Optional.empty();
                }

                leftNodeRef = trie.getLeft();
                rightNodeRef = new NodeReference(null, rightNodeOpt.get(), null);
            }

            return Optional.of(new Trie(null, trie.getSharedPath(), trie.getValue(),
                    leftNodeRef, rightNodeRef,
                    trie.getValueLength(), trie.getValueHash()));
        }

        public byte[] encode() {
            byte[][] proofNodeEncodings = new byte[proofNodes.size()][];
            for (int i = 0; i < proofNodes.size(); i++) {
                proofNodeEncodings[i] = proofNodes.get(i).encode();
            }
            byte[] rlpProofNodes = RLP.encodeList(proofNodeEncodings);

            byte[] rlpLeftChildNode = RLP.encodeElement(leftChildNode != null ? leftChildNode.toMessage() : null);
            byte[] rlpRightChildNode = RLP.encodeElement(rightChildNode != null ? rightChildNode.toMessage() : null);

            return RLP.encodeList(rlpProofNodes, rlpLeftChildNode, rlpRightChildNode);
        }

        public static Proof decode(RLPList list) {
            if (list.size() != 3) {
                throw new IllegalArgumentException("Invalid Proof RLP structure: expected 3 elements, got " + list.size());
            }

            RLPList proofNodesRLP = (RLPList) RLP.decode2(list.get(0).getRLPData()).get(0);
            List<ProofNode> proofNodes = new ArrayList<>();
            for (int i = 0; i < proofNodesRLP.size(); i++) {
                proofNodes.add(ProofNode.decode((RLPList) proofNodesRLP.get(i)));
            }

            byte[] leftChildNodeRlp = list.get(1).getRLPData();
            Trie leftChildNode = leftChildNodeRlp != null ? Trie.fromMessage(leftChildNodeRlp, null) : null;

            byte[] rightChildNodeRlp = list.get(2).getRLPData();
            Trie rightChildNode = rightChildNodeRlp != null ? Trie.fromMessage(rightChildNodeRlp, null) : null;

            return new Proof(proofNodes, leftChildNode, rightChildNode);
        }

        public static Optional<Proof> from(@Nonnull Trie root, @Nonnull TrieKeySlice lastNodeKey) {
            List<Trie> nodes = root.findNodes(lastNodeKey);
            if (nodes == null || nodes.isEmpty()) {
                return Optional.empty();
            }

            List<ProofNode> proofNodes = new ArrayList<>();
            Trie leftChildNode = null;
            Trie rightChildNode = null;

            Trie node = nodes.get(0);
            if (!node.getLeft().isEmpty()) {
                leftChildNode = Trie.fromRef(node.getLeft());
            }
            if (!node.getRight().isEmpty()) {
                rightChildNode = Trie.fromRef(node.getRight());
            }

            for (int i = 1; i < nodes.size(); i++) {
                node = nodes.get(i);

                Trie rightNode = null;
                Optional<Keccak256> rightNodeHashOpt = node.getRight().getHash();
                if (rightNodeHashOpt.isPresent() && !rightNodeHashOpt.get().equals(nodes.get(i - 1).getHash())) {
                    rightNode = Trie.fromRef(node.getRight());
                }
                proofNodes.add(new ProofNode(node.getSharedPath().length(), rightNode));
            }

            Collections.reverse(proofNodes);
            return Optional.of(new Proof(proofNodes, leftChildNode, rightChildNode));
        }
    }

    public record ProofNode (int sharedPathLength, @Nullable Trie rightNode) {

        public byte[] encode() {
            byte[] rlpSharedPathLength = RLP.encodeInt(sharedPathLength);
            byte[] rlpRightNode = RLP.encodeElement(rightNode != null ? rightNode.toMessage() : null);
            return RLP.encodeList(rlpSharedPathLength, rlpRightNode);
        }

        public static ProofNode decode(RLPList list) {
            if (list.size() != 2) {
                throw new IllegalArgumentException("Invalid ProofNode RLP structure: expected 2 elements, got " + list.size());
            }

            int sharedPathLength = RLP.decodeInt(list.get(0).getRLPRawData(), 0);

            byte[] rightNodeRlp = list.get(1).getRLPData();
            Trie rightNode = null;
            if (rightNodeRlp != null) {
                rightNode = Trie.fromMessage(rightNodeRlp, null);
            }
            return new ProofNode(sharedPathLength, rightNode);
        }
    }
}
