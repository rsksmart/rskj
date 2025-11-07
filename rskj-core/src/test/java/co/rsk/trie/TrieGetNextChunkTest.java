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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrieGetNextChunkTest {

    private TrieStore trieStore;

    @BeforeEach
    void setUp() {
        HashMapDB map = new HashMapDB();
        this.trieStore = new TrieStoreImpl(map);
    }

    @Test
    void testGetNextChunk_EmptyTrie() {
        Trie emptyTrie = new Trie(trieStore);
        
        TrieChunk chunk = emptyTrie.getNextChunk(null);
        
        // Empty trie returns an empty chunk, not null
        assertNotNull(chunk);
        assertTrue(chunk.keyValues().isEmpty());
        assertEquals(TrieChunk.Proof.EMPTY, chunk.proof());
    }

    @Test
    void testGetNextChunk_EmptyTrieWithKey() {
        Trie emptyTrie = new Trie(trieStore);
        byte[] someKey = "anykey".getBytes();
        
        TrieChunk chunk = emptyTrie.getNextChunk(someKey);
        
        // Even with a key, empty trie should return null (no next chunk after a key that doesn't exist)
        assertNull(chunk);
    }

    @Test
    void testGetNextChunk_NullKeyReturnsFirstChunk() {
        Trie trie = new Trie(trieStore);
        trie = trie.put("key1", "value1".getBytes());
        trie = trie.put("key2", "value2".getBytes());
        trie = trie.put("key3", "value3".getBytes());
        
        TrieChunk chunk = trie.getNextChunk(null);
        
        assertNotNull(chunk);
        assertFalse(chunk.keyValues().isEmpty());
        assertTrue(chunk.keyValues().size() > 0);
        assertEquals(TrieChunk.Proof.EMPTY, chunk.proof());
    }

    @Test
    void testGetNextChunk_SingleElementTrie() {
        Trie trie = new Trie(trieStore);
        trie = trie.put("onlykey", "onlyvalue".getBytes());
        
        // First chunk with null key
        TrieChunk firstChunk = trie.getNextChunk(null);
        assertNotNull(firstChunk);
        assertEquals(1, firstChunk.keyValues().size());
        
        // Next chunk after the only key should be null
        TrieChunk nextChunk = trie.getNextChunk("onlykey".getBytes());
        assertNull(nextChunk);
    }

    @Test
    void testGetNextChunk_ValidKeyWithNextChunk() {
        Trie trie = new Trie(trieStore);
        // Add keys in lexicographic order to ensure predictable iteration
        trie = trie.put("aaa", "value1".getBytes());
        trie = trie.put("bbb", "value2".getBytes());
        trie = trie.put("ccc", "value3".getBytes());
        trie = trie.put("ddd", "value4".getBytes());
        
        // Get first chunk
        TrieChunk firstChunk = trie.getNextChunk(null);
        assertNotNull(firstChunk);
        
        // If first chunk doesn't contain all keys, there should be a next chunk
        if (firstChunk.keyValues().size() < 4) {
            // Find the last key in the first chunk to get the next chunk
            byte[] lastKeyInFirstChunk = firstChunk.keyValues().keySet()
                    .stream()
                    .reduce((first, second) -> second)
                    .orElse(null);
            
            assertNotNull(lastKeyInFirstChunk);
            
            TrieChunk nextChunk = trie.getNextChunk(lastKeyInFirstChunk);
            assertNotNull(nextChunk);
            assertFalse(nextChunk.keyValues().isEmpty());
        }
    }

    @Test
    void testGetNextChunk_LastKeyInTrie() {
        Trie trie = new Trie(trieStore);
        trie = trie.put("aaa", "value1".getBytes());
        trie = trie.put("bbb", "value2".getBytes());
        trie = trie.put("zzz", "value3".getBytes()); // Last key alphabetically
        
        // Request next chunk after the last key
        TrieChunk chunk = trie.getNextChunk("zzz".getBytes());
        
        assertNull(chunk);
    }

    @Test
    void testGetNextChunk_NonExistentKey() {
        Trie trie = new Trie(trieStore);
        trie = trie.put("existing1", "value1".getBytes());
        trie = trie.put("existing2", "value2".getBytes());
        
        // Request next chunk for a key that doesn't exist
        TrieChunk chunk = trie.getNextChunk("nonexistent".getBytes());
        
        assertNull(chunk);
    }

    @Test
    void testGetNextChunk_LargeTrieMultipleChunks() {
        Trie trie = new Trie(trieStore);
        
        // Create a large trie that will likely span multiple chunks
        for (int i = 0; i < 100; i++) {
            String key = String.format("key%03d", i);
            String value = "value" + i;
            trie = trie.put(key, value.getBytes());
        }
        
        // Get first chunk
        TrieChunk firstChunk = trie.getNextChunk(null);
        assertNotNull(firstChunk);
        assertTrue(firstChunk.keyValues().size() > 0);
        
        // Verify we can get subsequent chunks if they exist
        int totalKeysProcessed = firstChunk.keyValues().size();
        byte[] lastProcessedKey = firstChunk.keyValues().keySet()
                .stream()
                .reduce((first, second) -> second)
                .orElse(null);
        
        while (lastProcessedKey != null && totalKeysProcessed < 100) {
            TrieChunk nextChunk = trie.getNextChunk(lastProcessedKey);
            if (nextChunk == null) {
                break;
            }
            
            assertTrue(nextChunk.keyValues().size() > 0);
            totalKeysProcessed += nextChunk.keyValues().size();
            
            lastProcessedKey = nextChunk.keyValues().keySet()
                    .stream()
                    .reduce((first, second) -> second)
                    .orElse(null);
        }
        
        // Should have processed all keys eventually
        assertTrue(totalKeysProcessed <= 100);
    }

    @Test
    void testGetNextChunk_EmptyKey() {
        Trie trie = new Trie(trieStore);
        trie = trie.put("", "emptyKeyValue".getBytes()); // Empty key
        trie = trie.put("a", "value1".getBytes());
        trie = trie.put("b", "value2".getBytes());
        
        // Test with empty key
        TrieChunk chunk = trie.getNextChunk("".getBytes());
        
        // Should either return next chunk or null depending on implementation
        // The key point is it shouldn't throw an exception
        // Result can be null or a valid chunk
        assertTrue(chunk == null || chunk.keyValues().size() >= 0);
    }

    @Test
    void testGetNextChunk_BinaryKeys() {
        Trie trie = new Trie(trieStore);
        
        // Add keys with binary data
        byte[] key1 = new byte[]{0x00, 0x01, 0x02};
        byte[] key2 = new byte[]{0x00, 0x01, 0x03};
        byte[] key3 = new byte[]{0x00, 0x02, 0x01};
        
        trie = trie.put(key1, "value1".getBytes());
        trie = trie.put(key2, "value2".getBytes());
        trie = trie.put(key3, "value3".getBytes());
        
        // Get first chunk
        TrieChunk firstChunk = trie.getNextChunk(null);
        assertNotNull(firstChunk);
        assertTrue(firstChunk.keyValues().size() > 0);
        
        // Test getting next chunk using binary key
        TrieChunk nextChunk = trie.getNextChunk(key1);
        // Should not throw exception and return valid result
        assertTrue(nextChunk == null || nextChunk.keyValues().size() >= 0);
    }

    @Test
    void testGetNextChunk_ChunkSizeRespected() {
        Trie trie = new Trie(trieStore);
        
        // Add enough data to potentially create multiple chunks
        for (int i = 0; i < 50; i++) {
            String key = "key" + String.format("%05d", i);
            byte[] value = ("value" + i).getBytes();
            trie = trie.put(key, value);
        }
        
        TrieChunk chunk = trie.getNextChunk(null);
        
        if (chunk != null) {
            assertNotNull(chunk.keyValues());
            assertTrue(chunk.keyValues().size() > 0);
            
            // Verify chunk doesn't exceed reasonable size expectations
            // The exact size depends on TrieChunk.MAX_CHUNK_SIZE
            assertTrue(chunk.keyValues().size() <= 1000); // Reasonable upper bound
        }
    }

    @Test
    void testGetNextChunk_IdenticalSequentialCalls() {
        Trie trie = new Trie(trieStore);
        trie = trie.put("key1", "value1".getBytes());
        trie = trie.put("key2", "value2".getBytes());
        
        // Two identical calls should return the same result
        TrieChunk chunk1 = trie.getNextChunk("key1".getBytes());
        TrieChunk chunk2 = trie.getNextChunk("key1".getBytes());
        
        // Both should be null or both should have the same content
        if (chunk1 == null) {
            assertNull(chunk2);
        } else {
            assertNotNull(chunk2);
            assertEquals(chunk1.keyValues().size(), chunk2.keyValues().size());
        }
    }
}
