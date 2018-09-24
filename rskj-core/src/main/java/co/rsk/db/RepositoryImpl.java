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

package co.rsk.db;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.db.RepositoryTrack;

public class RepositoryImpl extends RepositoryTrack {

    public RepositoryImpl() {
        super();
    }

    public RepositoryImpl (Trie atrie) {
        super(atrie);
    }

    public RepositoryImpl(TrieStore store) {
        super(new TrieImpl(store,true),null); // secure by default
    }

    public RepositoryImpl(TrieStoreImpl storeImpl) {
        super(new TrieImpl(storeImpl,true),null); // secure by default
    }


}
