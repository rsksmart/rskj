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

import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 08/01/2017.
 */
class TrieStoreImplTest {

    private HashMapDB map;
    private TrieStoreImpl store;

    @BeforeEach
    void setUp() {
        this.map = spy(new HashMapDB());
        this.store = new TrieStoreImpl(map);
    }

    @Test
    void saveTrieNode() {
        Trie trie = new Trie(store).put("foo", "bar".getBytes());

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(0)).get(trie.getHash().getBytes());
        verifyNoMoreInteractions(map);
    }

    @Test
    void saveAndRetrieveTrieNodeWith32BytesKey() {
        Trie trie = new Trie(store).put(Keccak256Helper.keccak256("foo".getBytes()), "bar".getBytes());

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(0)).get(trie.getHash().getBytes());
        verifyNoMoreInteractions(map);

        Trie newTrie = store.retrieve(trie.getHash().getBytes()).get();

        Assertions.assertNotNull(newTrie);
        Assertions.assertEquals(1, newTrie.trieSize());
        Assertions.assertNotNull(newTrie.get(Keccak256Helper.keccak256("foo".getBytes())));
    }

    @Test
    void saveAndRetrieveTrieNodeWith33BytesValue() {
        byte[] key = Keccak256Helper.keccak256("foo".getBytes());
        byte[] value = new byte[33];

        Trie trie = new Trie(store).put(key, value);

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(1)).put(trie.getValueHash().getBytes(), trie.getValue());
        verify(map, times(0)).get(trie.getHash().getBytes());
        verifyNoMoreInteractions(map);

        Trie newTrie = store.retrieve(trie.getHash().getBytes()).get();

        Assertions.assertNotNull(newTrie);
        Assertions.assertEquals(1, newTrie.trieSize());
        Assertions.assertNotNull(newTrie.get(key));
        Assertions.assertArrayEquals(value, newTrie.get(key));
    }

    @Test
    void saveFullTrie() {
        Trie trie = new Trie(store).put("foo", "bar".getBytes());

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(0)).get(trie.getHash().getBytes());
        verifyNoMoreInteractions(map);
    }

    @Test
    void saveFullTrieWithLongValue() {
        Trie trie = new Trie(store).put("foo", TrieValueTest.makeValue(100));

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(1)).put(trie.getValueHash().getBytes(), trie.getValue());
        verify(map, times(0)).get(trie.getHash().getBytes());
        verifyNoMoreInteractions(map);
    }

    @Test
    void saveFullTrieWithTwoLongValues() {
        Trie trie = new Trie(store)
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200));

        store.save(trie);

        verify(map, times(trie.trieSize())).put(any(), any());
        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(0)).get(trie.getHash().getBytes());
        verifyNoMoreInteractions(map);
    }

    @Test
    void saveFullTrieTwice() {
        Trie trie = new Trie(store).put("foo", "bar".getBytes());

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(0)).get(trie.getHash().getBytes());
        verifyNoMoreInteractions(map);

        store.save(trie);

        verify(map, times(1)).put(trie.getHash().getBytes(), trie.toMessage());
        verify(map, times(0)).get(trie.getHash().getBytes());
        verifyNoMoreInteractions(map);
    }

    @Test
    void saveFullTrieUpdateAndSaveAgainUsingBinaryTrie() {
        Trie trie = new Trie(store).put("foo", "bar".getBytes());

        store.save(trie);

        Keccak256 hash1 = trie.getHash();

        verify(map, times(trie.trieSize())).put(any(), any());

        trie = trie.put("foo", "bar2".getBytes());

        store.save(trie);

        Keccak256 hash2 = trie.getHash();

        verify(map, times(trie.trieSize() + 1)).put(any(), any());
        verify(map, times(0)).get(hash1.getBytes());
        verify(map, times(0)).get(hash2.getBytes());
        verifyNoMoreInteractions(map);
    }

    @Test
    void saveFullTrieUpdateAndSaveAgain() {
        Trie trie = new Trie(store).put("foo", "bar".getBytes());

        store.save(trie);

        verify(map, times(trie.trieSize())).put(any(), any());

        trie = trie.put("foo", "bar2".getBytes());

        store.save(trie);

        verify(map, times(trie.trieSize() + 1)).put(any(), any());
        verify(map, times(0)).get(any());

        verifyNoMoreInteractions(map);
    }

    @Test
    void retrieveTrieNotFound() {
        Assertions.assertFalse(store.retrieve(new byte[]{0x01, 0x02, 0x03, 0x04}).isPresent());
    }

    @Test
    void retrieveTrieByHashEmbedded() {
        Trie trie = new Trie(store)
                .put("bar", "foo".getBytes())
                .put("foo", "bar".getBytes());

        store.save(trie);
        int size = trie.trieSize();

        Trie trie2 = store.retrieve(trie.getHash().getBytes()).get();

        verify(map, times(1)).get(any());

        Assertions.assertEquals(size, trie2.trieSize());

        verify(map, times(1)).get(any());
    }

    @Test
    void retrieveTrieByHashNotEmbedded() {
        Trie trie = new Trie(store)
                .put("baaaaaaaaaaaaaaaaaaaaar", "foooooooooooooooooooooo".getBytes())
                .put("foooooooooooooooooooooo", "baaaaaaaaaaaaaaaaaaaaar".getBytes());

        store.save(trie);
        int size = trie.trieSize();

        Trie trie2 = store.retrieve(trie.getHash().getBytes()).get();

        verify(map, times(1)).get(any());

        Assertions.assertEquals(size, trie2.trieSize());

        verify(map, times(size)).get(any());
    }

    @Test
    void retrieveTrieWithLongValuesByHash() {
        Trie trie = new Trie(store)
                .put("bar", TrieValueTest.makeValue(100))
                .put("foo", TrieValueTest.makeValue(200));

        store.save(trie);

        store.retrieve(trie.getHash().getBytes());

        verify(map, times(1)).get(any());
    }

    @Test
    void saveAndRetrieveTrieDTO() {
        Trie trie = new Trie(store).put("foo", "bar".getBytes());

        TrieDTO dto = TrieDTO.decodeFromMessage(trie.toMessage(), store);
        store.saveDTO(dto);

        verify(map, times(trie.trieSize())).put(any(), any());
        verifyNoMoreInteractions(map);

        Optional<TrieDTO> optStoredDto = store.retrieveDTO(trie.getHash().getBytes());
        assertTrue(optStoredDto.isPresent());

        TrieDTO storedDto = optStoredDto.get();
        assertArrayEquals("bar".getBytes(), storedDto.getValue());
    }

    @Test
    void saveAndRetrieveTrieDTOLongValue() {
        byte[] longValue = TrieValueTest.makeValue(100);
        Trie trie = new Trie(store).put("foo", longValue);
        store.save(trie);
        TrieDTO dto = TrieDTO.decodeFromMessage(trie.toMessage(), store);
        store.saveDTO(dto);

        verify(map, times(4)).put(any(), any());

        Optional<TrieDTO> optStoredDto = store.retrieveDTO(trie.getHash().getBytes());
        assertTrue(optStoredDto.isPresent());

        TrieDTO storedDto = optStoredDto.get();
        assertArrayEquals(longValue, storedDto.getValue());
    }

    @Test
    void saveComposedTrieDtoWithLongValues() {
        Trie trie = new Trie(store)
                .put("foo", TrieValueTest.makeValue(100))
                .put("bar", TrieValueTest.makeValue(200));
        store.save(trie);
        verify(map, times(trie.trieSize())).put(any(), any());

        TrieDTO dto = TrieDTO.decodeFromMessage(trie.toMessage(), store, true, null);
        store.saveDTO(dto);
        verify(map, times(6)).put(any(), any());
    }
}
