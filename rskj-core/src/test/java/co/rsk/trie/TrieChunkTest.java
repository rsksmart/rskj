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
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrieChunkTest {

    private TrieStore trieStore;

    @BeforeEach
    void setUp() {
        HashMapDB map = new HashMapDB();
        this.trieStore = new TrieStoreImpl(map);
    }

    @Test
    void testTrieChunkConstruction() {
        LinkedHashMap<byte[], byte[]> keyValues = new LinkedHashMap<>();
        keyValues.put("key1".getBytes(), "value1".getBytes());
        keyValues.put("key2".getBytes(), "value2".getBytes());
        
        TrieChunk.Proof proof = TrieChunk.Proof.EMPTY;
        TrieChunk chunk = new TrieChunk(keyValues, proof);
        
        assertNotNull(chunk.keyValues());
        assertEquals(2, chunk.keyValues().size());
        assertSame(proof, chunk.proof());
    }

    @Test
    void testTrieChunkEncodeDecodeWithEmptyProof() {
        LinkedHashMap<byte[], byte[]> keyValues = new LinkedHashMap<>();
        keyValues.put("key1".getBytes(), "value1".getBytes());
        keyValues.put("key2".getBytes(), "value2".getBytes());
        
        TrieChunk originalChunk = new TrieChunk(keyValues, TrieChunk.Proof.EMPTY);
        
        // Encode and decode
        byte[] encoded = originalChunk.encode();
        RLPList rlpList = (RLPList) RLP.decode2(encoded).get(0);
        TrieChunk decodedChunk = TrieChunk.decode(rlpList);
        
        // Verify key-values are preserved
        assertEquals(originalChunk.keyValues().size(), decodedChunk.keyValues().size());
        for (Map.Entry<byte[], byte[]> entry : originalChunk.keyValues().entrySet()) {
            boolean found = false;
            for (Map.Entry<byte[], byte[]> decodedEntry : decodedChunk.keyValues().entrySet()) {
                if (Arrays.equals(entry.getKey(), decodedEntry.getKey()) && 
                    Arrays.equals(entry.getValue(), decodedEntry.getValue())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Key-value pair not found in decoded chunk");
        }
        
        // Verify proof
        assertTrue(decodedChunk.proof().isEmpty());
    }

    @Test
    void testTrieChunkEncodeDecodeEmptyKeyValues() {
        LinkedHashMap<byte[], byte[]> emptyKeyValues = new LinkedHashMap<>();
        TrieChunk originalChunk = new TrieChunk(emptyKeyValues, TrieChunk.Proof.EMPTY);
        
        byte[] encoded = originalChunk.encode();
        RLPList rlpList = (RLPList) RLP.decode2(encoded).get(0);
        TrieChunk decodedChunk = TrieChunk.decode(rlpList);
        
        assertTrue(decodedChunk.keyValues().isEmpty());
        assertTrue(decodedChunk.proof().isEmpty());
    }

    @Test
    void testTrieChunkDecodeInvalidStructure() {
        // Create invalid RLP with wrong number of elements
        byte[][] elements = new byte[][]{RLP.encodeList(), RLP.encodeElement(new byte[0]), RLP.encodeElement(new byte[0])};
        byte[] invalidRlp = RLP.encodeList(elements);
        RLPList invalidList = (RLPList) RLP.decode2(invalidRlp).get(0);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> TrieChunk.decode(invalidList)
        );
        assertTrue(exception.getMessage().contains("expected 2 elements"));
    }

    @Test
    void testProofEmpty() {
        TrieChunk.Proof proof = TrieChunk.Proof.EMPTY;
        
        assertTrue(proof.isEmpty());
        assertTrue(proof.proofNodes().isEmpty());
        assertNull(proof.leftChildNode());
        assertNull(proof.rightChildNode());
    }

    @Test
    void testProofWithNodes() {
        Trie leftChild = new Trie(trieStore).put("left", "value".getBytes());
        Trie rightChild = new Trie(trieStore).put("right", "value".getBytes());
        List<TrieChunk.ProofNode> nodes = Arrays.asList(
            new TrieChunk.ProofNode(5, null),
            new TrieChunk.ProofNode(10, rightChild)
        );
        
        TrieChunk.Proof proof = new TrieChunk.Proof(nodes, leftChild, rightChild);
        
        assertFalse(proof.isEmpty());
        assertEquals(2, proof.proofNodes().size());
        assertNotNull(proof.leftChildNode());
        assertNotNull(proof.rightChildNode());
    }

    @Test
    void testProofEncodeDecodeEmpty() {
        TrieChunk.Proof originalProof = TrieChunk.Proof.EMPTY;
        
        byte[] encoded = originalProof.encode();
        RLPList rlpList = (RLPList) RLP.decode2(encoded).get(0);
        TrieChunk.Proof decodedProof = TrieChunk.Proof.decode(rlpList);
        
        assertTrue(decodedProof.isEmpty());
        assertTrue(decodedProof.proofNodes().isEmpty());
        assertNull(decodedProof.leftChildNode());
        assertNull(decodedProof.rightChildNode());
    }

    @Test
    void testProofDecodeInvalidStructure() {
        // Create invalid RLP with wrong number of elements
        byte[][] elements = new byte[][]{RLP.encodeList(), RLP.encodeElement(new byte[0])};
        byte[] invalidRlp = RLP.encodeList(elements);
        RLPList invalidList = (RLPList) RLP.decode2(invalidRlp).get(0);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> TrieChunk.Proof.decode(invalidList)
        );
        assertTrue(exception.getMessage().contains("expected 3 elements"));
    }

    @Test
    void testProofNodeConstruction() {
        Trie rightNode = new Trie(trieStore).put("test", "value".getBytes());
        TrieChunk.ProofNode proofNode = new TrieChunk.ProofNode(10, rightNode);
        
        assertEquals(10, proofNode.sharedPathLength());
        assertSame(rightNode, proofNode.rightNode());
    }

    @Test
    void testProofNodeEncodeDecodeWithRightNode() {
        Trie rightNode = new Trie(trieStore).put("test", "value".getBytes());
        TrieChunk.ProofNode originalNode = new TrieChunk.ProofNode(15, rightNode);
        
        byte[] encoded = originalNode.encode();
        RLPList rlpList = (RLPList) RLP.decode2(encoded).get(0);
        TrieChunk.ProofNode decodedNode = TrieChunk.ProofNode.decode(rlpList);
        
        assertEquals(originalNode.sharedPathLength(), decodedNode.sharedPathLength());
        assertNotNull(decodedNode.rightNode());
        // Note: We can't directly compare Trie objects, but we can verify the decoded node exists
    }

    @Test
    void testProofNodeEncodeDecodeWithNullRightNode() {
        TrieChunk.ProofNode originalNode = new TrieChunk.ProofNode(5, null);
        
        byte[] encoded = originalNode.encode();
        RLPList rlpList = (RLPList) RLP.decode2(encoded).get(0);
        TrieChunk.ProofNode decodedNode = TrieChunk.ProofNode.decode(rlpList);
        
        assertEquals(originalNode.sharedPathLength(), decodedNode.sharedPathLength());
        assertNull(decodedNode.rightNode());
    }

    @Test
    void testProofNodeDecodeInvalidStructure() {
        // Create invalid RLP with wrong number of elements
        byte[] invalidRlp = RLP.encodeList(RLP.encodeElement(new byte[0]));
        RLPList invalidList = (RLPList) RLP.decode2(invalidRlp).get(0);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> TrieChunk.ProofNode.decode(invalidList)
        );
        assertTrue(exception.getMessage().contains("expected 2 elements"));
    }

    @Test
    void testMaxChunkSizeConstant() {
        assertEquals(1024, TrieChunk.MAX_CHUNK_SIZE);
    }

    @Test
    void testTrieChunkWithNullValues() {
        LinkedHashMap<byte[], byte[]> keyValues = new LinkedHashMap<>();
        keyValues.put("key1".getBytes(), null);
        keyValues.put(null, "value2".getBytes());
        
        TrieChunk originalChunk = new TrieChunk(keyValues, TrieChunk.Proof.EMPTY);
        
        // This should handle null values gracefully during encoding/decoding
        byte[] encoded = originalChunk.encode();
        RLPList rlpList = (RLPList) RLP.decode2(encoded).get(0);
        TrieChunk decodedChunk = TrieChunk.decode(rlpList);
        
        assertEquals(2, decodedChunk.keyValues().size());
    }

    @Test
    void testTrieChunkDecodeInvalidKeyValuePairs() {
        // Create RLP with odd number of key-value elements (invalid)
        byte[][] keyValueElements = new byte[][]{RLP.encodeElement("key1".getBytes())};
        byte[] keyValuesRlp = RLP.encodeList(keyValueElements);
        byte[] proofRlp = RLP.encodeElement(TrieChunk.Proof.EMPTY.encode());
        byte[] chunkRlp = RLP.encodeList(keyValuesRlp, proofRlp);
        
        RLPList invalidChunkList = (RLPList) RLP.decode2(chunkRlp).get(0);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> TrieChunk.decode(invalidChunkList)
        );
        assertTrue(exception.getMessage().contains("expected even number of elements"));
    }

    @Test
    void testProofVerifyWithNullInputs() {
        TrieChunk.Proof proof = TrieChunk.Proof.EMPTY;
        Trie root = new Trie(trieStore);
        Keccak256 hash = root.getHash();
        
        // Test null safety
        assertThrows(NullPointerException.class, () -> proof.verifyAndApply(root, null));
        assertThrows(NullPointerException.class, () -> proof.verifyAndApply(null, hash));
    }

    @Test
    void testProofFromFactory() {
        Trie root = new Trie(trieStore)
            .put("test1", "value1".getBytes())
            .put("test2", "value2".getBytes());
        
        TrieKeySlice key = TrieKeySlice.fromKey("test1".getBytes());
        Optional<TrieChunk.Proof> proofOpt = TrieChunk.Proof.from(root, key);
        
        // The proof creation may return empty if no matching node is found
        // This tests the factory method works without throwing exceptions
        assertNotNull(proofOpt);
    }
}
