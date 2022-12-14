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

import java.util.Optional;

public class CachedTrieStore implements TrieStore {

    private TrieStore cache;
    private TrieStore base;
    /**
     */
    public CachedTrieStore(TrieStore cache, TrieStore base) {

        this.base = base;
        this.cache = cache;
    }

    @Override
    public void save(Trie trie) {
        base.save(trie);
    }


    @Override
    public void flush() {
        base.flush();
    }



    @Override
    public Optional<Trie> retrieve(byte[] rootHash) {
        Optional<Trie> t = cache.retrieve(rootHash);
        if (!t.isPresent()) {
            t = base.retrieve(rootHash);
        }
        return t;
    }

    @Override
    public byte[] retrieveValue(byte[] hash) {
        byte[] t = cache.retrieveValue(hash);
        if (t==null) {
            t = base.retrieveValue(hash);
        }
        return t;
    }

    @Override
    public void dispose() {
        base.dispose();
        cache.dispose();
    }
}
