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

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

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

    private KeyValueDataSource store;

    /** Weak references are removed once the tries are garbage collected */
    private Set<Trie> savedTries = Collections.newSetFromMap(new WeakHashMap<>());

    public TrieStoreImpl(KeyValueDataSource store) {
        this.store = store;
    }

    /**
     * Recursively saves all unsaved nodes of this trie to the underlying key-value store
     */
    @Override
    public void save(Trie trie) {
        save(trie, true);
    }

    /**
     * @param forceSaveRoot allows saving the root node even if it's embeddable
     */
    private void save(Trie trie, boolean forceSaveRoot) {
        if (savedTries.contains(trie)) {
            // it is guaranteed that the children of a saved node are also saved
            return;
        }

        byte[] trieKeyBytes = trie.getHash().getBytes();

        if (forceSaveRoot && this.store.get(trieKeyBytes) != null) {
            // the full trie is already saved
            return;
        }

        trie.getLeft().getNode().ifPresent(t -> save(t, false));
        trie.getRight().getNode().ifPresent(t -> save(t, false));

        if (trie.hasLongValue()) {
            // Note that there is no distinction in keys between node data and value data. This could bring problems in
            // the future when trying to garbage-collect the data. We could split the key spaces bit a single
            // overwritten MSB of the hash. Also note that when storing a node that has long value it could be the case
            // that the save the value here, but the value is already present in the database because other node shares
            // the value. This is suboptimal, we could check existence here but maybe the database already has
            // provisions to reduce the load in these cases where a key/value is set equal to the previous value.
            // In particular our levelDB driver has not method to test for the existence of a key without retrieving the
            // value also, so manually checking pre-existence here seems it will add overhead on the average case,
            // instead of reducing it.
            this.store.put(trie.getValueHash().getBytes(), trie.getValue());
        }

        if (trie.isEmbeddable() && !forceSaveRoot) {
            return;
        }

        this.store.put(trieKeyBytes, trie.toMessage());
        savedTries.add(trie);
    }

    @Override
    public void flush(){
        this.store.flush();
    }

    @Override
    public Optional<Trie> retrieve(byte[] hash) {
        byte[] message = this.store.get(hash);
        if (message == null) {
            return Optional.empty();
        }

        Trie trie = Trie.fromMessage(message, this);
        savedTries.add(trie);
        return Optional.of(trie);
    }

    @Override
    public byte[] retrieveValue(byte[] hash) {
        return this.store.get(hash);
    }

    @Override
    public void dispose() {
        store.close();
    }
}
