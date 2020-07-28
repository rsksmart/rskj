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

package co.rsk.db;

import co.rsk.trie.MutableTrie;

/**
 * Implements a cache accessor to a MutableTrie, using a MultiLevelCache system.
 * IF the nested MutableTrie is a JournalTrieCache too, the same MultiLevelCache is shared across the accessors
 */
public class JournalTrieCache extends MutableTrieCache {


    /**
     * Builds a cache accessor to a MutableTrie, using a MultiLevelCache system.
     * IF the nested MutableTrie is a JournalTrieCache too, the same MultiLevelCache is shared across the accessors
     *
     * @param parentTrie
     */
    public JournalTrieCache(MutableTrie parentTrie) {
        // if parentTrie is a JournalTrieCache, then we add a MultiLevelCacheSnapshot to the same instance
        // of MultiLevelCache than the parent, with one caching level upper.
        // else, we create a new MultiLevelCacheSnapshot looking at a
        // new MultiCacheLevel instance. Then, only one caching level for now.

        super((parentTrie instanceof JournalTrieCache) ? ((JournalTrieCache) parentTrie).getMutableTrie() : parentTrie,
                (parentTrie instanceof JournalTrieCache) ? new MultiLevelCacheSnapshot(
                        ((JournalTrieCache) parentTrie).getCache(),
                        ((JournalTrieCache) parentTrie).getCacheLevel() + 1) :
                        new MultiLevelCacheSnapshot((parentTrie instanceof JournalTrieCache) ? ((JournalTrieCache) parentTrie).getMutableTrie() : parentTrie)
        );
    }

    private MultiLevelCache getCache() {
        return ((MultiLevelCacheSnapshot) cache).getCache();
    }

    public MutableTrie getMutableTrie() {
        return trie;
    }

    private int getCacheLevel() {
        return ((MultiLevelCacheSnapshot) cache).getCurrentLevel();
    }


}