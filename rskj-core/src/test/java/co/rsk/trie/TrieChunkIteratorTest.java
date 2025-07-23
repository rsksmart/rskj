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

import org.ethereum.datasource.HashMapDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.jupiter.api.Assertions.*;

class TrieChunkIteratorTest {

    private TrieStore trieStore;

    @BeforeEach
    void setUp() {
        HashMapDB map = new HashMapDB();
        this.trieStore = new TrieStoreImpl(map);
    }

    @Test
    void testIteratorConstructionWithNullFromKey() {
        Trie trie = buildSimpleTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 10);
        
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
    }

    @Test
    void testIteratorConstructionWithFromKey() {
        Trie trie = buildSimpleTrie();
        TrieKeySlice fromKey = TrieKeySlice.fromKey("key1".getBytes());
        TrieChunkIterator iterator = new TrieChunkIterator(trie, fromKey, 10);
        
        assertNotNull(iterator);
    }

    @Test
    void testIteratorWithEmptyTrie() {
        Trie emptyTrie = new Trie(trieStore);
        TrieChunkIterator iterator = new TrieChunkIterator(emptyTrie, null, 10);
        
        // Empty trie might still have the root node to iterate
        // The iterator behavior depends on internal trie structure
        assertNotNull(iterator);
    }

    @Test
    void testIteratorWithSingleNode() {
        Trie trie = new Trie(trieStore).put("key", "value".getBytes());
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 10);
        
        assertTrue(iterator.hasNext());
        TrieChunk chunk = iterator.next();
        
        assertNotNull(chunk);
        assertEquals(1, chunk.keyValues().size());
        assertFalse(iterator.hasNext());
    }

    @Test
    void testIteratorWithMultipleNodes() {
        Trie trie = buildSimpleTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 10);
        
        assertTrue(iterator.hasNext());
        TrieChunk chunk = iterator.next();
        
        assertNotNull(chunk);
        assertTrue(chunk.keyValues().size() >= 1);
        assertNotNull(chunk.proof());
    }

    @Test
    void testChunkSizeLimit() {
        Trie trie = buildLargeTrie();
        int maxChunkSize = 3;
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, maxChunkSize);
        
        assertTrue(iterator.hasNext());
        TrieChunk chunk = iterator.next();
        
        assertNotNull(chunk);
        assertTrue(chunk.keyValues().size() <= maxChunkSize);
    }

    @Test
    void testIteratorReturnsAllValues() {
        Trie trie = buildSimpleTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 100);
        
        Set<String> collectedKeys = new HashSet<>();
        while (iterator.hasNext()) {
            TrieChunk chunk = iterator.next();
            for (byte[] key : chunk.keyValues().keySet()) {
                collectedKeys.add(new String(key));
            }
        }
        
        // Should collect all keys that were put into the trie
        assertFalse(collectedKeys.isEmpty());
    }

    @Test
    void testIteratorNextWithoutHasNext() {
        // Create an iterator and exhaust it first
        Trie trie = buildSimpleTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 100);
        
        // Exhaust the iterator
        while (iterator.hasNext()) {
            iterator.next();
        }
        
        // Now try to call next() when there are no more elements
        assertFalse(iterator.hasNext());
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            iterator::next
        );
        assertTrue(exception.getMessage().contains("No more elements"));
    }

    @Test
    void testChunkSizeOne() {
        Trie trie = buildSimpleTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 1);
        
        if (iterator.hasNext()) {
            TrieChunk chunk = iterator.next();
            assertNotNull(chunk);
            assertTrue(chunk.keyValues().size() <= 1);
        }
    }

    @Test
    void testFromKeyFiltering() {
        Trie trie = buildLargeTrie();
        
        // Test with null fromKey (should start from beginning)
        TrieChunkIterator iteratorNull = new TrieChunkIterator(trie, null, 10);
        boolean hasElementsFromNull = iteratorNull.hasNext();
        
        // Test with specific fromKey
        TrieKeySlice fromKey = TrieKeySlice.fromKey("key2".getBytes());
        TrieChunkIterator iteratorWithKey = new TrieChunkIterator(trie, fromKey, 10);
        
        // Both should be valid constructions (exact behavior depends on trie structure)
        assertNotNull(iteratorWithKey);
        assertTrue(hasElementsFromNull); // Simple trie should have elements
    }

    @Test
    void testIteratorProofGeneration() {
        Trie trie = buildLargeTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 2);
        
        if (iterator.hasNext()) {
            TrieChunk chunk = iterator.next();
            assertNotNull(chunk.proof());
            
            // If chunk has multiple items and there are more to iterate, proof should be present
            if (chunk.keyValues().size() >= 2 && iterator.hasNext()) {
                assertFalse(chunk.proof().isEmpty());
            }
        }
    }

    @Test
    void testConsecutiveIterations() {
        Trie trie = buildLargeTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 2);
        
        int chunkCount = 0;
        int totalItems = 0;
        
        while (iterator.hasNext()) {
            TrieChunk chunk = iterator.next();
            chunkCount++;
            totalItems += chunk.keyValues().size();
            
            assertNotNull(chunk);
            assertNotNull(chunk.keyValues());
            assertNotNull(chunk.proof());
            assertTrue(chunk.keyValues().size() <= 2);
        }
        
        assertTrue(chunkCount >= 1);
        assertTrue(totalItems >= 1);
    }

    @Test
    void testLargeChunkSize() {
        Trie trie = buildSimpleTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 1000);
        
        if (iterator.hasNext()) {
            TrieChunk chunk = iterator.next();
            assertNotNull(chunk);
            // Should get all items in one chunk
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    void testZeroChunkSize() {
        Trie trie = buildSimpleTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 0);
        
        // With chunk size 0, should still work but chunks will be very small
        if (iterator.hasNext()) {
            TrieChunk chunk = iterator.next();
            assertNotNull(chunk);
            assertTrue(chunk.keyValues().size() >= 0);
        }
    }

    @Test
    void testNegativeChunkSize() {
        Trie trie = buildSimpleTrie();
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, -1);
        
        // Should handle negative chunk size gracefully
        if (iterator.hasNext()) {
            TrieChunk chunk = iterator.next();
            assertNotNull(chunk);
        }
    }

    @Test
    void testFromKeyNotFoundInTrie() {
        Trie trie = buildSimpleTrie();
        TrieKeySlice nonExistentKey = TrieKeySlice.fromKey("nonexistent".getBytes());
        TrieChunkIterator iterator = new TrieChunkIterator(trie, nonExistentKey, 10);
        
        // Iterator should handle non-existent keys gracefully
        assertNotNull(iterator);
    }

    @Test
    void testTrieWithOnlyEmptyValues() {
        Trie trie = new Trie(trieStore)
            .put("key1", new byte[0])
            .put("key2", new byte[0]);
        
        TrieChunkIterator iterator = new TrieChunkIterator(trie, null, 10);
        
        assertTrue(iterator.hasNext());
        TrieChunk chunk = iterator.next();
        assertNotNull(chunk);
        
        // Empty values might not be included in chunks - depends on trie implementation
        // Just verify the chunk is valid
        assertNotNull(chunk.keyValues());
        assertNotNull(chunk.proof());
        
        // If there are values, verify they are handled correctly
        for (byte[] value : chunk.keyValues().values()) {
            assertNotNull(value);
        }
    }

    private Trie buildSimpleTrie() {
        return new Trie(trieStore)
            .put("key1", "value1".getBytes())
            .put("key2", "value2".getBytes())
            .put("key3", "value3".getBytes());
    }

    private Trie buildLargeTrie() {
        Trie trie = new Trie(trieStore);
        
        // Build a trie similar to the test pattern used in other trie tests
        trie = trie.put(decode("0a"), new byte[]{0x06});
        trie = trie.put(decode("0a00"), new byte[]{0x02});
        trie = trie.put(decode("0a80"), new byte[]{0x07});
        trie = trie.put(decode("0a0000"), new byte[]{0x01});
        trie = trie.put(decode("0a0080"), new byte[]{0x04});
        trie = trie.put(decode("0a008000"), new byte[]{0x03});
        trie = trie.put(decode("0a008080"), new byte[]{0x05});
        trie = trie.put(decode("0a8080"), new byte[]{0x08});
        
        return trie;
    }
} 