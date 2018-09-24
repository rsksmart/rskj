/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.db;

import co.rsk.db.*;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.core.Repository;

public class RepositoryTrack extends MutableRepository {

    public RepositoryTrack() {
        trie = new MutableTrieImpl(new TrieImpl());
    }

    public RepositoryTrack(boolean isSecure) {
        trie = new MutableTrieImpl(new TrieImpl(isSecure));
    }

    public RepositoryTrack(Repository aparentRepo) {
        trie = new MutableTrieCache(aparentRepo.getMutableTrie());
        this.parentRepo = aparentRepo;
    }

    public RepositoryTrack(Trie atrie) {
        trie = new MutableTrieCache(new MutableTrieImpl(atrie));
        this.parentRepo = null;
    }

    public RepositoryTrack(Trie atrie, Repository aparentRepo) {
        // If there is no parent then we don't need to track changes
        if (aparentRepo == null) {
            trie = new MutableTrieImpl(atrie);
        } else {
            trie = new MutableTrieCache(new MutableTrieImpl(atrie));
        }

        this.parentRepo = aparentRepo;
    }

}
