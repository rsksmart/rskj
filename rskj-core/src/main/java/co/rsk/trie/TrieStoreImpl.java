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

import org.ethereum.datasource.KeyValueDataSource;

/**
 * TrieStoreImpl store and retrieve Trie node by hash
 *
 * It saves/retrieves the serialized form (byte array) of a Trie node
 *
 * Internally, it uses a key value data source
 *
 * Created by ajlopez on 08/01/2017.
 */
public class TrieStoreImpl implements TrieStore {

    // a key value data source to use
    private KeyValueDataSource store;

    public TrieStoreImpl(KeyValueDataSource store) {
        this.store = store;
    }

    /**
     * save saves a Trie to the store
     * @param trie
     */
    @Override
    public void save(Trie trie) {
        this.store.put(trie.getHash().getBytes(), trie.toMessage());

        if (trie.hasLongValue()) {
            this.store.put(trie.getValueHash(), trie.getValue());
        }
    }

    /**
     * retrieve retrieves a Trie instance from store, using hash a key
     *
     * @param hash  the hash to retrieve
     *
     * @return  the retrieved Trie, null if key does not exist
     */
    @Override
    public Trie retrieve(byte[] hash) {
        byte[] message = this.store.get(hash);

        return TrieImpl.fromMessage(message, this);
    }

    public byte[] retrieveValue(byte[] hash) {
        return this.store.get(hash);
    }

    public byte[] storeValue(byte[] key, byte[] value) {
        return this.store.put(key, value);
    }

    public KeyValueDataSource getDataSource() {
        return this.store;
    }
}
