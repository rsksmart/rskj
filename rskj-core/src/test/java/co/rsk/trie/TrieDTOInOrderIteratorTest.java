/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

import java.util.List;

import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.jupiter.api.Assertions.*;

class TrieDTOInOrderIteratorTest {

    private HashMapDB map;
    private TrieStore trieStore;

    @BeforeEach
    void setUp() {
        this.map = new HashMapDB();
        this.trieStore = new TrieStoreImpl(map);
    }


    @Test
    void basicTest() {
        Trie trie = new Trie().put("key", "value".getBytes());
        trieStore.save(trie);
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(trieStore, trie.getHash().getBytes(), 0, 3);
        assertNotNull(iterator);

        assertTrue(iterator.hasNext());
        assertFalse(iterator.isEmpty());
        assertEquals(0, iterator.getFrom());

        TrieDTO trieDTO = iterator.next();
        assertNotNull(trieDTO);
        assertArrayEquals("value".getBytes(), trieDTO.getValue());

        assertFalse(iterator.hasNext());
    }

    @Test
    void peekDoesNotRemoveItemFromNext() {
        Trie trie = new Trie(trieStore).put("foo", "bar".getBytes()).put("bar", "fee".getBytes());
        trieStore.save(trie);
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(trieStore, trie.getHash().getBytes(), 0, 1024);
        assertTrue(iterator.hasNext());
        TrieDTO peekItem = iterator.peek();
        TrieDTO nextItem = iterator.next();
        assertEquals(peekItem, nextItem);
    }

    @Test
    void getNodesLeftVisiting() {
        Trie trie = buildTestTrie(trieStore);
        trieStore.save(trie);
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(trieStore, trie.getHash().getBytes(), 0, 1024);
        assertTrue(iterator.hasNext());
        List<TrieDTO> nodesLeftVisiting = iterator.getNodesLeftVisiting();
        assertEquals(3, nodesLeftVisiting.size());
    }

    @Test
    void getPreRootNodes() {
        Trie trie = buildTestTrie(trieStore);
        trieStore.save(trie);
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(trieStore, trie.getHash().getBytes(), 190, 1024);
        assertTrue(iterator.hasNext());
        List<TrieDTO> preRootNodes = iterator.getPreRootNodes();
        assertEquals(1, preRootNodes.size());
    }

    @Test
    void getOrderedNodes(){
        Trie trie = buildTestTrie(trieStore);
        trieStore.save(trie);
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(trieStore, trie.getHash().getBytes(), 0, 1024);
        int expected = 1;
        while (iterator.hasNext()) {
            TrieDTO node = iterator.next();
            assertNotNull(node);
            int decimalValue = node.getValue()[0];
            assertEquals(expected, decimalValue);
            expected++;
        }
    }


    /**
     @formatter:off
     * @return the following tree
     *
     *           4
     *        /    \
     *       /      \
     *      /        6
     *     2        / \
     *    / \      5   7
     *   1   \    /   /
     *  /     3  13  14
     * 10    / \
     *      11  12
     *
     @formatter:on
     */
    private static Trie buildTestTrie(TrieStore trieStore) {
        Trie trie = new Trie(trieStore);
        trie = trie.put(decode("0a"), new byte[]{0x04});
        trie = trie.put(decode("0a00"), new byte[]{0x02});
        trie = trie.put(decode("0a80"), new byte[]{0x06});
        trie = trie.put(decode("0a0000"), new byte[]{0x01});
        trie = trie.put(decode("0a000000"), new byte[]{0x0a});
        trie = trie.put(decode("0a0080"), new byte[]{0x03});
        trie = trie.put(decode("0a008000"), new byte[]{0x0b});
        trie = trie.put(decode("0a008080"), new byte[]{0x0c});
        trie = trie.put(decode("0a8000"), new byte[]{0x05});
        trie = trie.put(decode("0a800000"), new byte[]{0x0d});
        trie = trie.put(decode("0a8080"), new byte[]{0x07});
        trie = trie.put(decode("0a808000"), new byte[]{0x0e});
        return trie;
    }
}