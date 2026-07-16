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

import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiTrieStoreTest {
    @Test
    void oldestEpochNameIsntNegative() {
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        new MultiTrieStore(3, 3, storeFactory, null);

        verify(storeFactory).newInstance("2");
        verify(storeFactory).newInstance("1");
        verify(storeFactory).newInstance("0");
        verifyNoMoreInteractions(storeFactory);
    }

    @Test
    void openLastThreeEpochsStores() {
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        new MultiTrieStore(49, 3, storeFactory, null);

        verify(storeFactory).newInstance("48");
        verify(storeFactory).newInstance("47");
        verify(storeFactory).newInstance("46");
        verifyNoMoreInteractions(storeFactory);
    }

    @Test
    void callsSaveOnlyOnNewestStore() {
        TrieStore store1 = mock(TrieStore.class);
        TrieStore store2 = mock(TrieStore.class);
        TrieStore store3 = mock(TrieStore.class);
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        when(storeFactory.newInstance("46")).thenReturn(store1);
        when(storeFactory.newInstance("47")).thenReturn(store2);
        when(storeFactory.newInstance("48")).thenReturn(store3);
        MultiTrieStore store = new MultiTrieStore(49, 3, storeFactory, null);

        Trie trie = mock(Trie.class);
        store.save(trie);

        verify(store1, never()).save(trie);
        verify(store2, never()).save(trie);
        verify(store3).save(trie);
    }

    @Test
    void saveValueDelegatesToCurrentStoreAndIsRetrievableByHash() {
        // saveValue must write only to the current (newest) epoch store; the value is then retrievable by
        // its keccak256 value-hash through the MultiTrieStore's newest-to-oldest lookup, and the older
        // epochs are never written to.
        TrieStore currentStore = new TrieStoreImpl(new HashMapDB());
        TrieStore store2 = mock(TrieStore.class);
        TrieStore store3 = mock(TrieStore.class);
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        when(storeFactory.newInstance("48")).thenReturn(currentStore); // newest epoch -> current store
        when(storeFactory.newInstance("47")).thenReturn(store2);
        when(storeFactory.newInstance("46")).thenReturn(store3);
        MultiTrieStore store = new MultiTrieStore(49, 3, storeFactory, null);

        byte[] value = "a-long-bootstrap-value-well-over-32-bytes-long".getBytes(StandardCharsets.UTF_8);
        store.saveValue(value);

        // only the newest store received the write
        verify(store2, never()).saveValue(any());
        verify(store3, never()).saveValue(any());

        // retrievable by its value-hash (keccak256 of the value), served from the current store
        byte[] valueHash = Keccak256Helper.keccak256(value);
        assertArrayEquals(value, store.retrieveValue(valueHash));
    }

    @Test
    void callsFlushOnAllStores() {
        TrieStore store1 = mock(TrieStore.class);
        TrieStore store2 = mock(TrieStore.class);
        TrieStore store3 = mock(TrieStore.class);
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        when(storeFactory.newInstance("46")).thenReturn(store1);
        when(storeFactory.newInstance("47")).thenReturn(store2);
        when(storeFactory.newInstance("48")).thenReturn(store3);
        MultiTrieStore store = new MultiTrieStore(49, 3, storeFactory, null);

        store.flush();

        verify(store1).flush();
        verify(store2).flush();
        verify(store3).flush();
    }

    @Test
    void callsDisposeOnAllStores() {
        TrieStore store1 = mock(TrieStore.class);
        TrieStore store2 = mock(TrieStore.class);
        TrieStore store3 = mock(TrieStore.class);
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        when(storeFactory.newInstance("46")).thenReturn(store1);
        when(storeFactory.newInstance("47")).thenReturn(store2);
        when(storeFactory.newInstance("48")).thenReturn(store3);
        MultiTrieStore store = new MultiTrieStore(49, 3, storeFactory, null);

        store.dispose();

        verify(store1).dispose();
        verify(store2).dispose();
        verify(store3).dispose();
    }

    public void retrievesTrieNotFound() {
        TrieStoreFactory storeFactory = name -> mock(TrieStore.class);
        MultiTrieStore store = new MultiTrieStore(49, 3, storeFactory, null);

        Trie testTrie = new Trie();
        byte[] hashToRetrieve = testTrie.getHash().getBytes();
        assertFalse(store.retrieve(hashToRetrieve).isPresent());
    }

    @Test
    void retrievesFromNewestStoreWithValue() {
        TrieStore store1 = mock(TrieStore.class);
        TrieStore store2 = mock(TrieStore.class);
        TrieStore store3 = mock(TrieStore.class);
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        when(storeFactory.newInstance("46")).thenReturn(store1);
        when(storeFactory.newInstance("47")).thenReturn(store2);
        when(storeFactory.newInstance("48")).thenReturn(store3);
        MultiTrieStore store = new MultiTrieStore(49, 3, storeFactory, null);

        Trie testTrie = new Trie();
        byte[] hashToRetrieve = testTrie.getHash().getBytes();
        when(store2.retrieveValue(hashToRetrieve)).thenReturn(testTrie.toMessage());

        Trie retrievedTrie = store.retrieve(hashToRetrieve).get();
        assertEquals(testTrie, retrievedTrie);

        verify(store1, never()).retrieveValue(hashToRetrieve);
        verify(store2).retrieveValue(hashToRetrieve);
        verify(store3).retrieveValue(hashToRetrieve);
    }

    @Test
    void retrievesValueFromNewestStoreWithValue() {
        TrieStore store1 = mock(TrieStore.class);
        TrieStore store2 = mock(TrieStore.class);
        TrieStore store3 = mock(TrieStore.class);
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        when(storeFactory.newInstance("46")).thenReturn(store1);
        when(storeFactory.newInstance("47")).thenReturn(store2);
        when(storeFactory.newInstance("48")).thenReturn(store3);
        MultiTrieStore store = new MultiTrieStore(49, 3, storeFactory, null);

        byte[] testValue = new byte[] {0x32, 0x42};
        byte[] hashToRetrieve = new byte[] {0x2, 0x4};
        when(store2.retrieveValue(hashToRetrieve)).thenReturn(testValue);

        byte[] retrievedValue = store.retrieveValue(hashToRetrieve);
        assertArrayEquals(testValue, retrievedValue);

        verify(store1, never()).retrieveValue(hashToRetrieve);
        verify(store2).retrieveValue(hashToRetrieve);
        verify(store3).retrieveValue(hashToRetrieve);
    }

    @Test
    void openStore3OnEpoch0Collection() {
        MultiTrieStore.OnEpochDispose disposer = mock(MultiTrieStore.OnEpochDispose.class);
        TrieStore store1 = mock(TrieStore.class);
        TrieStore store2 = mock(TrieStore.class);
        TrieStore store3 = mock(TrieStore.class);
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        when(storeFactory.newInstance("0")).thenReturn(store1);
        when(storeFactory.newInstance("1")).thenReturn(store2);
        when(storeFactory.newInstance("2")).thenReturn(store3);

        MultiTrieStore store = new MultiTrieStore(0, 3, storeFactory, disposer);

        Trie testTrie = new Trie();
        byte[] hashToRetrieve = testTrie.getHash().getBytes();
        when(store1.retrieveValue(hashToRetrieve)).thenReturn(testTrie.toMessage());

        store.collect(hashToRetrieve);

        verify(disposer).callback(0);
        verify(storeFactory).newInstance("3");
    }

    @Test
    void performsStoreRotationOnCollect() {
        TrieStore store1 = mock(TrieStore.class);
        TrieStore store2 = mock(TrieStore.class);
        TrieStore store3 = mock(TrieStore.class);
        TrieStore store4 = mock(TrieStore.class);
        TrieStoreFactory storeFactory = mock(TrieStoreFactory.class);
        when(storeFactory.newInstance("46")).thenReturn(store1);
        when(storeFactory.newInstance("47")).thenReturn(store2);
        when(storeFactory.newInstance("48")).thenReturn(store3);
        when(storeFactory.newInstance("49")).thenReturn(store4);
        MultiTrieStore.OnEpochDispose disposer = mock(MultiTrieStore.OnEpochDispose.class);
        MultiTrieStore store = new MultiTrieStore(49, 3, storeFactory, disposer);

        Trie testTrie = new Trie();
        byte[] testTrieHash = testTrie.getHash().getBytes();
        when(store1.retrieveValue(testTrieHash)).thenReturn(testTrie.toMessage());

        store.collect(testTrieHash);

        // 1. save the oldest trie into the second to last store
        verify(store2).save(testTrie);
        when(store2.retrieveValue(testTrieHash)).thenReturn(testTrie.toMessage());

        // 2. disposes the last store
        verify(store1).dispose();
        verify(disposer).callback(46);

        // 3. oldest trie is still accessible and disposed database is not queried
        Trie retrievedTrie = store.retrieve(testTrieHash).get();
        assertEquals(testTrie, retrievedTrie);
        // 1 was only called for collect
        // 2 and 3 were called once during collect and another one for retrieve.
        // 4 was only called for retrieve
        verify(store1).retrieveValue(testTrieHash);
        verify(store2, times(2)).retrieveValue(testTrieHash);
        verify(store3, times(2)).retrieveValue(testTrieHash);
        verify(store4).retrieveValue(testTrieHash);

        // 4. disposed database is not queried
        byte[] hashToRetrieve = new byte[] {0x2, 0x4};
        byte[] retrievedValue = store.retrieveValue(hashToRetrieve);
        assertNull(retrievedValue);

        verify(store1, never()).retrieveValue(hashToRetrieve);
        verify(store2).retrieveValue(hashToRetrieve);
        verify(store3).retrieveValue(hashToRetrieve);
        verify(store4).retrieveValue(hashToRetrieve);
    }
}
